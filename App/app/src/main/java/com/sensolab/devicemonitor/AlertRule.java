package com.sensolab.devicemonitor;

import android.content.Context;
import androidx.annotation.StringRes;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A user-defined "notify me when X goes above/below Y" rule, evaluated by the periodic
 * background worker. Thresholds are stored in canonical units (°C, %, hPa, lx) and only
 * converted for display / input.
 */
public final class AlertRule {

    public enum Metric {
        CPU_TEMP(R.string.alert_metric_cpu_temp, true, 3f),
        SKIN_TEMP(R.string.alert_metric_skin_temp, true, 2f),
        BATTERY_TEMP(R.string.alert_metric_battery_temp, true, 2f),
        BATTERY_LEVEL(R.string.alert_metric_battery_level, false, 2f),
        AMBIENT_TEMP(R.string.alert_metric_ambient_temp, true, 1f),
        HUMIDITY(R.string.alert_metric_humidity, false, 5f),
        PRESSURE(R.string.alert_metric_pressure, false, 2f),
        LIGHT(R.string.alert_metric_light, false, 0f);

        @StringRes public final int label;
        public final boolean isTemperature;
        /** Hysteresis: how far back past the threshold the value must go to re-arm. */
        final float rearmMargin;

        Metric(int label, boolean isTemperature, float rearmMargin) {
            this.label = label;
            this.isTemperature = isTemperature;
            this.rearmMargin = rearmMargin;
        }

        /** Light spans orders of magnitude, so its margin is relative (10 %). */
        float marginFor(float threshold) {
            return this == LIGHT ? Math.max(5f, Math.abs(threshold) * 0.1f) : rearmMargin;
        }
    }

    public final long id;
    public final Metric metric;
    public final boolean above;
    public final float threshold;
    public final boolean enabled;

    public AlertRule(long id, Metric metric, boolean above, float threshold, boolean enabled) {
        this.id = id;
        this.metric = metric;
        this.above = above;
        this.threshold = threshold;
        this.enabled = enabled;
    }

    public AlertRule withEnabled(boolean on) { return new AlertRule(id, metric, above, threshold, on); }

    /** User-unit value + unit, e.g. "45 °C" / "113 °F" / "29.9 inHg". */
    public static String formatValue(Context ctx, Metric m, float canonical) {
        switch (m) {
            case BATTERY_LEVEL:
            case HUMIDITY: return String.format(Locale.US, "%.0f%%", canonical);
            case PRESSURE: return String.format(Locale.US, AppPrefs.isImperial(ctx) ? "%.2f %s" : "%.0f %s",
                    AppPrefs.pressure(canonical, ctx), AppPrefs.pressureUnit(ctx));
            case LIGHT:    return String.format(Locale.US, "%.0f lx", canonical);
            default:       return String.format(Locale.US, "%.0f %s", AppPrefs.temp(canonical, ctx), AppPrefs.tempUnit(ctx));
        }
    }

    /** Converts a value typed in the user's unit system back to canonical units. */
    public static float toCanonical(Context ctx, Metric m, float userValue) {
        if (!AppPrefs.isImperial(ctx)) return userValue;
        if (m.isTemperature) return (userValue - 32f) * 5f / 9f;
        if (m == Metric.PRESSURE) return userValue / 0.02953f;
        return userValue;
    }

    /** Unit shown next to the threshold input field. */
    public static String inputUnit(Context ctx, Metric m) {
        switch (m) {
            case BATTERY_LEVEL:
            case HUMIDITY: return "%";
            case PRESSURE: return AppPrefs.pressureUnit(ctx);
            case LIGHT:    return "lx";
            default:       return AppPrefs.tempUnit(ctx);
        }
    }

    public String describe(Context ctx) {
        return ctx.getString(above ? R.string.alert_rule_above : R.string.alert_rule_below,
                ctx.getString(metric.label), formatValue(ctx, metric, threshold));
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("metric", metric.name())
                .put("above", above)
                .put("threshold", (double) threshold)
                .put("enabled", enabled);
    }

    static AlertRule fromJson(JSONObject o) throws JSONException {
        return new AlertRule(o.getLong("id"), Metric.valueOf(o.getString("metric")),
                o.getBoolean("above"), (float) o.getDouble("threshold"), o.optBoolean("enabled", true));
    }
}
