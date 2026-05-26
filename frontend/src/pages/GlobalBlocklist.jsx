import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../api.js';

const REASONS_PRESET = [
  'Spam call', 'Cybercrime / fraud', 'Phishing',
  'Telemarketing / promotional', 'Robocall / IVR',
  'Personal harassment', 'Other'
];

export default function GlobalBlocklist() {
  const [entries, setEntries]     = useState([]);
  const [total, setTotal]         = useState(0);
  const [page, setPage]           = useState(1);
  const [reasons, setReasons]     = useState([]);
  const [search, setSearch]       = useState('');
  const [filterReason, setFilter] = useState('');
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');

  // Add form
  const [addNum, setAddNum]       = useState('');
  const [addReason, setAddReason] = useState('');
  const [addCustomR, setAddCustomR] = useState('');
  const [addNotes, setAddNotes]   = useState('');
  const [addErr, setAddErr]       = useState('');
  const [adding, setAdding]       = useState(false);

  // Edit state
  const [editId, setEditId]       = useState(null);
  const [editNum, setEditNum]     = useState('');
  const [editReason, setEditReason] = useState('');
  const [editNotes, setEditNotes] = useState('');
  const [editErr, setEditErr]     = useState('');
  const [saving, setSaving]       = useState(false);

  const limit = 50;

  const load = useCallback(async (p = page) => {
    setLoading(true); setError('');
    try {
      const params = new URLSearchParams({ page: p, limit });
      if (search) params.set('search', search);
      if (filterReason) params.set('reason', filterReason);
      const data = await api.get(`/admin/global-blocklist?${params}`);
      setEntries(data.entries || []);
      setTotal(data.total || 0);
      setPage(p);
      if (data.reasons?.length) setReasons(data.reasons);
    } catch (e) {
      setError(e.message || 'Failed to load');
    } finally { setLoading(false); }
  }, [page, search, filterReason]);

  useEffect(() => { load(1); }, [search, filterReason]);
  useEffect(() => { load(page); }, [page]);

  const handleAdd = async e => {
    e.preventDefault();
    setAddErr('');
    const finalReason = addReason === '__custom__' ? addCustomR.trim() : addReason;
    if (!addNum.trim()) { setAddErr('Number is required'); return; }
    if (!finalReason)   { setAddErr('Reason is required'); return; }
    setAdding(true);
    try {
      await api.post('/admin/global-blocklist', {
        number: addNum.trim(), reason: finalReason, notes: addNotes.trim() });
      setAddNum(''); setAddReason(''); setAddCustomR(''); setAddNotes('');
      load(1);
    } catch (e) {
      setAddErr(e.message || 'Failed to add');
    } finally { setAdding(false); }
  };

  const handleDelete = async id => {
    if (!confirm('Delete this entry from the global blocklist?')) return;
    try {
      await api.delete(`/admin/global-blocklist/${id}`);
      load(page);
    } catch (e) { alert(e.message || 'Delete failed'); }
  };

  const startEdit = entry => {
    setEditId(entry.id);
    setEditNum(entry.number);
    setEditReason(entry.reason);
    setEditNotes(entry.notes || '');
    setEditErr('');
  };

  const cancelEdit = () => { setEditId(null); setEditErr(''); };

  const saveEdit = async id => {
    if (!editNum.trim() || !editReason.trim()) {
      setEditErr('Number and reason are required'); return;
    }
    setSaving(true); setEditErr('');
    try {
      await api.put(`/admin/global-blocklist/${id}`, {
        number: editNum.trim(), reason: editReason.trim(), notes: editNotes.trim() });
      setEditId(null);
      load(page);
    } catch (e) {
      setEditErr(e.message || 'Save failed');
    } finally { setSaving(false); }
  };

  const toggleActive = async (entry) => {
    try {
      await api.put(`/admin/global-blocklist/${entry.id}`, { active: !entry.active });
      load(page);
    } catch (e) { alert(e.message || 'Failed'); }
  };

  const fmtDate = d => d ? new Date(d).toLocaleDateString('en-IN',
    { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' }) : '—';

  const allReasons = [...new Set([...REASONS_PRESET, ...reasons])];
  const totalPages = Math.ceil(total / limit);

  return (
    <div className="page">
      <div style={{ display:'flex', alignItems:'center', gap:12, marginBottom:24 }}>
        <span style={{ fontSize:28 }}>🌐</span>
        <div>
          <h1 style={{ margin:0 }}>Global Blocklist</h1>
          <p style={{ margin:0, color:'var(--text-muted)', fontSize:14 }}>
            Numbers added here are blocked for all users who enable the matching reason category.
          </p>
        </div>
        <div style={{ marginLeft:'auto', background:'var(--card)', borderRadius:8,
          padding:'8px 16px', fontSize:13, color:'var(--text-muted)' }}>
          <strong style={{ color:'var(--text)', fontSize:18 }}>{total}</strong> total entries
        </div>
      </div>

      {/* Add new entry form */}
      <div className="card" style={{ marginBottom:24 }}>
        <h3 style={{ margin:'0 0 16px' }}>➕ Add Number</h3>
        <form onSubmit={handleAdd}>
          <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr 1fr auto', gap:12 }}>
            <div>
              <label style={{ fontSize:12, color:'var(--text-muted)', display:'block', marginBottom:4 }}>
                PHONE NUMBER *
              </label>
              <input
                value={addNum} onChange={e => setAddNum(e.target.value)}
                placeholder="+919876543210"
                style={{ width:'100%', padding:'8px 12px', borderRadius:6,
                  border:'1px solid var(--border)', background:'var(--input)',
                  color:'var(--text)', fontSize:14, boxSizing:'border-box' }}/>
            </div>
            <div>
              <label style={{ fontSize:12, color:'var(--text-muted)', display:'block', marginBottom:4 }}>
                REASON *
              </label>
              <select
                value={addReason} onChange={e => setAddReason(e.target.value)}
                style={{ width:'100%', padding:'8px 12px', borderRadius:6,
                  border:'1px solid var(--border)', background:'var(--input)',
                  color:'var(--text)', fontSize:14, boxSizing:'border-box' }}>
                <option value="">Select reason…</option>
                {allReasons.map(r => <option key={r} value={r}>{r}</option>)}
                <option value="__custom__">+ Custom reason</option>
              </select>
              {addReason === '__custom__' && (
                <input
                  value={addCustomR} onChange={e => setAddCustomR(e.target.value)}
                  placeholder="Enter custom reason"
                  style={{ width:'100%', padding:'8px 12px', borderRadius:6, marginTop:8,
                    border:'1px solid var(--border)', background:'var(--input)',
                    color:'var(--text)', fontSize:14, boxSizing:'border-box' }}/>
              )}
            </div>
            <div>
              <label style={{ fontSize:12, color:'var(--text-muted)', display:'block', marginBottom:4 }}>
                NOTES (optional)
              </label>
              <input
                value={addNotes} onChange={e => setAddNotes(e.target.value)}
                placeholder="e.g. Reported 12 times"
                style={{ width:'100%', padding:'8px 12px', borderRadius:6,
                  border:'1px solid var(--border)', background:'var(--input)',
                  color:'var(--text)', fontSize:14, boxSizing:'border-box' }}/>
            </div>
            <div style={{ display:'flex', alignItems:'flex-end' }}>
              <button type="submit" disabled={adding}
                style={{ padding:'8px 20px', borderRadius:6, border:'none',
                  background:'var(--accent)', color:'#fff', fontWeight:600,
                  cursor: adding ? 'not-allowed' : 'pointer', fontSize:14,
                  opacity: adding ? 0.6 : 1, whiteSpace:'nowrap' }}>
                {adding ? 'Adding…' : 'Add'}
              </button>
            </div>
          </div>
          {addErr && <p style={{ color:'var(--reject)', margin:'8px 0 0', fontSize:13 }}>{addErr}</p>}
        </form>
      </div>

      {/* Filters */}
      <div style={{ display:'flex', gap:12, marginBottom:16 }}>
        <input
          value={search} onChange={e => setSearch(e.target.value)}
          placeholder="🔍 Search number or notes…"
          style={{ flex:1, padding:'8px 14px', borderRadius:6,
            border:'1px solid var(--border)', background:'var(--input)',
            color:'var(--text)', fontSize:14 }}/>
        <select
          value={filterReason} onChange={e => setFilter(e.target.value)}
          style={{ padding:'8px 14px', borderRadius:6, border:'1px solid var(--border)',
            background:'var(--input)', color:'var(--text)', fontSize:14 }}>
          <option value="">All reasons</option>
          {allReasons.map(r => <option key={r} value={r}>{r}</option>)}
        </select>
      </div>

      {error && <p style={{ color:'var(--reject)' }}>{error}</p>}

      {/* Table */}
      <div className="card" style={{ padding:0, overflow:'hidden' }}>
        <table style={{ width:'100%', borderCollapse:'collapse', fontSize:14 }}>
          <thead>
            <tr style={{ borderBottom:'1px solid var(--border)',
              background:'var(--card-header, var(--card))' }}>
              {['Number','Reason','Notes','Added By','Added','Active','Actions'].map(h => (
                <th key={h} style={{ padding:'12px 16px', textAlign:'left',
                  fontWeight:600, color:'var(--text-muted)', fontSize:12,
                  whiteSpace:'nowrap' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={7} style={{ padding:24, textAlign:'center',
                color:'var(--text-muted)' }}>Loading…</td></tr>
            )}
            {!loading && entries.length === 0 && (
              <tr><td colSpan={7} style={{ padding:32, textAlign:'center',
                color:'var(--text-muted)' }}>
                No entries yet. Add a number above to get started.
              </td></tr>
            )}
            {entries.map(entry => (
              editId === entry.id ? (
                <tr key={entry.id} style={{ borderBottom:'1px solid var(--border)',
                  background:'var(--input)' }}>
                  <td style={{ padding:'10px 16px' }}>
                    <input value={editNum} onChange={e => setEditNum(e.target.value)}
                      style={{ padding:'6px 10px', borderRadius:4, border:'1px solid var(--border)',
                        background:'var(--bg)', color:'var(--text)', fontSize:14, width:160 }}/>
                  </td>
                  <td style={{ padding:'10px 16px' }}>
                    <input value={editReason} onChange={e => setEditReason(e.target.value)}
                      list="reason-list"
                      style={{ padding:'6px 10px', borderRadius:4, border:'1px solid var(--border)',
                        background:'var(--bg)', color:'var(--text)', fontSize:14, width:180 }}/>
                    <datalist id="reason-list">
                      {allReasons.map(r => <option key={r} value={r}/>)}
                    </datalist>
                  </td>
                  <td style={{ padding:'10px 16px' }}>
                    <input value={editNotes} onChange={e => setEditNotes(e.target.value)}
                      style={{ padding:'6px 10px', borderRadius:4, border:'1px solid var(--border)',
                        background:'var(--bg)', color:'var(--text)', fontSize:14, width:160 }}/>
                  </td>
                  <td colSpan={2} style={{ padding:'10px 16px', color:'var(--text-muted)' }}>
                    {editErr && <span style={{ color:'var(--reject)', marginRight:8 }}>{editErr}</span>}
                  </td>
                  <td colSpan={2} style={{ padding:'10px 16px' }}>
                    <button onClick={() => saveEdit(entry.id)} disabled={saving}
                      style={{ padding:'5px 14px', borderRadius:4, border:'none',
                        background:'var(--accent)', color:'#fff', cursor:'pointer',
                        fontWeight:600, marginRight:8, fontSize:13 }}>
                      {saving ? '…' : 'Save'}
                    </button>
                    <button onClick={cancelEdit}
                      style={{ padding:'5px 14px', borderRadius:4, border:'1px solid var(--border)',
                        background:'transparent', color:'var(--text)', cursor:'pointer', fontSize:13 }}>
                      Cancel
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={entry.id} style={{ borderBottom:'1px solid var(--border)' }}>
                  <td style={{ padding:'12px 16px', fontFamily:'monospace',
                    fontWeight:600, color:'var(--text)' }}>{entry.number}</td>
                  <td style={{ padding:'12px 16px' }}>
                    <span style={{ background:'#ef444422', color:'#ef4444',
                      borderRadius:4, padding:'2px 8px', fontSize:12, fontWeight:600 }}>
                      {entry.reason}
                    </span>
                  </td>
                  <td style={{ padding:'12px 16px', color:'var(--text-muted)',
                    fontSize:13 }}>{entry.notes || '—'}</td>
                  <td style={{ padding:'12px 16px', color:'var(--text-muted)',
                    fontSize:13 }}>{entry.added_by}</td>
                  <td style={{ padding:'12px 16px', color:'var(--text-muted)',
                    fontSize:12, whiteSpace:'nowrap' }}>{fmtDate(entry.created_at)}</td>
                  <td style={{ padding:'12px 16px' }}>
                    <button onClick={() => toggleActive(entry)}
                      style={{ padding:'3px 12px', borderRadius:12, border:'none',
                        background: entry.active ? '#22c55e22' : '#6b728022',
                        color: entry.active ? '#22c55e' : '#6b7280',
                        cursor:'pointer', fontWeight:600, fontSize:12 }}>
                      {entry.active ? '✓ Active' : '✗ Inactive'}
                    </button>
                  </td>
                  <td style={{ padding:'12px 16px' }}>
                    <button onClick={() => startEdit(entry)}
                      style={{ padding:'4px 12px', borderRadius:4, border:'none',
                        background:'var(--accent)', color:'#fff', cursor:'pointer',
                        fontWeight:600, fontSize:12, marginRight:8 }}>
                      Edit
                    </button>
                    <button onClick={() => handleDelete(entry.id)}
                      style={{ padding:'4px 12px', borderRadius:4, border:'none',
                        background:'#ef444422', color:'#ef4444', cursor:'pointer',
                        fontWeight:600, fontSize:12 }}>
                      Delete
                    </button>
                  </td>
                </tr>
              )
            ))}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div style={{ display:'flex', justifyContent:'center', gap:8, marginTop:16 }}>
          <button onClick={() => setPage(1)} disabled={page <= 1}
            style={{ padding:'6px 12px', borderRadius:4, border:'1px solid var(--border)',
              background:'var(--card)', color:'var(--text)', cursor: page <= 1 ? 'not-allowed' : 'pointer',
              opacity: page <= 1 ? 0.4 : 1 }}>«</button>
          <button onClick={() => setPage(p => Math.max(1, p-1))} disabled={page <= 1}
            style={{ padding:'6px 12px', borderRadius:4, border:'1px solid var(--border)',
              background:'var(--card)', color:'var(--text)', cursor: page <= 1 ? 'not-allowed' : 'pointer',
              opacity: page <= 1 ? 0.4 : 1 }}>‹ Prev</button>
          <span style={{ padding:'6px 16px', color:'var(--text-muted)', fontSize:14 }}>
            Page {page} of {totalPages} &nbsp;·&nbsp; {total} entries
          </span>
          <button onClick={() => setPage(p => Math.min(totalPages, p+1))} disabled={page >= totalPages}
            style={{ padding:'6px 12px', borderRadius:4, border:'1px solid var(--border)',
              background:'var(--card)', color:'var(--text)', cursor: page >= totalPages ? 'not-allowed' : 'pointer',
              opacity: page >= totalPages ? 0.4 : 1 }}>Next ›</button>
          <button onClick={() => setPage(totalPages)} disabled={page >= totalPages}
            style={{ padding:'6px 12px', borderRadius:4, border:'1px solid var(--border)',
              background:'var(--card)', color:'var(--text)', cursor: page >= totalPages ? 'not-allowed' : 'pointer',
              opacity: page >= totalPages ? 0.4 : 1 }}>»</button>
        </div>
      )}
    </div>
  );
}
