const express = require('express');
const bcrypt = require('bcryptjs');
const { query, one, many } = require('./db');
const { requireAdmin, requireRole, globalScopeWhere, PERMS } = require('./auth_admin.js');
const router = express.Router();

async function audit(actor, event, details) {
  await query(
    'INSERT INTO audit_log(actor, event, details) VALUES ($1, $2, $3)',
    [actor, event, details || '']
  );
}

// POST /admin/login
const { loginHandler } = require('./auth_admin.js');
router.post('/login', loginHandler);

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
    // global_db_admin can view app users (view only, no sensitive billing data)
    const role = req.admin.role;
    if (!['super_admin','admin','support','global_db_admin'].includes(role))
      return res.status(403).json({ error: 'Forbidden' });

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
    const role = req.admin.role;
    if (!['super_admin','admin','support','global_db_admin'].includes(role))
      return res.status(403).json({ error: 'Forbidden' });

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
      `SELECT id, number, rule_type, rule_pattern, rule_action, reason,
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
      'otp_length', 'otp_expiry_minutes', 'otp_show_in_response',
      'subscription_required',
      'razorpay_enabled', 'razorpay_mode',
      'razorpay_key_id_test', 'razorpay_secret_test',
      'razorpay_key_id_live', 'razorpay_secret_live',
      'razorpay_webhook_secret',
      'block_reasons',
      'global_blocklist_show_total',
      'global_blocklist_show_active'
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

// ============================================================
// BILLING — admin endpoints
// ============================================================

// --- Plans ---
router.get('/plans', requireAdmin, async (req, res, next) => {
  try {
    const plans = await many('SELECT * FROM plans ORDER BY id');
    res.json({ plans });
  } catch (e) { next(e); }
});

router.post('/plans', requireAdmin, async (req, res, next) => {
  try {
    const { name, duration_days, actual_price, offer_price, currency, is_one_time_per_user } = req.body || {};
    if (!name || !duration_days || actual_price == null || offer_price == null) {
      return res.status(400).json({ error: 'name, duration_days, actual_price, offer_price required' });
    }
    const r = await one(
      `INSERT INTO plans(name, duration_days, actual_price, offer_price, currency, is_one_time_per_user)
       VALUES ($1, $2, $3, $4, $5, $6) RETURNING *`,
      [name, duration_days, actual_price, offer_price, currency || 'INR', is_one_time_per_user === true]
    );
    await audit(req.admin.username, 'plan_created', `id=${r.id}, name=${name}`);
    res.json({ plan: r });
  } catch (e) { next(e); }
});

router.put('/plans/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const { name, duration_days, actual_price, offer_price, currency, is_active, is_one_time_per_user } = req.body || {};
    const r = await one(
      `UPDATE plans
          SET name = COALESCE($2, name),
              duration_days = COALESCE($3, duration_days),
              actual_price = COALESCE($4, actual_price),
              offer_price = COALESCE($5, offer_price),
              currency = COALESCE($6, currency),
              is_active = COALESCE($7, is_active),
              is_one_time_per_user = COALESCE($8, is_one_time_per_user),
              updated_at = NOW()
        WHERE id = $1 RETURNING *`,
      [id, name, duration_days, actual_price, offer_price, currency, is_active,
       typeof is_one_time_per_user === 'boolean' ? is_one_time_per_user : null]
    );
    if (!r) return res.status(404).json({ error: 'Plan not found' });
    await audit(req.admin.username, 'plan_updated', `id=${id}`);
    res.json({ plan: r });
  } catch (e) { next(e); }
});

router.delete('/plans/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    await query(`UPDATE plans SET is_active = FALSE WHERE id = $1`, [id]);
    await audit(req.admin.username, 'plan_deactivated', `id=${id}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// --- Coupons ---
router.get('/coupons', requireAdmin, async (req, res, next) => {
  try {
    const coupons = await many('SELECT * FROM coupons ORDER BY id DESC');
    res.json({ coupons });
  } catch (e) { next(e); }
});

router.post('/coupons', requireAdmin, async (req, res, next) => {
  try {
    const { code, discount_type, discount_value, valid_until, max_uses } = req.body || {};
    if (!code || !discount_type || discount_value == null || !valid_until) {
      return res.status(400).json({ error: 'code, discount_type, discount_value, valid_until required' });
    }
    if (!['percent', 'flat'].includes(discount_type)) {
      return res.status(400).json({ error: 'discount_type must be percent or flat' });
    }
    const r = await one(
      `INSERT INTO coupons(code, discount_type, discount_value, valid_until, max_uses)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [code.toUpperCase(), discount_type, discount_value, valid_until, max_uses || null]
    );
    await audit(req.admin.username, 'coupon_created', `code=${code}`);
    res.json({ coupon: r });
  } catch (e) {
    if (e.code === '23505') return res.status(409).json({ error: 'Coupon code already exists' });
    next(e);
  }
});

router.put('/coupons/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const { discount_type, discount_value, valid_until, max_uses, is_active } = req.body || {};
    const r = await one(
      `UPDATE coupons
          SET discount_type  = COALESCE($2, discount_type),
              discount_value = COALESCE($3, discount_value),
              valid_until    = COALESCE($4, valid_until),
              max_uses       = COALESCE($5, max_uses),
              is_active      = COALESCE($6, is_active)
        WHERE id = $1 RETURNING *`,
      [id, discount_type, discount_value, valid_until, max_uses, is_active]
    );
    if (!r) return res.status(404).json({ error: 'Coupon not found' });
    await audit(req.admin.username, 'coupon_updated', `id=${id}`);
    res.json({ coupon: r });
  } catch (e) { next(e); }
});

router.delete('/coupons/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    await query(`UPDATE coupons SET is_active = FALSE WHERE id = $1`, [id]);
    await audit(req.admin.username, 'coupon_deactivated', `id=${id}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// --- Subscriptions overview ---
router.get('/subscriptions', requireAdmin, async (req, res, next) => {
  try {
    const page   = Math.max(parseInt(req.query.page, 10) || 1, 1);
    const limit  = Math.min(Math.max(parseInt(req.query.limit, 10) || 50, 1), 200);
    const offset = (page - 1) * limit;
    const filter = (req.query.status || '').trim();

    let where = '';
    const params = [];
    if (filter) {
      where = ' WHERE s.status = $1';
      params.push(filter);
    }

    const total = (await one(
      `SELECT COUNT(*)::int AS c FROM subscriptions s ${where}`, params)).c;

    params.push(limit, offset);
    const rows = await many(
      `SELECT s.id, s.user_id, s.status, s.is_trial, s.starts_at, s.expires_at,
              s.amount_paid, p.name AS plan_name,
              u.dial_code, u.mobile
         FROM subscriptions s
         LEFT JOIN users u ON u.id = s.user_id
         LEFT JOIN plans p ON p.id = s.plan_id
         ${where}
         ORDER BY s.expires_at DESC
         LIMIT $${params.length - 1} OFFSET $${params.length}`,
      params);

    res.json({
      subscriptions: rows, total, page, limit,
      total_pages: Math.max(1, Math.ceil(total / limit))
    });
  } catch (e) { next(e); }
});

// Get a specific user's subscription history
router.get('/users/:id/subscriptions', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const subs = await many(
      `SELECT s.*, p.name AS plan_name, p.duration_days, c.code AS coupon_code
         FROM subscriptions s
         LEFT JOIN plans   p ON p.id = s.plan_id
         LEFT JOIN coupons c ON c.id = s.coupon_id
        WHERE s.user_id = $1
        ORDER BY s.id DESC`, [id]);
    res.json({ subscriptions: subs });
  } catch (e) { next(e); }
});

// Manually grant a subscription / extend trial for a specific user
router.post('/users/:id/subscriptions', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const { plan_id, days, is_trial } = req.body || {};
    let durationDays;
    if (plan_id) {
      const plan = await one('SELECT * FROM plans WHERE id = $1', [plan_id]);
      if (!plan) return res.status(404).json({ error: 'Plan not found' });
      durationDays = plan.duration_days;
    } else if (days) {
      durationDays = parseInt(days, 10);
    } else {
      return res.status(400).json({ error: 'plan_id or days required' });
    }
    const r = await one(
      `INSERT INTO subscriptions(user_id, plan_id, status, is_trial, expires_at)
       VALUES ($1, $2, $3, $4, NOW() + ($5 || ' days')::interval) RETURNING *`,
      [id, plan_id || null, is_trial ? 'trial' : 'active', !!is_trial, String(durationDays)]
    );
    await audit(req.admin.username, 'subscription_granted',
      `user_id=${id}, plan_id=${plan_id}, days=${durationDays}, trial=${!!is_trial}`);
    res.json({ subscription: r });
  } catch (e) { next(e); }
});

// --- Payments overview ---
router.get('/payments', requireAdmin, async (req, res, next) => {
  try {
    const page   = Math.max(parseInt(req.query.page, 10) || 1, 1);
    const limit  = Math.min(Math.max(parseInt(req.query.limit, 10) || 50, 1), 200);
    const offset = (page - 1) * limit;

    const total = (await one('SELECT COUNT(*)::int AS c FROM payments')).c;
    const rows = await many(
      `SELECT p.id, p.user_id, p.amount, p.currency, p.status,
              p.razorpay_order_id, p.razorpay_payment_id,
              p.created_at,
              pl.name AS plan_name,
              u.dial_code, u.mobile
         FROM payments p
         LEFT JOIN users u  ON u.id = p.user_id
         LEFT JOIN plans pl ON pl.id = p.plan_id
         ORDER BY p.id DESC
         LIMIT $1 OFFSET $2`,
      [limit, offset]);
    res.json({
      payments: rows, total, page, limit,
      total_pages: Math.max(1, Math.ceil(total / limit))
    });
  } catch (e) { next(e); }
});


// ============================================================
// SCHEDULES — admin view (read-only; user manages from app)
// ============================================================
router.get('/users/:id/schedules', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const schedules = await many(
      `SELECT id, client_id, name, start_minute, end_minute, days_mask,
              is_enabled, allow_numbers, allow_names, quick_until_ms,
              freq_bypass_enabled, freq_count, freq_window_min,
              EXTRACT(EPOCH FROM last_toggled_at) * 1000 AS last_toggled_ms
         FROM schedules WHERE user_id = $1 ORDER BY id`,
      [id]
    );
    res.json({ schedules });
  } catch (e) { next(e); }
});

// ============================================================
// BLOCK-ALL STATE — admin view (read-only)
// ============================================================
router.get('/users/:id/block-all', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const state = await one(
      `SELECT mode, expires_at_ms, allow_numbers, allow_names, updated_at
         FROM block_all_state WHERE user_id = $1`, [id]);
    res.json({ state: state || null });
  } catch (e) { next(e); }
});

// ============================================================
// RULES — admin can create, update, delete
// ============================================================
router.post('/users/:id/rules', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const { rule_type, pattern, action } = req.body || {};
    if (!rule_type || !pattern || !action) {
      return res.status(400).json({ error: 'rule_type, pattern, action required' });
    }
    if (!['prefix','suffix','range','between'].includes(rule_type)) {
      return res.status(400).json({ error: 'invalid_rule_type' });
    }
    if (!['accept','reject'].includes(action)) {
      return res.status(400).json({ error: 'invalid_action' });
    }
    // Duplicate guard: same (user, type, pattern) shouldn't exist twice
    const dup = await one(
      'SELECT id FROM user_rules WHERE user_id = $1 AND rule_type = $2 AND pattern = $3',
      [id, rule_type, pattern]);
    if (dup) {
      return res.status(409).json({ error: 'duplicate_rule',
        message: 'A rule with this type and pattern already exists' });
    }
    // Generate a client_id so cloud→app sync can dedup correctly
    const clientId = 'admin-' + Date.now().toString(36) + '-' +
                     Math.random().toString(36).slice(2, 8);
    const rule = await one(
      `INSERT INTO user_rules(user_id, rule_type, pattern, action, client_id)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [id, rule_type, pattern, action, clientId]
    );
    await audit(req.admin.username, 'admin_rule_created',
      `user=$1{id} ${rule_type} ${pattern} ${action}`.replace('$1{id}', String(id)));
    res.json({ ok: true, rule });
  } catch (e) { next(e); }
});

router.put('/users/:id/rules/:rid', requireAdmin, async (req, res, next) => {
  try {
    const id  = parseInt(req.params.id, 10);
    const rid = parseInt(req.params.rid, 10);
    const { rule_type, pattern, action } = req.body || {};
    await query(
      `UPDATE user_rules
          SET rule_type = COALESCE($1, rule_type),
              pattern   = COALESCE($2, pattern),
              action    = COALESCE($3, action)
        WHERE id = $4 AND user_id = $5`,
      [rule_type || null, pattern || null, action || null, rid, id]
    );
    await audit(req.admin.username, 'admin_rule_updated', `user=${id} rid=${rid}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

router.delete('/users/:id/rules/:rid', requireAdmin, async (req, res, next) => {
  try {
    const id  = parseInt(req.params.id, 10);
    const rid = parseInt(req.params.rid, 10);
    await query('DELETE FROM user_rules WHERE id = $1 AND user_id = $2', [rid, id]);
    await audit(req.admin.username, 'admin_rule_deleted', `user=${id} rid=${rid}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// ============================================================
// RAZORPAY ORDERS — payment/transaction log
// ============================================================
router.get('/razorpay/orders', requireAdmin, async (req, res, next) => {
  try {
    const page   = Math.max(parseInt(req.query.page, 10) || 1, 1);
    const limit  = Math.min(Math.max(parseInt(req.query.limit, 10) || 50, 1), 200);
    const offset = (page - 1) * limit;
    const status = (req.query.status || '').trim();
    const userId = parseInt(req.query.user_id, 10) || null;

    const where = [];
    const params = [];
    if (status) { params.push(status); where.push(`o.status = $${params.length}`); }
    if (userId) { params.push(userId); where.push(`o.user_id = $${params.length}`); }
    const whereSql = where.length ? ('WHERE ' + where.join(' AND ')) : '';

    const total = await one(
      `SELECT COUNT(*)::int AS n FROM razorpay_orders o ${whereSql}`, params);
    params.push(limit); params.push(offset);
    const orders = await many(
      `SELECT o.id, o.user_id, o.plan_id, o.order_id, o.amount_paise, o.currency,
              o.status, o.razorpay_payment_id, o.created_at, o.paid_at,
              u.dial_code, u.mobile, u.name AS user_name,
              p.name AS plan_name
         FROM razorpay_orders o
         LEFT JOIN users u ON u.id = o.user_id
         LEFT JOIN plans p ON p.id = o.plan_id
         ${whereSql}
         ORDER BY o.created_at DESC
         LIMIT $${params.length-1} OFFSET $${params.length}`, params);
    res.json({ orders, total: total.n, page, limit });
  } catch (e) { next(e); }
});


// POST /admin/users/:id/activate  — toggle user.status
router.post('/users/:id/activate', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const { active } = req.body || {};
    const newStatus = active === false ? 'disabled' : 'active';
    await query('UPDATE users SET status = $1 WHERE id = $2', [newStatus, id]);
    await audit(req.admin.username, 'user_' + newStatus, 'id=' + id);
    res.json({ ok: true, status: newStatus });
  } catch (e) { next(e); }
});

// ============================================================
// GLOBAL BLOCKLIST — admin CRUD
// ============================================================

// GET  /admin/global-blocklist   — paginated list with search
router.get('/global-blocklist', requireAdmin, async (req, res, next) => {
  try {
    const role = req.admin.role;
    // Check access
    if (!['super_admin','admin','support','billing',
          'global_db_admin','global_db_user'].includes(role)) {
      if (!PERMS[role]?.includes('*') && !PERMS[role]?.includes('global_blocklist_view'))
        return res.status(403).json({ error: 'Forbidden' });
    }

    const page   = Math.max(parseInt(req.query.page, 10) || 1, 1);
    const limit  = Math.min(parseInt(req.query.limit, 10) || 50, 200);
    const offset = (page - 1) * limit;
    const search = (req.query.search || '').trim();
    const reason = (req.query.reason || '').trim();

    // Build scope filter
    const scope = await globalScopeWhere(req.admin, 0);

    const where = [];
    const params = [...(scope?.params || [])];
    if (scope?.where) where.push(scope.where.replace(/^AND /, ''));
    // Hide soft-deleted unless super_admin
    if (role !== 'super_admin') where.push('g.deleted_at IS NULL');

    if (search) {
      params.push('%' + search.toLowerCase() + '%');
      where.push(`(LOWER(g.number) LIKE $${params.length} OR LOWER(g.notes) LIKE $${params.length})`);
    }
    if (reason) { params.push(reason); where.push(`g.reason = $${params.length}`); }

    const whereSql = where.length ? ('WHERE ' + where.join(' AND ')) : '';
    const totalRow = await one(`SELECT COUNT(*)::int AS n FROM global_blocklist g ${whereSql}`, params);

    params.push(limit); params.push(offset);
    const rows = await many(
      `SELECT g.id, g.number, g.reason, g.notes, g.added_by,
              g.active, g.created_at, g.updated_at, g.deleted_at,
              au.username AS added_by_username,
              au.display_name AS added_by_display,
              au.role AS added_by_role
         FROM global_blocklist g
         LEFT JOIN admin_users au ON au.id = g.added_by_admin_id
         ${whereSql}
         ORDER BY g.created_at DESC
         LIMIT $${params.length-1} OFFSET $${params.length}`, params);

    const reasons = await many('SELECT DISTINCT reason FROM global_blocklist WHERE deleted_at IS NULL ORDER BY reason');
    res.json({ ok: true, entries: rows, total: totalRow.n, page, limit,
               reasons: reasons.map(r => r.reason) });
  } catch (e) { next(e); }
});

// POST /admin/global-blocklist  — add entry
router.post('/global-blocklist', requireAdmin, async (req, res, next) => {
  try {
    const { number, reason, notes } = req.body || {};
    if (!number || !reason) {
      return res.status(400).json({ error: 'number and reason are required' });
    }
    const clean = number.trim().replace(/[\s\-().]/g, '');
    if (!clean) return res.status(400).json({ error: 'invalid number' });

    // Dedup check: is this number already in the active list?
    const exists = await one(
      `SELECT id FROM global_blocklist WHERE number = $1 AND active = TRUE`, [clean]);
    if (exists) {
      return res.status(409).json({ error: 'duplicate',
        message: 'This number is already in the active global blocklist' });
    }
    const entry = await one(
      `INSERT INTO global_blocklist(number, reason, notes, added_by, added_by_admin_id)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [clean, reason.trim(), (notes || '').trim() || null,
       req.admin.username, req.admin.id || null]);
    await audit(req.admin.username, 'global_block_added',
      `${clean} reason="${reason}"`);
    res.json({ ok: true, entry });
  } catch (e) { next(e); }
});

// PUT /admin/global-blocklist/:id  — edit entry
router.put('/global-blocklist/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const { number, reason, notes, active } = req.body || {};
    const clean = number ? number.trim().replace(/[\s\-().]/g, '') : undefined;

    // If changing number, check dedup
    if (clean) {
      const dup = await one(
        `SELECT id FROM global_blocklist WHERE number = $1 AND active = TRUE AND id <> $2`,
        [clean, id]);
      if (dup) {
        return res.status(409).json({ error: 'duplicate',
          message: 'Another active entry already has this number' });
      }
    }
    const entry = await one(
      `UPDATE global_blocklist
          SET number     = COALESCE($1, number),
              reason     = COALESCE($2, reason),
              notes      = COALESCE($3, notes),
              active     = COALESCE($4, active),
              updated_at = NOW()
        WHERE id = $5 RETURNING *`,
      [clean || null,
       reason ? reason.trim() : null,
       notes !== undefined ? ((notes || '').trim() || null) : null,
       active !== undefined ? active : null,
       id]);
    if (!entry) return res.status(404).json({ error: 'not found' });
    await audit(req.admin.username, 'global_block_updated', `id=${id}`);
    res.json({ ok: true, entry });
  } catch (e) { next(e); }
});

// DELETE /admin/global-blocklist/:id  — hard delete
router.delete('/global-blocklist/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const entry = await one('SELECT * FROM global_blocklist WHERE id = $1', [id]);
    if (!entry) return res.status(404).json({ error: 'not found' });

    // Check scope for global_db roles
    if (['global_db_admin','global_db_user'].includes(req.admin.role)) {
      const scope = await globalScopeWhere(req.admin, 0);
      if (scope && scope.params && !scope.params.includes(entry.added_by_admin_id))
        return res.status(403).json({ error: 'Out of scope' });
    }

    if (req.admin.role === 'super_admin') {
      // Hard delete
      await query('DELETE FROM global_blocklist WHERE id = $1', [id]);
      await audit(req.admin.username, 'global_block_hard_deleted', `${entry.number}`);
    } else {
      // Soft delete
      await query('UPDATE global_blocklist SET deleted_at = NOW(), active = FALSE WHERE id = $1', [id]);
      await audit(req.admin.username, 'global_block_soft_deleted', `${entry.number}`);
    }
    res.json({ ok: true });
  } catch (e) { next(e); }
});


// ── GLOBAL BLOCKLIST IMPORT — bulk CSV/Excel upload ───────────────────
router.post('/global-blocklist/import', requireAdmin, async (req, res, next) => {
  try {
    const { rows } = req.body || {};
    if (!Array.isArray(rows) || rows.length === 0) {
      return res.status(400).json({ error: 'rows array is required' });
    }

    let inserted = 0, skipped = 0, errors = [];

    for (const row of rows) {
      const number = (row.number || row.Number || row.NUMBER || '').toString().trim().replace(/[\s\-().]/g, '');
      const reason = (row.reason || row.Reason || row.REASON || '').toString().trim();
      const notes  = (row.notes  || row.Notes  || row.NOTES  || '').toString().trim();

      if (!number || !reason) { skipped++; continue; }

      try {
        // Skip duplicates silently
        const exists = await one(
          'SELECT id FROM global_blocklist WHERE number = $1 AND active = TRUE', [number]);
        if (exists) { skipped++; continue; }

        await query(
          `INSERT INTO global_blocklist(number, reason, notes, added_by)
           VALUES ($1, $2, $3, $4)`,
          [number, reason, notes || null, req.admin.username + ' (import)']);
        inserted++;
      } catch (e) {
        errors.push({ number, error: e.message });
      }
    }

    await audit(req.admin.username, 'global_block_imported',
      `inserted=${inserted} skipped=${skipped}`);
    res.json({ ok: true, inserted, skipped, errors: errors.slice(0, 10) });
  } catch (e) { next(e); }
});


// GET /admin/users/:id/global-config — user's global blocklist enabled reasons
router.get('/users/:id/global-config', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const row = await one('SELECT global_enabled_reasons FROM users WHERE id = $1', [id]);
    if (!row) return res.status(404).json({ error: 'not found' });
    let reasons = [];
    try { reasons = row.global_enabled_reasons ? JSON.parse(row.global_enabled_reasons) : []; }
    catch { reasons = []; }
    res.json({ ok: true, enabled_reasons: reasons });
  } catch (e) { next(e); }
});


// ============================================================
// ADMIN USER MANAGEMENT — CRUD for multi-admin roles
// ============================================================

// GET /admin/admin-users — list admin users (scoped by role)
router.get('/admin-users', requireAdmin, async (req, res, next) => {
  try {
    const role = req.admin.role;
    let rows;

    if (role === 'super_admin' || role === 'admin') {
      // See all admin users
      rows = await many(
        `SELECT id, username, display_name, role, active, parent_id,
                created_at, last_login_at, deleted_at,
                (SELECT username FROM admin_users p WHERE p.id = au.parent_id) AS parent_username,
                (SELECT username FROM admin_users cr WHERE cr.id = au.created_by) AS created_by_username
           FROM admin_users au
          ORDER BY created_at ASC`
      );
    } else if (role === 'global_db_admin') {
      // Only see own sub-users (global_db_user children)
      rows = await many(
        `SELECT id, username, display_name, role, active, parent_id,
                created_at, last_login_at, deleted_at
           FROM admin_users
          WHERE parent_id = $1 AND role = 'global_db_user'
          ORDER BY created_at ASC`,
        [req.admin.id]
      );
    } else {
      return res.status(403).json({ error: 'Forbidden' });
    }
    res.json({ ok: true, users: rows });
  } catch (e) { next(e); }
});

// POST /admin/admin-users — create admin user
router.post('/admin-users', requireAdmin, async (req, res, next) => {
  try {
    const creatorRole = req.admin.role;
    if (!['super_admin','admin','global_db_admin'].includes(creatorRole))
      return res.status(403).json({ error: 'Forbidden' });

    const { username, password, display_name, role } = req.body || {};
    if (!username || !password || !role)
      return res.status(400).json({ error: 'username, password and role are required' });

    // Role restrictions
    if (creatorRole === 'global_db_admin' && role !== 'global_db_user')
      return res.status(403).json({ error: 'Global DB Admin can only create global_db_user accounts' });
    if (creatorRole === 'admin' && role === 'super_admin')
      return res.status(403).json({ error: 'Admin cannot create super_admin accounts' });

    const bcrypt = require('bcryptjs');
    const hash = await bcrypt.hash(password, 12);
    const parent_id = creatorRole === 'global_db_admin' ? req.admin.id : null;

    const newUser = await one(
      `INSERT INTO admin_users(username, password_hash, display_name, role, parent_id, created_by)
       VALUES ($1, $2, $3, $4, $5, $6) RETURNING id, username, display_name, role, active, created_at`,
      [username.trim(), hash, (display_name||'').trim()||null, role, parent_id, req.admin.id]
    );

    await audit(req.admin.username, 'admin_user_created',
      `${newUser.username} role=${role}`);
    res.json({ ok: true, user: newUser });
  } catch (e) {
    if (e.message?.includes('unique')) return res.status(409).json({ error: 'Username already exists' });
    next(e);
  }
});

// PUT /admin/admin-users/:id — update admin user
router.put('/admin-users/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const creatorRole = req.admin.role;

    // Check scope
    const target = await one('SELECT * FROM admin_users WHERE id = $1', [id]);
    if (!target) return res.status(404).json({ error: 'Not found' });

    if (creatorRole === 'global_db_admin' && target.parent_id !== req.admin.id)
      return res.status(403).json({ error: 'Out of scope' });
    if (!['super_admin','admin','global_db_admin'].includes(creatorRole))
      return res.status(403).json({ error: 'Forbidden' });

    const { display_name, password, active, role } = req.body || {};
    const bcrypt = require('bcryptjs');

    const hash = password ? await bcrypt.hash(password, 12) : null;

    const updated = await one(
      `UPDATE admin_users SET
          display_name  = COALESCE($1, display_name),
          password_hash = COALESCE($2, password_hash),
          active        = COALESCE($3, active),
          role          = COALESCE($4, role),
          updated_at    = NOW()
        WHERE id = $5
        RETURNING id, username, display_name, role, active`,
      [display_name||null, hash, active??null, role||null, id]
    );
    await audit(req.admin.username, 'admin_user_updated', `id=${id}`);
    res.json({ ok: true, user: updated });
  } catch (e) { next(e); }
});

// DELETE /admin/admin-users/:id — soft or hard delete
router.delete('/admin-users/:id', requireAdmin, async (req, res, next) => {
  try {
    const id = parseInt(req.params.id, 10);
    const creatorRole = req.admin.role;

    if (!['super_admin','admin','global_db_admin'].includes(creatorRole))
      return res.status(403).json({ error: 'Forbidden' });

    const target = await one('SELECT * FROM admin_users WHERE id = $1', [id]);
    if (!target) return res.status(404).json({ error: 'Not found' });
    if (target.id === req.admin.id)
      return res.status(400).json({ error: 'Cannot delete yourself' });

    if (creatorRole === 'global_db_admin' && target.parent_id !== req.admin.id)
      return res.status(403).json({ error: 'Out of scope' });

    if (creatorRole === 'super_admin') {
      // Hard delete
      await query('DELETE FROM admin_users WHERE id = $1', [id]);
      await audit(req.admin.username, 'admin_user_hard_deleted', `${target.username}`);
    } else {
      // Soft delete
      await query('UPDATE admin_users SET deleted_at = NOW(), active = FALSE WHERE id = $1', [id]);
      await audit(req.admin.username, 'admin_user_soft_deleted', `${target.username}`);
    }
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// POST /admin/admin-users/:id/restore — restore soft-deleted (super_admin only)
router.post('/admin-users/:id/restore', requireAdmin,
  async (req, res, next) => {
  try {
    if (req.admin.role !== 'super_admin')
      return res.status(403).json({ error: 'Only super_admin can restore' });
    await query(
      'UPDATE admin_users SET deleted_at = NULL, active = TRUE WHERE id = $1', [req.params.id]);
    await audit(req.admin.username, 'admin_user_restored', `id=${req.params.id}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// GET /admin/me — current admin user info
router.get('/me', requireAdmin, async (req, res) => {
  res.json({ ok: true, admin: req.admin });
});

module.exports = router;
