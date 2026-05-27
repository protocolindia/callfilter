import React, { useState, useEffect, useRef } from 'react';
import { api } from '../api.js';
import { getAdminRole } from '../api.js';

const ROLE_LABELS = {
  super_admin:     { label:'Super Admin',     color:'#a855f7' },
  admin:           { label:'Admin',           color:'#4f8ef7' },
  support:         { label:'Support',         color:'#22c55e' },
  billing:         { label:'Billing',         color:'#f59e0b' },
  global_db_admin: { label:'Global DB Admin', color:'#ef4444' },
  global_db_user:  { label:'Global DB User',  color:'#fb923c' },
};

const DEFAULT_REASONS = [
  'Spam call','Cybercrime / fraud','Phishing',
  'Telemarketing / promotional','Robocall / IVR',
  'Personal harassment','Other'
];

const inp = {
  padding:'8px 12px', borderRadius:6, border:'1px solid var(--border)',
  background:'var(--surface)', color:'var(--text)', fontSize:14, boxSizing:'border-box', width:'100%'
};

function RoleBadge({ role }) {
  const r = ROLE_LABELS[role] || { label:role, color:'#6b7280' };
  return (
    <span style={{ background:r.color+'22', color:r.color,
      borderRadius:4, padding:'2px 8px', fontSize:12, fontWeight:600 }}>
      {r.label}
    </span>
  );
}

export default function AdminUsers() {
  const currentRole = getAdminRole();
  const isSuperAdmin = currentRole === 'super_admin';

  const [users, setUsers]         = useState([]);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState('');
  const [blockReasons, setBlockReasons] = useState(DEFAULT_REASONS);

  // Add form state
  const [showAdd, setShowAdd]     = useState(false);
  const [addUser, setAddUser]     = useState('');
  const [addPass, setAddPass]     = useState('');
  const [addName, setAddName]     = useState('');
  const [addRole, setAddRole]     = useState('');
  const [addSelectedReasons, setAddSelectedReasons] = useState([]);
  const [addErr, setAddErr]       = useState('');
  const [adding, setAdding]       = useState(false);

  // Edit state
  const [editId, setEditId]       = useState(null);
  const [editName, setEditName]   = useState('');
  const [editPass, setEditPass]   = useState('');
  const [editActive, setEditActive] = useState(true);
  const [editRole, setEditRole]   = useState('');
  const [editReasons, setEditReasons] = useState([]);
  const [editErr, setEditErr]     = useState('');
  const [saving, setSaving]       = useState(false);

  // Popup image state
  const [imgAdminId, setImgAdminId]   = useState(null);
  const [imgPreview, setImgPreview]   = useState(null);
  const [imgData, setImgData]         = useState(null);
  const [imgMime, setImgMime]         = useState('');
  const [imgUploading, setImgUploading] = useState(false);
  const [imgMsg, setImgMsg]           = useState('');
  const imgRef = useRef(null);

  const creatableRoles = isSuperAdmin
    ? ['admin','support','billing','global_db_admin','global_db_user']
    : currentRole === 'admin'
    ? ['support','billing','global_db_admin','global_db_user']
    : currentRole === 'global_db_admin'
    ? ['global_db_user'] : [];

  const load = async () => {
    setLoading(true); setError('');
    try {
      const data = await api.get('/admin/admin-users');
      setUsers(data.users || []);
    } catch (e) { setError(e.message||'Failed'); }
    finally { setLoading(false); }
  };

  const loadReasons = async () => {
    try {
      const s = await api.get('/admin/settings');
      if (s.block_reasons) {
        setBlockReasons(s.block_reasons.split('\n').map(r=>r.trim()).filter(Boolean));
      }
    } catch {}
  };

  useEffect(() => { load(); loadReasons(); }, []);

  const toggleReason = (list, setList, reason) => {
    setList(list.includes(reason) ? list.filter(r=>r!==reason) : [...list, reason]);
  };

  // ── Add ───────────────────────────────────────────────────────────────
  const handleAdd = async e => {
    e.preventDefault(); setAddErr('');
    if (!addUser.trim() || !addPass.trim() || !addRole)
      { setAddErr('Username, password and role required'); return; }
    if (addRole === 'global_db_admin' && addSelectedReasons.length === 0)
      { setAddErr('Select at least one reason category for this Global DB Admin'); return; }
    setAdding(true);
    try {
      await api.post('/admin/admin-users',
        { username:addUser.trim(), password:addPass,
          display_name:addName.trim(), role:addRole });
      // If global_db_admin, set assigned reasons
      if (addRole === 'global_db_admin' && addSelectedReasons.length > 0) {
        const newUser = (await api.get('/admin/admin-users')).users
          .find(u => u.username === addUser.trim());
        if (newUser) {
          await api.put(`/admin/admin-users/${newUser.id}/assigned-reasons`,
            { reasons: addSelectedReasons });
        }
      }
      setAddUser(''); setAddPass(''); setAddName(''); setAddRole('');
      setAddSelectedReasons([]); setShowAdd(false); load();
    } catch (e) { setAddErr(e.message||'Failed'); }
    finally { setAdding(false); }
  };

  // ── Edit ─────────────────────────────────────────────────────────────
  const startEdit = u => {
    setEditId(u.id); setEditName(u.display_name||''); setEditPass('');
    setEditActive(u.active); setEditRole(u.role); setEditErr('');
    try { setEditReasons(JSON.parse(u.assigned_reasons||'[]')); } catch { setEditReasons([]); }
  };

  const saveEdit = async id => {
    setSaving(true); setEditErr('');
    try {
      const body = { display_name:editName, active:editActive, role:editRole };
      if (editPass.trim()) body.password = editPass;
      await api.put(`/admin/admin-users/${id}`, body);
      // Update assigned reasons for global_db_admin
      if (editRole === 'global_db_admin') {
        await api.put(`/admin/admin-users/${id}/assigned-reasons`, { reasons: editReasons });
      }
      setEditId(null); load();
    } catch (e) { setEditErr(e.message||'Failed'); }
    finally { setSaving(false); }
  };

  const handleDelete = async u => {
    const action = isSuperAdmin ? 'permanently delete' : 'deactivate';
    if (!confirm(`${action} "${u.username}"?`)) return;
    try { await api.delete(`/admin/admin-users/${u.id}`); load(); }
    catch (e) { alert(e.message); }
  };

  const handleRestore = async id => {
    try { await api.post(`/admin/admin-users/${id}/restore`); load(); }
    catch (e) { alert(e.message); }
  };

  // ── Popup image ───────────────────────────────────────────────────────
  const openImageUpload = u => {
    setImgAdminId(u.id); setImgPreview(null); setImgData(null);
    setImgMsg(''); setImgMime('');
    if (u.popup_image_data) {
      setImgPreview(`data:${u.popup_image_mime||'image/jpeg'};base64,${u.popup_image_data}`);
    }
  };

  const handleImagePick = e => {
    const file = e.target.files?.[0]; if (!file) return;
    const mime = file.type || 'image/jpeg';
    const reader = new FileReader();
    reader.onload = ev => {
      const b64 = ev.target.result.split(',')[1];
      setImgData(b64); setImgMime(mime);
      setImgPreview(ev.target.result);
    };
    reader.readAsDataURL(file);
  };

  const uploadImage = async () => {
    if (!imgData || !imgAdminId) return;
    setImgUploading(true); setImgMsg('');
    try {
      await api.post(`/admin/admin-users/${imgAdminId}/popup-image`,
        { image_data: imgData, mime: imgMime });
      setImgMsg('✓ Image saved');
      load();
    } catch (e) { setImgMsg('Error: ' + (e.message||'Upload failed')); }
    finally { setImgUploading(false); }
  };

  const deleteImage = async () => {
    if (!confirm('Remove popup image?')) return;
    try {
      await api.delete(`/admin/admin-users/${imgAdminId}/popup-image`);
      setImgPreview(null); setImgData(null); setImgMsg('✓ Image removed');
      load();
    } catch (e) { setImgMsg('Error: ' + (e.message||'Failed')); }
  };

  const fmt = d => d ? new Date(d).toLocaleDateString('en-IN',
    { day:'2-digit', month:'short', year:'numeric' }) : '—';

  // ── Reason selector component ─────────────────────────────────────────
  const ReasonPicker = ({ selected, setSelected }) => (
    <div style={{ marginTop:8 }}>
      <div style={{ fontSize:12, color:'var(--subtext)', marginBottom:6 }}>
        ASSIGNED REASON CATEGORIES *
      </div>
      <div style={{ display:'flex', flexWrap:'wrap', gap:6 }}>
        {blockReasons.map(r => {
          const active = selected.includes(r);
          return (
            <button key={r} type="button"
              onClick={() => toggleReason(selected, setSelected, r)}
              style={{ padding:'4px 10px', borderRadius:20, border:'none', cursor:'pointer',
                fontSize:12, fontWeight:600,
                background: active ? 'var(--accent)' : 'var(--surface)',
                color: active ? '#fff' : 'var(--subtext)',
                transition:'all 0.15s' }}>
              {active ? '✓ ' : ''}{r}
            </button>
          );
        })}
      </div>
      {selected.length > 0 && (
        <div style={{ marginTop:6, fontSize:11, color:'var(--subtext)' }}>
          {selected.length} reason{selected.length>1?'s':''} selected
        </div>
      )}
    </div>
  );

  return (
    <div className="page">
      {/* Header */}
      <div style={{ display:'flex', alignItems:'center', gap:12, marginBottom:24 }}>
        <span style={{ fontSize:28 }}>👥</span>
        <div>
          <h1 style={{ margin:0 }}>Admin Users</h1>
          <p style={{ margin:0, color:'var(--subtext)', fontSize:14 }}>
            Manage admin accounts, roles and permissions.
          </p>
        </div>
        {creatableRoles.length > 0 && (
          <button onClick={() => setShowAdd(v=>!v)}
            style={{ marginLeft:'auto', padding:'8px 18px', borderRadius:6, border:'none',
              background:'var(--accent)', color:'#fff', fontWeight:600, cursor:'pointer' }}>
            {showAdd ? 'Cancel' : '+ New Admin User'}
          </button>
        )}
      </div>

      {/* Add form */}
      {showAdd && (
        <div className="card" style={{ marginBottom:20 }}>
          <h3 style={{ margin:'0 0 14px' }}>Create Admin User</h3>
          <form onSubmit={handleAdd}>
            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr 1fr 1fr auto', gap:12 }}>
              <div>
                <label style={{ fontSize:12, color:'var(--subtext)', display:'block', marginBottom:4 }}>USERNAME *</label>
                <input value={addUser} onChange={e=>setAddUser(e.target.value)} placeholder="jsmith" style={inp}/>
              </div>
              <div>
                <label style={{ fontSize:12, color:'var(--subtext)', display:'block', marginBottom:4 }}>PASSWORD *</label>
                <input type="password" value={addPass} onChange={e=>setAddPass(e.target.value)}
                  placeholder="Min 8 chars" style={inp}/>
              </div>
              <div>
                <label style={{ fontSize:12, color:'var(--subtext)', display:'block', marginBottom:4 }}>DISPLAY NAME</label>
                <input value={addName} onChange={e=>setAddName(e.target.value)}
                  placeholder="John Smith" style={inp}/>
              </div>
              <div>
                <label style={{ fontSize:12, color:'var(--subtext)', display:'block', marginBottom:4 }}>ROLE *</label>
                <select value={addRole} onChange={e=>{setAddRole(e.target.value); setAddSelectedReasons([]);}} style={inp}>
                  <option value="">Select role…</option>
                  {creatableRoles.map(r=>(
                    <option key={r} value={r}>{ROLE_LABELS[r]?.label||r}</option>
                  ))}
                </select>
              </div>
              <div style={{ display:'flex', alignItems:'flex-end' }}>
                <button type="submit" disabled={adding}
                  style={{ padding:'8px 20px', borderRadius:6, border:'none',
                    background:'var(--accent)', color:'#fff', fontWeight:600,
                    cursor:adding?'not-allowed':'pointer', opacity:adding?0.6:1 }}>
                  {adding?'Creating…':'Create'}
                </button>
              </div>
            </div>
            {addRole === 'global_db_admin' && (
              <ReasonPicker selected={addSelectedReasons} setSelected={setAddSelectedReasons}/>
            )}
            {addErr && <p style={{ color:'var(--reject)', margin:'8px 0 0', fontSize:13 }}>{addErr}</p>}
          </form>
        </div>
      )}

      {/* Popup image modal */}
      {imgAdminId && (
        <div style={{ position:'fixed', inset:0, background:'rgba(0,0,0,0.7)',
          display:'flex', alignItems:'center', justifyContent:'center', zIndex:1000 }}>
          <div style={{ background:'var(--card)', borderRadius:12, padding:24,
            width:480, maxWidth:'95vw' }}>
            <h3 style={{ margin:'0 0 16px' }}>📷 Popup Image for Blocked Calls</h3>
            <p style={{ color:'var(--subtext)', fontSize:13, margin:'0 0 16px' }}>
              This image appears on the user's phone when a call is blocked by this admin's global blocklist.
              Recommended: 1080 × 600px, max 2MB.
            </p>
            {imgPreview && (
              <img src={imgPreview} alt="Preview"
                style={{ width:'100%', borderRadius:8, marginBottom:12, objectFit:'cover', maxHeight:200 }}/>
            )}
            <div style={{ display:'flex', gap:8, marginBottom:12 }}>
              <label style={{ padding:'8px 16px', borderRadius:6, border:'none',
                background:'var(--accent)', color:'#fff', cursor:'pointer', fontSize:13, fontWeight:600 }}>
                Choose Image
                <input ref={imgRef} type="file" accept="image/jpeg,image/png,image/webp"
                  onChange={handleImagePick} style={{ display:'none' }}/>
              </label>
              {imgData && (
                <button onClick={uploadImage} disabled={imgUploading}
                  style={{ padding:'8px 16px', borderRadius:6, border:'none',
                    background:'#22c55e', color:'#fff', cursor:'pointer', fontSize:13,
                    fontWeight:600, opacity:imgUploading?0.6:1 }}>
                  {imgUploading ? 'Saving…' : '✓ Save Image'}
                </button>
              )}
              {imgPreview && !imgData && (
                <button onClick={deleteImage}
                  style={{ padding:'8px 16px', borderRadius:6, border:'none',
                    background:'rgba(239,68,68,0.15)', color:'#ef4444',
                    cursor:'pointer', fontSize:13, fontWeight:600 }}>
                  🗑 Remove Image
                </button>
              )}
            </div>
            {imgMsg && (
              <p style={{ color:imgMsg.startsWith('✓')?'#22c55e':'var(--reject)',
                fontSize:13, margin:'0 0 12px' }}>{imgMsg}</p>
            )}
            <button onClick={()=>{setImgAdminId(null);setImgMsg('');}}
              style={{ padding:'8px 20px', borderRadius:6, border:'1px solid var(--border)',
                background:'transparent', color:'var(--text)', cursor:'pointer', fontSize:13 }}>
              Close
            </button>
          </div>
        </div>
      )}

      {error && <p style={{ color:'var(--reject)' }}>{error}</p>}

      {/* Table */}
      <div className="card" style={{ padding:0, overflow:'hidden' }}>
        <table style={{ width:'100%', borderCollapse:'collapse', fontSize:14 }}>
          <thead>
            <tr style={{ borderBottom:'1px solid var(--border)' }}>
              {['User','Display Name','Role','Assigned Reasons','Status','Created','Actions'].map(h=>(
                <th key={h} style={{ padding:'12px 16px', textAlign:'left',
                  fontWeight:600, color:'var(--subtext)', fontSize:12 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td colSpan={7} style={{ padding:24, textAlign:'center', color:'var(--subtext)' }}>Loading…</td></tr>}
            {!loading && !users.length &&
              <tr><td colSpan={7} style={{ padding:32, textAlign:'center', color:'var(--subtext)' }}>
                No admin users yet.
              </td></tr>}
            {users.map(u => editId===u.id ? (
              <tr key={u.id} style={{ borderBottom:'1px solid var(--border)', background:'var(--surface)' }}>
                <td style={{ padding:'10px 16px', fontFamily:'monospace', color:'var(--subtext)' }}>{u.username}</td>
                <td style={{ padding:'10px 16px' }}>
                  <input value={editName} onChange={e=>setEditName(e.target.value)} style={{...inp,width:130}}/>
                </td>
                <td style={{ padding:'10px 16px' }}>
                  <select value={editRole} onChange={e=>setEditRole(e.target.value)} style={{...inp,width:150}}>
                    {creatableRoles.map(r=><option key={r} value={r}>{ROLE_LABELS[r]?.label||r}</option>)}
                  </select>
                </td>
                <td style={{ padding:'10px 16px' }}>
                  {editRole === 'global_db_admin' && (
                    <ReasonPicker selected={editReasons} setSelected={setEditReasons}/>
                  )}
                </td>
                <td style={{ padding:'10px 16px' }}>
                  <select value={String(editActive)} onChange={e=>setEditActive(e.target.value==='true')}
                    style={{...inp,width:100}}>
                    <option value="true">Active</option>
                    <option value="false">Inactive</option>
                  </select>
                </td>
                <td style={{ padding:'10px 16px' }}>
                  <input type="password" value={editPass} onChange={e=>setEditPass(e.target.value)}
                    placeholder="New password" style={{...inp,width:150}}/>
                  {editErr && <div style={{ color:'var(--reject)', fontSize:11, marginTop:4 }}>{editErr}</div>}
                </td>
                <td style={{ padding:'10px 16px' }}>
                  <button onClick={()=>saveEdit(u.id)} disabled={saving}
                    style={{ padding:'5px 14px', borderRadius:4, border:'none',
                      background:'var(--accent)', color:'#fff', cursor:'pointer', fontWeight:600,
                      marginRight:6, fontSize:13 }}>{saving?'…':'Save'}</button>
                  <button onClick={()=>setEditId(null)}
                    style={{ padding:'5px 14px', borderRadius:4, border:'1px solid var(--border)',
                      background:'transparent', color:'var(--text)', cursor:'pointer', fontSize:13 }}>Cancel</button>
                </td>
              </tr>
            ) : (
              <tr key={u.id} style={{ borderBottom:'1px solid var(--border)', opacity:u.deleted_at?0.45:1 }}>
                <td style={{ padding:'12px 16px', fontFamily:'monospace', fontWeight:600, color:'var(--text)' }}>
                  {u.username}
                </td>
                <td style={{ padding:'12px 16px', color:'var(--text)' }}>{u.display_name||'—'}</td>
                <td style={{ padding:'12px 16px' }}><RoleBadge role={u.role}/></td>
                <td style={{ padding:'12px 16px' }}>
                  {u.role === 'global_db_admin' && (() => {
                    try {
                      const r = JSON.parse(u.assigned_reasons||'[]');
                      return r.length > 0
                        ? <div style={{ display:'flex', flexWrap:'wrap', gap:4 }}>
                            {r.map(reason => (
                              <span key={reason} style={{ background:'rgba(239,68,68,0.12)',
                                color:'#ef4444', borderRadius:3, padding:'1px 6px', fontSize:11 }}>
                                {reason}
                              </span>
                            ))}
                          </div>
                        : <span style={{ color:'var(--muted)', fontSize:12 }}>None assigned</span>;
                    } catch { return '—'; }
                  })()}
                  {u.role === 'global_db_user' && (
                    <span style={{ color:'var(--subtext)', fontSize:12 }}>Inherits from parent</span>
                  )}
                </td>
                <td style={{ padding:'12px 16px' }}>
                  {u.deleted_at
                    ? <span style={{ color:'#ef4444', fontSize:12, fontWeight:600 }}>Deleted</span>
                    : u.active
                    ? <span style={{ color:'#22c55e', fontSize:12, fontWeight:600 }}>Active</span>
                    : <span style={{ color:'#6b7280', fontSize:12, fontWeight:600 }}>Inactive</span>}
                </td>
                <td style={{ padding:'12px 16px', color:'var(--subtext)', fontSize:12 }}>{fmt(u.created_at)}</td>
                <td style={{ padding:'12px 16px' }}>
                  {u.deleted_at ? (
                    isSuperAdmin && (
                      <button onClick={()=>handleRestore(u.id)}
                        style={{ padding:'4px 10px', borderRadius:4, border:'none',
                          background:'rgba(34,197,94,0.15)', color:'#22c55e',
                          cursor:'pointer', fontWeight:600, fontSize:12 }}>Restore</button>
                    )
                  ) : (
                    <div style={{ display:'flex', gap:6, flexWrap:'wrap' }}>
                      <button onClick={()=>startEdit(u)}
                        style={{ padding:'4px 10px', borderRadius:4, border:'none',
                          background:'var(--accent)', color:'#fff', cursor:'pointer',
                          fontWeight:600, fontSize:12 }}>Edit</button>
                      {isSuperAdmin && u.role === 'global_db_admin' && (
                        <button onClick={()=>openImageUpload(u)}
                          style={{ padding:'4px 10px', borderRadius:4, border:'none',
                            background: u.popup_image_data
                              ? 'rgba(34,197,94,0.15)' : 'rgba(79,142,247,0.15)',
                            color: u.popup_image_data ? '#22c55e' : '#4f8ef7',
                            cursor:'pointer', fontWeight:600, fontSize:12 }}>
                          {u.popup_image_data ? '📷 Image ✓' : '📷 Add Image'}
                        </button>
                      )}
                      <button onClick={()=>handleDelete(u)}
                        style={{ padding:'4px 10px', borderRadius:4, border:'none',
                          background:'rgba(239,68,68,0.15)', color:'#ef4444',
                          cursor:'pointer', fontWeight:600, fontSize:12 }}>
                        {isSuperAdmin ? 'Delete' : 'Deactivate'}
                      </button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
