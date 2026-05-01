import React, { useEffect, useState } from 'react';
import { api } from '../api';

export default function Audit() {
  const [log, setLog] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/admin/audit')
      .then(r => setLog(r.log || []))
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  return (
    <>
      <header className="page-head">
        <h1>Audit Log</h1>
        <p className="muted">Last 500 events across the system</p>
      </header>

      <section className="card">
        {loading ? <p className="muted">Loading…</p>
         : error ? <div className="alert alert-error">{error}</div>
         : log.length === 0 ? <p className="muted">No events yet.</p>
         : (
          <table>
            <thead><tr><th>Time</th><th>Actor</th><th>Event</th><th>Details</th></tr></thead>
            <tbody>
              {log.map(l => (
                <tr key={l.id}>
                  <td className="muted">{new Date(l.ts).toLocaleString()}</td>
                  <td>{l.actor}</td>
                  <td><span className="pill">{l.event}</span></td>
                  <td className="muted">{l.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
