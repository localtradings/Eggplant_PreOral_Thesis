-- v1.5.1 stabilization contracts. This migration is forward-only and preserves
-- existing request notes while enforcing the smaller limit for new writes.

-- Melon Thrips is an existing model class (index 6) and belongs to the product's
-- fruit-disease category. Its stable id and model mapping remain unchanged.
update public.disease_catalog
set category = 'FRUIT_DISEASE'
where id = 'melon-thrips'
  and category is distinct from 'FRUIT_DISEASE';

-- Canonical cloud content needs its own recommended action so catalog edits
-- propagate to Result and History instead of leaving the bundled treatment.
alter table public.disease_localizations
  add column if not exists recommended_action text not null default '';
update public.disease_localizations
set recommended_action = guidance
where recommended_action = '';

-- Every localized response now includes a new canonical field and Melon Thrips
-- changed category. Bump both levels so conditional catalog clients cannot
-- receive a stale 304 response after this migration.
update public.disease_catalog
set content_version = content_version + 1;
update public.app_config
set catalog_version = catalog_version + 1
where id = true;

-- A requester may not know the disease name. Normalize an empty value to NULL
-- while retaining the existing 2..120 bound for provided names.
alter table public.disease_requests
  alter column requested_name drop not null;
alter table public.disease_requests
  drop constraint if exists disease_requests_requested_name_check;
alter table public.disease_requests
  add constraint disease_requests_requested_name_optional_check
  check (
    requested_name is null
    or char_length(trim(requested_name)) between 2 and 120
  );

-- Keep legacy notes readable. New inserts and updates which change notes are
-- capped at 200 characters without invalidating old rows up to the old limit.
create or replace function app_private.enforce_disease_request_note_limit()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if tg_op = 'INSERT'
    or new.notes is distinct from old.notes then
    if new.notes is not null and char_length(new.notes) > 200 then
      raise exception using errcode = '22023', message = 'disease request notes exceed 200 characters';
    end if;
  end if;
  new.requested_name := nullif(trim(coalesce(new.requested_name, '')), '');
  return new;
end;
$$;
revoke all on function app_private.enforce_disease_request_note_limit() from public, anon, authenticated;

drop trigger if exists disease_requests_note_limit on public.disease_requests;
create trigger disease_requests_note_limit
before insert or update of notes, requested_name on public.disease_requests
for each row execute function app_private.enforce_disease_request_note_limit();

alter table public.disease_request_photos
  add column if not exists capture_source text not null default 'capture'
    check (capture_source in ('live', 'capture'));

-- Deletion requests are intentionally scoped to public contributions/photos;
-- private disease requests and local mobile history are outside this action.
alter table public.deletion_requests
  add column if not exists scope text not null default 'shared'
    check (scope = 'shared'),
  add column if not exists unpublished_at timestamptz,
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists attempt_count integer not null default 0
    check (attempt_count >= 0),
  add column if not exists last_error_code text
    check (last_error_code is null or last_error_code ~ '^[a-z0-9_:-]{1,100}$');

drop trigger if exists deletion_requests_updated_at on public.deletion_requests;
create trigger deletion_requests_updated_at
before update on public.deletion_requests
for each row execute function app_private.set_updated_at();

-- Durable receipts make retries and repeated button presses idempotent even if
-- the browser loses the first response.
create table if not exists public.admin_action_receipts (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid not null references auth.users(id) on delete cascade,
  action text not null check (action in ('catalog_publish', 'request_review', 'scan_moderation')),
  resource_key text not null check (char_length(resource_key) between 1 and 200),
  idempotency_key uuid not null,
  payload_hash text not null check (payload_hash ~ '^[a-f0-9]{64}$'),
  outcome text not null check (outcome in ('applied', 'unchanged')),
  created_at timestamptz not null default now(),
  unique (admin_id, action, resource_key, idempotency_key)
);
alter table public.admin_action_receipts enable row level security;
revoke all on public.admin_action_receipts from public, anon, authenticated;
grant select, insert, update, delete on public.admin_action_receipts to service_role;

-- Versioned request RPC: accepts optional names, enforces the new note limit,
-- and records camera provenance for every supplied photo.
create or replace function public.create_disease_request_with_quota_v2(
  p_owner_id uuid,
  p_client_request_id uuid,
  p_requested_name text,
  p_notes text,
  p_model_version text,
  p_rights_consent boolean,
  p_training_consent boolean,
  p_photo_hashes text[],
  p_photo_sources text[],
  p_rate_subject text
)
returns table (request_id uuid, request_status text, outcome text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  existing public.disease_requests%rowtype;
  created public.disease_requests%rowtype;
  ip_count integer;
  expected_paths text[];
  recorded_paths text[];
  normalized_name text := nullif(trim(coalesce(p_requested_name, '')), '');
  normalized_notes text := nullif(trim(coalesce(p_notes, '')), '');
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if p_rate_subject !~ '^[a-f0-9]{64}$'
    or not p_rights_consent
    or p_training_consent
    or cardinality(p_photo_hashes) not between 1 and 3
    or cardinality(p_photo_sources) <> cardinality(p_photo_hashes)
    or exists (select 1 from unnest(p_photo_hashes) as photo_hash where photo_hash !~ '^[a-f0-9]{64}$')
    or exists (select 1 from unnest(p_photo_sources) as source where source not in ('live', 'capture'))
    or (normalized_name is not null and char_length(normalized_name) not between 2 and 120)
    or (normalized_notes is not null and char_length(normalized_notes) > 200) then
    raise exception using errcode = '22023', message = 'invalid disease request';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('ip:' || p_rate_subject, 0));
  perform pg_advisory_xact_lock(hashtextextended('owner:' || p_owner_id::text, 0));

  select * into existing
  from public.disease_requests
  where owner_id = p_owner_id and client_request_id = p_client_request_id;
  if found then
    select array_agg(format('requests/%s/%s/%s-%s.jpg', p_owner_id, existing.id, photo.ordinality - 1, photo.hash) order by photo.ordinality)
      into expected_paths
    from unnest(p_photo_hashes) with ordinality as photo(hash, ordinality);
    select array_agg(object_path order by position) into recorded_paths
    from public.disease_request_photos
    where request_id = existing.id and owner_id = p_owner_id;
    if existing.requested_name is not distinct from normalized_name
      and existing.notes is not distinct from normalized_notes
      and existing.model_version = p_model_version
      and existing.rights_consent = p_rights_consent
      and existing.training_consent = p_training_consent
      and recorded_paths is not distinct from expected_paths then
      return query select existing.id, existing.status, 'existing'::text;
    else
      return query select existing.id, existing.status, 'conflict'::text;
    end if;
    return;
  end if;

  select coalesce(request_count, 0) into ip_count
  from public.api_rate_limits
  where action = 'disease_request' and window_start = current_date and subject_hash = p_rate_subject;
  if (select count(*) from public.disease_requests where owner_id = p_owner_id and created_at >= now() - interval '24 hours') >= 5
    or coalesce(ip_count, 0) >= 15 then
    return query select null::uuid, null::text, 'quota'::text;
    return;
  end if;

  insert into public.disease_requests (
    owner_id, client_request_id, requested_name, notes, model_version, rights_consent, training_consent
  ) values (
    p_owner_id, p_client_request_id, normalized_name, normalized_notes, p_model_version, p_rights_consent, p_training_consent
  ) returning * into created;

  insert into public.disease_request_photos (request_id, owner_id, position, object_path, capture_source)
  select created.id, p_owner_id, (photo.ordinality - 1)::smallint,
    format('requests/%s/%s/%s-%s.jpg', p_owner_id, created.id, photo.ordinality - 1, photo.hash),
    p_photo_sources[photo.ordinality]
  from unnest(p_photo_hashes) with ordinality as photo(hash, ordinality);

  insert into public.api_rate_limits (action, window_start, subject_hash, request_count)
  values ('disease_request', current_date, p_rate_subject, 1)
  on conflict (action, window_start, subject_hash) do update
    set request_count = public.api_rate_limits.request_count + 1, updated_at = now();

  return query select created.id, created.status, 'created'::text;
end;
$$;
revoke all on function public.create_disease_request_with_quota_v2(uuid, uuid, text, text, text, boolean, boolean, text[], text[], text) from public, anon, authenticated;
grant execute on function public.create_disease_request_with_quota_v2(uuid, uuid, text, text, text, boolean, boolean, text[], text[], text) to service_role;
