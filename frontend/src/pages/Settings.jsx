import React, { useEffect, useState } from 'react';
import { api, hasPermission } from '../api.js';
import RichTextEditor from '../components/RichTextEditor.jsx';

const PLACEHOLDERS = ['number','reporter','caller_name','block_reason','category','date'];

const inp = {
  padding: '8px 12px', borderRadius: 6, border: '1px solid var(--border)',
  background: 'var(--surface)', color: 'var(--text)',
  fontSize: 14, width: '100%', boxSizing: 'border-box',
};
const fld = { display: 'flex', flexDirection: 'column', gap: 4 };
const lbl = { fontSize: 11, fontWeight: 700, color: 'var(--subtext)',
              textTransform: 'uppercase', letterSpacing: '0.05em' };
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
  { id: 'sms',          icon: '📱', label: 'SMS',          perm: 'settings.sms'          },
  { id: 'otp',          icon: '🔑', label: 'OTP Rules',    perm: 'settings.otp'          },
  { id: 'subscription', icon: '💳', label: 'Subscription', perm: 'settings.subscription' },
  { id: 'razorpay',     icon: '💰', label: 'Razorpay',     perm: 'settings.razorpay'     },
  { id: 'contacts',     icon: '📇', label: 'Contacts Sync',perm: 'settings.contacts'     },
  { id: 'fraud',        icon: '🚩', label: 'Fraud Reports',perm: 'settings.fraud'        },
  { id: 'password',     icon: '🔒', label: 'Password',     perm: 'settings.password'     },
];

const PROVIDERS = [
  { id: 'custom_url', icon: '🔗', name: 'Custom URL',
    desc: 'Any HTTP GET-based SMS gateway (mudunuru, etc.)' },
  { id: 'twilio',     icon: '📞', name: 'Twilio',
    desc: 'Twilio SMS - global coverage, reliable delivery' },
  { id: 'msg91',      icon: '📨', name: 'MSG91',
    desc: 'MSG91 - popular Indian SMS provider' },
];

export default function Settings() {
  // Per-tab gating. A tab shows if the role has its specific permission.
  // Backward-compat: if the role has the broad 'settings.edit', or hasn't been
  // given ANY specific settings.<tab> permission yet, show all tabs.
  const anyTabPerm = TABS.some(t => hasPermission(t.perm));
  const seesAll = hasPermission('settings.edit') || !anyTabPerm;
  const visibleTabs = TABS.filter(t => seesAll || hasPermission(t.perm));
  const [tab, setTab]           = useState((visibleTabs[0] && visibleTabs[0].id) || 'sms');
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
      const r = await api.post('/admin/test-sms', { mobile: testNum.trim() });
      if (!r.sms_url) { setTestMsg('Error: ' + (r.error || 'No URL returned')); return; }
      setTestMsg('Sending from your browser...');
      try {
        await fetch(r.sms_url, { method: 'GET', mode: 'no-cors' });
        setTestMsg('SMS request sent to ' + r.mobile + '. Check your phone.');
      } catch { setTestMsg('SMS request sent (CORS blocked response, SMS should arrive). Check your phone.'); }
    } catch (e) { setTestMsg('Error: ' + (e.message || 'Failed')); }
    finally { setTesting(false); }
  }

  function previewUrl() {
    if (!settings || !settings.sms_api_url) return '';
    const mob  = settings.sms_api_mobile_param  || 'mobileno';
    const msgP = settings.sms_api_message_param || 'message';
    const msg  = (settings.sms_api_message_template || '{OTP} is your OTP')
                   .replace('{OTP}', '123456').replace(/\r?\n/g, ' ');
    const strip = settings.sms_api_strip_country_code !== 'false';
    let u = settings.sms_api_url + '?userid=' + (settings.sms_api_userid || 'USERID')
      + '&password=***&' + mob + '=' + (strip ? '9876543210' : '919876543210')
      + '&sendername=' + (settings.sms_api_sender_name || 'SENDER');
    if (settings.sms_api_sender_number) u += '&sendernumber=' + settings.sms_api_sender_number;
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

  const smsMode  = settings.sms_provider || 'none';
  const isDemo   = smsMode === 'none';
  const provider = isDemo ? null : smsMode;
  const preview  = previewUrl();

  const tabStyle = (id) => ({
    display: 'flex', alignItems: 'center', gap: 8, padding: '10px 18px',
    borderRadius: 8, border: 'none', cursor: 'pointer', fontSize: 14,
    fontWeight: tab === id ? 700 : 500,
    background: tab === id ? 'var(--accent)' : 'transparent',
    color: tab === id ? '#fff' : 'var(--subtext)',
    transition: 'all 0.15s', whiteSpace: 'nowrap',
  });

  const modeCard = (active, onClick, icon, label, desc, accentColor) => (
    <div onClick={onClick} style={{
      flex: 1, padding: '16px', borderRadius: 10, cursor: 'pointer',
      border: active ? '2px solid ' + accentColor : '2px solid var(--border)',
      background: active ? accentColor + '12' : 'var(--surface)',
      transition: 'all 0.15s',
    }}>
      <div style={{ fontSize: 24, marginBottom: 6 }}>{icon}</div>
      <div style={{ fontWeight: 700, fontSize: 14,
        color: active ? accentColor : 'var(--text)', marginBottom: 3 }}>{label}</div>
      <div style={{ fontSize: 12, color: 'var(--subtext)', lineHeight: 1.4 }}>{desc}</div>
      {active && (
        <div style={{ marginTop: 8, fontSize: 11, fontWeight: 700,
          color: accentColor }}>ACTIVE</div>
      )}
    </div>
  );

  return (
    <div className="page">
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ margin: 0 }}>Settings</h1>
        <p style={{ margin: '4px 0 0', color: 'var(--subtext)', fontSize: 14 }}>
          Configure your CyberGuard AI platform
        </p>
      </div>

      {/* Tab bar */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 24,
        background: 'var(--card)', borderRadius: 10, padding: 6, overflowX: 'auto' }}>
        {visibleTabs.map(t => (
          <button key={t.id} style={tabStyle(t.id)} onClick={() => setTab(t.id)}>
            <span style={{ fontSize: 16 }}>{t.icon}</span>{t.label}
          </button>
        ))}
      </div>

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

      {/* ========== SMS TAB ========== */}
      {tab === 'sms' && (
        <form onSubmit={save}>

          {/* -- TOP: Mode selector -- */}
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 4 }}>OTP Delivery Mode</h2>
            <p style={{ color: 'var(--subtext)', fontSize: 13, margin: '0 0 16px' }}>
              Select how OTPs are delivered to users during signup.
            </p>
            <div style={{ display: 'flex', gap: 12 }}>
              {modeCard(isDemo, () => set('sms_provider', 'none'),
                '🧪', 'Demo Mode',
                'OTP shown on screen after signup. No SMS sent. Use for development and testing.',
                '#f59e0b')}
              {modeCard(!isDemo, () => {
                if (isDemo) set('sms_provider', 'custom_url');
              }, '📡', 'Production Mode',
                'OTP sent via SMS to the user\'s phone. Select your SMS provider below.',
                '#22c55e')}
            </div>

            {isDemo && (
              <div style={{ marginTop: 12, padding: '10px 16px', borderRadius: 8, fontSize: 13,
                background: 'rgba(245,158,11,0.1)', color: '#f59e0b',
                border: '1px solid rgba(245,158,11,0.3)' }}>
                Demo mode is active. OTP will be shown on the signup screen. No SMS will be sent.
              </div>
            )}
            {!isDemo && (
              <div style={{ marginTop: 12, padding: '10px 16px', borderRadius: 8, fontSize: 13,
                background: 'rgba(34,197,94,0.1)', color: '#22c55e',
                border: '1px solid rgba(34,197,94,0.3)' }}>
                Production mode is active. OTPs will be sent via your configured SMS provider.
              </div>
            )}
          </div>

          {/* -- PRODUCTION: Provider selector -- */}
          {!isDemo && (
            <div className="card" style={{ marginBottom: 16 }}>
              <h2 style={{ marginTop: 0, marginBottom: 4 }}>SMS Provider</h2>
              <p style={{ color: 'var(--subtext)', fontSize: 13, margin: '0 0 16px' }}>
                Choose your SMS gateway provider.
              </p>
              <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
                {PROVIDERS.map(p => (
                  <div key={p.id} onClick={() => set('sms_provider', p.id)}
                    style={{
                      flex: 1, padding: 14, borderRadius: 10, cursor: 'pointer',
                      border: provider === p.id ? '2px solid var(--accent)' : '2px solid var(--border)',
                      background: provider === p.id ? 'rgba(79,142,247,0.08)' : 'var(--surface)',
                      transition: 'all 0.15s',
                    }}>
                    <div style={{ fontSize: 22, marginBottom: 4 }}>{p.icon}</div>
                    <div style={{ fontWeight: 700, fontSize: 13,
                      color: provider === p.id ? 'var(--accent)' : 'var(--text)',
                      marginBottom: 2 }}>{p.name}</div>
                    <div style={{ fontSize: 11, color: 'var(--subtext)', lineHeight: 1.4 }}>
                      {p.desc}
                    </div>
                    {provider === p.id && (
                      <div style={{ marginTop: 6, fontSize: 10, fontWeight: 700,
                        color: 'var(--accent)' }}>SELECTED</div>
                    )}
                  </div>
                ))}
              </div>

              {/* Custom URL config */}
              {provider === 'custom_url' && (
                <div>
                  <div style={{ ...lbl, marginBottom: 12, color: '#6b7280' }}>
                    Fixed credentials
                  </div>
                  <div style={{ ...g2, marginBottom: 16 }}>
                    <Field label="API Base URL" hint="URL without parameters" span>
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
                        placeholder="password" style={inp}/>
                    </Field>
                    <Field label="Sender Name">
                      <input value={settings.sms_api_sender_name || ''}
                        onChange={e => set('sms_api_sender_name', e.target.value)}
                        placeholder="BZIONX" style={inp}/>
                    </Field>
                    <Field label="Sender Number">
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

                  <div style={{ padding: 14, borderRadius: 8, marginBottom: 16,
                    border: '1px solid rgba(79,142,247,0.3)',
                    background: 'rgba(79,142,247,0.05)' }}>
                    <div style={{ ...lbl, color: '#4f8ef7', marginBottom: 12 }}>
                      Variable per SMS
                    </div>
                    <div style={g2}>
                      <Field label="Mobile param name" hint="default: mobileno">
                        <input value={settings.sms_api_mobile_param || 'mobileno'}
                          onChange={e => set('sms_api_mobile_param', e.target.value)}
                          placeholder="mobileno" style={inp}/>
                      </Field>
                      <Field label="Message param name" hint="default: message">
                        <input value={settings.sms_api_message_param || 'message'}
                          onChange={e => set('sms_api_message_param', e.target.value)}
                          placeholder="message" style={inp}/>
                      </Field>
                      <Field label="OTP message template" hint="Use {OTP} for the code" span>
                        <textarea rows={2}
                          value={settings.sms_api_message_template || ''}
                          onChange={e => set('sms_api_message_template', e.target.value)}
                          placeholder="{OTP} is your OTP. Do not share with anyone."
                          style={{ ...inp, resize: 'vertical' }}/>
                      </Field>
                    </div>
                  </div>

                  <div style={{ padding: 12, background: 'var(--surface)',
                    borderRadius: 8, marginBottom: 16 }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                      <input type="checkbox"
                        checked={settings.sms_api_strip_country_code !== 'false'}
                        onChange={e => set('sms_api_strip_country_code',
                          e.target.checked ? 'true' : 'false')}/>
                      <span style={{ fontSize: 13 }}>
                        Strip country code (send 10-digit: 9876543210 not 919876543210)
                      </span>
                    </label>
                  </div>

                  {preview && (
                    <div style={{ padding: 12, background: 'var(--surface)',
                      borderRadius: 6, marginBottom: 16 }}>
                      <div style={{ ...lbl, marginBottom: 6 }}>URL Preview (OTP = 123456)</div>
                      <div style={{ wordBreak: 'break-all', fontFamily: 'monospace',
                        fontSize: 11, color: 'var(--text)', lineHeight: 1.7 }}>
                        {preview}
                      </div>
                    </div>
                  )}

                  {/* Test SMS */}
                  <div style={{ padding: 14, background: 'var(--surface)', borderRadius: 8 }}>
                    <div style={{ ...lbl, marginBottom: 4 }}>Test SMS</div>
                    <p style={{ color: 'var(--subtext)', fontSize: 13, margin: '0 0 10px' }}>
                      Save first, then send OTP 123456 to verify your gateway works.
                    </p>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <input value={testNum} onChange={e => setTestNum(e.target.value)}
                        placeholder="9876543210" style={{ ...inp, flex: 1 }}/>
                      <button type="button" onClick={sendTest} disabled={testing}
                        style={{ padding: '8px 18px', borderRadius: 6, border: 'none',
                          background: '#22c55e', color: '#fff', fontWeight: 600,
                          cursor: testing ? 'not-allowed' : 'pointer',
                          opacity: testing ? 0.6 : 1, fontSize: 13, whiteSpace: 'nowrap' }}>
                        {testing ? 'Sending...' : 'Send Test OTP'}
                      </button>
                    </div>
                    {testMsg && (
                      <p style={{ margin: '8px 0 0', fontSize: 13, padding: '8px 12px',
                        borderRadius: 6,
                        background: testMsg.startsWith('SMS request') ? 'rgba(34,197,94,0.12)' : 'rgba(239,68,68,0.12)',
                        color: testMsg.startsWith('SMS request') ? '#22c55e' : '#ef4444' }}>
                        {testMsg}
                      </p>
                    )}
                  </div>
                </div>
              )}

              {/* Twilio config */}
              {provider === 'twilio' && (
                <div>
                  <div style={{ padding: 10, borderRadius: 6, marginBottom: 16, fontSize: 12,
                    background: 'rgba(79,142,247,0.08)', color: '#4f8ef7',
                    border: '1px solid rgba(79,142,247,0.25)' }}>
                    Find these in your Twilio Console at console.twilio.com
                  </div>
                  <div style={g2}>
                    <Field label="Account SID">
                      <input value={settings.twilio_account_sid || ''}
                        onChange={e => set('twilio_account_sid', e.target.value)}
                        placeholder="ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" style={inp}/>
                    </Field>
                    <Field label="Auth Token">
                      <input type="password" value={settings.twilio_auth_token || ''}
                        onChange={e => set('twilio_auth_token', e.target.value)}
                        placeholder="Auth token from Twilio Console" style={inp}/>
                    </Field>
                    <Field label="From Number" hint="Twilio phone number in E.164 format" span>
                      <input value={settings.twilio_from_number || ''}
                        onChange={e => set('twilio_from_number', e.target.value)}
                        placeholder="+1234567890" style={inp}/>
                    </Field>
                    <Field label="OTP message template" hint="Use {OTP} for the code" span>
                      <textarea rows={2}
                        value={settings.sms_api_message_template || ''}
                        onChange={e => set('sms_api_message_template', e.target.value)}
                        placeholder="{OTP} is your OTP. Do not share with anyone."
                        style={{ ...inp, resize: 'vertical' }}/>
                    </Field>
                  </div>
                  <div style={{ marginTop: 14, padding: '10px 14px', borderRadius: 6,
                    fontSize: 13, background: 'rgba(245,158,11,0.1)', color: '#f59e0b' }}>
                    Twilio sends OTPs via Twilio REST API from the backend. Make sure your Railway
                    plan allows outbound HTTPS to api.twilio.com.
                  </div>
                </div>
              )}

              {/* MSG91 config */}
              {provider === 'msg91' && (
                <div>
                  <div style={{ padding: 10, borderRadius: 6, marginBottom: 16, fontSize: 12,
                    background: 'rgba(79,142,247,0.08)', color: '#4f8ef7',
                    border: '1px solid rgba(79,142,247,0.25)' }}>
                    Find these in your MSG91 dashboard at msg91.com
                  </div>
                  <div style={g2}>
                    <Field label="Auth Key">
                      <input value={settings.msg91_auth_key || ''}
                        onChange={e => set('msg91_auth_key', e.target.value)}
                        placeholder="MSG91 auth key" style={inp}/>
                    </Field>
                    <Field label="Sender ID">
                      <input value={settings.msg91_sender_id || ''}
                        onChange={e => set('msg91_sender_id', e.target.value)}
                        placeholder="CALLFT" style={inp}/>
                    </Field>
                    <Field label="Route" hint="4 = Transactional, 1 = Promotional">
                      <input value={settings.msg91_route || '4'}
                        onChange={e => set('msg91_route', e.target.value)}
                        placeholder="4" style={inp}/>
                    </Field>
                    <Field label="Template ID">
                      <input value={settings.msg91_template_id || ''}
                        onChange={e => set('msg91_template_id', e.target.value)}
                        placeholder="MSG91 DLT template ID" style={inp}/>
                    </Field>
                    <Field label="OTP message template" hint="Use {OTP} for the code" span>
                      <textarea rows={2}
                        value={settings.sms_api_message_template || ''}
                        onChange={e => set('sms_api_message_template', e.target.value)}
                        placeholder="{OTP} is your OTP. Do not share with anyone."
                        style={{ ...inp, resize: 'vertical' }}/>
                    </Field>
                  </div>
                  <div style={{ marginTop: 14, padding: '10px 14px', borderRadius: 6,
                    fontSize: 13, background: 'rgba(34,197,94,0.1)', color: '#22c55e' }}>
                    MSG91 OTP requests are made by the Android app directly from the device
                    (Indian IP) for reliable delivery.
                  </div>
                </div>
              )}
            </div>
          )}

          <button type="submit" disabled={saving} style={{
            padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
            {saving ? 'Saving...' : 'Save SMS Settings'}
          </button>
        </form>
      )}

      {/* ========== OTP RULES TAB ========== */}
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
              <Field label="OTP Expiry">
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

      {/* ========== CONTACTS SYNC TAB ========== */}
      {tab === 'contacts' && (
        <form onSubmit={save}>
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 4 }}>Contacts Sync (global)</h2>
            <p className="muted" style={{ marginTop: 0, marginBottom: 14 }}>
              Master switch for the contacts backup/sync feature. When set to "Disabled
              for everyone", contacts sync is turned off for ALL users regardless of their
              individual setting. When "Enabled for all", each user is allowed by default and
              you can still disable specific users from their profile → Contacts tab.
              Existing cloud data is always retained.
            </p>
            <Field label="Contacts sync feature">
              <select value={settings.contacts_sync_enabled || 'true'}
                onChange={e => set('contacts_sync_enabled', e.target.value)} style={inp}>
                <option value="true">Enabled for all users</option>
                <option value="false">Disabled for everyone</option>
              </select>
            </Field>
          </div>
          <button type="submit" disabled={saving} style={{
            padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
            {saving ? 'Saving...' : 'Save Contacts Settings'}
          </button>
        </form>
      )}

      {/* ========== FRAUD REPORTS TAB ========== */}
      {tab === 'fraud' && (
        <div>
          <FraudCategories />
          <form onSubmit={save}>
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 4 }}>Email sending (SMTP)</h2>
            <p className="muted" style={{ marginTop: 0, marginBottom: 20 }}>
              Reports are emailed to each category's recipients using this SMTP server.
              Reports are always stored even if SMTP isn't configured.
            </p>
            <div style={g2}>
              <Field label="SMTP host">
                <input value={settings.smtp_host || ''}
                  onChange={e => set('smtp_host', e.target.value)}
                  placeholder="smtp.gmail.com" style={inp}/>
              </Field>
              <Field label="SMTP port">
                <input value={settings.smtp_port || '587'}
                  onChange={e => set('smtp_port', e.target.value)}
                  placeholder="587" style={inp}/>
              </Field>
              <Field label="SMTP username">
                <input value={settings.smtp_user || ''}
                  onChange={e => set('smtp_user', e.target.value)}
                  placeholder="apikey or email" style={inp}/>
              </Field>
              <Field label="SMTP password">
                <input type="password" value={settings.smtp_pass || ''}
                  onChange={e => set('smtp_pass', e.target.value)}
                  placeholder="••••••••" style={inp}/>
              </Field>
              <Field label="From address">
                <input value={settings.smtp_from || ''}
                  onChange={e => set('smtp_from', e.target.value)}
                  placeholder="CyberGuard <noreply@yourcompany.com>" style={inp}/>
              </Field>
              <Field label="Use TLS/SSL (secure)">
                <select value={settings.smtp_secure || 'false'}
                  onChange={e => set('smtp_secure', e.target.value)} style={inp}>
                  <option value="false">No (STARTTLS, port 587)</option>
                  <option value="true">Yes (SSL, port 465)</option>
                </select>
              </Field>
            </div>
          </div>
          <button type="submit" disabled={saving} style={{
            padding: '11px 28px', borderRadius: 6, border: 'none',
            background: 'var(--accent)', color: '#fff', fontWeight: 700,
            cursor: saving ? 'not-allowed' : 'pointer',
            opacity: saving ? 0.6 : 1, fontSize: 15 }}>
            {saving ? 'Saving...' : 'Save SMTP Settings'}
          </button>
          </form>
        </div>
      )}

      {/* ========== SUBSCRIPTION TAB ========== */}
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
                  When enabled, the app shows a paywall for users without a valid subscription.
                </div>
              </div>
            </label>
            <div style={{ marginTop: 12, padding: '10px 16px', borderRadius: 8, fontSize: 13,
              background: settings.subscription_required === 'true'
                ? 'rgba(79,142,247,0.1)' : 'rgba(34,197,94,0.1)',
              color: settings.subscription_required === 'true' ? '#4f8ef7' : '#22c55e',
              border: settings.subscription_required === 'true'
                ? '1px solid rgba(79,142,247,0.3)' : '1px solid rgba(34,197,94,0.3)' }}>
              {settings.subscription_required === 'true'
                ? 'ON - Users must have an active subscription.'
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

      {/* ========== RAZORPAY TAB ========== */}
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

      {/* ========== PASSWORD TAB ========== */}
      {tab === 'password' && (
        <form onSubmit={changePw}>
          <div className="card" style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0, marginBottom: 16 }}>Change Admin Password</h2>
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

// ── Fraud category manager (multi-recipient + per-category HTML template) ──
function FraudCategories() {
  const [cats, setCats] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null); // {new?, id, name, subject, template_html, emailsText}

  const load = async () => {
    setLoading(true);
    try {
      const r = await api.get('/admin/fraud-categories');
      setCats(r.categories || []);
    } catch (e) { /* ignore */ }
    finally { setLoading(false); }
  };
  useEffect(() => { load(); }, []);

  const startNew = () => setEditing({
    new: true, name: '', subject: 'Fraud report: {{number}}',
    template_html: '', emailsText: '',
  });
  const startEdit = (c) => setEditing({
    id: c.id, name: c.name, subject: c.subject || 'Fraud report: {{number}}',
    template_html: c.template_html || '', emailsText: (c.emails || []).join('\n'),
  });

  const save = async () => {
    if (!editing.name.trim()) { alert('Category name is required'); return; }
    const emails = editing.emailsText.split(/[\n,;]+/).map(s => s.trim()).filter(Boolean);
    const body = { name: editing.name, subject: editing.subject,
      template_html: editing.template_html, emails };
    try {
      if (editing.new) await api.post('/admin/fraud-categories', body);
      else await api.put(`/admin/fraud-categories/${editing.id}`, body);
      setEditing(null); load();
    } catch (e) { alert(e.message); }
  };

  const remove = async (c) => {
    if (!window.confirm(`Delete category "${c.name}"?`)) return;
    try { await api.delete(`/admin/fraud-categories/${c.id}`); load(); }
    catch (e) { alert(e.message); }
  };

  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
        <h2 style={{ margin: 0 }}>Fraud Categories</h2>
        <button type="button" className="btn btn-primary" onClick={startNew}>+ New category</button>
      </div>
      <p className="muted" style={{ marginTop: 6 }}>
        Each category has its own recipient list and HTML email template. When a user
        reports a call, they pick a category and the email goes to all its recipients.
      </p>

      {loading ? <p className="muted">Loading…</p> : (
        <table className="data-table">
          <thead><tr><th>Category</th><th>Recipients</th><th></th></tr></thead>
          <tbody>
            {cats.map(c => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td>{(c.emails || []).length} email(s)</td>
                <td style={{ whiteSpace:'nowrap' }}>
                  <button type="button" className="btn" onClick={() => startEdit(c)}>Edit</button>
                  <button type="button" className="btn" onClick={() => remove(c)}
                    style={{ marginLeft:6, background:'rgba(248,113,113,0.15)', color:'#f87171' }}>Delete</button>
                </td>
              </tr>
            ))}
            {cats.length === 0 && <tr><td colSpan="3" className="muted">No categories yet.</td></tr>}
          </tbody>
        </table>
      )}

      {editing && (
        <div style={{ position:'fixed', inset:0, background:'rgba(0,0,0,0.6)',
            display:'flex', alignItems:'center', justifyContent:'center', zIndex:1000, padding:20 }}>
          <div className="card" style={{ maxWidth:760, width:'100%', maxHeight:'88vh', overflow:'auto' }}>
            <h2 style={{ marginTop:0 }}>{editing.new ? 'New category' : `Edit: ${editing.name}`}</h2>

            <label style={{ display:'block', fontWeight:600, marginBottom:6 }}>Category name</label>
            <input value={editing.name} onChange={e => setEditing({ ...editing, name: e.target.value })}
              placeholder="e.g. Phishing" style={inp2} />

            <label style={{ display:'block', fontWeight:600, margin:'14px 0 6px' }}>Email subject</label>
            <input value={editing.subject} onChange={e => setEditing({ ...editing, subject: e.target.value })}
              placeholder="Fraud report: {{number}}" style={inp2} />

            <label style={{ display:'block', fontWeight:600, margin:'14px 0 6px' }}>
              Recipient emails (one per line)</label>
            <textarea value={editing.emailsText}
              onChange={e => setEditing({ ...editing, emailsText: e.target.value })}
              rows={3} placeholder={"abuse@bank.com\nfraud@police.gov"} style={{ ...inp2, fontFamily:'monospace' }} />

            <label style={{ display:'block', fontWeight:600, margin:'14px 0 6px' }}>Email template</label>
            <RichTextEditor
              value={editing.template_html}
              onChange={html => setEditing({ ...editing, template_html: html })}
              placeholders={PLACEHOLDERS} />

            <div style={{ display:'flex', gap:10, justifyContent:'flex-end', marginTop:16 }}>
              <button type="button" className="btn" onClick={() => setEditing(null)}>Cancel</button>
              <button type="button" className="btn btn-primary" onClick={save}>Save category</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const inp2 = {
  width: '100%', padding: '9px 12px', borderRadius: 6,
  border: '1px solid var(--border)', background: 'var(--surface)',
  color: 'var(--text)', fontSize: 14, boxSizing: 'border-box',
};
