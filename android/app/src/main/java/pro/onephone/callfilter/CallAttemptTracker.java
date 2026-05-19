package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Tracks recent REJECTED-call timestamps per normalized phone number,
 * for the per-schedule frequency-bypass feature.
 *
 * Usage:
 *   tracker.recordRejection(number, now);
 *   if (tracker.countInWindow(number, now, windowMs) >= threshold) {
 *       // let the next call through
 *   }
 *
 * Persists across process death via SharedPreferences (the call screening
 * service can be reaped between calls). Bounded so a single number can't
 * accumulate unbounded data.
 */
public class CallAttemptTracker {
    private static final String PREFS = "call_attempts_v1";
    private static final String KEY_MAP = "map";
    private static final int MAX_TIMESTAMPS_PER_NUMBER = 50;
    // Drop entries older than this regardless of window (12h, generous)
    private static final long HARD_RETENTION_MS = 12L * 60L * 60L * 1000L;

    private final SharedPreferences prefs;
    // number → list of rejection epoch-ms timestamps (most recent first)
    private final Map<String, List<Long>> cache = new HashMap<>();

    private static CallAttemptTracker instance;
    public static synchronized CallAttemptTracker getInstance(Context ctx) {
        if (instance == null) instance = new CallAttemptTracker(ctx.getApplicationContext());
        return instance;
    }

    private CallAttemptTracker(Context ctx) {
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private synchronized void load() {
        cache.clear();
        long now = System.currentTimeMillis();
        long cutoff = now - HARD_RETENTION_MS;
        try {
            JSONObject root = new JSONObject(prefs.getString(KEY_MAP, "{}"));
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String num = it.next();
                JSONArray arr = root.optJSONArray(num);
                if (arr == null) continue;
                List<Long> ts = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    long t = arr.optLong(i, 0);
                    if (t > cutoff) ts.add(t);
                }
                if (!ts.isEmpty()) cache.put(num, ts);
            }
        } catch (Exception ignored) {}
    }

    private synchronized void persist() {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, List<Long>> e : cache.entrySet()) {
                JSONArray arr = new JSONArray();
                for (Long t : e.getValue()) arr.put(t);
                root.put(e.getKey(), arr);
            }
            prefs.edit().putString(KEY_MAP, root.toString()).commit();
        } catch (Exception ignored) {}
    }

    public synchronized void recordRejection(String number, long whenMs) {
        if (number == null) return;
        String key = normalize(number);
        if (key.isEmpty()) return;

        List<Long> list = cache.get(key);
        if (list == null) { list = new ArrayList<>(); cache.put(key, list); }
        list.add(0, whenMs);
        // Drop oldest beyond cap
        while (list.size() > MAX_TIMESTAMPS_PER_NUMBER) {
            list.remove(list.size() - 1);
        }
        // Also drop anything older than hard retention
        long cutoff = whenMs - HARD_RETENTION_MS;
        Iterator<Long> it = list.iterator();
        while (it.hasNext()) {
            if (it.next() < cutoff) it.remove();
        }
        persist();
    }

    /**
     * Returns the count of recorded rejections for this number within the
     * last `windowMs` milliseconds (i.e. between [now - windowMs, now]).
     */
    public synchronized int countInWindow(String number, long nowMs, long windowMs) {
        if (number == null) return 0;
        String key = normalize(number);
        List<Long> list = cache.get(key);
        if (list == null || list.isEmpty()) return 0;
        long cutoff = nowMs - windowMs;
        int count = 0;
        for (Long t : list) {
            if (t >= cutoff) count++;
        }
        return count;
    }

    /** Wipe all tracked attempts (e.g. for testing or user reset). */
    public synchronized void clearAll() {
        cache.clear();
        prefs.edit().clear().commit();
    }

    private static String normalize(String n) {
        if (n == null) return "";
        return n.replaceAll("[^0-9+]", "");
    }
}
