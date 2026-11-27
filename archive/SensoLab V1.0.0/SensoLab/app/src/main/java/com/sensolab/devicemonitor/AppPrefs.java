package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    private static final String PREFS = "sensolab_prefs";
    public static final String KEY_THEME        = "theme";
    public static final String KEY_DISPLAY_MODE = "display_mode";
    public static final String MODE_TEXTUAL     = "textual";
    public static final String MODE_VISUAL      = "visual";
    public static final String THEME_DARK       = "dark";
    public static final String THEME_LIGHT      = "light";

    public static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    public static String getDisplayMode(Context ctx) {
        return get(ctx).getString(KEY_DISPLAY_MODE, MODE_TEXTUAL);
    }
    public static String getTheme(Context ctx) {
        return get(ctx).getString(KEY_THEME, THEME_DARK);
    }
    public static boolean isVisual(Context ctx) {
        return MODE_VISUAL.equals(getDisplayMode(ctx));
    }
    public static boolean isDark(Context ctx) {
        return THEME_DARK.equals(getTheme(ctx));
    }
}
