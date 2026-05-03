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
// Contacts upload — full address-book sync.
// Body shape:
//   {
//     user_id, mode: "full"|"delta",
//     contacts: [{
//       contact_id, name, photo_uri, starred, notes,
//       phones:    [{number, type}],
//       emails:    [{address, type}],
//       addresses: [{formatted_address, street, city, region, postcode, country, type}],
//       orgs:      [{company, title, department}],
//       websites:  [{url}],
//       events:    [{date_text, type}]
//     }, ...]
//   }
// We dedup at the contact level on (user_id, client_contact_id).
// New contacts are inserted with all their child rows.
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

    await query(
      `UPDATE users
         SET contacts_opted_in = TRUE,
             contacts_opted_in_at = COALESCE(contacts_opted_in_at, NOW())
       WHERE id = $1`,
      [user_id]
    );

    let inserted = 0;
    for (const c of contacts) {
      if (!c || !c.contact_id) continue;
      // Insert the contact (skip if we've already stored this client_contact_id)
      const inserted_row = await one(
        `INSERT INTO user_contacts(user_id, client_contact_id, display_name, photo_uri, starred, notes)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (user_id, client_contact_id) DO NOTHING
         RETURNING id`,
        [user_id, String(c.contact_id), c.name || null, c.photo_uri || null,
         c.starred === true, c.notes || null]
      );
      if (!inserted_row) continue; // existing — skip child inserts
      const cid = inserted_row.id;
      inserted++;

      // Phones
      if (Array.isArray(c.phones)) {
        for (const p of c.phones) {
          if (!p || !p.number) continue;
          await query(
            `INSERT INTO user_contact_phones(contact_id, number, normalized, type)
             VALUES ($1, $2, $3, $4)`,
            [cid, p.number, normalize(p.number), p.type || null]
          );
        }
      }
      // Emails
      if (Array.isArray(c.emails)) {
        for (const e of c.emails) {
          if (!e || !e.address) continue;
          await query(
            `INSERT INTO user_contact_emails(contact_id, address, type) VALUES ($1, $2, $3)`,
            [cid, e.address, e.type || null]
          );
        }
      }
      // Addresses
      if (Array.isArray(c.addresses)) {
        for (const a of c.addresses) {
          if (!a) continue;
          await query(
            `INSERT INTO user_contact_addresses
              (contact_id, formatted_address, street, city, region, postcode, country, type)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
            [cid, a.formatted_address || null, a.street || null, a.city || null,
             a.region || null, a.postcode || null, a.country || null, a.type || null]
          );
        }
      }
      // Orgs
      if (Array.isArray(c.orgs)) {
        for (const o of c.orgs) {
          if (!o) continue;
          await query(
            `INSERT INTO user_contact_orgs(contact_id, company, title, department)
             VALUES ($1, $2, $3, $4)`,
            [cid, o.company || null, o.title || null, o.department || null]
          );
        }
      }
      // Websites
      if (Array.isArray(c.websites)) {
        for (const w of c.websites) {
          if (!w || !w.url) continue;
          await query(
            `INSERT INTO user_contact_websites(contact_id, url) VALUES ($1, $2)`,
            [cid, w.url]
          );
        }
      }
      // Events
      if (Array.isArray(c.events)) {
        for (const ev of c.events) {
          if (!ev || !ev.date_text) continue;
          await query(
            `INSERT INTO user_contact_events(contact_id, date_text, type) VALUES ($1, $2, $3)`,
            [cid, ev.date_text, ev.type || null]
          );
        }
      }
    }

    const total = await one(
      'SELECT COUNT(*)::int AS c FROM user_contacts WHERE user_id = $1', [user_id]);
    await query(
      `UPDATE users SET last_contacts_sync = NOW(), contacts_count = $2 WHERE id = $1`,
      [user_id, total.c]);

    await audit('android',
      mode === 'full' ? 'contacts_full_sync' : 'contacts_delta_sync',
      `user_id=${user_id}, inserted=${inserted}, total=${total.c}`);
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

// POST /api/check-account
// App calls this on every launch to verify the user_id it has cached
// still exists on the server. If the admin has deleted the user, this
// returns 404 and the app wipes local state.
//
// Body: { user_id, dial_code, mobile }
// Response: { exists: true, user_id } or 404 { error }
router.post('/check-account', async (req, res, next) => {
  try {
    const { user_id, dial_code, mobile } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });

    const u = await one('SELECT id, dial_code, mobile, status, pin_set_at FROM users WHERE id = $1',
                        [user_id]);
    if (!u) return res.status(404).json({ error: 'User not found', exists: false });

    // Also flag if the dial_code/mobile have changed (user re-registered with same id, unusual)
    const numberMatches = u.dial_code === dial_code && u.mobile === mobile;
    res.json({
      exists: true,
      user_id: u.id,
      number_matches: numberMatches,
      status: u.status,
      pin_set: !!u.pin_set_at
    });
  } catch (e) { next(e); }
});

module.exports = router;
