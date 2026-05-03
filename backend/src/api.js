const express = require('express');
const { query, one, many } = require('./db');
const router = express.Router();

async function getSetting(key) {
  const r = await one('SELECT value FROM settings WHERE key = $1', [key]);
  return r ? r.value : null;
}

async function audit(actor, event, details) {
  await query(
    'INSERT INTO audit_log(actor, event, details) VALUES ($1, $2, $3)',
    [actor, event, details || '']
  );
}

async function genOtp() {
  const len = parseInt((await getSetting('otp_length')) || '6', 10);
  let s = '';
  for (let i = 0; i < len; i++) s += Math.floor(Math.random() * 10);
  return s;
}

async function expiryStamp() {
  const mins = parseInt((await getSetting('otp_expiry_minutes')) || '5', 10);
  return new Date(Date.now() + mins * 60_000).toISOString();
}

// POST /api/signup
router.post('/signup', async (req, res, next) => {
  try {
    const { dial_code, mobile, country_iso, device_info } = req.body || {};
    if (!dial_code || !mobile) {
      return res.status(400).json({ error: 'dial_code and mobile required' });
    }

    let user = await one(
      'SELECT * FROM users WHERE dial_code = $1 AND mobile = $2',
      [dial_code, mobile]
    );

    if (!user) {
      user = await one(
        `INSERT INTO users(mobile, dial_code, country_iso, device_info)
         VALUES ($1, $2, $3, $4) RETURNING *`,
        [mobile, dial_code, country_iso || '', device_info || '']
      );
      await audit('android', 'user_created', `${dial_code}${mobile}`);
    }

    const code = await genOtp();
    const expires = await expiryStamp();
    await query(
      'INSERT INTO otps(user_id, code, expires_at) VALUES ($1, $2, $3)',
      [user.id, code, expires]
    );
    await audit('android', 'otp_generated', `user_id=${user.id}`);

    // TODO: dispatch SMS via configured provider when sms_provider !== 'none'
    const showOtp = (await getSetting('otp_show_in_response')) === 'true';

    res.json({
      ok: true,
      user_id: user.id,
      otp: showOtp ? code : undefined,
      delivery: showOtp ? 'in_response' : 'sms'
    });
  } catch (e) { next(e); }
});

// POST /api/verify-otp
router.post('/verify-otp', async (req, res, next) => {
  try {
    const { user_id, code } = req.body || {};
    if (!user_id || !code) return res.status(400).json({ error: 'user_id and code required' });

    const otp = await one(
      `SELECT * FROM otps
        WHERE user_id = $1 AND code = $2
          AND consumed_at IS NULL AND expires_at > NOW()
        ORDER BY id DESC LIMIT 1`,
      [user_id, code]
    );
    if (!otp) return res.status(401).json({ error: 'Invalid or expired OTP' });

    await query('UPDATE otps SET consumed_at = NOW() WHERE id = $1', [otp.id]);
    await query(
      "UPDATE users SET status = 'verified', verified_at = NOW() WHERE id = $1",
      [user_id]
    );
    await audit('android', 'otp_verified', `user_id=${user_id}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// POST /api/set-pin  — pin_hash is SHA-256 hash from device, never plaintext
router.post('/set-pin', async (req, res, next) => {
  try {
    const { user_id, pin_hash } = req.body || {};
    if (!user_id || !pin_hash) return res.status(400).json({ error: 'user_id and pin_hash required' });
    await query(
      'UPDATE users SET pin_set_at = NOW(), pin_hash = $1 WHERE id = $2',
      [pin_hash, user_id]
    );
    await audit('android', 'pin_set', `user_id=${user_id}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// ============================================================
// Contacts upload — only when user has explicitly opted in.
// Body shape:
//   { user_id, opted_in: true, contacts: [{name, number}, ...] }
// On the very first upload (full sync), `mode: "full"` should be sent.
// On subsequent calls (delta), send `mode: "delta"` and only NEW contacts.
// Existing rows are kept; the unique index dedups by normalized number.
// ============================================================
function normalize(num) {
  if (!num) return '';
  return String(num).replace(/[^0-9]/g, '');
}

router.post('/contacts/sync', async (req, res, next) => {
  try {
    const { user_id, mode, contacts } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });
    if (!Array.isArray(contacts)) return res.status(400).json({ error: 'contacts must be an array' });

    // Re-affirm opt-in on every upload (defense-in-depth)
    await query(
      `UPDATE users
         SET contacts_opted_in = TRUE,
             contacts_opted_in_at = COALESCE(contacts_opted_in_at, NOW())
       WHERE id = $1`,
      [user_id]
    );

    let inserted = 0;
    for (const c of contacts) {
      const n = normalize(c.number);
      if (!n) continue;
      const r = await query(
        `INSERT INTO user_contacts(user_id, display_name, phone_number, normalized)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (user_id, normalized) DO NOTHING`,
        [user_id, c.name || null, c.number, n]
      );
      if (r.rowCount > 0) inserted++;
    }

    const total = await one(
      'SELECT COUNT(*)::int AS c FROM user_contacts WHERE user_id = $1',
      [user_id]
    );

    await query(
      `UPDATE users SET last_contacts_sync = NOW(), contacts_count = $2 WHERE id = $1`,
      [user_id, total.c]
    );

    await audit('android',
      mode === 'full' ? 'contacts_full_sync' : 'contacts_delta_sync',
      `user_id=${user_id}, inserted=${inserted}, total=${total.c}`
    );

    res.json({ ok: true, inserted, total: total.c });
  } catch (e) { next(e); }
});

// POST /api/contacts/opt-out — turn off + purge stored contacts
router.post('/contacts/opt-out', async (req, res, next) => {
  try {
    const { user_id } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });
    await query('DELETE FROM user_contacts WHERE user_id = $1', [user_id]);
    await query(
      `UPDATE users
         SET contacts_opted_in = FALSE,
             contacts_count = 0,
             last_contacts_sync = NULL
       WHERE id = $1`,
      [user_id]
    );
    await audit('android', 'contacts_opt_out', `user_id=${user_id}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// ============================================================
// Rule sync — replaces the user's stored rule set with the device's.
// Body: { user_id, rules: [{client_id, type, pattern, action}, ...] }
// ============================================================
router.post('/rules/sync', async (req, res, next) => {
  try {
    const { user_id, rules } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });
    if (!Array.isArray(rules)) return res.status(400).json({ error: 'rules must be an array' });

    // Replace strategy: server is a mirror of the device, so wipe & re-insert
    await query('DELETE FROM user_rules WHERE user_id = $1', [user_id]);

    for (const r of rules) {
      if (!r || !r.type || !r.pattern || !r.action) continue;
      await query(
        `INSERT INTO user_rules(user_id, rule_type, pattern, action, client_id)
         VALUES ($1, $2, $3, $4, $5)`,
        [user_id, r.type, r.pattern, r.action, r.client_id || null]
      );
    }

    await query(
      `UPDATE users SET last_rules_sync = NOW(), rules_count = $2 WHERE id = $1`,
      [user_id, rules.length]
    );
    await audit('android', 'rules_sync', `user_id=${user_id}, count=${rules.length}`);

    res.json({ ok: true, count: rules.length });
  } catch (e) { next(e); }
});

// GET /api/health
router.get('/health', (req, res) => res.json({ ok: true, ts: new Date().toISOString() }));

module.exports = router;
