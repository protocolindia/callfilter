import React, { useEffect, useState } from 'react';
import { api } from '../api';

export default function Settings() {
  const [settings, setSettings] = useState(null);
  const [saving, setSaving]     = useState(false);
  const [testMobile, setTestMobile] = useState('');
  const [testSmsMsg, setTestSmsMsg] = useState('');
  const [testSmsSending, setTestSmsSending] = useState(false);
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

        <section className="card">
          <h2>Razorpay (sideload payments)</h2>
          <p style={{ color: 'var(--muted)', fontSize: 13, marginTop: -8 }}>
            For sideloaded APKs only. Google Play builds use Play Billing.
          </p>

          <label className="checkbox" style={{ marginTop: 12 }}>
            <input type="checkbox"
              checked={settings.razorpay_enabled === 'true'}
              onChange={e => update('razorpay_enabled', e.target.checked ? 'true' : 'false')}/>
            Enable Razorpay payments
          </label>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 16 }}>
            <div className="field" style={{ gridColumn: '1 / -1' }}>
              <label>Mode</label>
              <select value={settings.razorpay_mode || 'test'}
                      onChange={e => update('razorpay_mode', e.target.value)}>
                <option value="test">Test mode (rzp_test_...)</option>
                <option value="live">Live mode (rzp_live_...)</option>
              </select>
            </div>
            <div className="field">
              <label>Test Key ID</label>
              <input value={settings.razorpay_key_id_test || ''}
                onChange={e => update('razorpay_key_id_test', e.target.value)}
                placeholder="rzp_test_..." />
            </div>
            <div className="field">
              <label>Test Secret</label>
              <input type="password" value={settings.razorpay_secret_test || ''}
                onChange={e => update('razorpay_secret_test', e.target.value)}
                placeholder="Test secret" />
            </div>
            <div className="field">
              <label>Live Key ID</label>
              <input value={settings.razorpay_key_id_live || ''}
                onChange={e => update('razorpay_key_id_live', e.target.value)}
                placeholder="rzp_live_..." />
            </div>
            <div className="field">
              <label>Live Secret</label>
              <input type="password" value={settings.razorpay_secret_live || ''}
                onChange={e => update('razorpay_secret_live', e.target.value)}
                placeholder="Live secret" />
            </div>
            <div className="field" style={{ gridColumn: '1 / -1' }}>
              <label>Webhook Secret</label>
              <input type="password" value={settings.razorpay_webhook_secret || ''}
                onChange={e => update('razorpay_webhook_secret', e.target.value)}
                placeholder="Used to verify webhook callbacks from Razorpay" />
              <small style={{ color: 'var(--muted)' }}>
                Configure your webhook URL in Razorpay dashboard as:<br/>
                <code>https://api.app.onephone.pro/api/razorpay/webhook</code>
              </small>
            </div>
          </div>

          {settings.razorpay_enabled === 'true' && (
            <div style={{
                marginTop: 14, padding: '10px 14px', borderRadius: 8,
                background: settings.razorpay_mode === 'live'
                  ? 'rgba(79, 142, 247, 0.12)' : 'rgba(245, 158, 11, 0.12)',
                border: settings.razorpay_mode === 'live'
                  ? '1px solid rgba(79, 142, 247, 0.4)' : '1px solid rgba(245, 158, 11, 0.4)',
                color: settings.razorpay_mode === 'live' ? '#4F8EF7' : '#F59E0B', fontSize: 13
              }}>
              <strong>{settings.razorpay_mode === 'live' ? 'LIVE mode' : 'TEST mode'}.</strong>
              {' '}Sideload app users will see Razorpay checkout
              {settings.razorpay_mode === 'live' ? ' with real money.' : ' (no real money).'}
            </div>
          )}
        </section>

        {/* SMS API Configuration */}
        <section className="card">
          <h2>📱 SMS API Configuration</h2>
          <p style={{ color:'var(--subtext)', fontSize:14, marginBottom:16 }}>
            Configure your SMS gateway to deliver OTPs to users.
            Based on URL format: <code style={{ background:'var(--surface)', padding:'2px 6px', borderRadius:4, fontSize:12 }}>
              https://api.example.com?userid=X&password=Y&mobileno=91XXXXXXXXXX&message=OTP...
            </code>
          </p>

          <div className="settings-grid">

            <div className="field">
              <label>SMS API URL</label>
              <input value={settings.sms_api_url || ''} onChange={e => update('sms_api_url', e.target.value)}
                placeholder="https://sms.mudunuru.com/SendSMS.aspx"/>
              <small className="muted">Base URL without parameters</small>
            </div>

            <div className="field">
              <label>User ID <span className="muted">(userid param)</span></label>
              <input value={settings.sms_api_userid || ''} onChange={e => update('sms_api_userid', e.target.value)}
                placeholder="myerppro"/>
            </div>

            <div className="field">
              <label>Password <span className="muted">(password param)</span></label>
              <input type="password" value={settings.sms_api_password || ''} onChange={e => update('sms_api_password', e.target.value)}
                placeholder="••••••••"/>
            </div>

            <div className="field">
              <label>Sender Name <span className="muted">(sendername param)</span></label>
              <input value={settings.sms_api_sender_name || ''} onChange={e => update('sms_api_sender_name', e.target.value)}
                placeholder="BZIONX"/>
            </div>

            <div className="field">
              <label>Sender Number <span className="muted">(sendernumber param, optional)</span></label>
              <input value={settings.sms_api_sender_number || ''} onChange={e => update('sms_api_sender_number', e.target.value)}
                placeholder="9493333747"/>
            </div>

            <div className="field">
              <label>Category <span className="muted">(category param)</span></label>
              <input value={settings.sms_api_category || ''} onChange={e => update('sms_api_category', e.target.value)}
                placeholder="2"/>
            </div>

            <div className="field">
              <label>Template ID <span className="muted">(templateid param)</span></label>
              <input value={settings.sms_api_template_id || ''} onChange={e => update('sms_api_template_id', e.target.value)}
                placeholder="1207175299467151781"/>
            </div>

            <div className="field">
              <label>Mobile Number Param Name</label>
              <input value={settings.sms_api_mobile_param || 'mobileno'} onChange={e => update('sms_api_mobile_param', e.target.value)}
                placeholder="mobileno"/>
              <small className="muted">Default: mobileno</small>
            </div>

            <div className="field">
              <label>Message Param Name</label>
              <input value={settings.sms_api_message_param || 'message'} onChange={e => update('sms_api_message_param', e.target.value)}
                placeholder="message"/>
              <small className="muted">Default: message</small>
            </div>

            <div className="field" style={{ gridColumn:'1 / -1' }}>
              <label>OTP Message Template</label>
              <textarea rows={3} value={settings.sms_api_message_template || ''} onChange={e => update('sms_api_message_template', e.target.value)}
                placeholder="{OTP} is your OTP to verify your phone number at yourapp.com. Please do not share OTP with anyone. Your Team"
                style={{ width:'100%', resize:'vertical' }}/>
              <small className="muted">Use <strong>&#123;OTP&#125;</strong> as placeholder — it gets replaced with the actual 6-digit code</small>
            </div>

          </div>

          {/* Preview — built as a JS string to avoid JSX special-char issues */}
          {settings.sms_api_url && (() => {
            const mob    = settings.sms_api_mobile_param  || 'mobileno';
            const msgP   = settings.sms_api_message_param || 'message';
            const msg    = (settings.sms_api_message_template || '{OTP} is your OTP')
                             .replace('{OTP}', '123456');
            const parts  = [
              settings.sms_api_url + '?',
              'userid=' + (settings.sms_api_userid || 'USERID'),
              'password=***',
              mob + '=91XXXXXXXXXX',
              'sendername=' + (settings.sms_api_sender_name || 'SENDER'),
              msgP + '=' + encodeURIComponent(msg),
            ];
            if (settings.sms_api_sender_number) parts.push('sendernumber=' + settings.sms_api_sender_number);
            if (settings.sms_api_category)      parts.push('category='     + settings.sms_api_category);
            if (settings.sms_api_template_id)   parts.push('templateid='   + settings.sms_api_template_id);
            const preview = parts[0] + parts.slice(1).join('&');
            return (
              <div style={{ marginTop:16, padding:12, background:'var(--surface)', borderRadius:6, fontSize:12 }}>
                <div style={{ color:'var(--subtext)', marginBottom:4, fontWeight:600 }}>URL Preview (OTP = 123456):</div>
                <div style={{ color:'var(--text)', wordBreak:'break-all', fontFamily:'monospace' }}>
                  {preview}
                </div>
              </div>
            );
          })()}

          {/* Test SMS */}
          <div style={{ marginTop:20, padding:16, background:'var(--surface)', borderRadius:8 }}>
            <div style={{ fontWeight:600, fontSize:14, marginBottom:8 }}>🧪 Test SMS</div>
            <p style={{ color:'var(--subtext)', fontSize:13, margin:'0 0 10px' }}>
              Save settings first, then send a test OTP (123456) to verify your SMS API works.
            </p>
            <div style={{ display:'flex', gap:8 }}>
              <input value={testMobile} onChange={e => setTestMobile(e.target.value)}
                placeholder="+919876543210 or 919876543210"
                style={{ flex:1, padding:'8px 12px', borderRadius:6, border:'1px solid var(--border)',
                  background:'var(--card)', color:'var(--text)', fontSize:14 }}/>
              <button type="button" onClick={handleTestSms} disabled={testSmsSending}
                style={{ padding:'8px 18px', borderRadius:6, border:'none',
                  background:'#22c55e', color:'#fff', fontWeight:600,
                  cursor:testSmsSending ? 'not-allowed' : 'pointer',
                  opacity:testSmsSending ? 0.6 : 1, fontSize:13 }}>
                {testSmsSending ? 'Sending…' : 'Send Test'}
              </button>
            </div>
            {testSmsMsg && (
              <p style={{ margin:'8px 0 0', fontSize:13,
                color: testSmsMsg.startsWith('✓') ? '#22c55e' : 'var(--reject)' }}>
                {testSmsMsg}
              </p>
            )}
          </div>
        </section>

        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save settings'}
        </button>
        {savedFlag && <span className="alert alert-success" style={{marginLeft:12}}>✓ Saved</span>}
        {error && <span className="alert alert-error" style={{marginLeft:12}}>{error}</span>}
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
