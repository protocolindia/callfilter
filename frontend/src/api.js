// Base URL of the backend API. Set VITE_API_URL in Railway frontend env vars.
const BASE = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');

function getToken() {
  return localStorage.getItem('cf_token');
}

export function setToken(token) {
  if (token) localStorage.setItem('cf_token', token);
  else localStorage.removeItem('cf_token');
}

async function request(path, options = {}) {
  const token = getToken();
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${BASE}${path}`, { ...options, headers });
  let data = null;
  try { data = await res.json(); } catch { /* might not be JSON */ }

  if (!res.ok) {
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
  del:  (p)        => request(p, { method: 'DELETE' }),
};
