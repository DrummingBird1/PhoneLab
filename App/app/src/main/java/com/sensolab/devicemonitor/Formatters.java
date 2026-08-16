package com.sensolab.devicemonitor;

import java.util.Locale;

/**
 * Pure formatting / data helpers with no Android dependencies, so they can be
 * exercised by plain JUnit tests (no Robolectric needed). Logic that used to
 * live as private methods inside Fragments / AppPrefs was hoisted here.
 */
public final class Formatters {
    private Formatters() {}

    /** Elapsed seconds → "MM:SS", or "H:MM:SS" once past an hour. */
    public static String duration(long elapsedSec) {
        if (elapsedSec < 0) elapsedSec = 0;
        long h = elapsedSec / 3600;
        long m = (elapsedSec % 3600) / 60;
        long s = elapsedSec % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s);
    }

    /**
     * Escape one CSV field: wrap in double quotes and double any internal quote
     * iff it contains a comma, quote, CR or LF. RFC-4180 style.
     */
    public static String csvField(String s) {
        if (s == null) return "";
        boolean needsQuoting = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                            || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needsQuoting) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /**
     * Prepend {@code row} to a newline-separated history string, keeping at most
     * {@code max} rows (newest first). Returns the new history string.
     */
    public static String historyPrepend(String existing, String row, int max) {
        if (existing == null || existing.isEmpty()) return row;
        if (max <= 1) return row;
        String[] rows = existing.split("\n");
        StringBuilder sb = new StringBuilder(row);
        for (int i = 0; i < Math.min(rows.length, max - 1); i++) {
            sb.append('\n').append(rows[i]);
        }
        return sb.toString();
    }
}
