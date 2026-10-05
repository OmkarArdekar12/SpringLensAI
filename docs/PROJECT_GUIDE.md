# SpringLens AI: project guide

Legend for the "vs tutorial" column: **SAME** = same as devPilot (only the name differs) · **SIMILAR** = same idea/structure, small changes · **CHANGED** = rewritten or extended on purpose (reason given) · **NEW** = not in the tutorial · **YOURS** = your original file, kept as is.

## 1. Spring Boot in 5 minutes (for React developers)

| Spring Boot idea | React/Next.js analogy |
|---|---|
| `@SpringBootApplication` main class | `app/layout.tsx` + the server entry point |
| `@RestController` + `@GetMapping` | a Next.js route handler (`app/api/.../route.ts`) |
| `@Service` classes | plain modules/hooks holding business logic |
| `@Entity` | a database table described as a class (like a Prisma model) |
| `JpaRepository` interface | an auto-generated data-access object (you only write method *names*; Spring writes the SQL) |
| DTO `record` | a TypeScript `type` for what the API sends/receives |
| Dependency injection (`@RequiredArgsConstructor` + `private final X x`) | like React context/providers: you *declare* what you need and Spring passes it in |
| `@Configuration` + `@Bean` | creating shared singletons (e.g. a QueryClient) in one place |
| `application.properties` | `.env` + `next.config.ts`; `${NAME:default}` reads an env var with a default |
| `@Async` | fire-and-forget background job on another thread |
| Lombok (`@Getter`, `@Builder`...) | generates boilerplate at compile time so classes stay short |

Request path: **Controller** (HTTP in/out) → **Service** (logic) → **Repository** (database). Controllers never touch the database directly.

## 2. How the pieces work together

**Login.** Browser opens `/oauth2/authorization/github` (Next.js forwards it to Spring) → GitHub → GitHub redirects to `/login/oauth2/code/github` on the *frontend* domain (forwarded to Spring) → Spring exchanges the code, saves the user and the **encrypted** GitHub token, creates a session (stored in Postgres) → redirects to `/auth/callback` → the page calls `/api/auth/me` and opens the dashboard.

**Indexing (RAG step 1).** `POST /api/repos/{id}/index` marks the repo INDEXING and returns `202` immediately; a background thread then: lists files (one GitHub tree call) → filters useful files → downloads each → splits into ~800-character, line-aligned chunks → embeds them with Gemini → stores vectors in pgvector, tagged with `repoId`. The dashboard polls and shows progress.

**Chat (RAG step 2).** `POST /api/chat/sessions/{id}/messages` → embed the question → fetch the 8 most similar chunks of *that repo* → build a prompt (rules + recent conversation + code + question) → stream Gemini's answer as Server-Sent Events (`user_message`, `token`…, `assistant_message`, `done`/`error`) → save the answer with its citations.

**Why the rewrites?** Vercel and Render are different sites, so a cookie set by Render would be a *third-party* cookie, which Safari and (increasingly) Chrome block. Next.js `rewrites` forward `/api/*`, `/oauth2/*`, `/login/oauth2/*` to Render, so the browser sees one origin and the cookie is first-party. I tested this (cookie forwarding, OAuth 302s, unbuffered streaming) against a mock backend.

## 3. Backend: `server/`

Package root: `server/src/main/java/com/springlensai/server/`

### Root and build files

| File | What it does | vs tutorial |
|---|---|---|
| `pom.xml` | Maven dependencies/build. Spring Boot 4.1.1, Spring AI 2.0.1, JPA, Security + OAuth2 client, **Session JDBC**, Actuator, **Validation**, pgvector, Gemini chat + embedding starters. Builds `target/app.jar`. | CHANGED: Gemini instead of OpenAI; added Session JDBC (sessions survive restarts), Validation (the tutorial's `@NotBlank` did nothing without a validator), fixed jar name for Docker; removed unused `vector-store-advisor`. |
| `Dockerfile` | Two-stage build (Maven → slim JRE), non-root user, memory-friendly JVM flags, reads Render's `PORT`. | NEW |
| `.dockerignore` | Keeps `target/`, `.env`, git data out of the image. | NEW |
| `.env.example` | Template of every env variable. Copy to `.env` locally (loaded automatically). | NEW |
| `mvnw`, `mvnw.cmd`, `.mvn/` | Maven wrapper so `./mvnw` works without installing Maven. | YOURS (same as tutorial) |
| `src/main/resources/application.properties` | All configuration, driven by env variables. | CHANGED: Gemini settings; `gemini-embedding-001` (the old `text-embedding-004` was shut down on 14 Jan 2026); 768 vector dimensions; Neon-friendly pool; session cookie flags; health probes; hourly session cleanup. (The tutorial zip did not include its properties file.) |
| `ServerApplication.java` | `main()` entry point. | YOURS (equivalent to the tutorial's `BackendApplication`) |
| `src/test/.../CodeChunkerTest.java`, `CodeFileFilterTest.java` | Unit tests that need no database or keys. | NEW (replaces the `contextLoads` test, which fails without DB/keys) |

### `config/`

| File | What it does | vs tutorial |
|---|---|---|
| `AppConfig.java` | Enables `@Async`; defines the `indexingExecutor` thread pool (2-4 workers, graceful shutdown); a prototype-scoped `RestClient.Builder`. | CHANGED: builder is prototype so callers can't share/mutate headers; graceful shutdown |
| `CorsConfig.java` | Allowed origins/methods for cross-origin calls (from `app.cors.allowed-origins`). | SIMILAR (+ preflight cache). Not exercised in the proxy setup, kept as a safety net |
| `CryptoConfig.java` | `TextEncryptor` bean (AES) used to encrypt GitHub tokens at rest. Needs a **hex** salt. | SAME |
| `SecurityConfig.java` | Which URLs are public, GitHub login wiring, 401 (not redirect) for API calls, logout endpoint, post-login redirect to `/auth/callback`. | SIMILAR: + public `/api/health` and actuator health, + failure handler that logs the reason and redirects to `/login?error=oauth_failed` |

### `entity/` (database tables)

| File | Table / purpose | vs tutorial |
|---|---|---|
| `User.java` | `users`: GitHub id, username, name, avatar, **encrypted** token, scopes. | YOURS |
| `Repository.java` | `repositories`: a GitHub repo for a user + index status, counters, error message. | SIMILAR: same fields, added a status index and column lengths |
| `IndexStatus.java` | enum PENDING / INDEXING / READY / FAILED. | SAME |
| `ChatSession.java` | `chat_sessions`: one conversation about a repo. | SIMILAR (+ index) |
| `ChatMessage.java` | `chat_messages`: USER/ASSISTANT text; assistant rows store citations as JSON. | SIMILAR (+ index) |
| `MessageRole.java` | enum USER / ASSISTANT. | SAME |

Tables are created automatically (`ddl-auto=update`). Spring Session creates `spring_session*`; Spring AI creates `vector_store`.

### `repository/` (data access, interfaces only)

| File | What it does | vs tutorial |
|---|---|---|
| `UserRepository.java` | find user by GitHub id. | YOURS |
| `RepositoryRepository.java` | user's repos, ownership-checked lookup, **find by index status**, bulk delete by user. | SIMILAR (+ `findByIndexStatus` for crash recovery, `existsByUserIdAndIndexStatus` and `deleteAllByUserId` for account deletion) |
| `ChatSessionRepository.java`, `ChatMessageRepository.java` | sessions per user+repo; messages by session in time order. | SAME (+ bulk `deleteAllByUserId` queries for account deletion) |

### `dto/` (API shapes, Java `record`s)

`DeleteAccountRequest` (NEW, typed username confirmation), `UserResponse`, `RepositoryResponse`, `IndexStatusResponse`, `ChatSessionResponse`, `ChatMessageResponse`, `CitationDto`, `CreateChatSessionRequest`, `ChatMessageRequest`: **SAME** as the tutorial, except the two request records now carry real validation (`@NotBlank`, `@Size`) that actually runs. They mirror the types in `client/src/lib/api.ts`.

### `exceptions/`

| File | What it does | vs tutorial |
|---|---|---|
| `NotFoundException`, `BadRequestException`, `UnauthorizedException` | Simple runtime exceptions mapped to 404/400/401. | YOURS (same as tutorial) |
| `GlobalExceptionHandler.java` | Turns every exception into `{status,error,message,timestamp}` JSON. | CHANGED: never leaks internal error text; maps GitHub errors (expired token → 401, rate limit → 429), Gemini quota → 429, bad JSON/params → 400, unknown URL → 404; JSON content type so SSE endpoints can still return errors |

### `security/`

| File | What it does | vs tutorial |
|---|---|---|
| `AppUserPrincipal.java` | Logged-in user object kept in the session. | CHANGED: stores only the user id + attributes and is `Serializable`. The tutorial kept the JPA `User` inside, which cannot be saved into a Postgres-backed session |
| `CurrentUser.java` | Helper: get the logged-in principal or throw 401. | SAME |
| `GithubOAuth2UserService.java` | After GitHub login: fetch profile, save user + encrypted token, return the principal. | SIMILAR |

### `service/`

| File | What it does | vs tutorial |
|---|---|---|
| `UserService.java` | Create/update user on login; load user (missing → 401, not a crash); decrypt token (unreadable → 401 "sign in again"). | CHANGED (error handling) |
| `RepoService.java` | Sync repos from GitHub into the DB, list, ownership check, index status. | CHANGED: per-user lock prevents duplicate inserts from double requests |
| `AccountService.java` | Orchestrates account deletion: validate confirmation and no running index job → delete vectors per repo → delete DB rows → delete login sessions → revoke GitHub authorization. | NEW |
| `AccountDataEraser.java` | The transactional database part of deletion (messages, chats, repositories, user in ONE transaction; login sessions in a second one). Separate bean because `@Transactional` only works across beans. | NEW |
| `ChatService.java` | Sessions + `streamReply` pipeline (validate → history → save question → retrieve → prompt → stream). | CHANGED: follow-up questions use the last 6 messages; first question becomes the session title |

### `service/ai/` (the RAG "brain")

| File | What it does | vs tutorial |
|---|---|---|
| `RagSettings.java` | Constants: top-K=8 chunks, 120 s stream timeout, history size, metadata key. | SIMILAR (+ history constants) |
| `RetrievedContext.java` | Result of retrieval: citations + context text. | SAME |
| `CodeContextRetriever.java` | Embeds the question, searches pgvector **filtered to one repo**. | SAME |
| `CitationMapper.java` | Vector-store document ⇄ citation DTO ⇄ JSON column. | SAME |
| `ChatPromptBuilder.java` | System prompt + user prompt (history + code + question). | CHANGED: history; tells the model to ignore instructions found inside repo code (prompt-injection guard) |
| `ChatStreamHandler.java` | Calls Gemini via Spring AI, streams tokens as SSE, saves the final answer. | CHANGED: stops generating when the browser leaves, sends an `error` event with a friendly message, handles empty answers, closes cleanly |
| `AiErrors.java` | Recognises Gemini rate-limit/availability errors, writes user-friendly text. | NEW |

### `service/github/`

| File | What it does | vs tutorial |
|---|---|---|
| `GithubApiClient.java` | Calls GitHub: list repos, file tree, file content, and (new) revoke the app's authorization when an account is deleted. | CHANGED: one immutable client, token attached **per request**. The tutorial mutated a shared builder, so two users indexing at once could send each other's token. Also timeouts and proper URL encoding |
| `GitHubRateLimiter.java` | Small pause between GitHub calls. | SIMILAR (configurable) |

### `service/indexing/`

| File | What it does | vs tutorial |
|---|---|---|
| `CodeFileFilter.java` | Which files to index (by extension/size) and language detection. Skips deps, build output, lock files, minified files, dotfiles like `.env`. | SIMILAR (+ minified/source-map skip) |
| `CodeChunker.java` | Splits files into overlapping, **line-aligned** chunks with `startLine/endLine` metadata. | CHANGED: the tutorial used `TokenTextSplitter` (no line numbers, ignored overlap). Now citations show real line ranges |
| `IndexingService.java` | The background pipeline (see section 2). | CHANGED: retry with backoff on Gemini limits, file cap, FAILED (not stuck) after a restart, clear failure messages, empty-repo handling |

### `controller/`

| File | Endpoints | vs tutorial |
|---|---|---|
| `AuthController.java` | `GET /api/auth/me`, `GET /api/auth/login-url` (logout is handled by Spring Security at `POST /api/auth/logout`). | SIMILAR (`/me` reads fresh data from the DB) |
| `RepoController.java` | `GET /api/repos`, `GET /api/repos/{id}`, `POST /api/repos/{id}/index`, `GET /api/repos/{id}/status`. | SIMILAR (+ handles a full job queue) |
| `ChatController.java` | `POST/GET /api/chat/sessions`, `GET /api/chat/sessions/{id}`, `POST .../messages` (SSE). | SIMILAR (+ anti-buffering headers on the stream) |
| `HealthController.java` | `GET /` and `GET /api/health` (no database). Used to wake the server. | NEW |
| `AccountController.java` | `POST /api/account/delete`: permanently deletes the signed-in user (typed username required), then signs out and expires the cookie. | NEW (account deletion) |

## 4. Frontend: `client/`

(The "vs tutorial" column below was produced by an automatic file diff, ignoring the DevPilot→SpringLens renaming.)

### Config and root files

| File | What it does | vs tutorial |
|---|---|---|
| `package.json` | Dependencies. Added `streamdown`, `@streamdown/code` (markdown + code blocks) and `react-icons` (language icons). | CHANGED (your base + 3 packages) |
| `next.config.ts` | **Rewrites** `/api`, `/oauth2`, `/login/oauth2` → Spring (`BACKEND_URL`); 180 s proxy timeout for streaming; security headers; fails the Vercel build if `BACKEND_URL` is missing. | CHANGED (the tutorial called the API directly) |
| `src/proxy.ts` | Runs before pages (Next 16's replacement for middleware). Redirects signed-out visitors away from `/dashboard` and `/chat` using a hint cookie. | CHANGED (small): cookie name and OAuth callback exemption |
| `components.json`, `tsconfig.json`, `eslint`, `postcss` | shadcn/TS/lint/Tailwind config. | YOURS |
| `.env.example` | `BACKEND_URL`, `NEXT_PUBLIC_SITE_URL`. | NEW |
| `public/`, `src/app/icon.svg`, `apple-icon.png`, `favicon.ico`, `opengraph-image.png`, `twitter-image.png` | **Placeholder** brand assets. Replace them keeping the same file names. | NEW |

### `src/lib/`

| File | What it does | vs tutorial |
|---|---|---|
| `api.ts` | Types, `ApiError` (carries the HTTP status), `apiFetch`, the `api.*` calls. Base URL is empty on purpose (same-origin via rewrites). | CHANGED |
| `stream-chat.ts` | Reads the SSE stream by hand (`fetch` + parser) and calls handlers per event. | CHANGED: understands the `error` event; detects a stream that died early |
| `query-keys.ts` | TanStack Query cache keys. | SAME |
| `dashboard-nav.ts` | Sidebar menu definition. | SAME (formatting only) |
| `site.ts` | Brand/SEO constants (name, description, URL). | NEW |
| `utils.ts` | Re-exports `cn` from the `cn` package (as your shadcn CLI generated). | YOURS |

### `src/hooks/`

| File | What it does | vs tutorial |
|---|---|---|
| `use-auth.ts` | Current user query, logout, `useDeleteAccount`, hint cookie. | CHANGED: only a real **401** signs the user out; a sleeping server is retried with backoff |
| `use-repos.ts` | Repo list/detail/status queries, start indexing, refresh; polls while INDEXING. | CHANGED (small) |
| `use-chat.ts` | Sessions, messages, create session, `useStreamChat` (optimistic message, live tokens, abort). | CHANGED: after an interrupted stream it re-reads messages from the server; refreshes the sidebar title |
| `use-mobile.ts` | shadcn helper. | YOURS |

### `src/components/`

| File | What it does | vs tutorial |
|---|---|---|
| `providers/query-provider.tsx` | TanStack Query client; doesn't retry 4xx errors. | CHANGED (small) |
| `providers/theme-provider.tsx` | next-themes wrapper. | YOURS |
| `providers/require-auth.tsx` | Guards pages; shows "waking up the server" and a Retry button instead of kicking the user out. | CHANGED |
| `layout/app-shell.tsx` | Sidebar + header shell for the dashboard. | SAME (only the avatar fallback initials) |
| `layout/brand-mark.tsx` | Logo + name. | NEW (split out so the landing page doesn't load the sidebar code) |
| `icons/springlens-icon.tsx` | **Placeholder** logo mark as SVG. | NEW (replaces DevPilot icon) |
| `icons/github-icon.tsx`, `icons/language-icon.tsx` | GitHub mark; icon per programming language. | SAME |
| `chat/chat-view.tsx` | The chat page: sidebar of sessions, messages, composer, indexing state. | SAME (one-line change to pass the repo id) |
| `chat/chat-messages.tsx`, `chat-composer.tsx`, `chat-sidebar.tsx`, `chat-markdown.tsx` (+ `.css`), `citation-chips.tsx`, `indexing-state.tsx` | Message list, input box, session list, markdown renderer, `file:line` chips, "repo not ready" screen. | SAME |
| `dashboard/repo-dashboard.tsx`, `repo-card.tsx`, `repo-status.tsx`, `dashboard-header.tsx`, `index-error-alert.tsx`, `language-badge.tsx`, `overview-dashboard.tsx` | Repo grid, cards with Index/Chat buttons and progress, header, error alert, stats overview. | SAME |
| `dashboard/settings-dashboard.tsx` | Profile, theme, sign out, and the delete-account card. | CHANGED (brand text + delete card) |
| `dashboard/delete-account-card.tsx` | "Danger zone" card + confirmation dialog (type your username to enable the button). | NEW |
| `marketing/chat-preview.tsx` | Static illustration of the product on the landing page. | NEW |
| `marketing/backend-warmup.tsx` | Pings `/api/health` on the landing page to wake Render early. | NEW |
| `ui/*` | Your shadcn components. I did not modify them. All exports match the tutorial's, so the tutorial UI code works unchanged. | YOURS |

### `src/app/` (pages)

| File | What it does | vs tutorial |
|---|---|---|
| `layout.tsx` | Fonts, providers, **full SEO metadata** (title template, Open Graph, Twitter, robots, viewport/theme color), mounts `<Toaster>`. | CHANGED: the tutorial never mounted a toast provider, so its toasts would not have appeared |
| `page.tsx` | Landing page: hero, product preview, how it works, FAQ accordion, **JSON-LD** (SoftwareApplication + FAQPage). | CHANGED (new design and SEO content) |
| `login/page.tsx` (+`layout.tsx`) | GitHub sign-in; shows OAuth errors; skips if already signed in; `noindex`. | SIMILAR |
| `auth/callback/page.tsx` | Confirms the session after GitHub redirect, then opens the dashboard. | SIMILAR |
| `dashboard/page.tsx`, `overview/page.tsx`, `settings/page.tsx` (+`dashboard/layout.tsx`) | Protected pages (`noindex`). | SAME |
| `chat/[repoId]/page.tsx` (+`chat/layout.tsx`) | Protected chat page. | SAME |
| `robots.ts`, `sitemap.ts`, `manifest.ts` | SEO + installable-app metadata; private routes are disallowed. | NEW |
| `not-found.tsx`, `error.tsx` | 404 and error screens. | NEW |
| `globals.css` | Your theme; I only added the two `@source` lines Tailwind needs to style Streamdown. | YOURS (+2 lines) |

## 5. Infrastructure files (repo root)

| File | What it does |
|---|---|
| `docker-compose.yml` | Local Postgres with pgvector on :5432 (YOURS, same as tutorial). |
| `docker/postgres/init-extensions.sql` | Creates `vector`, `hstore`, `uuid-ossp` on first start (YOURS). Run the same SQL once in Neon. |
| `render.yaml` | Render Blueprint for the API (Docker, health check, env var list). NEW |
| `.gitignore` | Your file, plus a rule that keeps `.env.example` files in git. |

## 6. Improvements over the tutorial (summary)

**Fixes for real bugs:** shared HTTP-client builder could mix two users' GitHub tokens · `@Valid` annotations did nothing (no validator) · toasts never rendered (no provider) · unhandled exceptions leaked internal messages · repos stuck on "Indexing" forever after a restart · `text-embedding-004` no longer exists.

**Needed for Vercel + Render:** same-origin proxy (first-party cookies), sessions in Postgres, forwarded-header handling, health endpoints that don't wake the database, cold-start UX, hourly (not per-minute) session cleanup so Neon can sleep, memory-friendly Docker image.

**Account deletion (new):** full erase of vectors, messages, chats, repositories, user and login sessions, plus revocation of the GitHub authorization. Needed explicit code because the tables have no foreign keys.

**Product quality:** real line-number citations, conversation memory, auto session titles, Gemini retry/backoff, friendly errors, SEO (metadata, JSON-LD, sitemap, robots, OG images).

## 7. Known limits and next steps

- I could not compile the Java code in my environment (no Maven Central access). I syntax-checked it with `javac` and ran the chunker/filter logic against stubs, and I built and exercised the whole frontend. The first `./mvnw spring-boot:run` is the real compile test: if a compiler error appears, it will name the file and line.
- `ddl-auto=update` is fine to start; for a long-lived product move to Flyway migrations.
- Content-Security-Policy is not set (it needs nonce handling with Next.js and next-themes). Other security headers are in place.
- Replace the placeholder images: `src/app/icon.svg`, `apple-icon.png`, `favicon.ico`, `opengraph-image.png`, `twitter-image.png`, `public/icon-192.png`, `icon-512.png`, `springlensai-logo.svg`, and the mark in `components/icons/springlens-icon.tsx`.
- Update `siteConfig.twitter` and `githubRepo` in `src/lib/site.ts`.
