import React, { useEffect, useState } from 'react';
import { api } from '../api';

export default function Settings() {
  const [settings, setSettings] = useState(null);
  const [saving, setSaving] = useState(false);
  const [savedFlag, setSavedFlag] = useState(false);
  const [error, setError] = useState('');

  // Password change
  const [pwCurrent, setPwCurrent] = useState('');
  const [pwNew, setPwNew] = useState('');
  const [pwMsg, setPwMsg] = useState('');

  useEffect(() => {
    api.get('/admin/settings')
      .then(r => setSettings(r.settings))
      .catch(err => setError(err.message));
  }, []);

  function update(k, v) {
    setSettings(s => ({ ...s, [k]: v }));
  }

  async function handleSave(e) {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      await api.put('/admin/settings', settings);
      setSavedFlag(true);
      setTimeout(() => setSavedFlag(false), 3000);
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }

  async function handlePassword(e) {
    e.preventDefault();
    setPwMsg('');
    try {
      await api.post('/admin/change-password', { current: pwCurrent, next: pwNew });
      setPwMsg('✓ Password updated');
      setPwCurrent(''); setPwNew('');
    } catch (err) { setPwMsg(err.message); }
  }

  if (!settings) return <p className="muted">Loading…</p>;

  return (
    <>
      <header className="page-head">
        <h1>Settings</h1>
        <p className="muted">Configure SMS provider, OTP rules, and admin password</p>
      </header>

      {savedFlag && <div className="alert alert-success">✓ Saved successfully</div>}
      {error && <div className="alert alert-error">{error}</div>}

      <form onSubmit={handleSave}>
        <section className="card">
          <h2>SMS Provider</h2>
          <p className="muted" style={{ marginBottom: 12 }}>
            Pick how OTPs are delivered. Until a provider is configured, the OTP is returned in the API
            response and shown on the Android signup screen for testing.
          </p>

          <div className="row">
            <div className="col">
              <label>Provider</label>
              <select value={settings.sms_provider} onChange={e => update('sms_provider', e.target.value)}>
                <option value="none">None (dev mode — OTP on screen)</option>
                <option value="twilio">Twilio</option>
                <option value="msg91">MSG91 (India)</option>
                <option value="textlocal">Textlocal</option>
                <option value="custom_http">Custom HTTP API</option>
              </select>
            </div>
            <div className="col">
              <label>Sender ID / From</label>
              <input type="text" value={settings.sms_sender_id || ''}
                onChange={e => update('sms_sender_id', e.target.value)} placeholder="e.g. CALFLT"/>
            </div>
          </div>

          <div className="row">
            <div className="col">
              <label>API Key / Account SID</label>
              <input type="text" value={settings.sms_api_key || ''}
                onChange={e => update('sms_api_key', e.target.value)} placeholder="Provider API key"/>
            </div>
            <div className="col">
              <label>API Secret / Auth Token</label>
              <input type="password" value={settings.sms_api_secret || ''}
                onChange={e => update('sms_api_secret', e.target.value)} placeholder="Provider API secret"/>
            </div>
          </div>

          <label>Custom HTTP endpoint (only for "Custom HTTP API")</label>
          <input type="text" value={settings.sms_endpoint || ''}
            onChange={e => update('sms_endpoint', e.target.value)} placeholder="https://api.example.com/send"/>

          <label>Message template (use <code>{'{{otp}}'}</code> as placeholder)</label>
          <input type="text" value={settings.sms_template || ''}
            onChange={e => update('sms_template', e.target.value)}/>
        </section>

        <section className="card">
          <h2>OTP Rules</h2>
          <div className="row">
            <div className="col">
              <label>Code length</label>
              <select value={settings.otp_length} onChange={e => update('otp_length', e.target.value)}>
                {[4, 5, 6, 8].map(n => <option key={n} value={n}>{n} digits</option>)}
              </select>
            </div>
            <div className="col">
              <label>Expiry (minutes)</label>
              <input type="number" min="1" max="60"
                value={settings.otp_expiry_minutes}
                onChange={e => update('otp_expiry_minutes', e.target.value)}/>
            </div>
          </div>
          {settings.sms_provider === 'none' ? (
            <div style={{
                marginTop: 12, padding: '10px 14px', borderRadius: 8,
                background: 'rgba(34, 197, 94, 0.1)',
                border: '1px solid rgba(34, 197, 94, 0.3)',
                color: '#22C55E', fontSize: 13
              }}>
              <strong>DEV MODE active.</strong> OTPs are returned in the
              /api/signup response and shown on the Android signup screen.
              No SMS is sent. To switch to production, pick an SMS provider
              above and configure its credentials.
            </div>
          ) : (
            <div style={{
                marginTop: 12, padding: '10px 14px', borderRadius: 8,
                background: 'rgba(79, 142, 247, 0.1)',
                border: '1px solid rgba(79, 142, 247, 0.3)',
                color: '#4F8EF7', fontSize: 13
              }}>
              <strong>Production mode.</strong> OTPs are dispatched via
              {' '}{settings.sms_provider}. The Android app will not show
              the code on screen.
            </div>
          )}
        </section>

        <section className="card">
          <h2>Subscription gating</h2>
          <p style={{ color: 'var(--muted)', fontSize: 13, marginTop: -8 }}>
            Controls whether the Android app shows the paywall and blocks calls
            for users without an active subscription.
          </p>
          <label className="checkbox" style={{ marginTop: 12 }}>
            <input type="checkbox"
              checked={settings.subscription_required === 'true'}
              onChange={e => update('subscription_required', e.target.checked ? 'true' : 'false')}/>
            Require active subscription
          </label>
          {settings.subscription_required !== 'true' && (
            <div style={{
                marginTop: 12, padding: '10px 14px', borderRadius: 8,
                background: 'rgba(34, 197, 94, 0.1)',
                border: '1px solid rgba(34, 197, 94, 0.3)',
                color: '#22C55E', fontSize: 13
              }}>
              <strong>Subscription gating OFF.</strong> All users have unrestricted
              access — the Android app skips the paywall. Enable this once
              Google Play Billing is configured and you want to enforce subscriptions.
            </div>
          )}
        </section>

        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save settings'}
        </button>
      </form>

      <form onSubmit={handlePassword} style={{ marginTop: 24 }}>
        <section className="card">
          <h2>Change Admin Password</h2>
          {pwMsg && <div className={`alert ${pwMsg.startsWith('✓') ? 'alert-success' : 'alert-error'}`}>{pwMsg}</div>}
          <div className="row">
            <div className="col">
              <label>Current password</label>
              <input type="password" value={pwCurrent} onChange={e => setPwCurrent(e.target.value)} required/>
            </div>
            <div className="col">
              <label>New password (min 6 chars)</label>
              <input type="password" value={pwNew} onChange={e => setPwNew(e.target.value)} required minLength={6}/>
            </div>
          </div>
          <button type="submit" className="btn btn-secondary">Update password</button>
        </section>
      </form>
    </>
  );
}
