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
 * The app's single periodic background tick (WorkManager's 15-minute minimum), shared by
 * everything that needs to run while the app is closed:
 * <ul>
 *   <li>thermal alerts ({@link ThermalAlerts})</li>
 *   <li>user-defined sensor alert rules ({@link AlertEngine})</li>
 *   <li>the 24 h battery log ({@link BatteryHistory})</li>
 *   <li>the CPU-temperature home-screen widget</li>
 * </ul>
 * A foreground service was rejected as overkill for "check occasionally" — no permanent
 * notification. Class name kept as-is: WorkManager persists it for already-scheduled work.
 */
public class ThermalCheckWorker extends Worker {

    private static final String UNIQUE_WORK_NAME = "sensolab_thermal_check";
    private static final long INTERVAL_MINUTES = 15L; // WorkManager's enforced minimum

    public ThermalCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        ThermalAlerts.checkAndNotify(ctx, ThermalZones.cpu());
        AlertEngine.checkAll(ctx);
        BatteryHistory.record(ctx, BatteryInfo.read(ctx));
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, CpuTempWidgetProvider.class));
        for (int id : ids) CpuTempWidgetProvider.updateWidget(ctx, mgr, id);
        return Result.success();
    }

    /** Enqueues or cancels the periodic tick to match current demand. Idempotent. */
    public static void reschedule(Context ctx) {
        WorkManager wm = WorkManager.getInstance(ctx);
        boolean needed = AppPrefs.isThermalAlertsEnabled(ctx)
                || AppPrefs.isBatteryHistoryEnabled(ctx)
                || AlertRules.anyEnabled(ctx)
                || hasActiveWidgets(ctx);
        if (needed) {
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    ThermalCheckWorker.class, INTERVAL_MINUTES, TimeUnit.MINUTES).build();
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
