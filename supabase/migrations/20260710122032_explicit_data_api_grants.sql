revoke all privileges on all tables in schema public from anon, authenticated;
revoke all privileges on all sequences in schema public from anon, authenticated;
revoke all privileges on all functions in schema public from anon, authenticated;

grant usage on schema public to authenticated;
grant select on public.disease_catalog, public.disease_localizations, public.disease_signs,
  public.disease_references, public.app_config to authenticated;
grant select on public.installations, public.scan_contributions, public.disease_requests,
  public.disease_request_photos, public.content_reports, public.admin_members,
  public.moderation_actions, public.deletion_requests to authenticated;

grant execute on function app_private.is_admin() to authenticated;
grant execute on function public.admin_storage_usage() to service_role;
