/**
 * auth_admin.js — Role-based admin authentication middleware
 *
 * Roles and their permissions:
 *   super_admin     — everything, hard delete, manage all admin users
 *   admin           — everything except admin user management
 *   support         — view users/contacts/rules/blocked, reset PIN, view global blocklist
 *   billing         — plans, subscriptions, payments, billing settings
 *   global_db_admin — CRUD global blocklist (own scope + sub-users), manage own sub-users
 *   global_db_user  — CRUD own global blocklist entries only
 */

const jwt     = require('jsonwebtoken');
const bcrypt  = require('bcryptjs');
const { one, query } = require('./db.js');

const SECRET = process.env.JWT_SECRET || 'dev-secret-change-me';

// ── Permission sets ───────────────────────────────────────────────────
const PERMS = {
  super_admin:    ['*'],
  admin:          ['dashboard','users','contacts','rules','billing','blocked',
                   'block_reasons','global_blocklist_all','settings','audit','payments'],
  support:        ['dashboard','users_view','contacts_view','rules_view',
                   'blocked_view','global_blocklist_view','reset_pin'],
  billing:        ['dashboard','billing','payments','settings_billing'],
  global_db_admin:['global_blocklist_scope','admin_users_children'],
  global_db_user: ['global_blocklist_own'],
};

function hasPerm(role, perm) {
  const perms = PERMS[role] || [];
  return perms.includes('*') || perms.includes(perm);
}

// ── Create JWT ────────────────────────────────────────────────────────
function signToken(adminUser) {
  return jwt.sign(
    { admin_id: adminUser.id, username: adminUser.username,
      display_name: adminUser.display_name || adminUser.username,
      role: adminUser.role },
    SECRET, { expiresIn: '12h' }
  );
}

// ── requireAdmin middleware ────────────────────────────────────────────
async function requireAdmin(req, res, next) {
  const auth = req.headers.authorization || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'Unauthorized' });

  try {
    const payload = jwt.verify(token, SECRET);

    // If new multi-user token
    if (payload.admin_id) {
      const row = await one(
        `SELECT id, username, display_name, role, active, deleted_at
           FROM admin_users WHERE id = $1`, [payload.admin_id]);
      if (!row || !row.active || row.deleted_at)
        return res.status(401).json({ error: 'Account inactive' });

      req.admin = { id: row.id, username: row.username,
                    displayName: row.display_name, role: row.role };
      return next();
    }

    // Legacy single-admin token (backward compat)
    req.admin = { id: null, username: payload.username,
                  displayName: 'Admin', role: 'super_admin' };
    next();
  } catch (e) {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }
}

// ── Role guard factory ─────────────────────────────────────────────────
function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.admin) return res.status(401).json({ error: 'Unauthorized' });
    if (req.admin.role === 'super_admin') return next(); // super always passes
    if (roles.includes(req.admin.role)) return next();
    return res.status(403).json({ error: 'Forbidden', required: roles });
  };
}

// ── Global blocklist scope helper ─────────────────────────────────────
// Returns a WHERE clause fragment + params to scope global_blocklist
// queries to what this admin can see/edit.
async function globalScopeWhere(admin, paramOffset = 0) {
  const role = admin.role;
  if (role === 'super_admin' || role === 'admin' || role === 'support') {
    // All active entries (super_admin also sees soft-deleted via separate flag)
    return { where: '', params: [], showDeleted: role === 'super_admin' };
  }
  if (role === 'global_db_admin') {
    // Own entries + sub-users' entries
    const subs = await query(
      'SELECT id FROM admin_users WHERE parent_id = $1 AND deleted_at IS NULL', [admin.id]);
    const subIds = subs.rows.map(r => r.id);
    const allIds = [admin.id, ...subIds];
    const placeholders = allIds.map((_, i) => `$${paramOffset + i + 1}`).join(',');
    return { where: `AND g.added_by_admin_id IN (${placeholders})`,
             params: allIds, showDeleted: false };
  }
  if (role === 'global_db_user') {
    return { where: `AND g.added_by_admin_id = $${paramOffset + 1}`,
             params: [admin.id], showDeleted: false };
  }
  return null; // no access
}

// ── Login handler ─────────────────────────────────────────────────────
async function loginHandler(req, res) {
  const { username, password } = req.body || {};
  if (!username || !password)
    return res.status(400).json({ error: 'username and password required' });

  try {
    // Try admin_users table first
    const row = await one(
      `SELECT * FROM admin_users
        WHERE username = $1 AND active = TRUE AND deleted_at IS NULL`, [username]);

    if (row) {
      const ok = await bcrypt.compare(password, row.password_hash);
      if (!ok) return res.status(401).json({ error: 'Invalid credentials' });

      await query('UPDATE admin_users SET last_login_at = NOW() WHERE id = $1', [row.id]);
      const token = signToken(row);
      return res.json({ ok: true, token,
        username: row.username, display_name: row.display_name || row.username,
        role: row.role });
    }

    // Legacy env-var fallback
    const envUser = process.env.ADMIN_USERNAME || 'admin';
    const envPass = process.env.ADMIN_PASSWORD || 'changeme';
    if (username === envUser && password === envPass) {
      const token = jwt.sign(
        { username, displayName: 'Admin', role: 'super_admin' },
        SECRET, { expiresIn: '12h' });
      return res.json({ ok: true, token, username, role: 'super_admin' });
    }

    return res.status(401).json({ error: 'Invalid credentials' });
  } catch (e) {
    console.error('Login error:', e);
    return res.status(500).json({ error: 'Server error' });
  }
}

module.exports = { requireAdmin, requireRole, signToken,
                   hasPerm, globalScopeWhere, loginHandler, PERMS };
