import React, { useEffect, useState } from 'react';
import { api } from '../api.js';

const inp = {
  padding: '8px 12px', borderRadius: 6, border: '1px solid var(--border)',
  background: 'var(--surface)', color: 'var(--text)',
  fontSize: 14, width: '100%', boxSizing: 'border-box',
};
const fld  = { display: 'flex', flexDirection: 'column', gap: 4 };
const lbl  = { fontSize: 12, fontWeight: 600, color: 'var(--subtext)', textTransform: 'uppercase' };
const hint = { fontSize: 11, color: 'var(--subtext)' };
const g2   = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 };

function Card({ title, children, style }) {
  return (
    <div className="card" style={{ marginBottom: 16, ...style }}>
      {title && <h2 style={{ marginTop: 0, marginBottom: 16, fontSize: 16 }}>{title}</h2>}
      {children}
    </div>
  );
}

function Field({ label, hint: h, span, children }) {
  return (
    <div style={{ ...fld, ...(span ? { gridColumn: '1 / -1' } : {}) }}>
      {label && <label style={lbl}>{label}</label>}
      {children}
      {h && <span style={hint}>{h}</span>}
    </div>
  );
}

function VarBadge({ label }) {
  return (
    <span style={{ background: 'rgba(79,142,247,0.18)', color: '#4f8ef7',
      borderRadius: 4, padding: '1px 6px', fontSize: 10, fontWeight: 700,
      marginLeft: 6, verticalAlign: 'middle' }}>
      {label || 'VARIABLE'}
    </span>
  );
}

function FixedBadge() {
  return (
    <span style={{ background: 'rgba(107,114,128,0.18)', color: '#6b7280',
      borderRadius: 4, padding: '1px 6px', fontSize: 10, fontWeight: 700,
      marginLeft: 6, verticalAlign: 'middle' }}>
      FIXED
    </span>
  );
}

export default function Settings() {
  const [settings, setSettings]   = useState(null);
  const [saving, setSaving]       = useState(false);
  const [saved, setSaved]         = useState(false);
  const [error, setError]         = useState('');
  const [pwCur, setPwCur]         = useState('');
  const [pwNew, setPwNew]         = useState('');
  const [pwMsg, setPwMsg]         = useState('');
  const [testNum, setTestNum]     = useState('');
  const [testMsg, setTestMsg]     = useState('');
  const [testing, setTesting]     = useState(false);

  useEffect(() => {
    api.get('/admin/settings')
      .then(r => setSettings(r.settings || r))
      .catch(e => setError(e.message || 'Failed to load'));
  }, []);

  function set(k, v) { setSettings(s => ({ ...s, [k]: v })); }

  async function save(e) {
    e.preventDefault(); setSaving(true); setError('');
    try {
      await api.put('/admin/settings', settings);
      setSaved(true); setTimeout(() => setSaved(false), 3000);
    } catch (e) { setError(e.message || 'Save failed'); }
    finally { setSaving(false); }
  }

  async function changePw(e) {
    e.preventDefault(); setPwMsg('');
    try {
      await api.post('/admin/change-password', { current: pwCur, next: pwNew });
      setPwMsg('Password updated'); setPwCur(''); setPwNew('');
    } catch (e) { setPwMsg(e.message || 'Failed'); }
  }

  async function sendTest() {
    if (!testNum.trim()) { setTestMsg('Enter a mobile number'); return; }
    setTesting(true); setTestMsg('');
    try {
      const r = await api.post('/admin/test-sms', { mobile: testNum.trim() });
      setTestMsg('Sent - API response: ' + (r.response || 'OK') + ' (HTTP ' + r.status + ')');
    } catch (e) { setTestMsg('Error: ' + (e.message || 'Failed')); }
    finally { setTesting(false); }
  }

  function previewUrl() {
    if (!settings || !settings.sms_api_url) return '';
    const mob  = settings.sms_api_mobile_param  || 'mobileno';
    const msgP = settings.sms_api_message_param || 'message';
    const msg  = (settings.sms_api_message_template || '{OTP} is your OTP')
                   .replace('{OTP}', '123456');
    const strip = settings.sms_api_strip_country_code !== 'false';
    const mobileEx = strip ? '9876543210' : '919876543210';
    let u = settings.sms_api_url + '?'
      + 'userid='     + (settings.sms_api_userid       || 'USERID')
      + '&password='  + (settings.sms_api_password      ? '***' : 'PASSWORD')
      + '&' + mob     + '=' + mobileEx
      + '&sendername='+ (settings.sms_api_sender_name   || 'SENDER');
    if (settings.sms_api_sender_number)
      u += '&sendernumber=' + settings.sms_api_sender_number;
    u += '&' + msgP + '=' + encodeURIComponent(msg);
    if (settings.sms_api_category)    u += '&category='   + settings.sms_api_category;
    if (settings.sms_api_template_id) u += '&templateid=' + settings.sms_api_template_id;
    return u;
  }

  if (!settings) return (
    <div style={{ padding: 32, color: 'var(--subtext)', fontSize: 14 }}>
      {error ? 'Error loading settings: ' + error : 'Loading...'}
    </div>
  );

  const preview = previewUrl();

  return (
    <div className="page">
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ margin: 0 }}>Settings</h1>
        <p style={{ margin: '4px 0 0', color: 'var(--subtext)', fontSize: 14 }}>
          Configure SMS gateway, OTP rules, subscriptions and admin password
        </p>
      </div>

      {saved && (
        <div style={{ padding: '10px 16px', borderRadius: 6, marginBottom: 16, fontSize: 14,
          background: 'rgba(34,197,94,0.15)', color: '#22c55e' }}>
          Settings saved successfully
        </div>
      )}
      {error && (
        <div style={{ padding: '10px 16px', borderRadius: 6, marginBottom: 16, fontSize: 14,
          background: 'rgba(239,68,68,0.15)', color: '#ef4444' }}>
          {error}
        </div>
      )}

      <form onSubmit={save}>

        {/* -- SMS API -- */}
        <Card title="SMS API Configuration">
          <p style={{ color: 'var(--subtext)', fontSize: 13, marginTop: -8, marginBottom: 20 }}>
            URL-based SMS gateway. Only
            <span style={{ color: '#4f8ef7', fontWeight: 700 }}> mobile number </span>
            and the
            <span style={{ color: '#4f8ef7', fontWeight: 700 }}> OTP value </span>
            change per message. Everything else is fixed config.
          </p>

          {/* Fixed config fields */}
          <div style={{ ...g2, marginBottom: 16 }}>
            <Field label="API Base URL" hint="URL without any parameters" span>
              <input value={settings.sms_api_url || ''}
                onChange={e => set('sms_api_url', e.target.value)}
                placeholder="https://sms.mudunuru.com/SendSMS.aspx" style={inp}/>
            </Field>
            <Field label={<span>User ID <FixedBadge/></span>}>
              <input value={settings.sms_api_userid || ''}
                onChange={e => set('sms_api_userid', e.target.value)}
                placeholder="myerppro" style={inp}/>
            </Field>
            <Field label={<span>Password <FixedBadge/></span>}>
              <input type="password" value={settings.sms_api_password || ''}
                onChange={e => set('sms_api_password', e.target.value)}
                placeholder="myerppro_2023" style={inp}/>
            </Field>
            <Field label={<span>Sender Name (sendername) <FixedBadge/></span>}>
              <input value={settings.sms_api_sender_name || ''}
                onChange={e => set('sms_api_sender_name', e.target.value)}
                placeholder="BZIONX" style={inp}/>
            </Field>
            <Field label={<span>Sender Number (sendernumber) <FixedBadge/></span>}>
              <input value={settings.sms_api_sender_number || ''}
                onChange={e => set('sms_api_sender_number', e.target.value)}
                placeholder="9493333747" style={inp}/>
            </Field>
            <Field label={<span>Category <FixedBadge/></span>}>
              <input value={settings.sms_api_category || ''}
                onChange={e => set('sms_api_category', e.target.value)}
                placeholder="2" style={inp}/>
            </Field>
            <Field label={<span>Template ID <FixedBadge/></span>}>
              <input value={settings.sms_api_template_id || ''}
                onChange={e => set('sms_api_template_id', e.target.value)}
                placeholder="1207175299467151781" style={inp}/>
            </Field>
          </div>

          {/* Variable fields - highlighted */}
          <div style={{ padding: 14, borderRadius: 8,
            border: '1px solid rgba(79,142,247,0.35)',
            background: 'rgba(79,142,247,0.06)', marginBottom: 16 }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: '#4f8ef7', marginBottom: 12 }}>
              VARIABLE FIELDS - change per SMS
            </div>
            <div style={{ ...g2 }}>
              <Field
                label={<span>Mobile Param Name <VarBadge label="= phone number"/></span>}
                hint="Parameter name for the recipient mobile number">
                <input value={settings.sms_api_mobile_param || 'mobileno'}
                  onChange={e => set('sms_api_mobile_param', e.target.value)}
                  placeholder="mobileno" style={inp}/>
              </Field>
              <Field
                label={<span>Message Param Name <VarBadge label="= OTP message"/></span>}
                hint="Parameter name for the SMS text content">
                <input value={settings.sms_api_message_param || 'message'}
                  onChange={e => set('sms_api_message_param', e.target.value)}
                  placeholder="message" style={inp}/>
              </Field>
              <Field span
                label={<span>OTP Message Template <VarBadge label="{OTP} = generated code"/></span>}
                hint="Use {OTP} where the 6-digit code should appear">
                <textarea rows={2}
                  value={settings.sms_api_message_template || ''}
                  onChange={e => set('sms_api_message_template', e.target.value)}
                  placeholder="{OTP} is your OTP to verify the phone number at yoursite.com Please do not share OTP with anyone. Your Team."
                  style={{ ...inp, resize: 'vertical' }}/>
              </Field>
            </div>
          </div>

          {/* Mobile number format */}
          <div style={{ padding: 12, borderRadius: 8, background: 'var(--surface)',
            marginBottom: 16 }}>
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--subtext)', marginBottom: 8 }}>
              MOBILE NUMBER FORMAT
            </div>
            <label style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer' }}>
              <input type="checkbox"
                checked={settings.sms_api_strip_country_code !== 'false'}
                onChange={e => set('sms_api_strip_country_code', e.target.checked ? 'true' : 'false')}/>
              <span style={{ fontSize: 13 }}>
                Send 10-digit number only (strip country code)
              </span>
            </label>
            <p style={{ margin: '6px 0 0 26px', fontSize: 12, color: 'var(--subtext)' }}>
              {settings.sms_api_strip_country_code !== 'false'
                ? 'Mobile sent as: 9876543210 (10 digits, no country code like 91)'
                : 'Mobile sent as: 919876543210 (with country code)'}
            </p>
          </div>

          {/* URL Preview */}
          {preview && (
            <div style={{ padding: 12, background: 'var(--surface)', borderRadius: 6,
              marginBottom: 16, fontSize: 11 }}>
              <div style={{ fontWeight: 700, color: 'var(--subtext)', marginBottom: 6 }}>
                URL Preview (OTP = 123456):
              </div>
              <div style={{ wordBreak: 'break-all', fontFamily: 'monospace', color: 'var(--text)',
                lineHeight: 1.6 }}>
                {preview}
              </div>
            </div>
          )}

          {/* Test SMS */}
          <div style={{ padding: 14, background: 'var(--surface)', borderRadius: 8 }}>
            <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 6 }}>Test SMS</div>
            <p style={{ color: 'var(--subtext)', fontSize: 13, margin: '0 0 10px' }}>
              Save settings first. Sends OTP 123456 to the number you enter.
            </p>
            <div style={{ display: 'flex', gap: 8 }}>
              <input value={testNum}
                onChange={e => setTestNum(e.target.value)}
                placeholder="9876543210 (10-digit)"
                style={{ ...inp, flex: 1 }}/>
              <button type="button" onClick={sendTest} disabled={testing}
                style={{ padding: '8px 18px', borderRadius: 6, border: 'none',
                  background: '#22c55e', color: '#fff', fontWeight: 600,
                  cursor: testing ? 'not-allowed' : 'pointer',
                  opacity: testing ? 0.6 : 1, fontSize: 13, whiteSpace: 'nowrap' }}>
                {testing ? 'Sending...' : 'Send Test OTP'}
              </button>
            </div>
            {testMsg && (
              <p style={{ margin: '8px 0 0', fontSize: 13,
                color: testMsg.startsWith('Sent') ? '#22c55e' : '#ef4444' }}>
                {testMsg}
              </p>
            )}
          </div>
        </Card>

        {/* -- OTP Rules -- */}
        <Card title="OTP Rules">
          <div style={g2}>
            <Field label="Code Length">
              <select value={settings.otp_length || '6'}
                onChange={e => set('otp_length', e.target.value)} style={inp}>
                <option value="4">4 digits</option>
                <option value="5">5 digits</option>
                <option value="6">6 digits</option>
                <option value="8">8 digits</option>
              </select>
            </Field>
            <Field label="Expiry (minutes)">
              <input type="number" min="1" max="60"
                value={settings.otp_expiry_minutes || '5'}
                onChange={e => set('otp_expiry_minutes', e.target.value)} style={inp}/>
            </Field>
          </div>
        </Card>

        {/* -- Subscription -- */}
        <Card title="Subscription Gating">
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
            <input type="checkbox"
              checked={settings.subscription_required === 'true'}
              onChange={e => set('subscription_required', e.target.checked ? 'true' : 'false')}/>
            <span style={{ fontSize: 14 }}>Require active subscription</span>
          </label>
          <p style={{ margin: '8px 0 0', fontSize: 13,
            color: settings.subscription_required === 'true' ? '#4f8ef7' : '#22c55e' }}>
            {settings.subscription_required === 'true'
              ? 'ON - users need a subscription to use the app.'
              : 'OFF - all users have unrestricted access.'}
          </p>
        </Card>

        {/* -- Razorpay -- */}
        <Card title="Razorpay (sideload payments)">
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', marginBottom: 16 }}>
            <input type="checkbox"
              checked={settings.razorpay_enabled === 'true'}
              onChange={e => set('razorpay_enabled', e.target.checked ? 'true' : 'false')}/>
            <span style={{ fontSize: 14 }}>Enable Razorpay</span>
          </label>
          <div style={g2}>
            <Field label="Mode" span>
              <select value={settings.razorpay_mode || 'test'}
                onChange={e => set('razorpay_mode', e.target.value)} style={inp}>
                <option value="test">Test mode (rzp_test_...)</option>
                <option value="live">Live mode (rzp_live_...)</option>
              </select>
            </Field>
            <Field label="Test Key ID">
              <input value={settings.razorpay_key_id_test || ''}
                onChange={e => set('razorpay_key_id_test', e.target.value)}
                placeholder="rzp_test_..." style={inp}/>
            </Field>
            <Field label="Test Secret">
              <input type="password" value={settings.razorpay_secret_test || ''}
                onChange={e => set('razorpay_secret_test', e.target.value)}
                placeholder="Test secret" style={inp}/>
            </Field>
            <Field label="Live Key ID">
              <input value={settings.razorpay_key_id_live || ''}
                onChange={e => set('razorpay_key_id_live', e.target.value)}
                placeholder="rzp_live_..." style={inp}/>
            </Field>
            <Field label="Live Secret">
              <input type="password" value={settings.razorpay_secret_live || ''}
                onChange={e => set('razorpay_secret_live', e.target.value)}
                placeholder="Live secret" style={inp}/>
            </Field>
            <Field label="Webhook Secret" span
              hint="Webhook URL: https://api.app.onephone.pro/api/razorpay/webhook">
              <input type="password" value={settings.razorpay_webhook_secret || ''}
                onChange={e => set('razorpay_webhook_secret', e.target.value)}
                placeholder="Webhook secret" style={inp}/>
            </Field>
          </div>
        </Card>

        <button type="submit" disabled={saving}
          style={{ padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
          {saving ? 'Saving...' : 'Save Settings'}
        </button>
      </form>

      {/* -- Change Password -- */}
      <form onSubmit={changePw} style={{ marginTop: 24 }}>
        <Card title="Change Admin Password">
          {pwMsg && (
            <p style={{ fontSize: 13, marginBottom: 12,
              color: pwMsg === 'Password updated' ? '#22c55e' : '#ef4444' }}>
              {pwMsg}
            </p>
          )}
          <div style={g2}>
            <Field label="Current Password">
              <input type="password" value={pwCur}
                onChange={e => setPwCur(e.target.value)} required style={inp}/>
            </Field>
            <Field label="New Password (min 6 chars)">
              <input type="password" value={pwNew}
                onChange={e => setPwNew(e.target.value)} required minLength={6} style={inp}/>
            </Field>
          </div>
          <button type="submit"
            style={{ marginTop: 16, padding: '9px 20px', borderRadius: 6,
              border: '1px solid var(--border)', background: 'var(--surface)',
              color: 'var(--text)', fontWeight: 600, cursor: 'pointer', fontSize: 14 }}>
            Update Password
          </button>
        </Card>
      </form>
    </div>
  );
}
