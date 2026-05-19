CallFilter v25.3 — full cumulative drop
=========================================

Six UX/UI/logic fixes addressing feedback on the Add New Rule card,
rule evaluation order, profile menu, and rule list readability.

============================================================
NEW IN v25.3
============================================================

1) RULE TYPE / ACTION BUTTONS NOW SHOW SELECTION STATE
---------------------------------------------------------
Previously: all three PREFIX/BETWEEN/SUFFIX buttons appeared identical
solid blue regardless of which was selected. Same for ✓ ACCEPT and
✗ REJECT.

Cause: Material3 theme was overriding the android:background drawable.
Fix: Replaced <Button> with <TextView> styled as buttons (with ripple).
Material3 ignores TextView, so the drawables apply cleanly. Text color
also flips between white (selected) and gray (unselected) so the state
is unmistakable.

2) ADD-NEW-RULE CARD REDESIGNED
---------------------------------------------------------
- Header: "+ ADD NEW RULE" with subtitle "Match by pattern, then accept or reject"
- Three numbered steps: "1. PATTERN TYPE", "2. PATTERN", "3. ACTION"
- Each section labeled so the flow is obvious
- Larger primary "+ ADD RULE" button at the bottom
- Single-line button text (no more "BETWE / EN" wrapping)

3) ACCEPT RULES NOW CHECKED FIRST
---------------------------------------------------------
Call evaluation order in CallBlockerService + CallStateReceiver:
   1. ACCEPT rules    ← explicit accept wins over everything (incl. Block-All)
   2. Block All Now
   3. REJECT rules
   4. Contacts-only mode
   5. Schedule allowlist
   6. Frequency bypass (overrides 2-5 reject only)

Added two new methods: rules.evaluateAccept(number) and
rules.evaluateReject(number) for clean separation.

4) CLOUD RULES PULLED ON EVERY LOGIN/RESUME
---------------------------------------------------------
Previously: rules were only pulled if local list was empty
("pullRulesFromCloudIfEmpty"). If you had even one local rule, the cloud
copy wasn't fetched.

Now: forcePullRulesFromCloud() runs on login AND on every onResume.
Local rules are replaced with what's in the cloud. UI refreshes 2s and 5s
after resume to catch the async HTTP completion.

5) RULES LIST CARDS REDESIGNED
---------------------------------------------------------
Previously: PREFIX badge had blue text on blue background — invisible.
Number, badge, action all on one line — paragraph-like.

Now:
   • Phone number is the prominent header (17sp bold)
   • Below it: side-by-side "PREFIX" + "✗ REJECT" badges, white text on
     colored pills (PREFIX = blue, ACCEPT = green, REJECT = red)
   • Delete X is a clean 40dp button on the right
   • More vertical space between rules (10dp margin)

6) PROFILE / ACCOUNT MENU IS NOW A FULL SCREEN
---------------------------------------------------------
Tapping the ⋮ icon top-right now opens a dedicated ProfileActivity
instead of a cramped AlertDialog. The screen has:

   👤 Identity card
      Shows your signed-in mobile number

   ✨ Subscription card
      Live status (Active / Inactive / Checking…)
      Plan name and renewal info
      "Manage subscription" button (deep-links to Play Store
       subscriptions page for this app) when active
      "View plans & subscribe" button when inactive

   ⚙️ Settings
      📇 Cloud contact sync (with explanation)
      🔐 Change PIN (row with chevron)

   🚪 Sign out (red row at bottom)

   Footer: "Call Filter · v1.0.25"

============================================================
ANDROID FILES CHANGED
============================================================
NEW
   ProfileActivity.java + activity_profile.xml
   drawable/badge_type.xml

MODIFIED
   AndroidManifest.xml         (registers ProfileActivity)
   res/layout/activity_main.xml (Add New Rule card rebuilt)
   res/layout/rule_item.xml    (vertical card layout, visible badges)
   MainActivity.java           (TextView refs, text-color flip,
                                ProfileActivity launch, force-pull on resume)
   RulesManager.java           (evaluateAccept + evaluateReject)
   CallBlockerService.java     (accept-first evaluation order)
   CallStateReceiver.java      (same)
   SyncManager.java            (forcePullRulesFromCloud method)
   LoginActivity.java          (force-pull on login)

============================================================
BACKEND / FRONTEND
============================================================
No changes. v25.3 is Android-only.

============================================================
DEPLOY
============================================================
cd D:\\callfilter
git pull origin main

# Extract this zip to e.g. C:\\temp\\v253, then:
robocopy C:\\temp\\v253\\callfilter-monorepo\\android  android  /E
robocopy C:\\temp\\v253\\callfilter-monorepo\\backend  backend  /E
robocopy C:\\temp\\v253\\callfilter-monorepo\\frontend frontend /E
copy C:\\temp\\v253\\callfilter-monorepo\\README.md .
copy C:\\temp\\v253\\callfilter-monorepo\\INTEGRATION_README.txt .

git status
git add .
git commit -m "v25.3 — Add New Rule UX, rule eval order, profile screen"
git push origin main

# Build Android in Android Studio: Run ▶ or Build → Build APK(s)

============================================================
WHAT TO TEST
============================================================
[ ] Tap PREFIX / BETWEEN / SUFFIX — selected one turns blue with white text;
    others stay dark with gray text
[ ] Tap ✓ ACCEPT / ✗ REJECT — selected one shows green/red; other is dark
[ ] "BETWEEN" text fits on one line
[ ] After logging in, all your cloud rules appear in the list within ~5 seconds
[ ] Tap ⋮ in top bar → opens full Profile screen (not AlertDialog)
[ ] Profile screen shows your mobile number, subscription status
[ ] If subscribed: "Manage subscription" button opens Play Store
[ ] Rule cards: number prominent, PREFIX + REJECT badges visible
[ ] Add an ACCEPT rule for a specific number, then make sure it overrides
    Block All Now (call from that number rings even with Block All on)
