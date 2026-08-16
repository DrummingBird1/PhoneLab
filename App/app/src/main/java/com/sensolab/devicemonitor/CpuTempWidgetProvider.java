package com.sensolab.devicemonitor;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.Locale;

/**
 * Home-screen widget showing live CPU temperature. Updates piggyback on
 * {@link ThermalCheckWorker}'s existing periodic tick (see its doWork()) rather than
 * scheduling a second independent timer — {@code updatePeriodMillis} in widget_info.xml
 * is left at the platform's ~30-min floor as a slow fallback; the worker's 15-min tick
 * is the real update path. A tap on the widget also triggers an immediate manual refresh.
 */
public class CpuTempWidgetProvider extends AppWidgetProvider {

    static final String ACTION_REFRESH = "com.sensolab.devicemonitor.WIDGET_REFRESH";

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateWidget(ctx, mgr, id);
        // First widget placed — make sure the periodic tick that refreshes it is scheduled
        // even if thermal alerts happen to be off.
        ThermalCheckWorker.reschedule(ctx);
    }

    @Override public void onDeleted(Context ctx, int[] ids) {
        // Last widget removed (and alerts off) — stop the now-unneeded periodic tick.
        ThermalCheckWorker.reschedule(ctx);
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, CpuTempWidgetProvider.class));
            for (int id : ids) updateWidget(ctx, mgr, id);
        }
    }

    static void updateWidget(Context ctx, AppWidgetManager mgr, int widgetId) {
        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.widget_cpu_temp);
        float cpu = ThermalZones.cpu();
        if (cpu > 0) {
            float disp = AppPrefs.temp(cpu, ctx);
            String unit = AppPrefs.tempUnit(ctx);
            views.setTextViewText(R.id.widget_temp_value, String.format(Locale.US, "%.0f%s", disp, unit));
        } else {
            views.setTextViewText(R.id.widget_temp_value, "--");
        }
        Intent refreshIntent = new Intent(ctx, CpuTempWidgetProvider.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);
        mgr.updateAppWidget(widgetId, views);
    }
}
