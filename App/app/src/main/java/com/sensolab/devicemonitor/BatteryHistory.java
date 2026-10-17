package com.sensolab.devicemonitor;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rolling 24 h battery log (level + temperature), one sample per background-worker tick
 * (~15 min → up to 96 rows). Stored as newline-separated "epochMs|level|tempDeciC" rows in
 * SharedPreferences — tiny, and nothing ever leaves the device.
 */
public final class BatteryHistory {
    private BatteryHistory() {}

    static final String KEY = "battery_history";
    static final int MAX_ROWS = 96;
    /** Skip a sample if the previous one is younger than this (app-open + worker overlap). */
    private static final long MIN_SPACING_MS = 10 * 60 * 1000L;

    public static final class Sample {
        public final long timeMs;
        public final int level;
        public final float tempC;
        Sample(long timeMs, int level, float tempC) { this.timeMs = timeMs; this.level = level; this.tempC = tempC; }
    }

    public static void record(Context ctx, BatteryInfo b) {
        if (b.levelPct < 0 || !AppPrefs.isBatteryHistoryEnabled(ctx)) return;
        String existing = AppPrefs.get(ctx).getString(KEY, "");
        List<Sample> rows = parse(existing);
        long now = System.currentTimeMillis();
        if (!rows.isEmpty() && now - rows.get(rows.size() - 1).timeMs < MIN_SPACING_MS) return;
        int deci = Float.isNaN(b.tempC) ? Integer.MIN_VALUE : Math.round(b.tempC * 10);
        String row = String.format(Locale.US, "%d|%d|%d", now, b.levelPct, deci);
        AppPrefs.get(ctx).edit().putString(KEY, append(existing, row, MAX_ROWS)).apply();
    }

    /** Oldest first. */
    public static List<Sample> load(Context ctx) {
        return parse(AppPrefs.get(ctx).getString(KEY, ""));
    }

    public static void clear(Context ctx) {
        AppPrefs.get(ctx).edit().remove(KEY).apply();
    }

    // ── Pure helpers (unit-testable) ─────────────────────────────────────

    static List<Sample> parse(String raw) {
        List<Sample> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            String[] p = line.split("\\|");
            if (p.length < 3) continue;
            try {
                int deci = Integer.parseInt(p[2]);
                out.add(new Sample(Long.parseLong(p[0]), Integer.parseInt(p[1]),
                        deci == Integer.MIN_VALUE ? Float.NaN : deci / 10f));
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    /** Appends a row and keeps only the newest {@code max} rows. */
    static String append(String existing, String row, int max) {
        List<String> lines = new ArrayList<>();
        if (existing != null && !existing.isEmpty()) {
            for (String l : existing.split("\n")) if (!l.isEmpty()) lines.add(l);
        }
        lines.add(row);
        int from = Math.max(0, lines.size() - max);
        return String.join("\n", lines.subList(from, lines.size()));
    }
}
