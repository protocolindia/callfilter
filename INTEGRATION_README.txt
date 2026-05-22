CallFilter v25.11 — ACTUAL root cause of the rules sync bug
=============================================================

The rules sync bug has been mysterious across v25.4 → v25.10 because
I kept finding symptoms (mirror-replace, pgcrypto, silent migrate
errors) without finding the underlying schema mismatch.

THE REAL CAUSE: user_rules.client_id was declared INTEGER in migration
002, but the Android app generates UUIDs (UUID.randomUUID().toString())
and sends them as STRINGS. Every /api/rules/add call was hitting
Postgres with:

    error: invalid input syntax for type integer: "a1b2c3d4-..."

The error was returned to the app, which logged it and moved on. The
backend never saved any rule. The "rules sync isn't working" symptom
was just that.

When v25.10's migration 009/010 tried to backfill client_id with
"legacy-" || id::text, Postgres rejected the string assignment to an
INTEGER column → "invalid input syntax for type integer: """

(That cryptic empty-string error in the Railway logs was Postgres
trying to cast '' to integer when the WHERE clause matched empty
strings — but the underlying problem was that the column was the
wrong type all along.)

============================================================
THE FIX
============================================================

Both migrations 009 and 010 now do this and only this for sync:

    ALTER TABLE user_rules
      ALTER COLUMN client_id TYPE TEXT
      USING client_id::text;

ALTER COLUMN ... TYPE TEXT USING client_id::text is idempotent:
  • If the column is INTEGER → converts existing integers to text
  • If the column is already TEXT → cast TEXT to TEXT is a no-op
  • If the column has NULLs → NULLs stay NULL

This works on Railway, on a fresh install, on a partially-migrated DB,
or anywhere else. No pgcrypto, no UUIDs in SQL, no backfill needed.

The UNIQUE (user_id, client_id) constraint that ON CONFLICT relies on
ALREADY EXISTS from migration 002 — I was adding a duplicate index
unnecessarily. That's removed too.

The plans.is_one_time_per_user column is added separately (ADD COLUMN
IF NOT EXISTS).

============================================================
WHAT YOUR DEPLOY WILL DO
============================================================

When you push v25.11, Railway backend deploy logs should show:

   🔧 Running migrations...
     ✓ 001_init.sql (already applied)
     ✓ 002_contacts_and_rules.sql (already applied)
     ...
     ✓ 008_user_name_and_razorpay.sql (already applied)
     → applying 009_diff_sync_activation_onetime.sql
     ✓ 009_diff_sync_activation_onetime.sql applied successfully
     → applying 010_repair_008_009.sql
     ✓ 010_repair_008_009.sql applied successfully
   ✅ Migrations OK
   ⚡ Listening on port 3000

After this, user_rules.client_id is TEXT, plans.is_one_time_per_user
exists, and the differential rules sync from v25.8 will finally work.

============================================================
WHAT CHANGED FROM v25.10
============================================================

ONLY these two files differ from v25.10:
  backend/migrations/009_diff_sync_activation_onetime.sql  (rewritten)
  backend/migrations/010_repair_008_009.sql                (rewritten)

Everything else from v25.10 (and the cumulative v25.x work) is intact:
smart login flow, rupee input, multi-currency plans, admin 401 auto-
logout, hard-fail server on migrate errors, etc.

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

robocopy F:\\app\\CallManager\\callfilter-v25.11-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.11-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.11-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.11-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.11 — convert user_rules.client_id INTEGER → TEXT (the real rules sync bug)"
git push origin main

# Watch Railway backend deploy logs for the green ticks above

============================================================
POST-DEPLOY TESTS
============================================================

[ ] Railway log: "✓ 009_diff_sync_activation_onetime.sql applied successfully"
[ ] Railway log: "✓ 010_repair_008_009.sql applied successfully"
[ ] Railway log: "✅ Migrations OK"
[ ] Backend service is RUNNING (not failed)

[ ] Admin → Billing → Edit a plan → check "One-time only per user" → Save
    → Should succeed (no "column does not exist")

[ ] Android app: add a rule (PREFIX 9494 REJECT)
[ ] Force-stop the app. Reopen.
    → Rule should still be there (was being silently dropped before)
[ ] Web admin → user → Rules tab
    → Should see the rule
[ ] Web admin: add a NEW rule from web (PREFIX 9090 REJECT)
[ ] App: background → foreground
    → New rule appears (admin → app sync)
[ ] Web admin: delete a rule
[ ] App: background → foreground
    → Rule disappears (cloud → app sync)
[ ] Uninstall + reinstall + log in with same mobile
    → All rules come back from cloud

If any of these still fail, paste the failing step number and any
backend logs around the time of the action, and I'll look at the
specific endpoint.
