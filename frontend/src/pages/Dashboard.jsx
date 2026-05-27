import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { getAdminRole, getAdminMeta } from '../api.js';

function Stat({ n, label, color }) {
  const colors = { green:'#22c55e', red:'#ef4444', orange:'#f59e0b', blue:'#4f8ef7', purple:'#a855f7' };
  return (
    <div className="stat-card">
      <div className="stat-n" style={color ? { color:colors[color]||color } : {}}>{n ?? '—'}</div>
      <div className="stat-label">{label}</div>
    </div>
  );
}

function fmt(d) {
  if (!d) return '—';
  return new Date(d).toLocaleDateString('en-IN',
    { day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit' });
}

// ── Super Admin / Admin / Support / Billing dashboard ─────────────────────
function AdminDashboard() {
  const [stats, setStats]     = useState(null);
  const [users, setUsers]     = useState([]);
  const [log, setLog]         = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');
  const role = getAdminRole();

  useEffect(() => {
    Promise.all([
      api.get('/admin/stats').catch(() => null),
    ]).then(([s]) => {
      if (s) { setStats(s.stats); setUsers(s.recent_users||[]); setLog(s.recent_log||[]); }
      setLoading(false);
    }).catch(e => { setError(e.message); setLoading(false); });
  }, []);

  if (loading) return <div className="loading">Loading…</div>;
  if (error)   return <div className="alert alert-error">{error}</div>;
  if (!stats)  return <div className="alert alert-error">Could not load stats</div>;

  return (
    <>
      <header className="page-head">
        <h1>Dashboard</h1>
        <p className="muted">Overview of AI CallFilter platform</p>
      </header>

      <div className="stat-grid">
        <Stat n={stats.total_users}    label="Total Users"/>
        <Stat n={stats.verified_users} label="Verified"     color="green"/>
        <Stat n={stats.pending_users}  label="Pending OTP"  color="orange"/>
        <Stat n={stats.pin_set_users}  label="PIN Set"      color="blue"/>
        {(role === 'super_admin' || role === 'admin' || role === 'support') && (
          <Stat n={stats.blocked_calls_total||0} label="Calls Blocked" color="red"/>
        )}
        <Stat n={stats.total_otps}     label="OTPs Sent"/>
        <Stat n={stats.used_otps}      label="OTPs Used"    color="green"/>
      </div>

      {(role === 'super_admin' || role === 'admin' || role === 'support') && (
        <div className="grid-2">
          <section className="card">
            <h2>Recent Signups</h2>
            {!users.length ? <p className="muted">No users yet.</p> : (
              <table>
                <thead><tr><th>ID</th><th>Mobile</th><th>Status</th><th>Created</th></tr></thead>
                <tbody>
                  {users.map(u => (
                    <tr key={u.id}>
                      <td>#{u.id}</td>
                      <td><Link to={`/users/${u.id}`}><strong>{u.dial_code}{u.mobile}</strong></Link></td>
                      <td><span className={`pill pill-${u.status}`}>{u.status}</span></td>
                      <td className="muted">{fmt(u.created_at)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
          <section className="card">
            <h2>Recent Activity</h2>
            {!log.length ? <p className="muted">No activity yet.</p> : (
              <table>
                <thead><tr><th>Actor</th><th>Event</th><th>Time</th></tr></thead>
                <tbody>
                  {log.map(l => (
                    <tr key={l.id}>
                      <td>{l.actor}</td>
                      <td className="muted" style={{ fontSize:12 }}>{l.event}</td>
                      <td className="muted">{fmt(l.created_at)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </div>
      )}
    </>
  );
}

// ── Global DB Admin / Global DB User dashboard ────────────────────────────
function GlobalDbDashboard() {
  const [data, setData]       = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');
  const meta = getAdminMeta();
  const role = getAdminRole();
  const isAdmin = role === 'global_db_admin';

  useEffect(() => {
    api.get('/admin/global-db-stats')
      .then(d => { setData(d); setLoading(false); })
      .catch(e => { setError(e.message); setLoading(false); });
  }, []);

  if (loading) return <div className="loading">Loading…</div>;
  if (error)   return <div className="alert alert-error">{error}</div>;

  return (
    <>
      <header className="page-head">
        <h1>Welcome, {meta?.display_name || meta?.username}</h1>
        <p className="muted">
          {isAdmin ? 'Global DB Admin — manage your blocklist entries and sub-users'
                   : 'Global DB User — add and manage your blocklist entries'}
        </p>
      </header>

      {/* Stats */}
      <div className="stat-grid">
        <Stat n={data?.my_entries ?? 0}
          label={isAdmin ? 'Total Entries (you + sub-users)' : 'Your Entries'} color="red"/>
        {isAdmin && <Stat n={data?.sub_users ?? 0} label="Sub-users" color="blue"/>}
        <Stat n={data?.assigned_reasons?.length ?? 0} label="Assigned Reasons" color="purple"/>
      </div>

      {/* Assigned reasons */}
      {data?.assigned_reasons?.length > 0 && (
        <section className="card" style={{ marginBottom:20 }}>
          <h2>Your Assigned Reason Categories</h2>
          <p className="muted" style={{ marginBottom:12 }}>
            You can only add numbers under these categories.
          </p>
          <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
            {data.assigned_reasons.map(r => (
              <span key={r} style={{ background:'rgba(239,68,68,0.15)', color:'#ef4444',
                borderRadius:20, padding:'6px 14px', fontSize:13, fontWeight:600 }}>
                🌐 {r}
              </span>
            ))}
          </div>
        </section>
      )}

      {/* Quick actions */}
      <section className="card" style={{ marginBottom:20 }}>
        <h2>Quick Actions</h2>
        <div style={{ display:'flex', gap:12, flexWrap:'wrap' }}>
          <Link to="/global-blocklist"
            style={{ padding:'10px 20px', borderRadius:6, background:'var(--accent)',
              color:'#fff', fontWeight:600, fontSize:14, textDecoration:'none' }}>
            ➕ Add Number to Blocklist
          </Link>
          {isAdmin && (
            <Link to="/admin-users"
              style={{ padding:'10px 20px', borderRadius:6, background:'var(--surface)',
                color:'var(--text)', fontWeight:600, fontSize:14, textDecoration:'none',
                border:'1px solid var(--border)' }}>
              👥 Manage Sub-users
            </Link>
          )}
        </div>
      </section>

      {/* Recent entries */}
      {data?.recent?.length > 0 && (
        <section className="card">
          <h2>Recent Entries</h2>
          <table>
            <thead>
              <tr>
                <th>Number</th>
                <th>Reason</th>
                {isAdmin && <th>Added By</th>}
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {data.recent.map((e, i) => (
                <tr key={i}>
                  <td style={{ fontFamily:'monospace', fontWeight:600 }}>{e.number}</td>
                  <td>
                    <span style={{ background:'rgba(239,68,68,0.12)', color:'#ef4444',
                      borderRadius:4, padding:'2px 8px', fontSize:12 }}>{e.reason}</span>
                  </td>
                  {isAdmin && <td className="muted">{e.added_by_username || '—'}</td>}
                  <td className="muted">{fmt(e.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </>
  );
}

// ── Main export — picks dashboard by role ─────────────────────────────────
export default function Dashboard() {
  const role = getAdminRole();
  if (role === 'global_db_admin' || role === 'global_db_user')
    return <GlobalDbDashboard />;
  return <AdminDashboard />;
}
