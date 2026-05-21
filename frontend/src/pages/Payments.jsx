import React, { useEffect, useState } from 'react';
import { api } from '../api';

function fmtINR(paise) {
  if (paise == null) return '—';
  return '₹' + (paise / 100).toLocaleString('en-IN', { minimumFractionDigits: 2 });
}

function fmt(iso) {
  if (!iso) return '—';
  try { return new Date(iso).toLocaleString(); } catch { return iso; }
}

const STATUS_FILTERS = [
  { value: '',          label: 'All' },
  { value: 'created',   label: 'Created' },
  { value: 'attempted', label: 'Attempted' },
  { value: 'paid',      label: 'Paid' },
  { value: 'failed',    label: 'Failed' },
  { value: 'cancelled', label: 'Cancelled' },
];

export default function Payments() {
  const [orders, setOrders] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);
  const limit = 50;

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [page, status]);

  function load() {
    setLoading(true);
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('limit', limit);
    if (status) params.set('status', status);
    api.get(`/admin/razorpay/orders?${params.toString()}`)
      .then(r => { setOrders(r.orders || []); setTotal(r.total || 0); })
      .finally(() => setLoading(false));
  }

  const totalPages = Math.max(1, Math.ceil(total / limit));

  return (
    <div>
      <header className="page-header">
        <h1>💳 Razorpay Payments</h1>
        <p className="muted">All Razorpay orders created by sideload app users.</p>
      </header>

      <section className="card">
        <div className="filter-bar" style={{ marginBottom: 14 }}>
          {STATUS_FILTERS.map(f => (
            <button key={f.value}
              className={`tab-btn ${status === f.value ? 'tab-active' : ''}`}
              onClick={() => { setStatus(f.value); setPage(1); }}>
              {f.label}
            </button>
          ))}
        </div>

        {loading ? <p className="muted">Loading…</p>
         : orders.length === 0 ? <p className="muted">No payments yet.</p>
         : (
          <>
            <table>
              <thead>
                <tr>
                  <th>When</th>
                  <th>User</th>
                  <th>Plan</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Order ID</th>
                  <th>Payment ID</th>
                </tr>
              </thead>
              <tbody>
                {orders.map(o => (
                  <tr key={o.id}>
                    <td className="muted">{fmt(o.created_at)}</td>
                    <td>
                      <div>{o.user_name || `${o.dial_code || ''}${o.mobile || ''}`}</div>
                      {o.user_name && <small className="muted">{o.dial_code}{o.mobile}</small>}
                    </td>
                    <td>{o.plan_name || '—'}</td>
                    <td>{fmtINR(o.amount_paise)} {o.currency !== 'INR' ? o.currency : ''}</td>
                    <td>
                      <span className={`pill pill-${
                        o.status === 'paid' ? 'verified' :
                        o.status === 'failed' ? 'pending' : ''
                      }`}>{o.status}</span>
                    </td>
                    <td><code style={{ fontSize: 11 }}>{o.order_id}</code></td>
                    <td><code style={{ fontSize: 11 }}>{o.razorpay_payment_id || '—'}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 16 }}>
              <button disabled={page <= 1} onClick={() => setPage(p => p - 1)}>← Prev</button>
              <span className="muted">Page {page} of {totalPages}  ·  {total} total</span>
              <button disabled={page >= totalPages} onClick={() => setPage(p => p + 1)}>Next →</button>
            </div>
          </>
         )}
      </section>
    </div>
  );
}
