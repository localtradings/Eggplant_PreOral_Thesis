-- Idempotent admin mutations. The existing RPCs remain available for backwards
-- compatibility; the v2 functions are the only functions used by the v1.5.1 UI.

alter table public.disease_catalog
  add column if not exists content_hash text
    check (content_hash is null or content_hash ~ '^[a-f0-9]{64}$');

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
    or p_action not in ('catalog_publish', 'request_review', 'scan_moderation')
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

create or replace function app_private.complete_admin_action(
  p_admin_id uuid,
  p_action text,
  p_resource_key text,
  p_idempotency_key uuid,
  p_outcome text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.admin_action_receipts
  set outcome = p_outcome
  where admin_id = p_admin_id
    and action = p_action
    and resource_key = p_resource_key
    and idempotency_key = p_idempotency_key;
end;
$$;
revoke all on function app_private.complete_admin_action(uuid, text, text, uuid, text) from public, anon, authenticated;

create or replace function public.review_disease_request_v2(
  p_request_id uuid,
  p_status text,
  p_admin_note text,
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
  request_row public.disease_requests%rowtype;
  claim text;
  normalized_note text := nullif(trim(coalesce(p_admin_note, '')), '');
  prior_outcome text;
begin
  if p_status not in ('under_review', 'planned', 'needs_information', 'not_supported', 'closed')
    or char_length(coalesce(p_admin_note, '')) > 2000 then
    raise exception using errcode = '22023', message = 'invalid request review';
  end if;
  claim := app_private.claim_admin_action(p_admin_id, 'request_review', p_request_id::text, p_idempotency_key, p_payload_hash);
  if claim = 'conflict' then
    raise exception using errcode = '23505', message = 'idempotency key conflicts with another review';
  end if;
  if claim = 'replay' then
    select outcome into prior_outcome from public.admin_action_receipts
    where admin_id = p_admin_id and action = 'request_review'
      and resource_key = p_request_id::text and idempotency_key = p_idempotency_key;
    return coalesce(prior_outcome, 'unchanged');
  end if;

  select * into request_row from public.disease_requests where id = p_request_id for update;
  if not found then
    perform app_private.complete_admin_action(p_admin_id, 'request_review', p_request_id::text, p_idempotency_key, 'unchanged');
    return 'missing';
  end if;
  if request_row.status = p_status and request_row.admin_note is not distinct from normalized_note then
    perform app_private.complete_admin_action(p_admin_id, 'request_review', p_request_id::text, p_idempotency_key, 'unchanged');
    return 'unchanged';
  end if;

  update public.disease_requests set status = p_status, admin_note = normalized_note where id = p_request_id;
  insert into public.moderation_actions (request_id, request_subject_id, admin_id, action, reason)
  values (p_request_id, p_request_id, p_admin_id, 'request_' || p_status, normalized_note);
  perform app_private.complete_admin_action(p_admin_id, 'request_review', p_request_id::text, p_idempotency_key, 'applied');
  return 'applied';
end;
$$;

create or replace function public.moderate_scan_contribution_v2(
  p_contribution_id uuid,
  p_status text,
  p_reason text,
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
  contribution public.scan_contributions%rowtype;
  claim text;
  normalized_reason text := nullif(trim(coalesce(p_reason, '')), '');
  prior_outcome text;
begin
  if p_status not in ('published', 'quarantined', 'removed')
    or char_length(coalesce(p_reason, '')) > 500 then
    raise exception using errcode = '22023', message = 'invalid moderation action';
  end if;
  claim := app_private.claim_admin_action(p_admin_id, 'scan_moderation', p_contribution_id::text, p_idempotency_key, p_payload_hash);
  if claim = 'conflict' then
    raise exception using errcode = '23505', message = 'idempotency key conflicts with another moderation action';
  end if;
  if claim = 'replay' then
    select outcome into prior_outcome from public.admin_action_receipts
    where admin_id = p_admin_id and action = 'scan_moderation'
      and resource_key = p_contribution_id::text and idempotency_key = p_idempotency_key;
    return coalesce(prior_outcome, 'unchanged');
  end if;

  select * into contribution from public.scan_contributions where id = p_contribution_id for update;
  if not found then
    perform app_private.complete_admin_action(p_admin_id, 'scan_moderation', p_contribution_id::text, p_idempotency_key, 'unchanged');
    return 'missing';
  end if;
  if contribution.status = p_status then
    perform app_private.complete_admin_action(p_admin_id, 'scan_moderation', p_contribution_id::text, p_idempotency_key, 'unchanged');
    return 'unchanged';
  end if;
  if p_status = 'published' and not exists (
    select 1 from storage.objects
    where bucket_id = 'eggplant-scans' and name = contribution.photo_path
  ) then
    raise exception using errcode = '23514', message = 'published contribution photo is unavailable';
  end if;

  update public.scan_contributions set status = p_status where id = p_contribution_id;
  insert into public.moderation_actions (contribution_id, contribution_subject_id, admin_id, action, reason)
  values (p_contribution_id, p_contribution_id, p_admin_id, p_status, normalized_reason);
  perform app_private.complete_admin_action(p_admin_id, 'scan_moderation', p_contribution_id::text, p_idempotency_key, 'applied');
  return 'applied';
end;
$$;

create or replace function public.update_disease_catalog_content_v2(
  p_disease_id text,
  p_en_content jsonb,
  p_fil_content jsonb,
  p_en_signs jsonb,
  p_fil_signs jsonb,
  p_en_reference jsonb,
  p_fil_reference jsonb,
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
  affected integer;
  claim text;
  prior_outcome text;
  catalog public.disease_catalog%rowtype;
begin
  if jsonb_typeof(p_en_signs) <> 'array'
    or jsonb_typeof(p_fil_signs) <> 'array'
    or jsonb_array_length(p_en_signs) not between 1 and 20
    or jsonb_array_length(p_fil_signs) not between 1 and 20
    or exists (select 1 from jsonb_array_elements_text(p_en_signs) as sign(value) where char_length(trim(sign.value)) not between 2 and 500)
    or exists (select 1 from jsonb_array_elements_text(p_fil_signs) as sign(value) where char_length(trim(sign.value)) not between 2 and 500) then
    raise exception using errcode = '22023', message = 'invalid bilingual symptoms';
  end if;
  claim := app_private.claim_admin_action(p_admin_id, 'catalog_publish', p_disease_id, p_idempotency_key, p_payload_hash);
  if claim = 'conflict' then
    raise exception using errcode = '23505', message = 'idempotency key conflicts with another catalog update';
  end if;
  if claim = 'replay' then
    select outcome into prior_outcome from public.admin_action_receipts
    where admin_id = p_admin_id and action = 'catalog_publish'
      and resource_key = p_disease_id and idempotency_key = p_idempotency_key;
    return coalesce(prior_outcome, 'unchanged');
  end if;

  select * into catalog from public.disease_catalog where id = p_disease_id for update;
  if not found then
    perform app_private.complete_admin_action(p_admin_id, 'catalog_publish', p_disease_id, p_idempotency_key, 'unchanged');
    return 'missing';
  end if;
  if catalog.content_hash = p_payload_hash then
    perform app_private.complete_admin_action(p_admin_id, 'catalog_publish', p_disease_id, p_idempotency_key, 'unchanged');
    return 'unchanged';
  end if;

  update public.disease_localizations set
    name = p_en_content ->> 'name', description = p_en_content ->> 'description', symptom_preview = p_en_content ->> 'symptom_preview',
    causes = p_en_content ->> 'causes', recommended_action = p_en_content ->> 'recommended_action', prevention = p_en_content ->> 'prevention', guidance = p_en_content ->> 'guidance',
    when_to_act = p_en_content ->> 'when_to_act', disclaimer = p_en_content ->> 'disclaimer'
  where disease_id = p_disease_id and language_tag = 'en';
  get diagnostics affected = row_count;
  if affected <> 1 then raise exception using errcode = 'P0002', message = 'English content not found'; end if;
  update public.disease_localizations set
    name = p_fil_content ->> 'name', description = p_fil_content ->> 'description', symptom_preview = p_fil_content ->> 'symptom_preview',
    causes = p_fil_content ->> 'causes', recommended_action = p_fil_content ->> 'recommended_action', prevention = p_fil_content ->> 'prevention', guidance = p_fil_content ->> 'guidance',
    when_to_act = p_fil_content ->> 'when_to_act', disclaimer = p_fil_content ->> 'disclaimer'
  where disease_id = p_disease_id and language_tag = 'fil';
  get diagnostics affected = row_count;
  if affected <> 1 then raise exception using errcode = 'P0002', message = 'Filipino content not found'; end if;

  delete from public.disease_signs where disease_id = p_disease_id and language_tag in ('en', 'fil');
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
  update public.disease_catalog set content_version = content_version + 1, content_hash = p_payload_hash where id = p_disease_id;
  update public.app_config set catalog_version = catalog_version + 1 where id = true;
  insert into public.moderation_actions (admin_id, resource_type, resource_key, action, reason)
  values (p_admin_id, 'disease_catalog', p_disease_id, 'catalog_content_updated', 'Bilingual educational content and symptoms updated');
  perform app_private.complete_admin_action(p_admin_id, 'catalog_publish', p_disease_id, p_idempotency_key, 'applied');
  return 'applied';
end;
$$;

revoke all on function public.review_disease_request_v2(uuid, text, text, uuid, uuid, text) from public, anon, authenticated;
revoke all on function public.moderate_scan_contribution_v2(uuid, text, text, uuid, uuid, text) from public, anon, authenticated;
revoke all on function public.update_disease_catalog_content_v2(text, jsonb, jsonb, jsonb, jsonb, jsonb, jsonb, uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.review_disease_request_v2(uuid, text, text, uuid, uuid, text) to service_role;
grant execute on function public.moderate_scan_contribution_v2(uuid, text, text, uuid, uuid, text) to service_role;
grant execute on function public.update_disease_catalog_content_v2(text, jsonb, jsonb, jsonb, jsonb, jsonb, jsonb, uuid, uuid, text) to service_role;
