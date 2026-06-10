import React, { createContext, useContext, useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { api, getAdminRole, setPermissions, getPermissions } from './api.js';

// Ordered list of nav destinations and the permission each requires.
// Order matters: the first one a user is allowed to see becomes their landing page.
export const NAV_ROUTES = [
  { path: '/dashboard',        perm: 'nav.dashboard' },
  { path: '/users',            perm: 'nav.users' },
  { path: '/global-blocklist', perm: 'nav.global_blocklist' },
  { path: '/sms-protection',   perm: 'nav.sms_protection' },
  { path: '/billing',          perm: 'nav.billing' },
  { path: '/payments',         perm: 'nav.payments' },
  { path: '/block-reasons',    perm: 'nav.block_reasons' },
  { path: '/settings',         perm: 'nav.settings' },
  { path: '/audit',            perm: 'nav.audit' },
  { path: '/admin-users',      perm: 'nav.admin_users' },
  { path: '/roles',            perm: 'nav.roles' },
];

const PermCtx = createContext(null);

export function PermissionsProvider({ children }) {
  const role = getAdminRole();
  const [perms, setPerms] = useState(getPermissions());
  const [ready, setReady] = useState(false);
  const [diag, setDiag] = useState({ status: 'loading', hasField: false, count: 0 });

  const fetchMe = React.useCallback(() => {
    setReady(false);
    setDiag(d => ({ ...d, status: 'loading' }));
    api.get('/admin/me')
      .then(r => {
        const hasField = !!(r && r.admin && Array.isArray(r.admin.permissions));
        const p = (r && r.admin && r.admin.permissions) || [];
        setPermissions(p);
        setPerms(p);
        setDiag({ status: 'ok', hasField, count: p.length,
                  build: r && r.build, perm_source: r && r.perm_source,
                  role_keys: (r && r.role_keys) || [],
                  migration_errors: (r && r.migration_errors) || [],
                  db_probe: (r && r.db_probe) || {} });
      })
      .catch(err => {
        setDiag({ status: 'error', hasField: false, count: 0, error: String(err && err.message || err) });
      })
      .finally(() => setReady(true));
  }, []);

  useEffect(() => { fetchMe(); }, [fetchMe]);

  const can = (perm) =>
    role === 'super_admin' || perms.includes('*') || perms.includes(perm);

  // First nav path this user may see (used for landing + redirects).
  const firstAllowed = () => {
    const hit = NAV_ROUTES.find(r => can(r.perm));
    return hit ? hit.path : null;
  };

  return (
    <PermCtx.Provider value={{ perms, ready, can, firstAllowed, role, diag, refresh: fetchMe }}>
      {children}
    </PermCtx.Provider>
  );
}

export function usePerms() {
  return useContext(PermCtx) || {
    perms: [], ready: true,
    can: () => false, firstAllowed: () => null, role: getAdminRole(),
  };
}

// Route guard: renders children only if the user has `perm`; otherwise
// redirects to their first allowed page (or a friendly no-access notice).
export function RequirePerm({ perm, children }) {
  const { ready, can, firstAllowed } = usePerms();
  if (!ready) return <div style={{ padding: 40, color: 'var(--subtext)' }}>Loading…</div>;
  if (can(perm)) return children;
  const dest = firstAllowed();
  if (dest && window.location.pathname !== dest) {
    return <RedirectTo to={dest} />;
  }
  return <NoAccess />;
}

function NoAccess() {
  const { role, diag, refresh } = usePerms();
  let hint;
  if (diag.status === 'error') {
    hint = "Couldn't reach the server for permissions. Error: " + (diag.error || 'unknown');
  } else if (!diag.build) {
    hint = "The backend is running an OLD version (no build tag in its response). Redeploy the BACKEND service to the latest version.";
  } else if (diag.perm_source === 'role_not_found') {
    hint = "Your role key '" + role + "' was not found in the roles table on the server. "
      + "The role the user is assigned does not match any role key. Available keys: "
      + ((diag.role_keys || []).join(', ') || '(none)');
  } else if (String(diag.perm_source || '').startsWith('error')) {
    hint = "The server errored reading roles: " + diag.perm_source;
  } else if (diag.count === 0) {
    hint = "Your role '" + role + "' resolved to 0 permissions on the server even though the database may show some. "
      + "This usually means the deployed backend differs from the database, or you're hitting a different backend.";
  } else {
    hint = "Your role has permissions but none match an available section.";
  }
  return (
    <div style={{ padding: 40, textAlign: 'center', color: 'var(--subtext)', maxWidth: 680, margin: '0 auto' }}>
      <h2 style={{ color: 'var(--text)' }}>No access</h2>
      <p>{hint}</p>
      <p className="muted" style={{ fontSize: 12 }}>
        role: <b>{role}</b> &nbsp;·&nbsp; backend build: <b>{diag.build || '(none / old)'}</b> &nbsp;·&nbsp;
        perm source: <b>{diag.perm_source || '?'}</b> &nbsp;·&nbsp; count: <b>{diag.count}</b>
      </p>
      {diag.db_probe && (
        <div style={{ marginTop: 10, padding: 12, borderRadius: 8, textAlign: 'left',
            background: 'rgba(79,142,247,0.08)', border: '1px solid var(--border)', fontSize: 12 }}>
          <b>Backend database probe:</b>
          <div>connected db: <b>{diag.db_probe.db || diag.db_probe.db_error || '?'}</b></div>
          <div>schema: <b>{diag.db_probe.schema || '?'}</b> &nbsp; host: <b>{diag.db_probe.host || '?'}</b></div>
          <div>roles rows the backend sees: <b>{diag.db_probe.roles_count != null ? diag.db_probe.roles_count : (diag.db_probe.roles_count_error || '?')}</b></div>
          {diag.db_probe.roles_query_error && <div style={{ color:'var(--red)' }}>roles query error: {diag.db_probe.roles_query_error}</div>}
          <div style={{ marginTop: 4 }}>role keys: {(diag.role_keys || []).join(', ') || '(none)'}</div>
        </div>
      )}
      {Array.isArray(diag.migration_errors) && diag.migration_errors.length > 0 && (
        <div style={{ marginTop: 10, padding: 12, borderRadius: 8, textAlign: 'left',
            background: 'rgba(248,113,113,0.10)', border: '1px solid var(--border)', fontSize: 12 }}>
          <b style={{ color: 'var(--red)' }}>Failed migrations on the server:</b>
          <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
            {diag.migration_errors.map((m, i) => (
              <li key={i}><b>{m.file}</b>: {m.error}</li>
            ))}
          </ul>
        </div>
      )}
      <button onClick={refresh} style={{ marginTop: 12, padding: '8px 18px', borderRadius: 6,
        border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)', cursor: 'pointer' }}>
        Retry
      </button>
    </div>
  );
}

// Small redirect helper (avoids importing Navigate everywhere).
function RedirectTo({ to }) { return <Navigate to={to} replace />; }

// Landing redirect for "/" — sends user to their first allowed page.
export function LandingRedirect() {
  const { ready, firstAllowed } = usePerms();
  if (!ready) return <div style={{ padding: 40, color: 'var(--subtext)' }}>Loading…</div>;
  const dest = firstAllowed();
  return <Navigate to={dest || '/settings'} replace />;
}
