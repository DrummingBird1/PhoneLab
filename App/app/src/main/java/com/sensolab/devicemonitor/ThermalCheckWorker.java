package com.sensolab.devicemonitor;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

/**
 * Periodic background check so thermal alerts fire even when the app isn't open —
 * previously {@link ThermalAlerts#checkAndNotify} only ran from SystemFragment's live
 * poll, so it silently did nothing unless the System tab happened to be on screen.
 *
 * A full Foreground Service (permanent notification) was considered but rejected as
 * overkill for "check the temperature occasionally" — WorkManager's periodic work
 * (15-minute platform minimum) matches this app's existing battery-conscious design
 * without a persistent notification. Scheduling is idempotent: {@link #reschedule}
 * is safe to call redundantly (app launch, toggle changes, widget add/remove) since
 * {@link ExistingPeriodicWorkPolicy#KEEP} no-ops if already scheduled.
 *
 * Batch 6: also drives the CPU-temp widget's refresh (see doWork()) — one periodic
 * tick serves both consumers instead of each scheduling its own timer. Because of that,
 * scheduling is needed whenever EITHER thermal alerts are on OR a widget is placed —
 * a user who only wants the widget (alerts off) still needs the tick to run.
 */
public class ThermalCheckWorker extends Worker {

    private static final String UNIQUE_WORK_NAME = "sensolab_thermal_check";
    private static final long INTERVAL_MINUTES = 15L; // WorkManager's own enforced minimum

    public ThermalCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        float cpu = ThermalZones.cpu();
        ThermalAlerts.checkAndNotify(ctx, cpu);
        // Batch 6: piggyback the widget refresh on this same tick.
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, CpuTempWidgetProvider.class));
        for (int id : ids) CpuTempWidgetProvider.updateWidget(ctx, mgr, id);
        return Result.success();
    }

    /** Enqueues or cancels the periodic check to match current demand (thermal alerts
     *  toggle and/or whether any CPU-temp widget is currently placed). */
    public static void reschedule(Context ctx) {
        WorkManager wm = WorkManager.getInstance(ctx);
        if (AppPrefs.isThermalAlertsEnabled(ctx) || hasActiveWidgets(ctx)) {
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    ThermalCheckWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES)
                    .build();
            wm.enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
        } else {
            wm.cancelUniqueWork(UNIQUE_WORK_NAME);
        }
    }

    private static boolean hasActiveWidgets(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        return mgr.getAppWidgetIds(new ComponentName(ctx, CpuTempWidgetProvider.class)).length > 0;
    }
}
