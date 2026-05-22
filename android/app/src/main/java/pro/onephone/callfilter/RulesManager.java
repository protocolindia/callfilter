package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class RulesManager {
    private static final String PREFS = "CallFilterRules";
    private static final String KEY_RULES = "rules";
    private static final String KEY_CONTACTS_ONLY = "contacts_only_mode";

    private final Context ctx;
    private final SharedPreferences prefs;
    private final List<Rule> rules = new ArrayList<>();

    private static RulesManager instance;
    public static synchronized RulesManager getInstance(Context c) {
        if (instance == null) instance = new RulesManager(c.getApplicationContext());
        return instance;
    }

    private RulesManager(Context c) {
        this.ctx = c;
        this.prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        reload();
    }

    public synchronized void reload() {
        rules.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_RULES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                rules.add(new Rule(
                    o.optString("id", java.util.UUID.randomUUID().toString()),
                    o.optString("type", "prefix"),
                    o.optString("pattern", ""),
                    o.optString("action", "reject")
                ));
            }
        } catch (Exception ignored) {}
    }

    private synchronized void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (Rule r : rules) {
                JSONObject o = new JSONObject();
                o.put("id", r.getId());
                o.put("type", r.getType());
                o.put("pattern", r.getPattern());
                o.put("action", r.getAction());
                arr.put(o);
            }
            prefs.edit().putString(KEY_RULES, arr.toString()).commit();
        } catch (Exception ignored) {}
    }

    public synchronized List<Rule> getRules() {
        return new ArrayList<>(rules);
    }

    public synchronized void addRule(String pattern, String type, String action) {
        Rule r = new Rule(type, pattern, action);
        rules.add(r);
        persist();
        // Differential push to backend
        SyncManager.getInstance(ctx).pushAddedRule(r);
    }

    /** Used when merging from cloud — preserves the existing client_id. */
    public synchronized void addRuleWithId(String id, String pattern, String type, String action) {
        rules.add(new Rule(id, type, pattern, action));
        persist();
        // No push — this rule came FROM the cloud
    }

    public synchronized void removeRule(String id) {
        boolean found = false;
        for (int i = rules.size() - 1; i >= 0; i--) {
            if (rules.get(i).getId().equals(id)) { rules.remove(i); found = true; }
        }
        persist();
        if (found) SyncManager.getInstance(ctx).pushDeletedRule(id);
    }

    public boolean isContactsOnlyMode() {
        return prefs.getBoolean(KEY_CONTACTS_ONLY, false);
    }

    public void setContactsOnlyMode(boolean on) {
        prefs.edit().putBoolean(KEY_CONTACTS_ONLY, on).commit();
    }

    /**
     * Evaluate rules for a caller. Returns:
     *   "accept" — explicit accept rule matched (overrides everything)
     *   "reject" — reject rule matched
     *   null     — no rule matched
     */
    /**
     * Returns "accept" if any ACCEPT rule matches, else null.
     * Used by the call blocker to short-circuit BEFORE checking Block-All etc.
     */
    public synchronized String evaluateAccept(String number) {
        for (Rule r : rules) {
            if (Rule.ACTION_ACCEPT.equals(r.getAction()) && r.matches(number)) return "accept";
        }
        return null;
    }

    /**
     * Returns "reject" if any REJECT rule matches, else null.
     * Used after accept + Block-All checks.
     */
    public synchronized String evaluateReject(String number) {
        for (Rule r : rules) {
            if (Rule.ACTION_REJECT.equals(r.getAction()) && r.matches(number)) return "reject";
        }
        return null;
    }

    /** Legacy combined evaluator. Accept wins over Reject. */
    public synchronized String evaluate(String number) {
        String a = evaluateAccept(number);
        if (a != null) return a;
        return evaluateReject(number);
    }
}
