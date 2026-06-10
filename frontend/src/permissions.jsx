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
        const p = (r && r.admin && r.admin.permissions) || [];
        setPermissions(p);
        setPerms(p);
        setDiag({ status: 'ok', count: p.length });
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
  const { firstAllowed } = usePerms();
  return (
    <div style={{ padding: 40, textAlign: 'center', color: 'var(--subtext)', maxWidth: 560, margin: '0 auto' }}>
      <h2 style={{ color: 'var(--text)' }}>No access</h2>
      <p>Your role doesn't have permission to view any sections. Please contact an administrator.</p>
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
