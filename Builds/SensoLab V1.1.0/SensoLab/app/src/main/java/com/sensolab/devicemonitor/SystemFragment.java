package com.sensolab.devicemonitor;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class SystemFragment extends Fragment {

    // Views — textual
    private LinearLayout layoutTextual, layoutVisual;
    private TextView tvBattTemp, tvCpuTemp, tvGpuTemp, tvSkinTemp,
                     tvModemTemp, tvWifiTemp, tvChargerTemp, tvNpuTemp;
    private TextView tvDevice, tvAndroid, tvCpu, tvRam, tvStorage, tvScreen, tvAbi;
    private TextView tvBenchResult, tvExportStatus;
    private ProgressBar pbBench;
    private Button btnBench, btnExport;

    // Views — visual
    private TextView visBattTemp, visCpuTemp, visGpuTemp, visSkinTemp;
    private TextView visDevice, visRam, visStorage;
    private ProgressBar pbRam, pbStorage;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private boolean benchRunning = false;

    private BroadcastReceiver battReceiver;
    private boolean battRegistered = false;

    // Cached spec strings for export
    private String cachedDevice="", cachedAndroid="", cachedCpu="",
                   cachedRam="", cachedStorage="", cachedScreen="", cachedAbi="";

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup c, @Nullable Bundle s) {
        View v = inf.inflate(R.layout.fragment_system, c, false);
        bindViews(v);
        populateSpecs();
        setupBattery();
        btnBench.setOnClickListener(x -> runBenchmark());
        btnExport.setOnClickListener(x -> exportSpecs());
        return v;
    }

    private void bindViews(View v) {
        layoutTextual = v.findViewById(R.id.sys_layout_textual);
        layoutVisual  = v.findViewById(R.id.sys_layout_visual);
        tvBattTemp    = v.findViewById(R.id.tv_battery_temp);
        tvCpuTemp     = v.findViewById(R.id.tv_cpu_temp);
        tvGpuTemp     = v.findViewById(R.id.tv_gpu_temp);
        tvSkinTemp    = v.findViewById(R.id.tv_skin_temp);
        tvModemTemp   = v.findViewById(R.id.tv_modem_temp);
        tvWifiTemp    = v.findViewById(R.id.tv_wifi_temp);
        tvChargerTemp = v.findViewById(R.id.tv_charger_temp);
        tvNpuTemp     = v.findViewById(R.id.tv_npu_temp);
        tvDevice      = v.findViewById(R.id.tv_device_name);
        tvAndroid     = v.findViewById(R.id.tv_android);
        tvCpu         = v.findViewById(R.id.tv_cpu);
        tvRam         = v.findViewById(R.id.tv_ram);
        tvStorage     = v.findViewById(R.id.tv_storage);
        tvScreen      = v.findViewById(R.id.tv_screen);
        tvAbi         = v.findViewById(R.id.tv_abi);
        tvBenchResult = v.findViewById(R.id.tv_bench_result);
        tvExportStatus= v.findViewById(R.id.tv_export_status);
        pbBench       = v.findViewById(R.id.pb_benchmark);
        btnBench      = v.findViewById(R.id.btn_benchmark);
        btnExport     = v.findViewById(R.id.btn_export);
        visBattTemp   = v.findViewById(R.id.vis_batt_temp);
        visCpuTemp    = v.findViewById(R.id.vis_cpu_temp);
        visGpuTemp    = v.findViewById(R.id.vis_gpu_temp);
        visSkinTemp   = v.findViewById(R.id.vis_skin_temp);
        visDevice     = v.findViewById(R.id.vis_device);
        visRam        = v.findViewById(R.id.vis_ram);
        visStorage    = v.findViewById(R.id.vis_storage);
        pbRam         = v.findViewById(R.id.pb_vis_ram);
        pbStorage     = v.findViewById(R.id.pb_vis_storage);
    }

    @Override public void onResume() {
        super.onResume();
        applyMode();
        registerBattery();
        startTempPolling();
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

    private void applyMode() {
        boolean vis = AppPrefs.isVisual(requireContext());
        layoutTextual.setVisibility(vis ? View.GONE : View.VISIBLE);
        layoutVisual .setVisibility(vis ? View.VISIBLE : View.GONE);
    }

    // ── Battery ───────────────────────────────────────────────────────────
    private void setupBattery() {
        battReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                if (!isAdded()) return;
                int raw  = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                float c  = raw / 10.0f;
                int lv   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int sc   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int pct  = sc > 0 ? lv * 100 / sc : 0;
                int st   = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                int plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                boolean chg = st==BatteryManager.BATTERY_STATUS_CHARGING
                           || st==BatteryManager.BATTERY_STATUS_FULL;
                String ps = plug==BatteryManager.BATTERY_PLUGGED_USB?"USB":
                            plug==BatteryManager.BATTERY_PLUGGED_AC?"AC":
                            plug==BatteryManager.BATTERY_PLUGGED_WIRELESS?"Wireless":"";
                Context c2 = requireContext();
                float d = AppPrefs.temp(c, c2); String u = AppPrefs.tempUnit(c2);
                String l = led(c);
                String chgS = chg ? " ⚡"+ps : "";
                set(tvBattTemp, String.format(Locale.US,"🔋 Battery\n%.1f%s %s\n%d%%%s",d,u,l,pct,chgS));
                set(visBattTemp, String.format(Locale.US,"%s\n%.1f%s\n%d%%",l,d,u,pct));
            }
        };
    }

    private void registerBattery() {
        if (!battRegistered && isAdded()) {
            requireActivity().registerReceiver(battReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            battRegistered = true;
        }
    }
    private void unregisterBattery() {
        if (battRegistered && isAdded()) {
            try { requireActivity().unregisterReceiver(battReceiver); } catch(Exception e){}
            battRegistered = false;
        }
    }

    // ── Thermal polling ───────────────────────────────────────────────────
    private void startTempPolling() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable(){
            @Override public void run(){
                if(!isAdded()) return;
                updateTemps();
                handler.postDelayed(this, 2000);
            }
        }, 200);
    }

    private void updateTemps() {
        Context ctx = requireContext();
        float cpu   = zone("cpu","cpu0","soc_thermal","mtktscpu","cpu_thermal","tsens_tz_sensor0","cpu_0");
        float gpu   = zone("gpu","gpu0","mtktsgpu","gpu_thermal","tsens_tz_sensor10","kgsl_therm");
        float skin  = zone("skin","skin_therm","quiet_therm","back_therm","bd_therm","xo_therm");
        float modem = zone("mdm","modem","pa","lte_pa","5g_pa","mmw_pa","nr_pa");
        float wifi  = zone("wifi","wlan","wlan_therm","bt_therm","wcn","wcnss");
        float chgr  = zone("charger","chg","usb","usb_therm","bms","bq_bms");
        float npu   = zone("npu","apu","ipa","nsp","cdsp","hexagon","adsp");

        setTemp(tvCpuTemp,  visCpuTemp,  "🔥 CPU",       cpu,   ctx);
        setTemp(tvGpuTemp,  visGpuTemp,  "🎮 GPU",       gpu,   ctx);
        setTemp(tvSkinTemp, visSkinTemp, "📱 Skin",      skin,  ctx);
        setTemp(tvModemTemp, null,        "📡 Modem/5G", modem, ctx);
        setTemp(tvWifiTemp,  null,        "📶 WiFi/BT",  wifi,  ctx);
        setTemp(tvChargerTemp,null,       "🔌 Charger",  chgr,  ctx);
        setTemp(tvNpuTemp,   null,        "🧠 NPU/AI",   npu,   ctx);
    }

    private void setTemp(TextView tv, TextView vis, String label, float celsius, Context ctx) {
        if (!isAdded() || tv == null) return;
        if (celsius > 0) {
            float d=AppPrefs.temp(celsius,ctx); String u=AppPrefs.tempUnit(ctx); String l=led(celsius);
            tv.setText(String.format(Locale.US,"%s\n%.1f%s %s",label,d,u,l));
            if(vis!=null) vis.setText(String.format(Locale.US,"%s\n%.1f%s",l,d,u));
        } else {
            tv.setText(label+"\nN/A");
            if(vis!=null) vis.setText("--\nN/A");
        }
    }

    private float zone(String... kw) {
        File dir = new File("/sys/class/thermal");
        if (!dir.exists()) return -1;
        File[] files = dir.listFiles();
        if (files == null) return -1;
        List<Float> hits = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().startsWith("thermal_zone")) continue;
            try {
                String type = readLine(new File(f,"type")).toLowerCase(Locale.US);
                for (String k : kw) {
                    if (type.contains(k.toLowerCase(Locale.US))) {
                        float raw=Float.parseFloat(readLine(new File(f,"temp")).trim());
                        float c=raw>1000?raw/1000f:raw;
                        if(c>0&&c<200){hits.add(c);break;}
                    }
                }
            } catch (Exception ignored) {}
        }
        if (hits.isEmpty()) return -1;
        float sum=0; for(float f:hits) sum+=f;
        return sum/hits.size();
    }

    private String readLine(File f) throws IOException {
        try(BufferedReader br=new BufferedReader(new FileReader(f))){
            String s=br.readLine(); return s!=null?s.trim():"";
        }
    }

    private String led(float c){return c>65?"🔴":c>45?"🟡":"🟢";}

    // ── Specs ─────────────────────────────────────────────────────────────
    private void populateSpecs() {
        if (!isAdded()) return;
        String brand=cap(Build.MANUFACTURER), model=Build.MODEL;
        cachedDevice = brand+" "+model;
        set(tvDevice,"📱 Device\n"+cachedDevice);
        set(visDevice,"📱\n"+brand+"\n"+model);

        cachedAndroid = "Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")";
        set(tvAndroid,"🤖 "+cachedAndroid);

        int cores=Runtime.getRuntime().availableProcessors();
        cachedCpu = getCpuInfo()+" · "+cores+" cores";
        set(tvCpu,"⚡ CPU\n"+cachedCpu);

        ActivityManager am=(ActivityManager)requireActivity().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long tMB=mi.totalMem/(1024*1024), fMB=mi.availMem/(1024*1024);
        int pct=(int)((tMB-fMB)*100/Math.max(tMB,1));
        cachedRam=tMB+" MB total | "+fMB+" MB free | "+pct+"% used";
        set(tvRam,"💾 RAM\n"+cachedRam);
        set(visRam,String.format(Locale.US,"💾\n%dMB free\n%d%%",fMB,pct));
        if(pbRam!=null) pbRam.setProgress(pct);

        try {
            android.os.StatFs sf=new android.os.StatFs(
                    android.os.Environment.getDataDirectory().getPath());
            long tB=sf.getBlockCountLong()*sf.getBlockSizeLong();
            long fB=sf.getAvailableBlocksLong()*sf.getBlockSizeLong();
            long tG=tB/(1024*1024*1024), fG=fB/(1024*1024*1024);
            int sp=(int)((tB-fB)*100/Math.max(tB,1));
            cachedStorage=tG+" GB total | "+fG+" GB free | "+sp+"% used";
            set(tvStorage,"🗂 Storage\n"+cachedStorage);
            set(visStorage,String.format(Locale.US,"🗂\n%dGB free\n%d%%",fG,sp));
            if(pbStorage!=null) pbStorage.setProgress(sp);
        } catch(Exception e){set(tvStorage,"🗂 Storage\nN/A");}

        DisplayMetrics dm=new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        float diag=(float)Math.sqrt(Math.pow(dm.widthPixels/dm.xdpi,2)+Math.pow(dm.heightPixels/dm.ydpi,2));
        cachedScreen=dm.widthPixels+"×"+dm.heightPixels+"px | "+(int)dm.xdpi+" dpi | "+String.format(Locale.US,"%.1f\"",diag);
        set(tvScreen,"📺 Screen\n"+cachedScreen);

        String[] abis=Build.SUPPORTED_ABIS;
        cachedAbi=(abis!=null&&abis.length>0)?String.join(", ",abis):Build.CPU_ABI;
        set(tvAbi,"🔧 ABI: "+cachedAbi);
    }

    private String getCpuInfo() {
        try(BufferedReader br=new BufferedReader(new FileReader("/proc/cpuinfo"))){
            String line;
            while((line=br.readLine())!=null)
                for(String tag:new String[]{"Hardware","model name","Processor"})
                    if(line.startsWith(tag)){String[]p=line.split(":",2);if(p.length>1&&!p[1].trim().isEmpty())return p[1].trim();}
        } catch(Exception ignored){}
        return Build.HARDWARE;
    }

    private String cap(String s){
        return(s==null||s.isEmpty())?s:s.substring(0,1).toUpperCase(Locale.US)+s.substring(1);
    }
    private void set(TextView tv,String t){if(tv!=null)tv.setText(t);}

    // ── Export ────────────────────────────────────────────────────────────
    private void exportSpecs() {
        if (!isAdded()) return;
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = "SensoLab_Specs_" + ts + ".txt";

            // Write to app-specific external docs dir (no permission needed on API 29+)
            File dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = requireContext().getCacheDir();
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, filename);

            // Build content
            StringBuilder sb = new StringBuilder();
            sb.append("══════════════════════════════\n");
            sb.append("  SensoLab v1.1.0 — System Spec Report\n");
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
                {zone("cpu","cpu0","soc_thermal")},
                {zone("gpu","gpu0","mtktsgpu")},
                {zone("skin","skin_therm","quiet_therm")},
                {zone("mdm","modem","pa","lte_pa")},
                {zone("wifi","wlan","wlan_therm")},
                {zone("charger","chg","usb")},
                {zone("npu","apu","ipa","nsp","cdsp")},
            };
            String[] tlabels = {"CPU","GPU","Skin","Modem","WiFi/BT","Charger","NPU"};
            for (int i=0;i<tlabels.length;i++) {
                float c=temps[i][0];
                if(c>0) sb.append(String.format(Locale.US,"  %-10s %.1f%s\n",
                        tlabels[i]+":", AppPrefs.temp(c,ctx), AppPrefs.tempUnit(ctx)));
            }
            sb.append("\n──────────────────────────────\n");
            sb.append("Generated by SensoLab v1.1.0\n");
            sb.append("com.sensolab.devicemonitor\n");

            try(FileWriter fw=new FileWriter(out)){fw.write(sb.toString());}

            // Share via intent
            android.net.Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName()+".fileprovider", out);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "SensoLab System Specs");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Export / Share Specs"));

            set(tvExportStatus, "✅ Exported: "+filename+"\nSaved to Documents/");

        } catch (Exception e) {
            set(tvExportStatus, "❌ Export failed: "+e.getMessage());
        }
    }

    // ── Benchmark ─────────────────────────────────────────────────────────
    private void runBenchmark() {
        if (benchRunning) return;
        benchRunning = true;
        if (executor != null) executor.shutdown();
        executor = Executors.newSingleThreadExecutor();
        btnBench.setEnabled(false);
        btnBench.setText("Running…");
        pbBench.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            post(()->set(tvBenchResult,"⚙️ Phase 1/4  Integer…"));
            long t=now();long s=0;
            for(long i=0;i<60_000_000L;i++) s^=i*2654435761L;
            long intT=now()-t;

            post(()->set(tvBenchResult,"⚙️ Phase 2/4  Float/Trig…"));
            t=now();double d=0;
            for(int i=1;i<6_000_000;i++) d+=Math.sqrt(i)*Math.sin(i*0.001);
            long floatT=now()-t;

            post(()->set(tvBenchResult,"⚙️ Phase 3/4  Memory (16 MB)…"));
            t=now();
            byte[]buf=new byte[16*1024*1024];
            for(int i=0;i<buf.length;i++) buf[i]=(byte)i;
            long chk=0;for(byte b:buf) chk+=b;
            long memT=now()-t;

            post(()->set(tvBenchResult,"⚙️ Phase 4/4  Multi-core…"));
            int cores=Runtime.getRuntime().availableProcessors();
            Thread[]threads=new Thread[cores];
            t=now();
            for(int c=0;c<cores;c++){
                threads[c]=new Thread(()->{double x=0;for(long i=0;i<25_000_000L;i++) x+=Math.sqrt(i);});
                threads[c].start();
            }
            for(Thread th:threads){try{th.join();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
            long multiT=now()-t;

            long score=(long)(60_000_000.0/Math.max(intT,1)*10+
                             6_000_000.0/Math.max(floatT,1)*32+
                             16384.0/Math.max(memT,1)*8+
                             25_000_000.0*cores/Math.max(multiT,1)*12);
            String rating=score>200000?"🏆 Flagship":score>130000?"🥇 High-End":
                          score>75000?"🥈 Mid-Range":score>35000?"🥉 Budget":"⚡ Entry Level";

            String res=String.format(Locale.US,
                    "✅ Benchmark Complete!\n\nScore: %,d pts\n%s\n\n"+
                    "① Integer:    %d ms\n② Float/Trig: %d ms\n"+
                    "③ Memory:     %d ms  (16 MB)\n④ Multi-core: %d ms  (%d cores)",
                    score,rating,intT,floatT,memT,multiT,cores);

            post(()->{
                if(!isAdded()) return;
                set(tvBenchResult,res);
                pbBench.setVisibility(View.GONE);
                btnBench.setEnabled(true);
                btnBench.setText("▶ Run Benchmark");
                benchRunning=false;
            });
        });
    }

    private long now(){return System.currentTimeMillis();}
    private void post(Runnable r){if(isAdded()) handler.post(r);}
}
