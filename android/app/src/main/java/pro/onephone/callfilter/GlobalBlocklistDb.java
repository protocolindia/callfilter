package pro.onephone.callfilter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLite-backed store for the global blocklist. Scales to hundreds of thousands
 * of numbers with an indexed lookup, instead of holding everything in memory.
 *
 * Numbers are stored normalized (digits only, last 10) so matching is fast and
 * format-independent. A single-row "meta" table holds the delta sync cursor.
 */
public class GlobalBlocklistDb extends SQLiteOpenHelper {

    private static final String TAG = "GlobalBlocklistDb";
    private static final String DB_NAME = "global_blocklist.db";
    private static final int DB_VERSION = 1;

    private static GlobalBlocklistDb instance;

    public static synchronized GlobalBlocklistDb getInstance(Context c) {
        if (instance == null) instance = new GlobalBlocklistDb(c.getApplicationContext());
        return instance;
    }

    private GlobalBlocklistDb(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE blocklist ("
            + "norm TEXT PRIMARY KEY, "      // normalized number (last 10 digits)
            + "reason TEXT, "
            + "admin_id INTEGER)");
        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS blocklist");
        db.execSQL("DROP TABLE IF EXISTS meta");
        onCreate(db);
    }

    /** Normalize to digits-only, last 10 (matches ContactsCacheManager style). */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        String s = sb.toString();
        return s.length() > 10 ? s.substring(s.length() - 10) : s;
    }

    /** Fast indexed lookup. Returns the reason if blocked, else null. */
    public String reasonFor(String number) {
        String norm = normalize(number);
        if (norm.isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("blocklist", new String[]{"reason"},
                "norm = ?", new String[]{norm}, null, null, null, "1");
            if (c.moveToFirst()) return c.getString(0);
        } catch (Exception e) {
            Log.w(TAG, "lookup error: " + e.getMessage());
        } finally { if (c != null) c.close(); }
        return null;
    }

    public Long adminIdFor(String number) {
        String norm = normalize(number);
        if (norm.isEmpty()) return null;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("blocklist", new String[]{"admin_id"},
                "norm = ?", new String[]{norm}, null, null, null, "1");
            if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception e) {
            Log.w(TAG, "adminId error: " + e.getMessage());
        } finally { if (c != null) c.close(); }
        return null;
    }

    /** Apply a batch of delta changes inside one transaction (fast for bulk). */
    public void applyChanges(org.json.JSONArray changes) {
        if (changes == null || changes.length() == 0) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < changes.length(); i++) {
                org.json.JSONObject o = changes.optJSONObject(i);
                if (o == null) continue;
                String norm = normalize(o.optString("number", ""));
                if (norm.isEmpty()) continue;
                String op = o.optString("op", "upsert");
                if ("remove".equals(op)) {
                    db.delete("blocklist", "norm = ?", new String[]{norm});
                } else {
                    ContentValues cv = new ContentValues();
                    cv.put("norm", norm);
                    cv.put("reason", o.optString("reason", ""));
                    long aid = o.optLong("added_by_admin_id", 0);
                    if (aid > 0) cv.put("admin_id", aid);
                    db.insertWithOnConflict("blocklist", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "applyChanges error: " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

    public int count() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM blocklist", null);
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Exception e) {
            Log.w(TAG, "count error: " + e.getMessage());
        } finally { if (c != null) c.close(); }
        return 0;
    }

    // ---- delta cursor ----
    public long getCursor() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("meta", new String[]{"v"}, "k = ?", new String[]{"cursor"}, null, null, null);
            if (c.moveToFirst()) return Long.parseLong(c.getString(0));
        } catch (Exception e) { /* default 0 */ }
        finally { if (c != null) c.close(); }
        return 0L;
    }

    public void setCursor(long cursor) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("k", "cursor");
        cv.put("v", String.valueOf(cursor));
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
