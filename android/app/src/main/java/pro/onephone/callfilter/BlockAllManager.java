package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * "Block All Now" — a panic-mode state separate from schedules.
 * Activated by the 🛑 icon in the top bar. Stays on until expiry,
 * or forever if expiresAtMs == 0.
 *
 * When active, the call blocker rejects every call EXCEPT:
 *   - mode "everything"        : no exceptions
 *   - mode "except_contacts"   : caller in device contacts allowed
 *   - mode "except_custom"     : caller in allowNumbers list allowed
 *
 * Frequency-bypass on an active schedule STILL wins over Block-All
 * (per user spec). That arbitration lives in the call blocker.
 */
public class BlockAllManager {

    private static final String TAG = "BlockAllManager";
    private static final String PREFS = "block_all_state";
    private static final String KEY_MODE          = "mode";
    private static final String KEY_EXPIRES_MS    = "expires_at_ms";
    private static final String KEY_ALLOW_NUMBERS = "allow_numbers";
    private static final String KEY_ALLOW_NAMES   = "allow_names";

    public static final String MODE_EVERYTHING       = "everything";
    public static final String MODE_EXCEPT_CONTACTS  = "except_contacts";
    public static final String MODE_EXCEPT_CUSTOM    = "except_custom";

    private final Context appCtx;
    private final SharedPreferences prefs;

    private static BlockAllManager instance;
    public static synchronized BlockAllManager getInstance(Context ctx) {
        if (instance == null) instance = new BlockAllManager(ctx.getApplicationContext());
        return instance;
    }

    private BlockAllManager(Context ctx) {
        this.appCtx = ctx;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void activate(String mode, long expiresAtMs,
                                      List<String> allowNumbers, List<String> allowNames) {
        try {
            JSONArray nums = new JSONArray();
            if (allowNumbers != null) for (String n : allowNumbers) nums.put(n);
            JSONArray names = new JSONArray();
            if (allowNames != null) for (String n : allowNames) names.put(n);

            prefs.edit()
                .putString(KEY_MODE, mode)
                .putLong(KEY_EXPIRES_MS, expiresAtMs)
                .putString(KEY_ALLOW_NUMBERS, nums.toString())
                .putString(KEY_ALLOW_NAMES, names.toString())
                .commit();
            syncToCloudAsync();
        } catch (Exception e) {
            Log.e(TAG, "activate failed", e);
        }
    }

    public synchronized void deactivate() {
        prefs.edit().clear().commit();
        syncToCloudAsync();
    }

    public synchronized boolean isActive() {
        String mode = prefs.getString(KEY_MODE, null);
        if (mode == null || mode.isEmpty()) return false;
        long ex = prefs.getLong(KEY_EXPIRES_MS, 0L);
        if (ex == 0L) return true;
        return System.currentTimeMillis() < ex;
    }

    public synchronized String getMode()      { return prefs.getString(KEY_MODE, null); }
    public synchronized long getExpiresAtMs() { return prefs.getLong(KEY_EXPIRES_MS, 0L); }

    public synchronized List<String> getAllowNumbers() { return readArr(KEY_ALLOW_NUMBERS); }
    public synchronized List<String> getAllowNames()   { return readArr(KEY_ALLOW_NAMES); }

    private List<String> readArr(String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i, ""));
        } catch (Exception ignored) {}
        return out;
    }

    /** True if {number} is allowed-through under current mode. */
    public synchronized boolean isCallerAllowed(Context ctx, String number) {
        if (!isActive()) return true;
        String mode = getMode();
        if (MODE_EVERYTHING.equals(mode)) return false;
        if (MODE_EXCEPT_CONTACTS.equals(mode)) {
            return ContactsHelper.isContactNumber(ctx, number);
        }
        if (MODE_EXCEPT_CUSTOM.equals(mode)) {
            String norm = normalize(number);
            for (String allowed : getAllowNumbers()) {
                if (normalize(allowed).equals(norm)) return true;
            }
            return false;
        }
        return true;
    }

    private static String normalize(String n) {
        if (n == null) return "";
        return n.replaceAll("[^0-9+]", "");
    }

    public String formatStatus() {
        long ex = getExpiresAtMs();
        if (ex == 0L) return "Active (until turned off)";
        long remain = ex - System.currentTimeMillis();
        if (remain <= 0) return "Expired";
        long h = remain / 3_600_000L;
        long m = (remain % 3_600_000L) / 60_000L;
        if (h > 0) return h + "h " + m + "m left";
        return m + "m left";
    }

    public void syncToCloudAsync() {
        new Thread(new Runnable() {
            public void run() {
                AuthManager auth = AuthManager.getInstance(appCtx);
                if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
                try {
                    JSONObject body = new JSONObject();
                    body.put("user_id", Long.parseLong(auth.getUserId()));
                    body.put("mode", getMode());
                    long ex = getExpiresAtMs();
                    body.put("expires_at_ms", ex == 0L ? JSONObject.NULL : ex);
                    JSONArray nums = new JSONArray();
                    for (String n : getAllowNumbers()) nums.put(n);
                    body.put("allow_numbers", nums);
                    JSONArray names = new JSONArray();
                    for (String n : getAllowNames()) names.put(n);
                    body.put("allow_names", names);
                    BackendClient.post(AuthManager.BACKEND_URL + "/api/block-all/set", body,
                        new BackendClient.Callback() {
                            public void onResult(boolean ok, JSONObject resp, String err) {
                                Log.d(TAG, "block-all sync ok=" + ok);
                            }
                        });
                } catch (Exception e) { Log.e(TAG, "sync failed", e); }
            }
        }).start();
    }

    public void pullFromCloud() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        String url = AuthManager.BACKEND_URL + "/api/block-all/get?user_id=" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String err) {
                if (!ok || resp == null) return;
                JSONObject st = resp.optJSONObject("state");
                if (st == null) return;
                String mode = st.optString("mode", null);
                if (mode == null || mode.isEmpty() || "null".equals(mode)) return;
                long ex = st.optLong("expires_at_ms", 0L);
                List<String> nums = new ArrayList<>();
                List<String> names = new ArrayList<>();
                JSONArray jn = st.optJSONArray("allow_numbers");
                if (jn != null) for (int i = 0; i < jn.length(); i++) nums.add(jn.optString(i,""));
                JSONArray jna = st.optJSONArray("allow_names");
                if (jna != null) for (int i = 0; i < jna.length(); i++) names.add(jna.optString(i,""));
                synchronized (BlockAllManager.this) {
                    JSONArray jnums = new JSONArray();
                    for (String n : nums) jnums.put(n);
                    JSONArray jnames = new JSONArray();
                    for (String n : names) jnames.put(n);
                    prefs.edit()
                        .putString(KEY_MODE, mode)
                        .putLong(KEY_EXPIRES_MS, ex)
                        .putString(KEY_ALLOW_NUMBERS, jnums.toString())
                        .putString(KEY_ALLOW_NAMES, jnames.toString())
                        .commit();
                }
            }
        });
    }
}
