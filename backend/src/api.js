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
    const { dial_code, mobile, country_iso, device_info, name } = req.body || {};
    if (!dial_code || !mobile) {
      return res.status(400).json({ error: 'dial_code and mobile required' });
    }
    const cleanName = (name || '').trim().slice(0, 100);

    let user = await one(
      'SELECT * FROM users WHERE dial_code = $1 AND mobile = $2',
      [dial_code, mobile]
    );

    if (user && cleanName && user.name !== cleanName) {
      await query('UPDATE users SET name = $1 WHERE id = $2', [cleanName, user.id]);
      user.name = cleanName;
    }

    if (!user) {
      user = await one(
        `INSERT INTO users(mobile, dial_code, country_iso, device_info, name)
         VALUES ($1, $2, $3, $4, $5) RETURNING *`,
        [mobile, dial_code, country_iso || '', device_info || '', cleanName || null]
      );
      await audit('android', 'user_created', `${dial_code}${mobile}`);

      // Grant a free trial. Trial duration is configurable via settings.trial_days.
      const trialDays = parseInt((await getSetting('trial_days')) || '7', 10);
      if (trialDays > 0) {
        await query(
          `INSERT INTO subscriptions(user_id, status, is_trial, expires_at)
           VALUES ($1, 'trial', TRUE, NOW() + ($2 || ' days')::interval)`,
          [user.id, String(trialDays)]
        );
        await audit('system', 'trial_granted', `user_id=${user.id}, days=${trialDays}`);
      }
    }

    const code = await genOtp();
    const expires = await expiryStamp();
    await query(
      'INSERT INTO otps(user_id, code, expires_at) VALUES ($1, $2, $3)',
      [user.id, code, expires]
    );
    await audit('android', 'otp_generated', `user_id=${user.id}`);

    // Dev mode = SMS provider is 'none' (so there's no way to actually
    // deliver the OTP) OR the legacy otp_show_in_response toggle is on.
    // In either case, return the OTP in the JSON response so the Android
    // app can display it on screen.
    const smsProvider = (await getSetting('sms_provider')) || 'none';
    const legacyToggle = (await getSetting('otp_show_in_response')) === 'true';
    const isDevMode = smsProvider === 'none' || legacyToggle;

    // TODO: when smsProvider !== 'none', dispatch via that provider here.

    res.json({
      ok: true,
      user_id: user.id,
      otp: isDevMode ? code : undefined,
      delivery: isDevMode ? 'in_response' : 'sms'
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

    // Tell the client whether this user already has a PIN (returning user re-install)
    // so the app can route to LoginActivity instead of SetPinActivity.
    const u = await one('SELECT pin_set_at, pin_hash FROM users WHERE id = $1', [user_id]);
    res.json({
      ok: true,
      pin_set: !!(u && u.pin_set_at),
      pin_hash: u && u.pin_hash ? u.pin_hash : null
    });
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
// ============================================================
// DIFFERENTIAL RULES SYNC (v25.8)
//
// Old design (replaced): /api/rules/sync mirrored the client's list to the
// server by DELETE+INSERT. That destroyed admin-added rules whenever the
// client synced, and lost user rules if the client raced with admin edits.
//
// New design: differential adds and deletes by client_id (UUID). Both app
// and admin set client_id when creating rules. The mirror /rules/sync
// endpoint is kept for backward-compat but now MERGES (upserts) instead
// of replacing.
// ============================================================

// POST /api/rules/add  — add ONE rule (no destructive side effects)
// Body: { user_id, client_id, type, pattern, action }
router.post('/rules/add', async (req, res, next) => {
  try {
    const { user_id, client_id, type, pattern, action } = req.body || {};
    if (!user_id || !client_id || !type || !pattern || !action) {
      return res.status(400).json({ error: 'missing_fields' });
    }
    // Dedup guard: don't allow two rules with the same (user, type, pattern).
    // We allow re-syncing the SAME client_id (idempotent upsert via ON CONFLICT),
    // but a different client_id with the same type+pattern is a duplicate.
    const dup = await one(
      `SELECT id, client_id FROM user_rules
         WHERE user_id = $1 AND rule_type = $2 AND pattern = $3
         LIMIT 1`,
      [user_id, type, pattern]);
    if (dup && dup.client_id !== client_id) {
      // Tell the app this is a no-op so it can dedup locally too
      return res.json({ ok: true, deduplicated: true, existing_client_id: dup.client_id });
    }
    // Upsert by (user_id, client_id) so the call is idempotent
    await query(
      `INSERT INTO user_rules(user_id, rule_type, pattern, action, client_id)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (user_id, client_id) DO UPDATE SET
         rule_type = EXCLUDED.rule_type,
         pattern   = EXCLUDED.pattern,
         action    = EXCLUDED.action`,
      [user_id, type, pattern, action, client_id]
    );
    const total = await one(
      'SELECT COUNT(*)::int AS n FROM user_rules WHERE user_id = $1', [user_id]);
    await query(
      `UPDATE users SET last_rules_sync = NOW(), rules_count = $2 WHERE id = $1`,
      [user_id, total.n]
    );
    await audit('android', 'rule_added', `user_id=${user_id} client=${client_id} ${type} ${pattern} ${action}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// POST /api/rules/delete — delete ONE rule by client_id
// Body: { user_id, client_id }
router.post('/rules/delete', async (req, res, next) => {
  try {
    const { user_id, client_id } = req.body || {};
    if (!user_id || !client_id) return res.status(400).json({ error: 'missing_fields' });
    await query(
      'DELETE FROM user_rules WHERE user_id = $1 AND client_id = $2',
      [user_id, client_id]);
    const total = await one(
      'SELECT COUNT(*)::int AS n FROM user_rules WHERE user_id = $1', [user_id]);
    await query(
      `UPDATE users SET last_rules_sync = NOW(), rules_count = $2 WHERE id = $1`,
      [user_id, total.n]
    );
    await audit('android', 'rule_deleted', `user_id=${user_id} client=${client_id}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// POST /api/rules/sync — LEGACY mirror endpoint, now MERGES instead of replaces.
// Body: { user_id, rules: [{client_id, type, pattern, action}, ...] }
// Behavior: upsert each rule (don't delete anything). Old clients still work.
router.post('/rules/sync', async (req, res, next) => {
  try {
    const { user_id, rules } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });
    if (!Array.isArray(rules)) return res.status(400).json({ error: 'rules must be an array' });

    for (const r of rules) {
      if (!r || !r.type || !r.pattern || !r.action) continue;
      if (!r.client_id) continue;  // can't upsert without client_id
      await query(
        `INSERT INTO user_rules(user_id, rule_type, pattern, action, client_id)
         VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (user_id, client_id) DO UPDATE SET
           rule_type = EXCLUDED.rule_type,
           pattern   = EXCLUDED.pattern,
           action    = EXCLUDED.action`,
        [user_id, r.type, r.pattern, r.action, r.client_id]
      );
    }

    const total = await one(
      'SELECT COUNT(*)::int AS n FROM user_rules WHERE user_id = $1', [user_id]);
    await query(
      `UPDATE users SET last_rules_sync = NOW(), rules_count = $2 WHERE id = $1`,
      [user_id, total.n]
    );
    await audit('android', 'rules_sync_merge', `user_id=${user_id} sent=${rules.length} total=${total.n}`);
    res.json({ ok: true, count: total.n });
  } catch (e) { next(e); }
});

// GET /api/rules/list?user_id=N — pull this user's rules down (used after reinstall)
router.get('/rules/list', async (req, res, next) => {
  try {
    const userId = parseInt(req.query.user_id, 10);
    if (!userId) return res.status(400).json({ error: 'user_id required' });
    const rules = await many(
      `SELECT id, rule_type, pattern, action, client_id
         FROM user_rules WHERE user_id = $1 ORDER BY id`,
      [userId]
    );
    res.json({ ok: true, rules });
  } catch (e) { next(e); }
});

// GET /api/blocked-calls/list?user_id=N&limit=200 — pull blocked-call log
router.get('/blocked-calls/list', async (req, res, next) => {
  try {
    const userId = parseInt(req.query.user_id, 10);
    const limit = Math.min(Math.max(parseInt(req.query.limit, 10) || 200, 1), 1000);
    if (!userId) return res.status(400).json({ error: 'user_id required' });
    const calls = await many(
      `SELECT client_id, number, rule_type, rule_pattern, rule_action, blocked_at_ms
         FROM blocked_calls
        WHERE user_id = $1
        ORDER BY blocked_at DESC
        LIMIT $2`,
      [userId, limit]
    );
    res.json({ ok: true, calls });
  } catch (e) { next(e); }
});

// ============================================================
// Blocked-calls sync — append-only.
// Body: { user_id, calls: [{client_id, number, rule_type, rule_pattern,
//                           rule_action, blocked_at_ms}, ...] }
// We dedup by (user_id, client_id) so re-uploads are idempotent.
// ============================================================
router.post('/blocked-calls/sync', async (req, res, next) => {
  try {
    const { user_id, calls } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });
    if (!Array.isArray(calls)) return res.status(400).json({ error: 'calls must be an array' });

    let inserted = 0;
    for (const c of calls) {
      if (!c || !c.client_id || !c.blocked_at_ms) continue;
      const r = await query(
        `INSERT INTO blocked_calls
           (user_id, client_id, number, rule_type, rule_pattern, rule_action,
            blocked_at_ms, blocked_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7::bigint, to_timestamp($7::bigint / 1000.0))
         ON CONFLICT (user_id, client_id) DO NOTHING`,
        [user_id, String(c.client_id), c.number || null,
         c.rule_type || null, c.rule_pattern || null, c.rule_action || null,
         Number(c.blocked_at_ms)]
      );
      if (r.rowCount > 0) inserted++;
    }

    const total = await one(
      `SELECT COUNT(*)::int AS c FROM blocked_calls WHERE user_id = $1`, [user_id]);
    await query(
      `UPDATE users SET blocked_calls_count = $2 WHERE id = $1`,
      [user_id, total.c]);

    await audit('android', 'blocked_calls_sync',
      `user_id=${user_id}, inserted=${inserted}, total=${total.c}`);
    res.json({ ok: true, inserted, total: total.c });
  } catch (e) { next(e); }
});

// ============================================================
// Google Play Billing — purchase verification
// Body: { user_id, product_id, purchase_token }
//
// In production this should call Google Play Developer API:
//   GET https://androidpublisher.googleapis.com/androidpublisher/v3/applications
//        /{packageName}/purchases/subscriptionsv2/tokens/{token}
// using a service account JWT. For now we accept the purchase token, log it,
// and create the subscription record. Add the Google API verification before
// going live with paid users.
// ============================================================
router.post('/billing/google-play/verify', async (req, res, next) => {
  try {
    const { user_id, product_id, purchase_token } = req.body || {};
    if (!user_id || !product_id || !purchase_token) {
      return res.status(400).json({ error: 'user_id, product_id, purchase_token required' });
    }

    // TODO: call Google's API to verify purchase_token is real and active.
    // For v23 we trust the client and log the token. The TODO is captured in
    // store-listing/PRODUCTION_CHECKLIST.md.

    // Map Play product IDs to a duration. In a fuller version this map lives
    // in a `play_products` table.
    const durationDaysByProduct = {
      'callfilter_monthly': 30,
      'callfilter_yearly':  365
    };
    const durationDays = durationDaysByProduct[product_id] || 30;

    // Insert subscription
    const sub = await one(
      `INSERT INTO subscriptions(user_id, status, is_trial, expires_at, payment_id)
       VALUES ($1, 'active', FALSE, NOW() + ($2 || ' days')::interval, $3) RETURNING *`,
      [user_id, String(durationDays), purchase_token]
    );

    // Log payment
    await query(
      `INSERT INTO payments(user_id, amount, currency, status, raw_payload)
       VALUES ($1, 0, 'INR', 'paid', $2)`,
      [user_id, JSON.stringify({ source: 'google_play', product_id, purchase_token })]
    );

    await audit('android', 'play_billing_purchase',
      `user_id=${user_id}, product=${product_id}`);

    res.json({ ok: true, subscription: sub });
  } catch (e) { next(e); }
});

// GET /api/health
router.get('/health', (req, res) => res.json({ ok: true, ts: new Date().toISOString() }));

// ============================================================
// BILLING — public endpoints called from the Android app
// ============================================================

// GET /api/subscription/:user_id  — current subscription status
router.get('/subscription/:user_id', async (req, res, next) => {
  try {
    const userId = parseInt(req.params.user_id, 10);
    if (!userId) return res.status(400).json({ error: 'user_id required' });

    // Dev/internal mode: subscription gating disabled by admin
    const subRequired = (await getSetting('subscription_required')) !== 'false';
    if (!subRequired) {
      return res.json({
        ok: true,
        has_subscription: true,
        active: true,
        seconds_remaining: 31536000,  // 1 year
        subscription: {
          id: 0,
          status: 'unrestricted',
          is_trial: false,
          plan_name: 'Unrestricted (dev mode)',
          seconds_remaining: 31536000,
          active: true
        }
      });
    }

    // Get the user's BEST subscription: prefer one whose expiry hasn't passed yet,
    // then fall back to the most recent. This way a still-valid 30-day trial isn't
    // hidden by a later expired row from a different grant.
    const sub = await one(
      `SELECT s.id, s.status, s.is_trial, s.starts_at, s.expires_at, s.amount_paid,
              p.id AS plan_id, p.name AS plan_name, p.duration_days,
              p.actual_price, p.offer_price, p.currency
         FROM subscriptions s
         LEFT JOIN plans p ON p.id = s.plan_id
        WHERE s.user_id = $1
        ORDER BY
          CASE WHEN s.expires_at > NOW() AND s.status IN ('trial','active') THEN 0 ELSE 1 END,
          s.expires_at DESC
        LIMIT 1`,
      [userId]
    );

    const now = new Date();
    let active = false;
    let secondsRemaining = 0;
    if (sub) {
      const expires = new Date(sub.expires_at);
      active = expires > now && (sub.status === 'trial' || sub.status === 'active');
      secondsRemaining = Math.max(0, Math.floor((expires - now) / 1000));

      // Auto-flip status to "expired" only if this single row is past expiry
      if (!active && sub.status !== 'expired' && sub.status !== 'cancelled') {
        await query(`UPDATE subscriptions SET status = 'expired' WHERE id = $1`, [sub.id]);
        sub.status = 'expired';
      }
    }

    res.json({
      ok: true,
      has_subscription: !!sub,
      active,
      seconds_remaining: secondsRemaining,
      subscription: sub ? {
        ...sub,
        active,
        seconds_remaining: secondsRemaining,
        is_trial: sub.is_trial
      } : null
    });
  } catch (e) { next(e); }
});

// GET /api/plans — all active plans (the app shows these on the paywall)
router.get('/plans', async (req, res, next) => {
  try {
    const userId = parseInt(req.query.user_id, 10) || 0;
    const plans = await many(
      `SELECT id, name, duration_days, actual_price, offer_price, currency,
              is_one_time_per_user
         FROM plans WHERE is_active = TRUE ORDER BY duration_days`
    );
    // For one-time plans, mark whether THIS user has used it (any prior sub OR
    // razorpay order with status=paid for that plan_id counts as used).
    let used = new Set();
    if (userId) {
      const sUsed = await many(
        `SELECT DISTINCT plan_id FROM subscriptions
           WHERE user_id = $1 AND plan_id IS NOT NULL`, [userId]);
      sUsed.forEach(r => used.add(r.plan_id));
      const rUsed = await many(
        `SELECT DISTINCT plan_id FROM razorpay_orders
           WHERE user_id = $1 AND status = 'paid' AND plan_id IS NOT NULL`, [userId]);
      rUsed.forEach(r => used.add(r.plan_id));
    }
    const out = plans.map(p => ({
      ...p,
      is_free: parseFloat(p.actual_price) === 0 && parseFloat(p.offer_price) === 0,
      already_used: p.is_one_time_per_user && used.has(p.id)
    }));
    res.json({ plans: out });
  } catch (e) { next(e); }
});

// POST /api/coupons/validate  — { code, plan_id }
// Returns whether the coupon is currently valid and the resulting price
router.post('/coupons/validate', async (req, res, next) => {
  try {
    const { code, plan_id } = req.body || {};
    if (!code || !plan_id) return res.status(400).json({ error: 'code and plan_id required' });

    const coupon = await one(
      `SELECT * FROM coupons WHERE LOWER(code) = LOWER($1)`, [code]);
    if (!coupon)              return res.json({ valid: false, reason: 'Coupon not found' });
    if (!coupon.is_active)    return res.json({ valid: false, reason: 'Coupon inactive' });
    const now = new Date();
    if (new Date(coupon.valid_until) < now)
      return res.json({ valid: false, reason: 'Coupon expired' });
    if (new Date(coupon.valid_from) > now)
      return res.json({ valid: false, reason: 'Coupon not yet valid' });
    if (coupon.max_uses && coupon.uses_count >= coupon.max_uses)
      return res.json({ valid: false, reason: 'Coupon usage limit reached' });

    const plan = await one(`SELECT * FROM plans WHERE id = $1`, [plan_id]);
    if (!plan) return res.json({ valid: false, reason: 'Plan not found' });

    const base = plan.offer_price;
    let discount = 0;
    if (coupon.discount_type === 'percent') {
      discount = Math.floor((base * coupon.discount_value) / 100);
    } else {
      discount = coupon.discount_value;
    }
    const finalPrice = Math.max(0, base - discount);

    res.json({
      valid: true,
      coupon_id: coupon.id,
      base_price: base,
      discount,
      final_price: finalPrice,
      currency: plan.currency
    });
  } catch (e) { next(e); }
});

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

    // Dev/internal mode: subscription gating disabled by admin
    const __subRequired = (await getSetting('subscription_required')) !== 'false';

    // Lookup by user_id if given, else by (dial_code+mobile). The latter is
    // used by LoginActivity in mobile-entry mode (before we have a local
    // user_id) to ask "does this number have an account?"
    let u;
    if (user_id) {
      u = await one('SELECT id, dial_code, mobile, status, pin_set_at FROM users WHERE id = $1',
                    [user_id]);
    } else if (dial_code && mobile) {
      u = await one(
        'SELECT id, dial_code, mobile, status, pin_set_at FROM users WHERE dial_code = $1 AND mobile = $2',
        [dial_code, mobile]);
    } else {
      return res.status(400).json({ error: 'user_id or (dial_code+mobile) required' });
    }
    if (!u) return res.json({ exists: false });

    const numberMatches = u.dial_code === dial_code && u.mobile === mobile;

    // Best subscription (prefer one that's still valid)
    const sub = await one(
      `SELECT s.id, s.status, s.is_trial, s.expires_at,
              p.name AS plan_name, p.duration_days
         FROM subscriptions s
         LEFT JOIN plans p ON p.id = s.plan_id
        WHERE s.user_id = $1
        ORDER BY
          CASE WHEN s.expires_at > NOW() AND s.status IN ('trial','active') THEN 0 ELSE 1 END,
          s.expires_at DESC
        LIMIT 1`,
      [user_id]
    );
    const now = new Date();
    let subActive = false;
    let secondsRemaining = 0;
    if (sub) {
      const expires = new Date(sub.expires_at);
      subActive = expires > now && (sub.status === 'trial' || sub.status === 'active');
      secondsRemaining = Math.max(0, Math.floor((expires - now) / 1000));
      if (!subActive && sub.status !== 'expired' && sub.status !== 'cancelled') {
        await query(`UPDATE subscriptions SET status = 'expired' WHERE id = $1`, [sub.id]);
      }
    }

    res.json({
      exists: true,
      user_id: u.id,
      number_matches: numberMatches,
      status: u.status,
      pin_set: !!u.pin_set_at,
      subscription: !__subRequired ? {
        active: true,
        is_trial: false,
        status: 'unrestricted',
        seconds_remaining: 31536000,
        plan_name: 'Unrestricted (dev mode)'
      } : (sub ? {
        active: subActive,
        is_trial: sub.is_trial,
        status: subActive ? sub.status : 'expired',
        expires_at: sub.expires_at,
        seconds_remaining: secondsRemaining,
        plan_name: sub.plan_name
      } : null)
    });
  } catch (e) { next(e); }
});


// ============================================================
// SCHEDULES — full mirror sync (client is source of truth)
// ============================================================
router.post('/schedules/sync', async (req, res, next) => {
  try {
    const { user_id, schedules } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });
    if (!Array.isArray(schedules)) return res.status(400).json({ error: 'schedules array required' });

    await query('DELETE FROM schedules WHERE user_id = $1', [user_id]);

    for (const s of schedules) {
      if (!s.client_id || !s.name) continue;
      await query(
        `INSERT INTO schedules(user_id, client_id, name, start_minute, end_minute,
            days_mask, is_enabled, allow_numbers, allow_names, quick_until_ms,
            last_toggled_at, freq_bypass_enabled, freq_count, freq_window_min)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8::jsonb, $9::jsonb, $10,
                 COALESCE(to_timestamp($11 / 1000.0), NOW()), $12, $13, $14)`,
        [
          user_id, s.client_id, s.name,
          Math.max(0, Math.min(1439, parseInt(s.start_minute, 10) || 0)),
          Math.max(0, Math.min(1439, parseInt(s.end_minute, 10) || 0)),
          parseInt(s.days_mask, 10) || 127,
          s.is_enabled !== false,
          JSON.stringify(s.allow_numbers || []),
          JSON.stringify(s.allow_names || []),
          s.quick_until_ms || null,
          s.last_toggled_at || null,
          s.freq_bypass_enabled === true,
          Math.max(1, Math.min(99, parseInt(s.freq_count, 10) || 5)),
          Math.max(1, Math.min(1440, parseInt(s.freq_window_min, 10) || 10))
        ]
      );
    }

    await audit('android', 'schedules_sync', `user_id=${user_id}, count=${schedules.length}`);
    res.json({ ok: true, count: schedules.length });
  } catch (e) { next(e); }
});

// GET /api/schedules/list?user_id=N
router.get('/schedules/list', async (req, res, next) => {
  try {
    const userId = parseInt(req.query.user_id, 10);
    if (!userId) return res.status(400).json({ error: 'user_id required' });
    const rows = await many(
      `SELECT client_id, name, start_minute, end_minute, days_mask,
              is_enabled, allow_numbers, allow_names, quick_until_ms,
              EXTRACT(EPOCH FROM last_toggled_at) * 1000 AS last_toggled_ms,
              freq_bypass_enabled, freq_count, freq_window_min
         FROM schedules
        WHERE user_id = $1
        ORDER BY id`,
      [userId]
    );
    res.json({ ok: true, schedules: rows });
  } catch (e) { next(e); }
});


// ============================================================
// BLOCK ALL NOW — panic-mode state (one row per user)
// ============================================================
router.post('/block-all/set', async (req, res, next) => {
  try {
    const { user_id, mode, expires_at_ms, allow_numbers, allow_names } = req.body || {};
    if (!user_id) return res.status(400).json({ error: 'user_id required' });
    if (mode && !['everything','except_contacts','except_custom'].includes(mode)) {
      return res.status(400).json({ error: 'invalid mode' });
    }
    await query(
      `INSERT INTO block_all_state(user_id, mode, expires_at_ms, allow_numbers, allow_names, updated_at)
       VALUES ($1, $2, $3, $4::jsonb, $5::jsonb, NOW())
       ON CONFLICT (user_id) DO UPDATE SET
         mode = EXCLUDED.mode,
         expires_at_ms = EXCLUDED.expires_at_ms,
         allow_numbers = EXCLUDED.allow_numbers,
         allow_names = EXCLUDED.allow_names,
         updated_at = NOW()`,
      [user_id, mode || null, expires_at_ms || null,
       JSON.stringify(allow_numbers || []), JSON.stringify(allow_names || [])]
    );
    await audit('android', 'block_all_set', `user_id=${user_id} mode=${mode}`);
    res.json({ ok: true });
  } catch (e) { next(e); }
});

router.get('/block-all/get', async (req, res, next) => {
  try {
    const userId = parseInt(req.query.user_id, 10);
    if (!userId) return res.status(400).json({ error: 'user_id required' });
    const row = await one(
      `SELECT mode, expires_at_ms, allow_numbers, allow_names
         FROM block_all_state WHERE user_id = $1`, [userId]);
    res.json({ ok: true, state: row || null });
  } catch (e) { next(e); }
});


// ============================================================
// RAZORPAY BILLING — for sideload distribution
// (Play Store distribution uses Google Play Billing instead.)
// ============================================================
const crypto = require('crypto');

async function razorpayCreds() {
  const mode = (await getSetting('razorpay_mode')) || 'test';
  const isLive = mode === 'live';
  const keyId  = (await getSetting(isLive ? 'razorpay_key_id_live'  : 'razorpay_key_id_test'))  || '';
  const secret = (await getSetting(isLive ? 'razorpay_secret_live'  : 'razorpay_secret_test'))  || '';
  return { mode, isLive, keyId, secret };
}

// POST /api/razorpay/create-order
// Body: { user_id, plan_id }
// Returns: { order_id, key_id, amount_paise, currency, plan }
router.post('/razorpay/create-order', async (req, res, next) => {
  try {
    const { user_id, plan_id } = req.body || {};
    if (!user_id || !plan_id) return res.status(400).json({ error: 'user_id and plan_id required' });

    const enabled = (await getSetting('razorpay_enabled')) === 'true';
    if (!enabled) return res.status(503).json({ error: 'razorpay_disabled' });

    const { keyId, secret } = await razorpayCreds();
    if (!keyId || !secret) return res.status(503).json({ error: 'razorpay_not_configured' });

    const plan = await one(`SELECT * FROM plans WHERE id = $1 AND is_active = TRUE`, [plan_id]);
    if (!plan) return res.status(404).json({ error: 'plan_not_found' });

    // plan.offer_price and plan.actual_price are stored in paise (smallest unit)
    // already, so we use them directly. Multiplying by 100 again gave us ₹2900
    // for a ₹29 plan in earlier versions.
    const amountPaise = parseInt(plan.offer_price || plan.actual_price, 10);
    const currency = plan.currency || 'INR';
    const receipt = `cf_u${user_id}_p${plan_id}_${Date.now()}`.slice(0, 40);

    // Create order via Razorpay REST API
    const auth = Buffer.from(`${keyId}:${secret}`).toString('base64');
    const orderResp = await fetch('https://api.razorpay.com/v1/orders', {
      method: 'POST',
      headers: {
        'Authorization': `Basic ${auth}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        amount: amountPaise,
        currency,
        receipt,
        notes: { user_id: String(user_id), plan_id: String(plan_id), plan_name: plan.name }
      })
    });
    if (!orderResp.ok) {
      const errText = await orderResp.text();
      await audit('razorpay', 'create_order_failed', `user=${user_id} ${errText}`);
      return res.status(502).json({ error: 'razorpay_api_error', detail: errText });
    }
    const order = await orderResp.json();

    await query(
      `INSERT INTO razorpay_orders(user_id, plan_id, order_id, amount_paise, currency, status, notes)
       VALUES ($1, $2, $3, $4, $5, 'created', $6::jsonb)`,
      [user_id, plan_id, order.id, amountPaise, currency, JSON.stringify(order.notes || {})]
    );
    await audit('razorpay', 'order_created', `user=${user_id} order=${order.id} amount=${amountPaise}`);

    res.json({
      ok: true,
      order_id: order.id,
      key_id: keyId,
      amount_paise: amountPaise,
      currency,
      plan: {
        id: plan.id,
        name: plan.name,
        duration_days: plan.duration_days,
        offer_price: plan.offer_price,
        actual_price: plan.actual_price
      }
    });
  } catch (e) { next(e); }
});

// POST /api/razorpay/verify-payment
// Body: { user_id, order_id, payment_id, signature? }
// If signature is supplied, verifies HMAC-SHA256. Otherwise queries Razorpay's
// /payments/{id} REST API as a fallback verification path (used by the simpler
// PaymentResultListener flow that doesn't expose the signature client-side).
router.post('/razorpay/verify-payment', async (req, res, next) => {
  try {
    const { user_id, order_id, payment_id, signature } = req.body || {};
    if (!user_id || !order_id || !payment_id) {
      return res.status(400).json({ error: 'missing_fields' });
    }

    const { keyId, secret } = await razorpayCreds();
    if (!keyId || !secret) return res.status(503).json({ error: 'razorpay_not_configured' });

    // Path A — signature provided: HMAC verify
    if (signature) {
      const expectedSig = crypto.createHmac('sha256', secret)
        .update(`${order_id}|${payment_id}`)
        .digest('hex');
      if (expectedSig !== signature) {
        await audit('razorpay', 'signature_mismatch', `user=${user_id} order=${order_id}`);
        await query(
          `UPDATE razorpay_orders SET status='failed' WHERE order_id=$1 AND user_id=$2`,
          [order_id, user_id]
        );
        return res.status(400).json({ error: 'signature_mismatch' });
      }
    } else {
      // Path B — no signature: query Razorpay to verify the payment is captured
      // AND belongs to our order_id. This is safe because we trust Razorpay's
      // server response (Basic auth with our secret).
      const auth = Buffer.from(`${keyId}:${secret}`).toString('base64');
      const pr = await fetch(`https://api.razorpay.com/v1/payments/${payment_id}`, {
        headers: { 'Authorization': `Basic ${auth}` }
      });
      if (!pr.ok) {
        await audit('razorpay', 'verify_api_failed', `payment=${payment_id}`);
        return res.status(502).json({ error: 'razorpay_api_error' });
      }
      const payment = await pr.json();
      if (payment.order_id !== order_id) {
        return res.status(400).json({ error: 'order_mismatch' });
      }
      if (payment.status !== 'captured' && payment.status !== 'authorized') {
        return res.status(400).json({ error: 'payment_not_captured', status: payment.status });
      }
    }

    const orderRow = await one(
      `SELECT o.*, p.duration_days, p.name AS plan_name
         FROM razorpay_orders o
         LEFT JOIN plans p ON p.id = o.plan_id
        WHERE o.order_id = $1 AND o.user_id = $2`,
      [order_id, user_id]
    );
    if (!orderRow) return res.status(404).json({ error: 'order_not_found' });
    if (orderRow.status === 'paid') {
      return res.json({ ok: true, already_paid: true });
    }

    await query(
      `UPDATE razorpay_orders
          SET status='paid', razorpay_payment_id=$1, razorpay_signature=$2, paid_at=NOW()
        WHERE order_id=$3`,
      [payment_id, signature, order_id]
    );

    const days = parseInt(orderRow.duration_days, 10) || 30;

    // EXTEND from the user's current expiry if they're still subscribed,
    // otherwise start from now. (Buying a plan while subscribed adds days.)
    const cur = await one(
      `SELECT MAX(expires_at) AS expires_at FROM subscriptions
         WHERE user_id = $1 AND status IN ('trial','active') AND expires_at > NOW()`,
      [user_id]
    );
    const baseExpires = (cur && cur.expires_at) ? new Date(cur.expires_at) : new Date();
    if (baseExpires < new Date()) baseExpires.setTime(Date.now());

    await query(
      `INSERT INTO subscriptions(user_id, plan_id, status, is_trial, expires_at,
                                amount_paid, provider, razorpay_order_id,
                                razorpay_payment_id, razorpay_signature)
       VALUES ($1, $2, 'active', FALSE, $3::timestamptz + ($4 || ' days')::interval,
               $5, 'razorpay', $6, $7, $8)`,
      [user_id, orderRow.plan_id, baseExpires.toISOString(), String(days),
       (orderRow.amount_paise / 100).toFixed(2), order_id, payment_id, signature]
    );

    // Expire any older active rows so /api/subscription returns the latest
    await query(
      `UPDATE subscriptions
            SET status = 'cancelled'
          WHERE user_id = $1
            AND status IN ('trial','active')
            AND razorpay_order_id IS DISTINCT FROM $2`,
      [user_id, order_id]
    );

    await audit('razorpay', 'payment_verified',
      `user=${user_id} order=${order_id} payment=${payment_id} days=${days}`);
    res.json({ ok: true, days, plan_name: orderRow.plan_name });
  } catch (e) { next(e); }
});

// POST /api/razorpay/webhook — server-to-server verification (recommended)
// Razorpay calls this URL when payment events happen. We verify the X-Razorpay-Signature
// header and update order status. The /verify-payment endpoint above is the client-driven
// path; this webhook is a backup that handles delayed/failed verifications.
router.post('/razorpay/webhook', async (req, res, next) => {
  try {
    const whSecret = (await getSetting('razorpay_webhook_secret')) || '';
    if (!whSecret) return res.status(503).json({ error: 'webhook_secret_not_set' });

    const sig = req.headers['x-razorpay-signature'];
    if (!sig) return res.status(400).json({ error: 'missing_signature' });

    const body = JSON.stringify(req.body);
    const expected = crypto.createHmac('sha256', whSecret).update(body).digest('hex');
    if (expected !== sig) {
      await audit('razorpay', 'webhook_sig_mismatch', `sig=${sig}`);
      return res.status(400).json({ error: 'signature_mismatch' });
    }

    const event = req.body.event;
    const payment = req.body.payload && req.body.payload.payment && req.body.payload.payment.entity;
    if (payment) {
      await query(
        `UPDATE razorpay_orders
            SET status = CASE
              WHEN $1 = 'payment.captured' THEN 'paid'
              WHEN $1 = 'payment.failed'   THEN 'failed'
              ELSE status END,
            razorpay_payment_id = COALESCE(razorpay_payment_id, $2),
            paid_at = CASE WHEN $1 = 'payment.captured' THEN NOW() ELSE paid_at END
          WHERE order_id = $3`,
        [event, payment.id, payment.order_id]
      );
      await audit('razorpay', 'webhook_received', `event=${event} order=${payment.order_id}`);
    }
    res.json({ ok: true });
  } catch (e) { next(e); }
});

// GET /api/razorpay/status — public, tells the client whether Razorpay is available
router.get('/razorpay/status', async (req, res, next) => {
  try {
    const enabled = (await getSetting('razorpay_enabled')) === 'true';
    const { keyId, mode } = await razorpayCreds();
    res.json({
      ok: true,
      enabled,
      configured: !!keyId,
      mode,
      key_id: enabled && keyId ? keyId : ''
    });
  } catch (e) { next(e); }
});

module.exports = router;
