import { useEffect, useState } from 'react';
import { api } from '../api';

export default function Roles() {
  const [roles, setRoles] = useState([]);
  const [catalog, setCatalog] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null); // role object being edited (or {new:true})

  const load = async () => {
    setLoading(true); setError('');
    try {
      const [r, c] = await Promise.all([
        api.get('/admin/roles'),
        api.get('/admin/permission-catalog'),
      ]);
      setRoles(r.roles || []);
      setCatalog(c.catalog || []);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };
  useEffect(() => { load(); }, []);

  const startNew = () => setEditing({ new: true, label: '', permissions: [] });
  const startEdit = (role) => setEditing({
    ...role,
    permissions: Array.isArray(role.permissions) ? [...role.permissions] : [],
  });

  const togglePerm = (key) => {
    setEditing(e => {
      const has = e.permissions.includes(key);
      return { ...e, permissions: has ? e.permissions.filter(p => p !== key) : [...e.permissions, key] };
    });
  };

  const save = async () => {
    if (!editing.label.trim()) { alert('Name is required'); return; }
    try {
      let res;
      if (editing.new) {
        res = await api.post('/admin/roles', { label: editing.label, permissions: editing.permissions });
      } else {
        res = await api.put(`/admin/roles/${editing.id}`, { label: editing.label, permissions: editing.permissions });
      }
      // Confirm what the server actually stored (proves the write worked).
      const saved = res && res.role && Array.isArray(res.role.permissions)
        ? res.role.permissions : null;
      if (editing.permissions.length > 0 && saved && saved.length === 0) {
        alert('Warning: the server saved 0 permissions even though you selected '
          + editing.permissions.length + '. The backend may not be updated. Please redeploy the backend.');
      }
      setEditing(null); load();
    } catch (e) { alert('Save failed: ' + e.message); }
  };

  const remove = async (role) => {
    if (!window.confirm(`Delete role "${role.label}"?`)) return;
    try { await api.delete(`/admin/roles/${role.id}`); load(); }
    catch (e) { alert(e.message); }
  };

  const isWildcard = editing && editing.permissions.includes('*');

  if (loading) return <div className="card">Loading roles…</div>;

  return (
    <div>
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', marginBottom:16 }}>
        <h1 style={{ margin:0 }}>🎭 Roles</h1>
        <button className="btn btn-primary" onClick={startNew}>+ New role</button>
      </div>
      <p className="muted" style={{ marginTop:0 }}>
        Create custom roles and choose exactly which left-menu links and actions each role can use.
        Assign roles to staff in Admin Users.
      </p>

      {error && <div className="card" style={{ color:'var(--red)' }}>{error}</div>}

      <table className="data-table">
        <thead><tr><th>Role</th><th>Key</th><th>Users</th><th>Permissions</th><th></th></tr></thead>
        <tbody>
          {roles.map(r => (
            <tr key={r.id}>
              <td>{r.label} {r.is_system && <span className="muted" style={{ fontSize:11 }}>(system)</span>}</td>
              <td><code>{r.key}</code></td>
              <td>{r.user_count}</td>
              <td>{Array.isArray(r.permissions) && r.permissions.includes('*')
                    ? 'All permissions'
                    : (Array.isArray(r.permissions) ? r.permissions.length : 0) + ' permissions'}
                {Array.isArray(r.permissions) && r.permissions.length > 0 && !r.permissions.includes('*') && (
                  <div className="muted" style={{ fontSize: 11, marginTop: 2, maxWidth: 360 }}>
                    {r.permissions.join(', ')}
                  </div>
                )}
              </td>
              <td style={{ whiteSpace:'nowrap' }}>
                {r.key !== 'super_admin' &&
                  <button className="btn" onClick={() => startEdit(r)}>Edit</button>}
                {!r.is_system &&
                  <button className="btn" onClick={() => remove(r)}
                    style={{ marginLeft:6, background:'rgba(248,113,113,0.15)', color:'#f87171' }}>Delete</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {editing && (
        <div style={{ position:'fixed', inset:0, background:'rgba(0,0,0,0.6)',
            display:'flex', alignItems:'center', justifyContent:'center', zIndex:1000, padding:20 }}>
          <div className="card" style={{ maxWidth:680, width:'100%', maxHeight:'85vh', overflow:'auto' }}>
            <h2 style={{ marginTop:0 }}>{editing.new ? 'New role' : `Edit: ${editing.label}`}</h2>
            <label style={{ display:'block', marginBottom:6, fontWeight:600 }}>Role name</label>
            <input className="input" value={editing.label}
              onChange={e => setEditing({ ...editing, label: e.target.value })}
              placeholder="e.g. Operations Manager" style={{ marginBottom:16, width:'100%' }} />

            {isWildcard ? (
              <p className="muted">This role has all permissions.</p>
            ) : catalog.map(group => (
              <div key={group.group} style={{ marginBottom:16 }}>
                <div style={{ fontWeight:700, marginBottom:8 }}>{group.group}</div>
                <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:6 }}>
                  {group.perms.map(p => (
                    <label key={p.key} style={{ display:'flex', alignItems:'center', gap:8, fontSize:14 }}>
                      <input type="checkbox"
                        checked={editing.permissions.includes(p.key)}
                        onChange={() => togglePerm(p.key)} />
                      {p.label}
                    </label>
                  ))}
                </div>
              </div>
            ))}

            <div style={{ display:'flex', gap:10, justifyContent:'flex-end', marginTop:10 }}>
              <button className="btn" onClick={() => setEditing(null)}>Cancel</button>
              <button className="btn btn-primary" onClick={save}>Save role</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
