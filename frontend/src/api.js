// Base URL of the backend API. Set VITE_API_URL in Railway frontend env vars.
const BASE = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');

function getToken() {
  return localStorage.getItem('cf_token');
}

export function setToken(token) {
  if (token) localStorage.setItem('cf_token', token);
  else localStorage.removeItem('cf_token');
}

// Store admin metadata from login response
export function setAdminMeta(data) {
  if (data) localStorage.setItem('cf_admin', JSON.stringify({
    username: data.username,
    display_name: data.display_name || data.username,
    role: data.role || 'admin'
  }));
  else localStorage.removeItem('cf_admin');
}

export function getAdminMeta() {
  try { return JSON.parse(localStorage.getItem('cf_admin') || 'null'); }
  catch { return null; }
}

export function getAdminRole() {
  return getAdminMeta()?.role || 'admin';
}

async function request(path, options = {}) {
  const token = getToken();
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${BASE}${path}`, { ...options, headers });
  let data = null;
  try { data = await res.json(); } catch { /* might not be JSON */ }

  if (!res.ok) {
    // Auto-logout on auth failures — token expired / invalid / missing
    if (res.status === 401 ||
        (data && (data.error === 'invalid_token' ||
                  data.error === 'expired_token' ||
                  data.error === 'token_expired' ||
                  data.error === 'unauthorized'))) {
      setToken(null);
      // Avoid redirect-loop when already on /login
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login?expired=1';
      }
      const err = new Error('Session expired. Please sign in again.');
      err.status = 401;
      throw err;
    }
    const err = new Error((data && data.error) || `HTTP ${res.status}`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

export const api = {
  get:  (p)        => request(p),
  post: (p, body)  => request(p, { method: 'POST', body: JSON.stringify(body || {}) }),
  put:  (p, body)  => request(p, { method: 'PUT',  body: JSON.stringify(body || {}) }),
  del:    (p)        => request(p, { method: 'DELETE' }),
  delete: (p)        => request(p, { method: 'DELETE' }),
};
