# AXION STREAM

A private, Netflix-inspired video streaming platform consisting of:

1. **Android User Streaming Application (`app/`)** — Native Kotlin + Jetpack Compose + Material 3 + Jetpack Media3 (ExoPlayer) client with obsidian dark cinema design, single-user access-code login, Home/Search/My List/Profile tabs, Movie Details, resume playback, landscape fullscreen player, and an isolated UI Preview mode.
2. **Secure Streaming Backend (`backend/`)** — Node.js + TypeScript + Express API powered by **Supabase PostgreSQL** (Row-Level Security + atomic `claim_access_code` RPC) and **Telegram MTProto (GramJS)** private-channel progressive HTTP `206 Partial Content` Range streaming and automatic thumbnail retrieval.

> **Important:** The Android application is strictly the **User Streaming App** (zero admin UI, zero public registration, zero Telegram credentials). Admin operations live exclusively behind isolated `/api/admin/*` routes on the backend server.

---

## Repository Structure

```text
.
├── .github/workflows/build.yml       # CI/CD: Backend test/build/Docker + Android APK artifact
├── .env.example                      # Android BuildConfig placeholders (NO secrets)
├── app/                              # Native Android Kotlin + Jetpack Compose streaming client
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/
│       │   ├── data/                 # ApiConfig, AxionApiService, SessionManager, Repository, SampleCatalogue
│       │   └── ui/                   # Theme, Components, ViewModel, Navigation, Screens (Login/Home/Detail/Player/Search/MyList/Profile)
│       └── res/                      # Custom adaptive icon, bundled fonts, generated sample posters, strings
├── backend/                          # Standalone Node.js + TypeScript streaming API server
│   ├── .env.example                  # Server-side secret placeholders (NEVER commit .env)
│   ├── Dockerfile                    # Multi-stage production container (non-root Node 20)
│   ├── fly.toml                      # Fly.io deployment config
│   ├── render.yaml                   # Render Web Service blueprint
│   ├── package.json & package-lock.json
│   ├── scripts/                      # hash-admin-password.ts & generate-telegram-session.ts
│   ├── src/                          # Express server, Supabase client, Telegram MTProto streamer, auth/catalogue/user/admin routes
│   ├── supabase/
│   │   ├── schema.sql                # Full PostgreSQL schema + RLS + atomic claim_access_code RPC
│   │   └── migrations/               # Supabase CLI migration files
│   └── tests/                        # Node.js unit tests (crypto, scrypt auth, HTTP Range parser)
├── gradle/libs.versions.toml         # Android dependency catalog
└── settings.gradle.kts
```

---

## Quick Start

### 1. Set Up Supabase Database
1. Create a Supabase project at [supabase.com](https://supabase.com).
2. Run `backend/supabase/schema.sql` in the Supabase **SQL Editor** (or apply `backend/supabase/migrations/` via the Supabase CLI).
3. Copy your `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and server-only `SUPABASE_SERVICE_ROLE_KEY`.

### 2. Configure & Deploy the Backend (`backend/`)
See [`backend/README.md`](backend/README.md) for full details:
```bash
cd backend
npm ci
openssl rand -hex 32          # -> TOKEN_PEPPER
npm run hash-password         # -> ADMIN_PASSWORD_HASH
npm run telegram-session      # -> TELEGRAM_SESSION_STRING
npm run typecheck && npm run build && npm test
```
Deploy `backend/` over HTTPS using **Google Cloud Run**, **Fly.io** (`fly.toml`), or **Render** (`render.yaml`), setting all variables from `backend/.env.example` in your host's secret manager.

### 3. Configure & Build the Android App (`app/`)
1. Set `AXION_API_BASE_URL` (your deployed HTTPS backend URL without `/api`), `SUPABASE_URL`, and `SUPABASE_ANON_KEY` in **AI Studio Secrets** (or GitHub Actions Repository Secrets for CI builds).
2. Never add Telegram bot tokens, API IDs, API hashes, session strings, or Supabase Service Role keys to the Android project.
3. Build locally or in CI:
   ```bash
   gradle :app:testDebugUnitTest
   gradle :app:assembleDebug
   ```

---

## GitHub Actions CI/CD (`.github/workflows/build.yml`)

Runs automatically on `push` / `pull_request` to `main`/`master` and manually via **Actions → Axion Stream CI/CD Build → Run workflow**:
- **Backend Job**: Installs dependencies (`npm ci`), type-checks (`tsc --noEmit`), compiles (`npm run build`), runs unit tests (`npm test`), and audits production dependencies (`npm audit --omit=dev`).
- **Docker Job**: Verifies the multi-stage `backend/Dockerfile` builds cleanly.
- **Android Job**: Runs `:app:testDebugUnitTest`, builds `:app:assembleDebug`, and uploads the APK as the `axion-stream-debug-apk` artifact.

### Optional GitHub Actions Secrets (for Android APK builds)
Add these under **Repository → Settings → Secrets and variables → Actions** if you want CI-built APKs pre-pointed at your live backend:
- `AXION_API_BASE_URL`
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

---

## Security Guarantees

- **No committed secrets**: `.env` files, keystores, and credentials are gitignored; only placeholder `.env.example` files exist in source control.
- **Zero Telegram secrets in the Android APK**: Private-channel MTProto streaming and thumbnail extraction occur strictly server-side.
- **No permanent video downloads**: Media3 ExoPlayer uses a bounded memory-only playback buffer over HTTP Range requests (`206 Partial Content`) and never writes videos to the device Gallery.
- **Isolated User & Admin Auth**: Single-user access codes are atomically claimed in PostgreSQL (`SELECT ... FOR UPDATE`), and user sessions can never access `/api/admin/*`.
