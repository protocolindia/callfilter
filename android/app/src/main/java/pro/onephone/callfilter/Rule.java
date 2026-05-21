package pro.onephone.callfilter;

import java.math.BigInteger;
import java.util.UUID;

public class Rule {
    public static final String TYPE_PREFIX  = "prefix";
    public static final String TYPE_SUFFIX  = "suffix";

    /**
     * Range — matches any number whose numeric value falls between two
     * endpoints (inclusive). Pattern is stored as "start-end" where both
     * include the country dial code. The user creates a range via the
     * "anchor + N before + M after" UI; we compute start = anchor - N
     * and end = anchor + M and store that pair.
     *
     * Renamed from the old TYPE_BETWEEN. Legacy "between" rules still
     * parse correctly since storage format is identical.
     */
    public static final String TYPE_RANGE   = "range";
    /** @deprecated kept only for cloud backward-compat with v25.4 rules */
    public static final String TYPE_BETWEEN = "between";

    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_ACCEPT = "accept";

    private String id;
    private String type;
    private String pattern;
    private String action;

    public Rule(String type, String pattern, String action) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.pattern = pattern;
        this.action = action;
    }

    public Rule(String id, String type, String pattern, String action) {
        this.id = id;
        this.type = type;
        this.pattern = pattern;
        this.action = action;
    }

    public String getId()      { return id; }
    public String getType()    { return type; }
    public String getPattern() { return pattern; }
    public String getAction()  { return action; }

    /**
     * Compute a range pattern from an anchor number and "before"/"after" counts.
     * Example: anchor "+919876543210", before=5, after=10 →
     *   "+919876543205-+919876543220"
     */
    public static String buildRangePattern(String anchorWithCountry, int before, int after) {
        try {
            String numericOnly = anchorWithCountry.replaceAll("[^0-9]", "");
            // Preserve the leading '+' if present so display is consistent
            String prefix = anchorWithCountry.trim().startsWith("+") ? "+" : "";
            BigInteger anchor = new BigInteger(numericOnly);
            BigInteger start  = anchor.subtract(BigInteger.valueOf(Math.max(0, before)));
            BigInteger end    = anchor.add(BigInteger.valueOf(Math.max(0, after)));
            return prefix + start.toString() + "-" + prefix + end.toString();
        } catch (Exception e) {
            return anchorWithCountry + "-" + anchorWithCountry;
        }
    }

    public boolean matches(String number) {
        if (number == null) return false;
        String n = number.replaceAll("[^0-9+]", "");
        String p = pattern.replaceAll("[^0-9+]", "");
        switch (type) {
            case TYPE_PREFIX:
                return n.startsWith(p);
            case TYPE_SUFFIX:
                // Suffix match is on digits only — country code stripped on both sides
                return stripPlus(n).endsWith(stripPlus(p));
            case TYPE_RANGE:
            case TYPE_BETWEEN: {
                int dash = p.indexOf('-');
                if (dash <= 0) return false;
                String start = p.substring(0, dash);
                String end   = p.substring(dash + 1);
                try {
                    BigInteger nn = new BigInteger(stripPlus(n));
                    BigInteger s  = new BigInteger(stripPlus(start));
                    BigInteger e  = new BigInteger(stripPlus(end));
                    return nn.compareTo(s) >= 0 && nn.compareTo(e) <= 0;
                } catch (Exception ex) { return false; }
            }
        }
        return false;
    }

    private static String stripPlus(String s) {
        return s == null ? "" : s.replace("+", "");
    }
}
