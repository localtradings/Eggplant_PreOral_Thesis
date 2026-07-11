-- Keep the reason/count needed for moderation while allowing a privacy deletion
-- to remove the reporter identity and optional user-authored details.
alter table public.content_reports
  alter column reporter_id drop not null,
  add column reporter_anonymized_at timestamptz;

alter table public.content_reports
  add constraint content_reports_reporter_state_check
  check (
    (reporter_id is not null and reporter_anonymized_at is null)
    or
    (reporter_id is null and reporter_anonymized_at is not null and details is null)
  );
