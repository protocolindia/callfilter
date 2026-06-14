package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatically sends a configurable SMS reply when a "Block All Now" temporary
 * block is active. Supports multiple saved templates; the user picks which one
 * (or "No SMS") when activating a temporary block.
 */
public class SmsAutoResponder {

    private static final String TAG   = "SmsAutoResponder";
    private static final String PREFS = "sms_auto_prefs";
    private static final String KEY_ENABLED   = "enabled";
    private static final String KEY_MESSAGE   = "message";         // active/selected template
    private static final String KEY_TEMPLATES = "templates_json";  // list of saved templates

    public static final String DEFAULT_MESSAGE =
        "Sorry, I'm currently unavailable and your call has been filtered. Please send a message.";

    private final SharedPreferences prefs;
    private final Context appCtx;
    private static SmsAutoResponder instance;
    private boolean pulledFromCloud = false;

    public static synchronized SmsAutoResponder getInstance(Context ctx) {
        if (instance == null) instance = new SmsAutoResponder(ctx.getApplicationContext());
        return instance;
    }

    private SmsAutoResponder(Context ctx) {
        appCtx = ctx.getApplicationContext();
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Seed a default template list on first run (LOCAL ONLY - never push,
        // so we don't clobber the user's real cloud templates before pull runs).
        if (!prefs.contains(KEY_TEMPLATES)) {
            JSONArray seed = new JSONArray();
            seed.put(DEFAULT_MESSAGE);
            seed.put("I can't take your call right now. Please text me instead.");
            seed.put("This number is screening calls at the moment. Kindly send an SMS.");
            prefs.edit().putString(KEY_TEMPLATES, seed.toString()).commit();
        }
    }

    // -- Enabled flag (legacy global toggle; still respected) --

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }
    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    // -- Selected/active message --

    public String getMessage() {
        return prefs.getString(KEY_MESSAGE, DEFAULT_MESSAGE);
    }
    public void setMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) msg = DEFAULT_MESSAGE;
        prefs.edit().putString(KEY_MESSAGE, msg.trim()).commit();
    }

    // -- Multiple templates --

    public List<String> getTemplates() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_TEMPLATES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String t = arr.optString(i, "").trim();
                if (!t.isEmpty()) out.add(t);
            }
        } catch (JSONException e) {
            Log.w(TAG, "templates parse error: " + e.getMessage());
        }
        if (out.isEmpty()) out.add(DEFAULT_MESSAGE);
        return out;
    }

    public void saveTemplates(List<String> templates) {
        JSONArray arr = new JSONArray();
        for (String t : templates) {
            if (t != null && !t.trim().isEmpty()) arr.put(t.trim());
        }
        prefs.edit().putString(KEY_TEMPLATES, arr.toString()).commit();
        pushToCloudAsync();
    }

    // ---- Cloud sync ----

    /** Push the current templates to the backend (fire-and-forget). */
    public void pushToCloudAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        try {
            JSONArray arr = new JSONArray();
            for (String t : getTemplates()) arr.put(t);
            org.json.JSONObject body = new org.json.JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("templates", arr);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/sms-templates", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, org.json.JSONObject resp, String err) {
                        if (!ok) Log.w(TAG, "Template push failed: " + err);
                    }
                });
        } catch (Exception e) { Log.w(TAG, "pushToCloud error: " + e.getMessage()); }
    }

    /** Pull templates from the backend and replace local copy.
     *  Runs once per app session; restores templates after reinstall/new version. */
    public void pullFromCloudAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        if (pulledFromCloud) return;
        pulledFromCloud = true;
        String url = AuthManager.BACKEND_URL + "/api/sms-templates?user_id=" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, org.json.JSONObject resp, String err) {
                if (ok && resp != null) {
                    JSONArray arr = resp.optJSONArray("templates");
                    if (arr != null && arr.length() > 0) {
                        List<String> cloud = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            String t = arr.optString(i, "").trim();
                            if (!t.isEmpty()) cloud.add(t);
                        }
                        if (!cloud.isEmpty()) {
                            // Save locally WITHOUT re-pushing (avoid loop)
                            JSONArray out = new JSONArray();
                            for (String t : cloud) out.put(t);
                            prefs.edit().putString(KEY_TEMPLATES, out.toString()).commit();
                            Log.d(TAG, "Pulled " + cloud.size() + " templates from cloud");
                        }
                    } else {
                        // No cloud templates yet - push our local defaults up
                        pushToCloudAsync();
                    }
                } else {
                    Log.w(TAG, "Template pull failed: " + err);
                }
            }
        });
    }

    public void addTemplate(String t) {
        if (t == null || t.trim().isEmpty()) return;
        List<String> list = getTemplates();
        list.add(t.trim());
        saveTemplates(list);
    }

    public void updateTemplate(int index, String t) {
        List<String> list = getTemplates();
        if (index >= 0 && index < list.size() && t != null && !t.trim().isEmpty()) {
            list.set(index, t.trim());
            saveTemplates(list);
        }
    }

    public void removeTemplate(int index) {
        List<String> list = getTemplates();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            saveTemplates(list);
        }
    }

    // -- Send --

    /**
     * Send an auto-reply SMS only for temporary "Block All Now" blocks.
     * @param number  caller's number
     * @param rType   block reason type (only "block_all" triggers SMS)
     */
    public void sendIfEnabled(final String number, final String rType) {
        if (!isEnabled()) return;
        if (!"block_all".equals(rType)) return;
        if (number == null || number.isEmpty()) return;
        if (number.equalsIgnoreCase("Unknown") || number.equalsIgnoreCase("Private")) return;
        // Never send without the runtime permission (it may have been revoked).
        if (androidx.core.content.ContextCompat.checkSelfPermission(appCtx,
                android.Manifest.permission.SEND_SMS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Auto-SMS skipped: SEND_SMS not granted");
            return;
        }

        final String msg = getMessage();
        if (msg == null || msg.trim().isEmpty()) return;
        new Thread(() -> {
            try {
                SmsManager sms = SmsManager.getDefault();
                if (msg.length() > 160) {
                    sms.sendMultipartTextMessage(number, null, sms.divideMessage(msg), null, null);
                } else {
                    sms.sendTextMessage(number, null, msg, null, null);
                }
                Log.d(TAG, "Auto-SMS sent to " + number + " (block_all)");
            } catch (Exception e) {
                Log.w(TAG, "Auto-SMS failed to " + number + ": " + e.getMessage());
            }
        }).start();
    }
}
