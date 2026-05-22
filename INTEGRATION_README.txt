CallFilter v25.10 — pgcrypto-free migration repair
====================================================

ONE-LINE SUMMARY: migration 010 in v25.9 needed pgcrypto, which Railway
managed Postgres doesn't allow. v25.10 removes that requirement.

============================================================
WHY v25.9 FAILED TO APPLY MIGRATION 010
============================================================

Root cause #1 — pgcrypto extension not available on Railway

  Migration 010 had:  CREATE EXTENSION IF NOT EXISTS pgcrypto;

  Railway's managed Postgres (and most managed Postgres services)
  don't grant CREATE EXTENSION permission to the app user. So this
  statement throws "permission denied to create extension" or
  "could not open extension control file". Postgres aborts the
  multi-statement query at the FIRST error, rolling back any later
  statements — so the is_one_time_per_user column was never added.

Root cause #2 — server.js silently caught migration failures

  Old code:
      try {
        await migrate();
      } catch (err) {
        console.error('⚠️ Auto-migrate failed (continuing anyway):', err.message);
      }

  This meant: even when migrate.js threw a loud error, server.js
  swallowed it and started the API anyway. The user got a "running"
  backend that was actually missing schema changes. No visible
  signal anywhere that anything was wrong.

============================================================
FIX
============================================================

1. Migration 010 rewritten WITHOUT pgcrypto.

   The only thing pgcrypto was used for was gen_random_uuid() to
   backfill NULL client_ids in user_rules. v25.10 replaces this
   with 'legacy-' || id::text — a deterministic placeholder that's
   unique per row (since id is the primary key). The Android app
   will replace these with proper UUIDs on its next sync.

2. Migration 009 also defanged (same gen_random_uuid → 'legacy-' fix)
   in case it ever gets retried on a fresh DB.

3. server.js now HARD-FAILS the deploy if migrations fail.

   New code:
      try {
        await migrate();
        console.log('✅ Migrations OK');
      } catch (err) {
        console.error('═══════════════════════════════');
        console.error('❌ MIGRATIONS FAILED — refusing to start');
        console.error(err.stack);
        process.exit(1);
      }

   This means if anything else goes wrong with future migrations,
   Railway will mark the deploy as FAILED and surface the exact
   error in the deploy logs. No more silent broken backends.

============================================================
EXPECTED DEPLOY OUTPUT
============================================================

When you push v25.10, Railway backend logs should show:

   🔧 Running migrations...
     ✓ 001_init.sql (already applied)
     ✓ 002_contacts_and_rules.sql (already applied)
     ...
     ✓ 008_user_name_and_razorpay.sql (already applied)
     ✓ 009_diff_sync_activation_onetime.sql (already applied)
     → applying 010_repair_008_009.sql
     ✓ 010_repair_008_009.sql applied successfully
   ✅ Migrations OK
   ⚡ Listening on port 3000

If you see "❌ MIGRATIONS FAILED — refusing to start" with a stack
trace, paste the error to me and I'll fix it. Deploy will fail in
this case — that's intentional, so you know to investigate.

============================================================
ALL OTHER v25.9 CHANGES ARE PRESERVED
============================================================

This release is essentially v25.9 with two file changes:
  • backend/migrations/010_repair_008_009.sql   (rewritten, no pgcrypto)
  • backend/migrations/009_diff_sync_activation_onetime.sql (defanged)
  • backend/src/server.js                       (hard-fail on migrate)

Everything else from v25.9 is intact — smart login flow, rupee input,
multi-currency plans, admin 401 auto-logout, etc.

============================================================
DEPLOY
============================================================

cd D:\\callfilter
git pull origin main

# Extract zip to e.g. F:\\app\\CallManager\\callfilter-v25.10-monorepo\\
robocopy F:\\app\\CallManager\\callfilter-v25.10-monorepo\\callfilter-monorepo\\android  android  /E
robocopy F:\\app\\CallManager\\callfilter-v25.10-monorepo\\callfilter-monorepo\\backend  backend  /E
robocopy F:\\app\\CallManager\\callfilter-v25.10-monorepo\\callfilter-monorepo\\frontend frontend /E
copy F:\\app\\CallManager\\callfilter-v25.10-monorepo\\callfilter-monorepo\\INTEGRATION_README.txt .

git add .
git commit -m "v25.10 — pgcrypto-free migration, server hard-fail on migrate error"
git push origin main

# WATCH RAILWAY BACKEND DEPLOY LOGS for:
#   → applying 010_repair_008_009.sql
#   ✓ 010_repair_008_009.sql applied successfully
#   ✅ Migrations OK

============================================================
POST-DEPLOY VERIFICATION
============================================================

[ ] Railway backend log: "✓ 010_repair_008_009.sql applied successfully"
[ ] Railway backend log: "✅ Migrations OK"
[ ] Backend service is RUNNING (not failed)

Then test the originally-broken admin feature:

[ ] Admin → Billing → Edit a plan (or New plan)
[ ] Check "One-time only per user" → Save
[ ] Should save successfully (NO "column does not exist" error)

Then test rules sync (which depends on the unique index that 010 creates):

[ ] App: add rule PREFIX 9494 REJECT
[ ] Admin → user → Rules tab → should see the rule
[ ] Admin: delete that rule from web
[ ] App: background→foreground → rule disappears
[ ] Admin: add new rule PREFIX 9090 REJECT from web
[ ] App: background→foreground → new rule appears

If sync still fails AFTER 010 confirms applied, paste me logcat lines
with "SyncManager" or "pushAddedRule" and I'll diagnose further.
