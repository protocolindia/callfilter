package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-device SMS phishing/spam detector.
 *
 * Two layers run locally for instant, offline flagging:
 *   1. URL heuristics  — extract links, flag shorteners / IP-literals / look-alike domains
 *   2. Keyword rules   — admin-managed phrases synced from the backend
 * A third optional layer asks the backend for URL reputation.
 *
 * Rules are cached in SharedPreferences and refreshed from
 * GET /api/sms-protection/rules. Detection never blocks the SMS — it only
 * scores it; the caller decides whether to warn the user.
 */
public class SmsThreatDetector {

    private static final String TAG   = "SmsThreatDetector";
    private static final String PREFS = "sms_protect_prefs";
    private static final String KEY_ENABLED   = "enabled";
    private static final String KEY_MODE      = "mode";          // "passive" | "default_app"
    private static final String KEY_KEYWORDS  = "keywords_json";
    private static final String KEY_URLS      = "url_blocklist_json";
    private static final String KEY_THRESHOLD = "threshold";

    public static final String MODE_PASSIVE      = "passive";
    public static final String MODE_DEFAULT_APP  = "default_app";

    private static final Pattern URL_PATTERN = Pattern.compile(
        "((https?://|www\\.)[\\w.-]+(\\.[a-zA-Z]{2,})(/\\S*)?|\\b[\\w.-]+\\.(com|net|org|info|xyz|top|link|click|in|co)\\b(/\\S*)?)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern IP_URL = Pattern.compile(
        "https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}", Pattern.CASE_INSENSITIVE);

    private final SharedPreferences prefs;
    private final Context appCtx;
    private static SmsThreatDetector instance;

    public static synchronized SmsThreatDetector getInstance(Context c) {
        if (instance == null) instance = new SmsThreatDetector(c.getApplicationContext());
        return instance;
    }

    private SmsThreatDetector(Context c) {
        appCtx = c.getApplicationContext();
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- Settings ----

    public boolean isEnabled() { return prefs.getBoolean(KEY_ENABLED, false); }
    public void setEnabled(boolean e) { prefs.edit().putBoolean(KEY_ENABLED, e).commit(); }

    public String getMode() { return prefs.getString(KEY_MODE, MODE_PASSIVE); }
    public void setMode(String m) { prefs.edit().putString(KEY_MODE, m).commit(); }

    /** Score at or above which an SMS is flagged. Default 50. */
    public int getThreshold() { return prefs.getInt(KEY_THRESHOLD, 50); }
    public void setThreshold(int t) { prefs.edit().putInt(KEY_THRESHOLD, t).commit(); }

    // ---- Rule sync ----

    /** Pull keyword + URL rules from the backend and cache locally. */
    public void syncRulesAsync() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled()) return;
        BackendClient.get(AuthManager.BACKEND_URL + "/api/sms-protection/rules",
            new BackendClient.Callback() {
                public void onResult(boolean ok, JSONObject resp, String err) {
                    if (ok && resp != null) {
                        JSONArray kw = resp.optJSONArray("keywords");
                        JSONArray ub = resp.optJSONArray("url_blocklist");
                        if (kw != null) prefs.edit().putString(KEY_KEYWORDS, kw.toString()).commit();
                        if (ub != null) prefs.edit().putString(KEY_URLS, ub.toString()).commit();
                        Log.d(TAG, "SMS rules synced: " + (kw != null ? kw.length() : 0) + " keywords");
                    } else {
                        Log.w(TAG, "SMS rule sync failed: " + err);
                    }
                }
            });
    }

    // ---- Detection ----

    public static class Result {
        public boolean flagged;
        public int score;
        public String category = "spam";
        public final List<String> reasons = new ArrayList<>();
        public final List<String> urls = new ArrayList<>();
    }

    /** Analyze a message body. Pure-local; does not call the network. */
    public Result analyze(String body) {
        Result r = new Result();
        if (body == null || body.isEmpty()) return r;
        String lower = body.toLowerCase(Locale.ROOT);

        // Layer 1: URLs
        Matcher m = URL_PATTERN.matcher(body);
        while (m.find()) r.urls.add(m.group());
        if (IP_URL.matcher(body).find()) {
            r.score += 40; r.category = "phishing";
            r.reasons.add("Link uses a raw IP address");
        }
        // shortener / blocklisted domains
        JSONArray urlRules = safeArray(prefs.getString(KEY_URLS, "[]"));
        for (String u : r.urls) {
            String host = hostOf(u);
            for (int i = 0; i < urlRules.length(); i++) {
                JSONObject o = urlRules.optJSONObject(i);
                if (o == null) continue;
                String dom = o.optString("domain", "").toLowerCase(Locale.ROOT);
                if (!dom.isEmpty() && host.contains(dom)) {
                    r.score += 35;
                    String cat = o.optString("category", "suspicious");
                    if ("phishing".equals(cat)) r.category = "phishing";
                    r.reasons.add("Suspicious link: " + dom);
                }
            }
        }
        // generic link presence in a message with urgency is mildly suspicious
        if (!r.urls.isEmpty() && (lower.contains("click") || lower.contains("link"))) {
            r.score += 10;
            r.reasons.add("Asks you to click a link");
        }

        // Layer 2: keyword rules
        JSONArray kwRules = safeArray(prefs.getString(KEY_KEYWORDS, "[]"));
        for (int i = 0; i < kwRules.length(); i++) {
            JSONObject o = kwRules.optJSONObject(i);
            if (o == null) continue;
            String phrase = o.optString("phrase", "").toLowerCase(Locale.ROOT);
            if (!phrase.isEmpty() && lower.contains(phrase)) {
                int w = o.optInt("weight", 30);
                r.score += w;
                String cat = o.optString("category", "spam");
                if ("phishing".equals(cat)) r.category = "phishing";
                r.reasons.add("Matched \"" + o.optString("phrase", "") + "\"");
            }
        }

        if (r.score > 100) r.score = 100;
        r.flagged = r.score >= getThreshold();
        return r;
    }

    /** Optional online step: ask the backend if any extracted URL is known-bad. */
    public void checkUrlReputationAsync(String url, final ReputationCallback cb) {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || url == null || url.isEmpty()) {
            if (cb != null) cb.onResult(false, null);
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("url", url);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/sms-protection/check-url", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        if (ok && resp != null && cb != null) {
                            cb.onResult(resp.optBoolean("blocked", false), resp.optString("category", null));
                        } else if (cb != null) {
                            cb.onResult(false, null);
                        }
                    }
                });
        } catch (Exception e) {
            if (cb != null) cb.onResult(false, null);
        }
    }

    public interface ReputationCallback { void onResult(boolean blocked, String category); }

    // ---- helpers ----

    private static JSONArray safeArray(String s) {
        try { return new JSONArray(s); } catch (Exception e) { return new JSONArray(); }
    }

    private static String hostOf(String url) {
        String u = url.toLowerCase(Locale.ROOT)
            .replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        int slash = u.indexOf('/');
        return slash > 0 ? u.substring(0, slash) : u;
    }
}
