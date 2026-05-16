v25 SCHEDULES INTEGRATION GUIDE
================================

This patch adds the "Schedules" feature: time-windowed call blocking with
per-schedule allowlists, quick-activate, and home-screen tile entry point.

============================================================================
STEP 1 — DATABASE MIGRATION (backend)
============================================================================

Drop `006_schedules.sql` into:
   callfilter-platform/backend/migrations/006_schedules.sql

It will be applied automatically when the backend deploys (the migrate.js
script picks up new files in this folder).

============================================================================
STEP 2 — BACKEND API ENDPOINTS
============================================================================

Open: callfilter-platform/backend/src/api.js

Find a good spot near the other sync endpoints (e.g. just after the
`/rules/list` endpoint, around line 320). Paste the entire contents of
`api_schedules_snippet.js` (the file in this zip) right there.

============================================================================
STEP 3 — ANDROID FILES (new — just copy into project)
============================================================================

Copy these 5 new Java files into:
   app/src/main/java/pro/onephone/callfilter/

  Schedule.java
  ScheduleManager.java
  SchedulesActivity.java
  EditScheduleActivity.java
  ContactPickerActivity.java

Copy these 5 new layout files into:
   app/src/main/res/layout/

  activity_schedules.xml
  activity_edit_schedule.xml
  activity_contact_picker.xml
  schedule_tile.xml
  contact_pick_row.xml
  home_schedules_card.xml

============================================================================
STEP 4 — REGISTER ACTIVITIES IN MANIFEST
============================================================================

Open: app/src/main/AndroidManifest.xml

Find the block of <activity> entries (around line 50). Add these three lines
alongside the existing ones:

   <activity android:name=".SchedulesActivity"        android:exported="false"/>
   <activity android:name=".EditScheduleActivity"     android:exported="false"/>
   <activity android:name=".ContactPickerActivity"    android:exported="false"/>

============================================================================
STEP 5 — INTEGRATE WITH CallBlockerService (CRITICAL — this is what makes
                                           schedules actually block calls)
============================================================================

Open: app/src/main/java/pro/onephone/callfilter/CallBlockerService.java

Find the part of onScreenCall that handles the contacts-only mode. Right
AFTER the existing rules check (where `shouldReject` is decided), add the
schedule check. The exact structure depends on your current code but the
goal is: if a schedule is currently active AND the caller is NOT in that
schedule's allowlist, REJECT the call.

Here's the snippet to insert. Adapt the variable names to match what you
already have in your file:

  // === Schedule check (Option C: existing rules + schedule allowlist) ===
  // If we're about to ACCEPT, see whether a schedule wants to override that
  // and reject because the caller isn't on the schedule's allowlist.
  if (!shouldReject) {
      Schedule activeSchedule = ScheduleManager.getInstance(this)
          .getActiveSchedule(System.currentTimeMillis());
      if (activeSchedule != null && !activeSchedule.isCallerAllowed(number)) {
          android.util.Log.d(TAG,
              "Schedule \"" + activeSchedule.name + "\" active — rejecting " + number);
          shouldReject = true;
      }
  }

============================================================================
STEP 6 — DO THE SAME IN CallStateReceiver (Samsung-compat path)
============================================================================

Open: app/src/main/java/pro/onephone/callfilter/CallStateReceiver.java

Same idea — after the existing rule evaluation, add a schedule check:

  // === Schedule check ===
  boolean rejected = ...; // result of existing rule evaluation
  if (!rejected) {
      Schedule activeSchedule = ScheduleManager.getInstance(context)
          .getActiveSchedule(System.currentTimeMillis());
      if (activeSchedule != null && !activeSchedule.isCallerAllowed(number)) {
          rejected = true;
      }
  }
  if (rejected) {
      // (your existing reject logic)
  }

============================================================================
STEP 7 — ADD HOME-SCREEN TILE TO MainActivity
============================================================================

Two parts:

A) Add the tile to activity_main.xml.
   Open: app/src/main/res/layout/activity_main.xml
   Find the "Contacts Only Mode" card. Right AFTER its closing </LinearLayout>,
   include the schedules card:

      <include layout="@layout/home_schedules_card"/>

B) Wire up the click handler in MainActivity.java.
   Open: app/src/main/java/pro/onephone/callfilter/MainActivity.java
   In onCreate (or wherever you bind views), add:

      View cardSchedules = findViewById(R.id.cardSchedules);
      cardSchedules.setOnClickListener(new View.OnClickListener() {
          public void onClick(View v) {
              startActivity(new Intent(MainActivity.this, SchedulesActivity.class));
          }
      });

C) Update the summary text & badge in refreshUI() (or wherever you refresh
   the main screen). Add this code:

      // Update schedules summary on home tile
      TextView schedulesSummary = findViewById(R.id.schedulesSummary);
      TextView schedulesBadge   = findViewById(R.id.schedulesActiveBadge);
      List<Schedule> all = ScheduleManager.getInstance(this).getAll();
      Schedule active = ScheduleManager.getInstance(this).getActiveSchedule(System.currentTimeMillis());
      if (active != null) {
          schedulesSummary.setText("\"" + active.name + "\" active now");
          schedulesBadge.setVisibility(View.VISIBLE);
      } else if (all.isEmpty()) {
          schedulesSummary.setText("Time-based blocking — tap to add");
          schedulesBadge.setVisibility(View.GONE);
      } else {
          schedulesSummary.setText(all.size() + " schedule" + (all.size() == 1 ? "" : "s")
                                   + " — none active now");
          schedulesBadge.setVisibility(View.GONE);
      }

============================================================================
STEP 8 — PULL SCHEDULES ON LOGIN (re-install restoration)
============================================================================

Open: app/src/main/java/pro/onephone/callfilter/LoginActivity.java

Find the place you call pullRulesFromCloudIfEmpty(). Add a sibling line:

      SyncManager.getInstance(LoginActivity.this).pullRulesFromCloudIfEmpty();
      SyncManager.getInstance(LoginActivity.this).pullBlockedCallsFromCloudIfEmpty();
      ScheduleManager.getInstance(LoginActivity.this).pullFromCloudIfEmpty();    // <-- new

Same in SetPinActivity if you have a similar pull there.

============================================================================
STEP 9 — REBUILD
============================================================================

In Android Studio:
   Build → Clean Project → Rebuild Project → Run

Or CLI:
   .\gradlew.bat clean
   .\gradlew.bat assembleRelease

============================================================================
USE
============================================================================

1. Tap "🗓️ Schedules" tile on home screen.
2. Tap "+ NEW SCHEDULE".
3. Name it (e.g. "Sleep"), pick start/end times, choose days.
4. Tap "Allow these contacts" → pick people who can break through (e.g.
   spouse, kids, boss).
5. Save.

When the schedule's time window arrives, the call blocker:
  - Applies all your existing block rules (PREFIX, SUFFIX, BETWEEN), AND
  - Also blocks anyone not on the schedule's allowlist.

The home-screen tile shows "ACTIVE" badge and which schedule is active.

QUICK ACTIVATE: tap the "⚡ Activate now" button inside a schedule tile to
force it on for 30 min / 1h / 2h / 4h regardless of time window.

OVERLAP: if two schedules are both active, the one you most recently
created or toggled wins.

============================================================================
NOTES
============================================================================

- Schedules sync to the cloud automatically on every save/toggle.
- On re-install, schedules are pulled back down by LoginActivity.
- Contact allowlist normalization: numbers compared digit-by-digit so
  "+91 9876 543 210" matches "9876543210".
- The day labels in the day-picker (S M T W T F S) follow Sunday-first
  convention. If you want Monday-first, swap the day0..day6 button text.
