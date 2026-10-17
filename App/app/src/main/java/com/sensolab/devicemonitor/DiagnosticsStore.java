package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/** Persists the latest result (+ optional note) of each {@link DiagTest} on-device. */
public final class DiagnosticsStore {
    private DiagnosticsStore() {}

    public enum Result {
        NOT_RUN(R.string.diag_result_not_run, R.drawable.ic_result_pending),
        PASS(R.string.diag_result_pass, R.drawable.ic_result_pass),
        FAIL(R.string.diag_result_fail, R.drawable.ic_result_fail),
        SKIPPED(R.string.diag_result_skipped, R.drawable.ic_result_skipped);

        @StringRes public final int label;
        @DrawableRes public final int icon;
        Result(int label, int icon) { this.label = label; this.icon = icon; }
    }

    private static final String PREFS = "phonelab_diagnostics";
    private static final String KEY_TIME = "last_run_ms";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context ctx, DiagTest test, Result result, @Nullable String note) {
        prefs(ctx).edit()
                .putString("r_" + test.name(), result.name())
                .putString("n_" + test.name(), note == null ? "" : note)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .apply();
    }

    public static Result result(Context ctx, DiagTest test) {
        String r = prefs(ctx).getString("r_" + test.name(), Result.NOT_RUN.name());
        try { return Result.valueOf(r); } catch (IllegalArgumentException e) { return Result.NOT_RUN; }
    }

    public static String note(Context ctx, DiagTest test) {
        return prefs(ctx).getString("n_" + test.name(), "");
    }

    public static long lastRunMs(Context ctx) {
        return prefs(ctx).getLong(KEY_TIME, 0L);
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }
}
