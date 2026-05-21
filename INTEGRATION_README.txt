CallFilter v25.7 — full cumulative drop
=========================================

Major release. Adds Razorpay billing for sideload distribution, captures
user name on signup, and gives the admin panel full visibility + edit
power over user data.

============================================================
WHAT'S NEW IN v25.7
============================================================

A) USER NAME ON SIGNUP
----------------------
- Signup screen now has a "YOUR NAME" field at the top.
- Name flows backend (users.name column, migration 008).
- Shown on:
  - ProfileActivity (above mobile number)
  - Admin user list (Name column where available)
  - Admin UserDetail page header + Account info row

B) WEB ADMIN — FULL SYNCED DATA VISIBILITY
-------------------------------------------
The user-detail page now has 6 tabs:
   Info  |  📇 Contacts  |  🛡️ Rules  |  🚫 Blocked Calls
       |  🗓️ Schedules  |  🛑 Block All

NEW: Schedules tab — time/days/allow-list/freq-bypass for each schedule
NEW: Block All tab — current panic-mode state with expiry
NEW: Rules tab — admin can CREATE/EDIT/DELETE rules (was read-only).
                  New form at top of tab; Delete button on each row.

If rules weren't showing up before, the cause was the admin endpoint
returning them correctly but the React state never reloading. The new
flow re-fetches after every mutation.

C) RAZORPAY BILLING (sideload flavor)
--------------------------------------
The Android app now has TWO build flavors:
   - playstore: Google Play Billing only (per Play Store policy)
   - sideload:  Razorpay only (UPI / card / wallet checkout via Razorpay)

Same codebase, same APK source. Pick the flavor when building:
   In Android Studio:  Build → Select Build Variant → app → "sideloadRelease"
                                                            or "playstoreRelease"
   From command line:  ./gradlew assembleSideloadRelease
                       ./gradlew assemblePlaystoreRelease

The flavor is driven by BuildConfig.BILLING_PROVIDER which the runtime
BillingProvider.Factory reads to pick PlayBillingProviderAdapter or
RazorpayBillingManager.

D) RAZORPAY ADMIN CONTROLS
---------------------------
NEW admin Settings section "Razorpay (sideload payments)":
   ☐ Enable Razorpay payments         (master switch)
   Mode: [Test mode] / [Live mode]    (drop-down)
   Test Key ID + Test Secret
   Live Key ID + Live Secret
   Webhook Secret

Webhook URL to configure in Razorpay dashboard:
   https://api.app.onephone.pro/api/razorpay/webhook

A status banner shows whether you're in TEST (orange) or LIVE (blue) mode.

NEW admin page: 💰 Payments (in left nav)
   Shows every Razorpay order with status, amount, plan, user, payment_id.
   Filter by status: All / Created / Paid / Failed / Cancelled.

E) PLAN MANAGEMENT (already existed, but worth reminding)
----------------------------------------------------------
Admin Billing page handles CRUD on Plans (name, price, duration). The
Android paywall now PULLS from this list (was hardcoded to Google Play
product details). One source of truth: the admin DB.

============================================================
BACKEND CHANGES (file-by-file)
============================================================
NEW    backend/migrations/008_user_name_and_razorpay.sql
       - users.name column
       - subscriptions.provider, razorpay_order_id, razorpay_payment_id, razorpay_signature
       - razorpay_orders table (transaction log)

CHG    backend/src/api.js
       POST /api/signup                accepts `name` field
       POST /api/razorpay/create-order new
       POST /api/razorpay/verify-payment new  (signature OR payment-id verify paths)
       POST /api/razorpay/webhook       new  (HMAC-verified server-to-server)
       GET  /api/razorpay/status        new  (client checks if enabled)

CHG    backend/src/admin.js
       GET  /admin/users/:id/schedules new
       GET  /admin/users/:id/block-all new
       POST /admin/users/:id/rules     new  (admin can create)
       PUT  /admin/users/:id/rules/:rid new (admin can edit)
       DEL  /admin/users/:id/rules/:rid new (admin can delete)
       GET  /admin/razorpay/orders     new  (paginated, filterable)
       PUT  /admin/settings allowlist  added 7 razorpay_* keys

CHG    backend/src/migrate.js
       DEFAULT_SETTINGS seeds: razorpay_enabled, razorpay_mode,
                               razorpay_key_id_test, razorpay_secret_test,
                               razorpay_key_id_live, razorpay_secret_live,
                               razorpay_webhook_secret

============================================================
FRONTEND (admin panel) CHANGES
============================================================
NEW    src/pages/Payments.jsx          (Razorpay transactions page)
CHG    src/main.jsx                    (route /payments)
CHG    src/components/Layout.jsx       (nav link 💰 Payments)
CHG    src/pages/Settings.jsx          (Razorpay section)
CHG    src/pages/UserDetail.jsx        (Schedules + Block All tabs,
                                        edit/delete on rules, Name row)

============================================================
ANDROID CHANGES
============================================================
NEW    java/.../BillingProvider.java                interface + Factory
NEW    java/.../PlayBillingProviderAdapter.java     wraps existing PlayBillingManager
NEW    sideload/java/.../RazorpayBillingManager.java real Razorpay impl
NEW    playstore/java/.../RazorpayBillingManager.java stub (throws)
NEW    res/layout/plan_card.xml                     one card per plan
CHG    app/build.gradle                             flavorDimensions+productFlavors,
                                                    Razorpay SDK dep (sideload only)
CHG    java/.../PaywallActivity.java                rewritten — flavor-agnostic,
                                                    fetches plans from /api/plans,
                                                    Razorpay reflection callbacks
CHG    res/layout/activity_paywall.xml              redesigned for plan list
CHG    java/.../ProfileActivity.java                shows user name
CHG    java/.../AuthManager.java                    name field added, persisted
CHG    java/.../SignupActivity.java                 name input wired
CHG    res/layout/activity_signup.xml               YOUR NAME field

============================================================
DEPLOY
============================================================
1. Backend + frontend (auto-deploy via Railway):

   cd D:\\callfilter
   git pull origin main

   # Extract zip to e.g. C:\\temp\\v257
   robocopy C:\\temp\\v257\\callfilter-monorepo\\android  android  /E
   robocopy C:\\temp\\v257\\callfilter-monorepo\\backend  backend  /E
   robocopy C:\\temp\\v257\\callfilter-monorepo\\frontend frontend /E
   copy C:\\temp\\v257\\callfilter-monorepo\\README.md .
   copy C:\\temp\\v257\\callfilter-monorepo\\INTEGRATION_README.txt .

   git add .
   git commit -m "v25.7 — name field, Razorpay billing, full admin"
   git push origin main

   Railway redeploys both services. Look for:
       Running migration 008_user_name_and_razorpay.sql

2. Configure Razorpay (one-time, admin panel):

   Admin → Settings → "Razorpay (sideload payments)" section:
     ☑ Enable Razorpay payments
     Mode: Test
     Test Key ID:     rzp_test_xxxxxxxxxxxxxxxx
     Test Secret:     xxxxxxxxxxxxxxxxxxxxxxxxxxxx
     Live Key ID:     rzp_live_xxxxxxxxxxxxxxxx
     Live Secret:     xxxxxxxxxxxxxxxxxxxxxxxxxxxx
     Webhook Secret:  whsec_xxxxxxxxxxxxxxxxxxxxxx
   Save.

   In Razorpay dashboard → Settings → Webhooks:
     URL:    https://api.app.onephone.pro/api/razorpay/webhook
     Events: payment.captured, payment.failed
     Secret: same value as Webhook Secret above

3. Build the Android app — pick a flavor:

   In Android Studio:
     Build → Select Build Variant → app → choose "sideloadRelease" or
     "playstoreRelease" → then Build → Generate Signed App Bundle / APK

   From command line:
     cd D:\\callfilter\\android
     .\\gradlew assembleSideloadRelease
     # output: app/build/outputs/apk/sideload/release/app-sideload-release.apk

     .\\gradlew assemblePlaystoreRelease
     # output: app/build/outputs/apk/playstore/release/app-playstore-release.apk

============================================================
POST-DEPLOY TEST PLAN
============================================================

[ ] Backend smoke: curl https://api.app.onephone.pro/api/razorpay/status
    → {"ok":true,"enabled":false,...}   (until you configure)

[ ] Admin panel — Settings page has Razorpay section. Enable + paste TEST
    keys. Status banner shows orange "TEST mode".

[ ] Admin panel — Users → click any user → tabs include Schedules + Block All

[ ] Admin panel — Rules tab: add a new rule from the form; delete one

[ ] Admin panel — Payments page in left nav → empty until first checkout

[ ] Android: install fresh, sign up with new mobile + NAME
[ ] After login, ProfileActivity shows name + mobile

[ ] Sideload flavor only:
    - Paywall opens, lists plans from /api/plans
    - Tap a plan's SUBSCRIBE → Razorpay checkout opens
    - Pay with a Razorpay test card (4111 1111 1111 1111, any CVV, any
      future date)
    - Toast: "✅ Subscription active"
    - Admin Payments page shows the new order with status=paid

[ ] Play Store flavor:
    - Existing Google Play Billing flow still works for builds uploaded
      to Internal Testing track

============================================================
KNOWN LIMITATIONS / TODO
============================================================
- Razorpay's PaymentResultListener (the simpler interface) is used here
  rather than PaymentResultWithDataListener so the Activity compiles
  cleanly in both flavors. This means the SIGNATURE isn't available
  client-side; backend falls back to querying Razorpay's /payments API
  to verify (Path B in /razorpay/verify-payment). Webhook is still the
  most reliable confirmation source.

- The webhook is the authoritative payment confirmation. Even if the
  client never calls /verify-payment (e.g. user closes app before
  callback), the webhook will mark the order as paid and the next
  subscription check picks it up.

- The "Restore purchase" button on the paywall currently only refreshes
  subscription status — it doesn't query Razorpay for prior orders.
  If a user's subscription was paid but didn't activate, they should
  contact admin who can manually extend via the Subscriptions page.
