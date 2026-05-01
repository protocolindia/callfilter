import React, { useEffect, useState } from 'react';
import { api } from '../api';

export default function Users() {
  const [users, setUsers] = useState([]);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams();
      if (q) params.set('q', q);
      if (status) params.set('status', status);
      const r = await api.get(`/admin/users${params.toString() ? '?' + params : ''}`);
      setUsers(r.users || []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  async function handleDelete(id) {
    if (!confirm('Delete user permanently?')) return;
    try {
      await api.del(`/admin/users/${id}`);
      load();
    } catch (err) { alert(err.message); }
  }
  async function handleReset(id) {
    if (!confirm('Reset this user — clear PIN and verification?')) return;
    try {
      await api.post(`/admin/users/${id}/reset`);
      load();
    } catch (err) { alert(err.message); }
  }

  return (
    <>
      <header className="page-head">
        <h1>Users</h1>
        <p className="muted">All people who have signed up via the Android app</p>
      </header>

      <form className="filter-bar" onSubmit={e => { e.preventDefault(); load(); }}>
        <input
          type="text" placeholder="Search by mobile or dial code…"
          value={q} onChange={e => setQ(e.target.value)}
        />
        <select value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All statuses</option>
          <option value="pending">Pending</option>
          <option value="verified">Verified</option>
        </select>
        <button type="submit" className="btn btn-secondary">Filter</button>
        {(q || status) && <button type="button" className="btn btn-ghost"
          onClick={() => { setQ(''); setStatus(''); setTimeout(load, 0); }}>Clear</button>}
      </form>

      <section className="card">
        {loading ? <p className="muted">Loading…</p>
         : error ? <div className="alert alert-error">{error}</div>
         : users.length === 0 ? <p className="muted">No users found.</p>
         : (
          <table>
            <thead>
              <tr>
                <th>ID</th><th>Mobile</th><th>Country</th><th>Status</th>
                <th>PIN Set</th><th>Created</th><th>Verified</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map(u => (
                <tr key={u.id}>
                  <td>#{u.id}</td>
                  <td><strong>{u.dial_code}{u.mobile}</strong></td>
                  <td>{u.country_iso || '—'}</td>
                  <td><span className={`pill pill-${u.status}`}>{u.status}</span></td>
                  <td>{u.pin_set_at ? '✓' : '—'}</td>
                  <td className="muted">{new Date(u.created_at).toLocaleString()}</td>
                  <td className="muted">{u.verified_at ? new Date(u.verified_at).toLocaleString() : '—'}</td>
                  <td className="actions">
                    <button className="btn btn-mini btn-ghost" onClick={() => handleReset(u.id)}>Reset</button>
                    <button className="btn btn-mini btn-danger" onClick={() => handleDelete(u.id)}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
