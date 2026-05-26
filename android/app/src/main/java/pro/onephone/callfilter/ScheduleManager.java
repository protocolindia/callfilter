package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ScheduleManager {
    private static final String TAG = "ScheduleManager";
    private static final String PREFS = "schedules_v1";
    private static final String KEY_LIST = "list";

    private final Context appCtx;
    private final SharedPreferences prefs;
    private final List<Schedule> cache = new ArrayList<>();

    private static ScheduleManager instance;
    public static synchronized ScheduleManager getInstance(Context ctx) {
        if (instance == null) instance = new ScheduleManager(ctx.getApplicationContext());
        return instance;
    }

    private ScheduleManager(Context ctx) {
        this.appCtx = ctx;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    private synchronized void load() {
        cache.clear();
        String raw = prefs.getString(KEY_LIST, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) cache.add(Schedule.fromJson(o));
            }
        } catch (Exception e) {
            Log.e(TAG, "load failed", e);
        }
    }

    private synchronized void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (Schedule s : cache) arr.put(s.toJson());
            prefs.edit().putString(KEY_LIST, arr.toString()).commit();
        } catch (Exception e) {
            Log.e(TAG, "persist failed", e);
        }
    }

    public synchronized List<Schedule> getAll() {
        return new ArrayList<>(cache);
    }

    public synchronized Schedule getByClientId(String clientId) {
        for (Schedule s : cache) {
            if (s.clientId.equals(clientId)) return s;
        }
        return null;
    }

    public synchronized void save(Schedule s) {
        s.lastToggledMs = System.currentTimeMillis();
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).clientId.equals(s.clientId)) {
                cache.set(i, s);
                persist();
                syncToCloudAsync();
                return;
            }
        }
        cache.add(s);
        persist();
        syncToCloudAsync();
    }

    public synchronized void delete(String clientId) {
        for (int i = cache.size() - 1; i >= 0; i--) {
            if (cache.get(i).clientId.equals(clientId)) cache.remove(i);
        }
        persist();
        syncToCloudAsync();
    }

    public synchronized void toggleEnabled(String clientId) {
        Schedule s = getByClientId(clientId);
        if (s == null) return;
        s.isEnabled = !s.isEnabled;
        s.lastToggledMs = System.currentTimeMillis();
        persist();
        syncToCloudAsync();
    }

    public synchronized void quickActivate(String clientId, int minutes) {
        Schedule s = getByClientId(clientId);
        if (s == null) return;
        s.quickUntilMs = System.currentTimeMillis() + (long) minutes * 60_000L;
        s.lastToggledMs = System.currentTimeMillis();
        s.isEnabled = true;
        persist();
        syncToCloudAsync();
    }

    public synchronized void cancelQuickActivation(String clientId) {
        Schedule s = getByClientId(clientId);
        if (s == null) return;
        s.quickUntilMs = 0L;
        s.lastToggledMs = System.currentTimeMillis();
        persist();
        syncToCloudAsync();
    }

    public synchronized Schedule getActiveSchedule(long whenMs) {
        Schedule winner = null;
        for (Schedule s : cache) {
            if (s.isActiveAt(whenMs)) {
                if (winner == null || s.lastToggledMs > winner.lastToggledMs) {
                    winner = s;
                }
            }
        }
        return winner;
    }

    public void syncToCloudAsync() {
        new Thread(new Runnable() {
            public void run() {
                AuthManager auth = AuthManager.getInstance(appCtx);
                if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
                try {
                    JSONObject body = new JSONObject();
                    body.put("user_id", Long.parseLong(auth.getUserId()));
                    JSONArray arr = new JSONArray();
                    synchronized (ScheduleManager.this) {
                        for (Schedule s : cache) arr.put(s.toJson());
                    }
                    body.put("schedules", arr);
                    BackendClient.post(AuthManager.BACKEND_URL + "/api/schedules/sync", body,
                        new BackendClient.Callback() {
                            public void onResult(boolean ok, JSONObject resp, String err) {
                                Log.d(TAG, "sync ok=" + ok);
                            }
                        });
                } catch (Exception e) { Log.e(TAG, "sync failed", e); }
            }
        }).start();
    }

    /** Always pulls from cloud — call on resume so admin-added schedules arrive. */
    public void pullFromCloud() {
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;
        String url = AuthManager.BACKEND_URL + "/api/schedules/list?user_id=" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String err) {
                if (!ok || resp == null) return;
                JSONArray arr = resp.optJSONArray("schedules");
                if (arr == null || arr.length() == 0) return;
                synchronized (ScheduleManager.this) {
                    cache.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) cache.add(Schedule.fromJson(o));
                    }
                    persist();
                    Log.d(TAG, "Pulled " + cache.size() + " schedules from cloud");
                }
            }
        });
    }

    public void pullFromCloudIfEmpty() {
        if (!cache.isEmpty()) return;
        AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) return;

        String url = AuthManager.BACKEND_URL + "/api/schedules/list?user_id=" + auth.getUserId();
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String err) {
                if (!ok || resp == null) return;
                JSONArray arr = resp.optJSONArray("schedules");
                if (arr == null) return;
                synchronized (ScheduleManager.this) {
                    cache.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) cache.add(Schedule.fromJson(o));
                    }
                    persist();
                }
            }
        });
    }
}
