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
      console.log(`  ✓ ${f} applied successfully`);
    } catch (e) {
      // Loud failure — print full error and DO NOT mark migration as
      // complete. Next deploy will retry. (Previously this code silently
      // marked migrations as complete on "already exists" / "does not
      // exist" errors, which masked real schema problems.)
      console.error(`  ✗ ${f} FAILED: ${e.message}`);
      console.error(`     The migration was NOT marked as applied. Fix the`);
      console.error(`     error and redeploy. To inspect, run: psql $DATABASE_URL`);
      throw e;
    }
  }

  // Seed default settings
  const defaults = {
    sms_provider:         'custom_url',
    sms_api_url:          '',
    sms_api_userid:       '',
    sms_api_password:     '',
    sms_api_sender_name:  '',
    sms_api_sender_number:'',
    sms_api_mobile_param: 'mobileno',
    sms_api_message_param:'message',
    sms_api_category:     '',
    sms_api_template_id:  '',
    sms_api_message_template: '{OTP} is your OTP to verify your phone number. Do not share with anyone.',
    sms_api_key:          '',
    sms_api_secret:       '',
    sms_sender_id:        '',
    sms_endpoint:         '',
    sms_template:         'Your verification code is {{otp}}',
    otp_length:           '6',
    otp_expiry_minutes:   '5',
    otp_show_in_response: 'true',
    subscription_required: 'false',
    razorpay_enabled:     'false',
    razorpay_mode:        'test',     // 'test' or 'live'
    razorpay_key_id_test: '',
    razorpay_secret_test: '',
    razorpay_key_id_live: '',
    razorpay_secret_live: '',
    razorpay_webhook_secret: '',
    block_reasons: 'Spam call\nCybercrime / fraud\nPhishing\nTelemarketing / promotional\nRobocall / IVR\nPersonal harassment\nOther',
    global_blocklist_show_total:  'true',
    global_blocklist_show_active: 'true',
  };
  for (const [k, v] of Object.entries(defaults)) {
    await query(
      `INSERT INTO settings(key, value) VALUES ($1, $2)
       ON CONFLICT (key) DO NOTHING`, [k, v]);
  }

  // ── Seed super_admin from env vars (first deploy only) ────────────
  const existingAdmins = await one('SELECT COUNT(*)::int AS n FROM admin_users');
  if (!existingAdmins || existingAdmins.n === 0) {
    const bcrypt = require('bcryptjs');
    const envUser = process.env.ADMIN_USERNAME || 'admin';
    const envPass = process.env.ADMIN_PASSWORD || 'changeme';
    const hash = await bcrypt.hash(envPass, 12);
    await query(
      `INSERT INTO admin_users(username, password_hash, display_name, role)
       VALUES ($1, $2, $3, 'super_admin')
       ON CONFLICT (username) DO NOTHING`,
      [envUser, hash, 'Super Admin']
    );
    // Link existing global_blocklist entries to this super_admin
    await query(
      `UPDATE global_blocklist
          SET added_by_admin_id = (SELECT id FROM admin_users WHERE role='super_admin' LIMIT 1)
        WHERE added_by_admin_id IS NULL`
    );
    console.log('  ✓ Super admin seeded from env vars');
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
