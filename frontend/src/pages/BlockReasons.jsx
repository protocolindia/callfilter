import React, { useEffect, useState } from 'react';
import { api } from '../api.js';

/**
 * Block reasons double as fraud-report categories. Each reason can be enabled
 * for reporting, and given its own recipient emails. The shared templates live
 * under Settings -> Fraud Reports.
 */
export default function BlockReasons() {
  const [reasons, setReasons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState('');
  const [editing, setEditing] = useState(null); // {id,label,report_enabled,emailsText}

  const load = async () => {
    setLoading(true);
    try {
      const r = await api.get('/admin/block-reasons');
      setReasons(r.reasons || []);
    } catch (e) { /* ignore */ }
    finally { setLoading(false); }
  };
  useEffect(() => { load(); }, []);

  const add = async () => {
    const label = adding.trim();
    if (!label) return;
    try { await api.post('/admin/block-reasons', { label }); setAdding(''); load(); }
    catch (e) { alert(e.message); }
  };

  const toggleReport = async (r) => {
    try { await api.put(`/admin/block-reasons/${r.id}`, { report_enabled: !r.report_enabled }); load(); }
    catch (e) { alert(e.message); }
  };

  const remove = async (r) => {
    if (!window.confirm(`Delete reason "${r.label}"?`)) return;
    try { await api.delete(`/admin/block-reasons/${r.id}`); load(); }
    catch (e) { alert(e.message); }
  };

  const startEditEmails = (r) =>
    setEditing({ id: r.id, label: r.label, emailsText: (r.emails || []).join('\n') });

  const saveEmails = async () => {
    const emails = editing.emailsText.split(/[\n,;]+/).map(s => s.trim()).filter(Boolean);
    try { await api.put(`/admin/block-reasons/${editing.id}`, { emails }); setEditing(null); load(); }
    catch (e) { alert(e.message); }
  };

  if (loading) return <div className="card">Loading…</div>;

  return (
    <div>
      <h1 style={{ marginTop: 0 }}>📋 Block Reasons</h1>
      <p className="muted" style={{ marginTop: 0 }}>
        Reasons appear in the app's post-call block picker. Enable "Report" to also use a
        reason as a fraud-report category (with its own recipient emails). Templates are in
        Settings → Fraud Reports.
      </p>

      <div className="card" style={{ marginBottom: 16, display: 'flex', gap: 8 }}>
        <input value={adding} onChange={e => setAdding(e.target.value)}
          placeholder="New reason (e.g. Loan scam)"
          onKeyDown={e => e.key === 'Enter' && add()}
          style={{ flex: 1, padding: '9px 12px', borderRadius: 6,
            border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)' }}/>
        <button className="btn btn-primary" onClick={add}>Add</button>
      </div>

      <table className="data-table">
        <thead><tr><th>Reason</th><th>Report?</th><th>Recipients</th><th></th></tr></thead>
        <tbody>
          {reasons.map(r => (
            <tr key={r.id}>
              <td>{r.label}</td>
              <td>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                  <input type="checkbox" checked={!!r.report_enabled} onChange={() => toggleReport(r)} />
                  {r.report_enabled ? 'Yes' : 'No'}
                </label>
              </td>
              <td>
                {r.report_enabled ? (
                  <button className="btn" onClick={() => startEditEmails(r)}>
                    {(r.emails || []).length} email(s) — edit
                  </button>
                ) : <span className="muted">—</span>}
              </td>
              <td style={{ whiteSpace: 'nowrap' }}>
                <button className="btn" onClick={() => remove(r)}
                  style={{ background: 'rgba(248,113,113,0.15)', color: '#f87171' }}>Delete</button>
              </td>
            </tr>
          ))}
          {reasons.length === 0 && <tr><td colSpan="4" className="muted">No reasons yet.</td></tr>}
        </tbody>
      </table>

      {editing && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20 }}>
          <div className="card" style={{ maxWidth: 520, width: '100%' }}>
            <h2 style={{ marginTop: 0 }}>Recipients for "{editing.label}"</h2>
            <p className="muted" style={{ marginTop: 0 }}>One email per line. A report in this
              category emails all of them (using the shared recipient template).</p>
            <textarea value={editing.emailsText}
              onChange={e => setEditing({ ...editing, emailsText: e.target.value })}
              rows={5} placeholder={"abuse@bank.com\nfraud@police.gov"}
              style={{ width: '100%', padding: 10, borderRadius: 6, fontFamily: 'monospace',
                border: '1px solid var(--border)', background: 'var(--surface)', color: 'var(--text)', boxSizing: 'border-box' }}/>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 14 }}>
              <button className="btn" onClick={() => setEditing(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={saveEmails}>Save</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
