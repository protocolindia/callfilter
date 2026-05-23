CallFilter v25.15 — three follow-up fixes from v25.14 test
=============================================================

ONE-LINE: payment integer error, login flow showing signup screen
mid-flow, and "SUBSCRIBE" button text when already subscribed.

============================================================
FIX 1 — "Payment error: invalid input syntax for type integer: '29.00'"
============================================================

Root cause: in v25.13 I changed amount_paid storage to
`(orderRow.amount_paise / 100).toFixed(2)` to keep rupees as decimal.
But subscriptions.amount_paid is declared INTEGER in migration 005.
Postgres rejected "29.00" — a string with decimals — as not castable
to integer. Payment verification failed → user saw the error toast.

Fix: store the integer paise value directly.

   Before: amount_paid = (amount_paise / 100).toFixed(2)  → "29.00" ✗
   After:  amount_paid = amount_paise                     → 2900    ✓

UserDetail.jsx accordingly divides by 100 when displaying:
   Display: ₹{(amount_paid / 100).toFixed(2)}  → ₹29.00

============================================================
FIX 2 — Login bouncing through "Sign up" page
============================================================

When a returning user signs in on a new device (or after full logout),
LoginActivity routes them through SignupActivity (with login_mode=true)
to send an OTP and let them set a new PIN. Technically correct — we
need OTP verification before granting access without a local PIN — but
the screen still LOOKED like a signup form (name field visible, "Block
unwanted calls" title, "Already have account? Sign in" cross-link).
User reasonably thought "why is it asking me to sign up again?"

Fix: in login_mode the screen now looks like a verification step:
   • Title: "Verify your mobile"  (was "Block unwanted calls")
   • Name input + "YOUR NAME" label HIDDEN
   • Continue button text: "SEND OTP"  (was "Continue")
   • Bottom "Already have an account? Sign in" link HIDDEN
     (you're already in the sign-in flow)

Mobile field + country picker remain visible & editable in case the
user prefilled the wrong number.

============================================================
FIX 3 — "SUBSCRIBE" button → "EXTEND SUBSCRIPTION"
============================================================

When user already has an active sub, every plan card showed a
"SUBSCRIBE" button. The page title already said "Extend plan" but the
button was inconsistent. Fixed:

   No active sub: button reads "SUBSCRIBE"
   Active sub:    button reads "EXTEND SUBSCRIPTION"

(Same code path either way — clicking the button creates a Razorpay
order; the backend already extends from current expiry.)

============================================================
NOT IN THIS DROP — POST-CALL POPUP
============================================================

You also mentioned not getting the post-call popup for unknown
numbers. That was addressed in v25.14 with a CallLog fallback in
CallStateReceiver. If you've deployed AND rebuilt the APK with v25.14,
the popup should fire ~1.2s after a call ends from an unknown number.

If you DID deploy v25.14 and the popup still isn't appearing, check:

  [ ] On Android 13+, did you tap ALLOW on the Notifications prompt?
  [ ] Did you tap "Open Settings → Display over other apps → grant" for
      CallFilter? (Settings → Apps → CallFilter → Display over other apps)
  [ ] Is your test number actually unknown? (not in contacts, no
      matching rule, not your own number) — popup is suppressed otherwise

If all three are yes and you still don't see it, paste me logcat
filtered to:
  adb logcat -s CallStateReceiver:V PostCallOverlay:V

I'll diagnose from the log lines.

============================================================
FILES TOUCHED
============================================================

BACKEND
  CHG src/api.js
      verify-payment stores amount_paid as integer paise (not "29.00")

FRONTEND admin
  CHG pages/UserDetail.jsx
      amount_paid display divides by 100

ANDROID
  CHG res/layout/activity_signup.xml
      Added id="@+id/nameLabel" to the YOUR NAME TextView
  CHG java/.../SignupActivity.java
      login_mode hides name section + cross-link + retitles to
      "Verify your mobile" with SEND OTP button
  CHG java/.../PaywallActivity.java
      Subscribe button text toggles to "EXTEND SUBSCRIPTION" when
      SubscriptionManager.isActive()

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

robocopy F:\\app\\CallManager\\callfilter-v25.15-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.15-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.15-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.15-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.15 — amount_paid integer fix + login flow refinement + EXTEND SUBSCRIPTION"
git push origin main

# Rebuild APK in Android Studio (Build → Rebuild Project → Generate Signed APK)

No new migrations.

============================================================
POST-DEPLOY TESTS
============================================================

[ ] App paywall → SUBSCRIBE button:
    • No active sub  → button reads "SUBSCRIBE"
    • Active sub     → button reads "EXTEND SUBSCRIPTION"

[ ] Complete a payment:
    • Razorpay shows ₹29 (not ₹2900)
    • After payment NO error toast — sub extends successfully
    • Admin → Payments page shows the payment row with ₹29
    • Admin → user → Info tab → "Paid" row shows ₹29.00

[ ] Sign out completely → LoginActivity (mobile mode)
    → Enter your registered mobile + Continue
    → SignupActivity opens, but now:
      ✓ Title says "Verify your mobile" (NOT "Block unwanted calls")
      ✓ NO "YOUR NAME" label
      ✓ NO name input field
      ✓ Continue button says "SEND OTP"
      ✓ NO "Already have an account? Sign in" link at bottom
    → Enter OTP, set new PIN → MainActivity
