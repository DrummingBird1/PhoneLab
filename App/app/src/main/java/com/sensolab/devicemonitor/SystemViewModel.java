package com.sensolab.devicemonitor;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * System-tab state that must survive rotation / theme changes: the benchmark (previously
 * cancelled whenever the Fragment's view was destroyed) and the CPU-temperature history
 * behind the trend sparkline.
 */
public class SystemViewModel extends AndroidViewModel {

    private static final String TAG = "PhoneLab.System";

    // A4: named constants instead of magic numbers
    private static final long BENCH_INT_ITERATIONS   = 60_000_000L;
    private static final int  BENCH_FLOAT_ITERATIONS = 6_000_000;
    private static final int  BENCH_MEM_BYTES        = 16 * 1024 * 1024;
    private static final long BENCH_CORE_ITERATIONS  = 25_000_000L;

    /** Snapshot of the benchmark for the UI. */
    public static final class BenchState {
        public final boolean running;
        public final String text;
        /** True only for the state carrying a completed result (refresh history views). */
        public final boolean justFinished;
        BenchState(boolean running, String text, boolean justFinished) {
            this.running = running; this.text = text; this.justFinished = justFinished;
        }
    }

    private final MutableLiveData<BenchState> bench = new MutableLiveData<>();
    private ExecutorService executor;
    private volatile boolean benchCancelled = false;

    // Batch 7: CPU-temp ring buffer (the live card only shows the instantaneous value).
    private static final int CPU_HISTORY_SIZE = 30;
    private final float[] cpuTempHistory = new float[CPU_HISTORY_SIZE];
    private int cpuHistoryCount = 0, cpuHistoryIndex = 0;

    public SystemViewModel(@NonNull Application app) { super(app); }

    public LiveData<BenchState> bench() { return bench; }

    public boolean isBenchRunning() {
        BenchState s = bench.getValue();
        return s != null && s.running;
    }

    /** Starts the 4-phase benchmark, or cancels it if one is already running (U5). */
    public void toggleBenchmark() {
        if (isBenchRunning()) {
            benchCancelled = true;
            return;
        }
        benchCancelled = false;
        if (executor != null) executor.shutdown();
        executor = NamedThreads.singleThread("Bench"); // Z1
        bench.setValue(new BenchState(true, str(R.string.bench_phase1), false));
        executor.execute(this::runBenchmark);
    }

    private void runBenchmark() {
        long t = now();
        // D3: the accumulators must be "used" or the JIT deletes the loops and the
        // benchmark measures nothing — they're logged below in debug builds (N8).
        long s = 0;
        for (long i = 0; i < BENCH_INT_ITERATIONS; i++) {
            s ^= i * 2654435761L;
            if ((i & 0xFFFFFFL) == 0 && benchCancelled) { postCancelled(); return; }
        }
        long intT = now() - t;

        bench.postValue(new BenchState(true, str(R.string.bench_phase2), false));
        t = now();
        double d = 0;
        for (int i = 1; i < BENCH_FLOAT_ITERATIONS; i++) {
            d += Math.sqrt(i) * Math.sin(i * 0.001);
            if ((i & 0x7FFFF) == 0 && benchCancelled) { postCancelled(); return; }
        }
        long floatT = now() - t;

        if (benchCancelled) { postCancelled(); return; }
        bench.postValue(new BenchState(true, str(R.string.bench_phase3), false));
        t = now();
        byte[] buf = new byte[BENCH_MEM_BYTES];
        for (int i = 0; i < buf.length; i++) buf[i] = (byte) i;
        long chk = 0;
        for (byte b : buf) chk += b;
        long memT = now() - t;
        if (BuildConfig.DEBUG) Log.v(TAG, "bench accumulators: s=" + s + " d=" + d + " chk=" + chk);

        if (benchCancelled) { postCancelled(); return; }
        bench.postValue(new BenchState(true, str(R.string.bench_phase4), false));
        int cores = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[cores];
        t = now();
        for (int c = 0; c < cores; c++) {
            threads[c] = new Thread(() -> {
                double x = 0;
                for (long i = 0; i < BENCH_CORE_ITERATIONS; i++) x += Math.sqrt(i);
                if (x < 0) Log.v(TAG, "unreachable"); // keeps x live for the JIT
            });
            threads[c].start();
        }
        for (Thread th : threads) {
            try { th.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        long multiT = now() - t;
        if (benchCancelled) { postCancelled(); return; }

        long score = (long) ((double) BENCH_INT_ITERATIONS / Math.max(intT, 1) * 10
                + (double) BENCH_FLOAT_ITERATIONS / Math.max(floatT, 1) * 32
                + (double) BENCH_MEM_BYTES / 1024.0 / Math.max(memT, 1) * 8
                + (double) BENCH_CORE_ITERATIONS * cores / Math.max(multiT, 1) * 12);
        // X7: thresholds calibrated for 2026; +20%/year so a newer device still rates fairly.
        int year = Calendar.getInstance().get(Calendar.YEAR);
        double yearMul = Math.pow(1.20, Math.max(0, year - 2026));
        int ratingRes = score > 200000 * yearMul ? R.string.bench_rating_flagship :
                        score > 130000 * yearMul ? R.string.bench_rating_highend  :
                        score >  75000 * yearMul ? R.string.bench_rating_midrange :
                        score >  35000 * yearMul ? R.string.bench_rating_budget   :
                                                   R.string.bench_rating_entry;
        String rating = str(ratingRes);
        // X6: Locale.US keeps digits ASCII even in Arabic
        String res = String.format(Locale.US,
                str(R.string.bench_complete_format).replace("\\n", "\n"),
                score, rating, intT, floatT, memT, multiT, cores);
        AppPrefs.appendBenchHistory(getApplication(), score, rating); // Y2
        bench.postValue(new BenchState(false, res, true));
    }

    private void postCancelled() {
        bench.postValue(new BenchState(false, str(R.string.bench_cancelled), false));
    }

    // ── CPU temperature history ───────────────────────────────────────────

    /** Invalid (<=0) readings are skipped rather than plotted as a spurious zero. */
    public void pushCpuTemp(float celsius) {
        if (celsius <= 0) return;
        cpuTempHistory[cpuHistoryIndex] = celsius;
        cpuHistoryIndex = (cpuHistoryIndex + 1) % CPU_HISTORY_SIZE;
        if (cpuHistoryCount < CPU_HISTORY_SIZE) cpuHistoryCount++;
    }

    /** Oldest-first copy for the sparkline (left → right). */
    public float[] cpuHistory() {
        float[] out = new float[cpuHistoryCount];
        int start = (cpuHistoryCount < CPU_HISTORY_SIZE) ? 0 : cpuHistoryIndex;
        for (int i = 0; i < cpuHistoryCount; i++) out[i] = cpuTempHistory[(start + i) % CPU_HISTORY_SIZE];
        return out;
    }

    @Override protected void onCleared() {
        benchCancelled = true;
        if (executor != null) executor.shutdownNow();
    }

    private String str(int res) { return getApplication().getString(res); }
    private static long now() { return System.currentTimeMillis(); }
}
