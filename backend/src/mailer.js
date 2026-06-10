// Lightweight email sender. Reads SMTP config from the `settings` table so it
// can be configured from the admin panel without redeploying. Falls back
// gracefully (returns {ok:false}) when SMTP isn't configured.
const { many } = require('./db');

let nodemailer = null;
try { nodemailer = require('nodemailer'); } catch (_) { /* dep optional */ }

async function loadSettings() {
  const rows = await many('SELECT key, value FROM settings');
  const s = {};
  rows.forEach(r => { s[r.key] = r.value; });
  return s;
}

/**
 * Send an email using the SMTP settings stored in the DB.
 * Returns { ok, error?, skipped? }.
 */
async function sendMail({ to, subject, text, html }) {
  if (!nodemailer) return { ok: false, error: 'nodemailer not installed' };
  const s = await loadSettings();
  const host = s.smtp_host, user = s.smtp_user, pass = s.smtp_pass;
  const from = s.smtp_from || user;
  if (!host || !from) return { ok: false, skipped: true, error: 'SMTP not configured' };

  const transport = nodemailer.createTransport({
    host,
    port: parseInt(s.smtp_port, 10) || 587,
    secure: String(s.smtp_secure) === 'true',
    auth: user ? { user, pass } : undefined,
  });

  // `to` may be a string or an array of addresses.
  const recipients = Array.isArray(to) ? to.join(', ') : to;

  try {
    const msg = { from, to: recipients, subject };
    if (html) msg.html = html;
    if (text) msg.text = text;
    if (!html && !text) msg.text = '';
    await transport.sendMail(msg);
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

module.exports = { sendMail, loadSettings };
