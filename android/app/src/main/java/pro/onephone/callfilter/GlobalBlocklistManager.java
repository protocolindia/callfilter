package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/**
 * GlobalBlocklistManager — downloads and caches the global blocklist from the backend.
 * Also stores per-admin popup images for displaying when a call is blocked.
 */
public class GlobalBlocklistManager {

    private static final String TAG   = "GlobalBlocklist";
    private static final String PREFS = "global_blocklist_prefs";

    private static final String KEY_ENTRIES          = "entries";
    private static final String KEY_ENABLED_REASONS  = "enabled_reasons";
    private static final String KEY_LAST_SYNC        = "last_sync_ts";
    private static final String KEY_SHOW_TOTAL       = "show_total";
    private static final String KEY_SHOW_ACTIVE      = "show_active";

    // ── Entry: one blocked number ─────────────────────────────────────────
    public static class Entry {
        public final String number;
        public final String reason;
        public final long   adminId;
        public Entry(String n, String r, long a) { number = n; reason = r; adminId = a; }
        public Entry(String n, String r)          { this(n, r, 0); }
    }

    // ── AdminConfig: popup image + assigned reasons per global_db_admin ───
    public static class AdminConfig {
        public final long         adminId;
        public final String       displayName;
        public final List<String> assignedReasons;
        public final String       popupImagePath; // local file path, null if no image
        public AdminConfig(long id, String name, List<String> reasons, String imgPath) {
            adminId = id; displayName = name; assignedReasons = reasons; popupImagePath = imgPath;
        }
    }

    // ── In-memory state ───────────────────────────────────────────────────
    private final List<Entry>               entries         = new ArrayList<>();
    private final Set<String>               enabledReasons  = new HashSet<>();
    private final Map<String, Long>         numberAdminMap  = new HashMap<>(); // normalised# -> adminId
    private final Map<Long,   AdminConfig>  adminConfigs    = new HashMap<>();

    private final Context          ctx;
    private final SharedPreferences prefs;

    private static GlobalBlocklistManager instance;
    public static synchronized GlobalBlocklistManager getInstance(Context c) {
        if (instance == null) instance = new GlobalBlocklistManager(c.getApplicationContext());
        return instance;
    }

    private GlobalBlocklistManager(Context c) {
        this.ctx   = c;
        this.prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private synchronized void loadFromPrefs() {
        entries.clear(); enabledReasons.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_ENTRIES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    entries.add(new Entry(
                        o.optString("number",""),
                        o.optString("reason",""),
                        o.optLong("admin_id", 0)));
                }
            }
        } catch (Exception ignored) {}
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_ENABLED_REASONS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String r = arr.optString(i,"");
                if (!r.isEmpty()) enabledReasons.add(r);
            }
        } catch (Exception ignored) {}
        rebuildNumberAdminMap();
    }

    private synchronized void saveEntries() {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("number", e.number);
                o.put("reason", e.reason);
                o.put("admin_id", e.adminId);
                arr.put(o);
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

    /** Rebuild the normalised-number → adminId map from current entries. */
    private void rebuildNumberAdminMap() {
        numberAdminMap.clear();
        for (Entry e : entries) {
            if (e.adminId > 0) {
                // Store both the full normalised number AND last-10-digit version
                String norm = normalise(e.number);
                numberAdminMap.put(norm, e.adminId);
                String last10 = norm.length() > 10 ? norm.substring(norm.length()-10) : norm;
                numberAdminMap.put(last10, e.adminId);
            }
        }
    }

    // ── Public getters ────────────────────────────────────────────────────

    public synchronized Set<String>      getEnabledReasons()  { return new HashSet<>(enabledReasons); }
    public synchronized int              getTotalEntries()     { return entries.size(); }
    public long                          getLastSyncTs()       { return prefs.getLong(KEY_LAST_SYNC, 0L); }
    public boolean                       isShowTotal()         { return prefs.getBoolean(KEY_SHOW_TOTAL,  true); }
    public boolean                       isShowActive()        { return prefs.getBoolean(KEY_SHOW_ACTIVE, true); }

    public synchronized boolean isReasonEnabled(String reason) { return enabledReasons.contains(reason); }

    public synchronized void setReasonEnabled(String reason, boolean enabled) {
        if (enabled) enabledReasons.add(reason); else enabledReasons.remove(reason);
        saveEnabledReasons();
        pushEnabledReasonsAsync();
    }

    public synchronized int getEnabledEntryCount() {
        int c = 0;
        for (Entry e : entries) if (enabledReasons.contains(e.reason)) c++;
        return c;
    }

    public synchronized Map<String, Integer> getCountByReason() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Entry e : entries) map.put(e.reason, map.getOrDefault(e.reason, 0) + 1);
        return map;
    }

    /** Check if a number is on the global blocklist (reason category must be enabled). */
    public synchronized String isNumberBlocked(String rawNumber) {
        if (rawNumber == null || rawNumber.isEmpty()) return null;
        if (entries.isEmpty() || enabledReasons.isEmpty()) return null;
        String norm   = normalise(rawNumber);
        String last10 = norm.length() > 10 ? norm.substring(norm.length()-10) : norm;
        for (Entry e : entries) {
            String eNorm   = normalise(e.number);
            String eLast10 = eNorm.length() > 10 ? eNorm.substring(eNorm.length()-10) : eNorm;
            boolean match  = norm.equals(eNorm) || last10.equals(eLast10);
            if (match && enabledReasons.contains(e.reason)) return e.reason;
        }
        return null;
    }

    // ── Popup image support ───────────────────────────────────────────────

    /** Get AdminConfig for the number that was just blocked (for popup display). */
    public synchronized AdminConfig getAdminConfigForNumber(String number) {
        if (number == null) return null;
        String norm   = normalise(number);
        String last10 = norm.length() > 10 ? norm.substring(norm.length()-10) : norm;
        Long adminId  = numberAdminMap.get(norm);
        if (adminId == null) adminId = numberAdminMap.get(last10);
        if (adminId == null) return null;
        return adminConfigs.get(adminId);
    }

    private String savePopupImage(long adminId, String base64Data, String mime) {
        try {
            byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
            String ext  = (mime != null && mime.contains("png")) ? ".png" : ".jpg";
            File   f    = new File(ctx.getFilesDir(), "popup_" + adminId + ext);
            try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(data); }
            return f.getAbsolutePath();
        } catch (Exception e) { Log.w(TAG, "savePopupImage: " + e.getMessage()); return null; }
    }

    // ── Sync ──────────────────────────────────────────────────────────────

    public interface SyncCallback { void onDone(boolean success, int count, String error); }

    public void syncAsync(final SyncCallback cb) {
        AuthManager auth = AuthManager.getInstance(ctx);
        if (!auth.isBackendEnabled()) {
            if (cb != null) cb.onDone(false, 0, "Backend not configured"); return;
        }
        new Thread(() -> {
            try {
                BackendClient.get(AuthManager.BACKEND_URL + "/api/global-blocklist",
                    new BackendClient.Callback() {
                        public void onResult(boolean ok, JSONObject resp, String err) {
                            if (!ok || resp == null) {
                                if (cb != null) cb.onDone(false, 0, err); return;
                            }
                            try {
                                // ── Parse entries ──────────────────────────────
                                JSONArray arr = resp.optJSONArray("entries");
                                if (arr == null) arr = new JSONArray();
                                List<Entry> fresh = new ArrayList<>();
                                for (int i = 0; i < arr.length(); i++) {
                                    JSONObject o = arr.optJSONObject(i);
                                    if (o == null) continue;
                                    String num = o.optString("number","");
                                    String rsn = o.optString("reason","");
                                    long   aid = o.optLong("added_by_admin_id", 0);
                                    if (!num.isEmpty() && !rsn.isEmpty())
                                        fresh.add(new Entry(num, rsn, aid));
                                }

                                // ── Parse admin_configs + save popup images ────
                                JSONArray adminArr = resp.optJSONArray("admin_configs");
                                Map<Long, AdminConfig> freshConfigs = new HashMap<>();
                                if (adminArr != null) {
                                    for (int j = 0; j < adminArr.length(); j++) {
                                        JSONObject ac = adminArr.optJSONObject(j);
                                        if (ac == null) continue;
                                        long   aid2  = ac.optLong("admin_id");
                                        String dName = ac.optString("display_name","");
                                        List<String> reasons = new ArrayList<>();
                                        JSONArray rArr = ac.optJSONArray("assigned_reasons");
                                        if (rArr != null)
                                            for (int k=0; k<rArr.length(); k++) reasons.add(rArr.optString(k,""));
                                        String imgPath = null;
                                        if (ac.optBoolean("has_popup_image")
                                                && !ac.isNull("popup_image_data")) {
                                            imgPath = savePopupImage(
                                                aid2,
                                                ac.optString("popup_image_data",""),
                                                ac.optString("popup_image_mime","image/jpeg"));
                                        }
                                        freshConfigs.put(aid2,
                                            new AdminConfig(aid2, dName, reasons, imgPath));
                                    }
                                }

                                // ── Update in-memory state ─────────────────────
                                synchronized (GlobalBlocklistManager.this) {
                                    entries.clear();     entries.addAll(fresh);
                                    adminConfigs.clear(); adminConfigs.putAll(freshConfigs);
                                    rebuildNumberAdminMap();
                                    saveEntries();
                                    prefs.edit().putLong(KEY_LAST_SYNC,
                                        System.currentTimeMillis()).commit();
                                }
                                fetchConfigAsync();
                                Log.d(TAG, "Synced " + fresh.size() + " entries, "
                                    + freshConfigs.size() + " admin configs");
                                if (cb != null) cb.onDone(true, fresh.size(), null);
                            } catch (Exception e) {
                                if (cb != null) cb.onDone(false, 0, e.getMessage());
                            }
                        }
                    });
            } catch (Exception e) {
                if (cb != null) cb.onDone(false, 0, e.getMessage());
            }
        }).start();
    }

    // ── Per-user enabled reasons sync ─────────────────────────────────────

    public void pushEnabledReasonsAsync() {
        AuthManager auth = AuthManager.getInstance(ctx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            synchronized (this) { for (String r : enabledReasons) arr.put(r); }
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("enabled_reasons", arr);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/global-blocklist/user-config",
                body, new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        Log.d(TAG, "Push enabled reasons: ok=" + ok);
                    }
                });
        } catch (Exception e) { Log.w(TAG, "pushEnabledReasonsAsync: " + e); }
    }

    public void pullEnabledReasonsAsync() {
        AuthManager auth = AuthManager.getInstance(ctx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        String url = AuthManager.BACKEND_URL + "/api/global-blocklist/user-config?user_id=" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String err) {
                if (!ok || resp == null) return;
                JSONArray arr = resp.optJSONArray("enabled_reasons");
                if (arr == null) return;
                synchronized (GlobalBlocklistManager.this) {
                    enabledReasons.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        String r = arr.optString(i,"").trim();
                        if (!r.isEmpty()) enabledReasons.add(r);
                    }
                    saveEnabledReasons();
                }
                Log.d(TAG, "Pulled " + arr.length() + " enabled reasons");
            }
        });
    }

    // ── Display config (show_total / show_active) ─────────────────────────

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

    // ── Clear on logout ───────────────────────────────────────────────────

    public void clear() {
        synchronized (this) { entries.clear(); numberAdminMap.clear(); adminConfigs.clear(); }
        prefs.edit().remove(KEY_ENTRIES).remove(KEY_LAST_SYNC).commit();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String normalise(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.toCharArray()) {
            if (Character.isDigit(ch)) sb.append(ch);
            else if (ch == '+' && sb.length() == 0) sb.append(ch);
        }
        return sb.toString();
    }
}
