package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class BlockedCallsManager {
    private static final String PREFS = "blocked_calls";
    private static final String KEY_LIST = "list";
    private static final int MAX_ENTRIES = 1000;

    private final Context ctx;
    private final SharedPreferences prefs;
    private final List<Entry> entries = new ArrayList<>();

    private static BlockedCallsManager instance;
    public static synchronized BlockedCallsManager getInstance(Context c) {
        if (instance == null) instance = new BlockedCallsManager(c.getApplicationContext());
        return instance;
    }

    public static class Entry {
        public String clientId;
        public String number;
        public String ruleType;
        public String rulePattern;
        public String ruleAction;
        public String reason;       // user-selected categorization (post-call popup)
        public long blockedAtMs;
        public boolean synced;
    }

    private BlockedCallsManager(Context c) {
        this.ctx = c;
        this.prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private synchronized void load() {
        entries.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Entry e = new Entry();
                e.clientId    = o.optString("client_id", UUID.randomUUID().toString());
                e.number      = o.optString("number", "");
                e.ruleType    = o.optString("rule_type", "");
                e.rulePattern = o.optString("rule_pattern", "");
                e.ruleAction  = o.optString("rule_action", "reject");
                e.reason      = o.optString("reason", null);
                if ("null".equals(e.reason)) e.reason = null;
                e.blockedAtMs = o.optLong("blocked_at_ms", System.currentTimeMillis());
                e.synced      = o.optBoolean("synced", false);
                entries.add(e);
            }
        } catch (Exception ignored) {}
    }

    synchronized void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("client_id", e.clientId);
                o.put("number", e.number);
                o.put("rule_type", e.ruleType);
                o.put("rule_pattern", e.rulePattern);
                o.put("rule_action", e.ruleAction);
                if (e.reason != null) o.put("reason", e.reason);
                o.put("blocked_at_ms", e.blockedAtMs);
                o.put("synced", e.synced);
                arr.put(o);
            }
            prefs.edit().putString(KEY_LIST, arr.toString()).commit();
        } catch (Exception ignored) {}
    }

    public synchronized void recordBlock(String number, String ruleType, String rulePattern, String ruleAction) {
        Entry e = new Entry();
        e.clientId    = UUID.randomUUID().toString();
        e.number      = number == null ? "" : number;
        e.ruleType    = ruleType == null ? "" : ruleType;
        e.rulePattern = rulePattern == null ? "" : rulePattern;
        e.ruleAction  = ruleAction == null ? "reject" : ruleAction;
        e.blockedAtMs = System.currentTimeMillis();
        e.synced      = false;
        entries.add(0, e);
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
        save();
    }

    /** Mark entries with the given client_ids as synced (called after upload success). */
    public synchronized void markSynced(java.util.List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) return;
        java.util.Set<String> set = new java.util.HashSet<>(clientIds);
        boolean changed = false;
        for (Entry e : entries) {
            if (set.contains(e.clientId) && !e.synced) {
                e.synced = true;
                changed = true;
            }
        }
        if (changed) save();
    }

    /** Set the reason on the MOST-RECENT blocked-call entry for this number.
     *  Used by the post-call popup follow-up dialog. Marks unsynced. */
    public synchronized boolean setReasonForMostRecent(String number, String reason) {
        if (number == null) return false;
        for (Entry e : entries) {
            if (number.equals(e.number)) {
                e.reason = reason;
                e.synced = false;
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized List<Entry> getEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized int getTotalCount() {
        return entries.size();
    }

    public synchronized void clearAll() {
        entries.clear();
        prefs.edit().clear().commit();
    }

    public synchronized void addRestoredEntry(Entry e) {
        if (e == null || e.clientId == null) return;
        for (Entry existing : entries) {
            if (e.clientId.equals(existing.clientId)) return;
        }
        entries.add(e);
        Collections.sort(entries, new Comparator<Entry>() {
            public int compare(Entry a, Entry b) {
                return Long.compare(b.blockedAtMs, a.blockedAtMs);
            }
        });
        save();
    }
}
