import React, { createContext, useContext, useState, useCallback } from 'react';
import { setToken as persistToken } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, _setToken] = useState(() => localStorage.getItem('cf_token'));
  const [username, setUsername] = useState(() => localStorage.getItem('cf_username') || '');

  const login = useCallback((tok, name) => {
    persistToken(tok);
    localStorage.setItem('cf_username', name || '');
    localStorage.removeItem('cf_perms');   // clear any previous session's perms
    _setToken(tok);
    setUsername(name || '');
  }, []);

  const logout = useCallback(() => {
    persistToken(null);
    localStorage.removeItem('cf_username');
    localStorage.removeItem('cf_admin');
    localStorage.removeItem('cf_perms');
    _setToken(null);
    setUsername('');
  }, []);

  return (
    <AuthContext.Provider value={{ token, username, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
