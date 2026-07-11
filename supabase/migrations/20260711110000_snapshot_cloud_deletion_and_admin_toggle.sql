-- v1.5.1: deletion work must be an immutable snapshot. A retry of an old
-- deletion request must never remove a scan shared after that request.
create table if not exists public.deletion_request_targets (
  deletion_request_id uuid not null references public.deletion_requests(id) on delete cascade,
  resource_type text not null check (resource_type in ('scan_contribution', 'global_share_intent')),
  resource_id uuid not null,
  photo_path text not null check (
    char_length(photo_path) between 1 and 1024
    and photo_path !~ '(^/|(^|/)\\.\\.(/|$)|[[:cntrl:]])'
  ),
  created_at timestamptz not null default now(),
  primary key (deletion_request_id, resource_type, resource_id)
);
create index if not exists deletion_request_targets_request_idx
  on public.deletion_request_targets (deletion_request_id, resource_type);
alter table public.deletion_request_targets enable row level security;
revoke all on public.deletion_request_targets from public, anon, authenticated;
grant select, insert, update, delete on public.deletion_request_targets to service_role;

-- Include the owner-only cloud write control in the same durable receipt
-- system as catalog publishing, request review, and scan moderation.
alter table public.admin_action_receipts
  drop constraint if exists admin_action_receipts_action_check;
alter table public.admin_action_receipts
  add constraint admin_action_receipts_action_check
  check (action in ('catalog_publish', 'request_review', 'scan_moderation', 'cloud_writes_toggle'));

create or replace function app_private.claim_admin_action(
  p_admin_id uuid,
  p_action text,
  p_resource_key text,
  p_idempotency_key uuid,
  p_payload_hash text
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  inserted integer;
  existing_hash text;
begin
  if (select auth.role()) <> 'service_role'
    or not exists (select 1 from public.admin_members where user_id = p_admin_id)
    or p_action not in ('catalog_publish', 'request_review', 'scan_moderation', 'cloud_writes_toggle')
    or p_idempotency_key is null
    or p_payload_hash !~ '^[a-f0-9]{64}$' then
    raise insufficient_privilege using message = 'valid authorized admin action required';
  end if;

  insert into public.admin_action_receipts (
    admin_id, action, resource_key, idempotency_key, payload_hash, outcome
  ) values (
    p_admin_id, p_action, p_resource_key, p_idempotency_key, p_payload_hash, 'unchanged'
  ) on conflict (admin_id, action, resource_key, idempotency_key) do nothing;
  get diagnostics inserted = row_count;
  if inserted = 1 then return 'new'; end if;

  select payload_hash into existing_hash
  from public.admin_action_receipts
  where admin_id = p_admin_id
    and action = p_action
    and resource_key = p_resource_key
    and idempotency_key = p_idempotency_key;
  if existing_hash is distinct from p_payload_hash then return 'conflict'; end if;
  return 'replay';
end;
$$;
revoke all on function app_private.claim_admin_action(uuid, text, text, uuid, text) from public, anon, authenticated;

create or replace function public.set_cloud_writes_enabled_v2(
  p_enabled boolean,
  p_admin_id uuid,
  p_idempotency_key uuid,
  p_payload_hash text
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  claim text;
  prior_outcome text;
  current_enabled boolean;
begin
  if p_enabled is null then
    raise exception using errcode = '22023', message = 'invalid cloud write state';
  end if;
  claim := app_private.claim_admin_action(
    p_admin_id, 'cloud_writes_toggle', 'cloud_writes_enabled', p_idempotency_key, p_payload_hash
  );
  if claim = 'conflict' then
    raise exception using errcode = '23505', message = 'idempotency key conflicts with another cloud write action';
  end if;
  if claim = 'replay' then
    select outcome into prior_outcome from public.admin_action_receipts
    where admin_id = p_admin_id and action = 'cloud_writes_toggle'
      and resource_key = 'cloud_writes_enabled' and idempotency_key = p_idempotency_key;
    return coalesce(prior_outcome, 'unchanged');
  end if;

  select cloud_writes_enabled into current_enabled from public.app_config where id = true for update;
  if current_enabled is null then
    raise exception using errcode = 'P0002', message = 'app configuration not found';
  end if;
  if current_enabled = p_enabled then
    perform app_private.complete_admin_action(
      p_admin_id, 'cloud_writes_toggle', 'cloud_writes_enabled', p_idempotency_key, 'unchanged'
    );
    return 'unchanged';
  end if;

  update public.app_config set cloud_writes_enabled = p_enabled where id = true;
  insert into public.moderation_actions (admin_id, resource_type, resource_key, action, reason)
  values (
    p_admin_id,
    'app_config',
    'cloud_writes_enabled',
    case when p_enabled then 'cloud_writes_enabled' else 'cloud_writes_paused' end,
    'Production write kill switch changed'
  );
  perform app_private.complete_admin_action(
    p_admin_id, 'cloud_writes_toggle', 'cloud_writes_enabled', p_idempotency_key, 'applied'
  );
  return 'applied';
end;
$$;

create or replace function public.request_shared_cloud_deletion_v2(p_owner_id uuid)
returns table (
  deletion_id uuid,
  deletion_status text,
  affected_contribution_ids uuid[],
  cancelled_paths text[]
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  request_row public.deletion_requests%rowtype;
  request_id uuid;
  snapshot_exists boolean;
  unpublished_ids uuid[] := array[]::uuid[];
  cleanup_paths text[] := array[]::text[];
begin
  if (select auth.role()) <> 'service_role' or p_owner_id is null then
    raise insufficient_privilege using message = 'service role required';
  end if;
  perform pg_advisory_xact_lock(hashtextextended('owner:' || p_owner_id::text, 0));

  -- Disable future publication first. The consent function uses the same owner
  -- lock and records cancellation cleanup durably before returning.
  select consent.cancelled_paths into cleanup_paths
  from public.set_sharing_consent(p_owner_id, false, null) as consent
  limit 1;

  select * into request_row
  from public.deletion_requests
  where owner_id = p_owner_id and status in ('queued', 'processing', 'failed')
  order by created_at desc, id desc
  limit 1
  for update;

  if found then
    request_id := request_row.id;
    if request_row.status = 'failed' then
      update public.deletion_requests
      set status = 'queued', last_error_code = null
      where id = request_id;
      request_row.status := 'queued';
    end if;
    select exists(
      select 1 from public.deletion_request_targets where deletion_request_id = request_id
    ) into snapshot_exists;
  else
    insert into public.deletion_requests (
      owner_id, status, scope, unpublished_at, last_error_code
    ) values (
      p_owner_id, 'queued', 'shared', now(), null
    ) returning id into request_id;
    request_row.status := 'queued';
    snapshot_exists := false;
  end if;

  -- Existing records without targets can only be pre-v1.5.1 requests. Capture
  -- their currently remaining data once; all newly-created requests always
  -- capture their exact snapshot in this transaction.
  if not snapshot_exists then
    insert into public.deletion_request_targets (
      deletion_request_id, resource_type, resource_id, photo_path
    )
    select request_id, 'scan_contribution', contribution.id, contribution.photo_path
    from public.scan_contributions as contribution
    where contribution.owner_id = p_owner_id
    union all
    select request_id, 'global_share_intent', intent.id, intent.photo_path
    from public.global_share_intents as intent
    where intent.owner_id = p_owner_id
    on conflict do nothing;

    with unpublished as (
      update public.scan_contributions
      set status = 'removed'
      where owner_id = p_owner_id
        and status in ('published', 'quarantined')
      returning id
    )
    select coalesce(array_agg(id order by id), array[]::uuid[]) into unpublished_ids
    from unpublished;
  end if;

  return query select
    request_id,
    request_row.status,
    unpublished_ids,
    coalesce(cleanup_paths, array[]::text[]);
end;
$$;

revoke all on function public.set_cloud_writes_enabled_v2(boolean, uuid, uuid, text) from public, anon, authenticated;
revoke all on function public.request_shared_cloud_deletion_v2(uuid) from public, anon, authenticated;
grant execute on function public.set_cloud_writes_enabled_v2(boolean, uuid, uuid, text) to service_role;
grant execute on function public.request_shared_cloud_deletion_v2(uuid) to service_role;
