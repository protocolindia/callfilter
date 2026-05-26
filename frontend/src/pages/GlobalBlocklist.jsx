import React, { useState, useEffect, useCallback, useRef } from 'react';
import { api } from '../api.js';

const REASONS_PRESET = [
  'Spam call','Cybercrime / fraud','Phishing',
  'Telemarketing / promotional','Robocall / IVR',
  'Personal harassment','Other'
];

// Inline style for selects/inputs that works on dark theme
const inputStyle = {
  padding:'8px 12px', borderRadius:6,
  border:'1px solid var(--border)',
  background:'var(--surface)',
  color:'var(--text)', fontSize:14,
  boxSizing:'border-box'
};

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
  const [addNum, setAddNum]         = useState('');
  const [addReason, setAddReason]   = useState('');
  const [addCustomR, setAddCustomR] = useState('');
  const [addNotes, setAddNotes]     = useState('');
  const [addErr, setAddErr]         = useState('');
  const [adding, setAdding]         = useState(false);

  // Edit
  const [editId, setEditId]         = useState(null);
  const [editNum, setEditNum]       = useState('');
  const [editReason, setEditReason] = useState('');
  const [editNotes, setEditNotes]   = useState('');
  const [editErr, setEditErr]       = useState('');
  const [saving, setSaving]         = useState(false);

  // Import
  const fileRef                     = useRef(null);
  const [importing, setImporting]   = useState(false);
  const [importMsg, setImportMsg]   = useState('');
  const [importErr, setImportErr]   = useState('');

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
    } catch (e) { setError(e.message || 'Failed to load'); }
    finally { setLoading(false); }
  }, [page, search, filterReason]);

  useEffect(() => { load(1); }, [search, filterReason]);
  useEffect(() => { load(page); }, [page]);

  // ── Add ─────────────────────────────────────────────────────────────
  const handleAdd = async e => {
    e.preventDefault(); setAddErr('');
    const finalReason = addReason === '__custom__' ? addCustomR.trim() : addReason;
    if (!addNum.trim()) { setAddErr('Number is required'); return; }
    if (!finalReason)   { setAddErr('Reason is required'); return; }
    setAdding(true);
    try {
      await api.post('/admin/global-blocklist', {
        number: addNum.trim(), reason: finalReason, notes: addNotes.trim() });
      setAddNum(''); setAddReason(''); setAddCustomR(''); setAddNotes('');
      load(1);
    } catch (e) { setAddErr(e.message || 'Failed to add'); }
    finally { setAdding(false); }
  };

  // ── Delete ───────────────────────────────────────────────────────────
  const handleDelete = async id => {
    if (!confirm('Delete this entry?')) return;
    try { await api.delete(`/admin/global-blocklist/${id}`); load(page); }
    catch (e) { alert(e.message || 'Delete failed'); }
  };

  // ── Edit ─────────────────────────────────────────────────────────────
  const startEdit = entry => {
    setEditId(entry.id); setEditNum(entry.number);
    setEditReason(entry.reason); setEditNotes(entry.notes || ''); setEditErr('');
  };
  const cancelEdit = () => { setEditId(null); setEditErr(''); };
  const saveEdit = async id => {
    if (!editNum.trim() || !editReason.trim()) { setEditErr('Number and reason required'); return; }
    setSaving(true); setEditErr('');
    try {
      await api.put(`/admin/global-blocklist/${id}`,
        { number: editNum.trim(), reason: editReason.trim(), notes: editNotes.trim() });
      setEditId(null); load(page);
    } catch (e) { setEditErr(e.message || 'Save failed'); }
    finally { setSaving(false); }
  };

  // ── Toggle active ────────────────────────────────────────────────────
  const toggleActive = async entry => {
    try { await api.put(`/admin/global-blocklist/${entry.id}`, { active: !entry.active }); load(page); }
    catch (e) { alert(e.message || 'Failed'); }
  };

  // ── CSV/Excel import ──────────────────────────────────────────────────
  const parseCSV = text => {
    const lines  = text.trim().split(/\r?\n/);
    if (lines.length < 2) return [];
    const header = lines[0].split(',').map(h => h.trim().toLowerCase().replace(/['"]/g,''));
    return lines.slice(1).map(line => {
      // Handle quoted fields
      const fields = [];
      let cur = '', inQ = false;
      for (const ch of line) {
        if (ch === '"') { inQ = !inQ; }
        else if (ch === ',' && !inQ) { fields.push(cur.trim()); cur = ''; }
        else cur += ch;
      }
      fields.push(cur.trim());
      const obj = {};
      header.forEach((h, i) => { obj[h] = (fields[i] || '').replace(/^"|"$/g,''); });
      return obj;
    }).filter(r => r.number || r.Number || r.NUMBER);
  };

  const handleImport = async e => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImportMsg(''); setImportErr(''); setImporting(true);
    try {
      const text = await file.text();
      const rows = parseCSV(text);
      if (rows.length === 0) { setImportErr('No valid rows found. Check column names: number, reason, notes'); setImporting(false); return; }
      const result = await api.post('/admin/global-blocklist/import', { rows });
      setImportMsg(`✓ Imported ${result.inserted} numbers, ${result.skipped} skipped (duplicates/empty)`);
      if (result.errors?.length) setImportErr(`${result.errors.length} errors: ` + result.errors.map(r=>r.number).join(', '));
      load(1);
    } catch (e) { setImportErr(e.message || 'Import failed'); }
    finally { setImporting(false); if (fileRef.current) fileRef.current.value = ''; }
  };

  const downloadTemplate = () => {
    const csv = 'number,reason,notes\n+919876543210,Spam call,Reported multiple times\n+919999999999,Phishing,Bank fraud attempt';
    const blob = new Blob([csv], { type: 'text/csv' });
    const url  = URL.createObjectURL(blob);
    const a    = Object.assign(document.createElement('a'), { href: url, download: 'global_blocklist_template.csv' });
    document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(url);
  };

  const allReasons   = [...new Set([...REASONS_PRESET, ...reasons])];
  const totalPages   = Math.ceil(total / limit);
  const fmtDate      = d => d ? new Date(d).toLocaleDateString('en-IN',
    { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' }) : '—';

  return (
    <div className="page">
      {/* Header */}
      <div style={{ display:'flex', alignItems:'center', gap:12, marginBottom:24 }}>
        <span style={{ fontSize:28 }}>🌐</span>
        <div>
          <h1 style={{ margin:0 }}>Global Blocklist</h1>
          <p style={{ margin:0, color:'var(--subtext)', fontSize:14 }}>
            Numbers blocked for all users who enable the matching reason category in their app.
          </p>
        </div>
        <div style={{ marginLeft:'auto', background:'var(--card)', borderRadius:8,
          padding:'8px 16px', fontSize:13, color:'var(--subtext)' }}>
          <strong style={{ color:'var(--text)', fontSize:18 }}>{total}</strong> entries
        </div>
      </div>

      {/* Import section */}
      <div className="card" style={{ marginBottom:16 }}>
        <div style={{ display:'flex', alignItems:'center', gap:12, flexWrap:'wrap' }}>
          <span style={{ fontWeight:600, fontSize:14 }}>📥 Import CSV</span>
          <span style={{ color:'var(--subtext)', fontSize:13 }}>
            Required columns: <code style={{ background:'var(--surface)', padding:'2px 6px', borderRadius:4 }}>number</code>,{' '}
            <code style={{ background:'var(--surface)', padding:'2px 6px', borderRadius:4 }}>reason</code>,{' '}
            <code style={{ background:'var(--surface)', padding:'2px 6px', borderRadius:4 }}>notes</code> (optional)
          </span>
          <div style={{ display:'flex', gap:8, marginLeft:'auto', alignItems:'center' }}>
            <button onClick={downloadTemplate}
              style={{ padding:'7px 14px', borderRadius:6, border:'1px solid var(--border)',
                background:'var(--surface)', color:'var(--text)', cursor:'pointer', fontSize:13 }}>
              📄 Download template
            </button>
            <label style={{ padding:'7px 14px', borderRadius:6, border:'none',
              background:'var(--accent)', color:'#fff', cursor:'pointer', fontSize:13,
              fontWeight:600, opacity: importing ? 0.6 : 1 }}>
              {importing ? 'Importing…' : '⬆ Upload CSV'}
              <input ref={fileRef} type="file" accept=".csv,.txt" onChange={handleImport}
                style={{ display:'none' }} disabled={importing}/>
            </label>
          </div>
        </div>
        {importMsg && <p style={{ margin:'8px 0 0', color:'var(--accept, #22c55e)', fontSize:13 }}>{importMsg}</p>}
        {importErr && <p style={{ margin:'4px 0 0', color:'var(--reject)', fontSize:13 }}>{importErr}</p>}
      </div>

      {/* Add new entry */}
      <div className="card" style={{ marginBottom:16 }}>
        <h3 style={{ margin:'0 0 14px' }}>➕ Add Number</h3>
        <form onSubmit={handleAdd}>
          <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr 1fr auto', gap:12 }}>
            <div>
              <label style={{ fontSize:12, color:'var(--subtext)', display:'block', marginBottom:4 }}>PHONE NUMBER *</label>
              <input value={addNum} onChange={e => setAddNum(e.target.value)}
                placeholder="+919876543210" style={{ ...inputStyle, width:'100%' }}/>
            </div>
            <div>
              <label style={{ fontSize:12, color:'var(--subtext)', display:'block', marginBottom:4 }}>REASON *</label>
              <select value={addReason} onChange={e => setAddReason(e.target.value)}
                style={{ ...inputStyle, width:'100%' }}>
                <option value="">Select reason…</option>
                {allReasons.map(r => <option key={r} value={r}>{r}</option>)}
                <option value="__custom__">+ Custom reason</option>
              </select>
              {addReason === '__custom__' && (
                <input value={addCustomR} onChange={e => setAddCustomR(e.target.value)}
                  placeholder="Enter custom reason"
                  style={{ ...inputStyle, width:'100%', marginTop:8 }}/>
              )}
            </div>
            <div>
              <label style={{ fontSize:12, color:'var(--subtext)', display:'block', marginBottom:4 }}>NOTES (optional)</label>
              <input value={addNotes} onChange={e => setAddNotes(e.target.value)}
                placeholder="e.g. Reported 12 times" style={{ ...inputStyle, width:'100%' }}/>
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
      <div style={{ display:'flex', gap:12, marginBottom:12 }}>
        <input value={search} onChange={e => setSearch(e.target.value)}
          placeholder="🔍 Search number or notes…"
          style={{ ...inputStyle, flex:1 }}/>
        <select value={filterReason} onChange={e => setFilter(e.target.value)}
          style={{ ...inputStyle, minWidth:180 }}>
          <option value="">All reasons</option>
          {allReasons.map(r => <option key={r} value={r}>{r}</option>)}
        </select>
      </div>

      {error && <p style={{ color:'var(--reject)' }}>{error}</p>}

      {/* Table */}
      <div className="card" style={{ padding:0, overflow:'hidden' }}>
        <table style={{ width:'100%', borderCollapse:'collapse', fontSize:14 }}>
          <thead>
            <tr style={{ borderBottom:'1px solid var(--border)' }}>
              {['Number','Reason','Notes','Added By','Date','Active','Actions'].map(h => (
                <th key={h} style={{ padding:'12px 16px', textAlign:'left',
                  fontWeight:600, color:'var(--subtext)', fontSize:12 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td colSpan={7} style={{ padding:24, textAlign:'center', color:'var(--subtext)' }}>Loading…</td></tr>}
            {!loading && entries.length === 0 && (
              <tr><td colSpan={7} style={{ padding:32, textAlign:'center', color:'var(--subtext)' }}>
                No entries yet. Add a number above or import a CSV.
              </td></tr>
            )}
            {entries.map(entry => (
              editId === entry.id ? (
                <tr key={entry.id} style={{ borderBottom:'1px solid var(--border)', background:'var(--surface)' }}>
                  <td style={{ padding:'10px 16px' }}>
                    <input value={editNum} onChange={e => setEditNum(e.target.value)}
                      style={{ ...inputStyle, width:160 }}/>
                  </td>
                  <td style={{ padding:'10px 16px' }}>
                    <input value={editReason} onChange={e => setEditReason(e.target.value)}
                      list="edit-reason-list" style={{ ...inputStyle, width:180 }}/>
                    <datalist id="edit-reason-list">
                      {allReasons.map(r => <option key={r} value={r}/>)}
                    </datalist>
                  </td>
                  <td style={{ padding:'10px 16px' }}>
                    <input value={editNotes} onChange={e => setEditNotes(e.target.value)}
                      style={{ ...inputStyle, width:160 }}/>
                  </td>
                  <td colSpan={2} style={{ padding:'10px 16px', color:'var(--subtext)', fontSize:12 }}>
                    {editErr && <span style={{ color:'var(--reject)' }}>{editErr}</span>}
                  </td>
                  <td colSpan={2} style={{ padding:'10px 16px' }}>
                    <button onClick={() => saveEdit(entry.id)} disabled={saving}
                      style={{ padding:'5px 14px', borderRadius:4, border:'none',
                        background:'var(--accent)', color:'#fff', cursor:'pointer', fontWeight:600, marginRight:8, fontSize:13 }}>
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
                  <td style={{ padding:'12px 16px', fontFamily:'monospace', fontWeight:600, color:'var(--text)' }}>
                    {entry.number}
                  </td>
                  <td style={{ padding:'12px 16px' }}>
                    <span style={{ background:'rgba(239,68,68,0.15)', color:'#ef4444',
                      borderRadius:4, padding:'2px 8px', fontSize:12, fontWeight:600 }}>
                      {entry.reason}
                    </span>
                  </td>
                  <td style={{ padding:'12px 16px', color:'var(--subtext)', fontSize:13 }}>{entry.notes || '—'}</td>
                  <td style={{ padding:'12px 16px', color:'var(--subtext)', fontSize:13 }}>{entry.added_by}</td>
                  <td style={{ padding:'12px 16px', color:'var(--subtext)', fontSize:12, whiteSpace:'nowrap' }}>{fmtDate(entry.created_at)}</td>
                  <td style={{ padding:'12px 16px' }}>
                    <button onClick={() => toggleActive(entry)}
                      style={{ padding:'3px 12px', borderRadius:12, border:'none',
                        background: entry.active ? 'rgba(34,197,94,0.15)' : 'rgba(107,114,128,0.15)',
                        color: entry.active ? '#22c55e' : '#6b7280',
                        cursor:'pointer', fontWeight:600, fontSize:12 }}>
                      {entry.active ? '✓ Active' : '✗ Off'}
                    </button>
                  </td>
                  <td style={{ padding:'12px 16px' }}>
                    <button onClick={() => startEdit(entry)}
                      style={{ padding:'4px 12px', borderRadius:4, border:'none',
                        background:'var(--accent)', color:'#fff', cursor:'pointer', fontWeight:600, fontSize:12, marginRight:8 }}>
                      Edit
                    </button>
                    <button onClick={() => handleDelete(entry.id)}
                      style={{ padding:'4px 12px', borderRadius:4, border:'none',
                        background:'rgba(239,68,68,0.15)', color:'#ef4444', cursor:'pointer', fontWeight:600, fontSize:12 }}>
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
          {[['«',1],['‹',page-1]].map(([label,p]) => (
            <button key={label} onClick={() => setPage(Math.max(1,p))} disabled={page<=1}
              style={{ padding:'6px 12px', borderRadius:4, border:'1px solid var(--border)',
                background:'var(--card)', color:'var(--text)', cursor: page<=1?'not-allowed':'pointer',
                opacity: page<=1?0.4:1 }}>{label}</button>
          ))}
          <span style={{ padding:'6px 16px', color:'var(--subtext)', fontSize:14 }}>
            Page {page} of {totalPages} · {total} entries
          </span>
          {[['›',page+1],['»',totalPages]].map(([label,p]) => (
            <button key={label} onClick={() => setPage(Math.min(totalPages,p))} disabled={page>=totalPages}
              style={{ padding:'6px 12px', borderRadius:4, border:'1px solid var(--border)',
                background:'var(--card)', color:'var(--text)', cursor: page>=totalPages?'not-allowed':'pointer',
                opacity: page>=totalPages?0.4:1 }}>{label}</button>
          ))}
        </div>
      )}
    </div>
  );
}
