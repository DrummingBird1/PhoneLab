package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Static facade over a single SharedPreferences file.
 *
 * P5: unit-system flag is read for every sensor sample (60+ Hz when on the
 * Sensors tab), so it is cached in a volatile field. Caller code should not
 * write to SharedPreferences directly — go through {@link #setUnitSystem},
 * {@link #setTheme}, {@link #setDisplayMode} so the cache stays consistent.
 */
public class AppPrefs {
    private static final String PREFS = "sensolab_prefs";

    public static final String KEY_THEME        = "theme";
    public static final String KEY_DISPLAY_MODE = "display_mode";
    public static final String KEY_UNIT_SYSTEM  = "unit_system";

    // R4: persisted session counters (survive app kill)
    public static final String KEY_STEP_DET_COUNT = "step_det_count";
    public static final String KEY_TILT_COUNT     = "tilt_count";
    public static final String KEY_SIG_MOT_COUNT  = "sig_mot_count";
    public static final String KEY_STEP_CNT_BASE  = "step_cnt_base";

    // Y2: benchmark history — newline-separated "epochMs|score|rating" rows, max 5
    public static final String KEY_BENCH_HISTORY  = "bench_history";
    public static final int    BENCH_HISTORY_MAX  = 5;
    // Y5: thermal alerts enabled toggle
    public static final String KEY_THERMAL_ALERTS = "thermal_alerts_enabled";

    public static final String MODE_TEXTUAL   = "textual";
    public static final String MODE_VISUAL    = "visual";
    public static final String THEME_DARK     = "dark";
    public static final String THEME_LIGHT    = "light";
    public static final String THEME_SYSTEM   = "system"; // N1
    public static final String UNITS_METRIC   = "metric";
    public static final String UNITS_IMPERIAL = "imperial";

    // ── Cache (P5) — read on hot path, invalidated on write ───────────────
    private static volatile Boolean imperialCache = null;

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getDisplayMode(Context ctx) { return get(ctx).getString(KEY_DISPLAY_MODE, MODE_TEXTUAL); }
    public static String getTheme(Context ctx)       { return get(ctx).getString(KEY_THEME, THEME_DARK); }
    public static String getUnitSystem(Context ctx)  { return get(ctx).getString(KEY_UNIT_SYSTEM, UNITS_METRIC); }

    public static boolean isVisual(Context ctx)   { return MODE_VISUAL.equals(getDisplayMode(ctx)); }
    public static boolean isDark(Context ctx)     { return THEME_DARK.equals(getTheme(ctx)); }
    public static boolean isFollowSystem(Context ctx) { return THEME_SYSTEM.equals(getTheme(ctx)); }

    /** N1: AppCompatDelegate night-mode constant for the current theme pref. */
    public static int getNightMode(Context ctx) {
        switch (getTheme(ctx)) {
            case THEME_LIGHT:  return AppCompatDelegate.MODE_NIGHT_NO;
            case THEME_SYSTEM: return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            case THEME_DARK:
            default:           return AppCompatDelegate.MODE_NIGHT_YES;
        }
    }

    /** Next theme in the cycle: dark → light → system → dark. */
    public static String nextTheme(Context ctx) {
        switch (getTheme(ctx)) {
            case THEME_DARK:   return THEME_LIGHT;
            case THEME_LIGHT:  return THEME_SYSTEM;
            default:           return THEME_DARK;
        }
    }

    /** Hot-path read — cached to avoid SharedPreferences hits per sensor sample. */
    public static boolean isImperial(Context ctx) {
        Boolean c = imperialCache;
        if (c != null) return c;
        boolean v = UNITS_IMPERIAL.equals(getUnitSystem(ctx));
        imperialCache = v;
        return v;
    }

    // ── Setters (keep cache consistent) ───────────────────────────────────
    public static void setUnitSystem(Context ctx, String value) {
        get(ctx).edit().putString(KEY_UNIT_SYSTEM, value).apply();
        imperialCache = UNITS_IMPERIAL.equals(value);
    }
    public static void setTheme(Context ctx, String value) {
        get(ctx).edit().putString(KEY_THEME, value).apply();
    }
    public static void setDisplayMode(Context ctx, String value) {
        get(ctx).edit().putString(KEY_DISPLAY_MODE, value).apply();
    }

    // Y5: thermal alerts can be silenced from notification ("Don't show again")
    public static boolean isThermalAlertsEnabled(Context ctx) {
        return get(ctx).getBoolean(KEY_THERMAL_ALERTS, true);
    }
    public static void setThermalAlertsEnabled(Context ctx, boolean enabled) {
        get(ctx).edit().putBoolean(KEY_THERMAL_ALERTS, enabled).apply();
    }

    // Y2: benchmark history (newest first, capped at BENCH_HISTORY_MAX rows)
    public static void appendBenchHistory(Context ctx, long score, String rating) {
        String existing = get(ctx).getString(KEY_BENCH_HISTORY, "");
        String row = System.currentTimeMillis() + "|" + score + "|" + rating;
        String combined = Formatters.historyPrepend(existing, row, BENCH_HISTORY_MAX);
        get(ctx).edit().putString(KEY_BENCH_HISTORY, combined).apply();
    }
    public static String getBenchHistory(Context ctx) {
        return get(ctx).getString(KEY_BENCH_HISTORY, "");
    }

    // ── Pure converters (no Context) — easily unit-testable ──────────────
    public static float cToF(float c)        { return c * 9f / 5f + 32f; }
    public static float mpsToKmh(float mps)  { return mps * 3.6f; }
    public static float mpsToMph(float mps)  { return mps * 2.23694f; }
    public static float mToFt(float m)       { return m * 3.28084f; }
    public static float hpaToInHg(float hpa) { return hpa * 0.02953f; }

    // ── Unit conversions (Context-aware wrappers) ────────────────────────
    public static float  temp(float celsius, Context ctx)    { return isImperial(ctx) ? cToF(celsius) : celsius; }
    public static String tempUnit(Context ctx)               { return isImperial(ctx) ? "°F" : "°C"; }
    public static float  speed(float mps, Context ctx)       { return isImperial(ctx) ? mpsToMph(mps) : mpsToKmh(mps); }
    public static String speedUnit(Context ctx)              { return isImperial(ctx) ? "mph" : "km/h"; }
    public static float  altitude(float metres, Context ctx) { return isImperial(ctx) ? mToFt(metres) : metres; }
    public static String altUnit(Context ctx)                { return isImperial(ctx) ? "ft" : "m"; }
    public static float  pressure(float hpa, Context ctx)    { return isImperial(ctx) ? hpaToInHg(hpa) : hpa; }
    public static String pressureUnit(Context ctx)           { return isImperial(ctx) ? "inHg" : "hPa"; }
    public static float  distance(float metres, Context ctx) { return isImperial(ctx) ? mToFt(metres) : metres; }
    public static String distUnit(Context ctx)               { return isImperial(ctx) ? "ft" : "m"; }
}
