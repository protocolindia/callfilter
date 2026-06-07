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
    private static final String KEY_INITIAL_SYNC_DONE = "initial_sync_done";
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

    // ===== Blocked-calls sync (push only) =====
    public void syncBlockedCallsAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        BlockedCallsManager bcm = BlockedCallsManager.getInstance(appCtx);

        // Build a list of unsynced (or recently-modified) entries
        java.util.List<BlockedCallsManager.Entry> all = bcm.getEntries();
        org.json.JSONArray arr = new org.json.JSONArray();
        java.util.List<String> sentIds = new java.util.ArrayList<>();
        for (BlockedCallsManager.Entry e : all) {
            // Re-sync any unsynced row; also re-sync any with a reason that
            // hasn't been pushed yet (we mark synced=false when reason is set).
            if (e.synced) continue;
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("client_id", e.clientId);
                o.put("number", e.number == null ? "" : e.number);
                o.put("rule_type", e.ruleType == null ? "" : e.ruleType);
                o.put("rule_pattern", e.rulePattern == null ? "" : e.rulePattern);
                o.put("rule_action", e.ruleAction == null ? "reject" : e.ruleAction);
                if (e.reason != null && !e.reason.isEmpty()) o.put("reason", e.reason);
                o.put("blocked_at_ms", e.blockedAtMs);
                arr.put(o);
                sentIds.add(e.clientId);
            } catch (Exception ignored) {}
        }
        if (arr.length() == 0) return;

        try {
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("calls", arr);
            final java.util.List<String> idsToMark = sentIds;
            BackendClient.post(AuthManager.BACKEND_URL + "/api/blocked-calls/sync", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, org.json.JSONObject resp, String err) {
                        if (ok) {
                            BlockedCallsManager.getInstance(appCtx).markSynced(idsToMark);
                            Log.d(TAG, "Pushed " + idsToMark.size() + " blocked-call entries");
                        } else {
                            Log.w(TAG, "Blocked-calls sync failed: " + err);
                        }
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "syncBlockedCallsAsync failed", e);
        }
    }
    /** Pull the blocked-call history from the cloud and merge locally.
     *  Called on login/startup so history survives reinstall & new versions. */
    public void pullBlockedCallsFromCloud() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        String url = AuthManager.BACKEND_URL + "/api/blocked-calls/list?user_id="
            + auth.getUserId() + "&limit=500";
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, org.json.JSONObject resp, String err) {
                if (ok && resp != null) {
                    org.json.JSONArray calls = resp.optJSONArray("calls");
                    int added = BlockedCallsManager.getInstance(appCtx).mergeFromCloud(calls);
                    Log.d(TAG, "Pulled blocked calls from cloud, merged " + added);
                } else {
                    Log.w(TAG, "Blocked-calls pull failed: " + err);
                }
            }
        });
    }

    /** Backwards-compatible alias. */
    public void pullBlockedCallsFromCloudIfEmpty() { pullBlockedCallsFromCloud(); }

    // ===== Rules sync (v25.8 — differential) =====

    /**
     * Push ONE rule to the server. Called from RulesManager.addRule.
     * Idempotent: backend upserts by (user_id, client_id).
     */
    public void pushAddedRule(final Rule rule) {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("client_id", rule.getId());
            body.put("type", rule.getType());
            body.put("pattern", rule.getPattern());
            body.put("action", rule.getAction());
            BackendClient.post(AuthManager.BACKEND_URL + "/api/rules/add", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        Log.d(TAG, "Rule push ok=" + ok + (err != null ? (" err=" + err) : ""));
                    }
                });
        } catch (Exception e) { Log.e(TAG, "pushAddedRule failed", e); }
    }

    /**
     * Tell server to delete ONE rule by client_id.
     */
    public void pushDeletedRule(final String clientId) {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("client_id", clientId);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/rules/delete", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        Log.d(TAG, "Rule delete ok=" + ok + (err != null ? (" err=" + err) : ""));
                    }
                });
        } catch (Exception e) { Log.e(TAG, "pushDeletedRule failed", e); }
    }

    /**
     * LEGACY mirror-style sync. Backend now MERGES instead of replacing, so
     * this is safe to keep — but pushAddedRule/pushDeletedRule are preferred.
     */
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
                        Log.d(TAG, "Rules sync(merge) ok=" + ok);
                    }
                });
        } catch (Exception e) { Log.e(TAG, "Rules sync failed", e); }
    }

    /**
     * Pull cloud rules and MERGE into local. Cloud is the source of truth for
     * rule existence — if a rule is in cloud, ensure it's in local. Local-only
     * rules are kept (they may not have been pushed yet). After merging, we
     * persist the union.
     *
     * Safe to call on every app launch — no race because we don't OVERWRITE
     * local; we only ADD missing cloud rules.
     */
    public void mergeRulesFromCloud() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        final RulesManager rm = RulesManager.getInstance(appCtx);
        String url = AuthManager.BACKEND_URL + "/api/rules/list?user_id=" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String error) {
                if (!ok || resp == null) {
                    Log.w(TAG, "mergeRules failed: " + error);
                    return;
                }
                JSONArray arr = resp.optJSONArray("rules");
                if (arr == null) return;
                rm.reload();
                int added = 0;
                java.util.Set<String> existing = new java.util.HashSet<>();
                for (Rule r : rm.getRules()) existing.add(r.getId());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject r = arr.optJSONObject(i);
                    if (r == null) continue;
                    String cid     = r.optString("client_id", "");
                    String type    = r.optString("rule_type", "");
                    String pattern = r.optString("pattern", "");
                    String action  = r.optString("action", "reject");
                    if (cid.isEmpty() || type.isEmpty() || pattern.isEmpty()) continue;
                    if (!existing.contains(cid)) {
                        rm.addRuleWithId(cid, pattern, type, action);
                        existing.add(cid);
                        added++;
                    }
                }
                Log.d(TAG, "mergeRules: " + added + " new rules pulled into local");
            }
        });
    }

    /** Has the initial sync flag been set? Kept for backward compat. */
    public boolean isInitialSyncDone() {
        return prefs.getBoolean(KEY_INITIAL_SYNC_DONE, false);
    }

    public void clearInitialSyncFlag() {
        prefs.edit().remove(KEY_INITIAL_SYNC_DONE).commit();
    }

    /**
     * Legacy entry point — still called by LoginActivity. Now invokes
     * the merge logic instead of the destructive "force pull".
     */
    public void forcePullRulesFromCloud() {
        mergeRulesFromCloud();
        prefs.edit().putBoolean(KEY_INITIAL_SYNC_DONE, true).commit();
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
                    final Set<String> allDeviceIds = new HashSet<>();
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
                            allDeviceIds.add(contactId);
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

                    // Contacts previously uploaded but no longer on the phone = deleted.
                    final Set<String> deletedIds = new HashSet<>(uploaded);
                    deletedIds.removeAll(allDeviceIds);

                    if (arr.length() == 0 && deletedIds.isEmpty()) return;

                    JSONObject body = new JSONObject();
                    body.put("user_id", Long.parseLong(auth.getUserId()));
                    body.put("mode", firstFull ? "full" : "delta");
                    body.put("contacts", arr);
                    if (!deletedIds.isEmpty()) {
                        body.put("deleted_ids", new JSONArray(deletedIds));
                    }

                    BackendClient.post(AuthManager.BACKEND_URL + "/api/contacts/sync", body,
                        new BackendClient.Callback() {
                            public void onResult(boolean ok, JSONObject resp, String err) {
                                if (!ok) return;
                                Set<String> updated = new HashSet<>(
                                    prefs.getStringSet(KEY_UPLOADED_IDS, new HashSet<String>()));
                                updated.addAll(newIds);
                                updated.removeAll(deletedIds);   // drop soft-deleted ids
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

    private static final String KEY_RESTORE_DONE = "contacts_restore_done";

    /** On a fresh device: pull the cloud phone book and write any missing
     *  contacts into the device's Contacts app. Runs once per device. */
    public void restoreContactsFromCloudAsync() {
        if (!isContactsOptedIn()) return;
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        if (prefs.getBoolean(KEY_RESTORE_DONE, false)) return;
        if (appCtx.checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return;
        if (appCtx.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) return;

        String url = AuthManager.BACKEND_URL + "/api/contacts/list?user_id=" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String err) {
                if (!ok || resp == null) return;
                final JSONArray contacts = resp.optJSONArray("contacts");
                if (contacts == null || contacts.length() == 0) {
                    prefs.edit().putBoolean(KEY_RESTORE_DONE, true).commit();
                    return;
                }
                new Thread(() -> writeContactsToPhone(contacts)).start();
            }
        });
    }

    private void writeContactsToPhone(JSONArray contacts) {
        try {
            // Existing device numbers (normalized) to avoid duplicates
            Set<String> existing = new HashSet<>();
            Cursor pc = appCtx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
            if (pc != null) {
                while (pc.moveToNext()) existing.add(normalizeNum(pc.getString(0)));
                pc.close();
            }

            int written = 0;
            for (int i = 0; i < contacts.length(); i++) {
                JSONObject c = contacts.optJSONObject(i);
                if (c == null) continue;
                String name = c.optString("name", "");
                JSONArray phones = c.optJSONArray("phones");
                if (phones == null || phones.length() == 0) continue;

                // Skip if any of this contact's numbers already exists on device
                boolean already = false;
                for (int p = 0; p < phones.length(); p++) {
                    JSONObject ph = phones.optJSONObject(p);
                    if (ph != null && existing.contains(normalizeNum(ph.optString("number", "")))) {
                        already = true; break;
                    }
                }
                if (already) continue;

                java.util.ArrayList<android.content.ContentProviderOperation> ops = new java.util.ArrayList<>();
                int rawIdx = ops.size();
                ops.add(android.content.ContentProviderOperation
                    .newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build());
                if (name != null && !name.isEmpty()) {
                    ops.add(android.content.ContentProviderOperation
                        .newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                        .build());
                }
                for (int p = 0; p < phones.length(); p++) {
                    JSONObject ph = phones.optJSONObject(p);
                    if (ph == null) continue;
                    String num = ph.optString("number", "");
                    if (num.isEmpty()) continue;
                    ops.add(android.content.ContentProviderOperation
                        .newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, num)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build());
                }
                try {
                    appCtx.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                    written++;
                } catch (Exception ex) { Log.w(TAG, "write contact failed: " + ex.getMessage()); }
            }
            prefs.edit().putBoolean(KEY_RESTORE_DONE, true).commit();
            Log.d(TAG, "Restored " + written + " contacts to phone book");
        } catch (Exception e) {
            Log.e(TAG, "restore contacts error", e);
        }
    }

    private static String normalizeNum(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.toCharArray()) if (Character.isDigit(ch)) sb.append(ch);
        String s = sb.toString();
        return s.length() > 10 ? s.substring(s.length() - 10) : s;
    }

    public void syncGlobalBlocklistAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled()) return;
        GlobalBlocklistManager.getInstance(appCtx).syncAsync((ok, count, err) -> {
            if (ok) Log.d(TAG, "Global blocklist synced: " + count);
            else    Log.w(TAG, "Global blocklist sync failed: " + err);
        });
    }
}