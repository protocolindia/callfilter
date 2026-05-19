CallFilter v25.4 — full cumulative drop
=========================================

Four critical fixes addressing the disappearing-rules bug, subscription
blocking, cloud-rules-not-fetching, and the redundant + ADD RULE button.

============================================================
NEW IN v25.4
============================================================

FIX 1 — Disappearing rules (race condition)
--------------------------------------------
Symptom: Add a rule. It appears. 2 seconds later it disappears.

Cause: v25.3 introduced aggressive cloud-pulls on every onResume. They
raced against the just-uploaded /rules/sync — the pull would come back
with the stale (pre-add) cloud snapshot and OVERWRITE the local list,
wiping the rule the user just added.

Fix: cloud-pull happens ONCE per device, at first login after install.
After that, all changes are pushed up; cloud never overwrites local.
Tracked via "initial_sync_done" sentinel flag in sync_prefs.

Additional SAFETY: even when forcePullRulesFromCloud runs, if the cloud
returns FEWER rules than local has, it refuses to overwrite. Mark
initial-sync as done and log a warning.

Logout clears the sentinel, so the next login force-pulls fresh data.

FIX 2 — Cloud rules not fetching on reinstall
----------------------------------------------
Same fix as Fix 1 — the initial-sync flag is set only AFTER a successful
pull, so reinstall (which clears all device prefs) will always trigger
a fresh pull on first login.

Watch logcat for these lines after login:
   forcePullRules: 3 rules pulled, initial-sync flag set

If you instead see:
   forcePullRules skipped — initial sync already done

it means a previous device session already populated local rules.
Workaround: tap ⋮ → Sign out → log back in.

FIX 3 — Subscription paywall blocking everything
-------------------------------------------------
Symptom: Subscription page not loading plans / blocking app usage even
when no real Play Store products are configured.

Fix: NEW admin setting "subscription_required" (default FALSE on a
fresh install). When false, backend returns active:true for everyone
("status":"unrestricted","plan_name":"Unrestricted (dev mode)"). The
Android app sees this as active subscription and skips the paywall.

When you're ready to enforce subscriptions (after configuring Google
Play Billing), flip the toggle in admin Settings:
   Admin panel → Settings → Subscription gating → check "Require active
   subscription" → Save settings

FIX 4 — One-tap ACCEPT / REJECT
--------------------------------
Per Q5: removed the redundant "+ ADD RULE" button.

Now: enter a pattern, then tap ✓ ACCEPT to add it as a whitelist rule
OR tap ✗ REJECT to add it as a blocklist rule. Single tap = rule created.

Both buttons are always primary-styled (white text on solid color).
No "selected" state since there's nothing to select — tap = commit.

============================================================
BACKEND CHANGES
============================================================
backend/src/api.js
   /api/subscription/:user_id — checks subscription_required setting,
                                returns unrestricted state when false
   /api/check-account         — same

backend/src/admin.js
   PUT /admin/settings — accepts subscription_required key

backend/src/migrate.js
   default settings now include subscription_required: 'false'

frontend/src/pages/Settings.jsx
   NEW section "Subscription gating" with checkbox + green status banner

============================================================
ANDROID CHANGES
============================================================
SyncManager.java
   isInitialSyncDone() / clearInitialSyncFlag()
   forcePullRulesFromCloud() — now gated, safe, only-once-per-session

AuthManager.java
   logout() now clears initial_sync_done so next login re-pulls

MainActivity.java
   onResume no longer triggers cloud pull (race fix)
   removed btnAddRule + selectAction + currentAction
   addRule(String action) — called directly by ACCEPT/REJECT taps
   ACCEPT toast: "✓ ACCEPT rule added: +91XXX"
   REJECT toast: "✗ REJECT rule added: +91XXX"

res/layout/activity_main.xml
   removed "+ ADD RULE" button
   ACCEPT and REJECT buttons enlarged (56dp height) and always-primary
   step 3 label changed to "TAP TO ADD"

============================================================
DEPLOY
============================================================
cd D:\\callfilter
git pull origin main

# Extract zip to e.g. C:\\temp\\v254
robocopy C:\\temp\\v254\\callfilter-monorepo\\android  android  /E
robocopy C:\\temp\\v254\\callfilter-monorepo\\backend  backend  /E
robocopy C:\\temp\\v254\\callfilter-monorepo\\frontend frontend /E
copy C:\\temp\\v254\\callfilter-monorepo\\README.md .
copy C:\\temp\\v254\\callfilter-monorepo\\INTEGRATION_README.txt .

git status
git add .
git commit -m "v25.4 — rule race fix, subscription gating, one-tap add"
git push origin main

Railway redeploys both services. NO new migrations in v25.4 — but the
subscription_required setting will be seeded with default 'false' on
next backend boot (via migrate.js DEFAULT_SETTINGS).

If you want to set it manually right now:
   curl -X PUT https://api.app.onephone.pro/admin/settings \\
     -H "Content-Type: application/json" \\
     -H "Cookie: admin_session=..." \\
     -d '{"subscription_required":"false"}'

Or just open admin panel → Settings → look for new "Subscription gating"
section → uncheck the box → Save.

============================================================
POST-DEPLOY VERIFICATION
============================================================
1. Backend dev-mode check:
   curl https://api.app.onephone.pro/api/subscription/1
   Expected: "status":"unrestricted","active":true

2. Admin panel Settings page shows "Subscription gating" section with
   GREEN banner "Subscription gating OFF"

3. Build APK in Android Studio:
   cd D:\\callfilter\\android
   .\\gradlew.bat clean
   .\\gradlew.bat assembleRelease
   (Or: hamburger → Build → Build APK(s) → Build APK(s))

4. Install on phone. Log in with PIN.
   [ ] You should NOT see the paywall
   [ ] MainActivity should open directly
   [ ] Cloud rules should appear within a few seconds
     (Logcat: "forcePullRules: N rules pulled")

5. Add a test rule: type "9876", tap ✓ ACCEPT.
   [ ] Toast: "✓ ACCEPT rule added: +919876"
   [ ] Rule appears in list immediately
   [ ] Rule STAYS in list after 30 seconds (no race overwrite)

6. Reboot phone or kill app, relaunch:
   [ ] Rules persist
   [ ] No paywall

7. Tap ⋮ → Sign out → log back in:
   [ ] Cloud pull runs again (Logcat shows the pull)
   [ ] All rules restored
