CallFilter v25.9 — full cumulative drop
=========================================

THIS RELEASE FIXES THE CRITICAL MIGRATION BUG that prevented v25.8
from applying its schema changes (column "is_one_time_per_user" missing,
rules sync still broken because client_id unique index never created).

Plus: smart Login flow, paise→rupees admin UI, multi-currency plans
with Razorpay International.

============================================================
1. MIGRATION BUG — ROOT CAUSE
============================================================

backend/src/migrate.js had this pattern:

    try { await query(sql); }
    catch (e) {
      if (e.message.includes('already exists') ||
          e.message.includes('does not exist')) {
        // Marks migration as complete and moves on
      }
    }

Migration 009's first statement was:
    UPDATE user_rules SET client_id = gen_random_uuid()::text WHERE ...

On first run, pgcrypto wasn't installed → error message:
    function gen_random_uuid() does not exist

That matched "does not exist" → migration 009 marked as applied → the
remaining statements (CREATE EXTENSION pgcrypto, ALTER TABLE plans ADD
COLUMN is_one_time_per_user, CREATE UNIQUE INDEX) were never run.

Result on Railway:
  • plans.is_one_time_per_user column missing → admin can't toggle
  • user_rules has no unique index → /api/rules/add can't ON CONFLICT
  • Rules sync degenerates to old mirror-replace behavior → still buggy

FIX:
  1. New migration 010_repair_008_009.sql — idempotently brings the
     schema up to expected state regardless of what 008/009 actually
     applied. Uses IF NOT EXISTS, IF EXISTS, DO $$ ... $$ guards.
     pgcrypto extension is the FIRST statement so gen_random_uuid()
     works.

  2. migrate.js no longer silently swallows errors. On failure it
     prints a loud "✗ FAILED" line WITH the full error message and
     does NOT mark the migration as applied. Next deploy will retry
     and you'll see the real error in Railway logs.

============================================================
2. WEB ADMIN — 401 AUTO-LOGOUT
============================================================

When admin's token expires, frontend was showing the raw error
"Invalid or expired token". Now:

  • api.js intercepts 401 / invalid_token / expired_token responses
  • Clears local token
  • Redirects to /login?expired=1
  • Login page shows orange banner: "Your session expired. Please
    sign in again."

Avoids the redirect-loop when already on /login.

============================================================
3. PAISE → RUPEES IN ADMIN BILLING FORM
============================================================

Old form asked for prices in paise:
  Actual price (paise): [4900]   ← confusing! that's ₹49

New form takes whole units (rupees / dollars) and converts on save:
  Currency: [INR ▼] [Actual price (₹): 49]  [Offer price (₹): 29]

DB still stores paise/cents internally (Razorpay needs smallest unit).
The conversion is purely a UX layer.

============================================================
4. MULTI-CURRENCY PLANS — INR + USD
============================================================

Admin Billing → New plan → Currency dropdown:
  • INR (₹)   — handled by Razorpay (existing test/live keys)
  • USD ($)   — handled by Razorpay International

For USD support:
  • In your Razorpay dashboard, enable "International Payments"
    (Settings → International Payments). No new keys needed.
  • Same Razorpay credentials work for both. The currency is set
    per-order at /api/razorpay/create-order time from the plan's
    currency column.

Display:
  • Symbol ₹ for INR, $ for USD throughout admin
  • Android paywall same — picks symbol based on plan.currency

UserDetail.jsx and Payments.jsx also fixed to show the correct symbol
and the right amount (was double-dividing amount_paid by 100).

============================================================
5. ANDROID LOGIN FLOW — SMART ROUTING
============================================================

Old: full sign-out → SignupActivity (force re-enter name + OTP + PIN).
     Fresh install → SignupActivity (same).
     No way to log back in as an existing user.

New: full sign-out → LoginActivity in MOBILE MODE.
     Fresh install → MainActivity → !isLoggedIn → LoginActivity in
                   MOBILE MODE.
     Locked session → LoginActivity in PIN MODE (existing UX).

LoginActivity now has TWO modes:

  PIN MODE — local mobile + PIN exist:
     • Greeting: "Signing in as +91…"
     • PIN input + SIGN IN button
     • "Use a different number" link → full sign-out → mobile mode
     • Always-visible "Don't have an account? Sign up" cross-link

  MOBILE MODE — no local PIN (post-logout or fresh install):
     • Country picker + mobile number input
     • CONTINUE button → POST /api/check-account
        - If account EXISTS  → SignupActivity in login_mode (skips
          name field, prompts OTP + new PIN setup, mobile prefilled)
        - If account DOESN'T → dialog: "No account found. Sign up?"
                               → if Yes, SignupActivity with prefill
     • Always-visible "Don't have an account? Sign up" cross-link

SignupActivity also got the matching cross-link:
   "Already have an account? Sign in" → LoginActivity

============================================================
6. RULES SYNC — VERIFY 010 RAN
============================================================

After deploying v25.9, the FIRST thing to verify in Railway backend
logs is the migration 010 output. Look for:

    Running migration 010_repair_008_009.sql
    ✓ 010_repair_008_009.sql applied successfully

If you see "✗ 010_repair_008_009.sql FAILED", paste the error message
to me and I'll fix it.

Once 010 runs, the differential rules sync from v25.8 will actually
work:
  • POST /api/rules/add upserts ONE rule (ON CONFLICT uses the new
    unique index on user_id+client_id)
  • POST /api/rules/delete deletes ONE rule
  • Cloud → app pull via mergeRulesFromCloud on every MainActivity.onResume

If sync STILL fails after 010 runs successfully, the bug is somewhere
else and I'll need logcat. But 80% chance 010 was the root cause.

============================================================
FILES TOUCHED
============================================================

BACKEND
  NEW  migrations/010_repair_008_009.sql        defensive schema repair
  CHG  src/migrate.js                           loud failure mode
  CHG  src/api.js                               /check-account returns
                                                200+exists:false

FRONTEND (admin)
  CHG  src/api.js                               401 → auto-logout
  CHG  src/pages/Login.jsx                      session-expired banner
  CHG  src/pages/Billing.jsx                    rupee input + USD option
  CHG  src/pages/UserDetail.jsx                 currency symbol on amounts

ANDROID — main flavor
  CHG  AndroidManifest.xml                      LAUNCHER → MainActivity
  CHG  res/layout/activity_login.xml            mobile section + PIN
                                                section + Sign-up link
  REWR java/.../LoginActivity.java              PIN mode / Mobile mode
  CHG  res/layout/activity_signup.xml           Scroll wrap + Sign-in link
  CHG  java/.../SignupActivity.java             prefill + login_mode
                                                + Sign-in link wiring
  CHG  java/.../ProfileActivity.java            Sign out → LoginActivity

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

# Extract this zip to e.g. F:\\app\\CallManager\\callfilter-v25.9-monorepo\\
robocopy F:\\app\\CallManager\\callfilter-v25.9-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.9-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.9-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.9-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.9 — fix migration runner, repair schema, smart login, rupees+USD"
git push origin main

# IMPORTANT: watch Railway backend deploy logs for migration 010 result

============================================================
POST-DEPLOY VERIFICATION
============================================================

Migration:
[ ] Railway backend logs: "✓ 010_repair_008_009.sql applied successfully"
[ ] If FAILED, paste the error and ping me

Admin 401:
[ ] Wait for current session token to expire (or clear localStorage manually)
[ ] Click any page → toast says "Session expired" → redirected to /login
[ ] Sign in again → works normally

Admin Billing:
[ ] New plan form shows: Currency [INR/USD], Actual price (₹), Offer price (₹)
[ ] Enter 99 for actual, 49 for offer → save
[ ] Plan list shows ₹99 / ₹49 (NOT ₹0.99 / ₹0.49)
[ ] One-time-only checkbox works (no "column does not exist" error)

Admin User Detail:
[ ] Open a user → Info tab → subscription row → shows correct ₹ amount

Android — fresh install:
[ ] Launcher → blank app → LoginActivity in mobile mode
[ ] "Don't have an account? Sign up" link visible at bottom
[ ] Type new mobile (one that's not in backend) → CONTINUE → "No account found"
    dialog → Sign up → SignupActivity with mobile prefilled

Android — login as existing user (without reinstall):
[ ] Profile → 🚪 Sign out completely → goes to LoginActivity (NOT signup)
[ ] Type old mobile + Continue → SignupActivity (login mode) → OTP → new PIN
[ ] Land on MainActivity, rules pulled from cloud

Android — login as existing user (after reinstall):
[ ] Uninstall app, install again
[ ] Launcher → LoginActivity in mobile mode
[ ] Type old mobile + Continue → SignupActivity (login mode) → OTP → PIN
[ ] Rules visible in app (pulled from cloud)

Rules sync (the recurring bug):
[ ] Add rule in app: PREFIX 9494 REJECT
[ ] Web admin → user → Rules tab → see the rule
[ ] Web admin: add rule PREFIX 9090 REJECT from web
[ ] App: background → foreground → 9090 appears in app rules
[ ] Web admin: Delete one of them
[ ] App: background → foreground → rule disappears
[ ] Uninstall + reinstall + log back in → all rules come back
