import React, { useState, useEffect } from 'react';
import { api } from '../api.js';

const ROLE_LABELS = {
  super_admin:     { label:'Super Admin',     color:'#a855f7' },
  admin:           { label:'Admin',           color:'#4f8ef7' },
  support:         { label:'Support',         color:'#22c55e' },
  billing:         { label:'Billing',         color:'#f59e0b' },
  global_db_admin: { label:'Global DB Admin', color:'#ef4444' },
  global_db_user:  { label:'Global DB User',  color:'#fb923c' },
};

const inp = {
  padding:'8px 12px', borderRadius:6, border:'1px solid var(--border)',
  background:'var(--surface)', color:'var(--text)', fontSize:14,
  boxSizing:'border-box', width:'100%'
};

function RoleBadge({ role }) {
  const r = ROLE_LABELS[role] || { label: role, color:'#6b7280' };
  return (
    <span style={{ background: r.color + '22', color: r.color,
      borderRadius:4, padding:'2px 8px', fontSize:12, fontWeight:600 }}>
      {r.label}
    </span>
  );
}

export default function AdminUsers({ currentRole }) {
  const [users, setUsers]       = useState([]);
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState('');

  // Add form
  const [addUser, setAddUser]   = useState('');
  const [addPass, setAddPass]   = useState('');
  const [addName, setAddName]   = useState('');
  const [addRole, setAddRole]   = useState('');
  const [addErr, setAddErr]     = useState('');
  const [adding, setAdding]     = useState(false);
  const [showAdd, setShowAdd]   = useState(false);

  // Edit
  const [editId, setEditId]     = useState(null);
  const [editName, setEditName] = useState('');
  const [editPass, setEditPass] = useState('');
  const [editActive, setEditActive] = useState(true);
  const [editRole, setEditRole] = useState('');
  const [editErr, setEditErr]   = useState('');
  const [saving, setSaving]     = useState(false);

  // Available roles for creator
  const creatableRoles = currentRole === 'super_admin'
    ? ['admin','support','billing','global_db_admin','global_db_user']
    : currentRole === 'admin'
    ? ['support','billing','global_db_admin','global_db_user']
    : currentRole === 'global_db_admin'
    ? ['global_db_user']
    : [];

  const load = async () => {
    setLoading(true); setError('');
    try {
      const data = await api.get('/admin/admin-users');
      setUsers(data.users || []);
    } catch (e) { setError(e.message || 'Failed to load'); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const handleAdd = async e => {
    e.preventDefault(); setAddErr('');
    if (!addUser.trim() || !addPass.trim() || !addRole)
      { setAddErr('All fields are required'); return; }
    setAdding(true);
    try {
      await api.post('/admin/admin-users',
        { username: addUser.trim(), password: addPass, display_name: addName.trim(), role: addRole });
      setAddUser(''); setAddPass(''); setAddName(''); setAddRole('');
      setShowAdd(false); load();
    } catch (e) { setAddErr(e.message || 'Failed'); }
    finally { setAdding(false); }
  };

  const startEdit = u => {
    setEditId(u.id); setEditName(u.display_name||'');
    setEditPass(''); setEditActive(u.active); setEditRole(u.role); setEditErr('');
  };

  const saveEdit = async id => {
    setSaving(true); setEditErr('');
    try {
      const body = { display_name: editName, active: editActive, role: editRole };
      if (editPass.trim()) body.password = editPass;
      await api.put(`/admin/admin-users/${id}`, body);
      setEditId(null); load();
    } catch (e) { setEditErr(e.message||'Failed'); }
    finally { setSaving(false); }
  };

  const handleDelete = async (u) => {
    const action = currentRole === 'super_admin' ? 'permanently delete' : 'deactivate';
    if (!confirm(`${action} "${u.username}"?`)) return;
    try { await api.delete(`/admin/admin-users/${u.id}`); load(); }
    catch (e) { alert(e.message); }
  };

  const handleRestore = async id => {
    try { await api.post(`/admin/admin-users/${id}/restore`); load(); }
    catch (e) { alert(e.message); }
  };

  const fmt = d => d ? new Date(d).toLocaleDateString('en-IN',
    { day:'2-digit', month:'short', year:'numeric' }) : '—';

  return (
    <div className="page">
      <div style={{ display:'flex', alignItems:'center', gap:12, marginBottom:24 }}>
        <span style={{ fontSize:28 }}>👥</span>
        <div>
          <h1 style={{ margin:0 }}>Admin Users</h1>
          <p style={{ margin:0, color:'var(--subtext)', fontSize:14 }}>
            Manage admin accounts and their roles.
          </p>
        </div>
        <button onClick={() => setShowAdd(v => !v)}
          style={{ marginLeft:'auto', padding:'8px 18px', borderRadius:6, border:'none',
            background:'var(--accent)', color:'#fff', fontWeight:600, cursor:'pointer' }}>
          {showAdd ? 'Cancel' : '+ New Admin User'}
        </button>
      </div>

      {/* Add form */}
      {showAdd && (
        <div className="card" style={{ marginBottom:20 }}>
          <h3 style={{ margin:'0 0 14px' }}>Create Admin User</h3>
          <form onSubmit={handleAdd}>
            <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr 1fr 1fr auto', gap:12 }}>
              <div>
                <label style={{ fontSize:12, color:'var(--subtext)', display:'block', marginBottom:4 }}>USERNAME *</label>
                <input value={addUser} onChange={e=>setAddUser(e.target.value)}
                  placeholder="jsmith" style={inp}/>
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
                <select value={addRole} onChange={e=>setAddRole(e.target.value)} style={inp}>
                  <option value="">Select role…</option>
                  {creatableRoles.map(r => (
                    <option key={r} value={r}>{ROLE_LABELS[r]?.label || r}</option>
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
            {addErr && <p style={{ color:'var(--reject)', margin:'8px 0 0', fontSize:13 }}>{addErr}</p>}
          </form>
        </div>
      )}

      {error && <p style={{ color:'var(--reject)' }}>{error}</p>}

      {/* Table */}
      <div className="card" style={{ padding:0, overflow:'hidden' }}>
        <table style={{ width:'100%', borderCollapse:'collapse', fontSize:14 }}>
          <thead>
            <tr style={{ borderBottom:'1px solid var(--border)' }}>
              {['User','Display Name','Role','Status','Created','Last Login','Parent','Actions'].map(h=>(
                <th key={h} style={{ padding:'12px 16px', textAlign:'left',
                  fontWeight:600, color:'var(--subtext)', fontSize:12 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td colSpan={8} style={{ padding:24, textAlign:'center', color:'var(--subtext)' }}>Loading…</td></tr>}
            {!loading && !users.length &&
              <tr><td colSpan={8} style={{ padding:32, textAlign:'center', color:'var(--subtext)' }}>
                No admin users yet. Create one above.
              </td></tr>}
            {users.map(u => editId===u.id ? (
              <tr key={u.id} style={{ borderBottom:'1px solid var(--border)', background:'var(--surface)' }}>
                <td style={{ padding:'10px 16px', color:'var(--subtext)', fontFamily:'monospace' }}>{u.username}</td>
                <td style={{ padding:'10px 16px' }}>
                  <input value={editName} onChange={e=>setEditName(e.target.value)} style={{...inp,width:140}}/>
                </td>
                <td style={{ padding:'10px 16px' }}>
                  <select value={editRole} onChange={e=>setEditRole(e.target.value)} style={{...inp,width:160}}>
                    {creatableRoles.map(r=><option key={r} value={r}>{ROLE_LABELS[r]?.label||r}</option>)}
                  </select>
                </td>
                <td style={{ padding:'10px 16px' }}>
                  <select value={editActive} onChange={e=>setEditActive(e.target.value==='true')}
                    style={{...inp,width:100}}>
                    <option value="true">Active</option>
                    <option value="false">Inactive</option>
                  </select>
                </td>
                <td colSpan={3} style={{ padding:'10px 16px' }}>
                  <input type="password" value={editPass} onChange={e=>setEditPass(e.target.value)}
                    placeholder="New password (optional)" style={{...inp,width:180}}/>
                  {editErr && <span style={{ color:'var(--reject)', marginLeft:8, fontSize:12 }}>{editErr}</span>}
                </td>
                <td style={{ padding:'10px 16px' }}>
                  <button onClick={()=>saveEdit(u.id)} disabled={saving}
                    style={{ padding:'5px 14px', borderRadius:4, border:'none',
                      background:'var(--accent)', color:'#fff', cursor:'pointer',
                      fontWeight:600, marginRight:8, fontSize:13 }}>
                    {saving?'…':'Save'}
                  </button>
                  <button onClick={()=>setEditId(null)}
                    style={{ padding:'5px 14px', borderRadius:4,
                      border:'1px solid var(--border)', background:'transparent',
                      color:'var(--text)', cursor:'pointer', fontSize:13 }}>
                    Cancel
                  </button>
                </td>
              </tr>
            ) : (
              <tr key={u.id} style={{
                borderBottom:'1px solid var(--border)',
                opacity: u.deleted_at ? 0.45 : 1
              }}>
                <td style={{ padding:'12px 16px', fontFamily:'monospace', fontWeight:600, color:'var(--text)' }}>
                  {u.username}
                </td>
                <td style={{ padding:'12px 16px', color:'var(--text)' }}>{u.display_name||'—'}</td>
                <td style={{ padding:'12px 16px' }}><RoleBadge role={u.role}/></td>
                <td style={{ padding:'12px 16px' }}>
                  {u.deleted_at ? (
                    <span style={{ color:'#ef4444', fontSize:12, fontWeight:600 }}>Deleted</span>
                  ) : u.active ? (
                    <span style={{ color:'#22c55e', fontSize:12, fontWeight:600 }}>Active</span>
                  ) : (
                    <span style={{ color:'#6b7280', fontSize:12, fontWeight:600 }}>Inactive</span>
                  )}
                </td>
                <td style={{ padding:'12px 16px', color:'var(--subtext)', fontSize:12 }}>{fmt(u.created_at)}</td>
                <td style={{ padding:'12px 16px', color:'var(--subtext)', fontSize:12 }}>{fmt(u.last_login_at)}</td>
                <td style={{ padding:'12px 16px', color:'var(--subtext)', fontSize:12 }}>
                  {u.parent_username || '—'}
                </td>
                <td style={{ padding:'12px 16px' }}>
                  {u.deleted_at ? (
                    currentRole === 'super_admin' && (
                      <button onClick={()=>handleRestore(u.id)}
                        style={{ padding:'4px 12px', borderRadius:4, border:'none',
                          background:'rgba(34,197,94,0.15)', color:'#22c55e',
                          cursor:'pointer', fontWeight:600, fontSize:12 }}>
                        Restore
                      </button>
                    )
                  ) : (
                    <>
                      <button onClick={()=>startEdit(u)}
                        style={{ padding:'4px 12px', borderRadius:4, border:'none',
                          background:'var(--accent)', color:'#fff', cursor:'pointer',
                          fontWeight:600, fontSize:12, marginRight:6 }}>Edit</button>
                      <button onClick={()=>handleDelete(u)}
                        style={{ padding:'4px 12px', borderRadius:4, border:'none',
                          background:'rgba(239,68,68,0.15)', color:'#ef4444',
                          cursor:'pointer', fontWeight:600, fontSize:12 }}>
                        {currentRole==='super_admin' ? 'Delete' : 'Deactivate'}
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Role descriptions */}
      <div className="card" style={{ marginTop:20 }}>
        <h3 style={{ margin:'0 0 14px' }}>Role Permissions</h3>
        <div style={{ display:'grid', gridTemplateColumns:'repeat(3, 1fr)', gap:12 }}>
          {Object.entries(ROLE_LABELS).map(([role, info]) => (
            <div key={role} style={{ background:'var(--surface)', borderRadius:8, padding:12 }}>
              <div style={{ marginBottom:6 }}><RoleBadge role={role}/></div>
              <div style={{ fontSize:12, color:'var(--subtext)', lineHeight:1.5 }}>
                {role === 'super_admin' && 'Full access. Hard delete. Manage all admins.'}
                {role === 'admin' && 'Full access except admin user management.'}
                {role === 'support' && 'View users, contacts, reset PIN, view global blocklist.'}
                {role === 'billing' && 'Plans, subscriptions, payments only.'}
                {role === 'global_db_admin' && 'CRUD global blocklist (own scope). Create/manage Global DB Users. Soft delete only.'}
                {role === 'global_db_user' && 'Add/edit own global blocklist entries only.'}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
