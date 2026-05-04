// Runs all SQL migrations and seeds default settings + admin user.
// Invoked manually via `npm run migrate` or auto-run on server start.
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const { pool, one, query } = require('./db');

async function migrate() {
  console.log('🔧 Running migrations...');

  // Tracking table for applied migrations
  await query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      filename    TEXT PRIMARY KEY,
      applied_at  TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  const dir = path.join(__dirname, '..', 'migrations');
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.sql')).sort();

  // Find which migrations have already run
  const appliedRows = await query('SELECT filename FROM schema_migrations');
  const applied = new Set(appliedRows.rows.map(r => r.filename));

  for (const f of files) {
    if (applied.has(f)) {
      console.log(`  ✓ ${f} (already applied)`);
      continue;
    }
    const sql = fs.readFileSync(path.join(dir, f), 'utf8');
    console.log(`  → applying ${f}`);
    try {
      await query(sql);
      await query('INSERT INTO schema_migrations(filename) VALUES ($1)', [f]);
    } catch (e) {
      // If a migration fails on a fresh install, abort. If on a re-run with
      // a partially-applied schema, we record it as applied so we don't keep
      // hitting the same error every boot.
      if (e.message && (
          e.message.includes('already exists') ||
          e.message.includes('does not exist'))) {
        console.warn(`  ⚠ ${f} partially applied (${e.message.split('\n')[0]}); marking complete`);
        await query('INSERT INTO schema_migrations(filename) VALUES ($1) ON CONFLICT DO NOTHING', [f]);
      } else {
        throw e;
      }
    }
  }

  // Seed default settings
  const defaults = {
    sms_provider:         'none',
    sms_api_key:          '',
    sms_api_secret:       '',
    sms_sender_id:        '',
    sms_endpoint:         '',
    sms_template:         'Your verification code is {{otp}}',
    otp_length:           '6',
    otp_expiry_minutes:   '5',
    otp_show_in_response: 'true'
  };
  for (const [k, v] of Object.entries(defaults)) {
    await query(
      `INSERT INTO settings(key, value) VALUES ($1, $2)
       ON CONFLICT (key) DO NOTHING`, [k, v]);
  }

  // Seed default admin if none exists
  const adminCount = await one('SELECT COUNT(*)::int AS c FROM admins');
  if (adminCount.c === 0) {
    const username = process.env.ADMIN_USERNAME || 'admin';
    const password = process.env.ADMIN_PASSWORD || 'changeme';
    const hash = bcrypt.hashSync(password, 10);
    await query('INSERT INTO admins(username, password_hash) VALUES ($1, $2)', [username, hash]);
    console.log(`✓ Default admin: ${username} / ${password === 'changeme' ? 'changeme (CHANGE THIS!)' : '(from env)'}`);
  }

  console.log('✅ Migrations complete');
}

if (require.main === module) {
  migrate()
    .then(() => process.exit(0))
    .catch(err => { console.error('❌ Migration failed:', err); process.exit(1); });
}

module.exports = { migrate };
