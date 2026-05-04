import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../api';

const PAGE_SIZE = 50;

export default function UserDetail() {
  const { id } = useParams();
  const [user, setUser] = useState(null);
  const [tab, setTab] = useState('info');

  // Contacts state
  const [contacts, setContacts] = useState([]);
  const [contactsTotal, setContactsTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [search, setSearch] = useState('');
  const [contactsLoading, setContactsLoading] = useState(false);

  const [rules, setRules] = useState([]);

  // Blocked calls state
  const [calls, setCalls] = useState([]);
  const [callsTotal, setCallsTotal] = useState(0);
  const [callsPage, setCallsPage] = useState(1);
  const [callsTotalPages, setCallsTotalPages] = useState(1);
  const [callsSearch, setCallsSearch] = useState('');
  const [callsLoading, setCallsLoading] = useState(false);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState({}); // contactId → bool

  useEffect(() => {
    Promise.all([
      api.get(`/admin/users/${id}`),
      api.get(`/admin/users/${id}/rules`)
    ]).then(([u, r]) => {
      setUser(u.user);
      setRules(r.rules);
    }).catch(e => setError(e.message))
      .finally(() => setLoading(false));
  }, [id]);

  // Load contacts whenever tab=contacts, page, or search changes
  useEffect(() => {
    if (tab !== 'contacts') return;
    loadContacts();
    // eslint-disable-next-line
  }, [tab, page, search]);

  // Load blocked calls when tab=blocked
  useEffect(() => {
    if (tab !== 'blocked') return;
    loadBlockedCalls();
    // eslint-disable-next-line
  }, [tab, callsPage, callsSearch]);

  async function loadBlockedCalls() {
    setCallsLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', callsPage);
      params.set('limit', PAGE_SIZE);
      if (callsSearch) params.set('q', callsSearch);
      const r = await api.get(`/admin/users/${id}/blocked-calls?${params}`);
      setCalls(r.calls);
      setCallsTotal(r.total);
      setCallsTotalPages(r.total_pages || 1);
    } catch (e) { setError(e.message); }
    finally { setCallsLoading(false); }
  }

  async function loadContacts() {
    setContactsLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', page);
      params.set('limit', PAGE_SIZE);
      if (search) params.set('q', search);
      const r = await api.get(`/admin/users/${id}/contacts?${params}`);
      setContacts(r.contacts);
      setContactsTotal(r.total);
      setTotalPages(r.total_pages || 1);
    } catch (e) { setError(e.message); }
    finally { setContactsLoading(false); }
  }

  function onSearchChange(v) {
    setSearch(v);
    setPage(1);
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
        <button className={`tab-btn ${tab === 'info' ? 'tab-active' : ''}`} onClick={() => setTab('info')}>
          Info
        </button>
        <button className={`tab-btn ${tab === 'contacts' ? 'tab-active' : ''}`} onClick={() => setTab('contacts')}>
          📇 Contacts ({user.contacts_count || 0})
        </button>
        <button className={`tab-btn ${tab === 'rules' ? 'tab-active' : ''}`} onClick={() => setTab('rules')}>
          🛡️ Rules ({rules.length})
        </button>
        <button className={`tab-btn ${tab === 'blocked' ? 'tab-active' : ''}`} onClick={() => setTab('blocked')}>
          🚫 Blocked Calls ({user.blocked_calls_count || 0})
        </button>
      </div>

      {tab === 'info' && <InfoTab user={user} />}

      {tab === 'contacts' && (
        <section className="card">
          <input
            type="text"
            placeholder="🔍 Search contacts (name, number, email)…"
            value={search}
            onChange={e => onSearchChange(e.target.value)}
            style={{ marginBottom: 14 }}
          />

          {contactsLoading ? <p className="muted">Loading…</p>
           : contacts.length === 0 ? <p className="muted">No contacts found.</p>
           : (
            <>
              <div className="contacts-grid">
                {contacts.map(c => (
                  <ContactRow
                    key={c.id}
                    c={c}
                    expanded={!!expanded[c.id]}
                    onToggle={() => setExpanded(s => ({ ...s, [c.id]: !s[c.id] }))}
                  />
                ))}
              </div>

              <Pagination
                page={page}
                totalPages={totalPages}
                total={contactsTotal}
                pageSize={contacts.length}
                onPage={setPage}
              />
            </>
          )}
        </section>
      )}

      {tab === 'rules' && (
        <section className="card">
          {rules.length === 0 ? <p className="muted">No rules synced yet.</p> : (
            <table>
              <thead><tr><th>Type</th><th>Pattern</th><th>Action</th><th>Synced</th></tr></thead>
              <tbody>
                {rules.map(r => (
                  <tr key={r.id}>
                    <td><span className="pill">{r.rule_type}</span></td>
                    <td><code>{r.rule_type === 'between' ? r.pattern.replace('~', ' → ') : r.pattern}</code></td>
                    <td>
                      <span className={`pill pill-${r.action === 'accept' ? 'verified' : 'pending'}`}>
                        {r.action}
                      </span>
                    </td>
                    <td className="muted">{fmt(r.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      {tab === 'blocked' && (
        <section className="card">
          <input
            type="text"
            placeholder="🔍 Search by number or pattern…"
            value={callsSearch}
            onChange={e => { setCallsSearch(e.target.value); setCallsPage(1); }}
            style={{ marginBottom: 14 }}
          />

          {callsLoading ? <p className="muted">Loading…</p>
           : calls.length === 0 ? <p className="muted">No blocked calls yet.</p>
           : (
            <>
              <table>
                <thead>
                  <tr>
                    <th>When</th><th>Number</th><th>Matched rule</th><th>Type</th>
                  </tr>
                </thead>
                <tbody>
                  {calls.map(c => (
                    <tr key={c.id}>
                      <td className="muted">{fmt(c.blocked_at)}</td>
                      <td><code>{c.number || '—'}</code></td>
                      <td>
                        {c.rule_pattern
                          ? <code>{c.rule_pattern.includes('~')
                              ? c.rule_pattern.replace('~', ' → ')
                              : c.rule_pattern}</code>
                          : <span className="muted">—</span>}
                      </td>
                      <td><span className="pill">{c.rule_type || '—'}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <Pagination
                page={callsPage}
                totalPages={callsTotalPages}
                total={callsTotal}
                pageSize={calls.length}
                onPage={setCallsPage}
              />
            </>
          )}
        </section>
      )}
    </>
  );
}

function InfoTab({ user }) {
  return (
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
          <Row label="Contacts opted in"
               value={user.contacts_opted_in
                 ? `Yes — since ${user.contacts_opted_in_at ? new Date(user.contacts_opted_in_at).toLocaleString() : '—'}`
                 : 'No'} />
          <Row label="Contacts synced"     value={`${user.contacts_count || 0} contacts`} />
          <Row label="Last contacts sync"  value={fmt(user.last_contacts_sync)} />
          <Row label="Rules synced"        value={`${user.rules_count || 0} rules`} />
          <Row label="Last rules sync"     value={fmt(user.last_rules_sync)} />
          <Row label="Blocked calls"       value={`${user.blocked_calls_count || 0} calls`} />
        </tbody>
      </table>
    </section>
  );
}

function ContactRow({ c, expanded, onToggle }) {
  const primary = c.phones[0];
  const hasMore = c.phones.length > 1 || c.emails.length || c.addresses.length
                || c.orgs.length || c.websites.length || c.events.length;

  return (
    <div className="contact-card">
      <div className="contact-header" onClick={hasMore ? onToggle : undefined}
           style={{ cursor: hasMore ? 'pointer' : 'default' }}>
        <div className="contact-avatar">
          {c.photo_uri
            ? <img src={c.photo_uri} alt="" onError={e => e.target.style.display='none'}/>
            : (c.display_name || '?').slice(0,1).toUpperCase()}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="contact-name">
            {c.starred && <span style={{ marginRight: 6 }}>⭐</span>}
            {c.display_name || <span className="muted">— no name —</span>}
          </div>
          <div className="contact-line muted">
            {primary
              ? <code>{primary.number}{primary.type ? ` · ${primary.type}` : ''}</code>
              : <span>—</span>
            }
            {c.phones.length > 1 && <span> · +{c.phones.length - 1} more</span>}
            {c.emails.length > 0 && <span> · {c.emails.length} email{c.emails.length === 1 ? '' : 's'}</span>}
            {c.orgs.length > 0 && c.orgs[0].company && <span> · {c.orgs[0].company}</span>}
          </div>
        </div>
        {hasMore && <div className="muted">{expanded ? '▴' : '▾'}</div>}
      </div>

      {expanded && (
        <div className="contact-detail">
          {c.phones.length > 0 && (
            <DetailGroup label="📞 Phones">
              {c.phones.map((p,i) => <li key={i}><code>{p.number}</code>{p.type && <span className="muted"> · {p.type}</span>}</li>)}
            </DetailGroup>
          )}
          {c.emails.length > 0 && (
            <DetailGroup label="✉️ Emails">
              {c.emails.map((e,i) => <li key={i}>{e.address}{e.type && <span className="muted"> · {e.type}</span>}</li>)}
            </DetailGroup>
          )}
          {c.addresses.length > 0 && (
            <DetailGroup label="📍 Addresses">
              {c.addresses.map((a,i) => (
                <li key={i}>
                  {a.formatted_address ||
                    [a.street, a.city, a.region, a.postcode, a.country].filter(Boolean).join(', ')}
                  {a.type && <span className="muted"> · {a.type}</span>}
                </li>
              ))}
            </DetailGroup>
          )}
          {c.orgs.length > 0 && (
            <DetailGroup label="🏢 Organizations">
              {c.orgs.map((o,i) => (
                <li key={i}>
                  {[o.title, o.company, o.department].filter(Boolean).join(' · ')}
                </li>
              ))}
            </DetailGroup>
          )}
          {c.websites.length > 0 && (
            <DetailGroup label="🌐 Websites">
              {c.websites.map((w,i) => <li key={i}>{w.url}</li>)}
            </DetailGroup>
          )}
          {c.events.length > 0 && (
            <DetailGroup label="🎂 Events">
              {c.events.map((ev,i) => <li key={i}>{ev.date_text}{ev.type && <span className="muted"> · {ev.type}</span>}</li>)}
            </DetailGroup>
          )}
          {c.notes && (
            <DetailGroup label="📝 Notes">
              <li>{c.notes}</li>
            </DetailGroup>
          )}
        </div>
      )}
    </div>
  );
}

function DetailGroup({ label, children }) {
  return (
    <div className="detail-group">
      <div className="detail-label">{label}</div>
      <ul>{children}</ul>
    </div>
  );
}

function Pagination({ page, totalPages, total, pageSize, onPage }) {
  if (totalPages <= 1) return <p className="muted" style={{ marginTop: 12 }}>{total} contacts total</p>;

  const start = (page - 1) * 50 + 1;
  const end   = (page - 1) * 50 + pageSize;

  return (
    <div className="pagination">
      <span className="muted">Showing {start}–{end} of {total}</span>
      <div className="pagination-controls">
        <button className="btn btn-mini btn-ghost"
                disabled={page <= 1} onClick={() => onPage(1)}>« First</button>
        <button className="btn btn-mini btn-ghost"
                disabled={page <= 1} onClick={() => onPage(page - 1)}>‹ Prev</button>
        <span className="pagination-pageinfo">Page {page} / {totalPages}</span>
        <button className="btn btn-mini btn-ghost"
                disabled={page >= totalPages} onClick={() => onPage(page + 1)}>Next ›</button>
        <button className="btn btn-mini btn-ghost"
                disabled={page >= totalPages} onClick={() => onPage(totalPages)}>Last »</button>
      </div>
    </div>
  );
}

function Row({ label, value }) {
  return <tr><td style={{ width: 200, fontWeight: 600 }}>{label}</td><td>{value}</td></tr>;
}

function fmt(ts) {
  if (!ts) return <span className="muted">—</span>;
  return new Date(ts).toLocaleString();
}
