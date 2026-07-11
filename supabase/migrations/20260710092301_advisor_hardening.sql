revoke execute on function public.admin_storage_usage() from authenticated;

drop policy if exists catalog_admin_all on public.disease_catalog;
drop policy if exists localization_admin_all on public.disease_localizations;
drop policy if exists signs_admin_all on public.disease_signs;
drop policy if exists references_admin_all on public.disease_references;

create policy global_ranking_ledger_admin_select on public.global_ranking_ledger
for select to authenticated using ((select app_private.is_admin()));

create index if not exists content_reports_reporter_idx on public.content_reports (reporter_id);
create index if not exists disease_request_photos_owner_idx on public.disease_request_photos (owner_id);
create index if not exists moderation_actions_admin_idx on public.moderation_actions (admin_id);
create index if not exists moderation_actions_contribution_idx on public.moderation_actions (contribution_id);
create index if not exists moderation_actions_request_idx on public.moderation_actions (request_id);
