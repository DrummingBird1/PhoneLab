package com.sensolab.devicemonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import androidx.core.app.NotificationManagerCompat;

/**
 * Y5: Handles the "Don't show again" action button on the thermal-alert notification.
 * Sets {@link AppPrefs#setThermalAlertsEnabled} to false and clears the visible alert.
 * User can re-enable from the About tab toggle.
 */
public class ThermalSilenceReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.sensolab.devicemonitor.SILENCE_THERMAL";

    @Override public void onReceive(Context ctx, Intent intent) {
        AppPrefs.setThermalAlertsEnabled(ctx, false);
        NotificationManagerCompat.from(ctx).cancel(ThermalAlerts.NOTIFICATION_ID);
        try {
            Toast.makeText(ctx, R.string.thermal_alert_silenced, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }
}
