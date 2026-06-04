import React, { useState, useEffect, useRef } from 'react';
import { api, getAdminRole } from '../api.js';

const ROLE_LABELS = {
  super_admin:     { label: 'Super Admin',     color: '#a855f7' },
  admin:           { label: 'Admin',           color: '#4f8ef7' },
  support:         { label: 'Support',         color: '#22c55e' },
  billing:         { label: 'Billing',         color: '#f59e0b' },
  global_db_admin: { label: 'Global DB Admin', color: '#ef4444' },
  global_db_user:  { label: 'Global DB User',  color: '#fb923c' },
};

const DEFAULT_REASONS = [
  'Spam call', 'Cybercrime / fraud', 'Phishing',
  'Telemarketing / promotional', 'Robocall / IVR',
  'Personal harassment', 'Other',
];

const inp = {
  padding: '8px 12px', borderRadius: 6, border: '1px solid var(--border)',
  background: 'var(--surface)', color: 'var(--text)', fontSize: 14,
  boxSizing: 'border-box', width: '100%',
};

function btn(bg, color, extra) {
  return {
    padding: '5px 12px', borderRadius: 4, border: 'none',
    background: bg, color: color, cursor: 'pointer',
    fontWeight: 600, fontSize: 12, ...extra,
  };
}

function RoleBadge({ role }) {
  const r = ROLE_LABELS[role] || { label: role, color: '#6b7280' };
  return (
    <span style={{
      background: r.color + '22', color: r.color,
      borderRadius: 4, padding: '2px 8px', fontSize: 12, fontWeight: 600,
    }}>
      {r.label}
    </span>
  );
}

function parseReasons(json) {
  try { return JSON.parse(json || '[]'); }
  catch { return []; }
}

function ReasonPicker({ selected, onChange, allReasons }) {
  return (
    <div style={{ marginTop: 8 }}>
      <div style={{ fontSize: 12, color: 'var(--subtext)', marginBottom: 6 }}>
        ASSIGNED REASON CATEGORIES *
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
        {allReasons.map(r => {
          const active = selected.includes(r);
          return (
            <button key={r} type="button"
              onClick={() => onChange(active ? selected.filter(x => x !== r) : [...selected, r])}
              style={{
                padding: '4px 10px', borderRadius: 20, border: 'none', cursor: 'pointer',
                fontSize: 12, fontWeight: 600,
                background: active ? 'var(--accent)' : 'var(--surface)',
                color: active ? '#fff' : 'var(--subtext)',
              }}>
              {active ? '+ ' : ''}{r}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export default function AdminUsers() {
  const currentRole = getAdminRole();
  const isSuperAdmin = currentRole === 'super_admin';
  const canCreate = ['super_admin', 'admin', 'global_db_admin'].includes(currentRole);

  const [users, setUsers]       = useState([]);
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState('');
  const [reasons, setReasons]   = useState(DEFAULT_REASONS);

  // Add form
  const [showAdd, setShowAdd]       = useState(false);
  const [addUser, setAddUser]       = useState('');
  const [addPass, setAddPass]       = useState('');
  const [addName, setAddName]       = useState('');
  const [addRole, setAddRole]       = useState('');
  const [addReasons, setAddReasons] = useState([]);
  const [addErr, setAddErr]         = useState('');
  const [adding, setAdding]         = useState(false);

  // Edit form
  const [editId, setEditId]           = useState(null);
  const [editName, setEditName]       = useState('');
  const [editPass, setEditPass]       = useState('');
  const [editActive, setEditActive]   = useState(true);
  const [editRole, setEditRole]       = useState('');
  const [editReasons, setEditReasons] = useState([]);
  const [editErr, setEditErr]         = useState('');
  const [saving, setSaving]           = useState(false);

  // Image upload
  const [imgAdminId, setImgAdminId]   = useState(null);
  const [imgPreview, setImgPreview]   = useState(null);
  const [imgData, setImgData]         = useState(null);
  const [imgMime, setImgMime]         = useState('');
  const [imgUploading, setImgUploading] = useState(false);
  const [imgMsg, setImgMsg]           = useState('');
  const imgRef = useRef(null);

  // Full-screen preview
  const [viewImg, setViewImg] = useState(null);

  // Reset password modal
  const [resetId, setResetId]       = useState(null);
  const [resetUser, setResetUser]   = useState('');
  const [resetPw, setResetPw]       = useState('');
  const [resetPw2, setResetPw2]     = useState('');
  const [resetMsg, setResetMsg]     = useState('');
  const [resetting, setResetting]   = useState(false);

  const creatableRoles = isSuperAdmin
    ? ['super_admin', 'admin', 'support', 'billing', 'global_db_admin', 'global_db_user']
    : currentRole === 'admin'
    ? ['support', 'billing', 'global_db_admin', 'global_db_user']
    : currentRole === 'global_db_admin'
    ? ['global_db_user']
    : [];

  const load = async () => {
    setLoading(true); setError('');
    try {
      const data = await api.get('/admin/admin-users');
      setUsers(data.users || []);
    } catch (e) { setError(e.message || 'Failed to load admin users'); }
    finally { setLoading(false); }
  };

  const loadReasons = async () => {
    try {
      const s = await api.get('/admin/settings');
      if (s && s.block_reasons) {
        setReasons(s.block_reasons.split('\n').map(r => r.trim()).filter(Boolean));
      }
    } catch { /* use defaults */ }
  };

  useEffect(() => { load(); loadReasons(); }, []);

  const handleAdd = async e => {
    e.preventDefault(); setAddErr('');
    if (!addUser.trim() || !addPass.trim() || !addRole) {
      setAddErr('Username, password and role are required'); return;
    }
    if (addRole === 'global_db_admin' && addReasons.length === 0) {
      setAddErr('Select at least one reason category for Global DB Admin'); return;
    }
    setAdding(true);
    try {
      const res = await api.post('/admin/admin-users', {
        username: addUser.trim(), password: addPass,
        display_name: addName.trim(), role: addRole,
      });
      if (addRole === 'global_db_admin' && addReasons.length > 0 && res?.user?.id) {
        await api.put(`/admin/admin-users/${res.user.id}/assigned-reasons`,
          { reasons: addReasons });
      }
      setAddUser(''); setAddPass(''); setAddName('');
      setAddRole(''); setAddReasons([]); setShowAdd(false);
      load();
    } catch (e) { setAddErr(e.message || 'Failed to create'); }
    finally { setAdding(false); }
  };

  const startEdit = u => {
    setEditId(u.id); setEditName(u.display_name || '');
    setEditPass(''); setEditActive(u.active);
    setEditRole(u.role); setEditReasons(parseReasons(u.assigned_reasons));
    setEditErr('');
  };

  const saveEdit = async id => {
    setSaving(true); setEditErr('');
    try {
      const body = { display_name: editName, active: editActive, role: editRole };
      if (editPass.trim()) body.password = editPass;
      await api.put(`/admin/admin-users/${id}`, body);
      if (editRole === 'global_db_admin') {
        await api.put(`/admin/admin-users/${id}/assigned-reasons`, { reasons: editReasons });
      }
      setEditId(null); load();
    } catch (e) { setEditErr(e.message || 'Failed'); }
    finally { setSaving(false); }
  };

  const handleDelete = async u => {
    const label = isSuperAdmin ? 'permanently delete' : 'deactivate';
    if (!window.confirm(`${label} "${u.username}"?`)) return;
    try { await api.delete(`/admin/admin-users/${u.id}`); load(); }
    catch (e) { alert(e.message); }
  };

  const handleRestore = async id => {
    try { await api.post(`/admin/admin-users/${id}/restore`); load(); }
    catch (e) { alert(e.message); }
  };

  const handleResetPassword = async () => {
    if (!resetPw.trim()) { setResetMsg('Enter a new password'); return; }
    if (resetPw.length < 6) { setResetMsg('Password must be at least 6 characters'); return; }
    if (resetPw !== resetPw2) { setResetMsg('Passwords do not match'); return; }
    setResetting(true); setResetMsg('');
    try {
      await api.put(`/admin/admin-users/${resetId}`, { password: resetPw });
      setResetMsg('ok');
      setTimeout(() => { setResetId(null); setResetPw(''); setResetPw2(''); setResetMsg(''); }, 1500);
    } catch (e) { setResetMsg(e.message || 'Failed'); }
    finally { setResetting(false); }
  };

  const openImageUpload = u => {
    setImgAdminId(u.id); setImgData(null); setImgMsg('');
    setImgPreview(u.popup_image_data
      ? `data:${u.popup_image_mime || 'image/jpeg'};base64,${u.popup_image_data}`
      : null);
  };

  const handleImagePick = e => {
    const file = e.target.files?.[0]; if (!file) return;
    const mime = file.type || 'image/jpeg';
    const reader = new FileReader();
    reader.onload = ev => {
      const b64 = ev.target.result.split(',')[1];
      setImgData(b64); setImgMime(mime); setImgPreview(ev.target.result);
    };
    reader.readAsDataURL(file);
  };

  const uploadImage = async () => {
    if (!imgData || !imgAdminId) return;
    setImgUploading(true); setImgMsg('');
    try {
      await api.post(`/admin/admin-users/${imgAdminId}/popup-image`,
        { image_data: imgData, mime: imgMime });
      setImgMsg('Image saved');
      load();
    } catch (e) { setImgMsg('Error: ' + (e.message || 'Upload failed')); }
    finally { setImgUploading(false); }
  };

  const deleteImage = async () => {
    if (!window.confirm('Remove popup image?')) return;
    try {
      await api.delete(`/admin/admin-users/${imgAdminId}/popup-image`);
      setImgPreview(null); setImgData(null); setImgMsg('Image removed');
      load();
    } catch (e) { setImgMsg('Error: ' + (e.message || 'Failed')); }
  };

  const fmt = d => d ? new Date(d).toLocaleDateString('en-IN',
    { day: '2-digit', month: 'short', year: 'numeric' }) : '-';

  return (
    <div className="page">

      {/* Full-screen image preview */}
      {viewImg && (
        <div onClick={() => setViewImg(null)} style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.88)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 3000,
        }}>
          <div style={{ position: 'relative' }}>
            <img src={viewImg} alt="Popup preview" style={{
              maxWidth: '90vw', maxHeight: '85vh', borderRadius: 8, display: 'block',
            }}/>
            <button onClick={() => setViewImg(null)} style={{
              position: 'absolute', top: -14, right: -14,
              background: '#ef4444', color: '#fff', border: 'none',
              borderRadius: '50%', width: 30, height: 30, cursor: 'pointer',
              fontWeight: 700, fontSize: 18,
            }}>x</button>
          </div>
        </div>
      )}

      {/* Image upload modal */}
      {imgAdminId && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000,
        }}>
          <div style={{
            background: 'var(--card)', borderRadius: 12, padding: 24,
            width: 480, maxWidth: '95vw',
          }}>
            <h3 style={{ margin: '0 0 12px' }}>Popup Image for Blocked Calls</h3>
            <p style={{ color: 'var(--subtext)', fontSize: 13, margin: '0 0 14px' }}>
              Shown when a call is blocked via this admin's global blocklist.
              Recommended: 1080x600px, max 2MB.
            </p>
            {imgPreview && (
              <img src={imgPreview} alt="Preview" style={{
                width: '100%', borderRadius: 8, marginBottom: 12,
                objectFit: 'cover', maxHeight: 200, display: 'block',
              }}/>
            )}
            <div style={{ display: 'flex', gap: 8, marginBottom: 12, flexWrap: 'wrap' }}>
              <label style={{
                padding: '8px 14px', borderRadius: 6, border: 'none',
                background: 'var(--accent)', color: '#fff',
                cursor: 'pointer', fontSize: 13, fontWeight: 600,
              }}>
                Choose Image
                <input ref={imgRef} type="file" accept="image/jpeg,image/png,image/webp"
                  onChange={handleImagePick} style={{ display: 'none' }}/>
              </label>
              {imgData && (
                <button onClick={uploadImage} disabled={imgUploading} style={{
                  padding: '8px 14px', borderRadius: 6, border: 'none',
                  background: '#22c55e', color: '#fff', cursor: 'pointer',
                  fontSize: 13, fontWeight: 600, opacity: imgUploading ? 0.6 : 1,
                }}>
                  {imgUploading ? 'Saving...' : 'Save Image'}
                </button>
              )}
              {imgPreview && !imgData && (
                <button onClick={deleteImage} style={{
                  padding: '8px 14px', borderRadius: 6, border: 'none',
                  background: 'rgba(239,68,68,0.15)', color: '#ef4444',
                  cursor: 'pointer', fontSize: 13, fontWeight: 600,
                }}>
                  Remove
                </button>
              )}
            </div>
            {imgMsg && (
              <p style={{
                color: imgMsg.startsWith('Error') ? 'var(--reject)' : '#22c55e',
                fontSize: 13, margin: '0 0 12px',
              }}>
                {imgMsg}
              </p>
            )}
            <button onClick={() => { setImgAdminId(null); setImgMsg(''); }} style={{
              padding: '8px 20px', borderRadius: 6, border: '1px solid var(--border)',
              background: 'transparent', color: 'var(--text)', cursor: 'pointer', fontSize: 13,
            }}>
              Close
            </button>
          </div>
        </div>
      )}

      {/* Reset password modal */}
      {resetId && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
          <div style={{ background: 'var(--card)', borderRadius: 12, padding: 28,
            width: 420, maxWidth: '95vw' }}>
            <h3 style={{ margin: '0 0 6px' }}>Reset Password</h3>
            <p style={{ color: 'var(--subtext)', fontSize: 13, margin: '0 0 20px' }}>
              Set a new password for <strong style={{ color: 'var(--text)' }}>{resetUser}</strong>
            </p>

            {resetMsg === 'ok' ? (
              <div style={{ padding: '12px 16px', borderRadius: 8,
                background: 'rgba(34,197,94,0.15)', color: '#22c55e',
                fontSize: 14, marginBottom: 16 }}>
                Password updated successfully
              </div>
            ) : (
              <>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 16 }}>
                  <div>
                    <label style={{ fontSize: 11, fontWeight: 700, color: 'var(--subtext)',
                      textTransform: 'uppercase', display: 'block', marginBottom: 4 }}>
                      New Password
                    </label>
                    <input type="password" value={resetPw}
                      onChange={e => setResetPw(e.target.value)}
                      placeholder="Min 6 characters"
                      autoFocus
                      style={{ padding: '9px 12px', borderRadius: 6, width: '100%',
                        border: '1px solid var(--border)', background: 'var(--surface)',
                        color: 'var(--text)', fontSize: 14, boxSizing: 'border-box' }}/>
                  </div>
                  <div>
                    <label style={{ fontSize: 11, fontWeight: 700, color: 'var(--subtext)',
                      textTransform: 'uppercase', display: 'block', marginBottom: 4 }}>
                      Confirm Password
                    </label>
                    <input type="password" value={resetPw2}
                      onChange={e => setResetPw2(e.target.value)}
                      placeholder="Repeat new password"
                      onKeyDown={e => e.key === 'Enter' && handleResetPassword()}
                      style={{ padding: '9px 12px', borderRadius: 6, width: '100%',
                        border: '1px solid var(--border)', background: 'var(--surface)',
                        color: 'var(--text)', fontSize: 14, boxSizing: 'border-box' }}/>
                  </div>
                </div>

                {resetMsg && (
                  <p style={{ color: 'var(--reject)', fontSize: 13,
                    margin: '0 0 12px', padding: '8px 12px', borderRadius: 6,
                    background: 'rgba(239,68,68,0.1)' }}>
                    {resetMsg}
                  </p>
                )}

                <div style={{ display: 'flex', gap: 10 }}>
                  <button onClick={handleResetPassword} disabled={resetting}
                    style={{ flex: 1, padding: '10px', borderRadius: 6, border: 'none',
                      background: 'var(--accent)', color: '#fff', fontWeight: 700,
                      cursor: resetting ? 'not-allowed' : 'pointer',
                      opacity: resetting ? 0.6 : 1, fontSize: 14 }}>
                    {resetting ? 'Saving...' : 'Reset Password'}
                  </button>
                  <button onClick={() => { setResetId(null); setResetPw(''); setResetPw2(''); setResetMsg(''); }}
                    style={{ padding: '10px 20px', borderRadius: 6,
                      border: '1px solid var(--border)', background: 'transparent',
                      color: 'var(--text)', cursor: 'pointer', fontSize: 14 }}>
                    Cancel
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Page header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
        <span style={{ fontSize: 28 }}>👥</span>
        <div>
          <h1 style={{ margin: 0 }}>Admin Users</h1>
          <p style={{ margin: 0, color: 'var(--subtext)', fontSize: 14 }}>
            Manage admin accounts, roles and permissions.
          </p>
        </div>
        {canCreate && (
          <button onClick={() => setShowAdd(v => !v)} style={{
            marginLeft: 'auto', padding: '8px 18px', borderRadius: 6,
            border: 'none', background: 'var(--accent)', color: '#fff',
            fontWeight: 600, cursor: 'pointer',
          }}>
            {showAdd ? 'Cancel' : '+ New Admin User'}
          </button>
        )}
      </div>

      {/* Add form */}
      {showAdd && (
        <div className="card" style={{ marginBottom: 20 }}>
          <h3 style={{ margin: '0 0 14px' }}>Create Admin User</h3>
          <form onSubmit={handleAdd}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr auto', gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: 'var(--subtext)', display: 'block', marginBottom: 4 }}>
                  USERNAME *
                </label>
                <input value={addUser} onChange={e => setAddUser(e.target.value)}
                  placeholder="jsmith" style={inp}/>
              </div>
              <div>
                <label style={{ fontSize: 12, color: 'var(--subtext)', display: 'block', marginBottom: 4 }}>
                  PASSWORD *
                </label>
                <input type="password" value={addPass} onChange={e => setAddPass(e.target.value)}
                  placeholder="Min 8 chars" style={inp}/>
              </div>
              <div>
                <label style={{ fontSize: 12, color: 'var(--subtext)', display: 'block', marginBottom: 4 }}>
                  DISPLAY NAME
                </label>
                <input value={addName} onChange={e => setAddName(e.target.value)}
                  placeholder="John Smith" style={inp}/>
              </div>
              <div>
                <label style={{ fontSize: 12, color: 'var(--subtext)', display: 'block', marginBottom: 4 }}>
                  ROLE *
                </label>
                <select value={addRole}
                  onChange={e => { setAddRole(e.target.value); setAddReasons([]); }}
                  style={inp}>
                  <option value="">Select role...</option>
                  {creatableRoles.map(r => (
                    <option key={r} value={r}>{ROLE_LABELS[r]?.label || r}</option>
                  ))}
                </select>
              </div>
              <div style={{ display: 'flex', alignItems: 'flex-end' }}>
                <button type="submit" disabled={adding} style={{
                  padding: '8px 20px', borderRadius: 6, border: 'none',
                  background: 'var(--accent)', color: '#fff', fontWeight: 600,
                  cursor: adding ? 'not-allowed' : 'pointer', opacity: adding ? 0.6 : 1,
                  whiteSpace: 'nowrap',
                }}>
                  {adding ? 'Creating...' : 'Create'}
                </button>
              </div>
            </div>
            {addRole === 'global_db_admin' && (
              <ReasonPicker selected={addReasons} onChange={setAddReasons} allReasons={reasons}/>
            )}
            {addErr && (
              <p style={{ color: 'var(--reject)', margin: '8px 0 0', fontSize: 13 }}>{addErr}</p>
            )}
          </form>
        </div>
      )}

      {error && <p style={{ color: 'var(--reject)' }}>{error}</p>}

      {/* Table */}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
          <thead>
            <tr style={{ borderBottom: '1px solid var(--border)' }}>
              {['User', 'Display Name', 'Role', 'Assigned Reasons', 'Status', 'Created', 'Actions'].map(h => (
                <th key={h} style={{
                  padding: '12px 16px', textAlign: 'left',
                  fontWeight: 600, color: 'var(--subtext)', fontSize: 12,
                }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={7} style={{ padding: 24, textAlign: 'center', color: 'var(--subtext)' }}>
                  Loading...
                </td>
              </tr>
            )}
            {!loading && !users.length && (
              <tr>
                <td colSpan={7} style={{ padding: 32, textAlign: 'center', color: 'var(--subtext)' }}>
                  No admin users yet.
                </td>
              </tr>
            )}
            {users.map(u => {
              const isEditing = editId === u.id;
              const userReasons = parseReasons(u.assigned_reasons);

              if (isEditing) {
                return (
                  <tr key={u.id} style={{
                    borderBottom: '1px solid var(--border)',
                    background: 'var(--surface)',
                  }}>
                    <td style={{ padding: '10px 16px', fontFamily: 'monospace', color: 'var(--subtext)' }}>
                      {u.username}
                    </td>
                    <td style={{ padding: '10px 16px' }}>
                      <input value={editName} onChange={e => setEditName(e.target.value)}
                        style={{ ...inp, width: 130 }}/>
                    </td>
                    <td style={{ padding: '10px 16px' }}>
                      <select value={editRole} onChange={e => setEditRole(e.target.value)}
                        style={{ ...inp, width: 150 }}>
                        {creatableRoles.map(r => (
                          <option key={r} value={r}>{ROLE_LABELS[r]?.label || r}</option>
                        ))}
                      </select>
                    </td>
                    <td style={{ padding: '10px 16px' }}>
                      {editRole === 'global_db_admin' && (
                        <ReasonPicker selected={editReasons}
                          onChange={setEditReasons} allReasons={reasons}/>
                      )}
                    </td>
                    <td style={{ padding: '10px 16px' }}>
                      <select value={String(editActive)}
                        onChange={e => setEditActive(e.target.value === 'true')}
                        style={{ ...inp, width: 100 }}>
                        <option value="true">Active</option>
                        <option value="false">Inactive</option>
                      </select>
                    </td>
                    <td style={{ padding: '10px 16px' }}>
                      <input type="password" value={editPass}
                        onChange={e => setEditPass(e.target.value)}
                        placeholder="New password" style={{ ...inp, width: 140 }}/>
                      {editErr && (
                        <div style={{ color: 'var(--reject)', fontSize: 11, marginTop: 4 }}>
                          {editErr}
                        </div>
                      )}
                    </td>
                    <td style={{ padding: '10px 16px' }}>
                      <button onClick={() => saveEdit(u.id)} disabled={saving}
                        style={{ ...btn('var(--accent)', '#fff', { marginRight: 6 }) }}>
                        {saving ? '...' : 'Save'}
                      </button>
                      <button onClick={() => setEditId(null)}
                        style={btn('transparent', 'var(--text)',
                          { border: '1px solid var(--border)' })}>
                        Cancel
                      </button>
                    </td>
                  </tr>
                );
              }

              return (
                <tr key={u.id} style={{
                  borderBottom: '1px solid var(--border)',
                  opacity: u.deleted_at ? 0.45 : 1,
                }}>
                  <td style={{ padding: '12px 16px', fontFamily: 'monospace', fontWeight: 600 }}>
                    {u.username}
                  </td>
                  <td style={{ padding: '12px 16px' }}>{u.display_name || '-'}</td>
                  <td style={{ padding: '12px 16px' }}><RoleBadge role={u.role}/></td>
                  <td style={{ padding: '12px 16px' }}>
                    {u.role === 'global_db_admin' && userReasons.length > 0 && (
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                        {userReasons.map(r => (
                          <span key={r} style={{
                            background: 'rgba(239,68,68,0.12)', color: '#ef4444',
                            borderRadius: 3, padding: '1px 6px', fontSize: 11,
                          }}>{r}</span>
                        ))}
                      </div>
                    )}
                    {u.role === 'global_db_admin' && userReasons.length === 0 && (
                      <span style={{ color: 'var(--muted)', fontSize: 12 }}>None assigned</span>
                    )}
                    {u.role === 'global_db_user' && (
                      <span style={{ color: 'var(--subtext)', fontSize: 12 }}>Inherits from parent</span>
                    )}
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    {u.deleted_at
                      ? <span style={{ color: '#ef4444', fontSize: 12, fontWeight: 600 }}>Deleted</span>
                      : u.active
                      ? <span style={{ color: '#22c55e', fontSize: 12, fontWeight: 600 }}>Active</span>
                      : <span style={{ color: '#6b7280', fontSize: 12, fontWeight: 600 }}>Inactive</span>
                    }
                  </td>
                  <td style={{ padding: '12px 16px', color: 'var(--subtext)', fontSize: 12 }}>
                    {fmt(u.created_at)}
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    {u.deleted_at ? (
                      isSuperAdmin && (
                        <button onClick={() => handleRestore(u.id)}
                          style={btn('rgba(34,197,94,0.15)', '#22c55e', {})}>
                          Restore
                        </button>
                      )
                    ) : (
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        <button onClick={() => startEdit(u)}
                          style={btn('var(--accent)', '#fff', {})}>
                          Edit
                        </button>
                        <button onClick={() => {
                            setResetId(u.id);
                            setResetUser(u.username);
                            setResetPw(''); setResetPw2(''); setResetMsg('');
                          }}
                          style={btn('rgba(245,158,11,0.15)', '#f59e0b', {})}>
                          Reset PW
                        </button>
                        {isSuperAdmin && u.role === 'global_db_admin' && (
                          <button onClick={() => openImageUpload(u)}
                            style={btn(
                              u.popup_image_data ? 'rgba(34,197,94,0.15)' : 'rgba(79,142,247,0.15)',
                              u.popup_image_data ? '#22c55e' : '#4f8ef7',
                              {}
                            )}>
                            {u.popup_image_data ? 'Image OK' : 'Add Image'}
                          </button>
                        )}
                        {isSuperAdmin && u.role === 'global_db_admin' && u.popup_image_data && (
                          <button onClick={() => setViewImg(
                            `data:${u.popup_image_mime || 'image/jpeg'};base64,${u.popup_image_data}`
                          )} style={btn('rgba(107,114,128,0.15)', 'var(--text)', {})}>
                            View
                          </button>
                        )}
                        <button onClick={() => handleDelete(u)}
                          style={btn('rgba(239,68,68,0.15)', '#ef4444', {})}>
                          {isSuperAdmin ? 'Delete' : 'Deactivate'}
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
