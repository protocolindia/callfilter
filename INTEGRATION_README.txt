CallFilter v25.1 — full cumulative drop
=========================================

This zip contains the complete repo, ready to push to:
    https://github.com/protocolindia/callfilter

Replace the three top-level folders (android/, backend/, frontend/) with
the contents of this archive, commit, and push. No piecemeal patches.

============================================================
CHANGES IN v25.1 — what's new vs your current live deploy
============================================================

BACKEND
-------
NEW    backend/migrations/006_schedules.sql
       Creates the schedules table for time-window blocking.
       Runs automatically when Railway redeploys.

CHG    backend/src/api.js
       - Added POST /api/schedules/sync
       - Added GET  /api/schedules/list?user_id=N
       - FIXED: SMS Provider dropdown "None (dev mode — OTP on screen)"
         in the admin panel now correctly drives OTP-in-response mode.
         Previously the dropdown did nothing; only the hidden checkbox
         "Return OTP in signup response" worked. Now sms_provider='none'
         is treated as dev mode (legacy checkbox still works too).

FRONTEND
--------
CHG    frontend/src/pages/Settings.jsx
       - Removed the misleading hidden checkbox.
       - Added a clear status banner under the SMS Provider section:
            GREEN  "DEV MODE active. OTPs returned in API response..."
            BLUE   "Production mode. OTPs dispatched via {provider}..."

ANDROID
-------
NEW    Schedules feature (5 new activities + cloud sync)
NEW    PermissionsActivity (two-step permissions, fixes login crash)
CHG    AndroidManifest.xml — removed FG service (fixes Android 14 crash)
CHG    CallBlockerService / CallStateReceiver — apply schedule allowlist
CHG    MainActivity — home Schedules tile + button color fix
CHG    LoginActivity / SetPinActivity — route via PermissionsActivity
CHG    AuthManager / SignupActivity / OtpActivity — OTP-in-response fix

============================================================
DEPLOYMENT
============================================================

    cd D:\callfilter
    git pull origin main

    # Extract this zip somewhere temporary, e.g. C:\temp\v251
    # Then mirror the three folders into your repo:
    robocopy C:\temp\v251\callfilter-monorepo\android  android  /E
    robocopy C:\temp\v251\callfilter-monorepo\backend  backend  /E
    robocopy C:\temp\v251\callfilter-monorepo\frontend frontend /E
    copy C:\temp\v251\callfilter-monorepo\README.md .
    copy C:\temp\v251\callfilter-monorepo\INTEGRATION_README.txt .

    git status                       # verify before committing
    git add .
    git commit -m "v25.1 — schedules + OTP dev mode + Android v25"
    git push origin main

Railway auto-redeploys both services in ~2 minutes.

============================================================
POST-DEPLOY SMOKE TESTS
============================================================

1. Backend schedules endpoint:
   curl https://api.app.onephone.pro/api/schedules/list?user_id=1
   Expected: {"ok":true,"schedules":[]}

2. SMS dev mode (with sms_provider='none' in admin):
   curl -X POST https://api.app.onephone.pro/api/signup \
     -H "Content-Type: application/json" \
     -d '{"dial_code":"+91","mobile":"9999999999"}'
   Expected: response includes "otp":"123456"

3. Admin panel Settings (https://app.onephone.pro/settings):
   GREEN banner visible when SMS Provider = "None (dev mode...)"

4. Android build:
   cd D:\callfilter\android
   .\gradlew.bat clean
   .\gradlew.bat assembleRelease
   APK at: app\build\outputs\apk\release\app-release.apk

5. Install on device → signup → OtpActivity shows the OTP banner
   and auto-fills the input.

============================================================
NOT IN THIS ZIP (by design — keep your local copies)
============================================================

- android/keystore/                  (your signing keys)
- android/local.properties           (auto-generated)
- android/gradle/wrapper/gradle-wrapper.jar  (auto-generated)
- android/gradlew, android/gradlew.bat       (auto-generated)
- node_modules/, build/, .gradle/, dist/     (build artifacts)
