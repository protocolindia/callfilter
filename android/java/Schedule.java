package pro.onephone.callfilter;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * A single user-defined "do not disturb" window.
 *
 * Time is expressed as start_minute / end_minute in 0..1439 (minute of the day).
 * A window may wrap past midnight: e.g. start=22*60, end=7*60 means 22:00 -> 07:00.
 *
 * Days_mask: bit 0 = Sunday, 1 = Monday, ..., 6 = Saturday. 127 (=0x7F) = every day.
 *
 * When active (current time inside window on an allowed day AND is_enabled true)
 * OR when quick_until_ms is in the future:
 *   - The user's normal rules apply (existing behavior)
 *   - Plus: any caller NOT in allow_numbers is rejected.
 */
public class Schedule {
    public String clientId;
    public String name;
    public int startMinute;          // 0..1439
    public int endMinute;            // 0..1439 (may equal startMinute meaning 24h)
    public int daysMask;             // bit 0=Sun..6=Sat
    public boolean isEnabled;        // user's on/off switch
    public List<String> allowNumbers;
    public List<String> allowNames;
    public long quickUntilMs;        // 0 = no quick activation
    public long lastToggledMs;       // ms epoch — used for overlap resolution

    public Schedule() {
        this.clientId = UUID.randomUUID().toString();
        this.name = "";
        this.startMinute = 22 * 60;     // 22:00
        this.endMinute = 7 * 60;        // 07:00
        this.daysMask = 0x7F;           // every day
        this.isEnabled = true;
        this.allowNumbers = new ArrayList<>();
        this.allowNames = new ArrayList<>();
        this.quickUntilMs = 0L;
        this.lastToggledMs = System.currentTimeMillis();
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("client_id", clientId);
        o.put("name", name);
        o.put("start_minute", startMinute);
        o.put("end_minute", endMinute);
        o.put("days_mask", daysMask);
        o.put("is_enabled", isEnabled);
        JSONArray nums = new JSONArray();
        for (String n : allowNumbers) nums.put(n);
        o.put("allow_numbers", nums);
        JSONArray names = new JSONArray();
        for (String n : allowNames) names.put(n);
        o.put("allow_names", names);
        o.put("quick_until_ms", quickUntilMs);
        o.put("last_toggled_at", lastToggledMs);
        return o;
    }

    public static Schedule fromJson(JSONObject o) {
        Schedule s = new Schedule();
        s.clientId    = o.optString("client_id", UUID.randomUUID().toString());
        s.name        = o.optString("name", "Schedule");
        s.startMinute = o.optInt("start_minute", 22 * 60);
        s.endMinute   = o.optInt("end_minute", 7 * 60);
        s.daysMask    = o.optInt("days_mask", 0x7F);
        s.isEnabled   = o.optBoolean("is_enabled", true);
        s.quickUntilMs = o.optLong("quick_until_ms", 0L);
        s.lastToggledMs = o.optLong("last_toggled_ms",
                          o.optLong("last_toggled_at", System.currentTimeMillis()));
        s.allowNumbers = new ArrayList<>();
        JSONArray nums = o.optJSONArray("allow_numbers");
        if (nums != null) {
            for (int i = 0; i < nums.length(); i++) s.allowNumbers.add(nums.optString(i, ""));
        }
        s.allowNames = new ArrayList<>();
        JSONArray names = o.optJSONArray("allow_names");
        if (names != null) {
            for (int i = 0; i < names.length(); i++) s.allowNames.add(names.optString(i, ""));
        }
        return s;
    }

    /** Returns true if this schedule's time window covers the given moment. */
    public boolean isActiveAt(long whenMs) {
        if (!isEnabled) return false;

        // Quick-activate overrides the time window
        if (quickUntilMs > whenMs) return true;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(whenMs);
        int dow = cal.get(Calendar.DAY_OF_WEEK) - 1;       // Sunday=0..Saturday=6
        if ((daysMask & (1 << dow)) == 0) return false;

        int minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        if (startMinute == endMinute) return true;          // full 24h
        if (startMinute < endMinute) {
            return minute >= startMinute && minute < endMinute;
        }
        // Wraps past midnight
        return minute >= startMinute || minute < endMinute;
    }

    public boolean isCallerAllowed(String number) {
        if (number == null) return false;
        String norm = normalize(number);
        for (String n : allowNumbers) {
            if (normalize(n).equals(norm)) return true;
        }
        return false;
    }

    private static String normalize(String n) {
        if (n == null) return "";
        return n.replaceAll("[^0-9+]", "");
    }

    /** Pretty-prints the time window like "22:00 — 07:00" */
    public String formatWindow() {
        return String.format("%02d:%02d \u2014 %02d:%02d",
            startMinute / 60, startMinute % 60,
            endMinute   / 60, endMinute   % 60);
    }

    /** Pretty-prints the days like "Mon, Wed, Fri" or "Every day" */
    public String formatDays() {
        if (daysMask == 0x7F) return "Every day";
        if (daysMask == 0x3E) return "Mon \u2013 Fri";   // 0011_1110
        if (daysMask == 0x41) return "Weekends";          // 0100_0001
        String[] names = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((daysMask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(names[i]);
            }
        }
        return sb.length() > 0 ? sb.toString() : "Never";
    }
}
