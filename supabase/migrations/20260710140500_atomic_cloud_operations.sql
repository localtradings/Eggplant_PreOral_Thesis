create table public.api_rate_limits (
  action text not null check (action in ('global_share_intent', 'disease_request', 'content_report')),
  window_start date not null,
  subject_hash text not null check (subject_hash ~ '^[a-f0-9]{64}$'),
  request_count integer not null check (request_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (action, window_start, subject_hash)
);

alter table public.api_rate_limits enable row level security;
revoke all privileges on public.api_rate_limits from public, anon, authenticated;
grant select, insert, update, delete on public.api_rate_limits to service_role;

create table public.global_share_intents (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  client_scan_id uuid not null,
  disease_id text not null references public.disease_catalog(id) on delete restrict,
  confidence numeric(5,4) not null check (confidence >= 0.5 and confidence <= 1),
  source text not null check (source in ('live', 'capture')),
  model_version text not null,
  photo_path text not null,
  expected_sha256 text not null check (expected_sha256 ~ '^[a-f0-9]{64}$'),
  status text not null default 'pending' check (status in ('pending', 'completed', 'cancelled')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (owner_id, client_scan_id)
);

create index global_share_intents_cleanup_idx
  on public.global_share_intents (status, created_at);
create index global_share_intents_owner_created_idx
  on public.global_share_intents (owner_id, created_at desc);
alter table public.global_share_intents enable row level security;
revoke all privileges on public.global_share_intents from public, anon, authenticated;
grant select, insert, update, delete on public.global_share_intents to service_role;

create table public.content_report_rate_subjects (
  contribution_id uuid not null references public.scan_contributions(id) on delete cascade,
  subject_hash text not null check (subject_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz not null default now(),
  primary key (contribution_id, subject_hash)
);
alter table public.content_report_rate_subjects enable row level security;
revoke all privileges on public.content_report_rate_subjects from public, anon, authenticated;
grant select, insert, delete on public.content_report_rate_subjects to service_role;

-- Android uploads exclusively through short-lived signed URLs issued after the
-- mobile API validates quotas and metadata. Direct anonymous Storage access
-- would bypass those controls and would allow published evidence to be changed.
drop policy if exists eggplant_storage_owner_insert on storage.objects;
drop policy if exists eggplant_storage_owner_select on storage.objects;
drop policy if exists eggplant_storage_owner_delete on storage.objects;

-- Retain an anonymous subject key after an owner invokes cloud deletion. The
-- original foreign keys still become null, but the audit record remains
-- attributable to the deleted subject without retaining an owner identity.
alter table public.moderation_actions
  add column contribution_subject_id uuid,
  add column request_subject_id uuid,
  add column resource_type text,
  add column resource_key text;
update public.moderation_actions
set contribution_subject_id = contribution_id,
    request_subject_id = request_id
where contribution_subject_id is null and request_subject_id is null;
alter table public.moderation_actions drop constraint moderation_actions_check;
alter table public.moderation_actions add constraint moderation_actions_subject_check
  check (
    contribution_id is not null or request_id is not null
    or contribution_subject_id is not null or request_subject_id is not null
    or (resource_type is not null and resource_key is not null)
  );
create index moderation_actions_contribution_subject_idx
  on public.moderation_actions (contribution_subject_id, created_at desc);
create index moderation_actions_request_subject_idx
  on public.moderation_actions (request_subject_id, created_at desc);

create or replace function app_private.quarantine_reported_contribution()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  quarantined_id uuid;
begin
  if (
    select count(*) >= 2
    from public.content_reports
    where contribution_id = new.contribution_id
  ) then
    update public.scan_contributions
      set status = 'quarantined'
      where id = new.contribution_id and status = 'published'
      returning id into quarantined_id;
    if quarantined_id is not null then
      insert into public.moderation_actions (
        contribution_id,
        contribution_subject_id,
        action,
        reason
      ) values (
        quarantined_id,
        quarantined_id,
        'automated_quarantine',
        'Two unique community reports'
      );
    end if;
  end if;
  return new;
end;
$$;

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
    if existing.status = 'pending'
      and existing.created_at >= now() - interval '2 hours' then
      return query select existing.photo_path, 'existing'::text;
      return;
    end if;
    renewing := true;
  end if;

  if not exists (
    select 1 from public.installations
    where owner_id = p_owner_id and sharing_enabled
  ) then
    return query select null::text, 'consent_required'::text;
    return;
  end if;

  select coalesce(request_count, 0) into ip_count
  from public.api_rate_limits
  where action = 'global_share_intent' and window_start = current_date and subject_hash = p_rate_subject;
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

create or replace function public.create_scan_contribution_with_quota(
  p_owner_id uuid,
  p_client_scan_id uuid,
  p_disease_id text,
  p_confidence numeric,
  p_source text,
  p_model_version text,
  p_photo_path text
)
returns table (
  contribution_id uuid,
  contribution_status text,
  contribution_published_at timestamptz,
  outcome text
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  existing public.scan_contributions%rowtype;
  created public.scan_contributions%rowtype;
  intent public.global_share_intents%rowtype;
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  perform pg_advisory_xact_lock(hashtextextended('owner:' || p_owner_id::text, 0));

  select * into existing
  from public.scan_contributions
  where owner_id = p_owner_id and client_scan_id = p_client_scan_id;
  if found then
    if existing.disease_id = p_disease_id
      and existing.confidence = p_confidence
      and existing.source = p_source
      and existing.model_version = p_model_version
      and existing.photo_path = p_photo_path then
      update public.global_share_intents set status = 'completed', updated_at = now()
        where owner_id = p_owner_id and client_scan_id = p_client_scan_id;
      return query select existing.id, existing.status, existing.published_at, 'existing'::text;
    else
      return query select existing.id, existing.status, existing.published_at, 'conflict'::text;
    end if;
    return;
  end if;

  select * into intent
  from public.global_share_intents
  where owner_id = p_owner_id and client_scan_id = p_client_scan_id;
  if not found
    or intent.status <> 'pending'
    or intent.disease_id <> p_disease_id
    or intent.confidence <> p_confidence
    or intent.source <> p_source
    or intent.model_version <> p_model_version
    or intent.photo_path <> p_photo_path
    or intent.created_at < now() - interval '2 hours' then
    return query select null::uuid, null::text, null::timestamptz, 'invalid_intent'::text;
    return;
  end if;

  if not exists (
    select 1 from public.installations
    where owner_id = p_owner_id and sharing_enabled
  ) then
    return query select null::uuid, null::text, null::timestamptz, 'consent_required'::text;
    return;
  end if;

  insert into public.scan_contributions (
    owner_id, client_scan_id, disease_id, confidence, source, model_version, photo_path, status
  ) values (
    p_owner_id, p_client_scan_id, p_disease_id, p_confidence, p_source, p_model_version, p_photo_path, 'published'
  ) returning * into created;

  update public.global_share_intents set status = 'completed', updated_at = now()
    where owner_id = p_owner_id and client_scan_id = p_client_scan_id;

  return query select created.id, created.status, created.published_at, 'created'::text;
end;
$$;

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
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if (p_enabled and p_consent_version <> 1)
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
    select array_agg(photo_path order by photo_path) into cancelled_paths
    from cancelled;
  end if;
  return query select p_enabled, coalesce(cancelled_paths, array[]::text[]);
end;
$$;

create or replace function public.report_scan_contribution(
  p_reporter_id uuid,
  p_contribution_id uuid,
  p_reason text,
  p_details text,
  p_rate_subject text
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  contribution public.scan_contributions%rowtype;
  ip_count integer;
  affected integer;
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if p_rate_subject !~ '^[a-f0-9]{64}$'
    or p_reason not in ('incorrect_result', 'not_eggplant', 'inappropriate', 'duplicate', 'other')
    or char_length(coalesce(p_details, '')) > 1000 then
    raise exception using errcode = '22023', message = 'invalid content report';
  end if;
  perform pg_advisory_xact_lock(hashtextextended('report:' || p_contribution_id::text, 0));
  perform pg_advisory_xact_lock(hashtextextended('ip:' || p_rate_subject, 0));

  select * into contribution
  from public.scan_contributions
  where id = p_contribution_id and status in ('published', 'quarantined');
  if not found then return 'unavailable'; end if;
  if contribution.owner_id = p_reporter_id then return 'self_report'; end if;
  if exists (
    select 1 from public.content_reports
    where contribution_id = p_contribution_id and reporter_id = p_reporter_id
  ) then
    return 'duplicate';
  end if;

  select coalesce(request_count, 0) into ip_count
  from public.api_rate_limits
  where action = 'content_report' and window_start = current_date and subject_hash = p_rate_subject;
  if coalesce(ip_count, 0) >= 20 then return 'quota'; end if;

  insert into public.content_report_rate_subjects (contribution_id, subject_hash)
  values (p_contribution_id, p_rate_subject)
  on conflict do nothing;
  get diagnostics affected = row_count;
  if affected <> 1 then return 'rate_duplicate'; end if;

  insert into public.content_reports (
    contribution_id,
    reporter_id,
    reason,
    details
  ) values (
    p_contribution_id,
    p_reporter_id,
    p_reason,
    nullif(trim(p_details), '')
  );

  insert into public.api_rate_limits (action, window_start, subject_hash, request_count)
  values ('content_report', current_date, p_rate_subject, 1)
  on conflict (action, window_start, subject_hash) do update
    set request_count = public.api_rate_limits.request_count + 1, updated_at = now();
  return 'accepted';
end;
$$;

create or replace function public.create_disease_request_with_quota(
  p_owner_id uuid,
  p_client_request_id uuid,
  p_requested_name text,
  p_notes text,
  p_model_version text,
  p_rights_consent boolean,
  p_training_consent boolean,
  p_photo_hashes text[],
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
begin
  if (select auth.role()) <> 'service_role' then
    raise insufficient_privilege using message = 'service role required';
  end if;
  if p_rate_subject !~ '^[a-f0-9]{64}$'
    or not p_rights_consent
    or p_training_consent
    or cardinality(p_photo_hashes) not between 1 and 3
    or exists (
      select 1 from unnest(p_photo_hashes) as photo_hash
      where photo_hash !~ '^[a-f0-9]{64}$'
    ) then
    raise exception using errcode = '22023', message = 'invalid request security metadata';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('ip:' || p_rate_subject, 0));
  perform pg_advisory_xact_lock(hashtextextended('owner:' || p_owner_id::text, 0));

  select * into existing
  from public.disease_requests
  where owner_id = p_owner_id and client_request_id = p_client_request_id;
  if found then
    select array_agg(
      format(
        'requests/%s/%s/%s-%s.jpg',
        p_owner_id,
        existing.id,
        photo.ordinality - 1,
        photo.hash
      ) order by photo.ordinality
    ) into expected_paths
    from unnest(p_photo_hashes) with ordinality as photo(hash, ordinality);
    select array_agg(object_path order by position) into recorded_paths
    from public.disease_request_photos
    where request_id = existing.id and owner_id = p_owner_id;
    if existing.requested_name = p_requested_name
      and existing.notes is not distinct from p_notes
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
  if (select count(*) from public.disease_requests
      where owner_id = p_owner_id and created_at >= now() - interval '24 hours') >= 5
    or coalesce(ip_count, 0) >= 15 then
    return query select null::uuid, null::text, 'quota'::text;
    return;
  end if;

  insert into public.disease_requests (
    owner_id, client_request_id, requested_name, notes, model_version,
    rights_consent, training_consent
  ) values (
    p_owner_id, p_client_request_id, p_requested_name, p_notes, p_model_version,
    p_rights_consent, p_training_consent
  ) returning * into created;

  insert into public.disease_request_photos (
    request_id,
    owner_id,
    position,
    object_path
  )
  select
    created.id,
    p_owner_id,
    (photo.ordinality - 1)::smallint,
    format(
      'requests/%s/%s/%s-%s.jpg',
      p_owner_id,
      created.id,
      photo.ordinality - 1,
      photo.hash
    )
  from unnest(p_photo_hashes) with ordinality as photo(hash, ordinality);

  insert into public.api_rate_limits (action, window_start, subject_hash, request_count)
  values ('disease_request', current_date, p_rate_subject, 1)
  on conflict (action, window_start, subject_hash) do update
    set request_count = public.api_rate_limits.request_count + 1, updated_at = now();

  return query select created.id, created.status, 'created'::text;
end;
$$;

create or replace function public.review_disease_request(
  p_request_id uuid,
  p_status text,
  p_admin_note text,
  p_admin_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  changed_id uuid;
begin
  if (select auth.role()) <> 'service_role'
    or not exists (select 1 from public.admin_members where user_id = p_admin_id) then
    raise insufficient_privilege using message = 'authorized admin required';
  end if;
  if p_status not in ('under_review', 'planned', 'needs_information', 'not_supported', 'closed')
    or char_length(coalesce(p_admin_note, '')) > 2000 then
    raise exception using errcode = '22023', message = 'invalid request review';
  end if;

  update public.disease_requests
    set status = p_status, admin_note = nullif(trim(p_admin_note), '')
    where id = p_request_id
    returning id into changed_id;
  if changed_id is null then return false; end if;

  insert into public.moderation_actions (
    request_id,
    request_subject_id,
    admin_id,
    action,
    reason
  ) values (
    p_request_id,
    p_request_id,
    p_admin_id,
    'request_' || p_status,
    nullif(trim(p_admin_note), '')
  );
  return true;
end;
$$;

create or replace function public.moderate_scan_contribution(
  p_contribution_id uuid,
  p_status text,
  p_reason text,
  p_admin_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  changed_id uuid;
begin
  if (select auth.role()) <> 'service_role'
    or not exists (select 1 from public.admin_members where user_id = p_admin_id) then
    raise insufficient_privilege using message = 'authorized admin required';
  end if;
  if p_status not in ('published', 'quarantined', 'removed')
    or char_length(coalesce(p_reason, '')) > 500 then
    raise exception using errcode = '22023', message = 'invalid moderation action';
  end if;

  if p_status = 'published' then
    update public.scan_contributions contribution
      set status = 'published'
      where contribution.id = p_contribution_id
        and contribution.status in ('published', 'quarantined', 'removed')
        and contribution.expires_at > now()
        and exists (
          select 1 from storage.objects object
          where object.bucket_id = 'eggplant-scans'
            and object.name = contribution.photo_path
        )
      returning contribution.id into changed_id;
  else
    update public.scan_contributions
      set status = p_status
      where id = p_contribution_id
      returning id into changed_id;
  end if;
  if changed_id is null then return false; end if;

  insert into public.moderation_actions (
    contribution_id,
    contribution_subject_id,
    admin_id,
    action,
    reason
  ) values (
    p_contribution_id,
    p_contribution_id,
    p_admin_id,
    p_status,
    nullif(trim(p_reason), '')
  );
  return true;
end;
$$;

create or replace function public.update_disease_catalog_content(
  p_disease_id text,
  p_en_content jsonb,
  p_fil_content jsonb,
  p_en_signs jsonb,
  p_fil_signs jsonb,
  p_en_reference jsonb,
  p_fil_reference jsonb,
  p_admin_id uuid
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  affected integer;
begin
  if (select auth.role()) <> 'service_role'
    or not exists (select 1 from public.admin_members where user_id = p_admin_id) then
    raise insufficient_privilege using message = 'authorized admin required';
  end if;
  if jsonb_typeof(p_en_signs) <> 'array'
    or jsonb_typeof(p_fil_signs) <> 'array'
    or jsonb_array_length(p_en_signs) not between 1 and 20
    or jsonb_array_length(p_fil_signs) not between 1 and 20
    or exists (
      select 1 from jsonb_array_elements_text(p_en_signs) as sign(value)
      where char_length(trim(sign.value)) not between 2 and 500
    )
    or exists (
      select 1 from jsonb_array_elements_text(p_fil_signs) as sign(value)
      where char_length(trim(sign.value)) not between 2 and 500
    ) then
    raise exception using errcode = '22023', message = 'invalid bilingual symptoms';
  end if;

  update public.disease_localizations set
    name = p_en_content ->> 'name',
    description = p_en_content ->> 'description',
    symptom_preview = p_en_content ->> 'symptom_preview',
    causes = p_en_content ->> 'causes',
    prevention = p_en_content ->> 'prevention',
    guidance = p_en_content ->> 'guidance',
    when_to_act = p_en_content ->> 'when_to_act',
    disclaimer = p_en_content ->> 'disclaimer'
    where disease_id = p_disease_id and language_tag = 'en';
  get diagnostics affected = row_count;
  if affected <> 1 then raise exception using errcode = 'P0002', message = 'English content not found'; end if;

  update public.disease_localizations set
    name = p_fil_content ->> 'name',
    description = p_fil_content ->> 'description',
    symptom_preview = p_fil_content ->> 'symptom_preview',
    causes = p_fil_content ->> 'causes',
    prevention = p_fil_content ->> 'prevention',
    guidance = p_fil_content ->> 'guidance',
    when_to_act = p_fil_content ->> 'when_to_act',
    disclaimer = p_fil_content ->> 'disclaimer'
    where disease_id = p_disease_id and language_tag = 'fil';
  get diagnostics affected = row_count;
  if affected <> 1 then raise exception using errcode = 'P0002', message = 'Filipino content not found'; end if;

  delete from public.disease_signs
  where disease_id = p_disease_id and language_tag in ('en', 'fil');
  insert into public.disease_signs (disease_id, language_tag, position, text)
  select p_disease_id, 'en', (sign.ordinality - 1)::smallint, trim(sign.value)
  from jsonb_array_elements_text(p_en_signs) with ordinality as sign(value, ordinality)
  union all
  select p_disease_id, 'fil', (sign.ordinality - 1)::smallint, trim(sign.value)
  from jsonb_array_elements_text(p_fil_signs) with ordinality as sign(value, ordinality);

  insert into public.disease_references (disease_id, language_tag, position, publisher, title, url)
  values
    (p_disease_id, 'en', 0, p_en_reference ->> 'publisher', p_en_reference ->> 'title', p_en_reference ->> 'url'),
    (p_disease_id, 'fil', 0, p_fil_reference ->> 'publisher', p_fil_reference ->> 'title', p_fil_reference ->> 'url')
  on conflict (disease_id, language_tag, position) do update set
    publisher = excluded.publisher, title = excluded.title, url = excluded.url;

  update public.disease_catalog
    set content_version = content_version + 1
    where id = p_disease_id;
  get diagnostics affected = row_count;
  if affected <> 1 then raise exception using errcode = 'P0002', message = 'Disease catalog entry not found'; end if;

  update public.app_config set catalog_version = catalog_version + 1 where id = true;
  insert into public.moderation_actions (
    admin_id,
    resource_type,
    resource_key,
    action,
    reason
  ) values (
    p_admin_id,
    'disease_catalog',
    p_disease_id,
    'catalog_content_updated',
    'Bilingual educational content and symptoms updated'
  );
end;
$$;

create or replace function public.set_cloud_writes_enabled(
  p_enabled boolean,
  p_admin_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  affected integer;
begin
  if (select auth.role()) <> 'service_role'
    or not exists (select 1 from public.admin_members where user_id = p_admin_id) then
    raise insufficient_privilege using message = 'authorized admin required';
  end if;
  update public.app_config
  set cloud_writes_enabled = p_enabled
  where id = true and cloud_writes_enabled is distinct from p_enabled;
  get diagnostics affected = row_count;
  if affected = 1 then
    insert into public.moderation_actions (
      admin_id,
      resource_type,
      resource_key,
      action,
      reason
    ) values (
      p_admin_id,
      'app_config',
      'cloud_writes_enabled',
      case when p_enabled then 'cloud_writes_enabled' else 'cloud_writes_paused' end,
      'Production write kill switch changed'
    );
  end if;
  return true;
end;
$$;

revoke all on function public.reserve_global_share_intent(uuid, uuid, text, numeric, text, text, text, text, text) from public, anon, authenticated;
revoke all on function public.create_scan_contribution_with_quota(uuid, uuid, text, numeric, text, text, text) from public, anon, authenticated;
revoke all on function public.set_sharing_consent(uuid, boolean, integer) from public, anon, authenticated;
revoke all on function public.report_scan_contribution(uuid, uuid, text, text, text) from public, anon, authenticated;
revoke all on function public.create_disease_request_with_quota(uuid, uuid, text, text, text, boolean, boolean, text[], text) from public, anon, authenticated;
revoke all on function public.review_disease_request(uuid, text, text, uuid) from public, anon, authenticated;
revoke all on function public.moderate_scan_contribution(uuid, text, text, uuid) from public, anon, authenticated;
revoke all on function public.update_disease_catalog_content(text, jsonb, jsonb, jsonb, jsonb, jsonb, jsonb, uuid) from public, anon, authenticated;
revoke all on function public.set_cloud_writes_enabled(boolean, uuid) from public, anon, authenticated;

grant execute on function public.reserve_global_share_intent(uuid, uuid, text, numeric, text, text, text, text, text) to service_role;
grant execute on function public.create_scan_contribution_with_quota(uuid, uuid, text, numeric, text, text, text) to service_role;
grant execute on function public.set_sharing_consent(uuid, boolean, integer) to service_role;
grant execute on function public.report_scan_contribution(uuid, uuid, text, text, text) to service_role;
grant execute on function public.create_disease_request_with_quota(uuid, uuid, text, text, text, boolean, boolean, text[], text) to service_role;
grant execute on function public.review_disease_request(uuid, text, text, uuid) to service_role;
grant execute on function public.moderate_scan_contribution(uuid, text, text, uuid) to service_role;
grant execute on function public.update_disease_catalog_content(text, jsonb, jsonb, jsonb, jsonb, jsonb, jsonb, uuid) to service_role;
grant execute on function public.set_cloud_writes_enabled(boolean, uuid) to service_role;
