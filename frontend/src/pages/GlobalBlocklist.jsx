import React, { useState, useEffect, useCallback, useRef } from 'react';
import { api, getAdminRole, getAdminMeta } from '../api.js';

const REASONS_PRESET = [
  'Spam call','Cybercrime / fraud','Phishing',
  'Telemarketing / promotional','Robocall / IVR',
  'Personal harassment','Other'
];

const inp = {
  padding:'8px 12px', borderRadius:6,
  border:'1px solid var(--border)',
  background:'var(--surface, #1e293b)',
  color:'var(--text)', fontSize:14,
  boxSizing:'border-box'
};

export default function GlobalBlocklist() {
  const [activeTab, setActiveTab] = useState('list');
  const role = getAdminRole();
  const isSuperAdmin = role === 'super_admin';
  const isGlobalDb = role === 'global_db_admin' || role === 'global_db_user';
  const [assignedReasons, setAssignedReasons] = useState([]);

  // ── List state ────────────────────────────────────────────────────────
  const [entries, setEntries]   = useState([]);
  const [total, setTotal]       = useState(0);
  const [page, setPage]         = useState(1);
  const [reasons, setReasons]   = useState([]);
  const [search, setSearch]     = useState('');
  const [filterReason, setFR]   = useState('');
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState('');
  const [addNum, setAddNum]     = useState('');
  const [addReason, setAddR]    = useState('');
  const [addCustom, setAddCust] = useState('');
  const [addNotes, setAddNotes] = useState('');
  const [addErr, setAddErr]     = useState('');
  const [adding, setAdding]     = useState(false);
  const [editId, setEditId]     = useState(null);
  const [editNum, setEditNum]   = useState('');
  const [editR, setEditR]       = useState('');
  const [editN, setEditN]       = useState('');
  const [editErr, setEditErr]   = useState('');
  const [saving, setSaving]     = useState(false);
  const fileRef                 = useRef(null);
  const [importing, setImp]     = useState(false);
  const [impMsg, setImpMsg]     = useState('');
  const [impErr, setImpErr]     = useState('');
  const limit = 50;

  // ── Settings state ────────────────────────────────────────────────────
  const [cfg, setCfg]           = useState({ global_blocklist_show_total:'true', global_blocklist_show_active:'true' });
  const [cfgSaving, setCfgSav]  = useState(false);
  const [cfgMsg, setCfgMsg]     = useState('');

  // Load settings on settings tab open
  useEffect(() => {
    if (activeTab !== 'settings') return;
    api.get('/admin/settings').then(r => {
      setCfg({
        global_blocklist_show_total:  r.global_blocklist_show_total  ?? 'true',
        global_blocklist_show_active: r.global_blocklist_show_active ?? 'true',
      });
    }).catch(() => {});
  }, [activeTab]);

  const saveCfg = async () => {
    setCfgSav(true); setCfgMsg('');
    try {
      await api.put('/admin/settings', cfg);
      setCfgMsg('✓ Saved');
      setTimeout(() => setCfgMsg(''), 3000);
    } catch (e) { setCfgMsg('Error: ' + (e.message || 'failed')); }
    finally { setCfgSav(false); }
  };

  // ── Load entries ──────────────────────────────────────────────────────
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
    } catch (e) { setError(e.message || 'Failed'); }
    finally { setLoading(false); }
  }, [page, search, filterReason]);

  // Fetch assigned reasons for global_db roles (limits reason dropdown)
  useEffect(() => {
    if (!isGlobalDb) return;
    api.get('/admin/global-db-stats').then(d => {
      if (d.assigned_reasons?.length) setAssignedReasons(d.assigned_reasons);
    }).catch(()=>{});
  }, [isGlobalDb]);

  useEffect(() => { if (activeTab === 'list') load(1); }, [search, filterReason, activeTab]);
  useEffect(() => { if (activeTab === 'list') load(page); }, [page]);

  // ── Add ───────────────────────────────────────────────────────────────
  const handleAdd = async e => {
    e.preventDefault(); setAddErr('');
    const finalR = addReason === '__custom__' ? addCustom.trim() : addReason;
    if (!addNum.trim()) { setAddErr('Number is required'); return; }
    if (!finalR)        { setAddErr('Reason is required'); return; }
    setAdding(true);
    try {
      await api.post('/admin/global-blocklist', { number: addNum.trim(), reason: finalR, notes: addNotes.trim() });
      setAddNum(''); setAddR(''); setAddCust(''); setAddNotes('');
      load(1);
    } catch (e) { setAddErr(e.message || 'Failed'); }
    finally { setAdding(false); }
  };

  const handleDelete = async id => {
    if (!confirm('Delete this entry?')) return;
    try { await api.delete(`/admin/global-blocklist/${id}`); load(page); }
    catch (e) { alert(e.message); }
  };

  const startEdit = r => { setEditId(r.id); setEditNum(r.number); setEditR(r.reason); setEditN(r.notes||''); setEditErr(''); };
  const cancelEdit = () => { setEditId(null); setEditErr(''); };
  const saveEdit = async id => {
    if (!editNum.trim() || !editR.trim()) { setEditErr('Number and reason required'); return; }
    setSaving(true);
    try {
      await api.put(`/admin/global-blocklist/${id}`, { number:editNum.trim(), reason:editR.trim(), notes:editN.trim() });
      setEditId(null); load(page);
    } catch (e) { setEditErr(e.message||'Failed'); }
    finally { setSaving(false); }
  };

  const toggleActive = async entry => {
    try { await api.put(`/admin/global-blocklist/${entry.id}`, { active: !entry.active }); load(page); }
    catch (e) { alert(e.message); }
  };

  // ── CSV import ────────────────────────────────────────────────────────
  const parseCSV = txt => {
    const lines = txt.trim().split(/\r?\n/);
    if (lines.length < 2) return [];
    const hdr = lines[0].split(',').map(h => h.trim().toLowerCase().replace(/['"]/g,''));
    return lines.slice(1).map(line => {
      const fields = []; let cur='', inQ=false;
      for (const ch of line) {
        if (ch==='"') { inQ=!inQ; } else if (ch===','&&!inQ) { fields.push(cur.trim()); cur=''; } else cur+=ch;
      }
      fields.push(cur.trim());
      const obj = {}; hdr.forEach((h,i) => { obj[h]=(fields[i]||'').replace(/^"|"$/g,''); });
      return obj;
    }).filter(r => r.number||r.Number||r.NUMBER);
  };

  const handleImport = async e => {
    const file = e.target.files?.[0]; if (!file) return;
    setImpMsg(''); setImpErr(''); setImp(true);
    try {
      const rows = parseCSV(await file.text());
      if (!rows.length) { setImpErr('No valid rows. Columns: number, reason, notes'); setImp(false); return; }
      const res = await api.post('/admin/global-blocklist/import', { rows });
      setImpMsg(`✓ ${res.inserted} imported, ${res.skipped} skipped`);
      if (res.errors?.length) setImpErr(res.errors.length + ' errors');
      load(1);
    } catch (e) { setImpErr(e.message||'Import failed'); }
    finally { setImp(false); if (fileRef.current) fileRef.current.value=''; }
  };

  const dlTemplate = () => {
    const csv = 'number,reason,notes\n+919876543210,Spam call,Reported\n+919999999999,Phishing,Bank fraud';
    const a = Object.assign(document.createElement('a'),
      { href: URL.createObjectURL(new Blob([csv],{type:'text/csv'})), download:'blocklist_template.csv' });
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
  };

  // For global_db roles, only show their assigned reasons; others see all
  const allReasons = isGlobalDb && assignedReasons.length > 0
    ? assignedReasons
    : [...new Set([...REASONS_PRESET, ...reasons])];
  const totalPages = Math.ceil(total/limit);
  const fmt = d => d ? new Date(d).toLocaleDateString('en-IN',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit'}) : '—';

  const tabStyle = active => ({
    padding:'8px 18px', borderRadius:6, border:'none', cursor:'pointer',
    fontWeight:600, fontSize:13,
    background: active ? 'var(--accent)' : 'var(--surface)',
    color: active ? '#fff' : 'var(--subtext)'
  });

  return (
    <div className="page">
      {/* Header */}
      <div style={{display:'flex',alignItems:'center',gap:12,marginBottom:20}}>
        <span style={{fontSize:28}}>🌐</span>
        <div>
          <h1 style={{margin:0}}>Global Blocklist</h1>
          <p style={{margin:0,color:'var(--subtext)',fontSize:14}}>
            Numbers blocked for all users who enable the matching reason category in their app.
          </p>
        </div>
        <div style={{marginLeft:'auto',background:'var(--card)',borderRadius:8,
          padding:'8px 16px',fontSize:13,color:'var(--subtext)'}}>
          <strong style={{color:'var(--text)',fontSize:18}}>{total}</strong> entries
        </div>
      </div>

      {/* Tab bar */}
      <div style={{display:'flex',gap:8,marginBottom:20}}>
        <button style={tabStyle(activeTab==='list')}    onClick={()=>setActiveTab('list')}>📋 Numbers</button>
        {isSuperAdmin && <button style={tabStyle(activeTab==='settings')} onClick={()=>setActiveTab('settings')}>⚙️ Settings</button>}
      </div>

      {/* ── NUMBERS TAB ─────────────────────────────────────────────── */}
      {activeTab === 'list' && (<>
        {/* Import bar */}
        <div className="card" style={{marginBottom:14}}>
          <div style={{display:'flex',alignItems:'center',gap:12,flexWrap:'wrap'}}>
            <span style={{fontWeight:600,fontSize:14}}>📥 Import CSV</span>
            <span style={{color:'var(--subtext)',fontSize:13}}>
              Columns: <code style={{background:'var(--surface)',padding:'2px 6px',borderRadius:4}}>number</code>,{' '}
              <code style={{background:'var(--surface)',padding:'2px 6px',borderRadius:4}}>reason</code>,{' '}
              <code style={{background:'var(--surface)',padding:'2px 6px',borderRadius:4}}>notes</code>
            </span>
            <div style={{display:'flex',gap:8,marginLeft:'auto'}}>
              <button onClick={dlTemplate}
                style={{...inp,cursor:'pointer',border:'1px solid var(--border)'}}>📄 Template</button>
              <label style={{padding:'8px 14px',borderRadius:6,border:'none',
                background:'var(--accent)',color:'#fff',cursor:importing?'not-allowed':'pointer',
                fontSize:13,fontWeight:600,opacity:importing?0.6:1}}>
                {importing ? 'Importing…' : '⬆ Upload CSV'}
                <input ref={fileRef} type="file" accept=".csv,.txt"
                  onChange={handleImport} style={{display:'none'}} disabled={importing}/>
              </label>
            </div>
          </div>
          {impMsg && <p style={{margin:'8px 0 0',color:'#22c55e',fontSize:13}}>{impMsg}</p>}
          {impErr && <p style={{margin:'4px 0 0',color:'var(--reject)',fontSize:13}}>{impErr}</p>}
        </div>

        {/* Add form */}
        <div className="card" style={{marginBottom:14}}>
          <h3 style={{margin:'0 0 12px'}}>➕ Add Number</h3>
          <form onSubmit={handleAdd}>
            <div style={{display:'grid',gridTemplateColumns:'1fr 1fr 1fr auto',gap:12}}>
              <div>
                <label style={{fontSize:12,color:'var(--subtext)',display:'block',marginBottom:4}}>NUMBER *</label>
                <input value={addNum} onChange={e=>setAddNum(e.target.value)}
                  placeholder="+919876543210" style={{...inp,width:'100%'}}/>
              </div>
              <div>
                <label style={{fontSize:12,color:'var(--subtext)',display:'block',marginBottom:4}}>REASON *</label>
                <select value={addReason} onChange={e=>setAddR(e.target.value)} style={{...inp,width:'100%'}}>
                  <option value="">Select reason…</option>
                  {allReasons.map(r=><option key={r} value={r}>{r}</option>)}
                  <option value="__custom__">+ Custom</option>
                </select>
                {addReason==='__custom__' &&
                  <input value={addCustom} onChange={e=>setAddCust(e.target.value)}
                    placeholder="Custom reason" style={{...inp,width:'100%',marginTop:8}}/>}
              </div>
              <div>
                <label style={{fontSize:12,color:'var(--subtext)',display:'block',marginBottom:4}}>NOTES</label>
                <input value={addNotes} onChange={e=>setAddNotes(e.target.value)}
                  placeholder="Optional" style={{...inp,width:'100%'}}/>
              </div>
              <div style={{display:'flex',alignItems:'flex-end'}}>
                <button type="submit" disabled={adding}
                  style={{padding:'8px 20px',borderRadius:6,border:'none',
                    background:'var(--accent)',color:'#fff',fontWeight:600,
                    cursor:adding?'not-allowed':'pointer',opacity:adding?0.6:1,whiteSpace:'nowrap'}}>
                  {adding?'Adding…':'Add'}
                </button>
              </div>
            </div>
            {addErr && <p style={{color:'var(--reject)',margin:'8px 0 0',fontSize:13}}>{addErr}</p>}
          </form>
        </div>

        {/* Filters */}
        <div style={{display:'flex',gap:12,marginBottom:12}}>
          <input value={search} onChange={e=>setSearch(e.target.value)}
            placeholder="🔍 Search…" style={{...inp,flex:1}}/>
          <select value={filterReason} onChange={e=>setFR(e.target.value)} style={{...inp,minWidth:180}}>
            <option value="">All reasons</option>
            {allReasons.map(r=><option key={r} value={r}>{r}</option>)}
          </select>
        </div>

        {error && <p style={{color:'var(--reject)'}}>{error}</p>}

        {/* Table */}
        <div className="card" style={{padding:0,overflow:'hidden'}}>
          <table style={{width:'100%',borderCollapse:'collapse',fontSize:14}}>
            <thead>
              <tr style={{borderBottom:'1px solid var(--border)'}}>
                {['Number','Reason','Notes','Added By','Date','Active','Actions'].map(h=>(
                  <th key={h} style={{padding:'12px 16px',textAlign:'left',fontWeight:600,
                    color:'var(--subtext)',fontSize:12}}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {loading && <tr><td colSpan={7} style={{padding:24,textAlign:'center',color:'var(--subtext)'}}>Loading…</td></tr>}
              {!loading && !entries.length &&
                <tr><td colSpan={7} style={{padding:32,textAlign:'center',color:'var(--subtext)'}}>
                  No entries yet. Add a number above or import CSV.
                </td></tr>}
              {entries.map(entry => editId===entry.id ? (
                <tr key={entry.id} style={{borderBottom:'1px solid var(--border)',background:'var(--surface)'}}>
                  <td style={{padding:'10px 16px'}}>
                    <input value={editNum} onChange={e=>setEditNum(e.target.value)} style={{...inp,width:155}}/>
                  </td>
                  <td style={{padding:'10px 16px'}}>
                    <input value={editR} onChange={e=>setEditR(e.target.value)}
                      list="edit-r-list" style={{...inp,width:175}}/>
                    <datalist id="edit-r-list">{allReasons.map(r=><option key={r} value={r}/>)}</datalist>
                  </td>
                  <td style={{padding:'10px 16px'}}>
                    <input value={editN} onChange={e=>setEditN(e.target.value)} style={{...inp,width:155}}/>
                  </td>
                  <td colSpan={2} style={{padding:'10px 16px',color:'var(--subtext)',fontSize:12}}>
                    {editErr && <span style={{color:'var(--reject)'}}>{editErr}</span>}
                  </td>
                  <td colSpan={2} style={{padding:'10px 16px'}}>
                    <button onClick={()=>saveEdit(entry.id)} disabled={saving}
                      style={{padding:'5px 14px',borderRadius:4,border:'none',background:'var(--accent)',
                        color:'#fff',cursor:'pointer',fontWeight:600,marginRight:8,fontSize:13}}>
                      {saving?'…':'Save'}
                    </button>
                    <button onClick={cancelEdit}
                      style={{padding:'5px 14px',borderRadius:4,border:'1px solid var(--border)',
                        background:'transparent',color:'var(--text)',cursor:'pointer',fontSize:13}}>
                      Cancel
                    </button>
                  </td>
                </tr>
              ) : (
                <tr key={entry.id} style={{borderBottom:'1px solid var(--border)', opacity: entry.deleted_at ? 0.45 : 1}}>
                  <td style={{padding:'12px 16px',fontFamily:'monospace',fontWeight:600,color:'var(--text)'}}>{entry.number}</td>
                  <td style={{padding:'12px 16px'}}>
                    <span style={{background:'rgba(239,68,68,0.15)',color:'#ef4444',
                      borderRadius:4,padding:'2px 8px',fontSize:12,fontWeight:600}}>{entry.reason}</span>
                  </td>
                  <td style={{padding:'12px 16px',color:'var(--subtext)',fontSize:13}}>{entry.notes||'—'}</td>
                  <td style={{padding:'12px 16px',color:'var(--text)',fontSize:13,fontWeight:500}}>
                    {entry.added_by_display || entry.added_by_username || entry.added_by || '—'}
                  </td>
                  <td style={{padding:'12px 16px'}}>
                    {entry.added_by_role && (
                      <span style={{
                        background: ({super_admin:'#a855f7',admin:'#4f8ef7',global_db_admin:'#ef4444',global_db_user:'#fb923c'}[entry.added_by_role]||'#6b7280')+'22',
                        color:       ({super_admin:'#a855f7',admin:'#4f8ef7',global_db_admin:'#ef4444',global_db_user:'#fb923c'}[entry.added_by_role]||'#6b7280'),
                        borderRadius:4, padding:'2px 6px', fontSize:10, fontWeight:700
                      }}>
                        {({super_admin:'SA',admin:'ADM',support:'SUP',billing:'BIL',global_db_admin:'GDA',global_db_user:'GDU'}[entry.added_by_role]||entry.added_by_role)}
                      </span>
                    )}
                  </td>
                  <td style={{padding:'12px 16px', textAlign:'center'}}>
                    {entry.block_count > 0
                      ? <span style={{background:'rgba(239,68,68,0.15)',color:'#ef4444',
                          borderRadius:12,padding:'2px 10px',fontSize:12,fontWeight:700}}>
                          {entry.block_count}
                        </span>
                      : <span style={{color:'var(--muted)',fontSize:12}}>0</span>}
                  </td>
                  <td style={{padding:'12px 16px', textAlign:'center'}}>
                    {entry.user_count > 0
                      ? <span style={{background:'rgba(79,142,247,0.15)',color:'#4f8ef7',
                          borderRadius:12,padding:'2px 10px',fontSize:12,fontWeight:700}}>
                          {entry.user_count}
                        </span>
                      : <span style={{color:'var(--muted)',fontSize:12}}>0</span>}
                  </td>
                  <td style={{padding:'12px 16px',color:'var(--subtext)',fontSize:12,whiteSpace:'nowrap'}}>{fmt(entry.created_at)}</td>
                  <td style={{padding:'12px 16px'}}>
                    <button onClick={()=>toggleActive(entry)}
                      style={{padding:'3px 12px',borderRadius:12,border:'none',
                        background:entry.active?'rgba(34,197,94,0.15)':'rgba(107,114,128,0.15)',
                        color:entry.active?'#22c55e':'#6b7280',cursor:'pointer',fontWeight:600,fontSize:12}}>
                      {entry.active?'✓ Active':'✗ Off'}
                    </button>
                  </td>
                  <td style={{padding:'12px 16px'}}>
                    <button onClick={()=>startEdit(entry)}
                      style={{padding:'4px 12px',borderRadius:4,border:'none',background:'var(--accent)',
                        color:'#fff',cursor:'pointer',fontWeight:600,fontSize:12,marginRight:8}}>Edit</button>
                    <button onClick={()=>handleDelete(entry.id)}
                      style={{padding:'4px 12px',borderRadius:4,border:'none',
                        background:'rgba(239,68,68,0.15)',color:'#ef4444',cursor:'pointer',fontWeight:600,fontSize:12}}>Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div style={{display:'flex',justifyContent:'center',gap:8,marginTop:16}}>
            {[['«',1],['‹',page-1]].map(([l,p])=>(
              <button key={l} onClick={()=>setPage(Math.max(1,p))} disabled={page<=1}
                style={{padding:'6px 12px',borderRadius:4,border:'1px solid var(--border)',
                  background:'var(--card)',color:'var(--text)',cursor:page<=1?'not-allowed':'pointer',opacity:page<=1?0.4:1}}>{l}</button>
            ))}
            <span style={{padding:'6px 16px',color:'var(--subtext)',fontSize:14}}>
              Page {page} of {totalPages} · {total} entries
            </span>
            {[['›',page+1],['»',totalPages]].map(([l,p])=>(
              <button key={l} onClick={()=>setPage(Math.min(totalPages,p))} disabled={page>=totalPages}
                style={{padding:'6px 12px',borderRadius:4,border:'1px solid var(--border)',
                  background:'var(--card)',color:'var(--text)',cursor:page>=totalPages?'not-allowed':'pointer',opacity:page>=totalPages?0.4:1}}>{l}</button>
            ))}
          </div>
        )}
      </>)}

      {/* ── SETTINGS TAB ────────────────────────────────────────────── */}
      {activeTab === 'settings' && isSuperAdmin && (
        <div>
          <div className="card" style={{marginBottom:16}}>
            <h3 style={{margin:'0 0 6px'}}>App Display Settings</h3>
            <p style={{color:'var(--subtext)',fontSize:13,margin:'0 0 20px'}}>
              Control what information the app shows users on the Global Blocklist screen.
            </p>

            {/* Show total toggle */}
            <div style={{display:'flex',alignItems:'center',justifyContent:'space-between',
              background:'var(--surface)',borderRadius:8,padding:'16px',marginBottom:10}}>
              <div>
                <div style={{fontWeight:600,fontSize:14,color:'var(--text)'}}>Show total number count</div>
                <div style={{fontSize:12,color:'var(--subtext)',marginTop:2}}>
                  App shows "X Total numbers" stat tile on the Global Blocklist screen
                </div>
              </div>
              <div style={{display:'flex',alignItems:'center',gap:12}}>
                <span style={{fontSize:13,color: (cfg.global_blocklist_show_total ?? 'true') === 'true' ? '#22c55e' : '#6b7280',fontWeight:600}}>
                  {(cfg.global_blocklist_show_total ?? 'true') === 'true' ? 'Visible' : 'Hidden'}
                </span>
                <input type="checkbox"
                  checked={(cfg.global_blocklist_show_total ?? 'true') === 'true'}
                  onChange={e => setCfg(p => ({...p, global_blocklist_show_total: e.target.checked ? 'true' : 'false'}))}
                  style={{width:20,height:20,cursor:'pointer'}}/>
              </div>
            </div>

            {/* Show active toggle */}
            <div style={{display:'flex',alignItems:'center',justifyContent:'space-between',
              background:'var(--surface)',borderRadius:8,padding:'16px',marginBottom:20}}>
              <div>
                <div style={{fontWeight:600,fontSize:14,color:'var(--text)'}}>Show currently blocking count</div>
                <div style={{fontSize:12,color:'var(--subtext)',marginTop:2}}>
                  App shows "X Currently blocking" stat tile on the Global Blocklist screen
                </div>
              </div>
              <div style={{display:'flex',alignItems:'center',gap:12}}>
                <span style={{fontSize:13,color: (cfg.global_blocklist_show_active ?? 'true') === 'true' ? '#22c55e' : '#6b7280',fontWeight:600}}>
                  {(cfg.global_blocklist_show_active ?? 'true') === 'true' ? 'Visible' : 'Hidden'}
                </span>
                <input type="checkbox"
                  checked={(cfg.global_blocklist_show_active ?? 'true') === 'true'}
                  onChange={e => setCfg(p => ({...p, global_blocklist_show_active: e.target.checked ? 'true' : 'false'}))}
                  style={{width:20,height:20,cursor:'pointer'}}/>
              </div>
            </div>

            <div style={{display:'flex',alignItems:'center',gap:12}}>
              <button onClick={saveCfg} disabled={cfgSaving}
                style={{padding:'10px 24px',borderRadius:6,border:'none',
                  background:'var(--accent)',color:'#fff',fontWeight:600,
                  cursor:cfgSaving?'not-allowed':'pointer',fontSize:14}}>
                {cfgSaving ? 'Saving…' : 'Save Settings'}
              </button>
              {cfgMsg && <span style={{fontSize:13,color: cfgMsg.startsWith('✓') ? '#22c55e' : 'var(--reject)',fontWeight:600}}>
                {cfgMsg}
              </span>}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
