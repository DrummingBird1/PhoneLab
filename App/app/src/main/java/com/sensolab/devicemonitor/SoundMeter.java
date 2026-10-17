package com.sensolab.devicemonitor;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.RequiresPermission;

/**
 * Microphone level meter, extracted from SensorsFragment. Reads PCM continuously on a
 * daemon thread (AudioRecord.read() blocks until a buffer is ready — natural pacing, no
 * sleeps) and posts a level to the main thread at most every {@link #POST_INTERVAL_NS}.
 *
 * <p>The dB figure is relative to the mic's full scale, offset to a rough SPL-like range —
 * phones' mics are not calibrated, so the UI labels it as an estimate.
 */
public final class SoundMeter {

    public interface Listener {
        /** Main thread. */
        void onLevel(double db);
        /** Main thread. */
        void onError(String message);
    }

    private static final int SAMPLE_RATE = 44100;
    private static final long POST_INTERVAL_NS = 200_000_000L; // ~5 Hz UI updates

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile boolean running = false;
    private Thread thread;

    public SoundMeter(Listener listener) { this.listener = listener; }

    public boolean isRunning() { return running; }

    /** Caller must have checked RECORD_AUDIO. No-op if already running. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public void start() {
        if (running) return;
        final int bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufSize == AudioRecord.ERROR_BAD_VALUE || bufSize <= 0) return;

        final AudioRecord rec;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 4);
        } catch (Exception e) {
            listener.onError(String.valueOf(e.getMessage()));
            return;
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            rec.release();
            return;
        }
        running = true;
        thread = new Thread(() -> {
            try {
                rec.startRecording();
                short[] buf = new short[bufSize];
                long lastPostNs = 0L;
                while (running && !Thread.interrupted()) {
                    int read = rec.read(buf, 0, buf.length);
                    if (read <= 0) continue;
                    long now = System.nanoTime();
                    if (now - lastPostNs < POST_INTERVAL_NS) continue;
                    lastPostNs = now;
                    final double db = dbFromSamples(buf, read);
                    main.post(() -> { if (running) listener.onLevel(db); });
                }
            } catch (Exception e) {
                final String msg = String.valueOf(e.getMessage());
                main.post(() -> listener.onError(msg));
            } finally {
                try { rec.stop(); } catch (Exception ignored) {}
                rec.release();
            }
        }, "PhoneLab-Mic");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) { thread.interrupt(); thread = null; }
        main.removeCallbacksAndMessages(null);
    }

    /** RMS of a PCM-16 buffer mapped to an approximate 30–120 dB display range. */
    static double dbFromSamples(short[] buf, int count) {
        if (count <= 0) return 30.0;
        long sum = 0;
        for (int i = 0; i < count; i++) sum += (long) buf[i] * buf[i];
        double rms = Math.sqrt((double) sum / count);
        double db = rms > 1 ? 20 * Math.log10(rms / 32768.0) + 90.0 : 30.0;
        return Math.max(30, Math.min(120, db));
    }
}
