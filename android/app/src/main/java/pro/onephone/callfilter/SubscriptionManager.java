package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;

public class SubscriptionManager {
    private static final String TAG = "SubscriptionManager";
    private static final String PREFS = "subscription";

    private static final String KEY_ACTIVE      = "active";
    private static final String KEY_IS_TRIAL    = "is_trial";
    private static final String KEY_EXPIRES_MS  = "expires_at_ms";
    private static final String KEY_PLAN_NAME   = "plan_name";
    private static final String KEY_LAST_CHECK  = "last_check_ms";

    private final Context appCtx;
    private final SharedPreferences prefs;

    private static SubscriptionManager instance;
    public static synchronized SubscriptionManager getInstance(Context ctx) {
        if (instance == null) instance = new SubscriptionManager(ctx.getApplicationContext());
        return instance;
    }

    private SubscriptionManager(Context ctx) {
        this.appCtx = ctx;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasBeenChecked() {
        return prefs.getLong(KEY_LAST_CHECK, 0L) > 0L;
    }

    public boolean isActive() {
        boolean cached = prefs.getBoolean(KEY_ACTIVE, false);
        long expiresAt = prefs.getLong(KEY_EXPIRES_MS, 0L);
        if (!cached) return false;
        return System.currentTimeMillis() < expiresAt;
    }

    public boolean isTrial()       { return prefs.getBoolean(KEY_IS_TRIAL, false); }
    public long    getExpiresMs()  { return prefs.getLong(KEY_EXPIRES_MS, 0L); }
    public String  getPlanName()   { return prefs.getString(KEY_PLAN_NAME, ""); }

    public long getSecondsRemaining() {
        long now = System.currentTimeMillis();
        long ex  = getExpiresMs();
        if (ex <= now) return 0;
        return (ex - now) / 1000L;
    }

    public long getDaysRemaining() {
        return getSecondsRemaining() / 86400L;
    }

    public String getStatusLabel() {
        if (!hasBeenChecked()) return "Checking…";
        if (!isActive()) return "Subscription expired";
        long days = getDaysRemaining();
        String which = isTrial() ? "Trial" : "Active";
        if (days <= 0) {
            long hours = getSecondsRemaining() / 3600L;
            return which + " · " + Math.max(1, hours) + "h left";
        }
        return which + " · " + days + " day" + (days == 1 ? "" : "s") + " left";
    }

    public void updateFromJson(JSONObject sub) {
        if (sub == null) {
            prefs.edit()
                .putBoolean(KEY_ACTIVE, false)
                .putBoolean(KEY_IS_TRIAL, false)
                .putLong(KEY_EXPIRES_MS, 0L)
                .putString(KEY_PLAN_NAME, "")
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .commit();
            return;
        }
        boolean active   = sub.optBoolean("active", false);
        boolean isTrial  = sub.optBoolean("is_trial", false);
        long secsLeft    = sub.optLong("seconds_remaining", 0L);
        long expiresMs   = System.currentTimeMillis() + (secsLeft * 1000L);
        String planName  = sub.optString("plan_name", "");
        if ("null".equals(planName)) planName = "";
        prefs.edit()
            .putBoolean(KEY_ACTIVE, active)
            .putBoolean(KEY_IS_TRIAL, isTrial)
            .putLong(KEY_EXPIRES_MS, expiresMs)
            .putString(KEY_PLAN_NAME, planName)
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .commit();
        Log.d(TAG, "cached: active=" + active + ", trial=" + isTrial + ", secsLeft=" + secsLeft);
    }

    public void refreshAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        String url = AuthManager.BACKEND_URL + "/api/subscription/" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String error) {
                if (ok && resp != null) {
                    updateFromJson(resp.optJSONObject("subscription"));
                    String nm = resp.optString("name", "");
                    if (nm != null && !nm.isEmpty()) {
                        AuthManager.getInstance(appCtx).setName(nm);
                    }
                } else {
                    Log.e(TAG, "refresh failed: " + error);
                }
            }
        });
    }

    public void clear() {
        prefs.edit().clear().commit();
    }
}
