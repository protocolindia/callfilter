package pro.onephone.callfilter;

import java.util.UUID;

public class Rule {
    public static final String TYPE_PREFIX  = "prefix";
    public static final String TYPE_SUFFIX  = "suffix";
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

    public boolean matches(String number) {
        if (number == null) return false;
        String n = number.replaceAll("[^0-9+]", "");
        String p = pattern.replaceAll("[^0-9+]", "");
        switch (type) {
            case TYPE_PREFIX:
                return n.startsWith(p);
            case TYPE_SUFFIX:
                return n.endsWith(p);
            case TYPE_BETWEEN:
                // pattern stored as "start-end"
                int dash = p.indexOf('-');
                if (dash <= 0) return false;
                String start = p.substring(0, dash);
                String end   = p.substring(dash + 1);
                try {
                    java.math.BigInteger nn = new java.math.BigInteger(n.replace("+",""));
                    java.math.BigInteger s  = new java.math.BigInteger(start.replace("+",""));
                    java.math.BigInteger e  = new java.math.BigInteger(end.replace("+",""));
                    return nn.compareTo(s) >= 0 && nn.compareTo(e) <= 0;
                } catch (Exception ex) { return false; }
        }
        return false;
    }
}
