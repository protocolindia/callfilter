import React, { useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { api, getAdminRole, getAdminMeta, setPermissions, getPermissions } from '../api.js';

const ROLE_COLORS = {
  super_admin:'#a855f7', admin:'#4f8ef7', support:'#22c55e',
  billing:'#f59e0b', global_db_admin:'#ef4444', global_db_user:'#fb923c'
};
const ROLE_LABELS = {
  super_admin:'Super Admin', admin:'Admin', support:'Support',
  billing:'Billing', global_db_admin:'Global DB Admin', global_db_user:'Global DB User'
};

export default function Layout({ children }) {
  const navigate = useNavigate();
  const { username, logout } = useAuth();
  const role = getAdminRole();
  const meta = getAdminMeta();
  const [perms, setPerms] = useState(getPermissions());

  // Fetch the current admin's effective permissions (kept fresh on each mount).
  useEffect(() => {
    let alive = true;
    api.get('/admin/me').then(r => {
      const p = r?.admin?.permissions || [];
      if (alive) { setPermissions(p); setPerms(p); }
    }).catch(() => {});
    return () => { alive = false; };
  }, []);

  // Permission-based gate: super_admin sees everything; otherwise check nav.* perm.
  const can = (perm) => role === 'super_admin' || perms.includes('*') || perms.includes(perm);

  const handleLogout = () => { logout(); navigate('/login'); };
  const linkClass = ({ isActive }) => isActive ? 'active' : '';

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <div className="logo">🛡️</div>
          <div>
            <div className="brand-name">CyberGuard AI</div>
            <div className="brand-sub">Admin</div>
          </div>
        </div>
        <nav>
          {can('nav.dashboard') && <NavLink to="/dashboard" className={linkClass}>📊 Dashboard</NavLink>}
          {can('nav.users') && <NavLink to="/users" className={linkClass}>👥 Users</NavLink>}
          {can('nav.billing') && <NavLink to="/billing" className={linkClass}>💳 Billing</NavLink>}
          {can('nav.payments') && <NavLink to="/payments" className={linkClass}>💰 Payments</NavLink>}
          {can('nav.block_reasons') && <NavLink to="/block-reasons" className={linkClass}>📋 Block Reasons</NavLink>}
          {can('nav.global_blocklist') && <NavLink to="/global-blocklist" className={linkClass}>🌐 Global Blocklist</NavLink>}
          {can('nav.sms_protection') && <NavLink to="/sms-protection" className={linkClass}>🛡️ SMS Protection</NavLink>}
          {can('nav.settings') && <NavLink to="/settings" className={linkClass}>⚙️ Settings</NavLink>}
          {can('nav.audit') && <NavLink to="/audit" className={linkClass}>📋 Audit Log</NavLink>}
          {can('nav.admin_users') && <NavLink to="/admin-users" className={linkClass}>🔐 Admin Users</NavLink>}
          {can('nav.roles') && <NavLink to="/roles" className={linkClass}>🎭 Roles</NavLink>}
        </nav>
        <div className="bottom">
          <div style={{ marginBottom:6 }}>
            <span style={{ background:(ROLE_COLORS[role]||'#6b7280')+'33',
              color:ROLE_COLORS[role]||'#6b7280',
              borderRadius:4, padding:'2px 6px', fontSize:10, fontWeight:700,
              textTransform:'uppercase' }}>
              {ROLE_LABELS[role]||role}
            </span>
          </div>
          <div className="who">{meta?.display_name || username || 'Admin'}</div>
          <button className="logout" onClick={handleLogout}>Sign out</button>
        </div>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
