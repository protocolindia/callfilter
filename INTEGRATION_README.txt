CallFilter v25.17 — items 1-5 from your batch
================================================

This drop implements items 1-5 from your 8-item list. Items 6-8
(global blocklist, admin CRUD, multi-user roles) are queued for
v25.18 and v25.19 respectively — they're each session-sized features
that deserve their own focused build.

============================================================
ITEM 1 — T&C / PRIVACY CHECKBOX ON SIGNUP
============================================================

Signup page now has an explicit checkbox above the CONTINUE button:

   [☐] I accept the Terms & Conditions and Privacy Policy
            CONTINUE
        Terms & Conditions  ·  Privacy Policy

  • Tapping the label (not just the box) toggles the checkbox
  • "Terms & Conditions" link opens https://app.onephone.pro/terms
  • "Privacy Policy" link opens https://app.onephone.pro/privacy
  • Pressing CONTINUE without the box ticked → toast and blocked
  • Hidden in login_mode (user already agreed when they signed up)

New /privacy page added to the admin frontend (Privacy.jsx). The
existing /terms page is unchanged.

============================================================
ITEM 2 — LOGOUT BACK-STACK FIX
============================================================

After Sign-out, swipe-back used to return to the Profile screen. The
issue was that `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK`
only clears activities ABOVE the destination in the same task — but
LoginActivity wasn't on the stack yet, so it cleared nothing.

Fix: use `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` plus
`finishAffinity()`. This kills every activity in the current task and
starts LoginActivity as the new root. Swipe-back from there → exits
the app, as expected.

Same fix applied to:
   • ProfileActivity → Sign out
   • ProfileActivity → Lock
   • LoginActivity → Use a different number

============================================================
ITEM 3 — CONSOLIDATED PERMISSIONS SCREEN (DENY = BLOCKED)
============================================================

PermissionsActivity completely rewritten. Shows all required
permissions on a single screen with the reason for each:

   🛡️ Permissions
   CallFilter needs these permissions to detect, evaluate, and block
   unwanted calls. All processing happens on your device.

     ┌─────────────────────────────────────────────────┐
     │ Phone state                                  ✗ │
     │ Required. Lets the app detect when a call is    │
     │ ringing so it can decide whether to block it.   │
     ├─────────────────────────────────────────────────┤
     │ Contacts                                     ✗ │
     │ Required for Contacts-Only Mode and to          │
     │ recognise known callers.                        │
     ├─────────────────────────────────────────────────┤
     │ Call log                                     ✗ │
     │ Required to show your recent calls and detect   │
     │ the number after a call ends (for the popup).   │
     ├─────────────────────────────────────────────────┤
     │ Notifications                                ✗ │
     │ Required for the post-call "Block this number?" │
     │ alert when the overlay can't draw.              │
     └─────────────────────────────────────────────────┘
              [GRANT ALL]

  • Tap GRANT ALL → Android runs the system dialog for each missing
    permission in sequence
  • If user denies any: red warning shows "CallFilter cannot work
    without these permissions. Tap GRANT ALL to try again."
  • If user picks "Don't ask again": Settings shortcut button appears
  • Back button is DISABLED until all critical permissions are granted
    (toast: "Please grant the required permissions to continue")
  • NO "Skip" button — items 3's "should not move forward" requirement

Wired into the flow at three entry points:
   • SetPinActivity → PermissionsActivity → MainActivity   (new signup)
   • OtpActivity (login_mode) → … → PermissionsActivity     (re-login)
   • LoginActivity PIN unlock → PermissionsActivity → MainActivity

PermissionsActivity is idempotent — if all permissions are already
granted, it immediately routes to MainActivity. So you don't see
this screen on a returning launch where everything is in order.

============================================================
ITEM 4 — POST-CALL POPUP: DIAGNOSTIC LOGGING + FALLBACK
============================================================

You said the popup STILL isn't appearing. Without logcat I can't see
why for certain, so I've added detailed branch-by-branch logging plus
a defensive try/catch around overlay creation that falls back to a
notification if WindowManager throws.

In PostCallBlockOverlay.offer():

   D/PostCallOverlay: offer() called with number=+919876xxx
   D/PostCallOverlay:   → skipped: rule already exists (type=prefix pattern=+919876)

OR:

   D/PostCallOverlay: offer() called with number=+919876xxx
   D/PostCallOverlay:   → skipped: number is in contacts

OR:

   D/PostCallOverlay: offer() called with number=+919876xxx
   D/PostCallOverlay:   display path: NOTIFICATION (canDrawOverlays=false)
   D/PostCallOverlay:   → notification posted

OR:

   D/PostCallOverlay: offer() called with number=+919876xxx
   D/PostCallOverlay:   display path: OVERLAY (canDrawOverlays=true)
   D/PostCallOverlay:   overlay failed: BadTokenException — falling back to notification

Combined with CallStateReceiver's existing logging, you'll see the
full path from PHONE_STATE broadcast → number resolution → offer().

To capture:

   adb logcat -c
   # make a test call from an unknown number, ring 3s, disconnect
   adb logcat -d -s CallStateReceiver:V PostCallOverlay:V CallBlockerService:V

Paste the output and I'll fix the exact cause in v25.18.

============================================================
ITEM 5 — REASON PICKER ON RECENT CALLS BLOCK
============================================================

When you tap a number in Recent Calls and choose "Block", the flow
now matches the post-call popup behaviour:

   1. Confirmation dialog: "Block this number?" → Block
   2. BlockReasonPickerActivity opens immediately
       • Adds the PREFIX/REJECT rule (blockNow=true intent extra)
       • Records the blocked-call entry
       • Shows reason picker (Spam / Cybercrime / etc + Skip)
   3. Pick a reason + Save → reason synced to backend
   4. Or tap Skip → block still recorded, no reason
   5. Web admin → user → Blocked Calls tab shows the reason

This uses BlockReasonPickerActivity (already exists from v25.14).
RecentCallsActivity just routes to it instead of calling
RulesManager.addRule directly.

============================================================
FILES TOUCHED
============================================================

ANDROID
  CHG res/layout/activity_signup.xml          T&C checkbox + Terms/Privacy links
  CHG res/layout/activity_permissions.xml     Full rewrite — list view + status
  NEW res/layout/permission_row.xml           Single permission row template
  REWR java/.../PermissionsActivity.java      Consolidated screen with deny-block
  CHG java/.../SignupActivity.java            T&C validation + openUrl helper
  CHG java/.../ProfileActivity.java           Sign out flags + finishAffinity()
  CHG java/.../LoginActivity.java             Switch-account fix + PIN→Permissions
  CHG java/.../RecentCallsActivity.java       Block routes through reason picker
  CHG java/.../PostCallBlockOverlay.java      Diagnostic logging + try/catch

FRONTEND admin
  NEW pages/Privacy.jsx                       /privacy route
  CHG main.jsx                                Privacy route registered

No backend changes. No migrations.

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

robocopy F:\\app\\CallManager\\callfilter-v25.17-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.17-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.17-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.17-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.17 — T&C checkbox, logout fix, consolidated perms, reason picker on RecentCalls"
git push origin main

# Then rebuild APK

============================================================
POST-DEPLOY TESTS
============================================================

T&C checkbox:
[ ] Signup screen shows checkbox above CONTINUE
[ ] Tapping label toggles checkbox
[ ] Pressing CONTINUE without checking → toast blocks it
[ ] Terms link opens https://app.onephone.pro/terms
[ ] Privacy link opens https://app.onephone.pro/privacy

Logout:
[ ] Profile → Sign out → confirm → LoginActivity opens
[ ] Press back / swipe back → app exits (does NOT show Profile)

Permissions:
[ ] Fresh install, OTP → Set PIN → permissions screen with all 4 (or 3
    if Android <13) rows visible, each with ✗ initially
[ ] Tap GRANT ALL → system dialogs run sequentially
[ ] If you deny one: red warning + GRANT ALL still works
[ ] Press back: toast "Please grant the required permissions to continue"
[ ] After all granted → MainActivity opens automatically

Recent Calls block:
[ ] Recent Calls tab → long-press any number → Block
[ ] Block dialog opens, tap Block
[ ] Reason picker activity opens — pick "Spam call" → Save
[ ] Admin → user → Blocked Calls → entry has "Spam call" reason

Post-call popup (diagnostic):
[ ] Capture logcat as described in item 4 above
[ ] Paste the output to me — I'll fix it in v25.18 with the exact cause
