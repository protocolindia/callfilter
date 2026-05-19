package pro.onephone.callfilter;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final String PREFS = "sync_prefs";
    private static final String KEY_CONTACTS_OPTED_IN = "contacts_opted_in";
    private static final String KEY_FIRST_FULL_DONE = "contacts_first_full_done";
    private static final String KEY_UPLOADED_IDS = "uploaded_contact_ids";

    private final Context appCtx;
    private final SharedPreferences prefs;

    private static SyncManager instance;
    public static synchronized SyncManager getInstance(Context ctx) {
        if (instance == null) instance = new SyncManager(ctx.getApplicationContext());
        return instance;
    }

    private SyncManager(Context ctx) {
        this.appCtx = ctx;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ===== Contacts opt-in state =====
    public boolean isContactsOptedIn() {
        return prefs.getBoolean(KEY_CONTACTS_OPTED_IN, false);
    }

    public void setContactsOptedIn(boolean optedIn) {
        prefs.edit().putBoolean(KEY_CONTACTS_OPTED_IN, optedIn).commit();
        if (!optedIn) {
            prefs.edit().remove(KEY_FIRST_FULL_DONE).remove(KEY_UPLOADED_IDS).commit();
            optOutOnServer();
        }
    }

    private void optOutOnServer() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            BackendClient.post(AuthManager.BACKEND_URL + "/api/contacts/opt-out", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        Log.d(TAG, "Opt-out: ok=" + ok);
                    }
                });
        } catch (Exception ignored) {}
    }

    // ===== No-op blocked-calls sync stubs (Play Store edition keeps log local) =====
    public void syncBlockedCallsAsync() { /* no-op */ }
    public void pullBlockedCallsFromCloudIfEmpty() { /* no-op */ }

    // ===== Rules sync =====
    public void syncRulesAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        RulesManager rm = RulesManager.getInstance(appCtx);
        rm.reload();
        List<Rule> rules = rm.getRules();
        try {
            JSONArray arr = new JSONArray();
            for (Rule r : rules) {
                JSONObject o = new JSONObject();
                o.put("client_id", r.getId());
                o.put("type", r.getType());
                o.put("pattern", r.getPattern());
                o.put("action", r.getAction());
                arr.put(o);
            }
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("rules", arr);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/rules/sync", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        Log.d(TAG, "Rules sync ok=" + ok);
                    }
                });
        } catch (Exception e) { Log.e(TAG, "Rules sync failed", e); }
    }

    public void pullRulesFromCloudIfEmpty() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        RulesManager rm = RulesManager.getInstance(appCtx);
        rm.reload();
        if (!rm.getRules().isEmpty()) return;

        String url = AuthManager.BACKEND_URL + "/api/rules/list?user_id=" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String error) {
                if (!ok || resp == null) return;
                JSONArray arr = resp.optJSONArray("rules");
                if (arr == null) return;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject r = arr.optJSONObject(i);
                    if (r == null) continue;
                    String type    = r.optString("rule_type", "");
                    String pattern = r.optString("pattern", "");
                    String action  = r.optString("action", "reject");
                    if (!type.isEmpty() && !pattern.isEmpty()) {
                        rm.addRule(pattern, type, action);
                    }
                }
            }
        });
    }

    // ===== Contacts sync (only when user has opted in) =====
    public void syncContactsAsync() {
        if (!isContactsOptedIn()) return;
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        if (appCtx.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return;

        new Thread(new Runnable() {
            public void run() {
                try {
                    boolean firstFull = !prefs.getBoolean(KEY_FIRST_FULL_DONE, false);
                    Set<String> uploaded = new HashSet<>(
                        prefs.getStringSet(KEY_UPLOADED_IDS, new HashSet<String>()));

                    JSONArray arr = new JSONArray();
                    final Set<String> newIds = new HashSet<>();
                    Cursor c = null;
                    try {
                        c = appCtx.getContentResolver().query(
                            ContactsContract.Contacts.CONTENT_URI,
                            new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                            null, null, null);
                        if (c == null) return;
                        while (c.moveToNext()) {
                            String contactId = c.getString(0);
                            String name = c.getString(1);
                            if (!firstFull && uploaded.contains(contactId)) continue;

                            JSONArray phones = new JSONArray();
                            Cursor pc = appCtx.getContentResolver().query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                new String[]{contactId}, null);
                            if (pc != null) {
                                while (pc.moveToNext()) {
                                    JSONObject p = new JSONObject();
                                    p.put("number", pc.getString(0));
                                    phones.put(p);
                                }
                                pc.close();
                            }
                            JSONObject o = new JSONObject();
                            o.put("contact_id", contactId);
                            o.put("name", name == null ? "" : name);
                            o.put("phones", phones);
                            arr.put(o);
                            newIds.add(contactId);
                        }
                    } finally { if (c != null) c.close(); }

                    if (arr.length() == 0) return;

                    JSONObject body = new JSONObject();
                    body.put("user_id", Long.parseLong(auth.getUserId()));
                    body.put("mode", firstFull ? "full" : "delta");
                    body.put("contacts", arr);

                    BackendClient.post(AuthManager.BACKEND_URL + "/api/contacts/sync", body,
                        new BackendClient.Callback() {
                            public void onResult(boolean ok, JSONObject resp, String err) {
                                if (!ok) return;
                                Set<String> updated = new HashSet<>(
                                    prefs.getStringSet(KEY_UPLOADED_IDS, new HashSet<String>()));
                                updated.addAll(newIds);
                                prefs.edit()
                                    .putStringSet(KEY_UPLOADED_IDS, updated)
                                    .putBoolean(KEY_FIRST_FULL_DONE, true)
                                    .commit();
                            }
                        });
                } catch (Exception e) { Log.e(TAG, "Contacts sync error", e); }
            }
        }).start();
    }
}
