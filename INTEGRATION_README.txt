CallFilter v25.13 — Razorpay 100x bug + login + rules sync + dedup
====================================================================

Four bugs reported after v25.12.1:

1. Razorpay showed ₹2900 for a ₹29 plan
2. Login screen "Could not reach server: user_id required"
3. Admin-added rules not syncing to app (web 3 rules, app 2 rules)
4. Need duplicate-rule prevention in both web and app

============================================================
FIX 1 — RAZORPAY AMOUNT 100x INFLATION
============================================================

Backend was multiplying the stored price by 100 when creating a
Razorpay order:

    const amountPaise = Math.round(parseFloat(plan.offer_price) * 100);

But since v25.11/v25.12, prices are stored IN PAISE in the DB:
    Admin enters 29 → Billing.jsx sends 2900 → DB stores 2900

So `plan.offer_price` was already 2900. Multiplying by 100 → 290000
paise → Razorpay displays ₹2900.

Fix: read the stored value directly.

    const amountPaise = parseInt(plan.offer_price || plan.actual_price, 10);

Now: DB has 2900 → Razorpay receives 2900 paise → displays ₹29. ✓

============================================================
FIX 2 — LOGIN "Could not reach server: user_id required"
============================================================

The new LoginActivity (v25.9) calls /api/check-account in mobile-mode
to ask "does this number have an account?", passing dial_code+mobile.
But the endpoint always required user_id.

Fix: /api/check-account now accepts EITHER user_id OR (dial_code+mobile).
If user_id is missing, it looks up by phone number.

============================================================
FIX 3 — ADMIN RULES NOT SYNCING TO APP
============================================================

Two-part bug.

Part A: When admin added a rule via web (POST /admin/users/:id/rules),
the INSERT didn't include client_id. The app's mergeRulesFromCloud
uses client_id as the dedup key — rules without it weren't being
imported.

Fix: admin endpoint now generates a client_id automatically:
    'admin-' + base36 timestamp + '-' + random suffix

Part B: see Fix 4 below — the app could double-merge if dedup wasn't
done on (type, pattern) too.

============================================================
FIX 4 — DUPLICATE-RULE PREVENTION (web + app)
============================================================

Same (user_id, rule_type, pattern) shouldn't exist twice. Three layers:

  Backend /api/rules/add:
     SELECT ... WHERE user_id=$1 AND rule_type=$2 AND pattern=$3
     If a row exists with a DIFFERENT client_id → return
     { ok: true, deduplicated: true, existing_client_id: ... }
     instead of inserting.

  Backend POST /admin/users/:id/rules:
     Same SELECT — returns 409 Conflict if duplicate.
     Admin UI will show "A rule with this type and pattern already exists".

  Android RulesManager.addRule:
     Returns boolean. False = duplicate found locally, didn't add.
     MainActivity shows toast: "⚠ A PREFIX rule for +91XXX already exists".

  Android RulesManager.addRuleWithId (cloud merge path):
     Skips silently if a same-pattern rule is already local.

============================================================
FILES TOUCHED
============================================================

BACKEND
  CHG src/api.js
      • /api/razorpay/create-order — no longer x100 the stored price
      • /api/check-account — accepts (dial_code+mobile) when no user_id
      • /api/rules/add — duplicate guard on (user_id, type, pattern)
      • amount_paid stored as decimal rupees properly

  CHG src/admin.js
      • POST /admin/users/:id/rules generates client_id
      • Returns 409 on duplicate (user_id, type, pattern)

ANDROID
  CHG java/.../RulesManager.java
      • addRule() returns boolean (false = duplicate, didn't add)
      • addRuleWithId() silently skips duplicates from cloud
      • new findDuplicate() helper
  CHG java/.../MainActivity.java
      • Shows toast on duplicate-rule attempt

NO migration changes. No frontend changes.

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

robocopy F:\\app\\CallManager\\callfilter-v25.13-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.13-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.13-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.13-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.13 — razorpay amount fix + login lookup by mobile + rule dedup + admin client_id"
git push origin main

# Rebuild APK in Android Studio (Build → Rebuild Project → Generate APK)

============================================================
POST-DEPLOY TESTS
============================================================

Razorpay amount:
[ ] In app, tap SUBSCRIBE on the ₹29 plan
[ ] Razorpay checkout should show ₹29 (not ₹2,900)
[ ] Complete a test payment — admin Payments page shows ₹29

Login:
[ ] Sign out completely
[ ] LoginActivity → mobile mode → enter your number → CONTINUE
[ ] Should now route to SignupActivity in login_mode (NOT show
    "Could not reach server: user_id required")

Rules sync:
[ ] Admin → user → Rules tab → click + Add rule (e.g. PREFIX +91143)
[ ] App: background → foreground → +91143 appears in app

Duplicate prevention:
[ ] App: try to add PREFIX +91140 when one already exists
    → toast: "⚠ A PREFIX rule for +91140 already exists"
[ ] Web: try to add PREFIX +91140 when one already exists
    → red error banner: "A rule with this type and pattern already exists"
