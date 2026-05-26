package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Caches the list of "block reasons" that the admin configures in the
 * web panel. Used by the post-call popup follow-up dialog.
 *
 * Strategy:
 *   • get() returns the cached list immediately (or a default fallback if
 *     never fetched).
 *   • refreshAsync() pulls /api/block-reasons and updates the cache.
 *     Call this on app launch / MainActivity onResume.
 */
public class BlockReasonsCache {

    private static final String TAG = "BlockReasonsCache";
    private static final String PREFS = "block_reasons_cache";
    private static final String KEY_REASONS = "reasons_json";

    private static final List<String> DEFAULTS = Arrays.asList(
        "Spam call",
        "Cybercrime / fraud",
        "Phishing",
        "Telemarketing / promotional",
        "Robocall / IVR",
        "Personal harassment",
        "Other"
    );

    private static BlockReasonsCache instance;
    public static synchronized BlockReasonsCache getInstance(Context ctx) {
        if (instance == null) instance = new BlockReasonsCache(ctx.getApplicationContext());
        return instance;
    }

    private final Context appCtx;
    private final SharedPreferences prefs;

    private BlockReasonsCache(Context c) {
        this.appCtx = c;
        this.prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<String> get() {
        String json = prefs.getString(KEY_REASONS, null);
        if (json == null) return new ArrayList<>(DEFAULTS);
        try {
            JSONArray arr = new JSONArray(json);
            List<String> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (!s.isEmpty()) out.add(s);
            }
            return out.isEmpty() ? new ArrayList<>(DEFAULTS) : out;
        } catch (Exception e) {
            return new ArrayList<>(DEFAULTS);
        }
    }

    public void refreshAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled()) return;
        BackendClient.get(AuthManager.BACKEND_URL + "/api/settings/block-reasons",
            new BackendClient.Callback() {
                public void onResult(boolean ok, JSONObject resp, String err) {
                    if (!ok || resp == null) {
                        Log.d(TAG, "Refresh failed: " + err);
                        return;
                    }
                    JSONArray arr = resp.optJSONArray("reasons");
                    if (arr == null) return;
                    prefs.edit().putString(KEY_REASONS, arr.toString()).apply();
                    Log.d(TAG, "Cached " + arr.length() + " block reasons");
                }
            });
    }
}
