create table public.storage_cleanup_outbox (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references auth.users(id) on delete set null,
  bucket_id text not null default 'eggplant-scans'
    check (bucket_id = 'eggplant-scans'),
  object_path text not null
    check (
      char_length(object_path) between 1 and 1024
      and object_path !~ '(^/|(^|/)\.\.(/|$)|[[:cntrl:]])'
    ),
  reason text not null
    check (reason in ('sharing_consent_disabled')),
  state text not null default 'pending'
    check (state in ('pending', 'processing', 'retry')),
  attempt_count integer not null default 0 check (attempt_count >= 0),
  next_attempt_at timestamptz not null default now(),
  locked_at timestamptz,
  last_error_code text
    check (last_error_code is null or last_error_code ~ '^[a-z0-9_:-]{1,100}$'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (bucket_id, object_path, reason)
);

create index storage_cleanup_outbox_ready_idx
  on public.storage_cleanup_outbox (state, next_attempt_at, created_at);
create index storage_cleanup_outbox_owner_idx
  on public.storage_cleanup_outbox (owner_id, state);

alter table public.storage_cleanup_outbox enable row level security;
revoke all privileges on public.storage_cleanup_outbox from public, anon, authenticated;
grant select, insert, update, delete on public.storage_cleanup_outbox to service_role;

create trigger storage_cleanup_outbox_updated_at
before update on public.storage_cleanup_outbox
for each row execute function app_private.set_updated_at();

-- Preserve cleanup work created before this durable queue existed.
insert into public.storage_cleanup_outbox (
  bucket_id,
  owner_id,
  object_path,
  reason
)
select
  'eggplant-scans',
  owner_id,
  photo_path,
  'sharing_consent_disabled'
from public.global_share_intents
where status = 'cancelled'
on conflict (bucket_id, object_path, reason) do nothing;

create or replace function public.set_sharing_consent(
  p_owner_id uuid,
  p_enabled boolean,
  p_consent_version integer
)
returns table (sharing_enabled boolean, cancelled_paths text[])
language plpgsql
security definer
set search_path = ''
as $$
declare
  affected integer;
  newly_cancelled_paths text[] := array[]::text[];
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if p_enabled is null
    or (p_enabled and p_consent_version is distinct from 1)
    or (not p_enabled and p_consent_version is not null) then
    raise exception using errcode = '22023', message = 'invalid sharing consent';
  end if;
  perform pg_advisory_xact_lock(hashtextextended('owner:' || p_owner_id::text, 0));

  update public.installations set
    sharing_enabled = p_enabled,
    consent_version = case when p_enabled then p_consent_version else null end,
    consented_at = case when p_enabled then now() else null end,
    last_seen_at = now()
  where owner_id = p_owner_id;
  get diagnostics affected = row_count;
  if affected <> 1 then
    raise exception using errcode = 'P0002', message = 'installation not found';
  end if;

  if not p_enabled then
    with cancelled as (
      update public.global_share_intents
      set status = 'cancelled', updated_at = now()
      where owner_id = p_owner_id and status = 'pending'
      returning photo_path
    )
    select array_agg(photo_path order by photo_path)
    into newly_cancelled_paths
    from cancelled;

    if cardinality(newly_cancelled_paths) > 0 then
      insert into public.storage_cleanup_outbox (
        bucket_id,
        owner_id,
        object_path,
        reason
      )
      select
        'eggplant-scans',
        p_owner_id,
        path,
        'sharing_consent_disabled'
      from unnest(newly_cancelled_paths) as cancelled_path(path)
      on conflict (bucket_id, object_path, reason) do update set
        owner_id = excluded.owner_id,
        state = case
          when public.storage_cleanup_outbox.state = 'processing'
            then public.storage_cleanup_outbox.state
          else 'pending'
        end,
        next_attempt_at = case
          when public.storage_cleanup_outbox.state = 'processing'
            then public.storage_cleanup_outbox.next_attempt_at
          else now()
        end,
        locked_at = case
          when public.storage_cleanup_outbox.state = 'processing'
            then public.storage_cleanup_outbox.locked_at
          else null
        end,
        last_error_code = case
          when public.storage_cleanup_outbox.state = 'processing'
            then public.storage_cleanup_outbox.last_error_code
          else null
        end,
        updated_at = now();
    end if;

    -- Return durable work, not only rows cancelled by this invocation. This
    -- lets a repeated disable retry cleanup after the prior response or
    -- Storage removal failed. Keep the batch aligned with the acknowledgement
    -- RPC limit while maintenance drains any remaining rows asynchronously.
    select coalesce(
      array_agg(
        outstanding.object_path
        order by outstanding.next_attempt_at, outstanding.created_at, outstanding.id
      ),
      array[]::text[]
    )
    into cancelled_paths
    from (
      select cleanup.id, cleanup.object_path, cleanup.next_attempt_at, cleanup.created_at
      from public.storage_cleanup_outbox as cleanup
      where cleanup.owner_id = p_owner_id
        and cleanup.bucket_id = 'eggplant-scans'
        and cleanup.reason = 'sharing_consent_disabled'
      order by cleanup.next_attempt_at, cleanup.created_at, cleanup.id
      limit 100
    ) as outstanding;
  end if;

  return query select p_enabled, coalesce(cancelled_paths, array[]::text[]);
end;
$$;

-- A cancelled intent must not return to pending while its former object path
-- can still be removed by an already claimed or queued cleanup. Blocking the
-- reservation until acknowledgement keeps one immutable meaning per path.
create or replace function public.reserve_global_share_intent(
  p_owner_id uuid,
  p_client_scan_id uuid,
  p_disease_id text,
  p_confidence numeric,
  p_source text,
  p_model_version text,
  p_photo_path text,
  p_expected_sha256 text,
  p_rate_subject text
)
returns table (intent_path text, outcome text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  existing public.global_share_intents%rowtype;
  ip_count integer;
  renewing boolean := false;
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if p_rate_subject !~ '^[a-f0-9]{64}$' or p_expected_sha256 !~ '^[a-f0-9]{64}$' then
    raise exception using errcode = '22023', message = 'invalid intent security metadata';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('ip:' || p_rate_subject, 0));
  perform pg_advisory_xact_lock(hashtextextended('owner:' || p_owner_id::text, 0));

  select * into existing
  from public.global_share_intents
  where owner_id = p_owner_id and client_scan_id = p_client_scan_id;
  if found then
    if existing.disease_id <> p_disease_id
      or existing.confidence <> p_confidence
      or existing.source <> p_source
      or existing.model_version <> p_model_version
      or existing.photo_path <> p_photo_path
      or existing.expected_sha256 <> p_expected_sha256 then
      return query select existing.photo_path, 'conflict'::text;
      return;
    end if;
    if existing.status = 'completed' then
      return query select existing.photo_path, 'completed'::text;
      return;
    end if;
  end if;

  if not exists (
    select 1 from public.installations
    where owner_id = p_owner_id and sharing_enabled
  ) then
    return query select null::text, 'consent_required'::text;
    return;
  end if;

  if exists (
    select 1
    from public.storage_cleanup_outbox
    where owner_id = p_owner_id
      and bucket_id = 'eggplant-scans'
      and object_path = p_photo_path
      and reason = 'sharing_consent_disabled'
  ) then
    return query select p_photo_path, 'cleanup_pending'::text;
    return;
  end if;

  if existing.id is not null then
    if existing.status = 'pending'
      and existing.created_at >= now() - interval '2 hours' then
      return query select existing.photo_path, 'existing'::text;
      return;
    end if;
    renewing := true;
  end if;

  select coalesce(request_count, 0) into ip_count
  from public.api_rate_limits
  where action = 'global_share_intent'
    and window_start = current_date
    and subject_hash = p_rate_subject;
  if (select count(*) from public.global_share_intents
      where owner_id = p_owner_id and created_at >= now() - interval '24 hours') >= 20
    or coalesce(ip_count, 0) >= 60 then
    return query select null::text, 'quota'::text;
    return;
  end if;

  if renewing then
    update public.global_share_intents set
      status = 'pending',
      created_at = now(),
      updated_at = now()
    where owner_id = p_owner_id and client_scan_id = p_client_scan_id;
  else
    insert into public.global_share_intents (
      owner_id, client_scan_id, disease_id, confidence, source, model_version,
      photo_path, expected_sha256
    ) values (
      p_owner_id, p_client_scan_id, p_disease_id, p_confidence, p_source, p_model_version,
      p_photo_path, p_expected_sha256
    );
  end if;

  insert into public.api_rate_limits (action, window_start, subject_hash, request_count)
  values ('global_share_intent', current_date, p_rate_subject, 1)
  on conflict (action, window_start, subject_hash) do update
    set request_count = public.api_rate_limits.request_count + 1, updated_at = now();

  return query select p_photo_path, case when renewing then 'renewed' else 'created' end;
end;
$$;

create or replace function public.claim_storage_cleanup_batch(p_limit integer)
returns table (
  cleanup_id uuid,
  cleanup_bucket_id text,
  cleanup_object_path text,
  cleanup_attempt_count integer
)
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if p_limit is null or p_limit not between 1 and 100 then
    raise exception using errcode = '22023', message = 'invalid cleanup batch limit';
  end if;

  return query
  with candidates as (
    select queue.id
    from public.storage_cleanup_outbox as queue
    where (
      queue.state in ('pending', 'retry')
      and queue.next_attempt_at <= now()
    ) or (
      queue.state = 'processing'
      and (queue.locked_at is null or queue.locked_at < now() - interval '15 minutes')
    )
    order by queue.next_attempt_at, queue.created_at, queue.id
    limit p_limit
    for update skip locked
  )
  update public.storage_cleanup_outbox as queue
  set
    state = 'processing',
    attempt_count = queue.attempt_count + 1,
    locked_at = now(),
    updated_at = now()
  from candidates
  where queue.id = candidates.id
  returning queue.id, queue.bucket_id, queue.object_path, queue.attempt_count;
end;
$$;

create or replace function public.acknowledge_storage_cleanup(
  p_cleanup_id uuid,
  p_succeeded boolean,
  p_error_code text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  cleanup public.storage_cleanup_outbox%rowtype;
  retry_seconds integer;
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if p_succeeded is null
    or p_cleanup_id is null
    or (
      not p_succeeded
      and coalesce(p_error_code, '') !~ '^[a-z0-9_:-]{1,100}$'
    ) then
    raise exception using errcode = '22023', message = 'invalid cleanup acknowledgement';
  end if;

  select * into cleanup
  from public.storage_cleanup_outbox
  where id = p_cleanup_id
  for update;
  if not found then
    -- Idempotent acknowledgement after an immediate cleanup won the race.
    return true;
  end if;
  if cleanup.state <> 'processing' then
    return false;
  end if;

  if p_succeeded then
    delete from public.storage_cleanup_outbox where id = cleanup.id;
    if cleanup.reason = 'sharing_consent_disabled' then
      delete from public.global_share_intents
      where status = 'cancelled' and photo_path = cleanup.object_path;
    end if;
    return true;
  end if;

  retry_seconds := least(
    86400,
    (30 * power(2, least(greatest(cleanup.attempt_count - 1, 0), 11)))::integer
  );
  update public.storage_cleanup_outbox set
    state = 'retry',
    next_attempt_at = now() + make_interval(secs => retry_seconds),
    locked_at = null,
    last_error_code = p_error_code,
    updated_at = now()
  where id = cleanup.id;
  return true;
end;
$$;

create or replace function public.acknowledge_storage_cleanup_paths(
  p_owner_id uuid,
  p_bucket_id text,
  p_paths text[]
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  affected integer;
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if p_bucket_id <> 'eggplant-scans'
    or p_owner_id is null
    or p_paths is null
    or cardinality(p_paths) not between 1 and 100
    or exists (
      select 1 from unnest(p_paths) as cleanup_path(path)
      where path is null or char_length(path) not between 1 and 1024
    ) then
    raise exception using errcode = '22023', message = 'invalid cleanup paths';
  end if;

  delete from public.storage_cleanup_outbox
  where bucket_id = p_bucket_id
    and owner_id = p_owner_id
    and reason = 'sharing_consent_disabled'
    and object_path = any(p_paths);
  get diagnostics affected = row_count;

  delete from public.global_share_intents
  where owner_id = p_owner_id
    and status = 'cancelled'
    and photo_path = any(p_paths);
  return affected;
end;
$$;

revoke all on function public.set_sharing_consent(uuid, boolean, integer) from public, anon, authenticated;
revoke all on function public.reserve_global_share_intent(uuid, uuid, text, numeric, text, text, text, text, text) from public, anon, authenticated;
revoke all on function public.claim_storage_cleanup_batch(integer) from public, anon, authenticated;
revoke all on function public.acknowledge_storage_cleanup(uuid, boolean, text) from public, anon, authenticated;
revoke all on function public.acknowledge_storage_cleanup_paths(uuid, text, text[]) from public, anon, authenticated;

grant execute on function public.set_sharing_consent(uuid, boolean, integer) to service_role;
grant execute on function public.reserve_global_share_intent(uuid, uuid, text, numeric, text, text, text, text, text) to service_role;
grant execute on function public.claim_storage_cleanup_batch(integer) to service_role;
grant execute on function public.acknowledge_storage_cleanup(uuid, boolean, text) to service_role;
grant execute on function public.acknowledge_storage_cleanup_paths(uuid, text, text[]) to service_role;
