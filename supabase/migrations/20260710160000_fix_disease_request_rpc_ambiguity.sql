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
    or p_photo_hashes is null
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
  from public.disease_requests disease_request
  where disease_request.owner_id = p_owner_id
    and disease_request.client_request_id = p_client_request_id;
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
    select array_agg(photo.object_path order by photo.position) into recorded_paths
    from public.disease_request_photos photo
    where photo.request_id = existing.id and photo.owner_id = p_owner_id;
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

  select coalesce(rate_limit.request_count, 0) into ip_count
  from public.api_rate_limits rate_limit
  where rate_limit.action = 'disease_request'
    and rate_limit.window_start = current_date
    and rate_limit.subject_hash = p_rate_subject;
  if (select count(*) from public.disease_requests disease_request
      where disease_request.owner_id = p_owner_id
        and disease_request.created_at >= now() - interval '24 hours') >= 5
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

revoke all on function public.create_disease_request_with_quota(uuid, uuid, text, text, text, boolean, boolean, text[], text)
  from public, anon, authenticated;
grant execute on function public.create_disease_request_with_quota(uuid, uuid, text, text, text, boolean, boolean, text[], text)
  to service_role;
