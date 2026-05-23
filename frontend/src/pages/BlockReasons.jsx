import React, { useEffect, useState } from 'react';
import { api } from '../api';

/**
 * Admin manages the list of reasons that the Android app shows in the
 * post-call "Block this number?" follow-up dialog.
 *
 * Stored server-side as a single newline-delimited string in
 * settings.block_reasons. Each non-empty line becomes one reason.
 */
export default function BlockReasons() {
  const [items, setItems] = useState([]);
  const [adding, setAdding] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [editingIdx, setEditingIdx] = useState(-1);
  const [editValue, setEditValue] = useState('');

  useEffect(() => { load(); }, []);

  async function load() {
    setLoading(true);
    try {
      const r = await api.get('/admin/settings');
      const raw = (r.settings && r.settings.block_reasons) || '';
      const arr = raw.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
      setItems(arr);
    } catch (e) {
      alert('Failed to load: ' + e.message);
    } finally {
      setLoading(false);
    }
  }

  async function save(newList) {
    setSaving(true);
    setSaved(false);
    try {
      await api.put('/admin/settings', { block_reasons: newList.join('\n') });
      setItems(newList);
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch (e) {
      alert('Save failed: ' + e.message);
    } finally {
      setSaving(false);
    }
  }

  function add() {
    const v = adding.trim();
    if (!v) return;
    if (items.includes(v)) {
      alert('That reason already exists.');
      return;
    }
    const newList = [...items, v];
    setAdding('');
    save(newList);
  }

  function remove(i) {
    if (!confirm(`Delete "${items[i]}"?`)) return;
    const newList = items.filter((_, idx) => idx !== i);
    save(newList);
  }

  function startEdit(i) {
    setEditingIdx(i);
    setEditValue(items[i]);
  }

  function commitEdit() {
    const v = editValue.trim();
    if (!v) { setEditingIdx(-1); return; }
    if (v === items[editingIdx]) { setEditingIdx(-1); return; }
    if (items.includes(v)) { alert('Already exists.'); return; }
    const newList = items.map((it, idx) => idx === editingIdx ? v : it);
    setEditingIdx(-1);
    save(newList);
  }

  function move(i, dir) {
    const j = i + dir;
    if (j < 0 || j >= items.length) return;
    const newList = [...items];
    [newList[i], newList[j]] = [newList[j], newList[i]];
    save(newList);
  }

  return (
    <div>
      <header className="page-head">
        <h1>📋 Block Reasons</h1>
        <p className="muted">
          Categories that appear in the Android app's "Why are you blocking?"
          follow-up dialog after a user taps BLOCK in the post-call popup.
          Selected reasons are saved on each blocked-call row.
        </p>
      </header>

      <section className="card">
        <div style={{ display: 'flex', gap: 8, marginBottom: 18 }}>
          <input
            type="text"
            placeholder="New reason (e.g. 'Insurance spam')"
            value={adding}
            onChange={e => setAdding(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && add()}
            style={{ flex: 1 }}
          />
          <button className="btn btn-primary" onClick={add} disabled={saving || !adding.trim()}>
            + Add reason
          </button>
        </div>

        {loading ? <p className="muted">Loading…</p>
         : items.length === 0
          ? <p className="muted">No reasons defined yet. Add some above.</p>
          : (
          <table>
            <thead>
              <tr><th style={{ width: 60 }}>#</th><th>Reason</th><th style={{ width: 240 }}>Actions</th></tr>
            </thead>
            <tbody>
              {items.map((item, i) => (
                <tr key={i}>
                  <td className="muted">{i + 1}</td>
                  <td>
                    {editingIdx === i ? (
                      <input
                        type="text"
                        value={editValue}
                        onChange={e => setEditValue(e.target.value)}
                        onKeyDown={e => {
                          if (e.key === 'Enter') commitEdit();
                          if (e.key === 'Escape') setEditingIdx(-1);
                        }}
                        autoFocus
                        style={{ width: '100%' }}
                      />
                    ) : item}
                  </td>
                  <td className="actions">
                    {editingIdx === i ? (
                      <>
                        <button className="btn btn-mini btn-primary" onClick={commitEdit}>Save</button>
                        <button className="btn btn-mini btn-ghost" onClick={() => setEditingIdx(-1)}>Cancel</button>
                      </>
                    ) : (
                      <>
                        <button className="btn btn-mini btn-ghost" onClick={() => move(i, -1)} disabled={i === 0}>↑</button>
                        <button className="btn btn-mini btn-ghost" onClick={() => move(i,  1)} disabled={i === items.length - 1}>↓</button>
                        <button className="btn btn-mini btn-ghost" onClick={() => startEdit(i)}>Edit</button>
                        <button className="btn btn-mini btn-danger" onClick={() => remove(i)}>Delete</button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {saving && <p className="muted" style={{ marginTop: 14 }}>Saving…</p>}
        {saved && <p style={{ marginTop: 14, color: 'var(--success, #4ade80)' }}>✓ Saved</p>}
      </section>

      <section className="card" style={{ marginTop: 24 }}>
        <h2>How users see this</h2>
        <p className="muted">
          When a user finishes a call with an unknown number, the Android app
          shows a "Block this number?" popup. If they tap BLOCK, a follow-up
          dialog lets them pick one of the reasons above (or Skip). The selected
          reason is saved on the blocked-call record and visible in the
          user's Blocked Calls tab.
        </p>
      </section>
    </div>
  );
}
