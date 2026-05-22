import React, { useEffect, useState } from 'react';
import { api } from '../api';

export default function Billing() {
  const [tab, setTab] = useState('plans');
  return (
    <>
      <header className="page-head">
        <h1>💳 Billing</h1>
        <p className="muted">Plans, coupons, subscriptions, and payments</p>
      </header>

      <div className="filter-bar" style={{ borderBottom: '1px solid var(--border)', paddingBottom: 0 }}>
        <button className={`tab-btn ${tab === 'plans' ? 'tab-active' : ''}`}         onClick={() => setTab('plans')}>Plans</button>
        <button className={`tab-btn ${tab === 'coupons' ? 'tab-active' : ''}`}       onClick={() => setTab('coupons')}>Coupons</button>
        <button className={`tab-btn ${tab === 'subscriptions' ? 'tab-active' : ''}`} onClick={() => setTab('subscriptions')}>Subscriptions</button>
        <button className={`tab-btn ${tab === 'payments' ? 'tab-active' : ''}`}      onClick={() => setTab('payments')}>Payments</button>
        <button className={`tab-btn ${tab === 'general' ? 'tab-active' : ''}`}       onClick={() => setTab('general')}>General</button>
      </div>

      {tab === 'plans'         && <PlansTab />}
      {tab === 'coupons'       && <CouponsTab />}
      {tab === 'subscriptions' && <SubscriptionsTab />}
      {tab === 'payments'      && <PaymentsTab />}
      {tab === 'general'       && <GeneralTab />}
    </>
  );
}

// =================== PLANS ===================
function PlansTab() {
  const [plans, setPlans] = useState([]);
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    try {
      const r = await api.get('/admin/plans');
      setPlans(r.plans);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);

  if (loading) return <p className="muted">Loading…</p>;
  return (
    <section className="card">
      {error && <div className="alert alert-error">{error}</div>}

      <button className="btn btn-primary" onClick={() => setEditing({ name: '', duration_days: 30, actual_price_unit: 99, offer_price_unit: 49, currency: 'INR' })}>
        + New plan
      </button>

      {editing && <PlanForm plan={editing} onSaved={() => { setEditing(null); load(); }} onCancel={() => setEditing(null)} />}

      {plans.length === 0 ? <p className="muted" style={{ marginTop: 14 }}>No plans yet. Create one above.</p> : (
        <table style={{ marginTop: 16 }}>
          <thead><tr>
            <th>Name</th><th>Duration</th><th>Actual</th><th>Offer</th><th>Status</th><th>Actions</th>
          </tr></thead>
          <tbody>
            {plans.map(p => (
              <tr key={p.id}>
                <td><strong>{p.name}</strong></td>
                <td>{p.duration_days} days</td>
                <td className="muted"><s>{formatPrice(p.actual_price, p.currency)}</s></td>
                <td><strong>{formatPrice(p.offer_price, p.currency)}</strong></td>
                <td><span className={`pill pill-${p.is_active ? 'verified' : 'pending'}`}>
                  {p.is_active ? 'active' : 'inactive'}
                </span></td>
                <td className="actions">
                  <button className="btn btn-mini btn-ghost" onClick={() => setEditing(p)}>Edit</button>
                  {p.is_active && (
                    <button className="btn btn-mini btn-danger"
                      onClick={async () => {
                        if (!confirm('Deactivate this plan?')) return;
                        await api.del(`/admin/plans/${p.id}`); load();
                      }}>Deactivate</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function PlanForm({ plan, onSaved, onCancel }) {
  // Form holds prices in WHOLE UNITS (rupees / dollars). DB stores in
  // smallest unit (paise / cents). Convert when loading and saving.
  const [f, setF] = useState(() => ({
    ...plan,
    actual_price_unit: plan.actual_price_unit != null
      ? plan.actual_price_unit
      : (plan.actual_price != null ? plan.actual_price / 100 : 99),
    offer_price_unit: plan.offer_price_unit != null
      ? plan.offer_price_unit
      : (plan.offer_price != null ? plan.offer_price / 100 : 49)
  }));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const sym = f.currency === 'USD' ? '$' : '₹';

  async function save() {
    setSaving(true); setError('');
    try {
      const body = {
        name: f.name,
        duration_days: parseInt(f.duration_days, 10),
        actual_price: Math.round(parseFloat(f.actual_price_unit) * 100),
        offer_price:  Math.round(parseFloat(f.offer_price_unit)  * 100),
        currency: f.currency || 'INR',
        is_active: f.is_active !== false,
        is_one_time_per_user: f.is_one_time_per_user === true
      };
      if (plan.id) await api.put(`/admin/plans/${plan.id}`, body);
      else         await api.post(`/admin/plans`, body);
      onSaved();
    } catch (e) { setError(e.message); }
    finally { setSaving(false); }
  }

  return (
    <div className="card" style={{ background: 'var(--surface)', marginTop: 14 }}>
      <h2>{plan.id ? 'Edit plan' : 'New plan'}</h2>
      {error && <div className="alert alert-error">{error}</div>}
      <p className="muted" style={{ fontSize: 12, marginBottom: 8 }}>
        Enter prices in whole {f.currency === 'USD' ? 'dollars' : 'rupees'}. Stored internally as {f.currency === 'USD' ? 'cents' : 'paise'}.
      </p>
      <div className="row">
        <div className="col">
          <label>Plan name</label>
          <input type="text" value={f.name} onChange={e => setF({ ...f, name: e.target.value })}/>
        </div>
        <div className="col">
          <label>Duration (days)</label>
          <input type="number" value={f.duration_days} onChange={e => setF({ ...f, duration_days: e.target.value })}/>
        </div>
      </div>
      <div className="row">
        <div className="col">
          <label>Currency</label>
          <select value={f.currency || 'INR'} onChange={e => setF({ ...f, currency: e.target.value })}>
            <option value="INR">INR (₹)</option>
            <option value="USD">USD ($)</option>
          </select>
        </div>
        <div className="col">
          <label>Actual price ({sym})</label>
          <input type="number" step="0.01" min="0" value={f.actual_price_unit}
            onChange={e => setF({ ...f, actual_price_unit: e.target.value })}/>
        </div>
        <div className="col">
          <label>Offer price ({sym})</label>
          <input type="number" step="0.01" min="0" value={f.offer_price_unit}
            onChange={e => setF({ ...f, offer_price_unit: e.target.value })}/>
        </div>
      </div>
      <div style={{ marginTop: 14 }}>
        <label className="checkbox">
          <input type="checkbox"
            checked={f.is_one_time_per_user === true}
            onChange={e => setF({ ...f, is_one_time_per_user: e.target.checked })}/>
          One-time only per user (free trial / one-shot upgrade)
        </label>
        <p className="muted" style={{ fontSize: 12, marginTop: 4 }}>
          When checked, a user can subscribe to this plan only once.
          On their second attempt, the plan appears disabled with
          "Already used" in the Android app.
        </p>
      </div>
      <div style={{ marginTop: 14, display: 'flex', gap: 8 }}>
        <button className="btn btn-primary" onClick={save} disabled={saving}>
          {saving ? 'Saving…' : 'Save'}
        </button>
        <button className="btn btn-ghost" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}

// =================== COUPONS ===================
function CouponsTab() {
  const [coupons, setCoupons] = useState([]);
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    try {
      const r = await api.get('/admin/coupons');
      setCoupons(r.coupons);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);
  if (loading) return <p className="muted">Loading…</p>;
  return (
    <section className="card">
      {error && <div className="alert alert-error">{error}</div>}
      <button className="btn btn-primary" onClick={() => setEditing({
        code: '', discount_type: 'percent', discount_value: 10,
        valid_until: futureDate(30), max_uses: ''
      })}>+ New coupon</button>

      {editing && <CouponForm coupon={editing} onSaved={() => { setEditing(null); load(); }} onCancel={() => setEditing(null)}/>}

      {coupons.length === 0 ? <p className="muted" style={{ marginTop: 14 }}>No coupons yet.</p> : (
        <table style={{ marginTop: 16 }}>
          <thead><tr>
            <th>Code</th><th>Discount</th><th>Valid until</th><th>Uses</th><th>Status</th><th>Actions</th>
          </tr></thead>
          <tbody>
            {coupons.map(c => {
              const expired = new Date(c.valid_until) < new Date();
              return (
                <tr key={c.id}>
                  <td><code>{c.code}</code></td>
                  <td>
                    {c.discount_type === 'percent'
                      ? `${c.discount_value}% off`
                      : `${formatPrice(c.discount_value, 'INR')} off`}
                  </td>
                  <td className="muted">{new Date(c.valid_until).toLocaleDateString()}</td>
                  <td className="muted">{c.uses_count}{c.max_uses ? ` / ${c.max_uses}` : ' / ∞'}</td>
                  <td>
                    {!c.is_active ? <span className="pill pill-pending">disabled</span>
                     : expired ? <span className="pill pill-pending">expired</span>
                     : <span className="pill pill-verified">active</span>}
                  </td>
                  <td className="actions">
                    <button className="btn btn-mini btn-ghost" onClick={() => setEditing(c)}>Edit</button>
                    {c.is_active && (
                      <button className="btn btn-mini btn-danger"
                        onClick={async () => {
                          if (!confirm('Disable this coupon?')) return;
                          await api.del(`/admin/coupons/${c.id}`); load();
                        }}>Disable</button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}

function CouponForm({ coupon, onSaved, onCancel }) {
  const [f, setF] = useState({
    ...coupon,
    valid_until: coupon.valid_until ? coupon.valid_until.slice(0, 10) : ''
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function save() {
    setSaving(true); setError('');
    try {
      const body = {
        code: f.code,
        discount_type: f.discount_type,
        discount_value: parseInt(f.discount_value, 10),
        valid_until: new Date(f.valid_until).toISOString(),
        max_uses: f.max_uses ? parseInt(f.max_uses, 10) : null,
        is_active: f.is_active !== false,
        is_one_time_per_user: f.is_one_time_per_user === true
      };
      if (coupon.id) await api.put(`/admin/coupons/${coupon.id}`, body);
      else           await api.post(`/admin/coupons`, body);
      onSaved();
    } catch (e) { setError(e.message); }
    finally { setSaving(false); }
  }

  return (
    <div className="card" style={{ background: 'var(--surface)', marginTop: 14 }}>
      <h2>{coupon.id ? 'Edit coupon' : 'New coupon'}</h2>
      {error && <div className="alert alert-error">{error}</div>}
      <div className="row">
        <div className="col">
          <label>Code</label>
          <input type="text"
            value={f.code}
            disabled={!!coupon.id}
            onChange={e => setF({ ...f, code: e.target.value.toUpperCase() })}
            placeholder="e.g. WELCOME10"/>
        </div>
        <div className="col">
          <label>Discount type</label>
          <select value={f.discount_type} onChange={e => setF({ ...f, discount_type: e.target.value })}>
            <option value="percent">Percentage (%)</option>
            <option value="flat">Flat amount (paise)</option>
          </select>
        </div>
      </div>
      <div className="row">
        <div className="col">
          <label>Discount value {f.discount_type === 'percent' ? '(0-100)' : '(paise)'}</label>
          <input type="number" value={f.discount_value}
            onChange={e => setF({ ...f, discount_value: e.target.value })}/>
        </div>
        <div className="col">
          <label>Valid until</label>
          <input type="date" value={f.valid_until}
            onChange={e => setF({ ...f, valid_until: e.target.value })}/>
        </div>
      </div>
      <label>Max uses (leave empty for unlimited)</label>
      <input type="number" value={f.max_uses || ''}
        onChange={e => setF({ ...f, max_uses: e.target.value })}/>
      <div style={{ marginTop: 14 }}>
        <label className="checkbox">
          <input type="checkbox"
            checked={f.is_one_time_per_user === true}
            onChange={e => setF({ ...f, is_one_time_per_user: e.target.checked })}/>
          One-time only per user (free trial / one-shot upgrade)
        </label>
        <p className="muted" style={{ fontSize: 12, marginTop: 4 }}>
          When checked, a user can subscribe to this plan only once.
          On their second attempt, the plan appears disabled with
          "Already used" in the Android app.
        </p>
      </div>
      <div style={{ marginTop: 14, display: 'flex', gap: 8 }}>
        <button className="btn btn-primary" onClick={save} disabled={saving}>
          {saving ? 'Saving…' : 'Save'}
        </button>
        <button className="btn btn-ghost" onClick={onCancel}>Cancel</button>
      </div>
    </div>
  );
}

// =================== SUBSCRIPTIONS ===================
function SubscriptionsTab() {
  const [data, setData] = useState({ subscriptions: [], total: 0, page: 1, total_pages: 1 });
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(true);

  async function load(page = 1) {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', page);
      if (filter) params.set('status', filter);
      const r = await api.get(`/admin/subscriptions?${params}`);
      setData(r);
    } finally { setLoading(false); }
  }
  useEffect(() => { load(1); /* eslint-disable-next-line */ }, [filter]);

  return (
    <section className="card">
      <div className="filter-bar" style={{ marginBottom: 14 }}>
        <select value={filter} onChange={e => setFilter(e.target.value)} style={{ width: 'auto' }}>
          <option value="">All statuses</option>
          <option value="trial">Trial</option>
          <option value="active">Active</option>
          <option value="expired">Expired</option>
          <option value="cancelled">Cancelled</option>
        </select>
      </div>

      {loading ? <p className="muted">Loading…</p>
       : data.subscriptions.length === 0 ? <p className="muted">No subscriptions.</p>
       : (
        <>
          <table>
            <thead><tr>
              <th>User</th><th>Plan</th><th>Status</th><th>Started</th><th>Expires</th><th>Paid</th>
            </tr></thead>
            <tbody>
              {data.subscriptions.map(s => (
                <tr key={s.id}>
                  <td><a href={`/users/${s.user_id}`}>{s.dial_code}{s.mobile}</a></td>
                  <td>{s.plan_name || (s.is_trial ? 'Trial' : '—')}</td>
                  <td><SubStatusPill s={s}/></td>
                  <td className="muted">{fmtShort(s.starts_at)}</td>
                  <td className="muted">{fmtShort(s.expires_at)}</td>
                  <td>{s.amount_paid != null ? formatPrice(s.amount_paid, 'INR') : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <SimplePagination data={data} onPage={load}/>
        </>
      )}
    </section>
  );
}

function SubStatusPill({ s }) {
  const expired = new Date(s.expires_at) < new Date();
  let kind;
  if (expired) kind = 'pending';
  else if (s.is_trial) kind = 'pending';
  else kind = 'verified';
  const label = expired ? 'expired' : s.status;
  return <span className={`pill pill-${kind}`}>{label}</span>;
}

// =================== PAYMENTS ===================
function PaymentsTab() {
  const [data, setData] = useState({ payments: [], total: 0, page: 1, total_pages: 1 });
  const [loading, setLoading] = useState(true);

  async function load(page = 1) {
    setLoading(true);
    try {
      const r = await api.get(`/admin/payments?page=${page}`);
      setData(r);
    } finally { setLoading(false); }
  }
  useEffect(() => { load(1); }, []);

  return (
    <section className="card">
      {loading ? <p className="muted">Loading…</p>
       : data.payments.length === 0
         ? <p className="muted">No payments yet. Razorpay integration ships in v19.</p>
         : (
        <>
          <table>
            <thead><tr>
              <th>User</th><th>Plan</th><th>Amount</th><th>Status</th><th>Razorpay payment</th><th>When</th>
            </tr></thead>
            <tbody>
              {data.payments.map(p => (
                <tr key={p.id}>
                  <td><a href={`/users/${p.user_id}`}>{p.dial_code}{p.mobile}</a></td>
                  <td>{p.plan_name || '—'}</td>
                  <td>{formatPrice(p.amount, p.currency)}</td>
                  <td><span className={`pill pill-${p.status === 'paid' ? 'verified' : 'pending'}`}>{p.status}</span></td>
                  <td><code>{p.razorpay_payment_id || '—'}</code></td>
                  <td className="muted">{fmtShort(p.created_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <SimplePagination data={data} onPage={load}/>
        </>
      )}
    </section>
  );
}

// =================== GENERAL ===================
function GeneralTab() {
  const [trialDays, setTrialDays] = useState('');
  const [razorpayMode, setRazorpayMode] = useState('test');
  const [keyId, setKeyId]         = useState('');
  const [keySecret, setKeySecret] = useState('');
  const [webhookSec, setWebhookSec] = useState('');
  const [savedFlag, setSavedFlag] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/admin/settings').then(r => {
      setTrialDays(r.settings.trial_days || '7');
      setRazorpayMode(r.settings.razorpay_mode || 'test');
      setKeyId(r.settings.razorpay_key_id || '');
      setKeySecret(r.settings.razorpay_key_secret || '');
      setWebhookSec(r.settings.razorpay_webhook_secret || '');
    }).catch(e => setError(e.message));
  }, []);

  async function save() {
    setError('');
    try {
      await api.put('/admin/settings', {
        trial_days: trialDays,
        razorpay_mode: razorpayMode,
        razorpay_key_id: keyId,
        razorpay_key_secret: keySecret,
        razorpay_webhook_secret: webhookSec
      });
      setSavedFlag(true);
      setTimeout(() => setSavedFlag(false), 3000);
    } catch (e) { setError(e.message); }
  }

  return (
    <>
      {savedFlag && <div className="alert alert-success">✓ Saved</div>}
      {error && <div className="alert alert-error">{error}</div>}

      <section className="card">
        <h2>Free trial</h2>
        <p className="muted" style={{ marginBottom: 12 }}>
          Number of days a brand-new signup gets free. Set to 0 to disable trials.
        </p>
        <label>Trial days</label>
        <input type="number" min="0" max="365" value={trialDays}
          onChange={e => setTrialDays(e.target.value)} style={{ maxWidth: 200 }}/>
      </section>

      <section className="card">
        <h2>Razorpay credentials</h2>
        <p className="muted" style={{ marginBottom: 12 }}>
          Get these from your Razorpay dashboard → Settings → API Keys.
          Webhook secret comes from Razorpay → Settings → Webhooks.
          The Android app calls these via the backend — credentials never leave the server.
        </p>
        <div className="row">
          <div className="col">
            <label>Mode</label>
            <select value={razorpayMode} onChange={e => setRazorpayMode(e.target.value)}>
              <option value="test">Test</option>
              <option value="live">Live</option>
            </select>
          </div>
          <div className="col">
            <label>Key ID</label>
            <input type="text" value={keyId} onChange={e => setKeyId(e.target.value)} placeholder="rzp_test_… or rzp_live_…"/>
          </div>
        </div>
        <label>Key Secret</label>
        <input type="password" value={keySecret} onChange={e => setKeySecret(e.target.value)}/>
        <label>Webhook secret</label>
        <input type="password" value={webhookSec} onChange={e => setWebhookSec(e.target.value)}/>
      </section>

      <button className="btn btn-primary" onClick={save}>Save</button>
    </>
  );
}

// =================== HELPERS ===================
function SimplePagination({ data, onPage }) {
  if (data.total_pages <= 1) return null;
  return (
    <div className="pagination">
      <span className="muted">Page {data.page} of {data.total_pages} · {data.total} total</span>
      <div className="pagination-controls">
        <button className="btn btn-mini btn-ghost" disabled={data.page <= 1} onClick={() => onPage(data.page - 1)}>‹ Prev</button>
        <button className="btn btn-mini btn-ghost" disabled={data.page >= data.total_pages} onClick={() => onPage(data.page + 1)}>Next ›</button>
      </div>
    </div>
  );
}

function formatPrice(paise, currency) {
  if (paise == null) return '—';
  const rupees = paise / 100;
  const sym = currency === 'INR' ? '₹' : (currency === 'USD' ? '$' : (currency || ''));
  return `${sym}${rupees.toLocaleString(undefined, { minimumFractionDigits: rupees % 1 ? 2 : 0 })}`;
}

function fmtShort(ts) {
  if (!ts) return '—';
  const d = new Date(ts);
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function futureDate(daysFromNow) {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  return d.toISOString();
}
