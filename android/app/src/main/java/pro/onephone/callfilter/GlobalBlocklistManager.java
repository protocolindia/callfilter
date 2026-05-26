package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class GlobalBlocklistManager {
    private static final String TAG   = "GlobalBlocklist";
    private static final String PREFS = "global_blocklist_prefs";
    private static final String KEY_ENTRIES         = "entries";
    private static final String KEY_ENABLED_REASONS = "enabled_reasons";
    private static final String KEY_LAST_SYNC       = "last_sync_ts";
    private static final String KEY_SHOW_TOTAL  = "show_total";
    private static final String KEY_SHOW_ACTIVE = "show_active";

    public static class Entry {
        public final String number;
        public final String reason;
        Entry(String n, String r) { number = n; reason = r; }
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> enabledReasons = new HashSet<>();

    private static GlobalBlocklistManager instance;
    public static synchronized GlobalBlocklistManager getInstance(Context c) {
        if (instance == null) instance = new GlobalBlocklistManager(c.getApplicationContext());
        return instance;
    }

    private GlobalBlocklistManager(Context c) {
        this.ctx = c;
        this.prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    private synchronized void loadFromPrefs() {
        entries.clear(); enabledReasons.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_ENTRIES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) entries.add(new Entry(o.optString("number",""), o.optString("reason","")));
            }
        } catch (Exception ignored) {}
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_ENABLED_REASONS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String r = arr.optString(i);
                if (r != null && !r.isEmpty()) enabledReasons.add(r);
            }
        } catch (Exception ignored) {}
    }

    private synchronized void saveEntries() {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("number", e.number); o.put("reason", e.reason); arr.put(o);
            }
            prefs.edit().putString(KEY_ENTRIES, arr.toString()).commit();
        } catch (Exception ignored) {}
    }

    private synchronized void saveEnabledReasons() {
        try {
            JSONArray arr = new JSONArray();
            for (String r : enabledReasons) arr.put(r);
            prefs.edit().putString(KEY_ENABLED_REASONS, arr.toString()).commit();
        } catch (Exception ignored) {}
    }

    public synchronized Set<String> getEnabledReasons() { return new HashSet<>(enabledReasons); }

    public synchronized void setReasonEnabled(String reason, boolean enabled) {
        if (enabled) enabledReasons.add(reason); else enabledReasons.remove(reason);
        saveEnabledReasons();
    }

    public synchronized boolean isReasonEnabled(String reason) { return enabledReasons.contains(reason); }

    public synchronized String isNumberBlocked(String rawNumber) {
        if (rawNumber == null || rawNumber.isEmpty()) return null;
        if (entries.isEmpty() || enabledReasons.isEmpty()) return null;
        String normalized = rawNumber.replaceAll("[\\s\\-().]", "");
        for (Entry e : entries) {
            if (normalized.equals(e.number.replaceAll("[\\s\\-().]", ""))) {
                if (enabledReasons.contains(e.reason)) return e.reason;
            }
        }
        return null;
    }

    public synchronized int getTotalEntries() { return entries.size(); }

    public synchronized Map<String, Integer> getCountByReason() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Entry e : entries) map.put(e.reason, map.getOrDefault(e.reason, 0) + 1);
        return map;
    }

    public synchronized int getEnabledEntryCount() {
        int count = 0;
        for (Entry e : entries) if (enabledReasons.contains(e.reason)) count++;
        return count;
    }

    public long getLastSyncTs() { return prefs.getLong(KEY_LAST_SYNC, 0L); }

    public boolean isShowTotal()  { return prefs.getBoolean(KEY_SHOW_TOTAL,  true); }
    public boolean isShowActive() { return prefs.getBoolean(KEY_SHOW_ACTIVE, true); }

    public interface SyncCallback { void onDone(boolean success, int count, String error); }

    public void syncAsync(final SyncCallback cb) {
        AuthManager auth = AuthManager.getInstance(ctx);
        if (!auth.isBackendEnabled()) { if (cb != null) cb.onDone(false, 0, "Backend not configured"); return; }
        new Thread(() -> {
            try {
                BackendClient.get(AuthManager.BACKEND_URL + "/api/global-blocklist",
                    new BackendClient.Callback() {
                        public void onResult(boolean ok, JSONObject resp, String err) {
                            if (!ok || resp == null) { if (cb != null) cb.onDone(false, 0, err); return; }
                            try {
                                JSONArray arr = resp.optJSONArray("entries");
                                if (arr == null) arr = new JSONArray();
                                List<Entry> fresh = new ArrayList<>();
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject o = arr.optJSONObject(i);
                                    if (o == null) continue;
                                    String num = o.optString("number",""); String rsn = o.optString("reason","");
                                    if (!num.isEmpty() && !rsn.isEmpty()) fresh.add(new Entry(num, rsn));
                                }
                                synchronized (GlobalBlocklistManager.this) {
                                    entries.clear(); entries.addAll(fresh);
                                    saveEntries();
                                    prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).commit();
                                }
                                if (cb != null) cb.onDone(true, fresh.size(), null);
                            } catch (Exception e) { if (cb != null) cb.onDone(false, 0, e.getMessage()); }
                        }
                    });
            } catch (Exception e) { if (cb != null) cb.onDone(false, 0, e.getMessage()); }
        }).start();
    }

    private void fetchConfigAsync() {
        BackendClient.get(AuthManager.BACKEND_URL + "/api/global-blocklist/config",
            new BackendClient.Callback() {
                public void onResult(boolean ok, JSONObject resp, String err) {
                    if (!ok || resp == null) return;
                    prefs.edit()
                        .putBoolean(KEY_SHOW_TOTAL,  resp.optBoolean("show_total",  true))
                        .putBoolean(KEY_SHOW_ACTIVE, resp.optBoolean("show_active", true))
                        .commit();
                }
            });
    }

    public void clear() {
        synchronized (this) { entries.clear(); enabledReasons.clear(); }
        prefs.edit().remove(KEY_ENTRIES).remove(KEY_ENABLED_REASONS).remove(KEY_LAST_SYNC).commit();
    }
}
