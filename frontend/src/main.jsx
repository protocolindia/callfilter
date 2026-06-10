import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import App from './App.jsx';
import Login from './pages/Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Users from './pages/Users.jsx';
import Settings from './pages/Settings.jsx';
import Audit from './pages/Audit.jsx';
import Billing from './pages/Billing.jsx';
import Payments from './pages/Payments.jsx';
import BlockReasons from './pages/BlockReasons.jsx';
import GlobalBlocklist from './pages/GlobalBlocklist.jsx';
import SmsProtection from './pages/SmsProtection.jsx';
import Roles from './pages/Roles.jsx';
import AdminUsers from './pages/AdminUsers.jsx';
import UserDetail from './pages/UserDetail.jsx';
import Terms from './pages/Terms.jsx';
import Privacy from './pages/Privacy.jsx';
import { AuthProvider, useAuth } from './auth.jsx';
import { getAdminRole } from './api.js';
import { PermissionsProvider, RequirePerm, LandingRedirect } from './permissions.jsx';
import './style.css';

function Protected({ children }) {
  const { token } = useAuth();
  return token ? children : <Navigate to="/login" replace />;
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <BrowserRouter>
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/terms" element={<Terms />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route element={<Protected><PermissionsProvider><App /></PermissionsProvider></Protected>}>
          <Route path="/" element={<LandingRedirect />} />
          <Route path="/dashboard" element={<RequirePerm perm="nav.dashboard"><Dashboard /></RequirePerm>} />
          <Route path="/users" element={<RequirePerm perm="nav.users"><Users /></RequirePerm>} />
          <Route path="/users/:id" element={<RequirePerm perm="nav.users"><UserDetail /></RequirePerm>} />
          <Route path="/settings" element={<RequirePerm perm="nav.settings"><Settings /></RequirePerm>} />
          <Route path="/audit" element={<RequirePerm perm="nav.audit"><Audit /></RequirePerm>} />
          <Route path="/billing" element={<RequirePerm perm="nav.billing"><Billing /></RequirePerm>} />
          <Route path="/payments" element={<RequirePerm perm="nav.payments"><Payments /></RequirePerm>} />
          <Route path="/block-reasons" element={<RequirePerm perm="nav.block_reasons"><BlockReasons /></RequirePerm>} />
          <Route path="/global-blocklist" element={<RequirePerm perm="nav.global_blocklist"><GlobalBlocklist /></RequirePerm>} />
          <Route path="/sms-protection" element={<RequirePerm perm="nav.sms_protection"><SmsProtection /></RequirePerm>} />
          <Route path="/admin-users" element={<RequirePerm perm="nav.admin_users"><AdminUsers /></RequirePerm>} />
          <Route path="/roles" element={<RequirePerm perm="nav.roles"><Roles /></RequirePerm>} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  </BrowserRouter>
);
