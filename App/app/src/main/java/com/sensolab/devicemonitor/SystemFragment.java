package com.sensolab.devicemonitor;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import com.sensolab.devicemonitor.databinding.FragmentSystemBinding;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class SystemFragment extends ModeAwareFragment {

    private static final long THERMAL_POLL_MS = 2000L;
    private static final long THERMAL_FIRST_DELAY_MS = 200L;

    private FragmentSystemBinding binding;
    private SystemViewModel vm;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** R1: dedicated background thread for /sys file I/O so it never blocks the UI. */
    private ExecutorService thermalIo;
    /** Composed poller (not a base class — this fragment already extends ModeAwareFragment). */
    private final Poller thermalPoller = new Poller(handler, THERMAL_POLL_MS, this::pollThermal);

    private BroadcastReceiver battReceiver;
    private boolean battRegistered = false;

    // Cached spec strings for export
    private String cachedDevice = "", cachedAndroid = "", cachedCpu = "", cachedGpu = "",
                   cachedRam = "", cachedStorage = "", cachedScreen = "", cachedAbi = "";

    @Override public void onCreate(@Nullable Bundle s) {
        super.onCreate(s);
        vm = new ViewModelProvider(this).get(SystemViewModel.class);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentSystemBinding.inflate(inf, c, false);
        bindModeLayouts(binding.sysLayoutTextual, binding.sysLayoutVisual);
        populateSpecs();
        setupBattery();
        binding.btnBenchmark.setOnClickListener(x -> vm.toggleBenchmark());
        binding.btnExport.setOnClickListener(x -> exportSpecs());
        binding.btnExportJson.setOnClickListener(x -> exportSpecsJson());
        binding.btnCopySpecs.setOnClickListener(x -> copySpecsToClipboard());
        vm.bench().observe(getViewLifecycleOwner(), this::renderBench);
        updateBenchSparkline();
        binding.sparkCpuTemp.setValues(vm.cpuHistory());
        // X4: export status survives theme change / recreate
        if (s != null) {
            String export = s.getString("exportStatus");
            if (export != null) binding.tvExportStatus.setText(export);
        }
        return binding.getRoot();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (binding != null) out.putString("exportStatus", binding.tvExportStatus.getText().toString());
    }

    @Override public void onResume() {
        super.onResume();
        if (thermalIo == null || thermalIo.isShutdown()) thermalIo = NamedThreads.singleThread("Thermal"); // Z1
        registerBattery();
        thermalPoller.start(THERMAL_FIRST_DELAY_MS);
    }

    @Override public void onPause() {
        super.onPause();
        thermalPoller.stop();
        handler.removeCallbacksAndMessages(null);
        if (thermalIo != null) { thermalIo.shutdownNow(); thermalIo = null; }
        unregisterBattery();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Benchmark (runs in SystemViewModel; survives rotation) ────────────
    private void renderBench(SystemViewModel.BenchState st) {
        if (binding == null || st == null) return;
        binding.pbBenchmark.setVisibility(st.running ? View.VISIBLE : View.GONE);
        binding.btnBenchmark.setText(st.running ? R.string.bench_running : R.string.system_btn_run_benchmark);
        if (st.justFinished) {
            binding.tvBenchResult.setText(st.text + renderBenchHistory()); // Y2
            binding.tvBenchResult.announceForAccessibility(st.text); // Q5
            updateBenchSparkline();
        } else {
            binding.tvBenchResult.setText(st.text);
        }
    }

    /** Y2: the last few runs as a small text block, or "" if none yet. */
    private String renderBenchHistory() {
        String raw = AppPrefs.getBenchHistory(requireContext());
        if (raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n").append(getString(R.string.bench_history_title)).append("\n");
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        int i = 0;
        for (String row : raw.split("\n")) {
            String[] parts = row.split("\\|", 3);
            if (parts.length < 3) continue;
            try {
                long ts = Long.parseLong(parts[0]);
                sb.append("  ").append(fmt.format(new Date(ts)))
                  .append(String.format(Locale.US, "  %,d", Long.parseLong(parts[1])))
                  .append("  ").append(parts[2]).append("\n");
            } catch (NumberFormatException ignored) {}
            if (++i >= AppPrefs.BENCH_HISTORY_MAX) break;
        }
        return sb.toString();
    }

    /** Batch 7: benchmark score trend. History is newest-first; the sparkline wants oldest-first. */
    private void updateBenchSparkline() {
        if (binding == null || !isAdded()) return;
        String raw = AppPrefs.getBenchHistory(requireContext());
        List<Float> scores = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String row : raw.split("\n")) {
                String[] parts = row.split("\\|", 3);
                if (parts.length < 2) continue;
                try { scores.add(Float.parseFloat(parts[1])); } catch (NumberFormatException ignored) {}
            }
        }
        Collections.reverse(scores);
        float[] arr = new float[scores.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = scores.get(i);
        binding.sparkBenchHistory.setValues(arr);
    }

    // ── Battery ───────────────────────────────────────────────────────────
    private void setupBattery() {
        battReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (!isAdded() || binding == null) return;
                float celsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f;
                int lv = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int sc = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int pct = sc > 0 ? lv * 100 / sc : 0;
                int st = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                int plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                boolean charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
                String source = plug == BatteryManager.BATTERY_PLUGGED_USB ? getString(R.string.charge_usb) :
                                plug == BatteryManager.BATTERY_PLUGGED_AC ? getString(R.string.charge_ac) :
                                plug == BatteryManager.BATTERY_PLUGGED_WIRELESS ? getString(R.string.charge_wireless) : "";
                Context c = requireContext();
                String temp = String.format(Locale.US, "%.1f", AppPrefs.temp(celsius, c));
                String detail = pct + "%" + (charging ? " · " + getString(R.string.battery_charging_via, source).trim() : "");
                binding.cardBattTemp.setReading(temp, AppPrefs.tempUnit(c), detail);
                binding.gaugeBattTemp.setReading(celsius, temp, AppPrefs.tempUnit(c), pct + "%");
            }
        };
    }

    private void registerBattery() {
        if (!battRegistered && isAdded()) {
            // B1: Android 14+ requires an explicit export flag; BATTERY_CHANGED is protected.
            ContextCompat.registerReceiver(requireActivity(), battReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
            battRegistered = true;
        }
    }

    private void unregisterBattery() {
        if (battRegistered && isAdded()) {
            try { requireActivity().unregisterReceiver(battReceiver); }
            catch (IllegalArgumentException ignored) { /* already unregistered */ }
            battRegistered = false;
        }
    }

    // ── Thermal polling ───────────────────────────────────────────────────
    /** One Poller tick — /sys reads on thermalIo, results posted back to the UI. */
    private void pollThermal() {
        if (!isAdded()) return;
        final ExecutorService io = thermalIo;
        if (io == null || io.isShutdown()) return;
        io.execute(() -> {
            float cpu   = ThermalZones.cpu();
            float gpu   = ThermalZones.gpu();
            float skin  = ThermalZones.skin();
            float modem = ThermalZones.zone("mdm", "modem", "pa", "lte_pa", "5g_pa", "mmw_pa", "nr_pa");
            float wifi  = ThermalZones.zone("wifi", "wlan", "wlan_therm", "bt_therm", "wcn", "wcnss");
            float chgr  = ThermalZones.zone("charger", "chg", "usb", "usb_therm", "bms", "bq_bms");
            float npu   = ThermalZones.zone("npu", "apu", "ipa", "nsp", "cdsp", "hexagon", "adsp");
            handler.post(() -> {
                if (!isAdded() || binding == null) return;
                Context ctx = requireContext();
                setTemp(binding.cardCpuTemp,     binding.gaugeCpuTemp,  cpu,   ctx);
                setTemp(binding.cardGpuTemp,     binding.gaugeGpuTemp,  gpu,   ctx);
                setTemp(binding.cardSkinTemp,    binding.gaugeSkinTemp, skin,  ctx);
                setTemp(binding.cardModemTemp,   null, modem, ctx);
                setTemp(binding.cardWifiTemp,    null, wifi,  ctx);
                setTemp(binding.cardChargerTemp, null, chgr,  ctx);
                setTemp(binding.cardNpuTemp,     null, npu,   ctx);
                ThermalAlerts.checkAndNotify(ctx, cpu);
                vm.pushCpuTemp(cpu);
                binding.sparkCpuTemp.setValues(vm.cpuHistory());
            });
        });
    }

    private void setTemp(SensorCardView card, @Nullable GaugeCardView gauge, float celsius, Context ctx) {
        if (celsius > 0) {
            String v = String.format(Locale.US, "%.1f", AppPrefs.temp(celsius, ctx));
            String state = getString(celsius > 65 ? R.string.temp_state_hot
                                   : celsius > 45 ? R.string.temp_state_warm : R.string.temp_state_ok);
            card.setReading(v, AppPrefs.tempUnit(ctx), state);
            if (gauge != null) gauge.setReading(celsius, v, AppPrefs.tempUnit(ctx), state);
        } else {
            String na = getString(R.string.status_na);
            card.setStatus(na);
            if (gauge != null) gauge.setUnavailable(na);
        }
    }

    // ── Specs ─────────────────────────────────────────────────────────────
    private void populateSpecs() {
        if (!isAdded()) return;
        String brand = cap(Build.MANUFACTURER), model = Build.MODEL;
        cachedDevice = brand + " " + model;
        binding.cardDevice.setReading(model, null, brand);
        binding.cardVisDevice.setReading(model, null, brand + " · Android " + Build.VERSION.RELEASE);

        cachedAndroid = getString(R.string.spec_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
        binding.cardAndroid.setReading(Build.VERSION.RELEASE, null, "API " + Build.VERSION.SDK_INT
                + (Build.VERSION.SECURITY_PATCH != null ? "\n" + Build.VERSION.SECURITY_PATCH : ""));

        int cores = Runtime.getRuntime().availableProcessors();
        String cpuName = getCpuInfo();
        cachedCpu = cpuName + " · " + getString(R.string.spec_cores, cores);
        binding.cardCpu.setReading(String.valueOf(cores), getString(R.string.spec_cores_unit), cpuName);

        ActivityManager am = (ActivityManager) requireActivity().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long tMB = mi.totalMem / (1024 * 1024), fMB = mi.availMem / (1024 * 1024);
        int ramPct = (int) ((tMB - fMB) * 100 / Math.max(tMB, 1));
        cachedRam = tMB + " MB total | " + fMB + " MB free | " + ramPct + "% used";
        binding.cardRam.setReading(String.format(Locale.US, "%.1f", tMB / 1024f), "GB",
                String.format(Locale.US, "%d MB free · %d%%", fMB, ramPct));
        binding.gaugeRam.setReading(ramPct, ramPct + "%", null,
                String.format(Locale.US, "%d MB free / %d MB", fMB, tMB));

        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            long tB = sf.getBlockCountLong() * sf.getBlockSizeLong();
            long fB = sf.getAvailableBlocksLong() * sf.getBlockSizeLong();
            long tG = tB / (1024L * 1024 * 1024), fG = fB / (1024L * 1024 * 1024);
            int sp = (int) ((tB - fB) * 100 / Math.max(tB, 1));
            cachedStorage = tG + " GB total | " + fG + " GB free | " + sp + "% used";
            binding.cardStorage.setReading(String.valueOf(tG), "GB",
                    String.format(Locale.US, "%d GB free · %d%%", fG, sp));
            binding.gaugeStorage.setReading(sp, sp + "%", null,
                    String.format(Locale.US, "%d GB free / %d GB", fG, tG));
        } catch (Exception e) {
            binding.cardStorage.setStatus(getString(R.string.status_na));
        }

        DisplayMetrics dm = new DisplayMetrics();
        // B2: getDefaultDisplay() is deprecated since API 30.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.Display d = requireContext().getDisplay();
            if (d != null) d.getRealMetrics(dm);
        } else {
            requireActivity().getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        }
        float diag = (float) Math.sqrt(Math.pow(dm.widthPixels / dm.xdpi, 2) + Math.pow(dm.heightPixels / dm.ydpi, 2));
        cachedScreen = dm.widthPixels + "×" + dm.heightPixels + "px | " + (int) dm.xdpi + " dpi | "
                + String.format(Locale.US, "%.1f\"", diag);
        binding.cardScreen.setReading(String.format(Locale.US, "%.1f", diag), "\"",
                dm.widthPixels + "×" + dm.heightPixels + " · " + (int) dm.xdpi + " dpi");

        String[] abis = Build.SUPPORTED_ABIS;
        cachedAbi = (abis != null && abis.length > 0) ? String.join(", ", abis) : "";
        binding.cardAbi.setReading(abis != null && abis.length > 0 ? abis[0] : "—", null, cachedAbi);

        binding.cardGpu.setStatus(getString(R.string.status_waiting));
        ExecutorService gpuIo = NamedThreads.singleThread("GpuInfo");
        gpuIo.execute(() -> {
            GpuInfo.Result gpu = GpuInfo.query();
            handler.post(() -> {
                if (binding == null || !isAdded()) return;
                if (gpu == null || gpu.renderer.isEmpty()) {
                    binding.cardGpu.setStatus(getString(R.string.status_na));
                } else {
                    cachedGpu = gpu.renderer + " (" + gpu.vendor + ") · " + gpu.version;
                    binding.cardGpu.setReading(gpu.renderer, null, gpu.vendor + "\n" + gpu.version);
                }
            });
        });
        gpuIo.shutdown(); // lets the one queued query finish, then the thread exits
    }

    private String getCpuInfo() {
        // C5: catch IOException only — let real bugs surface
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                for (String tag : new String[]{"Hardware", "model name", "Processor"}) {
                    if (line.startsWith(tag)) {
                        String[] p = line.split(":", 2);
                        if (p.length > 1 && !p[1].trim().isEmpty()) return p[1].trim();
                    }
                }
            }
        } catch (IOException ignored) {
            // unreadable on this device — fall back to the SoC fields below
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Build.SOC_MODEL.isEmpty()
                && !Build.UNKNOWN.equals(Build.SOC_MODEL)) {
            return Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL;
        }
        return Build.HARDWARE;
    }

    private static String cap(String s) {
        return (s == null || s.isEmpty()) ? s : s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }

    // ── Export ────────────────────────────────────────────────────────────
    private File exportDir() throws IOException {
        File dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        // C6: cache fallback uses the subdir declared in file_paths.xml
        if (dir == null) dir = new File(requireContext().getCacheDir(), "PhoneLab_exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        return dir;
    }

    private void exportSpecs() {
        if (!isAdded() || binding == null) return;
        try {
            // C2: ms precision so two taps in the same second don't overwrite
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            String filename = "PhoneLab_Specs_" + ts + ".txt";
            File out = new File(exportDir(), filename);
            Context ctx = requireContext();

            StringBuilder sb = new StringBuilder();
            sb.append("══════════════════════════════\n");
            sb.append("  PhoneLab v").append(BuildConfig.VERSION_NAME).append(" — System Spec Report\n");
            sb.append("  Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("\n");
            sb.append("══════════════════════════════\n\n");
            sb.append("Device\n").append(cachedDevice).append("\n\n");
            sb.append("Android\n").append(cachedAndroid).append("\n\n");
            sb.append("CPU\n").append(cachedCpu).append("\n\n");
            if (!cachedGpu.isEmpty()) sb.append("GPU\n").append(cachedGpu).append("\n\n");
            sb.append("RAM\n").append(cachedRam).append("\n\n");
            sb.append("Storage\n").append(cachedStorage).append("\n\n");
            sb.append("Screen\n").append(cachedScreen).append("\n\n");
            sb.append("ABI\n").append(cachedAbi).append("\n\n");
            sb.append("Temperatures at export time\n");
            String[] labels = {"CPU", "GPU", "Skin", "Modem", "WiFi/BT", "Charger", "NPU"};
            float[] temps = exportTemps();
            for (int i = 0; i < labels.length; i++) {
                if (temps[i] > 0) sb.append(String.format(Locale.US, "  %-10s %.1f%s\n",
                        labels[i] + ":", AppPrefs.temp(temps[i], ctx), AppPrefs.tempUnit(ctx)));
            }
            sb.append("\n──────────────────────────────\n");
            sb.append("Generated by PhoneLab v").append(BuildConfig.VERSION_NAME).append("\n");
            sb.append("com.sensolab.devicemonitor\n");

            try (FileWriter fw = new FileWriter(out)) { fw.write(sb.toString()); }
            share(out, "text/plain");
            exportDone(filename);
        } catch (Exception e) {
            exportFailed(e);
        }
    }

    /** Batch 7: JSON alongside the plain-text export (org.json is built into the platform). */
    private void exportSpecsJson() {
        if (!isAdded() || binding == null) return;
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            String filename = "PhoneLab_Specs_" + ts + ".json";
            File out = new File(exportDir(), filename);

            org.json.JSONObject root = new org.json.JSONObject();
            root.put("app", "PhoneLab");
            root.put("version", BuildConfig.VERSION_NAME);
            root.put("generated", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
            root.put("device", cachedDevice);
            root.put("android", cachedAndroid);
            root.put("cpu", cachedCpu);
            root.put("gpu", cachedGpu);
            root.put("ram", cachedRam);
            root.put("storage", cachedStorage);
            root.put("screen", cachedScreen);
            root.put("abi", cachedAbi);
            org.json.JSONObject temps = new org.json.JSONObject();
            String[] keys = {"cpu_c", "gpu_c", "skin_c", "modem_c", "wifi_c", "charger_c", "npu_c"};
            float[] values = exportTemps();
            for (int i = 0; i < keys.length; i++) {
                temps.put(keys[i], values[i] > 0 ? (Object) values[i] : org.json.JSONObject.NULL);
            }
            root.put("temperatures", temps);

            try (FileWriter fw = new FileWriter(out)) { fw.write(root.toString(2)); }
            share(out, "application/json");
            exportDone(filename);
        } catch (Exception e) {
            exportFailed(e);
        }
    }

    private static float[] exportTemps() {
        return new float[]{
                ThermalZones.cpu(), ThermalZones.gpu(), ThermalZones.skin(),
                ThermalZones.zone("mdm", "modem", "pa", "lte_pa"),
                ThermalZones.zone("wifi", "wlan", "wlan_therm"),
                ThermalZones.zone("charger", "chg", "usb"),
                ThermalZones.zone("npu", "apu", "ipa", "nsp", "cdsp")};
    }

    private void share(File file, String mime) {
        Context ctx = requireContext();
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.export_chooser)));
    }

    private void exportDone(String filename) {
        // S2: success + privacy notice, visible before the user forwards the file
        String msg = getString(R.string.export_success, filename) + "\n\n" + getString(R.string.export_privacy_note);
        binding.tvExportStatus.setText(msg);
    }

    private void exportFailed(Exception e) {
        binding.tvExportStatus.setText(getString(R.string.export_failed, e.getMessage()));
    }

    /** Y4: copy the spec snapshot as plain text, without writing a file. */
    private void copySpecsToClipboard() {
        if (!isAdded() || binding == null) return;
        String text = "PhoneLab v" + BuildConfig.VERSION_NAME + " — Specs\n\n"
                + cachedDevice + "\n" + cachedAndroid + "\n" + cachedCpu + "\n"
                + (cachedGpu.isEmpty() ? "" : cachedGpu + "\n")
                + cachedRam + "\n" + cachedStorage + "\n" + cachedScreen + "\n" + cachedAbi;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("PhoneLab Specs", text));
            binding.tvExportStatus.setText(R.string.export_copied);
        }
    }
}
