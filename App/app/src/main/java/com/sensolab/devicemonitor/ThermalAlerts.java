package com.sensolab.devicemonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Thermal alert helper. Posts a system notification when CPU crosses a threshold,
 * with hysteresis so it doesn't spam: once tripped, the temperature has to drop
 * back below the recovery line before another alert can fire.
 */
public final class ThermalAlerts {
    private ThermalAlerts() {}

    public static final String CHANNEL_ID = "sensolab_thermal";
    public static final int    NOTIFICATION_ID = 1001;

    /** CPU °C at which we notify. Conservative — sustained 80°C is the upper-mid of normal under load. */
    public static final float TRIP_C     = 80f;
    /** Must cool to here before the next alert can fire. */
    public static final float RECOVER_C  = 65f;

    private static boolean tripped = false;

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.thermal_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(ctx.getString(R.string.thermal_channel_desc));
        nm.createNotificationChannel(ch);
    }

    public static void checkAndNotify(Context ctx, float cpuCelsius) {
        if (cpuCelsius <= 0) return; // unknown
        if (!AppPrefs.isThermalAlertsEnabled(ctx)) return; // Y5: user silenced
        if (!tripped && cpuCelsius >= TRIP_C) {
            tripped = true;
            postAlert(ctx, cpuCelsius);
        } else if (tripped && cpuCelsius <= RECOVER_C) {
            tripped = false; // re-armed
        }
    }

    private static void postAlert(Context ctx, float cpuCelsius) {
        ensureChannel(ctx);
        // API 33+: POST_NOTIFICATIONS is runtime; silently no-op if not granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx,
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        float disp = AppPrefs.temp(cpuCelsius, ctx);
        String unit = AppPrefs.tempUnit(ctx);

        // Y5: "Don't show again" action
        android.content.Intent silenceIntent = new android.content.Intent(ThermalSilenceReceiver.ACTION)
                .setPackage(ctx.getPackageName());
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE;
        android.app.PendingIntent silencePi = android.app.PendingIntent.getBroadcast(
                ctx, 0, silenceIntent, flags);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_thermal_alert) // X3: monochrome thermometer
                .setContentTitle(ctx.getString(R.string.thermal_alert_title))
                .setContentText(ctx.getString(R.string.thermal_alert_body,
                        String.format(java.util.Locale.US, "%.1f%s", disp, unit)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .addAction(0, ctx.getString(R.string.thermal_alert_silence), silencePi) // Y5
                .build();

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n);
        } catch (SecurityException ignored) {
            // race with permission revoke — silently drop
        }
    }
}
