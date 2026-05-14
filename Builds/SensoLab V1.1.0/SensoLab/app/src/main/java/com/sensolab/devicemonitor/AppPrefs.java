package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREFS = "sensolab_prefs";

    public static final String KEY_THEME        = "theme";
    public static final String KEY_DISPLAY_MODE = "display_mode";
    public static final String KEY_UNIT_SYSTEM  = "unit_system";

    public static final String MODE_TEXTUAL   = "textual";
    public static final String MODE_VISUAL    = "visual";
    public static final String THEME_DARK     = "dark";
    public static final String THEME_LIGHT    = "light";
    public static final String UNITS_METRIC   = "metric";
    public static final String UNITS_IMPERIAL = "imperial";

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getDisplayMode(Context ctx) { return get(ctx).getString(KEY_DISPLAY_MODE, MODE_TEXTUAL); }
    public static String getTheme(Context ctx)       { return get(ctx).getString(KEY_THEME, THEME_DARK); }
    public static String getUnitSystem(Context ctx)  { return get(ctx).getString(KEY_UNIT_SYSTEM, UNITS_METRIC); }

    public static boolean isVisual(Context ctx)   { return MODE_VISUAL.equals(getDisplayMode(ctx)); }
    public static boolean isDark(Context ctx)     { return THEME_DARK.equals(getTheme(ctx)); }
    public static boolean isImperial(Context ctx) { return UNITS_IMPERIAL.equals(getUnitSystem(ctx)); }

    // ── Unit conversions ──────────────────────────────────────────────────
    public static float  temp(float celsius, Context ctx)    { return isImperial(ctx) ? celsius * 9f / 5f + 32f : celsius; }
    public static String tempUnit(Context ctx)               { return isImperial(ctx) ? "°F" : "°C"; }
    public static float  speed(float mps, Context ctx)       { return isImperial(ctx) ? mps * 2.23694f : mps * 3.6f; }
    public static String speedUnit(Context ctx)              { return isImperial(ctx) ? "mph" : "km/h"; }
    public static float  altitude(float metres, Context ctx) { return isImperial(ctx) ? metres * 3.28084f : metres; }
    public static String altUnit(Context ctx)                { return isImperial(ctx) ? "ft" : "m"; }
    public static float  pressure(float hpa, Context ctx)    { return isImperial(ctx) ? hpa * 0.02953f : hpa; }
    public static String pressureUnit(Context ctx)           { return isImperial(ctx) ? "inHg" : "hPa"; }
    public static float  distance(float metres, Context ctx) { return isImperial(ctx) ? metres * 3.28084f : metres; }
    public static String distUnit(Context ctx)               { return isImperial(ctx) ? "ft" : "m"; }
}
