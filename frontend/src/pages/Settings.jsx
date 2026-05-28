import React, { useEffect, useState } from 'react';
import { api } from '../api.js';

const inp = {
  padding: '8px 12px', borderRadius: 6, border: '1px solid var(--border)',
  background: 'var(--surface)', color: 'var(--text)',
  fontSize: 14, width: '100%', boxSizing: 'border-box',
};

const fieldStyle = { display: 'flex', flexDirection: 'column', gap: 4 };
const labelStyle = { fontSize: 12, fontWeight: 600, color: 'var(--subtext)' };
const smallStyle = { fontSize: 11, color: 'var(--subtext)' };
const grid2 = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 };

export default function Settings() {
  const [settings, setSettings]         = useState(null);
  const [saving, setSaving]             = useState(false);
  const [savedFlag, setSavedFlag]       = useState(false);
  const [error, setError]               = useState('');
  const [pwCurrent, setPwCurrent]       = useState('');
  const [pwNew, setPwNew]               = useState('');
  const [pwMsg, setPwMsg]               = useState('');
  const [testMobile, setTestMobile]     = useState('');
  const [testResult, setTestResult]     = useState('');
  const [testSending, setTestSending]   = useState(false);

  useEffect(() => {
    api.get('/admin/settings')
      .then(r => setSettings(r.settings || r))
      .catch(e => setError(e.message || 'Failed to load settings'));
  }, []);

  function upd(k, v) {
    setSettings(s => ({ ...s, [k]: v }));
  }

  async function handleSave(e) {
    e.preventDefault();
    setSaving(true); setError('');
    try {
      await api.put('/admin/settings', settings);
      setSavedFlag(true);
      setTimeout(() => setSavedFlag(false), 3000);
    } catch (e) { setError(e.message || 'Save failed'); }
    finally { setSaving(false); }
  }

  async function handlePassword(e) {
    e.preventDefault(); setPwMsg('');
    try {
      await api.post('/admin/change-password', { current: pwCurrent, next: pwNew });
      setPwMsg('Password updated');
      setPwCurrent(''); setPwNew('');
    } catch (e) { setPwMsg(e.message || 'Failed'); }
  }

  async function handleTestSms() {
    if (!testMobile.trim()) { setTestResult('Enter a mobile number'); return; }
    setTestSending(true); setTestResult('');
    try {
      const r = await api.post('/admin/test-sms', { mobile: testMobile.trim() });
      setTestResult('Sent OK  -  API response: ' + (r.response || 'OK') + ' (HTTP ' + r.status + ')');
    } catch (e) { setTestResult('Error: ' + (e.message || 'Failed')); }
    finally { setTestSending(false); }
  }

  function buildPreview() {
    if (!settings || !settings.sms_api_url) return '';
    const mob  = settings.sms_api_mobile_param  || 'mobileno';
    const msgP = settings.sms_api_message_param || 'message';
    const msg  = (settings.sms_api_message_template || '{OTP} is your OTP')
                   .replace('{OTP}', '123456');
    let url = settings.sms_api_url + '?'
      + 'userid=' + (settings.sms_api_userid || 'USERID')
      + '&password=***'
      + '&' + mob + '=91XXXXXXXXXX'
      + '&sendername=' + (settings.sms_api_sender_name || 'SENDER')
      + '&' + msgP + '=' + encodeURIComponent(msg);
    if (settings.sms_api_sender_number) url += '&sendernumber=' + settings.sms_api_sender_number;
    if (settings.sms_api_category)      url += '&category='     + settings.sms_api_category;
    if (settings.sms_api_template_id)   url += '&templateid='   + settings.sms_api_template_id;
    return url;
  }

  if (!settings) return (
    <div style={{ padding: 32, color: 'var(--subtext)' }}>
      {error ? 'Error: ' + error : 'Loading settings...'}
    </div>
  );

  const preview = buildPreview();

  return (
    <div className="page">
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ margin: 0 }}>Settings</h1>
        <p style={{ margin: 0, color: 'var(--subtext)', fontSize: 14 }}>
          Configure SMS, OTP rules, subscriptions and admin password
        </p>
      </div>

      {savedFlag && (
        <div style={{ padding: '10px 16px', borderRadius: 6, background: 'rgba(34,197,94,0.15)',
          color: '#22c55e', marginBottom: 16, fontSize: 14 }}>
          Settings saved
        </div>
      )}
      {error && (
        <div style={{ padding: '10px 16px', borderRadius: 6, background: 'rgba(239,68,68,0.15)',
          color: '#ef4444', marginBottom: 16, fontSize: 14 }}>
          {error}
        </div>
      )}

      <form onSubmit={handleSave}>

        {/* SMS Provider */}
        <div className="card" style={{ marginBottom: 16 }}>
          <h2 style={{ marginTop: 0 }}>SMS Provider</h2>
          <p style={{ color: 'var(--subtext)', fontSize: 13, marginBottom: 16 }}>
            Legacy provider settings. Use the SMS API section below for URL-based gateways.
          </p>
          <div style={grid2}>
            <div style={fieldStyle}>
              <label style={labelStyle}>Provider</label>
              <select value={settings.sms_provider || 'none'}
                onChange={e => upd('sms_provider', e.target.value)} style={inp}>
                <option value="none">None (dev mode - OTP on screen)</option>
                <option value="custom_url">Custom URL API</option>
                <option value="twilio">Twilio</option>
                <option value="msg91">MSG91</option>
              </select>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Sender ID</label>
              <input value={settings.sms_sender_id || ''}
                onChange={e => upd('sms_sender_id', e.target.value)}
                placeholder="e.g. CALFLT" style={inp}/>
            </div>
          </div>
        </div>

        {/* OTP Rules */}
        <div className="card" style={{ marginBottom: 16 }}>
          <h2 style={{ marginTop: 0 }}>OTP Rules</h2>
          <div style={grid2}>
            <div style={fieldStyle}>
              <label style={labelStyle}>Code Length</label>
              <select value={settings.otp_length || '6'}
                onChange={e => upd('otp_length', e.target.value)} style={inp}>
                <option value="4">4 digits</option>
                <option value="5">5 digits</option>
                <option value="6">6 digits</option>
                <option value="8">8 digits</option>
              </select>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Expiry (minutes)</label>
              <input type="number" min="1" max="60"
                value={settings.otp_expiry_minutes || '5'}
                onChange={e => upd('otp_expiry_minutes', e.target.value)} style={inp}/>
            </div>
          </div>
        </div>

        {/* Subscription */}
        <div className="card" style={{ marginBottom: 16 }}>
          <h2 style={{ marginTop: 0 }}>Subscription Gating</h2>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
            <input type="checkbox"
              checked={settings.subscription_required === 'true'}
              onChange={e => upd('subscription_required', e.target.checked ? 'true' : 'false')}/>
            <span style={{ fontSize: 14 }}>Require active subscription</span>
          </label>
          {settings.subscription_required !== 'true' && (
            <p style={{ margin: '8px 0 0', fontSize: 13, color: '#22c55e' }}>
              Subscription gating OFF  -  all users have unrestricted access.
            </p>
          )}
        </div>

        {/* Razorpay */}
        <div className="card" style={{ marginBottom: 16 }}>
          <h2 style={{ marginTop: 0 }}>Razorpay (sideload payments)</h2>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', marginBottom: 16 }}>
            <input type="checkbox"
              checked={settings.razorpay_enabled === 'true'}
              onChange={e => upd('razorpay_enabled', e.target.checked ? 'true' : 'false')}/>
            <span style={{ fontSize: 14 }}>Enable Razorpay</span>
          </label>
          <div style={grid2}>
            <div style={{ ...fieldStyle, gridColumn: '1 / -1' }}>
              <label style={labelStyle}>Mode</label>
              <select value={settings.razorpay_mode || 'test'}
                onChange={e => upd('razorpay_mode', e.target.value)} style={inp}>
                <option value="test">Test mode</option>
                <option value="live">Live mode</option>
              </select>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Test Key ID</label>
              <input value={settings.razorpay_key_id_test || ''}
                onChange={e => upd('razorpay_key_id_test', e.target.value)}
                placeholder="rzp_test_..." style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Test Secret</label>
              <input type="password" value={settings.razorpay_secret_test || ''}
                onChange={e => upd('razorpay_secret_test', e.target.value)}
                placeholder="Test secret" style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Live Key ID</label>
              <input value={settings.razorpay_key_id_live || ''}
                onChange={e => upd('razorpay_key_id_live', e.target.value)}
                placeholder="rzp_live_..." style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Live Secret</label>
              <input type="password" value={settings.razorpay_secret_live || ''}
                onChange={e => upd('razorpay_secret_live', e.target.value)}
                placeholder="Live secret" style={inp}/>
            </div>
            <div style={{ ...fieldStyle, gridColumn: '1 / -1' }}>
              <label style={labelStyle}>Webhook Secret</label>
              <input type="password" value={settings.razorpay_webhook_secret || ''}
                onChange={e => upd('razorpay_webhook_secret', e.target.value)}
                placeholder="Webhook verification secret" style={inp}/>
              <span style={smallStyle}>
                Webhook URL: https://api.app.onephone.pro/api/razorpay/webhook
              </span>
            </div>
          </div>
        </div>

        {/* SMS API */}
        <div className="card" style={{ marginBottom: 16 }}>
          <h2 style={{ marginTop: 0 }}>SMS API Configuration</h2>
          <p style={{ color: 'var(--subtext)', fontSize: 13, marginBottom: 8 }}>
            URL-based SMS gateway for OTP delivery.
          </p>
          <p style={{ color: 'var(--subtext)', fontSize: 12, marginBottom: 16,
            fontFamily: 'monospace', background: 'var(--surface)',
            padding: '6px 10px', borderRadius: 4, wordBreak: 'break-all' }}>
            Example: https://sms.mudunuru.com/SendSMS.aspx?userid=X
            {'&'}password=Y{'&'}mobileno=91XXXXXXXXXX{'&'}sendername=SENDER
            {'&'}message=OTP...{'&'}category=2{'&'}templateid=123456
          </p>

          <div style={grid2}>
            <div style={{ ...fieldStyle, gridColumn: '1 / -1' }}>
              <label style={labelStyle}>SMS API Base URL</label>
              <input value={settings.sms_api_url || ''}
                onChange={e => upd('sms_api_url', e.target.value)}
                placeholder="https://sms.mudunuru.com/SendSMS.aspx" style={inp}/>
              <span style={smallStyle}>Base URL without any parameters</span>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>User ID (userid param)</label>
              <input value={settings.sms_api_userid || ''}
                onChange={e => upd('sms_api_userid', e.target.value)}
                placeholder="myerppro" style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Password (password param)</label>
              <input type="password" value={settings.sms_api_password || ''}
                onChange={e => upd('sms_api_password', e.target.value)}
                placeholder="API password" style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Sender Name (sendername param)</label>
              <input value={settings.sms_api_sender_name || ''}
                onChange={e => upd('sms_api_sender_name', e.target.value)}
                placeholder="BZIONX" style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Sender Number (sendernumber param)</label>
              <input value={settings.sms_api_sender_number || ''}
                onChange={e => upd('sms_api_sender_number', e.target.value)}
                placeholder="9493333747" style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Category (category param)</label>
              <input value={settings.sms_api_category || ''}
                onChange={e => upd('sms_api_category', e.target.value)}
                placeholder="2" style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Template ID (templateid param)</label>
              <input value={settings.sms_api_template_id || ''}
                onChange={e => upd('sms_api_template_id', e.target.value)}
                placeholder="1207175299467151781" style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Mobile Param Name</label>
              <input value={settings.sms_api_mobile_param || 'mobileno'}
                onChange={e => upd('sms_api_mobile_param', e.target.value)}
                placeholder="mobileno" style={inp}/>
              <span style={smallStyle}>Default: mobileno</span>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>Message Param Name</label>
              <input value={settings.sms_api_message_param || 'message'}
                onChange={e => upd('sms_api_message_param', e.target.value)}
                placeholder="message" style={inp}/>
              <span style={smallStyle}>Default: message</span>
            </div>
            <div style={{ ...fieldStyle, gridColumn: '1 / -1' }}>
              <label style={labelStyle}>OTP Message Template</label>
              <textarea rows={3}
                value={settings.sms_api_message_template || ''}
                onChange={e => upd('sms_api_message_template', e.target.value)}
                placeholder="{OTP} is your OTP to verify your phone number. Do not share with anyone."
                style={{ ...inp, resize: 'vertical' }}/>
              <span style={smallStyle}>
                Use {'{OTP}'} as the placeholder  -  replaced with the actual code when sending
              </span>
            </div>
          </div>

          {preview !== '' && (
            <div style={{ marginTop: 16, padding: 12, background: 'var(--surface)',
              borderRadius: 6, fontSize: 11 }}>
              <div style={{ color: 'var(--subtext)', fontWeight: 600, marginBottom: 6 }}>
                URL Preview (OTP = 123456):
              </div>
              <div style={{ color: 'var(--text)', wordBreak: 'break-all', fontFamily: 'monospace' }}>
                {preview}
              </div>
            </div>
          )}

          <div style={{ marginTop: 20, padding: 16, background: 'var(--surface)', borderRadius: 8 }}>
            <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 6 }}>Test SMS</div>
            <p style={{ color: 'var(--subtext)', fontSize: 13, margin: '0 0 10px' }}>
              Save settings first, then send a test OTP (123456) to verify your SMS API.
            </p>
            <div style={{ display: 'flex', gap: 8 }}>
              <input value={testMobile}
                onChange={e => setTestMobile(e.target.value)}
                placeholder="+919876543210 or 919876543210"
                style={{ ...inp, flex: 1 }}/>
              <button type="button" onClick={handleTestSms} disabled={testSending}
                style={{ padding: '8px 18px', borderRadius: 6, border: 'none',
                  background: '#22c55e', color: '#fff', fontWeight: 600,
                  cursor: testSending ? 'not-allowed' : 'pointer',
                  opacity: testSending ? 0.6 : 1, fontSize: 13, whiteSpace: 'nowrap' }}>
                {testSending ? 'Sending...' : 'Send Test'}
              </button>
            </div>
            {testResult && (
              <p style={{ margin: '8px 0 0', fontSize: 13,
                color: testResult.startsWith('Sent') ? '#22c55e' : '#ef4444' }}>
                {testResult}
              </p>
            )}
          </div>
        </div>

        <button type="submit" disabled={saving}
          style={{ padding: '10px 24px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 600,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
          {saving ? 'Saving...' : 'Save Settings'}
        </button>
      </form>

      <form onSubmit={handlePassword} style={{ marginTop: 24 }}>
        <div className="card">
          <h2 style={{ marginTop: 0 }}>Change Admin Password</h2>
          {pwMsg && (
            <p style={{ fontSize: 13, color: pwMsg === 'Password updated' ? '#22c55e' : '#ef4444',
              margin: '0 0 12px' }}>
              {pwMsg}
            </p>
          )}
          <div style={grid2}>
            <div style={fieldStyle}>
              <label style={labelStyle}>Current Password</label>
              <input type="password" value={pwCurrent}
                onChange={e => setPwCurrent(e.target.value)} required style={inp}/>
            </div>
            <div style={fieldStyle}>
              <label style={labelStyle}>New Password (min 6 chars)</label>
              <input type="password" value={pwNew}
                onChange={e => setPwNew(e.target.value)} required minLength={6} style={inp}/>
            </div>
          </div>
          <button type="submit"
            style={{ marginTop: 16, padding: '9px 20px', borderRadius: 6, border: 'none',
              background: 'var(--surface)', color: 'var(--text)', fontWeight: 600,
              cursor: 'pointer', fontSize: 14, border: '1px solid var(--border)' }}>
            Update Password
          </button>
        </div>
      </form>
    </div>
  );
}
