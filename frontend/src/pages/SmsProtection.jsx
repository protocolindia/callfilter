import { useEffect, useState } from 'react';
import { api } from '../api';

export default function SmsProtection() {
  const [keywords, setKeywords] = useState([]);
  const [urls, setUrls] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [newPhrase, setNewPhrase] = useState('');
  const [newCategory, setNewCategory] = useState('spam');
  const [newWeight, setNewWeight] = useState(30);
  const [newDomain, setNewDomain] = useState('');
  const [newUrlCategory, setNewUrlCategory] = useState('phishing');

  const load = async () => {
    setLoading(true); setError('');
    try {
      const [k, u] = await Promise.all([
        api.get('/admin/sms-protection/keywords'),
        api.get('/admin/sms-protection/urls'),
      ]);
      setKeywords(k.keywords || []);
      setUrls(u.urls || []);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const addKeyword = async () => {
    if (!newPhrase.trim()) return;
    try {
      await api.post('/admin/sms-protection/keywords',
        { phrase: newPhrase.trim(), category: newCategory, weight: Number(newWeight) });
      setNewPhrase(''); setNewWeight(30); load();
    } catch (e) { alert(e.message); }
  };

  const deleteKeyword = async (id) => {
    if (!window.confirm('Delete this keyword rule?')) return;
    try { await api.delete(`/admin/sms-protection/keywords/${id}`); load(); }
    catch (e) { alert(e.message); }
  };

  const toggleKeyword = async (k) => {
    try { await api.put(`/admin/sms-protection/keywords/${k.id}`, { is_active: !k.is_active }); load(); }
    catch (e) { alert(e.message); }
  };

  const addUrl = async () => {
    if (!newDomain.trim()) return;
    try {
      await api.post('/admin/sms-protection/urls',
        { domain: newDomain.trim(), category: newUrlCategory });
      setNewDomain(''); load();
    } catch (e) { alert(e.message); }
  };

  const deleteUrl = async (id) => {
    if (!window.confirm('Delete this blocked domain?')) return;
    try { await api.delete(`/admin/sms-protection/urls/${id}`); load(); }
    catch (e) { alert(e.message); }
  };

  if (loading) return <div className="card">Loading SMS protection rules...</div>;

  return (
    <div>
      <h1 style={{ marginBottom: 6 }}>🛡️ SMS Protection</h1>
      <p className="muted" style={{ marginBottom: 20 }}>
        Manage the keyword rules and URL blocklist used to detect phishing and spam
        SMS on user devices. Changes sync to the app automatically.
      </p>

      {error && <div className="card" style={{ color: 'var(--red)' }}>{error}</div>}

      <div className="card">
        <h2>Keyword Rules</h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', margin: '12px 0 16px' }}>
          <input className="input" placeholder="Phrase, e.g. verify your account"
            value={newPhrase} onChange={e => setNewPhrase(e.target.value)}
            style={{ flex: 2, minWidth: 200 }} />
          <select className="input" value={newCategory} onChange={e => setNewCategory(e.target.value)}>
            <option value="spam">spam</option>
            <option value="phishing">phishing</option>
            <option value="scam">scam</option>
            <option value="promotional">promotional</option>
          </select>
          <input className="input" type="number" placeholder="weight" value={newWeight}
            onChange={e => setNewWeight(e.target.value)} style={{ width: 90 }} />
          <button className="btn btn-primary" onClick={addKeyword}>Add</button>
        </div>

        <table className="data-table">
          <thead>
            <tr><th>Phrase</th><th>Category</th><th>Weight</th><th>Active</th><th></th></tr>
          </thead>
          <tbody>
            {keywords.length === 0 && <tr><td colSpan="5" className="muted">No keyword rules yet.</td></tr>}
            {keywords.map(k => (
              <tr key={k.id}>
                <td>{k.phrase}</td>
                <td>{k.category}</td>
                <td>{k.weight}</td>
                <td>
                  <button className="btn" onClick={() => toggleKeyword(k)}
                    style={{ background: k.is_active ? 'rgba(52,211,153,0.15)' : 'rgba(107,114,128,0.15)',
                             color: k.is_active ? '#34d399' : '#9ca3af' }}>
                    {k.is_active ? 'ON' : 'OFF'}
                  </button>
                </td>
                <td>
                  <button className="btn" onClick={() => deleteKeyword(k.id)}
                    style={{ background: 'rgba(248,113,113,0.15)', color: '#f87171' }}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card" style={{ marginTop: 20 }}>
        <h2>URL / Domain Blocklist</h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', margin: '12px 0 16px' }}>
          <input className="input" placeholder="Domain, e.g. bit.ly or secure-login.xyz"
            value={newDomain} onChange={e => setNewDomain(e.target.value)}
            style={{ flex: 2, minWidth: 200 }} />
          <select className="input" value={newUrlCategory} onChange={e => setNewUrlCategory(e.target.value)}>
            <option value="phishing">phishing</option>
            <option value="suspicious">suspicious</option>
            <option value="scam">scam</option>
          </select>
          <button className="btn btn-primary" onClick={addUrl}>Add</button>
        </div>

        <table className="data-table">
          <thead><tr><th>Domain</th><th>Category</th><th></th></tr></thead>
          <tbody>
            {urls.length === 0 && <tr><td colSpan="3" className="muted">No blocked domains yet.</td></tr>}
            {urls.map(u => (
              <tr key={u.id}>
                <td>{u.domain}</td>
                <td>{u.category}</td>
                <td>
                  <button className="btn" onClick={() => deleteUrl(u.id)}
                    style={{ background: 'rgba(248,113,113,0.15)', color: '#f87171' }}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
