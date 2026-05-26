CallFilter v25.17.1 — hotfix for v25.17 issues
================================================

This is a hotfix sprint, NOT v25.18. Still pending for separate drops:
  • v25.18 — global blocklist DB + admin CRUD (your items 6+7)
  • v25.19 — multi-user admin with roles (your item 8)

============================================================
BUG A — PERMISSION PROMPTS COLLIDING AT STARTUP
============================================================

The v25.17 design was: PermissionsActivity is a HARD GATE — you can't
proceed without granting. Plus MainActivity.onResume was firing THREE
separate system prompts (overlay, notifications, default screening)
all at once. Result: stacked windows, can't dismiss cleanly.

Fix per Q1=b (soft mode + feature-gate redirect):

  • Permissions gate REMOVED from boot flow:
       SetPinActivity → MainActivity directly (was → Permissions → Main)
       LoginActivity PIN unlock → MainActivity directly
  • MainActivity.onResume NO LONGER auto-fires any system prompt
  • PermissionsActivity completely rewritten as a "manage" screen,
    reachable from Profile → 🛡️ Permissions

  Each permission has its OWN row with its own Grant button. Tapping
  Grant fires ONE system prompt — never two stacked windows.

  Rows shown:
       Phone state          (runtime)
       Contacts             (runtime)
       Call log             (runtime)
       Notifications        (runtime, Android 13+)
       Display over other apps  (Settings page)
       Default call screening app  (RoleManager)  ← critical for blocking

  All open one system interaction at a time, in response to user tap.

============================================================
NEW PROFILE FEATURES
============================================================

🛡️ Permissions row — opens PermissionsActivity (above).

🎁 Refer a friend row — opens Android share chooser. Pick WhatsApp,
   SMS, email, anything that handles text. Message text:

      "I've been using Call Filter to block spam, scam, and unwanted
       calls. It works great — you should try it.

       Android: https://play.google.com/store/apps/details?id=pro.onephone.callfilter
       iOS: https://apps.apple.com/app/onephone

       — shared via Call Filter"

ℹ️ About card — at the bottom. Shows app name + dynamic version,
   read from PackageManager so it always matches the actual installed
   APK. Example: "Version 1.0.25 (build 25)".

============================================================
BUG B — CONTACT PICKER CRASHES
============================================================

You said "when I select exception number from phone book in multiple
places, the app is crashing". Most likely cause: the calling activity
gets destroyed by the system while ContactPickerActivity is open
(low memory or strict OEM background limits), then when the picker
returns, the caller's `editing` member is null and the result handler
NPEs.

Fixed by hardening every onActivityResult call site:

  • EditScheduleActivity — null-guards `editing`, wraps body in
    try/catch, shows a toast and finishes instead of crashing
  • MainActivity (Block All picker handler) — same treatment
  • ContactPickerActivity itself — outer try/catch around the
    ContentResolver.query() in case it throws on stricter OEM builds,
    plus safe cursor close

If you still get a crash, please run:

    adb logcat -d -s AndroidRuntime:E -t 200

and paste the stack. The hardening covers the most likely cause but
some OEMs (Xiaomi/Vivo/Realme) have unusual content provider quirks.

============================================================
BUG C — "DON'T HAVE ACCOUNT? SIGN UP" ON PIN ENTRY PAGE
============================================================

LoginActivity PIN mode now hides the bottom "Don't have an account?
Sign up" link. The link is still visible in MOBILE mode (when no
local PIN exists) since that's where new users actually need it.

============================================================
BUG D — REASON PICKER NOT APPEARING (MANUAL OR POST-CALL)
============================================================

Two real bugs found and fixed:

1. BlockReasonsCache had a wrong URL:
       /api/block-reasons  →  /api/settings/block-reasons
   So the cache refresh never succeeded. (The picker still got
   defaults from the in-memory fallback, so this alone didn't cause
   the no-show.)

2. BlockReasonPickerActivity manifest theme was
   Theme.Translucent.NoTitleBar. On Android 12+/13+ with strict
   background-activity-launch rules, the activity launches but the
   AlertDialog inside silently fails to render — the user sees
   nothing. Changed to Theme.AppCompat.Dialog — a proper dialog
   theme that renders reliably.

Also added Log.d lines at every step of BlockReasonPickerActivity
so if it STILL doesn't appear, the next logcat will show exactly
where it stops:

   D/BlockReasonPicker: onCreate: number=+919876xxx blockNow=true
   D/BlockReasonPicker: reasons available: 7
   D/BlockReasonPicker: dialog.show() called

If you see all three but no picker → it's a device-specific
overlay/dialog issue. If you DON'T see "dialog.show() called" →
something is throwing before that line.

============================================================
FILES TOUCHED
============================================================

ANDROID — boot flow
  CHG java/.../MainActivity.java
      Removed maybePromptOverlayPermission, maybePromptNotificationPermission,
      checkBlockingStatus from onResume — no more startup window storm
  CHG java/.../SetPinActivity.java
      → MainActivity directly (was → PermissionsActivity)
  CHG java/.../LoginActivity.java
      PIN unlock → MainActivity directly
      PIN mode hides the "Sign up" link

ANDROID — Permissions screen rewrite
  REWR java/.../PermissionsActivity.java
       Per-row Grant buttons, no boot gate, includes overlay + role rows

ANDROID — Profile additions
  CHG res/layout/activity_profile.xml
      🛡️ Permissions row + 🎁 Refer a friend row + ℹ️ About card
  CHG java/.../ProfileActivity.java
      Wires both new rows + dynamic version + shareReferral()

ANDROID — reason picker fix
  CHG AndroidManifest.xml
      BlockReasonPickerActivity → Theme.AppCompat.Dialog
  CHG java/.../BlockReasonsCache.java
      Fixed URL to /api/settings/block-reasons
  CHG java/.../BlockReasonPickerActivity.java
      Added Log.d at every step for diagnostics

ANDROID — contact picker crash hardening
  CHG java/.../EditScheduleActivity.java
      onActivityResult null-guards editing + try/catch
  CHG java/.../MainActivity.java
      Block All picker result wrapped in try/catch
  CHG java/.../ContactPickerActivity.java
      Outer try/catch around loadContacts + applyFilter

No backend / frontend changes. No migrations.

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

robocopy F:\\app\\CallManager\\callfilter-v25.17.1-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.17.1-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.17.1-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.17.1-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.17.1 — soft permissions, Profile rows, picker theme fix, contact crash guards"
git push origin main

# Rebuild APK in Android Studio

============================================================
POST-DEPLOY TESTS
============================================================

[ ] Fresh install + signup → after Set PIN → MainActivity opens
    directly, no system prompts firing on top of each other

[ ] Profile screen shows three new rows above Logout:
       🛡️ Permissions
       🎁 Refer a friend
       ℹ️ About (card with version number)

[ ] Tap 🛡️ Permissions → 6 rows (or 5 on Android <13):
       Phone state / Contacts / Call log / Notifications / Display over other apps / Default call screening app
    Each granted permission shows ✓ (green). Each missing shows
    "Grant" (clickable). Tap Grant on Default call screening app →
    Android role picker → pick CallFilter → only this one prompt fires.

[ ] Refer a friend → share chooser opens → pick WhatsApp / Messages /
    Gmail → message contains both app store links

[ ] PIN entry page → no "Don't have an account? Sign up" link visible
    at the bottom. Mobile-entry page DOES show it.

[ ] Recent Calls → tap a number → Block → reason picker dialog appears
    showing the 7 default reasons + Skip + Save. Pick one → Save →
    admin sees the reason in Blocked Calls tab.

[ ] Edit a Schedule → tap "Exceptions" / pick contacts → select some
    contacts → Done. Returns to schedule editor without crashing.
    Repeat in Block All Now dialog.

If post-call popup STILL doesn't appear OR reason picker STILL doesn't
appear on Recent Calls block, paste:

    adb logcat -d -s CallStateReceiver:V PostCallOverlay:V \
                    BlockReasonPicker:V CallBlockerService:V

The new BlockReasonPicker log lines will tell us if the activity
launches but the dialog never shows (device-specific) or if something
throws before the dialog can be built.
