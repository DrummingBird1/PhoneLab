package com.sensolab.devicemonitor;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SystemFragment extends Fragment {

    // textual
    private LinearLayout layoutTextual, layoutVisual;
    private TextView tvBattTemp, tvCpuTemp, tvGpuTemp;
    private TextView tvDevice, tvAndroid, tvCpu, tvRam, tvStorage, tvScreen, tvAbi;
    private TextView tvBenchResult;
    private ProgressBar pbBench;
    private Button btnBench;

    // visual
    private TextView visBattTemp, visCpuTemp, visGpuTemp;
    private TextView visDevice, visRam, visStorage;
    private ProgressBar pbRam, pbStorage;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private boolean benchRunning = false;
    private BroadcastReceiver battReceiver;
    private boolean battRegistered = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_system, container, false);

        layoutTextual = view.findViewById(R.id.sys_layout_textual);
        layoutVisual  = view.findViewById(R.id.sys_layout_visual);

        // textual
        tvBattTemp  = view.findViewById(R.id.tv_battery_temp);
        tvCpuTemp   = view.findViewById(R.id.tv_cpu_temp);
        tvGpuTemp   = view.findViewById(R.id.tv_gpu_temp);
        tvDevice    = view.findViewById(R.id.tv_device_name);
        tvAndroid   = view.findViewById(R.id.tv_android);
        tvCpu       = view.findViewById(R.id.tv_cpu);
        tvRam       = view.findViewById(R.id.tv_ram);
        tvStorage   = view.findViewById(R.id.tv_storage);
        tvScreen    = view.findViewById(R.id.tv_screen);
        tvAbi       = view.findViewById(R.id.tv_abi);
        tvBenchResult = view.findViewById(R.id.tv_bench_result);
        pbBench     = view.findViewById(R.id.pb_benchmark);
        btnBench    = view.findViewById(R.id.btn_benchmark);

        // visual
        visBattTemp  = view.findViewById(R.id.vis_batt_temp);
        visCpuTemp   = view.findViewById(R.id.vis_cpu_temp);
        visGpuTemp   = view.findViewById(R.id.vis_gpu_temp);
        visDevice    = view.findViewById(R.id.vis_device);
        visRam       = view.findViewById(R.id.vis_ram);
        visStorage   = view.findViewById(R.id.vis_storage);
        pbRam        = view.findViewById(R.id.pb_vis_ram);
        pbStorage    = view.findViewById(R.id.pb_vis_storage);

        populateSpecs();
        setupBattery();
        btnBench.setOnClickListener(v -> runBenchmark());

        return view;
    }

    @Override public void onResume() {
        super.onResume();
        applyDisplayMode();
        startTempUpdates();
        registerBattery();
    }

    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
        unregisterBattery();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if (executor != null && !executor.isShutdown()) executor.shutdown();
    }

    private void applyDisplayMode() {
        boolean visual = AppPrefs.isVisual(requireContext());
        layoutTextual.setVisibility(visual ? View.GONE  : View.VISIBLE);
        layoutVisual .setVisibility(visual ? View.VISIBLE : View.GONE);
    }

    private void setupBattery() {
        battReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                float temp = tempRaw / 10.0f;
                int  level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int  scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int  pct   = (level * 100) / scale;
                String color = temp > 45 ? "🔴" : temp > 38 ? "🟡" : "🟢";
                String txt = String.format(Locale.US,
                        "🔋 Battery\n%.1f°C  %s\n%d%% charged", temp, color, pct);
                if (tvBattTemp  != null) tvBattTemp.setText(txt);
                if (visBattTemp != null) visBattTemp.setText(
                        String.format(Locale.US,"%s\n%.1f°C\n%d%%",
                                temp>45?"🔴":temp>38?"🟡":"🟢", temp, pct));
            }
        };
    }

    private void registerBattery() {
        if (!battRegistered && battReceiver != null && isAdded()) {
            requireActivity().registerReceiver(battReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            battRegistered = true;
        }
    }

    private void unregisterBattery() {
        if (battRegistered && battReceiver != null && isAdded()) {
            try { requireActivity().unregisterReceiver(battReceiver); }
            catch (Exception ignored) {}
            battRegistered = false;
        }
    }

    private void startTempUpdates() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!isAdded()) return;
                updateTemps();
                handler.postDelayed(this, 2000);
            }
        }, 300);
    }

    private void updateTemps() {
        float cpuT = readThermalZone("cpu","cpu0","soc_thermal","mtktscpu",
                "cpu_thermal","tsens_tz_sensor0","thermal_zone0_temp");
        float gpuT = readThermalZone("gpu","gpu0","mtktsgpu",
                "gpu_thermal","tsens_tz_sensor10");

        String cpuColor = cpuT > 70 ? "🔴" : cpuT > 50 ? "🟡" : "🟢";
        String gpuColor = gpuT > 70 ? "🔴" : gpuT > 50 ? "🟡" : "🟢";

        if (tvCpuTemp != null) tvCpuTemp.setText(cpuT > 0
                ? String.format(Locale.US,"🔥 CPU Temp\n%.1f°C %s", cpuT, cpuColor)
                : "🔥 CPU Temp\nN/A");
        if (tvGpuTemp != null) tvGpuTemp.setText(gpuT > 0
                ? String.format(Locale.US,"🎮 GPU Temp\n%.1f°C %s", gpuT, gpuColor)
                : "🎮 GPU Temp\nN/A");

        if (visCpuTemp != null) visCpuTemp.setText(cpuT > 0
                ? String.format(Locale.US,"%s\n%.1f°C", cpuColor, cpuT) : "🔥\nN/A");
        if (visGpuTemp != null) visGpuTemp.setText(gpuT > 0
                ? String.format(Locale.US,"%s\n%.1f°C", gpuColor, gpuT) : "🎮\nN/A");
    }

    private float readThermalZone(String... keywords) {
        File dir = new File("/sys/class/thermal");
        if (!dir.exists()) return -1;
        File[] zones = dir.listFiles();
        if (zones == null) return -1;
        for (File z : zones) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            try {
                String type = readLine(new File(z,"type")).toLowerCase(Locale.US);
                for (String kw : keywords) {
                    if (type.contains(kw)) {
                        float val = Float.parseFloat(readLine(new File(z,"temp")).trim());
                        return val > 1000 ? val/1000f : val;
                    }
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private String readLine(File f) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine();
        }
    }

    private void populateSpecs() {
        String brand = capitalize(Build.MANUFACTURER);
        String model = Build.MODEL;
        if (tvDevice != null) tvDevice.setText("📱 Device\n" + brand + " " + model);
        if (tvAndroid!= null) tvAndroid.setText("🤖 Android " + Build.VERSION.RELEASE
                + "\nAPI " + Build.VERSION.SDK_INT);
        if (visDevice!= null) visDevice.setText("📱\n"+brand+"\n"+model);

        // CPU
        String cpuInfo = getCpuInfo();
        int cores = Runtime.getRuntime().availableProcessors();
        if (tvCpu != null) tvCpu.setText("⚡ CPU\n" + cpuInfo + "\n" + cores + " cores");

        // RAM
        ActivityManager am = (ActivityManager) requireActivity()
                .getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long totalRam = mi.totalMem / (1024*1024);
        long freeRam  = mi.availMem / (1024*1024);
        int ramPct = (int)((totalRam - freeRam) * 100 / Math.max(totalRam,1));
        if (tvRam != null) tvRam.setText(String.format(Locale.US,
                "💾 RAM\n%d MB total\n%d MB free", totalRam, freeRam));
        if (visRam != null) visRam.setText(String.format(Locale.US,
                "💾\n%dMB free\n%d%% used", freeRam, ramPct));
        if (pbRam != null) pbRam.setProgress(ramPct);

        // Storage
        try {
            StatFs sf = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long totalB = sf.getBlockCountLong() * sf.getBlockSizeLong();
            long freeB  = sf.getAvailableBlocksLong() * sf.getBlockSizeLong();
            long totalG = totalB/(1024*1024*1024);
            long freeG  = freeB/(1024*1024*1024);
            int stPct = (int)((totalB-freeB)*100/Math.max(totalB,1));
            if (tvStorage != null) tvStorage.setText(String.format(Locale.US,
                    "🗂 Storage\n%d GB total\n%d GB free", totalG, freeG));
            if (visStorage != null) visStorage.setText(String.format(Locale.US,
                    "🗂\n%dGB free\n%d%% used", freeG, stPct));
            if (pbStorage != null) pbStorage.setProgress(stPct);
        } catch (Exception e) {
            if (tvStorage != null) tvStorage.setText("🗂 Storage\nN/A");
        }

        // Screen
        DisplayMetrics dm = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        if (tvScreen != null) tvScreen.setText(String.format(Locale.US,
                "📺 Screen\n%d×%d px\n%.0f dpi", dm.widthPixels, dm.heightPixels, dm.xdpi));

        // ABI
        String abi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;
        if (tvAbi != null) tvAbi.setText("🔧 Arch: " + abi);
    }

    private String getCpuInfo() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Hardware") || line.startsWith("model name")
                        || line.startsWith("Processor")) {
                    String[] p = line.split(":");
                    if (p.length > 1) return p[1].trim();
                }
            }
        } catch (Exception ignored) {}
        return Build.HARDWARE;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase(Locale.US)+s.substring(1);
    }

    // ── BENCHMARK ─────────────────────────────────────────────────────────
    private void runBenchmark() {
        if (benchRunning) return;
        benchRunning = true;
        if (executor != null) executor.shutdown();
        executor = Executors.newSingleThreadExecutor();
        btnBench.setEnabled(false);
        btnBench.setText("Running...");
        pbBench.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            post(()-> tvBenchResult.setText("⚙️ Phase 1: Integer Math..."));
            long t = System.currentTimeMillis(); long s = 0;
            for (long i=0;i<50_000_000L;i++) s += i*31L + i%7;
            long intT = System.currentTimeMillis()-t;

            post(()-> tvBenchResult.setText("⚙️ Phase 2: Float / Trig..."));
            t = System.currentTimeMillis(); double d = 1.0;
            for (int i=1;i<5_000_000;i++) d += Math.sqrt(i)*Math.sin(i)/(i);
            long floatT = System.currentTimeMillis()-t;

            post(()-> tvBenchResult.setText("⚙️ Phase 3: Memory bandwidth..."));
            t = System.currentTimeMillis();
            byte[] buf = new byte[8*1024*1024];
            for (int i=0;i<buf.length;i++) buf[i]=(byte)(i&0xFF);
            long memT = System.currentTimeMillis()-t;

            post(()-> tvBenchResult.setText("⚙️ Phase 4: Multi-Core..."));
            int cores = Runtime.getRuntime().availableProcessors();
            Thread[] threads = new Thread[cores];
            t = System.currentTimeMillis();
            for (int c=0;c<cores;c++) {
                threads[c] = new Thread(()->{long x=0; for(long i=0;i<20_000_000L;i++) x+=i;});
                threads[c].start();
            }
            for (Thread th:threads){try{th.join();}catch(Exception ig){}}
            long multiT = System.currentTimeMillis()-t;

            long score = (long)(
                50_000_000.0/intT * 10 +
                5_000_000.0/floatT * 30 +
                8192.0/memT * 5 +
                20_000_000.0*cores/multiT * 8);

            String rating = score>120000?"🏆 Flagship":
                    score>80000?"🥇 High-End":
                    score>50000?"🥈 Mid-Range":
                    score>25000?"🥉 Budget":"⚡ Entry Level";

            String res = String.format(Locale.US,
                    "✅ Benchmark Complete!\n\n" +
                    "📊 Score: %,d pts\n%s\n\n" +
                    "Integer:    %d ms\n" +
                    "Float:      %d ms\n" +
                    "Memory:     %d ms\n" +
                    "Multi-Core: %d ms (%d cores)",
                    score,rating,intT,floatT,memT,multiT,cores);

            post(()->{
                if (!isAdded()) return;
                tvBenchResult.setText(res);
                pbBench.setVisibility(View.GONE);
                btnBench.setEnabled(true);
                btnBench.setText("▶ Run Benchmark");
                benchRunning = false;
            });
        });
    }

    private void post(Runnable r) { if (isAdded()) handler.post(r); }
}
