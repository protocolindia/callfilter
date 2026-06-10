// Firebase Cloud Messaging push helper.
//
// Sends a lightweight "data" push to the "global_blocklist" topic so devices
// wake up and pull the latest delta. Uses the legacy HTTP API with a server
// key stored in settings (key: fcm_server_key). If the key isn't configured,
// this is a no-op (returns {ok:false, skipped:true}) and never throws.
//
// ── SETUP (placeholders) ────────────────────────────────────────────────────
//   1. Create a Firebase project at https://console.firebase.google.com
//   2. Project settings → Cloud Messaging → copy the "Server key"
//      (enable the legacy Cloud Messaging API if needed).
//   3. In the admin panel: Settings → set fcm_server_key to that value.
//      (Or set the FCM_SERVER_KEY environment variable.)
// ─────────────────────────────────────────────────────────────────────────────
const fetch = require('node-fetch');
const { one } = require('./db');

const TOPIC = 'global_blocklist';

async function getServerKey() {
  if (process.env.FCM_SERVER_KEY) return process.env.FCM_SERVER_KEY;
  try {
    const row = await one("SELECT value FROM settings WHERE key = 'fcm_server_key'");
    return row && row.value ? row.value : null;
  } catch (_) { return null; }
}

/**
 * Notify all devices that the global blocklist changed. Best-effort.
 * Returns { ok, skipped?, error? }.
 */
async function pushBlocklistChanged() {
  try {
    const key = await getServerKey();
    if (!key) return { ok: false, skipped: true };

    const resp = await fetch('https://fcm.googleapis.com/fcm/send', {
      method: 'POST',
      headers: {
        'Authorization': 'key=' + key,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        to: '/topics/' + TOPIC,
        priority: 'high',
        // data-only message so the app handles it in the background
        data: { type: 'global_blocklist_changed', ts: String(Date.now()) },
      }),
    });
    if (!resp.ok) {
      const t = await resp.text().catch(() => '');
      return { ok: false, error: `FCM ${resp.status}: ${t.slice(0, 200)}` };
    }
    return { ok: true };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

module.exports = { pushBlocklistChanged, FCM_TOPIC: TOPIC };
