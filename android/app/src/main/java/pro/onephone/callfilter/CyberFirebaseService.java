package pro.onephone.callfilter;

import android.util.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Receives FCM pushes. When the server signals a global-blocklist change, the
 * app pulls the latest delta in the background (even if the app is closed), so
 * newly added numbers reach devices quickly without waiting for the next open.
 *
 * Requires a Firebase project (google-services.json). Without it, this service
 * simply never fires and the app falls back to on-open / on-call sync.
 */
public class CyberFirebaseService extends FirebaseMessagingService {

    private static final String TAG = "CyberFCM";

    @Override
    public void onMessageReceived(RemoteMessage message) {
        try {
            String type = message.getData() != null ? message.getData().get("type") : null;
            Log.d(TAG, "FCM message received: " + type);
            if ("global_blocklist_changed".equals(type)) {
                GlobalBlocklistManager.getInstance(getApplicationContext()).syncDeltaAsync();
            }
        } catch (Exception e) {
            Log.w(TAG, "onMessageReceived error: " + e.getMessage());
        }
    }

    @Override
    public void onNewToken(String token) {
        // Topic-based push is used, so no per-device token storage is needed.
        Log.d(TAG, "FCM token refreshed");
    }
}
