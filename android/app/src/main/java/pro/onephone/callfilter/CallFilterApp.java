package pro.onephone.callfilter;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

public class CallFilterApp extends Application {

    public static final String CHANNEL_GENERAL = "callfilter_general";

    @Override
    public void onCreate() {
        super.onCreate();
        installCrashCatcher();
        createChannels();
    }

    // Saves any uncaught crash to prefs so it can be shown on next launch.
    private void installCrashCatcher() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                getSharedPreferences("crash_prefs", MODE_PRIVATE).edit()
                    .putString("last_crash", "Thread: " + t.getName() + "\n\n" + sw).commit();
            } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
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
