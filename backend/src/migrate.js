// Runs all SQL migrations and seeds default settings + admin user.
// Invoked manually via `npm run migrate` or auto-run on server start.
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const { pool, one, query } = require('./db');

async function migrate() {
  console.log('🔧 Running migrations...');

  const dir = path.join(__dirname, '..', 'migrations');
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.sql')).sort();
  for (const f of files) {
    const sql = fs.readFileSync(path.join(dir, f), 'utf8');
    console.log(`  → applying ${f}`);
    await query(sql);
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
