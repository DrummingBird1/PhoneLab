package com.sensolab.devicemonitor;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.sensolab.devicemonitor.databinding.FragmentSystemBinding;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class SystemFragment extends ModeAwareFragment {

    private static final String TAG = "PhoneLab.System";

    // A4: constants instead of magic numbers
    private static final long THERMAL_POLL_MS = 2000L;
    private static final long THERMAL_FIRST_DELAY_MS = 200L;
    private static final long BENCH_INT_ITERATIONS    = 60_000_000L;
    private static final int  BENCH_FLOAT_ITERATIONS  = 6_000_000;
    private static final int  BENCH_MEM_BYTES         = 16 * 1024 * 1024;
    private static final long BENCH_CORE_ITERATIONS   = 25_000_000L;

    private FragmentSystemBinding binding;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    /** R1: dedicated background pool for thermal file I/O so it never blocks UI. */
    private ExecutorService thermalIo;
    /** Composed poller (not a base class — this fragment already extends ModeAwareFragment). */
    private final Poller thermalPoller = new Poller(handler, THERMAL_POLL_MS, this::pollThermal);
    private volatile boolean benchRunning = false;   // B4: read on UI, written on worker
    private volatile boolean benchCancelled = false; // U5: cancel flag

    private BroadcastReceiver battReceiver;
    private boolean battRegistered = false;

    // Cached spec strings for export
    private String cachedDevice="", cachedAndroid="", cachedCpu="",
                   cachedRam="", cachedStorage="", cachedScreen="", cachedAbi="";

    // Batch 7: CPU-temp ring buffer for the trend sparkline — the live text card only
    // ever shows the instantaneous reading, so this history has to be kept separately.
    private static final int CPU_HISTORY_SIZE = 30;
    private final float[] cpuTempHistory = new float[CPU_HISTORY_SIZE];
    private int cpuHistoryCount = 0;
    private int cpuHistoryIndex = 0;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentSystemBinding.inflate(inf, c, false);
        bindModeLayouts(binding.sysLayoutTextual, binding.sysLayoutVisual);
        populateSpecs();
        setupBattery();
        binding.btnBenchmark.setOnClickListener(x -> runBenchmark());
        binding.btnExport.setOnClickListener(x -> exportSpecs());
        binding.btnExportJson.setOnClickListener(x -> exportSpecsJson()); // Batch 7
        binding.btnCopySpecs.setOnClickListener(x -> copySpecsToClipboard()); // Y4
        updateBenchSparkline(); // Batch 7: show existing history immediately
        // X4: restore last benchmark result + export status across theme change / recreate
        if (s != null) {
            String bench  = s.getString("benchResult");
            String export = s.getString("exportStatus");
            if (bench  != null) binding.tvBenchResult.setText(bench);
            if (export != null) binding.tvExportStatus.setText(export);
        }
        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        // X4
        if (binding != null) {
            out.putString("benchResult",  binding.tvBenchResult.getText().toString());
            out.putString("exportStatus", binding.tvExportStatus.getText().toString());
        }
    }

    @Override public void onResume() {
        super.onResume(); // ModeAwareFragment calls applyMode() + registers prefs listener
        // Z1: named thread for profiler visibility
        if (thermalIo == null || thermalIo.isShutdown()) thermalIo = NamedThreads.singleThread("Thermal");
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
        if (executor != null && !executor.isShutdown()) {
            benchCancelled = true;
            executor.shutdownNow(); // B5: stop bench worker if running
            try {
                executor.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        binding = null;
    }

    /** U2: re-render cached spec strings if units changed while we were resumed. */
    @Override protected void onUnitsChanged() {
        // Temperatures will refresh on next 2s poll; nothing else displays units in cache.
    }

    // ── Battery ───────────────────────────────────────────────────────────
    private void setupBattery() {
        battReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (!isAdded() || binding == null) return;
                int raw  = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                float c  = raw / 10.0f;
                int lv   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int sc   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int pct  = sc > 0 ? lv * 100 / sc : 0;
                int st   = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                int plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                boolean chg = st==BatteryManager.BATTERY_STATUS_CHARGING
                           || st==BatteryManager.BATTERY_STATUS_FULL;
                String ps = plug==BatteryManager.BATTERY_PLUGGED_USB?getString(R.string.charge_usb):
                            plug==BatteryManager.BATTERY_PLUGGED_AC?getString(R.string.charge_ac):
                            plug==BatteryManager.BATTERY_PLUGGED_WIRELESS?getString(R.string.charge_wireless):"";
                Context c2 = requireContext();
                float d = AppPrefs.temp(c, c2); String u = AppPrefs.tempUnit(c2);
                String l = led(c);
                String chgS = chg ? " ⚡"+ps : "";
                String batt = getString(R.string.temp_battery);
                binding.tvBatteryTemp.setText(batt + String.format(Locale.US,"\n%.1f%s %s\n%d%%%s",d,u,l,pct,chgS));
                binding.visBattTemp.setText(String.format(Locale.US,"%s\n%.1f%s\n%d%%",l,d,u,pct));
            }
        };
    }

    private void registerBattery() {
        if (!battRegistered && isAdded()) {
            // B1: Android 14+ requires explicit export flag. BATTERY_CHANGED is a
            // protected system broadcast → not exported.
            ContextCompat.registerReceiver(requireActivity(), battReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            battRegistered = true;
        }
    }
    private void unregisterBattery() {
        if (battRegistered && isAdded()) {
            try {
                requireActivity().unregisterReceiver(battReceiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was already unregistered (system reclaim, double-pause, etc.) — safe to ignore.
            }
            battRegistered = false;
        }
    }

    // ── Thermal polling ───────────────────────────────────────────────────
    /** One Poller tick — offloads the /sys reads to thermalIo, posts results back to binding. */
    private void pollThermal() {
        if (!isAdded()) return;
        // R1: do file I/O off main thread; post UI update back when done
        final ExecutorService io = thermalIo;
        if (io == null || io.isShutdown()) return;
        io.execute(() -> {
            float cpu   = ThermalZones.cpu();
            float gpu   = ThermalZones.zone("gpu","gpu0","mtktsgpu","gpu_thermal","tsens_tz_sensor10","kgsl_therm");
            float skin  = ThermalZones.zone("skin","skin_therm","quiet_therm","back_therm","bd_therm","xo_therm");
            float modem = ThermalZones.zone("mdm","modem","pa","lte_pa","5g_pa","mmw_pa","nr_pa");
            float wifi  = ThermalZones.zone("wifi","wlan","wlan_therm","bt_therm","wcn","wcnss");
            float chgr  = ThermalZones.zone("charger","chg","usb","usb_therm","bms","bq_bms");
            float npu   = ThermalZones.zone("npu","apu","ipa","nsp","cdsp","hexagon","adsp");
            handler.post(() -> {
                if (!isAdded() || binding == null) return;
                Context ctx = requireContext();
                setTemp(binding.tvCpuTemp,     binding.visCpuTemp,  R.string.temp_cpu,     cpu,   ctx);
                setTemp(binding.tvGpuTemp,     binding.visGpuTemp,  R.string.temp_gpu,     gpu,   ctx);
                setTemp(binding.tvSkinTemp,    binding.visSkinTemp, R.string.temp_skin,    skin,  ctx);
                setTemp(binding.tvModemTemp,   null,                R.string.temp_modem,   modem, ctx);
                setTemp(binding.tvWifiTemp,    null,                R.string.temp_wifi,    wifi,  ctx);
                setTemp(binding.tvChargerTemp, null,                R.string.temp_charger, chgr,  ctx);
                setTemp(binding.tvNpuTemp,     null,                R.string.temp_npu,     npu,   ctx);
                ThermalAlerts.checkAndNotify(ctx, cpu); // temperature alert hook
                pushCpuTemp(cpu); // Batch 7: trend sparkline
            });
        });
    }

    /** Batch 7: append to the ring buffer and refresh the sparkline. Invalid (<=0) readings
     *  are skipped rather than plotted as a spurious zero. */
    private void pushCpuTemp(float celsius) {
        if (celsius <= 0 || binding == null) return;
        cpuTempHistory[cpuHistoryIndex] = celsius;
        cpuHistoryIndex = (cpuHistoryIndex + 1) % CPU_HISTORY_SIZE;
        if (cpuHistoryCount < CPU_HISTORY_SIZE) cpuHistoryCount++;
        binding.sparkCpuTemp.setValues(chronological(cpuTempHistory, cpuHistoryIndex, cpuHistoryCount, CPU_HISTORY_SIZE));
    }

    /** Reads a circular buffer out in oldest-first order for the sparkline (left → right). */
    private static float[] chronological(float[] buf, int writeIndex, int count, int capacity) {
        float[] out = new float[count];
        int start = (count < capacity) ? 0 : writeIndex;
        for (int i = 0; i < count; i++) out[i] = buf[(start + i) % capacity];
        return out;
    }

    private void setTemp(TextView tv, TextView vis, int labelRes, float celsius, Context ctx) {
        if (!isAdded() || tv == null) return;
        String label = getString(labelRes);
        String na    = getString(R.string.status_na);
        if (celsius > 0) {
            float d=AppPrefs.temp(celsius,ctx); String u=AppPrefs.tempUnit(ctx); String l=led(celsius);
            tv.setText(String.format(Locale.US,"%s\n%.1f%s %s",label,d,u,l));
            if(vis!=null) vis.setText(String.format(Locale.US,"%s\n%.1f%s",l,d,u));
        } else {
            tv.setText(label + "\n" + na);
            if(vis!=null) vis.setText("--\n" + na);
        }
    }

    private String led(float c){return c>65?"🔴":c>45?"🟡":"🟢";}

    // ── Specs ─────────────────────────────────────────────────────────────
    private void populateSpecs() {
        if (!isAdded()) return;
        String brand=cap(Build.MANUFACTURER), model=Build.MODEL;
        cachedDevice = brand+" "+model;
        binding.tvDeviceName.setText(getString(R.string.spec_device) + "\n" + cachedDevice);
        binding.visDevice.setText("📱\n"+brand+"\n"+model);

        cachedAndroid = getString(R.string.spec_android, Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
        binding.tvAndroid.setText(cachedAndroid);

        int cores=Runtime.getRuntime().availableProcessors();
        cachedCpu = getCpuInfo() + " · " + getString(R.string.spec_cores, cores);
        binding.tvCpu.setText(getString(R.string.spec_cpu) + "\n" + cachedCpu);

        ActivityManager am=(ActivityManager)requireActivity().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long tMB=mi.totalMem/(1024*1024), fMB=mi.availMem/(1024*1024);
        int pct=(int)((tMB-fMB)*100/Math.max(tMB,1));
        cachedRam=tMB+" MB total | "+fMB+" MB free | "+pct+"% used";
        binding.tvRam.setText(getString(R.string.spec_ram) + "\n" + cachedRam);
        binding.visRam.setText(String.format(Locale.US,"💾\n%dMB free\n%d%%",fMB,pct));
        binding.pbVisRam.setProgress(pct);

        try {
            android.os.StatFs sf=new android.os.StatFs(
                    android.os.Environment.getDataDirectory().getPath());
            long tB=sf.getBlockCountLong()*sf.getBlockSizeLong();
            long fB=sf.getAvailableBlocksLong()*sf.getBlockSizeLong();
            long tG=tB/(1024*1024*1024), fG=fB/(1024*1024*1024);
            int sp=(int)((tB-fB)*100/Math.max(tB,1));
            cachedStorage=tG+" GB total | "+fG+" GB free | "+sp+"% used";
            binding.tvStorage.setText(getString(R.string.spec_storage) + "\n" + cachedStorage);
            binding.visStorage.setText(String.format(Locale.US,"🗂\n%dGB free\n%d%%",fG,sp));
            binding.pbVisStorage.setProgress(sp);
        } catch(Exception e){ binding.tvStorage.setText(getString(R.string.spec_storage) + "\n" + getString(R.string.status_na)); }

        DisplayMetrics dm = new DisplayMetrics();
        // B2: getDefaultDisplay() deprecated since API 30. Use Context.getDisplay() on R+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.Display d = requireContext().getDisplay();
            if (d != null) d.getRealMetrics(dm);
        } else {
            requireActivity().getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        }
        float diag=(float)Math.sqrt(Math.pow(dm.widthPixels/dm.xdpi,2)+Math.pow(dm.heightPixels/dm.ydpi,2));
        cachedScreen=dm.widthPixels+"×"+dm.heightPixels+"px | "+(int)dm.xdpi+" dpi | "+String.format(Locale.US,"%.1f\"",diag);
        binding.tvScreen.setText(getString(R.string.spec_screen) + "\n" + cachedScreen);

        String[] abis=Build.SUPPORTED_ABIS;
        cachedAbi=(abis!=null&&abis.length>0)?String.join(", ",abis):Build.CPU_ABI;
        binding.tvAbi.setText(getString(R.string.spec_abi, cachedAbi));
    }

    private String getCpuInfo() {
        // C5: catch IOException only — let real bugs (NPE etc) surface
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
            // /proc/cpuinfo unreadable on this device — fall through to Build.HARDWARE
        }
        return Build.HARDWARE;
    }

    private String cap(String s){
        return(s==null||s.isEmpty())?s:s.substring(0,1).toUpperCase(Locale.US)+s.substring(1);
    }

    // ── Export ────────────────────────────────────────────────────────────
    private void exportSpecs() {
        if (!isAdded() || binding == null) return;
        try {
            // C2: ms precision so two clicks in the same second don't overwrite
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            String filename = "PhoneLab_Specs_" + ts + ".txt";

            // Write to app-specific external docs dir (no permission needed on API 29+).
            // C6: cache fallback uses a specific subdir matching file_paths.xml.
            File dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = new File(requireContext().getCacheDir(), "PhoneLab_exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
            File out = new File(dir, filename);

            // Build content (C3: avoid hardcoded version string)
            StringBuilder sb = new StringBuilder();
            sb.append("══════════════════════════════\n");
            sb.append("  PhoneLab v").append(BuildConfig.VERSION_NAME).append(" — System Spec Report\n");
            sb.append("  Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date())).append("\n");
            sb.append("══════════════════════════════\n\n");
            sb.append("📱 Device\n").append(cachedDevice).append("\n\n");
            sb.append("🤖 Android\n").append(cachedAndroid).append("\n\n");
            sb.append("⚡ CPU\n").append(cachedCpu).append("\n\n");
            sb.append("💾 RAM\n").append(cachedRam).append("\n\n");
            sb.append("🗂 Storage\n").append(cachedStorage).append("\n\n");
            sb.append("📺 Screen\n").append(cachedScreen).append("\n\n");
            sb.append("🔧 ABI\n").append(cachedAbi).append("\n\n");

            // Read live temps
            Context ctx = requireContext();
            sb.append("🌡 Temperatures at export time\n");
            float[][] temps = {
                {ThermalZones.zone("cpu","cpu0","soc_thermal")},
                {ThermalZones.zone("gpu","gpu0","mtktsgpu")},
                {ThermalZones.zone("skin","skin_therm","quiet_therm")},
                {ThermalZones.zone("mdm","modem","pa","lte_pa")},
                {ThermalZones.zone("wifi","wlan","wlan_therm")},
                {ThermalZones.zone("charger","chg","usb")},
                {ThermalZones.zone("npu","apu","ipa","nsp","cdsp")},
            };
            String[] tlabels = {"CPU","GPU","Skin","Modem","WiFi/BT","Charger","NPU"};
            for (int i=0;i<tlabels.length;i++) {
                float c=temps[i][0];
                if(c>0) sb.append(String.format(Locale.US,"  %-10s %.1f%s\n",
                        tlabels[i]+":", AppPrefs.temp(c,ctx), AppPrefs.tempUnit(ctx)));
            }
            sb.append("\n──────────────────────────────\n");
            sb.append("Generated by PhoneLab v").append(BuildConfig.VERSION_NAME).append("\n");
            sb.append("com.sensolab.devicemonitor\n");

            try(FileWriter fw=new FileWriter(out)){fw.write(sb.toString());}

            // Share via intent
            android.net.Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName()+".fileprovider", out);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_chooser)));

            // S2: success + privacy notice so the user sees it before forwarding the file
            String msg = getString(R.string.export_success, filename)
                    + "\n\n" + getString(R.string.export_privacy_note);
            binding.tvExportStatus.setText(msg);
            binding.tvExportStatus.announceForAccessibility(msg); // Q5

        } catch (Exception e) {
            String err = getString(R.string.export_failed, e.getMessage());
            binding.tvExportStatus.setText(err);
            binding.tvExportStatus.announceForAccessibility(err); // Q5
        }
    }

    /** Batch 7: JSON alongside the existing plain-text export — same FileProvider/share
     *  plumbing as exportSpecs(), using org.json (built into the platform, no new dep). */
    private void exportSpecsJson() {
        if (!isAdded() || binding == null) return;
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            String filename = "PhoneLab_Specs_" + ts + ".json";

            File dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = new File(requireContext().getCacheDir(), "PhoneLab_exports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
            File out = new File(dir, filename);

            Context ctx = requireContext();
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("app", "PhoneLab");
            root.put("version", BuildConfig.VERSION_NAME);
            root.put("generated", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));
            root.put("device", cachedDevice);
            root.put("android", cachedAndroid);
            root.put("cpu", cachedCpu);
            root.put("ram", cachedRam);
            root.put("storage", cachedStorage);
            root.put("screen", cachedScreen);
            root.put("abi", cachedAbi);

            org.json.JSONObject temps = new org.json.JSONObject();
            String[] keys    = {"cpu_c","gpu_c","skin_c","modem_c","wifi_c","charger_c","npu_c"};
            float[][] values = {
                {ThermalZones.zone("cpu","cpu0","soc_thermal")},
                {ThermalZones.zone("gpu","gpu0","mtktsgpu")},
                {ThermalZones.zone("skin","skin_therm","quiet_therm")},
                {ThermalZones.zone("mdm","modem","pa","lte_pa")},
                {ThermalZones.zone("wifi","wlan","wlan_therm")},
                {ThermalZones.zone("charger","chg","usb")},
                {ThermalZones.zone("npu","apu","ipa","nsp","cdsp")},
            };
            for (int i = 0; i < keys.length; i++) {
                float c = values[i][0];
                temps.put(keys[i], c > 0 ? (Object) c : org.json.JSONObject.NULL);
            }
            root.put("temperatures", temps);

            try (FileWriter fw = new FileWriter(out)) { fw.write(root.toString(2)); }

            android.net.Uri uri = FileProvider.getUriForFile(ctx,
                    ctx.getPackageName() + ".fileprovider", out);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_chooser)));

            String msg = getString(R.string.export_success, filename)
                    + "\n\n" + getString(R.string.export_privacy_note);
            binding.tvExportStatus.setText(msg);
            binding.tvExportStatus.announceForAccessibility(msg);

        } catch (Exception e) {
            String err = getString(R.string.export_failed, e.getMessage());
            binding.tvExportStatus.setText(err);
            binding.tvExportStatus.announceForAccessibility(err);
        }
    }

    /** Y4: copy current spec snapshot as plain text to system clipboard. */
    private void copySpecsToClipboard() {
        if (!isAdded() || binding == null) return;
        String text = "PhoneLab v" + BuildConfig.VERSION_NAME + " — Specs\n\n"
                + cachedDevice + "\n" + cachedAndroid + "\n"
                + cachedCpu + "\n" + cachedRam + "\n" + cachedStorage + "\n"
                + cachedScreen + "\n" + cachedAbi;
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("PhoneLab Specs", text));
            String msg = getString(R.string.export_copied);
            binding.tvExportStatus.setText(msg);
            binding.tvExportStatus.announceForAccessibility(msg);
        }
    }

    // ── Benchmark ─────────────────────────────────────────────────────────
    private void runBenchmark() {
        if (benchRunning) {
            // U5: tapping the button while running cancels
            benchCancelled = true;
            return;
        }
        benchRunning  = true;
        benchCancelled = false;
        if (executor != null) executor.shutdown();
        executor = NamedThreads.singleThread("Bench"); // Z1
        binding.btnBenchmark.setText(R.string.bench_running);
        binding.pbBenchmark.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            post(()->setBenchResult(getString(R.string.bench_phase1)));
            long t=now();
            // D3: 's' must survive — without using it, the JIT eliminates the whole loop
            // and the benchmark measures nothing. Same applies to 'd' (D4) and 'chk' (D2).
            long s=0;
            for(long i=0;i<BENCH_INT_ITERATIONS;i++) { s^=i*2654435761L; if((i&0xFFFFFFL)==0 && benchCancelled) { finishBench(true,null); return; } }
            long intT=now()-t;

            post(()->setBenchResult(getString(R.string.bench_phase2)));
            t=now();double d=0;
            for(int i=1;i<BENCH_FLOAT_ITERATIONS;i++) { d+=Math.sqrt(i)*Math.sin(i*0.001); if((i&0x7FFFF)==0 && benchCancelled) { finishBench(true,null); return; } }
            long floatT=now()-t;

            if (benchCancelled) { finishBench(true,null); return; }
            post(()->setBenchResult(getString(R.string.bench_phase3)));
            t=now();
            byte[]buf=new byte[BENCH_MEM_BYTES];
            for(int i=0;i<buf.length;i++) buf[i]=(byte)i;
            long chk=0;for(byte b:buf) chk+=b;
            long memT=now()-t;

            // N8: feed the JIT-defeating accumulators through the log in debug builds
            // so they're observably "used" and a debugger can see plausible values.
            if (BuildConfig.DEBUG) Log.v(TAG, "bench accumulators: s=" + s + " d=" + d + " chk=" + chk);

            if (benchCancelled) { finishBench(true,null); return; }
            post(()->setBenchResult(getString(R.string.bench_phase4)));
            int cores=Runtime.getRuntime().availableProcessors();
            Thread[]threads=new Thread[cores];
            t=now();
            for(int c=0;c<cores;c++){
                threads[c]=new Thread(()->{double x=0;for(long i=0;i<BENCH_CORE_ITERATIONS;i++) x+=Math.sqrt(i);});
                threads[c].start();
            }
            for(Thread th:threads){try{th.join();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            long multiT=now()-t;

            long score=(long)((double)BENCH_INT_ITERATIONS/Math.max(intT,1)*10+
                             (double)BENCH_FLOAT_ITERATIONS/Math.max(floatT,1)*32+
                             (double)BENCH_MEM_BYTES/1024.0/Math.max(memT,1)*8+
                             (double)BENCH_CORE_ITERATIONS*cores/Math.max(multiT,1)*12);
            // X7: thresholds calibrated for 2026 baseline; shift +20% per year so a 2028
            // device with 1.4× the throughput of a 2026 device still scores "High-End".
            int year = Calendar.getInstance().get(Calendar.YEAR);
            double yearMul = Math.pow(1.20, Math.max(0, year - 2026));
            int ratingRes = score > 200000 * yearMul ? R.string.bench_rating_flagship :
                            score > 130000 * yearMul ? R.string.bench_rating_highend  :
                            score >  75000 * yearMul ? R.string.bench_rating_midrange :
                            score >  35000 * yearMul ? R.string.bench_rating_budget   :
                                                       R.string.bench_rating_entry;
            String rating = getString(ratingRes);

            // X6: format with Locale.US so digits stay ASCII even in Arabic/Hindi locales
            String res = String.format(Locale.US,
                    getString(R.string.bench_complete_format).replace("\\n", "\n"),
                    score, rating, intT, floatT, memT, multiT, cores);

            // Y2: persist this run to history
            Context ctxBench = getContext();
            if (ctxBench != null) AppPrefs.appendBenchHistory(ctxBench, score, rating);

            finishBench(false, res);
        });
    }

    /** Y2: render the last few benchmark runs as a small block, or "" if none yet. */
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

    private void setBenchResult(String text) {
        if (binding != null) binding.tvBenchResult.setText(text);
    }

    /** Batch 7: feeds the score column of AppPrefs.getBenchHistory() (already parsed as
     *  text by renderBenchHistory()) into the trend sparkline. History is newest-first;
     *  the sparkline wants oldest-first (chronological, left → right). */
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

    private void finishBench(boolean cancelled, String res) {
        post(()->{
            if(!isAdded() || binding == null) return;
            if (cancelled) {
                binding.tvBenchResult.setText(R.string.bench_cancelled);
                binding.tvBenchResult.announceForAccessibility(getString(R.string.bench_cancelled)); // Q5
            } else if (res != null) {
                String withHistory = res + renderBenchHistory(); // Y2
                binding.tvBenchResult.setText(withHistory);
                binding.tvBenchResult.announceForAccessibility(res); // Q5 — read result, not history
                updateBenchSparkline(); // Batch 7
            }
            binding.pbBenchmark.setVisibility(View.GONE);
            binding.btnBenchmark.setText(R.string.system_btn_run_benchmark);
            benchRunning = false;
        });
    }

    private long now(){return System.currentTimeMillis();}
    private void post(Runnable r){if(isAdded()) handler.post(r);}
}
