package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorEvent;
import android.net.Uri;
import android.os.Environment;
import androidx.core.content.FileProvider;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight CSV recorder for sensor events. Appends rows synchronously to a
 * buffered writer (the file lives in app-specific external Documents dir, so
 * no permission is needed on API 26+). Caller starts a session with
 * {@link #start(Context)} and ends with {@link #stopAndShare(Context)} which
 * also fires a chooser to share the file.
 *
 * <p>One {@code CsvRecorder} per session — caller creates a new instance to
 * start a fresh recording.
 */
public final class CsvRecorder {

    /** Row count is read on UI thread (button label) and written on sensor thread. */
    private final AtomicInteger count = new AtomicInteger();
    private final File out;
    private final BufferedWriter w;
    /** Map sensor type → human name (lazy) — keeps the CSV self-describing. */
    private final Map<Integer, String> nameCache = new HashMap<>();

    private CsvRecorder(File out, BufferedWriter w) {
        this.out = out;
        this.w = w;
    }

    public static CsvRecorder start(Context ctx) throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = new File(ctx.getCacheDir(), "PhoneLab_exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File file = new File(dir, "PhoneLab_Rec_" + ts + ".csv");
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        // X1: variable-width columns — some sensors emit 3 values, some 6 (uncalibrated)
        bw.write(ctx.getString(R.string.rec_csv_header));
        bw.newLine();
        return new CsvRecorder(file, bw);
    }

    /** Append a continuous-sensor event row. Safe to call from sensor threads. */
    public void append(SensorEvent ev) {
        if (ev == null || ev.values == null || ev.values.length == 0) return;
        String name = nameCache.get(ev.sensor.getType());
        if (name == null) {
            name = csvEscape(ev.sensor.getName());
            nameCache.put(ev.sensor.getType(), name);
        }
        writeRow(ev.timestamp, name, ev.values, ev.values.length);
    }

    /** X1: Append a trigger-sensor event (Significant Motion, Stationary, Motion Detect). */
    public void appendTrigger(String sensorName, long timestampNs) {
        writeRow(timestampNs, csvEscape(sensorName), null, 0);
    }

    /** X1: Append a GPS location row — uses lat,lon,alt as v1,v2,v3. */
    public void appendLocation(double lat, double lon, double alt, long timestampNs) {
        writeRow(timestampNs, "GPS",
                new float[]{(float)lat, (float)lon, (float)alt}, 3);
    }

    private void writeRow(long timestampNs, String name, float[] values, int n) {
        StringBuilder sb = new StringBuilder(80);
        sb.append(timestampNs).append(',').append(name);
        // X1: keep CSV rectangular — pad up to 6 value columns
        for (int i = 0; i < 6; i++) {
            sb.append(',');
            if (i < n && values != null) sb.append(values[i]);
        }
        try {
            synchronized (w) {
                w.write(sb.toString());
                w.newLine();
            }
            count.incrementAndGet();
        } catch (IOException ignored) {
            // Out of disk / closed mid-write — drop sample silently rather than crash recording.
        }
    }

    /** X5: Properly escape CSV field — delegates to the unit-tested Formatters.csvField. */
    private static String csvEscape(String s) {
        return Formatters.csvField(s);
    }

    public int sampleCount() { return count.get(); }

    public File file() { return out; }

    /**
     * Close the writer and keep the file on disk WITHOUT sharing. Use this when a
     * recording is ended implicitly (e.g. the fragment is paused / the tab is left),
     * so the user doesn't get an unexpected share sheet. Returns the final sample count.
     */
    public int stop() {
        int n = count.get();
        try {
            synchronized (w) { w.close(); }
        } catch (IOException ignored) {}
        return n;
    }

    /** Close the writer and launch a share chooser. Returns the final sample count. */
    public int stopAndShare(Context ctx) {
        int n = stop();

        try {
            Uri uri = FileProvider.getUriForFile(ctx,
                    ctx.getPackageName() + ".fileprovider", out);
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.rec_share_subject))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(share, ctx.getString(R.string.export_chooser));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(chooser);
        } catch (Exception ignored) {
            // File is still on disk — user can locate it manually
        }
        return n;
    }
}
