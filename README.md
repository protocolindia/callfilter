# CallFilter Platform

A 3-tier deployment of the Call Filter admin system on Railway, all in a single Git repo:

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   Frontend   │ →  │   Backend    │ →  │  PostgreSQL  │
│  React/Vite  │HTTP│  Express API │ pg │  (Railway DB)│
└──────────────┘    └──────────────┘    └──────────────┘
```

```
callfilter/                 ← single Git repo
├── android/                ← Android app (build locally, not deployed to Railway)
├── backend/                ← Railway service #1 (Root Directory: backend)
│   ├── src/
│   ├── migrations/
│   ├── package.json
│   └── railway.json
├── frontend/               ← Railway service #2 (Root Directory: frontend)
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── railway.json
├── INTEGRATION_README.txt  ← what changed in this version
└── README.md
```

## v25 (current) — Schedules feature

### Backend changes
- **NEW** `backend/migrations/006_schedules.sql` — creates `schedules` table (runs automatically on next deploy)
- **UPDATED** `backend/src/api.js` — adds two endpoints at line ~614:
  - `POST /api/schedules/sync` — full-mirror upload of user's schedules
  - `GET  /api/schedules/list?user_id=N` — pull schedules (used after re-install)

### Frontend changes
- None in v25.

### Android changes (full list)
- Schedules feature: 5 new activities + cloud sync
- Home-screen tile with ACTIVE badge
- Quick-activate ("Activate Sleep mode for 30m/1h/2h/4h")
- Foreground service removed (fixes Android 14 crash)
- Button color fix (PREFIX/BETWEEN/SUFFIX text now visible)
- Permissions screen properly inserted between login and main
- **OTP fix**: when admin setting `otp_show_in_response = true`, the OTP now displays on the verification screen and auto-fills the input

## Deploy

Push to GitHub → Railway auto-redeploys both services.

```bash
git add .
git commit -m "v25 — schedules + Android fixes"
git push origin main
```

Watch the Railway deploy logs for the backend service. You should see:

```
Running migration 006_schedules.sql
server listening on :PORT
```

## Smoke test after Railway redeploys

```bash
curl https://api.app.onephone.pro/api/schedules/list?user_id=1
# Expected: {"ok":true,"schedules":[]}
```

If you get a 404, the deploy hasn't finished yet. If you get `{"error":"user_id required"}`, schedules endpoints exist but you forgot the query param — that's fine. Any non-404 response means it's live.

## Building the Android APK

The Android app is NOT deployed to Railway. Build locally:

```powershell
cd D:\callfilter\android
.\gradlew.bat clean
.\gradlew.bat assembleRelease
```

Output: `app\build\outputs\apk\release\app-release.apk`

You'll need:
- `android/keystore/release.keystore` (your signing key — NOT committed to git)
- `android/keystore/keystore.properties` with storePassword, keyAlias, keyPassword

## Admin panel — "Show OTP on screen"

To make OTP appear inside the app for testing:

```
Admin panel → Settings → otp_show_in_response → true
```

Or via SQL:

```sql
UPDATE settings SET value = 'true' WHERE key = 'otp_show_in_response';
-- if the row doesn't exist:
INSERT INTO settings(key, value) VALUES ('otp_show_in_response', 'true');
```

The Android app (v25+) automatically reads this from the signup response and displays the OTP banner + auto-fills the input.

## Environment variables (Railway)

### Backend service
- `DATABASE_URL` — auto-provided by Railway when you attach the Postgres plugin
- `JWT_SECRET` — any 32+ char random string
- `PORT` — Railway sets this; don't override

### Frontend service
- `VITE_API_BASE=https://api.app.onephone.pro`

## Support

Send the exact error from:
- Railway deploy logs (backend/frontend issues)
- `gradlew.bat assembleRelease` output (Android build issues)
- Logcat (Android runtime crashes)
