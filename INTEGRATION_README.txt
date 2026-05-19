CallFilter v25 — what changed since v24
========================================

This integration drop is meant to be merged on top of your existing repo at:

    https://github.com/protocolindia/callfilter

Replace the three top-level folders (android/, backend/, frontend/) with the
contents of this archive, commit, and push.

------------------------------------------------------------
BACKEND
------------------------------------------------------------

NEW FILES
  backend/migrations/006_schedules.sql
    Creates the schedules table for time-window blocking.
    Runs automatically when Railway redeploys (migrate.js handles it).

MODIFIED FILES
  backend/src/api.js
    Two endpoints added near the end (before module.exports = router):
       POST /api/schedules/sync
       GET  /api/schedules/list?user_id=N

NO CHANGES TO
  backend/package.json, Procfile, railway.json, or any other files.

------------------------------------------------------------
FRONTEND
------------------------------------------------------------

No changes in v25.

------------------------------------------------------------
ANDROID
------------------------------------------------------------

This is the full v25 source tree. The previous version (v23/v24) is replaced
wholesale. Key changes:

NEW FILES (Java)
  android/app/src/main/java/pro/onephone/callfilter/
    Schedule.java                — POJO with time-window logic
    ScheduleManager.java         — persistence + cloud sync + overlap resolution
    SchedulesActivity.java       — list screen
    EditScheduleActivity.java    — add/edit a schedule
    ContactPickerActivity.java   — pick contacts for allowlist
    PermissionsActivity.java     — two-step permissions UX

NEW FILES (resources)
  android/app/src/main/res/layout/
    activity_schedules.xml
    activity_edit_schedule.xml
    activity_contact_picker.xml
    activity_permissions.xml
    schedule_tile.xml
    contact_pick_row.xml
    home_schedules_card.xml

MODIFIED
  android/app/src/main/AndroidManifest.xml
    Removed: FOREGROUND_SERVICE, FOREGROUND_SERVICE_PHONE_CALL,
             <service .CallFilterForegroundService>,
             <receiver .BootReceiver>
    Added:   <activity> entries for the 4 new screens

  android/app/src/main/java/pro/onephone/callfilter/
    CallBlockerService.java      — applies active schedule's allowlist
    CallStateReceiver.java       — same (Samsung-compat fallback)
    MainActivity.java            — home tile + summary text + onCreate wiring
    LoginActivity.java           — pulls schedules from cloud on re-install
    SetPinActivity.java          — routes through PermissionsActivity
    AuthManager.java             — OTP fix (carries devOtp from backend resp)
    SignupActivity.java          — OTP fix (passes devOtp via intent extra)
    OtpActivity.java             — OTP fix (auto-fills input in dev mode)

  android/app/src/main/res/drawable/
    btn_type_active.xml, btn_type_inactive.xml,
    btn_accept_active.xml, btn_accept_inactive.xml,
    btn_reject_active.xml, btn_reject_inactive.xml,
    btn_delete.xml
    (button color fix — text & symbols now visible in all states)

------------------------------------------------------------
ANDROID BUILD CHECKLIST
------------------------------------------------------------

When building locally, ensure your repo also contains (NOT in this archive):

  android/keystore/release.keystore       — your private signing key
  android/keystore/keystore.properties    — alias + passwords
  android/gradle/wrapper/gradle-wrapper.jar  (Android Studio auto-creates)
  android/gradlew, android/gradlew.bat        (Android Studio auto-creates)

If migrating from your v23/v24 project, copy the keystore/ and wrapper files
across from the old tree.

------------------------------------------------------------
TESTING CHECKLIST AFTER DEPLOY
------------------------------------------------------------

[ ] Railway backend redeploys; log shows "Running migration 006_schedules.sql"
[ ] curl https://api.app.onephone.pro/api/schedules/list?user_id=1
    → {"ok":true,"schedules":[]}
[ ] Build APK locally with .\gradlew.bat assembleRelease
[ ] Install on Android 14 device
[ ] Login flow works WITHOUT crash (FG service issue fixed)
[ ] Buttons (PREFIX/BETWEEN/SUFFIX/✓/✗/×) all show visible text & symbols
[ ] OTP screen shows the code in DEV MODE when admin setting is on
[ ] Home screen shows the 🗓️ Schedules tile
[ ] Tap tile → list works → + NEW SCHEDULE → edit → save → returns to list
[ ] During active window, non-allowlisted callers are silently rejected
[ ] Quick-activate works (Activate now → 30m / 1h / 2h / 4h)
[ ] Logout → log back in → schedules pulled from cloud
