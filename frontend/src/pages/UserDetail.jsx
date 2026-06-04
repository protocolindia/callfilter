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

  // Schedules + block-all state
  const [schedules, setSchedules] = useState([]);
  const [globalReasons, setGlobalReasons] = useState(null);
  const [blockAll, setBlockAll] = useState(null);

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

  // Load schedules when tab opens
  useEffect(() => {
    if (tab !== 'schedules') return;
    api.get(`/admin/users/${id}/schedules`).then(r => setSchedules(r.schedules || []));
  }, [tab, id]);

  // Load block-all when tab opens
  useEffect(() => {
    if (tab !== 'blockall') return;
    api.get(`/admin/users/${id}/block-all`).then(r => setBlockAll(r.state));
  }, [tab, id]);

  // Delete a rule
  async function deleteRule(rid) {
    if (!confirm('Delete this rule?')) return;
    await api.delete(`/admin/users/${id}/rules/${rid}`);
    const r = await api.get(`/admin/users/${id}/rules`);
    setRules(r.rules);
  }

  // Create a new rule
  const [newRule, setNewRule] = useState({ rule_type: 'prefix', pattern: '', action: 'reject' });
  async function addRule(e) {
    e.preventDefault();
    if (!newRule.pattern) return;
    await api.post(`/admin/users/${id}/rules`, newRule);
    setNewRule({ rule_type: 'prefix', pattern: '', action: 'reject' });
    const r = await api.get(`/admin/users/${id}/rules`);
    setRules(r.rules);
  }

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
        <h1>{user.name || `${user.dial_code}${user.mobile}`}</h1>
        {user.name && <small className="muted">{user.dial_code}{user.mobile}</small>}
        <p className="muted">User #{user.id} · {user.country_iso || 'Unknown country'}</p>
      </header>

      <div className="filter-bar" style={{ borderBottom: '1px solid var(--border)', paddingBottom: 0 }}>
        <button className={`tab-btn ${tab === 'info' ? 'tab-active' : ''}`} onClick={() => setTab('info')}>
          Info
        </button>
        <button className={`tab-btn ${tab === 'otp' ? 'tab-active' : ''}`} onClick={() => setTab('otp')}>
          🔑 OTP Mode
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
        <button className={`tab-btn ${tab === 'schedules' ? 'tab-active' : ''}`} onClick={() => setTab('schedules')}>
          🗓️ Schedules
        </button>
        <button className={`tab-btn ${tab === 'blockall' ? 'tab-active' : ''}`} onClick={() => setTab('blockall')}>
          🛑 Block All
        </button>
        <button className={`tab-btn ${tab === 'global' ? 'tab-active' : ''}`} onClick={() => setTab('global')}>
          🌐 Global
        </button>
      </div>

      {tab === 'info' && <InfoTab user={user} />}

      {tab === 'otp' && <OtpModeTab user={user} />}

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
          <form onSubmit={addRule} style={{
              display: 'grid', gridTemplateColumns: '1fr 2fr 1fr auto',
              gap: 10, marginBottom: 16, alignItems: 'end'
            }}>
            <div className="field">
              <label>Type</label>
              <select value={newRule.rule_type}
                onChange={e => setNewRule({ ...newRule, rule_type: e.target.value })}>
                <option value="prefix">prefix</option>
                <option value="suffix">suffix</option>
                <option value="range">range</option>
              </select>
            </div>
            <div className="field">
              <label>Pattern</label>
              <input value={newRule.pattern}
                onChange={e => setNewRule({ ...newRule, pattern: e.target.value })}
                placeholder={newRule.rule_type === 'range' ? '+919876543205-+919876543220' : '+919876'} />
            </div>
            <div className="field">
              <label>Action</label>
              <select value={newRule.action}
                onChange={e => setNewRule({ ...newRule, action: e.target.value })}>
                <option value="reject">reject</option>
                <option value="accept">accept</option>
              </select>
            </div>
            <button type="submit" className="btn btn-primary">+ Add rule</button>
          </form>

          {rules.length === 0 ? <p className="muted">No rules yet.</p> : (
            <table>
              <thead><tr><th>Type</th><th>Pattern</th><th>Action</th><th>Synced</th><th></th></tr></thead>
              <tbody>
                {rules.map(r => (
                  <tr key={r.id}>
                    <td><span className="pill">{r.rule_type}</span></td>
                    <td><code>{r.rule_type === 'between' || r.rule_type === 'range'
                      ? r.pattern.replace('-', ' → ') : r.pattern}</code></td>
                    <td>
                      <span className={`pill pill-${r.action === 'accept' ? 'verified' : 'pending'}`}>
                        {r.action}
                      </span>
                    </td>
                    <td className="muted">{fmt(r.created_at)}</td>
                    <td>
                      <button className="btn btn-danger" style={{ padding: '4px 10px', fontSize: 12 }}
                        onClick={() => deleteRule(r.id)}>Delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      {tab === 'schedules' && (
        <section className="card">
          {schedules.length === 0 ? <p className="muted">No schedules synced yet.</p> : (
            <table>
              <thead><tr><th>Name</th><th>Time</th><th>Days</th><th>Enabled</th><th>Allow list</th><th>Frequency bypass</th></tr></thead>
              <tbody>
                {schedules.map(s => {
                  const sh = Math.floor(s.start_minute / 60), sm = s.start_minute % 60;
                  const eh = Math.floor(s.end_minute   / 60), em = s.end_minute   % 60;
                  const time = `${String(sh).padStart(2,'0')}:${String(sm).padStart(2,'0')} → ${String(eh).padStart(2,'0')}:${String(em).padStart(2,'0')}`;
                  const dayLetters = ['S','M','T','W','T','F','S'];
                  const days = dayLetters.map((d, i) => (s.days_mask & (1 << i)) ? d : '·').join(' ');
                  const allowCount = Array.isArray(s.allow_numbers) ? s.allow_numbers.length : 0;
                  return (
                    <tr key={s.id}>
                      <td>{s.name || '(unnamed)'}</td>
                      <td><code>{time}</code></td>
                      <td><code style={{ letterSpacing: 2 }}>{days}</code></td>
                      <td>{s.is_enabled ? '✓' : '—'}</td>
                      <td className="muted">{allowCount} number(s)</td>
                      <td>{s.freq_bypass_enabled
                        ? `${s.freq_count} in ${s.freq_window_min}min`
                        : <span className="muted">off</span>}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </section>
      )}

      {tab === 'blockall' && (
        <section className="card">
          {!blockAll || !blockAll.mode ? (
            <p className="muted">Block All Now is not active for this user.</p>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: '160px 1fr', gap: 10 }}>
              <div className="muted">Mode</div>
              <div><span className="pill">{blockAll.mode}</span></div>
              <div className="muted">Expires</div>
              <div>{blockAll.expires_at_ms
                ? new Date(parseInt(blockAll.expires_at_ms, 10)).toLocaleString()
                : <em>Until user turns it off</em>}</div>
              <div className="muted">Allow list</div>
              <div>{Array.isArray(blockAll.allow_numbers) && blockAll.allow_numbers.length
                ? blockAll.allow_numbers.join(', ')
                : <span className="muted">(none)</span>}</div>
              <div className="muted">Updated</div>
              <div>{fmt(blockAll.updated_at)}</div>
            </div>
          )}
        </section>
      )}

      {tab === 'global' && (
        <div style={{ padding: 8 }}>
          <h3 style={{ margin: '0 0 16px', color: 'var(--text)' }}>🌐 Global Blocklist Settings</h3>
          {globalReasons === null ? (
            <p className="muted">Loading…</p>
          ) : globalReasons.length === 0 ? (
            <div style={{ padding: '24px', textAlign: 'center', color: 'var(--subtext)', background: 'var(--surface)', borderRadius: 8 }}>
              <div style={{ fontSize: 32, marginBottom: 8 }}>🌐</div>
              <div>No reason categories enabled.</div>
              <div style={{ fontSize: 13, marginTop: 4 }}>User has not enabled any global blocklist categories in the app.</div>
            </div>
          ) : (
            <div>
              <p style={{ color: 'var(--subtext)', fontSize: 14, marginBottom: 16 }}>
                {globalReasons.length} reason categor{globalReasons.length === 1 ? 'y' : 'ies'} enabled — calls matching numbers in these categories are blocked.
              </p>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {globalReasons.map(r => (
                  <span key={r} style={{
                    background: 'rgba(239,68,68,0.15)', color: '#ef4444',
                    borderRadius: 20, padding: '6px 14px', fontSize: 13, fontWeight: 600,
                    border: '1px solid rgba(239,68,68,0.3)'
                  }}>🌐 {r}</span>
                ))}
              </div>
            </div>
          )}
        </div>
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
                    <th>When</th><th>Number</th><th>Matched rule</th><th>Type</th><th>Reason</th>
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
                      <td>{c.reason
                        ? <span className="pill pill-pending">{c.reason}</span>
                        : <span className="muted">—</span>}</td>
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

function OtpModeTab({ user }) {
  const [mode, setMode] = useState(user.otp_mode || 'global');
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState('');

  async function save(newMode) {
    setSaving(true); setMsg('');
    try {
      await api.put(`/admin/users/${user.id}/otp-mode`, { otp_mode: newMode });
      setMode(newMode);
      setMsg('Saved — this user will use "' + newMode + '" OTP mode.');
      setTimeout(() => setMsg(''), 4000);
    } catch (e) {
      setMsg('Error: ' + e.message);
    } finally { setSaving(false); }
  }

  const options = [
    { id: 'global',     title: 'Follow global setting',
      desc: 'Use whatever the global SMS provider is configured to (default).' },
    { id: 'demo',       title: 'Demo mode (always)',
      desc: 'OTP is returned in the response and shown on screen. No real SMS. Good for testing.' },
    { id: 'production', title: 'Production mode (always)',
      desc: 'Always send a real SMS via the configured gateway. Requires a working SMS provider.' },
  ];

  return (
    <section className="card">
      <h2>🔑 OTP Delivery Mode</h2>
      <p className="muted" style={{ marginBottom: 16 }}>
        Override how this specific user receives their OTP. Useful for keeping
        a test account in demo mode while everyone else uses real SMS.
      </p>

      {msg && (
        <div style={{ marginBottom: 14, padding: 10, borderRadius: 8,
          background: msg.startsWith('Error') ? 'rgba(239,68,68,0.15)' : 'rgba(34,197,94,0.15)',
          color: msg.startsWith('Error') ? 'var(--reject)' : '#22c55e', fontSize: 14 }}>
          {msg}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {options.map(o => {
          const active = mode === o.id;
          return (
            <div key={o.id}
              onClick={() => !saving && save(o.id)}
              style={{
                padding: 16, borderRadius: 10, cursor: saving ? 'wait' : 'pointer',
                background: 'var(--surface)',
                border: active ? '2px solid var(--accent)' : '1px solid var(--border)',
              }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{
                  width: 18, height: 18, borderRadius: '50%', flexShrink: 0,
                  border: active ? '5px solid var(--accent)' : '2px solid var(--subtext)',
                }} />
                <strong style={{ color: 'var(--text)' }}>{o.title}</strong>
                {active && (
                  <span style={{ marginLeft: 'auto', fontSize: 11, fontWeight: 700,
                    color: '#22c55e', background: 'rgba(34,197,94,0.15)',
                    borderRadius: 4, padding: '2px 8px' }}>ACTIVE</span>
                )}
              </div>
              <p className="muted" style={{ margin: '6px 0 0 28px', fontSize: 13 }}>{o.desc}</p>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function InfoTab({ user }) {
  const [sub, setSub] = useState(null);
  const [loadingSub, setLoadingSub] = useState(true);
  const [grant, setGrant] = useState(false);
  const [plans, setPlans] = useState([]);
  const [grantPlanId, setGrantPlanId] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const r = await api.get(`/admin/users/${user.id}/subscriptions`);
        setSub(r.subscriptions[0] || null);
      } finally { setLoadingSub(false); }
    })();
  }, [user.id]);

  useEffect(() => {
    if (grant) api.get('/admin/plans').then(r => {
      const active = r.plans.filter(p => p.is_active);
      setPlans(active);
      if (active.length) setGrantPlanId(active[0].id);
    });
  }, [grant]);

  async function handleGrant() {
    if (!grantPlanId) return;
    await api.post(`/admin/users/${user.id}/subscriptions`, { plan_id: parseInt(grantPlanId, 10) });
    const r = await api.get(`/admin/users/${user.id}/subscriptions`);
    setSub(r.subscriptions[0]);
    setGrant(false);
  }

  return (
    <section className="card">
      <h2>Account</h2>
      <table>
        <tbody>
          <Row label="Name"          value={user.name || <span className="muted">Not provided</span>} />
          <Row label="Mobile"        value={`${user.dial_code}${user.mobile}`} />
          <Row label="Country"       value={user.country_iso || '—'} />
          <Row label="Status"        value={<span className={`pill pill-${user.status}`}>{user.status}</span>} />
          <Row label="Created"       value={fmt(user.created_at)} />
          <Row label="Verified at"   value={fmt(user.verified_at)} />
          <Row label="PIN set at"    value={fmt(user.pin_set_at)} />
          <Row label="Device"        value={user.device_info || '—'} />
        </tbody>
      </table>

      <h2 style={{ marginTop: 24 }}>💬 SMS Auto-Reply Templates</h2>
      {(() => {
        let templates = [];
        if (user.sms_templates) {
          try { templates = JSON.parse(user.sms_templates); } catch (_) { templates = []; }
        }
        if (!Array.isArray(templates) || templates.length === 0) {
          return <p className="muted">No templates synced yet.</p>;
        }
        return (
          <ol style={{ margin: '8px 0 0', paddingLeft: 20 }}>
            {templates.map((t, i) => (
              <li key={i} style={{ marginBottom: 6, color: 'var(--text)' }}>{t}</li>
            ))}
          </ol>
        );
      })()}

      <h2 style={{ marginTop: 24 }}>💳 Subscription</h2>
      {loadingSub ? <p className="muted">Loading…</p>
       : !sub ? <p className="muted">No subscription record.</p>
       : (() => {
          const expired = new Date(sub.expires_at) < new Date();
          return (
            <table>
              <tbody>
                <Row label="Plan"     value={sub.plan_name || (sub.is_trial ? 'Trial (no plan)' : '—')} />
                <Row label="Status"   value={
                  <span className={`pill pill-${expired ? 'pending' : (sub.is_trial ? 'pending' : 'verified')}`}>
                    {expired ? 'expired' : sub.status}
                  </span>
                } />
                <Row label="Started"  value={fmt(sub.starts_at)} />
                <Row label="Expires"  value={fmt(sub.expires_at)} />
                <Row label="Trial?"   value={sub.is_trial ? 'Yes' : 'No'} />
                <Row label="Paid"     value={sub.amount_paid != null ? `${sub.currency === 'USD' ? '$' : '₹'}${(parseFloat(sub.amount_paid) / 100).toFixed(2)}` : '—'} />
                {sub.coupon_code && <Row label="Coupon" value={<code>{sub.coupon_code}</code>} />}
              </tbody>
            </table>
          );
        })()}

      {!grant ? (
        <button className="btn btn-secondary" onClick={() => setGrant(true)} style={{ marginTop: 12 }}>
          Grant subscription
        </button>
      ) : (
        <div style={{ marginTop: 12, padding: 14, background: 'var(--surface)', borderRadius: 8 }}>
          <label>Plan</label>
          <select value={grantPlanId} onChange={e => setGrantPlanId(e.target.value)}>
            {plans.map(p => (
              <option key={p.id} value={p.id}>
                {p.name} — {p.duration_days} days — {p.currency === 'USD' ? '$' : '₹'}{(p.offer_price / 100).toFixed(2)}
              </option>
            ))}
          </select>
          <div style={{ marginTop: 10, display: 'flex', gap: 8 }}>
            <button className="btn btn-primary" onClick={handleGrant}>Grant</button>
            <button className="btn btn-ghost" onClick={() => setGrant(false)}>Cancel</button>
          </div>
        </div>
      )}

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
