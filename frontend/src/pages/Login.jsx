import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, setToken, setAdminMeta } from '../api.js';
import { useAuth } from '../auth.jsx';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const expired = typeof window !== 'undefined' &&
    window.location.search.includes('expired=1');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const r = await api.post('/admin/login', { username, password });
      login(r.token, r.username);
      navigate('/dashboard');
    } catch (err) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <div className="login-logo">🛡️</div>
        <h1>CallFilter Admin</h1>
        <p className="muted">Sign in to manage users and SMS providers</p>
        {expired && (
          <div style={{
              marginTop: 16, padding: '10px 14px', borderRadius: 8,
              background: 'rgba(245,158,11,0.12)',
              border: '1px solid rgba(245,158,11,0.4)',
              color: '#F59E0B', fontSize: 13
            }}>
            Your session expired. Please sign in again.
          </div>
        )}

        {error && <div className="alert alert-error" style={{ marginTop: 16 }}>{error}</div>}

        <form onSubmit={handleSubmit}>
          <label>Username</label>
          <input
            type="text"
            value={username}
            onChange={e => setUsername(e.target.value)}
            required autoFocus autoComplete="username"
          />
          <label>Password</label>
          <input
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            required autoComplete="current-password"
          />
          <button type="submit" className="btn btn-primary" disabled={loading}>
            {loading ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="hint">Default: <code>admin / changeme</code> — change in Settings.</p>
      </div>
    </div>
  );
}
