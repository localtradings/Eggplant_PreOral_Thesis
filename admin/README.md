# Eggplant Disease Detector Admin

Private Next.js administration and mobile API service for the Eggplant Disease Detector. The dashboard reads live Supabase data, protects every administrative page and mutation with both Supabase authentication and `admin_members` authorization, and never substitutes placeholder production metrics.

## Runtime configuration

Configure these values in Vercel encrypted environment variables. Do not commit them:

- `NEXT_PUBLIC_SUPABASE_URL` — public Supabase project URL.
- `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` — public publishable key used by browser authentication.
- `SUPABASE_SECRET_KEY` — server-only secret key used by protected route handlers and the admin data layer.
- `CRON_SECRET` — secret bearer token used by the daily retention/deletion job.

The service starts safely with cloud writes disabled in `app_config`. Read APIs and the offline-first Android client continue to work while writes are paused.

## Local verification

Use Node.js 22 or later:

```bash
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

Run locally with configured environment variables:

```bash
npm run dev
```

The health endpoint is `GET /api/health`. Mobile endpoints require a valid Supabase anonymous-user bearer token. Administrative routes require a magic-link session whose user ID exists in `admin_members`.

## Deployment

The Vercel project root directory is `admin/`. `vercel.json` schedules the daily maintenance route, which:

- immediately keeps expired photos out of the public feed and retries object cleanup;
- processes scoped user cloud-deletion requests;
- records failed work instead of returning a false success.

Promote a deployment only after authentication, authorization, Storage, feed, reporting, deletion, lint, type-check, test, build, and runtime-header smoke tests pass.
