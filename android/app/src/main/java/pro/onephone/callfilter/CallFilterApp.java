package pro.onephone.callfilter;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class CallFilterApp extends Application {

    public static final String CHANNEL_GENERAL = "callfilter_general";

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_GENERAL,
            "Call Filter",
            NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Status and important messages");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }
}
