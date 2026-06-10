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
        setDiag({ status: 'ok', hasField, count: p.length });
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
    hint = "Couldn't reach the server for permissions. The backend may be unreachable or running an older version. Error: " + (diag.error || 'unknown');
  } else if (!diag.hasField) {
    hint = "The server response did not include a permissions list. This means the BACKEND needs to be redeployed to the latest version.";
  } else if (diag.count === 0) {
    hint = "Your role '" + role + "' has no permissions assigned. Open Roles in the admin panel (as super admin), edit this role, tick the sections to allow, and save.";
  } else {
    hint = "Your role has permissions but none match an available section.";
  }
  return (
    <div style={{ padding: 40, textAlign: 'center', color: 'var(--subtext)', maxWidth: 620, margin: '0 auto' }}>
      <h2 style={{ color: 'var(--text)' }}>No access</h2>
      <p>{hint}</p>
      <p className="muted" style={{ fontSize: 12 }}>
        Role: <b>{role}</b> &nbsp;·&nbsp; permissions field present: <b>{String(diag.hasField)}</b> &nbsp;·&nbsp; count: <b>{diag.count}</b>
      </p>
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
