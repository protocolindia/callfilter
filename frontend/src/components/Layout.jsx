import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

export default function Layout({ children }) {
  const navigate = useNavigate();
  const { username, logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const linkClass = ({ isActive }) => isActive ? 'active' : '';

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <div className="logo">🛡️</div>
          <div>
            <div className="brand-name">CallFilter</div>
            <div className="brand-sub">Admin</div>
          </div>
        </div>
        <nav>
          <NavLink to="/dashboard" className={linkClass}>📊 Dashboard</NavLink>
          <NavLink to="/users" className={linkClass}>👥 Users</NavLink>
          <NavLink to="/billing" className={linkClass}>💳 Billing</NavLink>
          <NavLink to="/payments" className={linkClass}>💰 Payments</NavLink>
          <NavLink to="/block-reasons" className={linkClass}>📋 Block Reasons</NavLink>
          <NavLink to="/global-blocklist" className={linkClass}>🌐 Global Blocklist</NavLink>
          <NavLink to="/settings" className={linkClass}>⚙️ Settings</NavLink>
          <NavLink to="/audit" className={linkClass}>📋 Audit Log</NavLink>
        </nav>
        <div className="bottom">
          <div className="who">{username || 'Admin'}</div>
          <button className="logout" onClick={handleLogout}>Sign out</button>
        </div>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
