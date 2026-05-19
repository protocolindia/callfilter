package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.util.Random;

public class AuthManager {
    private static final String TAG = "AuthManager";

    public static final String BACKEND_URL = BuildConfig.BACKEND_URL;
    public static final boolean BACKEND_LIVE = true;

    private static final String PREFS = "auth_prefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DIAL_CODE = "dial_code";
    private static final String KEY_MOBILE = "mobile";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_VERIFIED = "verified";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_PENDING_OTP = "pending_otp";

    private final Context appContext;
    private final SharedPreferences prefs;

    private static AuthManager instance;
    public static synchronized AuthManager getInstance(Context ctx) {
        if (instance == null) instance = new AuthManager(ctx.getApplicationContext());
        return instance;
    }

    private AuthManager(Context ctx) {
        this.appContext = ctx;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isBackendEnabled() {
        return BACKEND_LIVE && BACKEND_URL != null && !BACKEND_URL.isEmpty();
    }

    public String getUserId()   { return prefs.getString(KEY_USER_ID, ""); }
    public String getDialCode() { return prefs.getString(KEY_DIAL_CODE, ""); }
    public String getMobile()   { return prefs.getString(KEY_MOBILE, ""); }
    public String getFullNumber() { return getDialCode() + getMobile(); }
    public boolean isVerified() { return prefs.getBoolean(KEY_VERIFIED, false); }
    public boolean hasPin()     { return !prefs.getString(KEY_PIN_HASH, "").isEmpty(); }
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false)
            && !getUserId().isEmpty()
            && hasPin();
    }

    public interface SignupCallback {
        /** devOtp is non-null only when admin setting otp_show_in_response=true */
        void onSuccess(String devOtp);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface OtpVerifyCallback {
        void onSuccess(boolean pinAlreadySet, String pinHashFromServer);
        void onError(String message);
    }

    public interface AccountCheckCallback {
        void onResult(boolean stillExists);
    }

    public void startSignup(final String dialCode, final String mobile,
                            final String countryIso, final SignupCallback cb) {
        // If the number changed, wipe local state first
        String existingMobile = getMobile();
        String existingDial   = getDialCode();
        if (!existingMobile.isEmpty()
            && (!existingMobile.equals(mobile) || !existingDial.equals(dialCode))) {
            resetAccount();
        }

        prefs.edit()
            .putString(KEY_DIAL_CODE, dialCode)
            .putString(KEY_MOBILE, mobile)
            .commit();

        if (!isBackendEnabled()) {
            // Dev fallback — generate local OTP
            String otp = String.format("%04d", new Random().nextInt(10000));
            prefs.edit().putString(KEY_PENDING_OTP, otp).commit();
            Log.d(TAG, "Dev OTP: " + otp);
            cb.onSuccess(otp);
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("dial_code", dialCode);
            body.put("mobile", mobile);
            body.put("country_iso", countryIso == null ? "" : countryIso);
            body.put("device_info", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            BackendClient.post(BACKEND_URL + "/api/signup", body, new BackendClient.Callback() {
                public void onResult(boolean ok, JSONObject resp, String error) {
                    if (ok && resp != null) {
                        prefs.edit()
                            .putString(KEY_USER_ID, String.valueOf(resp.optLong("user_id", 0L)))
                            .commit();
                        // When admin setting otp_show_in_response=true the
                        // backend includes the code in the JSON. Pass it through
                        // so OtpActivity can display & auto-fill it.
                        String devOtp = resp.optString("otp", null);
                        if (devOtp != null && devOtp.isEmpty()) devOtp = null;
                        cb.onSuccess(devOtp);
                    } else {
                        cb.onError(error != null ? error : "Signup failed");
                    }
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    public void verifyOtp(final String entered, final SimpleCallback cb) {
        verifyOtp(entered, new OtpVerifyCallback() {
            public void onSuccess(boolean pinSet, String hash) { cb.onSuccess(); }
            public void onError(String message) { cb.onError(message); }
        });
    }

    public void verifyOtp(final String entered, final OtpVerifyCallback cb) {
        if (!isBackendEnabled()) {
            String expected = prefs.getString(KEY_PENDING_OTP, "");
            if (!expected.isEmpty() && expected.equals(entered.trim())) {
                prefs.edit().putBoolean(KEY_VERIFIED, true).remove(KEY_PENDING_OTP).commit();
                cb.onSuccess(false, null);
            } else {
                cb.onError("Incorrect OTP");
            }
            return;
        }

        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(getUserId().isEmpty() ? "0" : getUserId()));
            body.put("code", entered.trim());

            BackendClient.post(BACKEND_URL + "/api/verify-otp", body, new BackendClient.Callback() {
                public void onResult(boolean ok, JSONObject resp, String error) {
                    if (ok) {
                        prefs.edit().putBoolean(KEY_VERIFIED, true).commit();
                        boolean pinSet = resp != null && resp.optBoolean("pin_set", false);
                        String hashFromServer = resp != null ? resp.optString("pin_hash", null) : null;
                        if (pinSet && hashFromServer != null && !hashFromServer.isEmpty()) {
                            prefs.edit().putString(KEY_PIN_HASH, hashFromServer).commit();
                        }
                        cb.onSuccess(pinSet, hashFromServer);
                    } else {
                        cb.onError(error != null ? error : "Verification failed");
                    }
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    public void setPin(final String pin, final SimpleCallback cb) {
        final String hash = sha256(pin);
        if (!isBackendEnabled()) {
            prefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .putBoolean(KEY_LOGGED_IN, true)
                .commit();
            cb.onSuccess();
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(getUserId()));
            body.put("pin_hash", hash);
            BackendClient.post(BACKEND_URL + "/api/set-pin", body, new BackendClient.Callback() {
                public void onResult(boolean ok, JSONObject resp, String error) {
                    if (ok) {
                        prefs.edit()
                            .putString(KEY_PIN_HASH, hash)
                            .putBoolean(KEY_LOGGED_IN, true)
                            .commit();
                        cb.onSuccess();
                    } else {
                        cb.onError(error != null ? error : "Set PIN failed");
                    }
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    public boolean checkPin(String pin) {
        String saved = prefs.getString(KEY_PIN_HASH, "");
        if (saved.isEmpty()) return false;
        return saved.equals(sha256(pin));
    }

    public void markLoggedIn() {
        prefs.edit().putBoolean(KEY_LOGGED_IN, true).commit();
    }

    public void logout() {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).commit();
    }

    public void resetAccount() {
        prefs.edit().clear().commit();
        Context ctx = appContext;
        if (ctx != null) {
            ctx.getSharedPreferences("CallFilterRules", Context.MODE_PRIVATE).edit().clear().commit();
            ctx.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE).edit().clear().commit();
            ctx.getSharedPreferences("blocked_calls", Context.MODE_PRIVATE).edit().clear().commit();
            ctx.getSharedPreferences("subscription", Context.MODE_PRIVATE).edit().clear().commit();
            ctx.getSharedPreferences("schedules_v1", Context.MODE_PRIVATE).edit().clear().commit();
            ctx.getSharedPreferences("block_all_state", Context.MODE_PRIVATE).edit().clear().commit();
            ctx.getSharedPreferences("freq_tracker", Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    public void verifyAccountStillExists(final AccountCheckCallback cb) {
        if (!isBackendEnabled() || getUserId().isEmpty()) {
            cb.onResult(true);
            return;
        }
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(getUserId()));
            body.put("dial_code", getDialCode());
            body.put("mobile", getMobile());
            BackendClient.post(BACKEND_URL + "/api/check-account", body, new BackendClient.Callback() {
                public void onResult(boolean ok, JSONObject resp, String error) {
                    if (!ok && resp != null && resp.has("error")
                        && resp.optString("error").toLowerCase().contains("not found")) {
                        resetAccount();
                        cb.onResult(false);
                        return;
                    }
                    if (ok && resp != null) {
                        SubscriptionManager.getInstance(appContext)
                            .updateFromJson(resp.optJSONObject("subscription"));
                    }
                    cb.onResult(true);
                }
            });
        } catch (Exception e) {
            cb.onResult(true);
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] b = d.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }
}
