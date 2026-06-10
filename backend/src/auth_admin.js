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

// ── Permission catalog ────────────────────────────────────────────────
// The master list of permissions, grouped for the role-editor UI.
//   nav.*       → controls visibility of left-menu links
//   <area>.*    → action-level permissions enforced on endpoints
const PERMISSION_CATALOG = [
  { group: 'Navigation (left menu)', perms: [
    { key: 'nav.dashboard',         label: 'Dashboard' },
    { key: 'nav.users',             label: 'Users' },
    { key: 'nav.global_blocklist',  label: 'Global Blocklist' },
    { key: 'nav.sms_protection',    label: 'SMS Protection' },
    { key: 'nav.billing',           label: 'Billing' },
    { key: 'nav.payments',          label: 'Payments' },
    { key: 'nav.block_reasons',     label: 'Block Reasons' },
    { key: 'nav.admin_users',       label: 'Admin Users' },
    { key: 'nav.roles',             label: 'Roles' },
    { key: 'nav.settings',          label: 'Settings' },
    { key: 'nav.audit',             label: 'Audit Log' },
    { key: 'nav.fraud_reports',     label: 'Fraud Reports' },
  ]},
  { group: 'Users', perms: [
    { key: 'users.view',           label: 'View users' },
    { key: 'users.edit',           label: 'Edit users' },
    { key: 'users.delete',         label: 'Delete users' },
    { key: 'users.reset_pin',      label: 'Reset PIN' },
    { key: 'users.contacts_view',  label: 'View contacts' },
    { key: 'users.rules_view',     label: 'View rules' },
    { key: 'users.blocked_view',   label: 'View blocked calls' },
  ]},
  { group: 'Global blocklist', perms: [
    { key: 'global_blocklist.view',   label: 'View' },
    { key: 'global_blocklist.create', label: 'Add numbers' },
    { key: 'global_blocklist.edit',   label: 'Edit numbers' },
    { key: 'global_blocklist.delete', label: 'Delete numbers' },
    { key: 'global_blocklist.import', label: 'Bulk import' },
  ]},
  { group: 'Admin & roles', perms: [
    { key: 'admin_users.manage',   label: 'Manage admin users' },
    { key: 'admin_users.children', label: 'Manage own sub-users' },
    { key: 'roles.manage',         label: 'Manage roles' },
  ]},
  { group: 'Settings tabs', perms: [
    { key: 'settings.sms',          label: 'SMS settings' },
    { key: 'settings.otp',          label: 'OTP Rules' },
    { key: 'settings.subscription', label: 'Subscription' },
    { key: 'settings.razorpay',     label: 'Razorpay' },
    { key: 'settings.contacts',     label: 'Contacts Sync' },
    { key: 'settings.fraud',        label: 'Fraud Reports' },
    { key: 'settings.password',     label: 'Password' },
  ]},
  { group: 'Other', perms: [
    { key: 'sms_protection.manage', label: 'Manage SMS protection' },
    { key: 'settings.edit',         label: 'Edit settings' },
    { key: 'fraud_reports.view',    label: 'View fraud reports' },
    { key: 'billing.view',          label: 'View billing' },
    { key: 'payments.view',         label: 'View payments' },
    { key: 'block_reasons.edit',    label: 'Edit block reasons' },
  ]},
];

// Legacy fallback (used only if the roles table is unavailable / role missing).
const LEGACY_PERMS = {
  super_admin:    ['*'],
  admin:          ['dashboard','users','contacts','rules','billing','blocked',
                   'block_reasons','global_blocklist_all','settings','audit','payments'],
  support:        ['dashboard','users_view','contacts_view','rules_view',
                   'blocked_view','global_blocklist_view','reset_pin'],
  billing:        ['dashboard','billing','payments','settings_billing'],
  global_db_admin:['global_blocklist_scope','admin_users_children'],
  global_db_user: ['global_blocklist_own'],
};

// ── DB-backed role cache ──────────────────────────────────────────────
let _roleCache = null;      // { roleKey: [permissions] }
let _roleCacheAt = 0;
const ROLE_CACHE_TTL = 30 * 1000; // 30s

async function loadRoles(force = false) {
  const now = Date.now();
  if (!force && _roleCache && (now - _roleCacheAt) < ROLE_CACHE_TTL) return _roleCache;
  try {
    const rows = await many('SELECT key, permissions FROM roles');
    const map = {};
    for (const r of rows) {
      let perms = [];
      try { perms = Array.isArray(r.permissions) ? r.permissions : JSON.parse(r.permissions || '[]'); }
      catch { perms = []; }
      map[r.key] = perms;
    }
    _roleCache = map;
    _roleCacheAt = now;
    return map;
  } catch (e) {
    // Table not migrated yet — fall back to legacy.
    return LEGACY_PERMS;
  }
}

function invalidateRoleCache() { _roleCache = null; _roleCacheAt = 0; }

// Async permission check against DB roles (super_admin always allowed).
async function hasPermAsync(role, perm) {
  if (role === 'super_admin') return true;
  const roles = await loadRoles();
  const perms = roles[role] || LEGACY_PERMS[role] || [];
  return perms.includes('*') || perms.includes(perm);
}

// Synchronous legacy check kept for existing call-sites.
function hasPerm(role, perm) {
  if (role === 'super_admin') return true;
  const src = (_roleCache && _roleCache[role]) || LEGACY_PERMS[role] || [];
  return src.includes('*') || src.includes(perm);
}

// Middleware: require an action-level permission on an endpoint.
function requirePerm(perm) {
  return async (req, res, next) => {
    if (!req.admin) return res.status(401).json({ error: 'Unauthorized' });
    if (req.admin.role === 'super_admin') return next();
    try {
      if (await hasPermAsync(req.admin.role, perm)) return next();
    } catch (_) {}
    return res.status(403).json({ error: 'Forbidden', required: perm });
  };
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

module.exports = { requireAdmin, requireRole, requirePerm, signToken,
                   hasPerm, hasPermAsync, loadRoles, invalidateRoleCache,
                   PERMISSION_CATALOG, globalScopeWhere, loginHandler,
                   PERMS: LEGACY_PERMS };
