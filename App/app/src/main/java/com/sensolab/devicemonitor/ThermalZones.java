package com.sensolab.devicemonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a device's thermal zones directly from /sys/class/thermal/thermal_zone*, matching
 * by keyword against each zone's "type" file. OEMs name zones differently ("soc_thermal",
 * "mtktscpu", "tsens_tz_sensor0", ...) — when a zone reports N/A on a specific device, the
 * fix is almost always adding another keyword to the right call, not changing this logic.
 *
 * Extracted out of SystemFragment so it can also be reused by the background thermal-check
 * worker and the Quick Settings Tile / widget, none of which should duplicate this matching.
 */
public final class ThermalZones {
    private ThermalZones() {}

    /**
     * Average temperature (°C) across all thermal zones whose "type" file contains any of
     * the given keywords (case-insensitive substring match). Returns -1 if no matching zone
     * is found or /sys/class/thermal is unavailable.
     */
    public static float zone(String... keywords) {
        File dir = new File("/sys/class/thermal");
        if (!dir.exists()) return -1;
        File[] files = dir.listFiles();
        if (files == null) return -1;
        List<Float> hits = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().startsWith("thermal_zone")) continue;
            try {
                String type = readLine(new File(f, "type"));
                if (typeMatches(type, keywords)) {
                    float raw = Float.parseFloat(readLine(new File(f, "temp")).trim());
                    float c = normalize(raw);
                    if (isPlausible(c)) hits.add(c);
                }
            } catch (Exception ignored) {}
        }
        if (hits.isEmpty()) return -1;
        float sum = 0;
        for (float f : hits) sum += f;
        return sum / hits.size();
    }

    /** CPU-zone keyword list, shared by the System tab's live poll, the background thermal
     *  worker, and the Quick Settings Tile / widget. */
    public static float cpu() {
        return zone("cpu", "cpu0", "soc_thermal", "mtktscpu", "cpu_thermal", "tsens_tz_sensor0", "cpu_0");
    }

    // ── Pure logic (no file I/O) — kept separate so it's unit-testable without a real
    //    /sys/class/thermal, same rationale as AppPrefs's Context-free converters. ──

    /** Case-insensitive substring match of any keyword against a zone's "type" file content. */
    static boolean typeMatches(String type, String... keywords) {
        String lower = type.toLowerCase(Locale.US);
        for (String k : keywords) {
            if (lower.contains(k.toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    /** Some OEMs report milli-degrees (e.g. 45000 = 45.0°C) instead of whole degrees. */
    static float normalize(float raw) {
        return raw > 1000 ? raw / 1000f : raw;
    }

    /** Reject obviously-bogus readings (0 = unread sysfs node, 200+ = implausible for a phone). */
    static boolean isPlausible(float celsius) {
        return celsius > 0 && celsius < 200;
    }

    private static String readLine(File f) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String s = br.readLine();
            return s != null ? s.trim() : "";
        }
    }
}
