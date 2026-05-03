import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../api';

export default function UserDetail() {
  const { id } = useParams();
  const [user, setUser] = useState(null);
  const [contacts, setContacts] = useState([]);
  const [contactsTotal, setContactsTotal] = useState(0);
  const [rules, setRules] = useState([]);
  const [tab, setTab] = useState('info');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => { loadAll(); /* eslint-disable-next-line */ }, [id]);

  async function loadAll() {
    try {
      const [u, c, r] = await Promise.all([
        api.get(`/admin/users/${id}`),
        api.get(`/admin/users/${id}/contacts?limit=200`),
        api.get(`/admin/users/${id}/rules`)
      ]);
      setUser(u.user);
      setContacts(c.contacts);
      setContactsTotal(c.total);
      setRules(r.rules);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }

  async function searchContacts(q) {
    setSearch(q);
    try {
      const c = await api.get(`/admin/users/${id}/contacts?limit=200&q=${encodeURIComponent(q)}`);
      setContacts(c.contacts);
    } catch (e) { setError(e.message); }
  }

  if (loading) return <p className="muted">Loading…</p>;
  if (error)   return <div className="alert alert-error">{error}</div>;
  if (!user)   return <p>User not found.</p>;

  return (
    <>
      <header className="page-head">
        <p style={{ marginBottom: 6 }}><Link to="/users">← All users</Link></p>
        <h1>{user.dial_code}{user.mobile}</h1>
        <p className="muted">User #{user.id} · {user.country_iso || 'Unknown country'}</p>
      </header>

      <div className="filter-bar" style={{ borderBottom: '1px solid var(--border)', paddingBottom: 0 }}>
        <button className={`tab-btn ${tab === 'info' ? 'tab-active' : ''}`} onClick={() => setTab('info')}>Info</button>
        <button className={`tab-btn ${tab === 'contacts' ? 'tab-active' : ''}`} onClick={() => setTab('contacts')}>
          📇 Contacts ({contactsTotal})
        </button>
        <button className={`tab-btn ${tab === 'rules' ? 'tab-active' : ''}`} onClick={() => setTab('rules')}>
          🛡️ Rules ({rules.length})
        </button>
      </div>

      {tab === 'info' && (
        <section className="card">
          <h2>Account</h2>
          <table>
            <tbody>
              <Row label="Mobile"        value={`${user.dial_code}${user.mobile}`} />
              <Row label="Country"       value={user.country_iso || '—'} />
              <Row label="Status"        value={<span className={`pill pill-${user.status}`}>{user.status}</span>} />
              <Row label="Created"       value={fmt(user.created_at)} />
              <Row label="Verified at"   value={fmt(user.verified_at)} />
              <Row label="PIN set at"    value={fmt(user.pin_set_at)} />
              <Row label="Device"        value={user.device_info || '—'} />
            </tbody>
          </table>

          <h2 style={{ marginTop: 24 }}>Sync status</h2>
          <table>
            <tbody>
              <Row label="Contacts opted in"   value={user.contacts_opted_in ? `Yes — since ${fmt(user.contacts_opted_in_at)}` : 'No'} />
              <Row label="Contacts synced"     value={`${user.contacts_count || 0} contacts`} />
              <Row label="Last contacts sync"  value={fmt(user.last_contacts_sync)} />
              <Row label="Rules synced"        value={`${user.rules_count || 0} rules`} />
              <Row label="Last rules sync"     value={fmt(user.last_rules_sync)} />
            </tbody>
          </table>
        </section>
      )}

      {tab === 'contacts' && (
        <section className="card">
          {!user.contacts_opted_in ? (
            <p className="muted">This user has not opted in to upload contacts.</p>
          ) : (
            <>
              <input
                type="text" placeholder="🔍 Search contacts (name or number)…"
                value={search}
                onChange={e => searchContacts(e.target.value)}
                style={{ marginBottom: 14 }}
              />
              {contacts.length === 0 ? (
                <p className="muted">No contacts match.</p>
              ) : (
                <table>
                  <thead><tr><th>Name</th><th>Phone Number</th><th>Synced</th></tr></thead>
                  <tbody>
                    {contacts.map(c => (
                      <tr key={c.id}>
                        <td>{c.display_name || <span className="muted">— no name —</span>}</td>
                        <td><code>{c.phone_number}</code></td>
                        <td className="muted">{fmt(c.created_at)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {contactsTotal > contacts.length && (
                <p className="muted" style={{ marginTop: 12 }}>
                  Showing {contacts.length} of {contactsTotal} total · refine search to narrow
                </p>
              )}
            </>
          )}
        </section>
      )}

      {tab === 'rules' && (
        <section className="card">
          {rules.length === 0 ? (
            <p className="muted">No rules synced yet.</p>
          ) : (
            <table>
              <thead><tr><th>Type</th><th>Pattern</th><th>Action</th><th>Synced</th></tr></thead>
              <tbody>
                {rules.map(r => (
                  <tr key={r.id}>
                    <td><span className="pill">{r.rule_type}</span></td>
                    <td><code>{r.rule_type === 'between' ? r.pattern.replace('~', ' → ') : r.pattern}</code></td>
                    <td><span className={`pill pill-${r.action === 'accept' ? 'verified' : 'pending'}`}>{r.action}</span></td>
                    <td className="muted">{fmt(r.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}
    </>
  );
}

function Row({ label, value }) {
  return (
    <tr>
      <td style={{ width: 200, fontWeight: 600 }}>{label}</td>
      <td>{value}</td>
    </tr>
  );
}

function fmt(ts) {
  if (!ts) return <span className="muted">—</span>;
  return new Date(ts).toLocaleString();
}
