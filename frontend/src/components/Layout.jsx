import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { getAdminRole, getAdminMeta } from '../api.js';

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
  const isGlobalOnly = role === 'global_db_admin' || role === 'global_db_user';
  const can = (...roles) => role === 'super_admin' || roles.includes(role);

  const handleLogout = () => { logout(); navigate('/login'); };
  const linkClass = ({ isActive }) => isActive ? 'active' : '';

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <div className="logo">🛡️</div>
          <div>
            <div className="brand-name">AI CallFilter</div>
            <div className="brand-sub">Admin</div>
          </div>
        </div>
        <nav>
          {!isGlobalOnly && <NavLink to="/dashboard" className={linkClass}>📊 Dashboard</NavLink>}
          {can('admin','support') && <NavLink to="/users" className={linkClass}>👥 Users</NavLink>}
          {can('admin','billing') && <NavLink to="/billing" className={linkClass}>💳 Billing</NavLink>}
          {can('admin','billing') && <NavLink to="/payments" className={linkClass}>💰 Payments</NavLink>}
          {can('admin','support') && <NavLink to="/block-reasons" className={linkClass}>📋 Block Reasons</NavLink>}
          {(can('admin','support') || isGlobalOnly) && <NavLink to="/global-blocklist" className={linkClass}>🌐 Global Blocklist</NavLink>}
          {can('admin','billing') && <NavLink to="/settings" className={linkClass}>⚙️ Settings</NavLink>}
          {can('admin') && <NavLink to="/audit" className={linkClass}>📋 Audit Log</NavLink>}
          {(can('admin') || role === 'global_db_admin') && <NavLink to="/admin-users" className={linkClass}>🔐 Admin Users</NavLink>}
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
