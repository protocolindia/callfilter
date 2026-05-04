const express = require('express');
const bcrypt = require('bcryptjs');
const { query, one, many } = require('./db');
const { sign, requireAdmin } = require('./auth');
const router = express.Router();

async function audit(actor, event, details) {
  await query(
    'INSERT INTO audit_log(actor, event, details) VALUES ($1, $2, $3)',
    [actor, event, details || '']
  );
}

// POST /admin/login
router.post('/login', async (req, res, next) => {
  try {
    const { username, password } = req.body || {};
    if (!username || !password) return res.status(400).json({ error: 'username and password required' });
    const a = await one('SELECT * FROM admins WHERE username = $1', [username]);
    if (!a || !bcrypt.compareSync(password, a.password_hash)) {
      await audit(username, 'login_failed', 'Invalid credentials');
      return res.status(401).json({ error: 'Invalid username or password' });
    }
    const token = sign({ admin: true, id: a.id, username: a.username });
    await audit(a.username, 'login_success', '');
    res.json({ ok: true, token, username: a.username });
  } catch (e) { next(e); }
});

// GET /admin/me
router.get('/me', requireAdmin, (req, res) => {
  res.json({ username: req.admin.username });
});

// GET /admin/stats
router.get('/stats', requireAdmin, async (req, res, next) => {
  try {
    const stats = {
      total_users:    (await one('SELECT COUNT(*)::int AS c FROM users')).c,
      verified_users: (await one("SELECT COUNT(*)::int AS c FROM users WHERE status='verified'")).c,
      pending_users:  (await one("SELECT COUNT(*)::int AS c FROM users WHERE status='pending'")).c,
      pin_set_users:  (await one('SELECT COUNT(*)::int AS c FROM users WHERE pin_set_at IS NOT NULL')).c,
      total_otps:     (await one('SELECT COUNT(*)::int AS c FROM otps')).c,
      used_otps:      (await one('SELECT COUNT(*)::int AS c FROM otps WHERE consumed_at IS NOT NULL')).c,
      blocked_calls_total: (await one('SELECT COUNT(*)::int AS c FROM blocked_calls')).c
    };
    const recent_users  = await many('SELECT * FROM users ORDER BY id DESC LIMIT 10');
    const recent_log    = await many('SELECT * FROM audit_log ORDER BY id DESC LIMIT 15');
    res.json({ stats, recent_users, recent_log });
  } catch (e) { next(e); }
});

// GET /admin/users
router.get('/users', requireAdmin, async (req, res, next) => {
  try {
    const q = (req.query.q || '').trim();
    const status = (req.query.status || '').trim();
    const params = [];
    let sql = 'SELECT * FROM users WHERE 1=1';
    if (q) {
      params.push(`%${q}%`);
      sql += ` AND (mobile ILIKE $${params.length} OR dial_code ILIKE $${params.length})`;
    }
    if (status) {
      params.push(status);
      sql += ` AND status = $${params.length}`;
    }
    sql += ' ORDER BY id DESC LIMIT 200';
    const rows = await many(sql, params);
    res.json({ users: rows });
  } catch (e) { next(e); }
});

// GET /admin/users/:id  — full user detail with counts
router.get('/users/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const u = await one('SELECT * FROM users WHERE id = $1', [id]);
    if (!u) return res.status(404).json({ error: 'User not found' });
    delete u.pin_hash; // never expose hash to admin UI
    res.json({ user: u });
  } catch (e) { next(e); }
});

// GET /admin/users/:id/contacts  — synced contacts with pagination
router.get('/users/:id/contacts', requireAdmin, async (req, res, next) => {
  try {
    const id     = parseInt(req.params.id, 10);
    const page   = Math.max(parseInt(req.query.page, 10) || 1, 1);
    const limit  = Math.min(Math.max(parseInt(req.query.limit, 10) || 50, 1), 200);
    const offset = (page - 1) * limit;
    const search = (req.query.q || '').trim();

    let whereSql = 'WHERE c.user_id = $1';
    const params = [id];
    if (search) {
      params.push(`%${search}%`);
      whereSql += ` AND (c.display_name ILIKE $${params.length}
                       OR EXISTS (SELECT 1 FROM user_contact_phones p
                                  WHERE p.contact_id = c.id AND p.number ILIKE $${params.length})
                       OR EXISTS (SELECT 1 FROM user_contact_emails e
                                  WHERE e.contact_id = c.id AND e.address ILIKE $${params.length}))`;
    }

    const totalRow = await one(
      `SELECT COUNT(*)::int AS c FROM user_contacts c ${whereSql}`,
      params
    );
    const total = totalRow.c;

    params.push(limit, offset);
    const rows = await many(
      `SELECT c.id, c.client_contact_id, c.display_name, c.photo_uri,
              c.starred, c.notes, c.created_at
         FROM user_contacts c
         ${whereSql}
         ORDER BY c.display_name ASC NULLS LAST, c.id ASC
         LIMIT $${params.length - 1} OFFSET $${params.length}`,
      params
    );

    // Hydrate child rows for the page
    const ids = rows.map(r => r.id);
    let phones = [], emails = [], addresses = [], orgs = [], websites = [], events = [];
    if (ids.length) {
      phones    = await many(
        `SELECT contact_id, number, type FROM user_contact_phones WHERE contact_id = ANY($1::bigint[])`,
        [ids]);
      emails    = await many(
        `SELECT contact_id, address, type FROM user_contact_emails WHERE contact_id = ANY($1::bigint[])`,
        [ids]);
      addresses = await many(
        `SELECT contact_id, formatted_address, street, city, region, postcode, country, type
         FROM user_contact_addresses WHERE contact_id = ANY($1::bigint[])`,
        [ids]);
      orgs      = await many(
        `SELECT contact_id, company, title, department FROM user_contact_orgs WHERE contact_id = ANY($1::bigint[])`,
        [ids]);
      websites  = await many(
        `SELECT contact_id, url FROM user_contact_websites WHERE contact_id = ANY($1::bigint[])`,
        [ids]);
      events    = await many(
        `SELECT contact_id, date_text, type FROM user_contact_events WHERE contact_id = ANY($1::bigint[])`,
        [ids]);
    }
    const groupBy = (arr) => {
      const m = {};
      for (const x of arr) {
        const k = String(x.contact_id);
        if (!m[k]) m[k] = [];
        m[k].push(x);
      }
      return m;
    };
    const ph = groupBy(phones), em = groupBy(emails), ad = groupBy(addresses);
    const og = groupBy(orgs), ws = groupBy(websites), ev = groupBy(events);

    const contacts = rows.map(r => ({
      id: r.id,
      display_name: r.display_name,
      photo_uri: r.photo_uri,
      starred: r.starred,
      notes: r.notes,
      created_at: r.created_at,
      phones:    ph[String(r.id)] || [],
      emails:    em[String(r.id)] || [],
      addresses: ad[String(r.id)] || [],
      orgs:      og[String(r.id)] || [],
      websites:  ws[String(r.id)] || [],
      events:    ev[String(r.id)] || []
    }));

    res.json({
      contacts,
      total,
      page,
      limit,
      total_pages: Math.max(1, Math.ceil(total / limit))
    });
  } catch (e) { next(e); }
});

// GET /admin/users/:id/rules  — synced blocking rules
router.get('/users/:id/rules', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const rules = await many(
      'SELECT id, rule_type, pattern, action, created_at FROM user_rules WHERE user_id = $1 ORDER BY id',
      [id]
    );
    res.json({ rules });
  } catch (e) { next(e); }
});

// GET /admin/users/:id/blocked-calls — paginated blocked-call log
router.get('/users/:id/blocked-calls', requireAdmin, async (req, res, next) => {
  try {
    const id     = parseInt(req.params.id, 10);
    const page   = Math.max(parseInt(req.query.page, 10) || 1, 1);
    const limit  = Math.min(Math.max(parseInt(req.query.limit, 10) || 50, 1), 200);
    const offset = (page - 1) * limit;
    const search = (req.query.q || '').trim();

    let whereSql = 'WHERE user_id = $1';
    const params = [id];
    if (search) {
      params.push(`%${search}%`);
      whereSql += ` AND (number ILIKE $${params.length}
                       OR rule_pattern ILIKE $${params.length})`;
    }

    const totalRow = await one(
      `SELECT COUNT(*)::int AS c FROM blocked_calls ${whereSql}`, params);
    const total = totalRow.c;

    params.push(limit, offset);
    const calls = await many(
      `SELECT id, number, rule_type, rule_pattern, rule_action,
              blocked_at_ms, blocked_at
         FROM blocked_calls
         ${whereSql}
         ORDER BY blocked_at DESC
         LIMIT $${params.length - 1} OFFSET $${params.length}`,
      params);

    res.json({
      calls, total, page, limit,
      total_pages: Math.max(1, Math.ceil(total / limit))
    });
  } catch (e) { next(e); }
});

// DELETE /admin/users/:id
router.delete('/users/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const u = await one('SELECT dial_code, mobile FROM users WHERE id = $1', [id]);
    await query('DELETE FROM users WHERE id = $1', [id]);
    if (u) await audit(req.admin.username, 'user_deleted', `id=${id}, ${u.dial_code}${u.mobile}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// POST /admin/users/:id/reset
router.post('/users/:id/reset', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    await query(
      "UPDATE users SET status='pending', pin_set_at=NULL, verified_at=NULL WHERE id=$1",
      [id]
    );
    await query('DELETE FROM otps WHERE user_id = $1', [id]);
    await audit(req.admin.username, 'user_reset', `id=${id}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// GET /admin/settings
router.get('/settings', requireAdmin, async (req, res, next) => {
  try {
    const rows = await many('SELECT key, value FROM settings');
    const out = {};
    rows.forEach(r => out[r.key] = r.value);
    res.json({ settings: out });
  } catch (e) { next(e); }
});

// PUT /admin/settings
router.put('/settings', requireAdmin, async (req, res, next) => {
  try {
    const allowed = [
      'sms_provider', 'sms_api_key', 'sms_api_secret',
      'sms_sender_id', 'sms_endpoint', 'sms_template',
      'otp_length', 'otp_expiry_minutes', 'otp_show_in_response'
    ];
    const incoming = req.body || {};
    for (const k of allowed) {
      if (typeof incoming[k] !== 'undefined') {
        await query(
          `INSERT INTO settings(key, value) VALUES ($1, $2)
           ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value`,
          [k, String(incoming[k])]
        );
      }
    }
    await audit(req.admin.username, 'settings_updated', '');
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// POST /admin/change-password
router.post('/change-password', requireAdmin, async (req, res, next) => {
  try {
    const { current, next: newPass } = req.body || {};
    const a = await one('SELECT * FROM admins WHERE id = $1', [req.admin.id]);
    if (!a || !bcrypt.compareSync(current, a.password_hash)) {
      return res.status(400).json({ error: 'Current password is incorrect' });
    }
    if (!newPass || newPass.length < 6) {
      return res.status(400).json({ error: 'New password must be at least 6 characters' });
    }
    const hash = bcrypt.hashSync(newPass, 10);
    await query('UPDATE admins SET password_hash = $1 WHERE id = $2', [hash, a.id]);
    await audit(req.admin.username, 'password_changed', '');
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// GET /admin/audit
router.get('/audit', requireAdmin, async (req, res, next) => {
  try {
    const log = await many('SELECT * FROM audit_log ORDER BY id DESC LIMIT 500');
    res.json({ log });
  } catch (e) { next(e); }
});

module.exports = router;
