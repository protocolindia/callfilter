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
      used_otps:      (await one('SELECT COUNT(*)::int AS c FROM otps WHERE consumed_at IS NOT NULL')).c
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
