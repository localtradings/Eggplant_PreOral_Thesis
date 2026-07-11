create schema if not exists app_private;
revoke all on schema app_private from public, anon, authenticated;

create table public.installations (
  owner_id uuid primary key references auth.users(id) on delete cascade,
  sharing_enabled boolean not null default false,
  consent_version integer,
  consented_at timestamptz,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

create table public.disease_catalog (
  id text primary key,
  model_class_index integer not null unique,
  model_label text not null unique,
  category text not null check (category in ('LEAF_DISEASE', 'FRUIT_DISEASE')),
  artwork_key text not null,
  content_version integer not null default 1,
  updated_at timestamptz not null default now()
);

create table public.disease_localizations (
  disease_id text not null references public.disease_catalog(id) on delete restrict,
  language_tag text not null check (language_tag in ('en', 'fil')),
  name text not null,
  description text not null,
  symptom_preview text not null,
  causes text not null,
  prevention text not null,
  guidance text not null,
  when_to_act text not null,
  disclaimer text not null,
  updated_at timestamptz not null default now(),
  primary key (disease_id, language_tag)
);

create table public.disease_signs (
  disease_id text not null references public.disease_catalog(id) on delete cascade,
  language_tag text not null check (language_tag in ('en', 'fil')),
  position smallint not null check (position >= 0),
  text text not null,
  primary key (disease_id, language_tag, position)
);

create table public.disease_references (
  disease_id text not null references public.disease_catalog(id) on delete cascade,
  language_tag text not null check (language_tag in ('en', 'fil')),
  position smallint not null check (position >= 0),
  publisher text not null,
  title text not null,
  url text not null check (url ~ '^https://'),
  primary key (disease_id, language_tag, position)
);

create table public.scan_contributions (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  client_scan_id uuid not null,
  disease_id text not null references public.disease_catalog(id) on delete restrict,
  confidence numeric(5,4) not null check (confidence >= 0.5 and confidence <= 1),
  source text not null check (source in ('live', 'capture')),
  model_version text not null,
  photo_path text not null,
  status text not null default 'published' check (status in ('published', 'quarantined', 'removed', 'expired')),
  published_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '180 days'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (owner_id, client_scan_id)
);

create index scan_contributions_public_feed_idx
  on public.scan_contributions (published_at desc, id desc)
  where status = 'published';
create index scan_contributions_ranking_idx
  on public.scan_contributions (disease_id, published_at desc)
  where status in ('published', 'expired');
create index scan_contributions_owner_idx
  on public.scan_contributions (owner_id, created_at desc);

create table public.global_ranking_ledger (
  contribution_id uuid primary key,
  disease_id text not null references public.disease_catalog(id) on delete restrict,
  published_at timestamptz not null
);
create index global_ranking_ledger_disease_idx on public.global_ranking_ledger (disease_id, published_at desc);

create table public.disease_requests (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  client_request_id uuid not null,
  requested_name text not null check (char_length(trim(requested_name)) between 2 and 120),
  notes text check (notes is null or char_length(notes) <= 2000),
  model_version text not null,
  rights_consent boolean not null check (rights_consent),
  training_consent boolean not null default false check (not training_consent),
  status text not null default 'upload_pending' check (status in ('upload_pending', 'submitted', 'under_review', 'planned', 'needs_information', 'not_supported', 'closed')),
  admin_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (owner_id, client_request_id)
);

create index disease_requests_owner_idx on public.disease_requests (owner_id, created_at desc);
create index disease_requests_queue_idx on public.disease_requests (status, created_at asc);

create table public.disease_request_photos (
  id uuid primary key default gen_random_uuid(),
  request_id uuid not null references public.disease_requests(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete cascade,
  position smallint not null check (position between 0 and 2),
  object_path text not null,
  created_at timestamptz not null default now(),
  unique (request_id, position)
);

create table public.content_reports (
  id uuid primary key default gen_random_uuid(),
  contribution_id uuid not null references public.scan_contributions(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reason text not null check (reason in ('incorrect_result', 'not_eggplant', 'inappropriate', 'duplicate', 'other')),
  details text check (details is null or char_length(details) <= 1000),
  created_at timestamptz not null default now(),
  unique (contribution_id, reporter_id)
);

create index content_reports_contribution_idx on public.content_reports (contribution_id, created_at desc);

create table public.admin_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  role text not null check (role in ('owner', 'admin', 'reviewer')),
  created_at timestamptz not null default now()
);

create table public.moderation_actions (
  id bigint generated always as identity primary key,
  contribution_id uuid references public.scan_contributions(id) on delete set null,
  request_id uuid references public.disease_requests(id) on delete set null,
  admin_id uuid references auth.users(id) on delete set null,
  action text not null,
  reason text,
  created_at timestamptz not null default now(),
  check (contribution_id is not null or request_id is not null)
);

create table public.app_config (
  id boolean primary key default true check (id),
  cloud_writes_enabled boolean not null default false,
  catalog_version integer not null default 1,
  updated_at timestamptz not null default now()
);
insert into public.app_config (id) values (true) on conflict (id) do nothing;

create table public.deletion_requests (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'queued' check (status in ('queued', 'processing', 'completed', 'failed')),
  created_at timestamptz not null default now(),
  completed_at timestamptz
);
create unique index deletion_requests_active_owner_idx on public.deletion_requests (owner_id)
  where status in ('queued', 'processing');

create or replace function app_private.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.admin_members where user_id = (select auth.uid())
  );
$$;
revoke all on function app_private.is_admin() from public;
grant execute on function app_private.is_admin() to authenticated;

create or replace function app_private.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger disease_catalog_updated_at before update on public.disease_catalog
for each row execute function app_private.set_updated_at();
create trigger disease_localizations_updated_at before update on public.disease_localizations
for each row execute function app_private.set_updated_at();
create trigger scan_contributions_updated_at before update on public.scan_contributions
for each row execute function app_private.set_updated_at();
create trigger disease_requests_updated_at before update on public.disease_requests
for each row execute function app_private.set_updated_at();
create trigger app_config_updated_at before update on public.app_config
for each row execute function app_private.set_updated_at();

create or replace function app_private.quarantine_reported_contribution()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (
    select count(*) >= 2
    from public.content_reports
    where contribution_id = new.contribution_id
  ) then
    update public.scan_contributions
      set status = 'quarantined'
      where id = new.contribution_id and status = 'published';
  end if;
  return new;
end;
$$;
revoke all on function app_private.quarantine_reported_contribution() from public, anon, authenticated;
create trigger quarantine_after_reports after insert on public.content_reports
for each row execute function app_private.quarantine_reported_contribution();

create or replace function app_private.record_global_ranking()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.global_ranking_ledger (contribution_id, disease_id, published_at)
  values (new.id, new.disease_id, new.published_at)
  on conflict (contribution_id) do nothing;
  return new;
end;
$$;
revoke all on function app_private.record_global_ranking() from public, anon, authenticated;
create trigger record_global_ranking after insert on public.scan_contributions
for each row when (new.status = 'published') execute function app_private.record_global_ranking();

create view public.global_scan_feed with (security_invoker = true) as
select
  contribution.id,
  contribution.client_scan_id,
  contribution.disease_id,
  contribution.confidence,
  contribution.source,
  contribution.model_version,
  contribution.photo_path,
  contribution.published_at,
  contribution.expires_at
from public.scan_contributions contribution
where contribution.status = 'published' and contribution.expires_at > now();

create view public.global_disease_rankings with (security_invoker = true) as
select disease_id, count(*)::bigint as scan_count, max(published_at) as latest_scan_at
from public.global_ranking_ledger
group by disease_id;

alter table public.installations enable row level security;
alter table public.disease_catalog enable row level security;
alter table public.disease_localizations enable row level security;
alter table public.disease_signs enable row level security;
alter table public.disease_references enable row level security;
alter table public.scan_contributions enable row level security;
alter table public.disease_requests enable row level security;
alter table public.disease_request_photos enable row level security;
alter table public.content_reports enable row level security;
alter table public.admin_members enable row level security;
alter table public.moderation_actions enable row level security;
alter table public.app_config enable row level security;
alter table public.deletion_requests enable row level security;
alter table public.global_ranking_ledger enable row level security;

create policy installations_owner_select on public.installations for select to authenticated
using ((select auth.uid()) = owner_id);

create policy catalog_read on public.disease_catalog for select to authenticated using (true);
create policy localization_read on public.disease_localizations for select to authenticated using (true);
create policy signs_read on public.disease_signs for select to authenticated using (true);
create policy references_read on public.disease_references for select to authenticated using (true);
create policy catalog_admin_all on public.disease_catalog for all to authenticated using ((select app_private.is_admin())) with check ((select app_private.is_admin()));
create policy localization_admin_all on public.disease_localizations for all to authenticated using ((select app_private.is_admin())) with check ((select app_private.is_admin()));
create policy signs_admin_all on public.disease_signs for all to authenticated using ((select app_private.is_admin())) with check ((select app_private.is_admin()));
create policy references_admin_all on public.disease_references for all to authenticated using ((select app_private.is_admin())) with check ((select app_private.is_admin()));

create policy contribution_owner_select on public.scan_contributions for select to authenticated
using ((select auth.uid()) = owner_id or (select app_private.is_admin()));

create policy request_owner_select on public.disease_requests for select to authenticated
using ((select auth.uid()) = owner_id or (select app_private.is_admin()));
create policy request_admin_update on public.disease_requests for update to authenticated
using ((select app_private.is_admin())) with check ((select app_private.is_admin()));
create policy request_photo_owner_select on public.disease_request_photos for select to authenticated
using ((select auth.uid()) = owner_id or (select app_private.is_admin()));

create policy report_owner_select on public.content_reports for select to authenticated
using ((select auth.uid()) = reporter_id or (select app_private.is_admin()));
create policy admin_members_admin_select on public.admin_members for select to authenticated
using ((select app_private.is_admin()));
create policy moderation_admin_all on public.moderation_actions for all to authenticated
using ((select app_private.is_admin())) with check ((select app_private.is_admin()));
create policy config_read on public.app_config for select to authenticated using (true);
create policy config_admin_update on public.app_config for update to authenticated
using ((select app_private.is_admin())) with check ((select app_private.is_admin()));
create policy deletion_owner_select on public.deletion_requests for select to authenticated
using ((select auth.uid()) = owner_id or (select app_private.is_admin()));
create policy deletion_admin_update on public.deletion_requests for update to authenticated
using ((select app_private.is_admin())) with check ((select app_private.is_admin()));

grant usage on schema public to authenticated;
grant select on public.disease_catalog, public.disease_localizations, public.disease_signs,
  public.disease_references, public.app_config to authenticated;
grant select on public.installations, public.scan_contributions, public.disease_requests,
  public.disease_request_photos, public.content_reports to authenticated;
grant select on public.admin_members, public.moderation_actions, public.deletion_requests to authenticated;
grant select on public.global_scan_feed, public.global_disease_rankings to authenticated;

create or replace function public.admin_storage_usage()
returns bigint
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(sum(coalesce((metadata ->> 'size')::bigint, 0)), 0)::bigint
  from storage.objects
  where bucket_id = 'eggplant-scans'
    and ((select auth.role()) = 'service_role' or (select app_private.is_admin()));
$$;
revoke all on function public.admin_storage_usage() from public, anon;
grant execute on function public.admin_storage_usage() to authenticated, service_role;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('eggplant-scans', 'eggplant-scans', false, 8388608, array['image/jpeg'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy eggplant_storage_owner_insert on storage.objects for insert to authenticated
with check (
  bucket_id = 'eggplant-scans'
  and (storage.foldername(name))[2] = (select auth.uid())::text
  and (storage.foldername(name))[1] in ('global', 'requests')
);
create policy eggplant_storage_owner_select on storage.objects for select to authenticated
using (
  bucket_id = 'eggplant-scans'
  and (owner_id = (select auth.uid())::text or (select app_private.is_admin()))
);
create policy eggplant_storage_owner_delete on storage.objects for delete to authenticated
using (
  bucket_id = 'eggplant-scans'
  and (owner_id = (select auth.uid())::text or (select app_private.is_admin()))
);

insert into public.disease_catalog (id, model_class_index, model_label, category, artwork_key) values
  ('fruit-rot', 0, 'Fruit_Rot', 'FRUIT_DISEASE', 'fruit-rot'),
  ('fruit-borer', 1, 'Fruit_borer', 'FRUIT_DISEASE', 'fruit-borer'),
  ('insect-pest', 4, 'Insect-Pest', 'LEAF_DISEASE', 'insect-pest'),
  ('leaf-spot', 5, 'Leaf-Spot', 'LEAF_DISEASE', 'leaf-spot'),
  ('melon-thrips', 6, 'Melon_Thrips', 'LEAF_DISEASE', 'melon-thrips'),
  ('mosaic-virus', 7, 'Mosaic', 'LEAF_DISEASE', 'mosaic-virus'),
  ('white-molds', 8, 'White-Mold', 'LEAF_DISEASE', 'white-molds'),
  ('wilt', 9, 'Wilt', 'LEAF_DISEASE', 'wilt')
on conflict (id) do nothing;
