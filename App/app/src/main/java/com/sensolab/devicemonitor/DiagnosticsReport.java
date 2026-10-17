package com.sensolab.devicemonitor;

import android.content.Context;
import android.os.Build;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Plain-text device-check report for sharing (e.g. with a buyer or seller). */
public final class DiagnosticsReport {
    private DiagnosticsReport() {}

    public static String build(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(ctx.getString(R.string.diag_report_title)).append("\n");
        long last = DiagnosticsStore.lastRunMs(ctx);
        if (last > 0) {
            sb.append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(last))).append("\n");
        }
        sb.append(cap(Build.MANUFACTURER)).append(" ").append(Build.MODEL)
          .append(" · Android ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");

        int pass = 0, fail = 0, skipped = 0, notRun = 0;
        for (DiagTest t : DiagTest.values()) {
            DiagnosticsStore.Result r = DiagnosticsStore.result(ctx, t);
            switch (r) {
                case PASS:    pass++; break;
                case FAIL:    fail++; break;
                case SKIPPED: skipped++; break;
                default:      notRun++; break;
            }
            sb.append(symbol(r)).append(" ").append(ctx.getString(t.title))
              .append(" — ").append(ctx.getString(r.label)).append("\n");
            String note = DiagnosticsStore.note(ctx, t);
            if (!note.isEmpty()) sb.append("    ").append(note.replace("\n", "\n    ")).append("\n");
        }
        sb.append("\n").append(ctx.getString(R.string.diag_summary, pass, fail, skipped, notRun)).append("\n\n");
        sb.append(ctx.getString(R.string.diag_report_footer, BuildConfig.VERSION_NAME));
        return sb.toString();
    }

    public static String batteryNote(Context ctx, BatteryInfo b) {
        StringBuilder sb = new StringBuilder(ctx.getString(b.healthLabel()));
        if (b.cycleCount >= 0) sb.append(" · ").append(ctx.getString(R.string.hw_batt_cycles_fmt, b.cycleCount));
        int est = b.estimatedFullCapacityMah();
        if (est > 0) sb.append(" · ≈").append(est).append(" mAh");
        if (!Float.isNaN(b.tempC)) sb.append(String.format(Locale.US, " · %.1f°C", b.tempC));
        return sb.toString();
    }

    private static String symbol(DiagnosticsStore.Result r) {
        switch (r) {
            case PASS:    return "✅";
            case FAIL:    return "❌";
            case SKIPPED: return "➖";
            default:      return "⬜";
        }
    }

    private static String cap(String s) {
        return (s == null || s.isEmpty()) ? "" : s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }
}
