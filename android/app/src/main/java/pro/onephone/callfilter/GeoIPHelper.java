package pro.onephone.callfilter;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GeoIPHelper {
    private static final String TAG = "GeoIPHelper";

    public interface Callback {
        void onCountry(String iso);
    }

    public static void detectAsync(final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                String iso = null;
                HttpURLConnection conn = null;
                try {
                    URL u = new URL("https://ipapi.co/json/");
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    JSONObject o = new JSONObject(sb.toString());
                    iso = o.optString("country_code", "");
                } catch (Exception e) {
                    Log.w(TAG, "GeoIP failed: " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
                final String fIso = iso;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() { cb.onCountry(fIso == null ? "" : fIso); }
                });
            }
        }).start();
    }
}
