CallFilter v25.16 — login flow simplified + blocking diagnostics
==================================================================

This release fixes the things I'm confident about, and adds verbose
logging to surface what's actually going wrong with the call blocking
regression you reported.

============================================================
FIX 1 — REMOVED "RESTORE PURCHASE" BUTTON
============================================================

The button on the paywall is now hidden (kept the id in the layout
so PaywallActivity's findViewById doesn't NPE on it).

If you want it back later, change one line in activity_paywall.xml:
   android:visibility="gone"   →   android:visibility="visible"

============================================================
FIX 2 — LOGIN GOES STRAIGHT TO OTP (NO INTERMEDIATE PAGE)
============================================================

Old flow:
   LoginActivity (mobile) → CONTINUE
     → SignupActivity in login_mode ("Verify your mobile" with the
       same mobile field) → SEND OTP
     → OtpActivity (enter code)

New flow (v25.16):
   LoginActivity (mobile) → CONTINUE
     → /api/check-account validates the number exists
     → /api/signup fires (sends OTP, idempotent for existing users)
     → OtpActivity (enter code)         ← skips the middle page

LoginActivity.handleContinue now calls AuthManager.startSignup
directly on success, then routes to OtpActivity. The "Verify your
mobile" screen is no longer shown during login. It still exists in
the codebase as the OTP-send step of SignupActivity for fresh sign-ups.

============================================================
FIX 3 — OTP PAGE: RESEND COUNTDOWN
============================================================

OtpActivity now shows:
   • 6-digit OTP input (was already 6-digit max but the label said
     "4-DIGIT CODE" — unchanged for now since it works either way)
   • "Resend code in 30s" — disabled, ticks down every second
   • After 30s: "Resend OTP" — tappable, re-sends OTP via /api/signup
   • "Use a different number" link below — back to LoginActivity

On resend success: countdown restarts (so user can't spam-resend).
On resend failure: button re-enables immediately with error toast.

If you tap "Resend OTP", a new OTP is sent to the same number. If
backend setting otp_show_in_response=true is enabled, the new OTP
auto-fills the input.

============================================================
FIX 4 — DIAGNOSTIC LOGGING FOR BLOCKING REGRESSION
============================================================

You said: blocking (prefix / suffix / range) stopped working in a
recent version. Confirmed Caller ID & spam app is still set to
CallFilter. Reinstalling the previous version restores blocking.

I can't diagnose without seeing what the device actually does when a
call comes in. The CallBlockerService.onScreenCall() code path looks
identical to the version that works — but something is clearly
different at runtime.

v25.16 adds these log lines to CallBlockerService:

   D/CallBlockerService: === onScreenCall ENTERED, number=+919876xxx ===
   D/CallBlockerService: State: rules=3 subActive=true subChecked=true
   D/CallBlockerService:   rule: type=prefix pattern=+91140 action=reject
                                 → matches(+919876xxx)=false
   D/CallBlockerService:   rule: type=prefix pattern=+919876 action=reject
                                 → matches(+919876xxx)=true
   D/CallBlockerService: VERDICT: REJECT (type=prefix pattern=+919876)

If you see ALL those lines: blocking is working correctly.

If you see "onScreenCall ENTERED" but NO matching rule:
   → rules don't match the incoming number format. The pattern is
     stored as you typed it; the incoming number on Android 10+ uses
     E.164 format like "+919876543210". Some devices give "9876543210"
     without country code. The log shows you exactly what comes in.

If you DON'T see "onScreenCall ENTERED" at all when a call rings:
   → CallFilter is not the active screening app at the OS level. The
     setting may show "CallFilter" but the system isn't actually
     binding the service. Possible causes:
       • Another app (Truecaller, Hiya, Samsung default) is registered
         as a competing screener and won out
       • Battery optimisation killed the service binding
       • System needs a reboot after the app was set as default

   To force a re-bind:
       1. Settings → Apps → Default apps → Caller ID & spam app
       2. Select "None" or "Default"
       3. Reboot the phone
       4. Set "CallFilter" again
       5. Make a test call

   If still nothing in logcat, send me:
       adb logcat -s CallBlockerService:V CallStateReceiver:V

   and I'll see what's happening.

============================================================
WHY POST-CALL POPUP ISN'T APPEARING
============================================================

Same diagnostic story. v25.14 added a CallLog fallback in the
receiver. To see what's happening:

   adb logcat -s CallStateReceiver:V PostCallOverlay:V

Look for these tags after a call:
   PHONE_STATE event: state=RINGING
   PHONE_STATE event: state=OFFHOOK    (or skipped if not answered)
   PHONE_STATE event: state=IDLE
   [1.2s later] Call ended — offering popup for +919...
   [or] Call ended but no number available — popup skipped

If "popup skipped" appears, none of the three number-source paths
worked. Possible reasons:
   • CallLog not yet written (rare; the 1.2s delay usually handles it)
   • READ_CALL_LOG permission revoked

If the popup DOES try to show but you don't see it:
   • SYSTEM_ALERT_WINDOW (Display over other apps) revoked
   • POST_NOTIFICATIONS (Android 13+) revoked
   • Battery optimiser killed the receiver before it could draw

The PostCallOverlay tag in logcat tells you which fallback fired.

============================================================
FILES TOUCHED
============================================================

ANDROID
  CHG res/layout/activity_paywall.xml
      btnRestore hidden (visibility=gone)
  CHG java/.../LoginActivity.java
      onSuccess callback wrapped in runOnUiThread
      (other login→OTP refactor was already in place from prior session)
  CHG java/.../OtpActivity.java
      30s resend countdown timer
      Resend OTP wired to AuthManager.startSignup
      "Use a different number" link → back to LoginActivity
      onDestroy cancels the timer to avoid leaks
  CHG java/.../CallBlockerService.java
      verbose logging at entry, per-rule, and verdict points

No backend changes. No migrations. No frontend changes.

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

robocopy F:\\app\\CallManager\\callfilter-v25.16-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.16-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.16-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.16-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.16 — login goes straight to OTP + resend countdown + diagnostic logs"
git push origin main

# Then in Android Studio: Build → Rebuild Project → Generate Signed APK

============================================================
POST-DEPLOY TESTS
============================================================

[ ] Paywall: open from Profile → no "Restore purchase" button visible

[ ] Login flow:
    Sign out completely → LoginActivity (mobile mode)
    Enter your registered mobile → CONTINUE
    → directly opens OtpActivity (NO "Verify your mobile" page)
    → first 30s: button shows "Resend code in 29s" → "28s" → … → "0s"
    → after 30s: button becomes "Resend OTP" (tappable)
    → "Use a different number" link → back to LoginActivity
    → Enter the OTP, Verify → MainActivity

[ ] Resend:
    Wait for countdown, tap Resend OTP → toast "OTP sent again"
    → countdown restarts at 30s

[ ] Blocking diagnostic — make a test call from a number matching one
    of your prefix rules, then run:
       adb logcat -s CallBlockerService:V
    Paste me the output. I'll diagnose from there.

[ ] Popup diagnostic — make a test call from an unknown number not in
    contacts, let it ring then disconnect, then run:
       adb logcat -s CallStateReceiver:V PostCallOverlay:V
    Paste me the output. I'll diagnose.
