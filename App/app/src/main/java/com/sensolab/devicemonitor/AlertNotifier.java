package com.sensolab.devicemonitor;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Notifications for user-defined sensor alert rules (separate channel from thermal alerts). */
public final class AlertNotifier {
    private AlertNotifier() {}

    public static final String CHANNEL_ID = "sensolab_sensor_alerts";
    private static final int ID_BASE = 3000;

    public static void ensureChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                ctx.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(ctx.getString(R.string.alert_channel_desc));
        nm.createNotificationChannel(ch);
    }

    public static void notify(Context ctx, AlertRule rule, float value) {
        ensureChannel(ctx);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return; // user declined notifications — nothing to show
        }
        Intent open = new Intent(ctx, MainActivity.class)
                .putExtra(MainActivity.EXTRA_START_TAB, MainActivity.TAB_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, (int) rule.id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(ctx.getString(R.string.alert_notif_title, ctx.getString(rule.metric.label)))
                .setContentText(ctx.getString(R.string.alert_notif_body,
                        AlertRule.formatValue(ctx, rule.metric, value), rule.describe(ctx)))
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(ctx).notify(ID_BASE + (int) (rule.id % 1000), b.build());
        } catch (SecurityException ignored) {
            // permission revoked between the check and the post
        }
    }
}
