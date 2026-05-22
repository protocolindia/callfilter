CallFilter v25.12 — paywall display + extend semantics
========================================================

Three things from v25.11 testing:

1. Price showed ₹9900 (the paise value) instead of ₹99 in the app's
   paywall. Admin form correctly took rupees and stored as paise, but
   the Android paywall wasn't dividing back.

2. "Currently subscribed: null — 6 days left" — when a subscription
   has no plan attached (e.g. the initial trial), `plan_name` comes
   back as JSON null. Android's `optString` quirks return the literal
   string "null" in that case.

3. Buying a plan while already subscribed REPLACED the expiry instead
   of EXTENDING it. So 6 days left + 365-day plan → 365 days, losing
   the 6 days already paid for.

============================================================
FIXES
============================================================

A) PaywallActivity.formatMoney — divides by 100 (paise → rupees)
   Old: "₹9900"   New: "₹99"
   Same for USD: "$1900" → "$19"

B) PaywallActivity + SubscriptionManager — null plan name handled.
   Now shows "Trial — 6 days left" instead of "null — 6 days left"
   when no plan is attached to the subscription.

C) /api/razorpay/verify-payment — when paying while subscribed,
   extends from the LATER of (now, current_expires_at):

       SELECT MAX(expires_at) FROM subscriptions
        WHERE user_id = ? AND status IN ('trial','active')
              AND expires_at > NOW();
       -- new expiry = (above) + plan duration

   Also marks older active subscriptions as 'cancelled' so the
   /api/subscription endpoint always returns the latest row. This
   means buying a 365-day plan while you have 6 days left → 371
   days left (not 365).

D) Profile page polish:
   • "Manage / Cancel subscription" button HIDDEN. Razorpay doesn't
     do self-service cancel; admin handles that.
   • "🛒 Buy a plan" button now says "⏳ Extend plan" when already
     subscribed. Routes to the same paywall, which fetches plans
     from /api/plans (free + already-used filtered server-side).

============================================================
FILES TOUCHED
============================================================

BACKEND
  CHG src/api.js
      /api/razorpay/verify-payment now extends from current expiry,
      cancels older overlapping rows

ANDROID
  CHG java/.../PaywallActivity.java
      formatMoney divides by 100, normalizes "null" plan name,
      title says "Extend plan" when subscribed
  CHG java/.../ProfileActivity.java
      Manage button hidden permanently, Buy/Extend label toggle
  CHG java/.../SubscriptionManager.java
      normalize "null" string → ""

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

robocopy F:\\app\\CallManager\\callfilter-v25.12-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.12-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.12-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.12-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.12 — paywall price fix, extend semantics, hide manage button"
git push origin main

============================================================
POST-DEPLOY TESTS
============================================================

[ ] App paywall shows ₹99 / ₹29 (NOT ₹9900 / ₹2900)
[ ] Current-sub banner shows "Trial — 6 days left" or
    "1-Year — 365 days left" (no "null")
[ ] Profile screen — no "Manage / Cancel subscription" button
[ ] When NO active sub: button reads "🛒 Buy a plan"
[ ] When subscribed: button reads "⏳ Extend plan"

Extend semantics:
[ ] Start with active trial / sub showing N days left
[ ] Buy a new plan (M days)
[ ] After payment, new expiry = N + M days (not just M)
[ ] /api/subscription returns the new row, old row marked cancelled

(Optional) Rules sync verification (the v25.11 fix):
[ ] Add rule in app → see in admin
[ ] Add rule in admin → see in app
[ ] Reinstall + login → rules come back from cloud
