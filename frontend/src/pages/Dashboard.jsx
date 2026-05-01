import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [users, setUsers] = useState([]);
  const [log, setLog] = useState([]);
  const [settings, setSettings] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      api.get('/admin/stats'),
      api.get('/admin/settings').catch(() => ({ settings: {} })),
    ]).then(([s, st]) => {
      setStats(s.stats);
      setUsers(s.recent_users || []);
      setLog(s.recent_log || []);
      setSettings(st.settings || {});
      setLoading(false);
    }).catch(err => {
      setError(err.message);
      setLoading(false);
    });
  }, []);

  if (loading) return <div className="loading">Loading…</div>;
  if (error)   return <div className="alert alert-error">{error}</div>;

  return (
    <>
      <header className="page-head">
        <h1>Dashboard</h1>
        <p className="muted">Overview of CallFilter users and OTP activity</p>
      </header>

      <div className="stat-grid">
        <Stat n={stats.total_users}    label="Total Users"/>
        <Stat n={stats.verified_users} label="Verified"   color="green"/>
        <Stat n={stats.pending_users}  label="Pending OTP" color="orange"/>
        <Stat n={stats.pin_set_users}  label="PIN Set"    color="blue"/>
        <Stat n={stats.total_otps}     label="OTPs Sent"/>
        <Stat n={stats.used_otps}      label="OTPs Used"  color="green"/>
      </div>

      <div className="grid-2">
        <section className="card">
          <h2>Recent Signups</h2>
          {users.length === 0 ? <p className="muted">No users yet.</p> : (
            <table>
              <thead><tr><th>ID</th><th>Mobile</th><th>Status</th><th>Created</th></tr></thead>
              <tbody>
                {users.map(u => (
                  <tr key={u.id}>
                    <td>#{u.id}</td>
                    <td><strong>{u.dial_code}{u.mobile}</strong></td>
                    <td><span className={`pill pill-${u.status}`}>{u.status}</span></td>
                    <td className="muted">{new Date(u.created_at).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="card">
          <h2>SMS Provider</h2>
          <p>Status: <strong className={settings.sms_provider === 'none' ? 'orange' : 'green'}>
            {settings.sms_provider === 'none'
              ? 'Not configured (dev mode — OTP shown on screen)'
              : settings.sms_provider}
          </strong></p>
          <p style={{ marginTop: 8 }}>
            OTP length: <strong>{settings.otp_length}</strong> · Expiry: <strong>{settings.otp_expiry_minutes} min</strong>
          </p>
          <Link to="/settings" className="btn btn-secondary" style={{ marginTop: 14, display: 'inline-block' }}>
            Configure SMS
          </Link>
        </section>
      </div>

      <section className="card">
        <h2>Recent Activity</h2>
        {log.length === 0 ? <p className="muted">No events yet.</p> : (
          <table>
            <thead><tr><th>Time</th><th>Actor</th><th>Event</th><th>Details</th></tr></thead>
            <tbody>
              {log.map(l => (
                <tr key={l.id}>
                  <td className="muted">{new Date(l.ts).toLocaleString()}</td>
                  <td>{l.actor}</td>
                  <td><span className="pill">{l.event}</span></td>
                  <td className="muted">{l.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}

function Stat({ n, label, color }) {
  return (
    <div className="stat">
      <div className={`stat-num ${color || ''}`}>{n}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}
