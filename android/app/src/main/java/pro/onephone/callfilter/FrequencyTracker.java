package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tracks recent REJECTED calls per phone number so that schedules with
 * frequency-bypass enabled can detect "urgent caller" patterns:
 *   if the same number was rejected freqCount times within freqWindowMin
 *   minutes, the next call rings through.
 *
 * Storage: SharedPreferences "freq_tracker" key "events", JSON array of
 * { "n": "+91...", "t": 1747500000000 } objects. Old entries pruned on every read.
 *
 * Memory cap: 200 events total. Beyond that, oldest are dropped.
 */
public class FrequencyTracker {

    private static final String TAG = "FrequencyTracker";
    private static final String PREFS = "freq_tracker";
    private static final String KEY = "events";
    private static final int MAX_EVENTS = 200;
    // Keep events that could conceivably be inside ANY active schedule's window.
    // Pruning anything older than 24h is more than enough.
    private static final long PRUNE_THRESHOLD_MS = 24L * 60L * 60L * 1000L;

    private final Context appCtx;
    private final SharedPreferences prefs;

    private static FrequencyTracker instance;
    public static synchronized FrequencyTracker getInstance(Context ctx) {
        if (instance == null) instance = new FrequencyTracker(ctx.getApplicationContext());
        return instance;
    }

    private FrequencyTracker(Context ctx) {
        this.appCtx = ctx;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalize(String n) {
        if (n == null) return "";
        return n.replaceAll("[^0-9+]", "");
    }

    /** Record that a call from {number} was REJECTED at {whenMs}. */
    public synchronized void recordRejection(String number, long whenMs) {
        String norm = normalize(number);
        if (norm.isEmpty()) return;
        try {
            JSONArray arr = loadArrayPruned(whenMs);
            JSONObject e = new JSONObject();
            e.put("n", norm);
            e.put("t", whenMs);
            arr.put(e);
            // Cap memory
            while (arr.length() > MAX_EVENTS) arr.remove(0);
            prefs.edit().putString(KEY, arr.toString()).commit();
        } catch (Exception e) {
            Log.e(TAG, "recordRejection failed", e);
        }
    }

    /**
     * Returns true if the urgent-caller threshold is met right NOW:
     *   count of previous rejections from {number} within the last
     *   {windowMin} minutes is >= {countThreshold}.
     *
     * This is called BEFORE recording the current call. So when the
     * threshold says "3 calls in 10 min triggers bypass", on the 4th call
     * we look back and see 3 prior rejections → bypass.
     */
    public synchronized boolean shouldBypass(String number, long whenMs,
                                             int countThreshold, int windowMin) {
        if (countThreshold <= 0 || windowMin <= 0) return false;
        String norm = normalize(number);
        if (norm.isEmpty()) return false;

        long windowStart = whenMs - (long) windowMin * 60_000L;
        int n = 0;
        try {
            JSONArray arr = loadArrayPruned(whenMs);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                long t = o.optLong("t", 0L);
                if (t < windowStart) continue;
                if (norm.equals(o.optString("n", ""))) n++;
            }
        } catch (Exception e) {
            Log.e(TAG, "shouldBypass read failed", e);
        }
        return n >= countThreshold;
    }

    /** Clear all tracked events (e.g. on logout/reset). */
    public synchronized void clear() {
        prefs.edit().clear().commit();
    }

    /** Internal — read events array and prune anything older than 24h. */
    private JSONArray loadArrayPruned(long nowMs) throws Exception {
        String raw = prefs.getString(KEY, "[]");
        JSONArray arr = new JSONArray(raw);
        long cutoff = nowMs - PRUNE_THRESHOLD_MS;
        JSONArray kept = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (o.optLong("t", 0L) >= cutoff) kept.put(o);
        }
        return kept;
    }
}
