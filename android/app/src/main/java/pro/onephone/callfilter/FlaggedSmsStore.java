package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores flagged-SMS history locally (privacy-safe preview only) and syncs it
 * to the backend, mirroring the blocked-calls history pattern.
 */
public class FlaggedSmsStore {

    private static final String TAG = "FlaggedSmsStore";
    private static final String PREFS = "flagged_sms_store";
    private static final String KEY = "items";
    private static final int MAX = 300;

    public static class Item {
        public String clientId, sender, preview, category, reasons;
        public int score;
        public long flaggedAtMs;
    }

    private final SharedPreferences prefs;
    private final Context appCtx;
    private static FlaggedSmsStore instance;

    public static synchronized FlaggedSmsStore getInstance(Context c) {
        if (instance == null) instance = new FlaggedSmsStore(c.getApplicationContext());
        return instance;
    }

    private FlaggedSmsStore(Context c) {
        appCtx = c.getApplicationContext();
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void record(String sender, String body, SmsThreatDetector.Result r) {
        Item it = new Item();
        it.clientId = "sms_" + System.currentTimeMillis() + "_" + Math.abs((sender + body).hashCode());
        it.sender = sender;
        it.preview = body.length() > 120 ? body.substring(0, 120) + "\u2026" : body;
        it.category = r.category;
        it.score = r.score;
        it.reasons = android.text.TextUtils.join(", ", r.reasons);
        it.flaggedAtMs = System.currentTimeMillis();

        List<Item> all = getAll();
        all.add(0, it);
        while (all.size() > MAX) all.remove(all.size() - 1);
        save(all);
        syncAsync();
    }

    public synchronized List<Item> getAll() {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Item it = new Item();
                it.clientId = o.optString("client_id");
                it.sender = o.optString("sender");
                it.preview = o.optString("preview");
                it.category = o.optString("category");
                it.score = o.optInt("score");
                it.reasons = o.optString("reasons");
                it.flaggedAtMs = o.optLong("flagged_at_ms");
                out.add(it);
            }
        } catch (Exception e) { Log.w(TAG, "parse: " + e.getMessage()); }
        return out;
    }

    private void save(List<Item> items) {
        JSONArray arr = new JSONArray();
        for (Item it : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("client_id", it.clientId);
                o.put("sender", it.sender);
                o.put("preview", it.preview);
                o.put("category", it.category);
                o.put("score", it.score);
                o.put("reasons", it.reasons);
                o.put("flagged_at_ms", it.flaggedAtMs);
                arr.put(o);
            } catch (Exception ignored) {}
        }
        prefs.edit().putString(KEY, arr.toString()).commit();
    }

    public void clear() { prefs.edit().remove(KEY).commit(); }

    /** Push flagged history to the backend (fire-and-forget). */
    public void syncAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        try {
            JSONArray items = new JSONArray();
            for (Item it : getAll()) {
                JSONObject o = new JSONObject();
                o.put("client_id", it.clientId);
                o.put("sender", it.sender);
                o.put("preview", it.preview);
                o.put("category", it.category);
                o.put("score", it.score);
                o.put("reasons", it.reasons);
                o.put("flagged_at_ms", it.flaggedAtMs);
                items.put(o);
            }
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("items", items);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/sms-protection/flagged", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        if (!ok) Log.w(TAG, "flagged sync failed: " + err);
                    }
                });
        } catch (Exception e) { Log.w(TAG, "sync: " + e.getMessage()); }
    }
}
