# AXION STREAM — Secure Backend API (Node.js + TypeScript)

Server-side streaming backend for **Axion Stream**, built for **Supabase PostgreSQL** and **Telegram MTProto (GramJS)** private-channel video streaming with HTTP Range (`206 Partial Content`) support for videos up to ~200 MB.

---

## Security Architecture

- **Zero Telegram secrets in the Android app**: `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`, `TELEGRAM_SESSION_STRING`, and `TELEGRAM_CHANNEL_ID` exist strictly on this server.
- **No permanent disk video storage**: `GET /api/movies/:id/stream` streams 4 KB-aligned MTProto chunks directly to the HTTP response with backpressure and immediate cancellation on client disconnect/seek.
- **Atomic one-time access codes**: `POST /api/auth/activate` executes a PostgreSQL `SELECT ... FOR UPDATE` RPC (`claim_access_code`) so each access code locks to one user/device and rejects invalid (`404`), disabled/expired (`403`), or already-claimed (`409`) codes.
- **Isolated User vs. Admin sessions**: User Bearer tokens (`user_sessions`) can never access `/api/admin/*` routes (`admin_sessions`), and no public registration endpoint exists.
- **Zero-trust Supabase RLS**: Row Level Security is enabled across all tables with no public anon policies; all database access runs server-side via `SUPABASE_SERVICE_ROLE_KEY`.

---

## Step-by-Step Setup & Deployment

### Step 1: Initialize Supabase Database
1. Create a project at [supabase.com](https://supabase.com).
2. Open **SQL Editor** in Supabase and run the entire contents of `backend/supabase/schema.sql`.
3. From **Project Settings → API**, note:
   - `SUPABASE_URL`
   - `SUPABASE_SERVICE_ROLE_KEY` *(server-side ONLY — never put in Android)*

### Step 2: Generate Server Secrets Locally
Run inside the `backend/` folder:
```bash
npm install
# 1. Generate a 32-byte random TOKEN_PEPPER
openssl rand -hex 32

# 2. Generate scrypt ADMIN_PASSWORD_HASH
npm run hash-password

# 3. Generate Telegram MTProto session string (requires API ID & Hash from https://my.telegram.org)
#    Use an account that is a member/admin of your private Telegram movie channel.
npm run telegram-session
```
Find your private channel numeric ID (e.g. `-1001234567890`) and store it as `TELEGRAM_CHANNEL_ID`.

### Step 3: Deploy Over HTTPS (Choose Any Streaming-Capable Host)

#### Option A: Google Cloud Run (Recommended)
```bash
cd backend
gcloud run deploy axion-stream-backend \
  --source . \
  --region asia-east1 \
  --allow-unauthenticated \
  --timeout 3600 \
  --memory 512Mi \
  --set-env-vars "NODE_ENV=production,CORS_ALLOWED_ORIGINS=https://admin.yourdomain.com,SUPABASE_URL=...,SUPABASE_SERVICE_ROLE_KEY=...,TOKEN_PEPPER=...,ADMIN_EMAIL=...,ADMIN_PASSWORD_HASH=...,TELEGRAM_API_ID=...,TELEGRAM_API_HASH=...,TELEGRAM_SESSION_STRING=...,TELEGRAM_CHANNEL_ID=..."
```
Copy the printed `Service URL` (`https://axion-stream-backend-...run.app`) — that is your live `AXION_API_BASE_URL`.

#### Option B: Fly.io (`fly.toml` included)
```bash
cd backend
fly launch --no-deploy
fly secrets set \
  SUPABASE_URL="..." \
  SUPABASE_SERVICE_ROLE_KEY="..." \
  TOKEN_PEPPER="..." \
  ADMIN_EMAIL="..." \
  ADMIN_PASSWORD_HASH="..." \
  TELEGRAM_API_ID="..." \
  TELEGRAM_API_HASH="..." \
  TELEGRAM_SESSION_STRING="..." \
  TELEGRAM_CHANNEL_ID="..."
fly deploy
```

#### Option C: Render (`render.yaml` included)
1. Push the `backend/` directory to a private GitHub repository.
2. In Render Dashboard → **New Blueprint** → select the repo (`render.yaml`), enter the prompted secret environment variables, and deploy.
3. Copy the live `https://*.onrender.com` URL.

### Step 4: Verify Deployment & Connect the Android App
1. Verify health: `curl https://<YOUR_DEPLOYED_HOST>/health`
2. Create your first user access code and register a Telegram channel movie via Admin APIs:
   ```bash
   # Sign in as Admin
   curl -X POST https://<YOUR_DEPLOYED_HOST>/api/admin/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"admin@yourdomain.com","password":"YOUR_ADMIN_PASSWORD"}'

   # Generate a single-user access code
   curl -X POST https://<YOUR_DEPLOYED_HOST>/api/admin/access-codes \
     -H "Authorization: Bearer <ADMIN_TOKEN>" \
     -H "Content-Type: application/json" \
     -d '{"count":1,"label":"VIP Member 1"}'

   # Inspect videos in your private Telegram channel
   curl https://<YOUR_DEPLOYED_HOST>/api/admin/telegram/channel-videos \
     -H "Authorization: Bearer <ADMIN_TOKEN>"

   # Publish a movie by telegram_message_id (auto-syncs duration, size & thumbnail)
   curl -X POST https://<YOUR_DEPLOYED_HOST>/api/admin/movies \
     -H "Authorization: Bearer <ADMIN_TOKEN>" \
     -H "Content-Type: application/json" \
     -d '{"title":"My Movie","description":"...","telegram_message_id":42,"is_featured":true,"is_trending":true}'
   ```
3. In **AI Studio → Secrets**, set `AXION_API_BASE_URL` to `https://<YOUR_DEPLOYED_HOST>` (no trailing `/api`), plus `SUPABASE_URL` and `SUPABASE_ANON_KEY`, then rebuild the Android app.

---

## Complete API Reference

### User App Endpoints (Android Compatible)
- `POST /api/auth/activate` — Atomic single-user access code activation
- `GET /api/auth/session` — Validate Bearer token & return user/code status
- `POST /api/auth/logout` — Revoke session token
- `GET /api/categories` — List categories
- `GET /api/movies` — List movies (`?category_id=`, `?q=`)
- `GET /api/movies/:id` — Movie detail + favorite flag + resume watch history
- `GET /api/movies/:id/thumbnail` — Authenticated proxy for embedded Telegram video thumbnail
- `GET /api/movies/:id/stream` — Authenticated HTTP `206 Partial Content` Range streaming from private Telegram channel
- `GET /api/user/favorites` — List user's saved movies
- `POST /api/user/favorites` — Add `{ movie_id }` to My List
- `DELETE /api/user/favorites/:id` — Remove movie from My List
- `GET /api/user/watch-history` & `POST /api/user/watch-history` — Cross-device resume playback sync

### Admin Endpoints (Separate Admin Application Only)
- `POST /api/admin/auth/login`, `GET /api/admin/auth/me`, `POST /api/admin/auth/logout`
- `GET /api/admin/access-codes`, `POST /api/admin/access-codes`, `PATCH /api/admin/access-codes/:id`
- `GET /api/admin/users`, `PATCH /api/admin/users/:id`
- `GET /api/admin/categories`, `POST /api/admin/categories`, `DELETE /api/admin/categories/:id`
- `GET /api/admin/telegram/channel-videos`
- `GET /api/admin/movies`, `POST /api/admin/movies`, `PATCH /api/admin/movies/:id`, `DELETE /api/admin/movies/:id`
