# Deployment guide: Neon + Render + Vercel

Order matters because Render needs the Vercel URL and Vercel needs the Render URL. Follow the steps in order; step 6 closes the loop.

## 1. Neon (database)

1. Create a project at https://neon.tech. In the **SQL Editor** run (once):
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   CREATE EXTENSION IF NOT EXISTS hstore;
   CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
   ```
2. Open **Connection details**, turn **Connection pooling OFF** (use the direct host; the app already pools) and note host, database, user, password.
3. Build the JDBC URL. Neon shows `postgresql://user:pass@HOST/db?sslmode=require`; Spring needs:
   ```
   DATABASE_URL=jdbc:postgresql://HOST/db?sslmode=require
   DATABASE_USERNAME=user
   DATABASE_PASSWORD=pass
   ```
   (username/password go in their own variables, **not** in the URL.)

## 2. Gemini key

Create a key at https://aistudio.google.com/apikey and keep it as `GEMINI_API_KEY`.

## 3. Generate the encryption values

```
TOKEN_ENCRYPTOR_PASSWORD = any long random string
TOKEN_ENCRYPTOR_SALT     = output of: openssl rand -hex 16     (must be HEX)
```
Never change these after users have signed in, or their stored GitHub tokens become unreadable (they will simply be asked to sign in again).

## 4. Render (Spring Boot API)

1. Push the repo to GitHub. In Render: **New + > Blueprint**, select the repo (it reads `render.yaml`), or **New + > Web Service > Docker** with *Root Directory* `server`.
2. Health check path: `/actuator/health/liveness` (already in `render.yaml`).
3. Fill the environment variables (table below). For `FRONTEND_URL` / `CORS_ALLOWED_ORIGINS` enter a placeholder for now.
4. Deploy and note the URL, e.g. `https://springlensai-server.onrender.com`. Visiting `/api/health` should return `{"status":"ok"}`.

| Variable | Value |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | from step 1 |
| `GEMINI_API_KEY` | step 2 |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | step 5 |
| `TOKEN_ENCRYPTOR_PASSWORD`, `TOKEN_ENCRYPTOR_SALT` | step 3 |
| `FRONTEND_URL` | your Vercel URL, no trailing slash |
| `CORS_ALLOWED_ORIGINS` | same as `FRONTEND_URL` |
| `COOKIE_SECURE` | `true` |
| optional: `GEMINI_CHAT_MODEL`, `GEMINI_EMBEDDING_MODEL`, `INDEX_MAX_FILES`, `JAVA_OPTS` | see `server/.env.example` |

## 5. GitHub OAuth App (create one for production, one for local)

https://github.com/settings/developers > New OAuth App:

- Homepage URL: your Vercel URL
- **Authorization callback URL: `https://YOUR-VERCEL-URL/login/oauth2/code/github`**

The callback is on the **Vercel** domain (Next.js forwards it to Render). Copy the client id/secret into Render.

## 6. Vercel (Next.js)

1. **Add New > Project**, import the repo, set **Root Directory = `client`**. Framework is auto-detected.
2. Environment variables:
   - `BACKEND_URL` = your Render URL (no trailing slash)
   - `NEXT_PUBLIC_SITE_URL` = your final Vercel/custom domain URL
3. Deploy. Then go back to Render and set `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` to the real Vercel URL, and make sure the GitHub OAuth callback uses the same URL. Render redeploys automatically.

Changing `BACKEND_URL` needs a Vercel **redeploy** (it is read at build time).

## 7. Smoke test

1. `https://YOUR-VERCEL-URL/api/health` returns `{"status":"ok"}` (proves the proxy works).
2. Sign in with GitHub, you land on the dashboard with your repos.
3. Index a small repo, wait for READY, open it and ask a question.

## Things to know on free plans

- **Render free sleeps after 15 min.** The first request after sleep takes ~30-60 s. The landing page pre-warms the API and the app shows "waking up the server". Upgrade to Starter to remove sleep. Sessions are stored in Postgres, so users stay signed in across restarts.
- **512 MB RAM.** The Dockerfile uses memory-friendly JVM flags. If you see out-of-memory restarts, upgrade the plan.
- **Gemini free tier is rate-limited.** Indexing retries with backoff; large repos may be slow. `INDEX_MAX_FILES` (default 400) caps work per repo.
- **Neon scales to zero.** First query after idle takes ~1 s. Sessions are cleaned hourly (not every minute) so the database can sleep.
- Vercel proxies streaming responses; answers are capped at 120 s.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Render crashes: "Could not resolve placeholder" | A required env var is missing (the message names it) |
| "Unable to open JDBC Connection" / SSL error | `DATABASE_URL` must start with `jdbc:postgresql://` and end with `?sslmode=require` |
| "Salt must be a hex string" style error at startup | `TOKEN_ENCRYPTOR_SALT` is not hex; use `openssl rand -hex 16` |
| GitHub says "redirect_uri is not associated" | Callback must be exactly `https://VERCEL-URL/login/oauth2/code/github` |
| Login loops back to /login | `COOKIE_SECURE` must be `true` on Render (HTTPS) and `FRONTEND_URL` must equal the URL in the browser bar |
| `/api/health` 404 on Vercel | `BACKEND_URL` missing/wrong at build time; fix and redeploy |
| Indexing fails with 404 from Gemini | Model name wrong; defaults are `gemini-3.8-flash` and `gemini-embedding-001` (`text-embedding-004` is shut down) |
| Indexing fails with "rate-limited" | Gemini free quota; retry later or lower `INDEX_MAX_FILES` |
| Repo stuck on "Indexing" | Can't happen after a restart: interrupted jobs are marked FAILED automatically; click Retry |
