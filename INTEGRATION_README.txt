CallFilter v25.2 — full cumulative drop
=========================================

This zip is the COMPLETE monorepo. Replace android/, backend/, frontend/
in your repo with the contents of this archive and push.

============================================================
NEW IN v25.2 — Frequency bypass + Block All Now
============================================================

FEATURE 1 — Per-schedule frequency bypass ("urgent caller")
-----------------------------------------------------------
Each schedule has a new section in its edit screen:
   ☐ Allow repeated callers to break through
        Count: [5]    in [10] minutes

When ON for a schedule, if the SAME number is rejected freqCount times
within freqWindowMin minutes (sliding window), the NEXT call from that
number rings through, regardless of which rule rejected the earlier ones.

  Default for new schedules: 5 calls in 10 minutes (OFF until you enable
  the toggle).

FEATURE 2 — Block All Now (top-bar 🛑 icon)
------------------------------------------
A new 🛑 icon in the top bar (left of the ⋮ menu) opens a 2-step picker:

   STEP 1 — Choose blocking mode:
       ⛔ Block everything (no exceptions)
       📇 Block everyone except my contacts
       ✅ Block everyone except specific contacts I pick

   STEP 2 — Choose duration:
       15 minutes | 30 minutes | 1 hour | 2 hours | 4 hours
       Custom...                 ← lets you enter any hh:mm
       Until I turn it off       ← indefinite, until you tap STOP

When active:
   - The 🛑 icon shows a red countdown chip (e.g. "1h 23m")
   - A red banner appears below the top bar with mode + remaining time
     and a STOP button to deactivate
   - Tapping the 🛑 icon while active = same as STOP

ARBITRATION
-----------
  If Block All is active AND someone hits the frequency threshold:
     → Frequency-bypass wins. The urgent caller rings through.

  If a schedule allows a caller AND Block All blocks them:
     → Block All wins. (When you panic-mode the phone, you really
       want quiet.)

  Block All has no own frequency-bypass — it's pure block.

REMOVED
-------
Per-schedule ⚡ "Activate now for 30m/1h/2h/4h" button — replaced by
the global 🛑 Block All Now. The Schedule data model still has the
quickUntilMs field for backward compatibility with existing cloud data,
but no UI exposes it. Schedules now activate only via their time window.

============================================================
BACKEND CHANGES
============================================================
NEW    backend/migrations/007_frequency_and_blockall.sql
       - Adds freq_bypass_enabled, freq_count, freq_window_min to schedules
       - Creates block_all_state table (1 row per user)

CHG    backend/src/api.js
       - /schedules/sync and /schedules/list now include freq_* fields
       - NEW POST /api/block-all/set   (activate/update/deactivate)
       - NEW GET  /api/block-all/get   (read current state)

============================================================
FRONTEND CHANGES
============================================================
None in v25.2. (No admin-panel UI for these yet — they're user-controlled
on the Android app.)

============================================================
ANDROID CHANGES
============================================================
NEW    FrequencyTracker.java       — sliding-window per-number rejection log
NEW    BlockAllManager.java        — panic-mode state + cloud sync
NEW    BlockAllNowDialog.java      — multi-step picker (mode → duration)
NEW    drawable/banner_block_all.xml
NEW    drawable/btn_stop_block_all.xml

CHG    Schedule.java               — freq_bypass_enabled JSON key sync
CHG    CallBlockerService.java     — frequency bypass + Block All arbitration
CHG    CallStateReceiver.java      — same logic for Samsung-compat path
CHG    AuthManager.java            — resetAccount() clears block_all + freq prefs
CHG    MainActivity.java           — 🛑 icon, banner, countdown ticker
CHG    res/layout/activity_main.xml — top-bar 🛑 icon + countdown + banner

UNCHANGED (still present from earlier work)
       EditScheduleActivity + activity_edit_schedule.xml
         already have the frequency-bypass toggle UI

DELETED
       BlockAllActivity.java       — was an unused stub from earlier
       res/layout/activity_block_all.xml — same

============================================================
DEPLOY
============================================================

    cd D:\callfilter
    git pull origin main

    # Extract this zip to a temp folder, then mirror into your repo:
    robocopy <extracted>\callfilter-monorepo\android  android  /E
    robocopy <extracted>\callfilter-monorepo\backend  backend  /E
    robocopy <extracted>\callfilter-monorepo\frontend frontend /E
    copy <extracted>\callfilter-monorepo\README.md .
    copy <extracted>\callfilter-monorepo\INTEGRATION_README.txt .

    git status
    git add .
    git commit -m "v25.2 — frequency bypass + Block All Now"
    git push origin main

Railway redeploys both services. On boot you should see:
    Running migration 007_frequency_and_blockall.sql

============================================================
POST-DEPLOY SMOKE TESTS
============================================================

1. Schedules sync (new fields):
   curl https://api.app.onephone.pro/api/schedules/list?user_id=1
   Each schedule row should include "freq_bypass_enabled":false,
   "freq_count":5, "freq_window_min":10

2. Block All state:
   curl https://api.app.onephone.pro/api/block-all/get?user_id=1
   → {"ok":true,"state":null}  (no panic mode active yet)

3. Build and install the Android app:
   cd D:\callfilter\android
   .\gradlew.bat clean
   .\gradlew.bat assembleRelease

4. App behavior:
   [ ] 🛑 icon visible in top bar
   [ ] Tap 🛑 → mode picker → duration picker → activate
   [ ] Red countdown chip + banner appear
   [ ] Banner STOP button deactivates
   [ ] Edit schedule shows "Allow repeated callers" toggle
   [ ] When ON, count/window fields appear
   [ ] Per-schedule ⚡ button is GONE
