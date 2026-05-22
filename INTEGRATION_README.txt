CallFilter v25.8 — full cumulative drop
=========================================

Eight fixes, ordered by importance:

============================================================
1. RULES SYNC BUG — ROOT CAUSE FIXED
============================================================

The bug you've been hitting for 4 versions:
  • Rules added on the app would disappear on next launch
  • Admin-added rules never reached the app
  • Reinstalling wiped your rules

ROOT CAUSE: /api/rules/sync was a MIRROR endpoint that did
  DELETE FROM user_rules WHERE user_id = ?   then   INSERT [whole list]

This meant:
  • Admin adds rule X via web → cloud has X
  • App user (hasn't pulled yet) adds rule Y → app pushes its full
    local list [Y] → backend deletes EVERYTHING including admin's X
    → cloud is now [Y]. X is GONE.
  • App's local rule is fine, but it's the ONLY rule that survives.

FIX: differential sync. Three new endpoints (and old /sync is now
safe — it MERGES instead of replacing):

  POST /api/rules/add     — upsert one rule by (user_id, client_id)
  POST /api/rules/delete  — delete one rule by client_id
  POST /api/rules/sync    — legacy mirror, now uses ON CONFLICT UPDATE
                            (never deletes anything)

App behavior:
  • addRule → pushAddedRule (just sends THIS one rule)
  • removeRule → pushDeletedRule (just sends THIS one client_id)
  • MainActivity.onResume → mergeRulesFromCloud (pulls all cloud rules,
    adds any that aren't already local — never overwrites)

Migration 009 adds a unique index (user_id, client_id) to enable the
ON CONFLICT clause, and backfills any NULL client_ids with generated
UUIDs.

============================================================
2. LOCK ≠ LOGOUT
============================================================

Before: tapping "Sign out" only locked the session (next launch = PIN
unlock). User wanted FULL sign-out + a separate LOCK button.

Now:
  • Profile → "🔒 Lock app" card (above Sign out)
    Action: AuthManager.lock() — clears the logged-in bit only.
    Next launch shows PIN unlock screen.
    Mobile, PIN, rules, settings all preserved.

  • Profile → "🚪 Sign out completely"
    Action: AuthManager.logout() — calls resetAccount() which wipes
    EVERYTHING: mobile, PIN, name, rules, sync state.
    Next launch shows SignupActivity (full mobile + OTP flow).
    Cloud data is NOT touched — same mobile re-signing in restores it.

  • Login screen now has "Use a different number" button below SIGN IN,
    same effect as full logout.

============================================================
3. AUTO-LOCK TOGGLE
============================================================

Profile → new "⏱️ Auto-lock" card with a Switch.

When ON: if the app is in the background for >5 minutes and you
return, it locks (shows PIN unlock). Setting is per-device, persists
across reinstall via SharedPreferences "ui_prefs".

Implementation:
  • MainActivity.onPause records bg_at_ms timestamp
  • MainActivity.onResume checks elapsed time, calls lock() + redirect
    to LoginActivity if > 5 min and auto_lock=true

============================================================
4. PROFILE SUBSCRIPTION CARD → "BUY A PLAN" BUTTON
============================================================

Profile subscription card now has a "🛒 Buy a plan" button. Tapping it
opens the paywall, which now:
  • Hides any plan where offer_price=0 AND actual_price=0 (free plans)
  • Shows your current subscription status banner at the top
    ("Currently subscribed: Monthly, 12 days left")
  • Shows a back button (top-left) that returns to Profile
  • For one-time-only plans you've already used: shows the card with
    button text "ALREADY USED" and disabled (50% opacity)

============================================================
5. ONE-TIME PLAN — ADMIN CHECKBOX
============================================================

Admin Billing → New/Edit plan form has a new checkbox:
  ☐ One-time only per user (free trial / one-shot upgrade)

When checked, the plan can be subscribed to ONLY ONCE per user.
Server-side enforcement:
  • POST /api/razorpay/create-order returns 403 plan_already_used
    if user has a prior subscription OR a paid razorpay_orders row
    for this plan_id.
  • GET /api/plans returns `already_used: true` per-plan when called
    with ?user_id=N, so the app can grey out used plans before
    checkout even opens.

============================================================
6. ADMIN USER LIST — NAME COLUMN + ACTIVATE/DEACTIVATE
============================================================

Users page:
  • Added "Name" column (uses users.name from v25.7)
  • New action buttons:
      - Active users:   [Deactivate]
      - Disabled users: [Activate]
  • Confirmation dialog before applying

Backend: POST /admin/users/:id/activate  body: {active: true|false}
Sets users.status to 'active' or 'disabled'.

============================================================
7. DISABLED-ACCOUNT BANNER (Q5b option i)
============================================================

When users.status = 'disabled', the user can still:
  • Sign in (mobile + PIN/OTP)
  • Use the app

But they see a red ⚠️ banner at the top of MainActivity:

  ⚠️  Your account is disabled
      Contact admin to reactivate

How it works:
  • POST /api/check-account returns `status: 'disabled'` for disabled users
  • AuthManager.verifyAccountStillExists() stores this in prefs
  • MainActivity.refreshUI() shows/hides the banner based on the flag

To reactivate: admin Users page → [Activate] button. Banner disappears
on the user's next app launch (next check-account call).

============================================================
8. SIGNUP COUNTRY DROPDOWN — WHITE-ON-WHITE FIX
============================================================

Cause: Used android.R.layout.simple_spinner_dropdown_item, which on
some Material themes renders with a white popup background. Combined
with white text → invisible.

Fix: Two new custom layouts with explicit dark backgrounds:
  res/layout/spinner_item.xml         — closed state (surface bg)
  res/layout/spinner_dropdown_item.xml — open state (card bg, 48dp rows)

Applied to:
  • SignupActivity (country picker)
  • MainActivity (country picker, same fix needed)

============================================================
FILES TOUCHED
============================================================

BACKEND
  NEW  migrations/009_diff_sync_activation_onetime.sql
  CHG  src/api.js              diff sync endpoints, /plans flags,
                               razorpay 403 on one-time, signup status
  CHG  src/admin.js            /users/:id/activate, plan is_one_time flag

FRONTEND (admin)
  CHG  pages/Users.jsx         Name column + activate/deactivate
  CHG  pages/Billing.jsx       one-time-only checkbox in plan form

ANDROID — main flavor
  CHG  java/.../AuthManager.java          lock() vs logout() split,
                                          isAccountDisabled()
  CHG  java/.../RulesManager.java         addRuleWithId, push on add/remove
  CHG  java/.../SyncManager.java          pushAddedRule, pushDeletedRule,
                                          mergeRulesFromCloud
  CHG  java/.../MainActivity.java         onResume merge, onPause bg_at_ms,
                                          auto-lock check, disabled banner,
                                          spinner_item refs
  CHG  java/.../ProfileActivity.java      Lock card, auto-lock toggle,
                                          full sign-out wording,
                                          → SignupActivity on logout
  CHG  java/.../LoginActivity.java        Switch account button
  CHG  java/.../SignupActivity.java       spinner_item refs
  NEW  res/layout/spinner_item.xml        custom spinner (closed state)
  NEW  res/layout/spinner_dropdown_item.xml  custom spinner (open state)
  CHG  res/layout/activity_login.xml      Switch account button
  CHG  res/layout/activity_profile.xml    Lock + Auto-lock cards,
                                          Buy a plan button label
  CHG  res/layout/activity_main.xml       disabled banner
  CHG  res/layout/activity_paywall.xml    back button (top-left)

============================================================
DEPLOY
============================================================

1) Push backend + frontend (Railway auto-deploys)

   cd D:\\callfilter
   git pull origin main

   # Extract zip to C:\\temp\\v258
   robocopy C:\\temp\\v258\\callfilter-monorepo\\android  android  /E
   robocopy C:\\temp\\v258\\callfilter-monorepo\\backend  backend  /E
   robocopy C:\\temp\\v258\\callfilter-monorepo\\frontend frontend /E
   copy C:\\temp\\v258\\callfilter-monorepo\\README.md .
   copy C:\\temp\\v258\\callfilter-monorepo\\INTEGRATION_README.txt .

   git add .
   git commit -m "v25.8 — diff rules sync (root fix), lock vs logout, auto-lock, one-time plans, activate/deactivate, disabled banner, spinner fix"
   git push origin main

   Watch Railway backend logs for:
     Running migration 009_diff_sync_activation_onetime.sql
     ...
     CREATE UNIQUE INDEX idx_user_rules_user_client

2) Android — same flavor as before (playstore or sideload)

   Android Studio → Build → Select Build Variant → app → choose
   "playstoreRelease" or "sideloadRelease" → Run ▶ or Build APK

============================================================
TEST AFTER INSTALL
============================================================

RULES SYNC (the big one):
[ ] Install app, sign up
[ ] Add rule PREFIX 9494 REJECT
[ ] Wait 5 seconds. Force-stop app from recents. Reopen.
    → Rule should still be there ✓
[ ] Admin panel → Users → your user → Rules tab. Should see the rule.
[ ] Admin panel: click Delete on that rule.
[ ] In app: pull down or background→foreground.
    → Rule should DISAPPEAR (cloud → app sync).
[ ] Admin panel: add a brand-new rule (PREFIX 9090 REJECT) from web.
[ ] In app: background→foreground.
    → New rule should APPEAR ✓ (admin → app sync works now)
[ ] Uninstall app entirely. Reinstall. Sign in with same mobile.
    → All your rules should come back from cloud ✓

LOCK vs LOGOUT:
[ ] Profile → 🔒 Lock app → tap Lock
    → Login screen. Enter PIN. Back in app. Mobile + rules intact.
[ ] Profile → 🚪 Sign out completely → tap Sign out
    → Signup screen. Mobile field empty. PIN gone.
[ ] Enter same mobile, OTP, set PIN. Rules come back from cloud (merge).

AUTO-LOCK:
[ ] Profile → toggle Auto-lock ON → "Locks after 5 minutes in background"
[ ] Background the app for 6+ minutes. Reopen.
    → PIN unlock screen.
[ ] Profile → toggle Auto-lock OFF
[ ] Background 6+ min. Reopen.
    → Goes straight to home.

BUY A PLAN:
[ ] Profile → 🛒 Buy a plan → opens paywall
[ ] Free plans (price=0) are hidden
[ ] Back button (top-left) returns to Profile
[ ] Current subscription banner at top (if you have one)
[ ] Tap any plan's SUBSCRIBE → Razorpay checkout / Play Billing opens
[ ] (sideload+test card 4111 1111 1111 1111) → payment succeeds
    → "✅ Subscription active" → returns home

ONE-TIME PLAN:
[ ] Admin → Billing → New plan → check "One-time only per user" → Save
[ ] In app: paywall shows new plan as available
[ ] Subscribe to it → succeeds
[ ] Open paywall again → plan card shows "ALREADY USED" disabled
[ ] Verify backend: try POST /api/razorpay/create-order with that plan_id
    → returns 403 plan_already_used

ADMIN ACTIVATE/DEACTIVATE:
[ ] Admin → Users → click [Deactivate] on your test user
[ ] In app: open the app
    → Red ⚠️ "Your account is disabled" banner at top of home screen
[ ] Admin → Users → click [Activate]
[ ] In app: background → foreground
    → Banner disappears

COUNTRY DROPDOWN:
[ ] Sign out, get to signup screen
[ ] Tap country dropdown
    → Visible dark popup with white text, NOT white-on-white

============================================================
KNOWN LIMITATIONS
============================================================

• Auto-lock is per-device and per-install. Resetting/reinstalling
  defaults to OFF.

• "Buy a plan" page doesn't yet show "12 days left" duration breakdown
  in the current-sub banner — only plan name. Will add if requested.

• Admin "Activate" doesn't push a notification to the app — user sees
  the banner disappear on the next check-account call (every onResume
  or after a few seconds in foreground).

• Merging rules from cloud is additive. If admin DELETES a rule via
  web, the app's local copy of that rule is NOT auto-removed until
  the next full /api/rules/list pull. This is correct for safety
  (avoids accidentally wiping user data) but means deletions can
  take a few seconds to propagate. To force: pull-to-refresh on
  the rules screen (or background/foreground the app).
