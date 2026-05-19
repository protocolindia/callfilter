package pro.onephone.callfilter;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class BackendClient {
    private static final String TAG = "BackendClient";

    public interface Callback {
        void onResult(boolean ok, JSONObject resp, String error);
    }

    public static void post(final String url, final JSONObject body, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(url);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();

                    int code = conn.getResponseCode();
                    java.io.InputStream stream = (code >= 200 && code < 300)
                            ? conn.getInputStream() : conn.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (stream != null) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(stream));
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();
                    }
                    final String raw = sb.toString();
                    JSONObject resp = null;
                    try { resp = raw.isEmpty() ? new JSONObject() : new JSONObject(raw); }
                    catch (Exception ignored) {}

                    final boolean ok = code >= 200 && code < 300 && resp != null;
                    final JSONObject fResp = resp;
                    final String err = ok ? null
                            : (resp != null && resp.has("error") ? resp.optString("error") : "HTTP " + code);
                    postMain(new Runnable() {
                        public void run() { cb.onResult(ok, fResp, err); }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "POST failed: " + e.getMessage(), e);
                    postMain(new Runnable() {
                        public void run() { cb.onResult(false, null, e.getMessage()); }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    public static void get(final String url, final Callback cb) {
        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL u = new URL(url);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("Accept", "application/json");

                    int code = conn.getResponseCode();
                    java.io.InputStream stream = (code >= 200 && code < 300)
                            ? conn.getInputStream() : conn.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (stream != null) {
                        BufferedReader r = new BufferedReader(new InputStreamReader(stream));
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();
                    }
                    final String raw = sb.toString();
                    JSONObject resp = null;
                    try { resp = raw.isEmpty() ? new JSONObject() : new JSONObject(raw); }
                    catch (Exception ignored) {}

                    final boolean ok = code >= 200 && code < 300 && resp != null;
                    final JSONObject fResp = resp;
                    final String err = ok ? null
                            : (resp != null && resp.has("error") ? resp.optString("error") : "HTTP " + code);
                    postMain(new Runnable() {
                        public void run() { cb.onResult(ok, fResp, err); }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "GET failed: " + e.getMessage(), e);
                    postMain(new Runnable() {
                        public void run() { cb.onResult(false, null, e.getMessage()); }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private static void postMain(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }
}
