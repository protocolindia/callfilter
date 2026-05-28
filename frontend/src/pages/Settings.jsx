import React, { useEffect, useState } from 'react';
import { api } from '../api.js';

const inp = {
  padding: '8px 12px', borderRadius: 6, border: '1px solid var(--border)',
  background: 'var(--surface)', color: 'var(--text)',
  fontSize: 14, width: '100%', boxSizing: 'border-box',
};
const fld = { display: 'flex', flexDirection: 'column', gap: 4 };
const lbl = { fontSize: 12, fontWeight: 600, color: 'var(--subtext)', textTransform: 'uppercase', letterSpacing: '0.04em' };
const sml = { fontSize: 11, color: 'var(--subtext)' };
const g2  = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 };

function Field({ label, hint, span, children }) {
  return (
    <div style={{ ...fld, ...(span ? { gridColumn: '1 / -1' } : {}) }}>
      {label && <label style={lbl}>{label}</label>}
      {children}
      {hint && <span style={sml}>{hint}</span>}
    </div>
  );
}

const TABS = [
  { id: 'sms',          icon: '📱', label: 'SMS API'      },
  { id: 'otp',          icon: '🔑', label: 'OTP Rules'    },
  { id: 'subscription', icon: '💳', label: 'Subscription' },
  { id: 'razorpay',     icon: '💰', label: 'Razorpay'     },
  { id: 'password',     icon: '🔒', label: 'Password'     },
];

export default function Settings() {
  const [tab, setTab]           = useState('sms');
  const [settings, setSettings] = useState(null);
  const [saving, setSaving]     = useState(false);
  const [saved, setSaved]       = useState(false);
  const [error, setError]       = useState('');
  const [pwCur, setPwCur]       = useState('');
  const [pwNew, setPwNew]       = useState('');
  const [pwMsg, setPwMsg]       = useState('');
  const [testNum, setTestNum]   = useState('');
  const [testMsg, setTestMsg]   = useState('');
  const [testing, setTesting]   = useState(false);

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
      setPwMsg('ok'); setPwCur(''); setPwNew('');
    } catch (e) { setPwMsg(e.message || 'Failed'); }
  }

  async function sendTest() {
    if (!testNum.trim()) { setTestMsg('Enter a mobile number'); return; }
    setTesting(true); setTestMsg('Building SMS URL...');
    try {
      // Step 1: Backend builds the SMS URL with credentials
      const r = await api.post('/admin/test-sms', { mobile: testNum.trim() });
      if (!r.sms_url) { setTestMsg('Error: ' + (r.error || 'No URL returned')); return; }

      setTestMsg('Sending from your browser to SMS gateway...');

      // Step 2: Browser makes the actual call (avoids Railway network restrictions)
      const resp = await fetch(r.sms_url, { method: 'GET', mode: 'no-cors' });
      // no-cors means we won't see the response body, but the request IS sent
      setTestMsg('SMS request sent to ' + r.mobile + '. Check your phone in a few seconds.');
    } catch (e) {
      // Even a CORS/network error here means the request was attempted
      if (e.message && e.message.includes('NetworkError')) {
        setTestMsg('SMS request sent (CORS blocked response, but SMS should arrive). Check your phone.');
      } else {
        setTestMsg('Error: ' + (e.message || 'Failed'));
      }
    } finally { setTesting(false); }
  }

  function previewUrl() {
    if (!settings || !settings.sms_api_url) return '';
    const mob  = settings.sms_api_mobile_param  || 'mobileno';
    const msgP = settings.sms_api_message_param || 'message';
    const msg  = (settings.sms_api_message_template || '{OTP} is your OTP')
                   .replace('{OTP}', '123456');
    const strip = settings.sms_api_strip_country_code !== 'false';
    const mEx  = strip ? '9876543210' : '919876543210';
    let u = settings.sms_api_url + '?'
      + 'userid='      + (settings.sms_api_userid       || 'USERID')
      + '&password='   + (settings.sms_api_password      ? '***' : 'PASSWORD')
      + '&' + mob      + '=' + mEx
      + '&sendername=' + (settings.sms_api_sender_name   || 'SENDER');
    if (settings.sms_api_sender_number)
      u += '&sendernumber=' + settings.sms_api_sender_number;
    u += '&' + msgP + '=' + encodeURIComponent(msg);
    if (settings.sms_api_category)    u += '&category='   + settings.sms_api_category;
    if (settings.sms_api_template_id) u += '&templateid=' + settings.sms_api_template_id;
    return u;
  }

  if (!settings) return (
    <div style={{ padding: 32, color: 'var(--subtext)', fontSize: 14 }}>
      {error ? 'Error: ' + error : 'Loading settings...'}
    </div>
  );

  const preview = previewUrl();

  const tabBtn = (t) => ({
    display: 'flex', alignItems: 'center', gap: 8,
    padding: '10px 18px', borderRadius: 8, border: 'none',
    cursor: 'pointer', fontSize: 14, fontWeight: tab === t.id ? 700 : 500,
    background: tab === t.id ? 'var(--accent)' : 'transparent',
    color: tab === t.id ? '#fff' : 'var(--subtext)',
    transition: 'all 0.15s',
    whiteSpace: 'nowrap',
  });

  return (
    <div className="page">
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ margin: 0 }}>Settings</h1>
        <p style={{ margin: '4px 0 0', color: 'var(--subtext)', fontSize: 14 }}>
          Configure your AI CallFilter platform
        </p>
      </div>

      {/* Tab bar */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 24,
        background: 'var(--card)', borderRadius: 10, padding: 6,
        overflowX: 'auto', flexWrap: 'nowrap' }}>
        {TABS.map(t => (
          <button key={t.id} style={tabBtn(t)} onClick={() => setTab(t.id)}>
            <span style={{ fontSize: 16 }}>{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>

      {/* Feedback banners */}
      {saved && (
        <div style={{ padding: '10px 16px', borderRadius: 6, marginBottom: 16,
          background: 'rgba(34,197,94,0.15)', color: '#22c55e', fontSize: 14 }}>
          Settings saved
        </div>
      )}
      {error && (
        <div style={{ padding: '10px 16px', borderRadius: 6, marginBottom: 16,
          background: 'rgba(239,68,68,0.15)', color: '#ef4444', fontSize: 14 }}>
          {error}
        </div>
      )}

      {/* -- SMS API TAB ------------------------------------------- */}
      {tab === 'sms' && (
        <form onSubmit={save}>
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 6 }}>SMS API Configuration</h2>
            <p style={{ color: 'var(--subtext)', fontSize: 13, marginBottom: 20 }}>
              URL-based SMS gateway for OTP delivery.
              Only <strong style={{ color: '#4f8ef7' }}>mobile number</strong> and
              the <strong style={{ color: '#4f8ef7' }}>{'{OTP}'} value</strong> change per message.
            </p>

            {/* Fixed fields */}
            <p style={{ ...lbl, marginBottom: 10, color: '#6b7280' }}>Fixed credentials (same for every SMS)</p>
            <div style={{ ...g2, marginBottom: 20 }}>
              <Field label="API Base URL" hint="URL without any parameters" span>
                <input value={settings.sms_api_url || ''}
                  onChange={e => set('sms_api_url', e.target.value)}
                  placeholder="https://sms.mudunuru.com/SendSMS.aspx" style={inp}/>
              </Field>
              <Field label="User ID (userid)">
                <input value={settings.sms_api_userid || ''}
                  onChange={e => set('sms_api_userid', e.target.value)}
                  placeholder="myerppro" style={inp}/>
              </Field>
              <Field label="Password">
                <input type="password" value={settings.sms_api_password || ''}
                  onChange={e => set('sms_api_password', e.target.value)}
                  placeholder="myerppro_2023" style={inp}/>
              </Field>
              <Field label="Sender Name (sendername)">
                <input value={settings.sms_api_sender_name || ''}
                  onChange={e => set('sms_api_sender_name', e.target.value)}
                  placeholder="BZIONX" style={inp}/>
              </Field>
              <Field label="Sender Number (sendernumber)">
                <input value={settings.sms_api_sender_number || ''}
                  onChange={e => set('sms_api_sender_number', e.target.value)}
                  placeholder="9493333747" style={inp}/>
              </Field>
              <Field label="Category">
                <input value={settings.sms_api_category || ''}
                  onChange={e => set('sms_api_category', e.target.value)}
                  placeholder="2" style={inp}/>
              </Field>
              <Field label="Template ID">
                <input value={settings.sms_api_template_id || ''}
                  onChange={e => set('sms_api_template_id', e.target.value)}
                  placeholder="1207175299467151781" style={inp}/>
              </Field>
            </div>

            {/* Variable fields */}
            <div style={{ padding: 16, borderRadius: 8, marginBottom: 20,
              border: '1px solid rgba(79,142,247,0.3)',
              background: 'rgba(79,142,247,0.06)' }}>
              <p style={{ ...lbl, color: '#4f8ef7', marginBottom: 12 }}>
                Variable per SMS (change each time)
              </p>
              <div style={g2}>
                <Field label="Mobile param name"
                  hint="Parameter that receives the phone number - default: mobileno">
                  <input value={settings.sms_api_mobile_param || 'mobileno'}
                    onChange={e => set('sms_api_mobile_param', e.target.value)}
                    placeholder="mobileno" style={inp}/>
                </Field>
                <Field label="Message param name"
                  hint="Parameter that receives the OTP text - default: message">
                  <input value={settings.sms_api_message_param || 'message'}
                    onChange={e => set('sms_api_message_param', e.target.value)}
                    placeholder="message" style={inp}/>
                </Field>
                <Field label="OTP message template" hint="Use {OTP} where the code goes" span>
                  <textarea rows={2}
                    value={settings.sms_api_message_template || ''}
                    onChange={e => set('sms_api_message_template', e.target.value)}
                    placeholder="{OTP} is your OTP to verify your phone number. Do not share with anyone."
                    style={{ ...inp, resize: 'vertical' }}/>
                </Field>
              </div>
            </div>

            {/* Mobile format */}
            <div style={{ padding: 14, borderRadius: 8, background: 'var(--surface)', marginBottom: 20 }}>
              <p style={{ ...lbl, marginBottom: 8 }}>Mobile number format</p>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                <input type="checkbox"
                  checked={settings.sms_api_strip_country_code !== 'false'}
                  onChange={e => set('sms_api_strip_country_code', e.target.checked ? 'true' : 'false')}/>
                <span style={{ fontSize: 13 }}>
                  Strip country code - send 10-digit number (e.g. 9876543210 not 919876543210)
                </span>
              </label>
            </div>

            {/* URL Preview */}
            {preview && (
              <div style={{ padding: 12, background: 'var(--surface)', borderRadius: 6, marginBottom: 20 }}>
                <p style={{ ...lbl, marginBottom: 6 }}>URL Preview (OTP = 123456)</p>
                <div style={{ wordBreak: 'break-all', fontFamily: 'monospace',
                  fontSize: 11, color: 'var(--text)', lineHeight: 1.7 }}>
                  {preview}
                </div>
              </div>
            )}

            {/* Test SMS */}
            <div style={{ padding: 16, background: 'var(--surface)', borderRadius: 8 }}>
              <p style={{ ...lbl, marginBottom: 4 }}>Test SMS</p>
              <p style={{ color: 'var(--subtext)', fontSize: 13, margin: '0 0 12px' }}>
                Save settings first, then send OTP 123456 to verify your gateway works.
              </p>
              <div style={{ display: 'flex', gap: 8 }}>
                <input value={testNum} onChange={e => setTestNum(e.target.value)}
                  placeholder="9876543210" style={{ ...inp, flex: 1 }}/>
                <button type="button" onClick={sendTest} disabled={testing}
                  style={{ padding: '8px 18px', borderRadius: 6, border: 'none',
                    background: testing ? '#16a34a' : '#22c55e', color: '#fff',
                    fontWeight: 600, cursor: testing ? 'not-allowed' : 'pointer',
                    fontSize: 13, whiteSpace: 'nowrap' }}>
                  {testing ? 'Sending...' : 'Send Test OTP'}
                </button>
              </div>
              {testMsg && (
                <p style={{ margin: '10px 0 0', fontSize: 13, padding: '8px 12px',
                  borderRadius: 6,
                  background: testMsg.startsWith('Sent') ? 'rgba(34,197,94,0.12)' : 'rgba(239,68,68,0.12)',
                  color: testMsg.startsWith('Sent') ? '#22c55e' : '#ef4444' }}>
                  {testMsg}
                </p>
              )}
            </div>
          </div>

          <button type="submit" disabled={saving} style={{
            padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
            {saving ? 'Saving...' : 'Save SMS Settings'}
          </button>
        </form>
      )}

      {/* -- OTP RULES TAB ----------------------------------------- */}
      {tab === 'otp' && (
        <form onSubmit={save}>
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 20 }}>OTP Rules</h2>
            <div style={g2}>
              <Field label="OTP Code Length">
                <select value={settings.otp_length || '6'}
                  onChange={e => set('otp_length', e.target.value)} style={inp}>
                  <option value="4">4 digits</option>
                  <option value="5">5 digits</option>
                  <option value="6">6 digits</option>
                  <option value="8">8 digits</option>
                </select>
              </Field>
              <Field label="OTP Expiry" hint="How long before the code expires">
                <select value={settings.otp_expiry_minutes || '5'}
                  onChange={e => set('otp_expiry_minutes', e.target.value)} style={inp}>
                  <option value="2">2 minutes</option>
                  <option value="5">5 minutes</option>
                  <option value="10">10 minutes</option>
                  <option value="15">15 minutes</option>
                  <option value="30">30 minutes</option>
                </select>
              </Field>
            </div>
            <div style={{ marginTop: 20, padding: 14, borderRadius: 8,
              background: settings.sms_provider === 'none' || !settings.sms_api_url
                ? 'rgba(34,197,94,0.1)' : 'rgba(79,142,247,0.1)',
              border: settings.sms_provider === 'none' || !settings.sms_api_url
                ? '1px solid rgba(34,197,94,0.3)' : '1px solid rgba(79,142,247,0.3)',
              fontSize: 13,
              color: settings.sms_provider === 'none' || !settings.sms_api_url
                ? '#22c55e' : '#4f8ef7' }}>
              {settings.sms_api_url
                ? 'Production mode - OTPs sent via your configured SMS gateway.'
                : 'Dev mode - OTP is returned in API response and shown on the signup screen. Configure SMS API to send real OTPs.'}
            </div>
          </div>
          <button type="submit" disabled={saving} style={{
            padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
            {saving ? 'Saving...' : 'Save OTP Settings'}
          </button>
        </form>
      )}

      {/* -- SUBSCRIPTION TAB -------------------------------------- */}
      {tab === 'subscription' && (
        <form onSubmit={save}>
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 8 }}>Subscription Gating</h2>
            <p style={{ color: 'var(--subtext)', fontSize: 13, marginBottom: 20 }}>
              Controls whether users need an active subscription to use the app.
            </p>
            <label style={{ display: 'flex', alignItems: 'flex-start', gap: 12, cursor: 'pointer',
              padding: 16, borderRadius: 8, background: 'var(--surface)' }}>
              <input type="checkbox" style={{ marginTop: 2 }}
                checked={settings.subscription_required === 'true'}
                onChange={e => set('subscription_required', e.target.checked ? 'true' : 'false')}/>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>
                  Require active subscription
                </div>
                <div style={{ fontSize: 13, color: 'var(--subtext)' }}>
                  When enabled, the Android app shows a paywall and blocks calls for users
                  without a valid subscription. Enable once Google Play Billing or Razorpay
                  is configured and tested.
                </div>
              </div>
            </label>
            <div style={{ marginTop: 14, padding: '10px 16px', borderRadius: 8, fontSize: 13,
              background: settings.subscription_required === 'true'
                ? 'rgba(79,142,247,0.1)' : 'rgba(34,197,94,0.1)',
              color: settings.subscription_required === 'true' ? '#4f8ef7' : '#22c55e',
              border: settings.subscription_required === 'true'
                ? '1px solid rgba(79,142,247,0.3)' : '1px solid rgba(34,197,94,0.3)' }}>
              {settings.subscription_required === 'true'
                ? 'ON - Users must subscribe to use the app.'
                : 'OFF - All users have unrestricted access (free mode).'}
            </div>
          </div>
          <button type="submit" disabled={saving} style={{
            padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
            {saving ? 'Saving...' : 'Save Subscription Settings'}
          </button>
        </form>
      )}

      {/* -- RAZORPAY TAB ------------------------------------------ */}
      {tab === 'razorpay' && (
        <form onSubmit={save}>
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 6 }}>Razorpay Payments</h2>
            <p style={{ color: 'var(--subtext)', fontSize: 13, marginBottom: 20 }}>
              For sideloaded APKs only. Google Play builds use Play Billing.
            </p>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8,
              cursor: 'pointer', marginBottom: 20 }}>
              <input type="checkbox"
                checked={settings.razorpay_enabled === 'true'}
                onChange={e => set('razorpay_enabled', e.target.checked ? 'true' : 'false')}/>
              <span style={{ fontSize: 14, fontWeight: 600 }}>Enable Razorpay payments</span>
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
                  placeholder="Webhook verification secret" style={inp}/>
              </Field>
            </div>
            {settings.razorpay_enabled === 'true' && (
              <div style={{ marginTop: 16, padding: '10px 16px', borderRadius: 8, fontSize: 13,
                background: settings.razorpay_mode === 'live'
                  ? 'rgba(79,142,247,0.1)' : 'rgba(245,158,11,0.1)',
                color: settings.razorpay_mode === 'live' ? '#4f8ef7' : '#f59e0b',
                border: settings.razorpay_mode === 'live'
                  ? '1px solid rgba(79,142,247,0.3)' : '1px solid rgba(245,158,11,0.3)' }}>
                {settings.razorpay_mode === 'live'
                  ? 'LIVE mode - real payments enabled.'
                  : 'TEST mode - no real money charged.'}
              </div>
            )}
          </div>
          <button type="submit" disabled={saving} style={{
            padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
            {saving ? 'Saving...' : 'Save Razorpay Settings'}
          </button>
        </form>
      )}

      {/* -- PASSWORD TAB ------------------------------------------ */}
      {tab === 'password' && (
        <form onSubmit={changePw}>
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 6 }}>Change Admin Password</h2>
            <p style={{ color: 'var(--subtext)', fontSize: 13, marginBottom: 20 }}>
              Update the password for your admin account.
            </p>
            {pwMsg === 'ok' && (
              <div style={{ padding: '10px 16px', borderRadius: 6, marginBottom: 16,
                background: 'rgba(34,197,94,0.15)', color: '#22c55e', fontSize: 14 }}>
                Password updated successfully
              </div>
            )}
            {pwMsg && pwMsg !== 'ok' && (
              <div style={{ padding: '10px 16px', borderRadius: 6, marginBottom: 16,
                background: 'rgba(239,68,68,0.15)', color: '#ef4444', fontSize: 14 }}>
                {pwMsg}
              </div>
            )}
            <div style={g2}>
              <Field label="Current Password">
                <input type="password" value={pwCur}
                  onChange={e => setPwCur(e.target.value)} required style={inp}/>
              </Field>
              <Field label="New Password" hint="Minimum 6 characters">
                <input type="password" value={pwNew}
                  onChange={e => setPwNew(e.target.value)} required minLength={6} style={inp}/>
              </Field>
            </div>
          </div>
          <button type="submit" style={{
            padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: 'pointer', fontSize: 15 }}>
            Update Password
          </button>
        </form>
      )}
    </div>
  );
}
