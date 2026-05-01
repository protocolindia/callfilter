# CallFilter Platform

A 3-tier deployment of the Call Filter admin system on Railway, all in a single Git repo:

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  Frontend    │ ───▶ │  Backend     │ ───▶ │  PostgreSQL  │
│  React/Vite  │ HTTP │  Express API │  pg  │  (Railway DB)│
└──────────────┘      └──────────────┘      └──────────────┘
```

```
callfilter-platform/        ← single Git repo
├── backend/                ← Railway service #1 (Root Directory: backend)
│   ├── src/
│   ├── migrations/
│   ├── package.json
│   └── railway.json
├── frontend/               ← Railway service #2 (Root Directory: frontend)
│   ├── src/
│   ├── package.json
│   └── railway.json
└── README.md
```

The trick: **one repo, two Railway services, each pointing at a different Root Directory.** Railway runs `npm install && npm start` inside that subfolder only, so each service is built independently.

---

## 🚀 Deployment to Railway — Step by Step

### Prerequisites
1. A free [Railway](https://railway.app) account
2. A GitHub account (Railway pulls from a repo)
3. Git installed locally

### Step 1 — Push the whole folder to ONE GitHub repo

```bash
cd callfilter-platform
git init
git add .
git commit -m "Initial commit — backend + frontend"
git branch -M main
git remote add origin https://github.com/YOU/callfilter-platform.git
git push -u origin main
```

### Step 2 — Create Railway project + database

1. railway.app → **New Project**
2. Click **+ Add Service → Database → PostgreSQL**
3. Wait ~30 seconds for Postgres to provision

### Step 3 — Add the backend service

1. **+ Add Service → GitHub Repo** → pick `callfilter-platform`
2. Railway will try to deploy it — let it fail the first attempt; we need to set the root directory.
3. Click the new service → **Settings** tab → scroll to **Source**:
   - **Root Directory:** `backend`
   - **Watch Paths** (optional): `backend/**` (so frontend changes don't trigger a backend redeploy)
4. **Variables** tab — click **+ New Variable** → **Add Reference** → choose Postgres service → select `DATABASE_URL`
5. Add these variables (use **Raw Editor** to paste all at once):
   ```
   JWT_SECRET=<run: openssl rand -hex 32>
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD=<your strong password>
   NODE_ENV=production
   CORS_ORIGINS=*
   ```
6. **Settings → Networking → Generate Domain.** Copy this URL — you'll need it in Step 4.
7. The backend redeploys. **Deployments → View Logs** should show:
   ```
   🔧 Running migrations...
     → applying 001_init.sql
   ✓ Default admin: admin / (from env)
   ✅ Migrations complete
   🚀 Backend listening on http://0.0.0.0:3000
   ```
8. Test it: visit `https://<backend-url>/api/health` → returns `{"ok":true,"ts":"..."}`.

### Step 4 — Add the frontend service

1. Back in the **same Railway project** → **+ Add Service → GitHub Repo** → pick the **same repo** (`callfilter-platform`)
2. Click the new service → **Settings** → **Source**:
   - **Root Directory:** `frontend`
   - **Watch Paths:** `frontend/**`
3. **Variables** tab → add:
   ```
   VITE_API_URL=https://<backend-url-from-step-3>
   ```
4. **Settings → Networking → Generate Domain.** This is your admin panel URL.

> **Important:** Vite bakes `VITE_API_URL` into the built JavaScript at *build time*, not runtime. If you change this variable later, you must **manually trigger a redeploy** of the frontend (Deployments tab → ⋮ → Redeploy).

### Step 5 — Lock down CORS

1. Go back to **Backend → Variables** → change:
   ```
   CORS_ORIGINS=https://<your-frontend-url>
   ```
2. Save. Backend redeploys with stricter CORS.

### Step 6 — First login

1. Open your **frontend URL** in a browser
2. Log in with the credentials you set in Step 3 (`ADMIN_USERNAME` / `ADMIN_PASSWORD`)
3. Open **Settings → Change Admin Password** to set a fresh password (the env-var password becomes inactive once you change it here)

### Step 7 — How redeploys work in a monorepo

Whenever you `git push` to `main`:
- Backend service redeploys **only** if files in `backend/` changed (because of Watch Paths)
- Frontend service redeploys **only** if files in `frontend/` changed
- README/.gitignore changes trigger nothing

You only have **one repo to maintain** but get **independent deployments** per service.

---

## 📱 Connecting the Android app

In your CallFilter Android project, edit `AuthManager.java`:

```java
public static final String BACKEND_URL = "https://<your-backend-url>";
public static final boolean BACKEND_LIVE = true;
```

Rebuild the APK. Now the app calls your Railway backend for signup, OTP, and PIN.

Until you wire a real SMS provider in **Settings → SMS Provider**, OTP is returned in the API response and shown on the Android signup screen (dev mode). After picking a provider and saving credentials, uncheck "Return OTP in signup response" — OTPs become SMS-only.

---

## 🛠 Local development

You need PostgreSQL running locally. Easiest with Docker:

```bash
docker run --name cf-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=callfilter -p 5432:5432 -d postgres:16
```

### Backend
```bash
cd backend
cp .env.example .env
# Edit .env: DATABASE_URL=postgresql://postgres:postgres@localhost:5432/callfilter
npm install
npm start
```

### Frontend (in a second terminal)
```bash
cd frontend
cp .env.example .env
# .env: VITE_API_URL=http://localhost:3000
npm install
npm run dev   # http://localhost:5173
```

Log in with `admin / changeme`.

---

## 🔐 API reference

### Public endpoints (Android app)

| Endpoint | Method | Body | Response |
|---|---|---|---|
| `/api/signup` | POST | `{dial_code, mobile, country_iso, device_info}` | `{ok, user_id, otp?}` |
| `/api/verify-otp` | POST | `{user_id, code}` | `{ok}` |
| `/api/set-pin` | POST | `{user_id, pin_hash}` | `{ok}` |
| `/api/health` | GET | — | `{ok, ts}` |

PIN is SHA-256 hashed on the device — plaintext PIN never leaves the phone.

### Admin endpoints (require `Authorization: Bearer <token>` from `/admin/login`)

| Endpoint | Method | Body | Response |
|---|---|---|---|
| `/admin/login` | POST | `{username, password}` | `{ok, token, username}` |
| `/admin/me` | GET | — | `{username}` |
| `/admin/stats` | GET | — | `{stats, recent_users, recent_log}` |
| `/admin/users` | GET | `?q=&status=` | `{users}` |
| `/admin/users/:id` | DELETE | — | `{ok}` |
| `/admin/users/:id/reset` | POST | — | `{ok}` |
| `/admin/settings` | GET | — | `{settings}` |
| `/admin/settings` | PUT | settings object | `{ok}` |
| `/admin/change-password` | POST | `{current, next}` | `{ok}` |
| `/admin/audit` | GET | — | `{log}` |

---

## 💸 Cost on Railway

- **Hobby plan:** $5/month gives ~$5 of usage credit. A small backend + small frontend + tiny Postgres typically costs **$2-4/month combined**.
- **Free trial:** Every new account gets $5 of one-time credit, enough for several weeks of testing.
- **No sleeping:** Hobby-plan services run 24/7. Database is always on.

## 🧯 Troubleshooting

**Backend keeps failing with "ECONNREFUSED 5432"**
The Postgres reference variable isn't connected yet. Variables tab → confirm `DATABASE_URL` shows a value (not just the variable name). If it's missing, click **+ New Variable → Add Reference → Postgres → DATABASE_URL** and redeploy.

**Frontend shows blank page**
Check **Deployments → View Logs** for the build output. Confirm `VITE_API_URL` is set. **After changing this variable you must click Redeploy manually** — Vite bakes it in at build time.

**"Invalid or expired token" on every admin request**
Either `JWT_SECRET` changed between deploys (regenerated by you), or the token expired (8 hour TTL). Log out and back in.

**Android signup returns network error**
Check `AuthManager.BACKEND_URL` matches your Railway backend domain exactly (no trailing slash, includes `https://`). If you tightened `CORS_ORIGINS` and forgot to include the Android app's origin, widen it back to `*` temporarily — Android apps don't have a fixed origin so CORS is mostly about protecting the browser frontend.

**Service won't deploy — "no package.json found"**
You forgot to set the **Root Directory** in Settings → Source. It must be `backend` for the backend service and `frontend` for the frontend service (no leading slash).

**I want backend changes to NOT redeploy the frontend (and vice versa)**
Settings → Source → **Watch Paths**:
- Backend service: `backend/**`
- Frontend service: `frontend/**`

Now `git push` only triggers redeploys for services whose watch paths matched changed files.
