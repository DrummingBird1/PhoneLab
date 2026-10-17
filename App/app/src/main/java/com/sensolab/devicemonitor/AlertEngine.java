package com.sensolab.devicemonitor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.WorkerThread;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Reads the metrics that enabled {@link AlertRule}s need and posts notifications.
 * Runs on the background worker thread: environmental sensors are sampled once (short
 * one-shot registration), thermal and battery values come from sysfs / sticky broadcast.
 */
public final class AlertEngine {
    private AlertEngine() {}

    private static final long SENSOR_TIMEOUT_MS = 2500L;

    @WorkerThread
    public static void checkAll(Context ctx) {
        List<AlertRule> rules = AlertRules.load(ctx);
        if (rules.isEmpty()) return;
        Map<AlertRule.Metric, Float> cache = new EnumMap<>(AlertRule.Metric.class);
        BatteryInfo battery = null;
        for (AlertRule rule : rules) {
            if (!rule.enabled) continue;
            Float value = cache.get(rule.metric);
            if (value == null) {
                if (battery == null && (rule.metric == AlertRule.Metric.BATTERY_LEVEL
                        || rule.metric == AlertRule.Metric.BATTERY_TEMP)) {
                    battery = BatteryInfo.read(ctx);
                }
                value = read(ctx, rule.metric, battery);
                cache.put(rule.metric, value);
            }
            AlertEvaluator.Decision d = AlertEvaluator.evaluate(rule, value, AlertRules.isTripped(ctx, rule.id));
            AlertRules.setTripped(ctx, rule.id, d.tripped);
            if (d.notify) AlertNotifier.notify(ctx, rule, value);
        }
    }

    private static float read(Context ctx, AlertRule.Metric m, BatteryInfo battery) {
        switch (m) {
            case CPU_TEMP:      return positiveOrNaN(ThermalZones.cpu());
            case SKIN_TEMP:     return positiveOrNaN(ThermalZones.skin());
            case BATTERY_TEMP:  return battery == null ? Float.NaN : battery.tempC;
            case BATTERY_LEVEL: return battery == null || battery.levelPct < 0 ? Float.NaN : battery.levelPct;
            case AMBIENT_TEMP:  return sampleSensor(ctx, Sensor.TYPE_AMBIENT_TEMPERATURE);
            case HUMIDITY:      return sampleSensor(ctx, Sensor.TYPE_RELATIVE_HUMIDITY);
            case PRESSURE:      return sampleSensor(ctx, Sensor.TYPE_PRESSURE);
            case LIGHT:         return sampleSensor(ctx, Sensor.TYPE_LIGHT);
            default:            return Float.NaN;
        }
    }

    private static float positiveOrNaN(float v) { return v > 0 ? v : Float.NaN; }

    /** Registers for one event on a private looper thread and waits briefly for it. */
    @WorkerThread
    static float sampleSensor(Context ctx, int type) {
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sm == null ? null : sm.getDefaultSensor(type);
        if (s == null) return Float.NaN;
        final float[] out = {Float.NaN};
        final CountDownLatch latch = new CountDownLatch(1);
        HandlerThread thread = new HandlerThread("PhoneLab-AlertSample");
        thread.start();
        SensorEventListener l = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent ev) {
                if (ev.values != null && ev.values.length > 0 && latch.getCount() > 0) {
                    out[0] = ev.values[0];
                    latch.countDown();
                }
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        try {
            sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL, new Handler(thread.getLooper()));
            latch.await(SENSOR_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sm.unregisterListener(l);
            thread.quitSafely();
        }
        return out[0];
    }
}
