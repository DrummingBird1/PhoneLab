package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.fragment.app.Fragment;
import com.sensolab.devicemonitor.databinding.FragmentHardwareBinding;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Hardware tab: battery health (level, health, charging power, estimated capacity, cycles,
 * 24 h history), per-core CPU frequencies, display modes and capability flags.
 * File I/O runs on a background executor; refresh cadence 2 s.
 *
 * <p><b>Z5:</b> deliberately not a {@link ModeAwareFragment} — the data is too dense for a
 * gauge-only visual variant.
 */
public class HardwareFragment extends Fragment {

    private static final long POLL_MS = 2000L;

    private FragmentHardwareBinding binding;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Poller poller = new Poller(ui, POLL_MS, this::poll);
    private ExecutorService io;

    /** Z4: capabilities are static — cached across view recreations. */
    private static String cachedCaps;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        binding = FragmentHardwareBinding.inflate(inflater, container, false);
        if (cachedCaps == null) cachedCaps = renderCapabilities(requireContext());
        binding.cardCaps.setReading(capsSummary(requireContext()), null, cachedCaps);
        showDisplay(requireContext());
        showBatteryHistory();
        return binding.getRoot();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override public void onResume() {
        super.onResume();
        if (io == null || io.isShutdown()) io = NamedThreads.singleThread("Hw"); // Z1
        // Also log a battery sample on open (spaced ≥10 min apart by BatteryHistory itself).
        BatteryHistory.record(requireContext(), BatteryInfo.read(requireContext()));
        poller.start();
    }

    @Override public void onPause() {
        super.onPause();
        poller.stop();
        if (io != null) { io.shutdownNow(); io = null; }
    }

    private void poll() {
        if (!isAdded()) return;
        final ExecutorService ex = io;
        final Context c = getContext();
        if (ex == null || ex.isShutdown() || c == null) return;
        ex.execute(() -> {
            final CpuFreqs freqs = readCpuFrequencies();
            final BatteryInfo batt = BatteryInfo.read(c);
            ui.post(() -> {
                if (!isAdded() || binding == null) return;
                showCpu(freqs);
                showBattery(batt);
                showDisplay(requireContext()); // X2: refresh rate can change at runtime
            });
        });
    }

    // ── Battery ───────────────────────────────────────────────────────────
    private void showBattery(BatteryInfo b) {
        Context c = requireContext();
        String plug = b.plugLabel(c);
        binding.cardBattLevel.setReading(b.levelPct >= 0 ? String.valueOf(b.levelPct) : "—", "%",
                getString(b.statusLabel()) + (plug != null && b.isCharging() ? " · " + plug : "")
                        + (b.technology.isEmpty() ? "" : "\n" + b.technology));

        StringBuilder health = new StringBuilder();
        if (b.cycleCount >= 0) health.append(getString(R.string.hw_batt_cycles_fmt, b.cycleCount));
        if (!Float.isNaN(b.tempC)) {
            if (health.length() > 0) health.append("\n");
            health.append(String.format(Locale.US, "%.1f%s", AppPrefs.temp(b.tempC, c), AppPrefs.tempUnit(c)));
        }
        binding.cardBattHealth.setReading(getString(b.healthLabel()), null, health.toString());

        float w = b.powerW();
        StringBuilder power = new StringBuilder();
        if (!Float.isNaN(b.currentMa)) power.append(String.format(Locale.US, "%+.0f mA", b.currentMa));
        if (b.voltageMv > 0) {
            if (power.length() > 0) power.append(" · ");
            power.append(String.format(Locale.US, "%.2f V", b.voltageMv / 1000f));
        }
        if (Float.isNaN(w)) binding.cardBattPower.setStatus(getString(R.string.status_na));
        else binding.cardBattPower.setReading(String.format(Locale.US, "%.1f", w), "W",
                getString(b.statusLabel()) + "\n" + power);

        int est = b.estimatedFullCapacityMah();
        StringBuilder cap = new StringBuilder();
        if (b.isChargeCounterPlausible()) cap.append(getString(R.string.hw_batt_remaining_fmt, b.chargeCounterMah));
        if (b.energyMwh > 0) {
            if (cap.length() > 0) cap.append("\n");
            cap.append(String.format(Locale.US, "%d mWh", b.energyMwh));
        }
        if (est > 0) binding.cardBattCapacity.setReading(String.valueOf(est), "mAh",
                getString(R.string.hw_batt_capacity_est) + (cap.length() > 0 ? "\n" + cap : ""));
        else if (cap.length() > 0) binding.cardBattCapacity.setReading("—", null, cap.toString());
        else binding.cardBattCapacity.setStatus(getString(R.string.status_na));
    }

    private void showBatteryHistory() {
        Context c = requireContext();
        if (!AppPrefs.isBatteryHistoryEnabled(c)) {
            binding.sparkBattHistory.setValues(new float[0]);
            binding.tvBattHistory.setText(R.string.hw_batt_history_off);
            return;
        }
        List<BatteryHistory.Sample> rows = BatteryHistory.load(c);
        float[] levels = new float[rows.size()];
        for (int i = 0; i < levels.length; i++) levels[i] = rows.get(i).level;
        binding.sparkBattHistory.setValues(levels);
        if (rows.size() < 2) {
            binding.tvBattHistory.setText(R.string.hw_batt_history_empty);
            return;
        }
        int min = 100, max = 0;
        for (BatteryHistory.Sample s : rows) { min = Math.min(min, s.level); max = Math.max(max, s.level); }
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);
        binding.tvBattHistory.setText(getString(R.string.hw_batt_history_summary,
                rows.size(), fmt.format(new Date(rows.get(0).timeMs)), min, max));
    }

    // ── CPU per-core frequency ───────────────────────────────────────────
    private static final class CpuFreqs {
        final long[] cur, max;
        CpuFreqs(int n) { cur = new long[n]; max = new long[n]; }
    }

    private static CpuFreqs readCpuFrequencies() {
        int cores = Runtime.getRuntime().availableProcessors();
        CpuFreqs f = new CpuFreqs(cores);
        for (int i = 0; i < cores; i++) {
            f.cur[i] = readKHz("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            f.max[i] = readKHz("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
        }
        return f;
    }

    private void showCpu(CpuFreqs f) {
        long peak = -1;
        StringBuilder sb = new StringBuilder(256);
        for (int i = 0; i < f.cur.length; i++) {
            sb.append(getString(R.string.hw_cpu_core, i)).append("  ");
            if (f.cur[i] > 0) {
                peak = Math.max(peak, f.cur[i]);
                sb.append(String.format(Locale.US, "%.2f GHz", f.cur[i] / 1_000_000.0));
                // A "max" below the current clock is a bogus driver value (the emulator reports 2 kHz).
                if (f.max[i] >= f.cur[i]) sb.append(String.format(Locale.US, " / %.2f", f.max[i] / 1_000_000.0));
            } else {
                sb.append("—");
            }
            if (i < f.cur.length - 1) sb.append("\n");
        }
        binding.cardCpuFreq.setReading(peak > 0 ? String.format(Locale.US, "%.2f", peak / 1_000_000.0) : "—",
                peak > 0 ? "GHz" : null, sb.toString());
    }

    private static long readKHz(String path) {
        File f = new File(path);
        if (!f.exists() || !f.canRead()) return -1;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            return line == null ? -1 : Long.parseLong(line.trim());
        } catch (IOException | NumberFormatException ignored) {
            return -1;
        }
    }

    // ── Display ──────────────────────────────────────────────────────────
    private void showDisplay(Context ctx) {
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (d == null) {
            binding.cardDisplay.setStatus(getString(R.string.status_not_available));
            return;
        }
        Display.Mode cur = d.getMode();
        StringBuilder sb = new StringBuilder();
        sb.append(cur.getPhysicalWidth()).append("×").append(cur.getPhysicalHeight()).append("\n");
        Display.Mode[] modes = d.getSupportedModes();
        sb.append(getString(R.string.hw_disp_supported)).append("  ");
        for (int i = 0; i < modes.length; i++) {
            sb.append(String.format(Locale.US, "%.0f", modes[i].getRefreshRate()));
            if (i < modes.length - 1) sb.append(", ");
        }
        sb.append(" Hz\n").append(getString(R.string.hw_disp_hdr)).append("  ");
        Display.HdrCapabilities hdr = d.getHdrCapabilities();
        int[] types = (hdr != null) ? hdr.getSupportedHdrTypes() : null;
        if (types == null || types.length == 0) {
            sb.append(getString(R.string.hw_disp_none));
        } else {
            for (int i = 0; i < types.length; i++) {
                sb.append(hdrTypeName(types[i]));
                if (i < types.length - 1) sb.append(", ");
            }
        }
        binding.cardDisplay.setReading(String.format(Locale.US, "%.0f", cur.getRefreshRate()), "Hz", sb.toString());
    }

    private static String hdrTypeName(int t) {
        switch (t) {
            case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION: return "Dolby Vision";
            case Display.HdrCapabilities.HDR_TYPE_HDR10:        return "HDR10";
            case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS:   return "HDR10+";
            case Display.HdrCapabilities.HDR_TYPE_HLG:          return "HLG";
            default: return "type " + t;
        }
    }

    // ── Capabilities (static) ─────────────────────────────────────────────
    private static String capsSummary(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        int n = 0;
        if (pm.hasSystemFeature(PackageManager.FEATURE_NFC)) n++;
        if (pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST)) n++;
        if (pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) n++;
        if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
                || pm.hasSystemFeature(PackageManager.FEATURE_FACE)) n++;
        return n + " / 4";
    }

    private static String renderCapabilities(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        StringBuilder sb = new StringBuilder();

        NfcAdapter nfcAdapter = pm.hasSystemFeature(PackageManager.FEATURE_NFC) ? NfcAdapter.getDefaultAdapter(ctx) : null;
        sb.append(ctx.getString(R.string.hw_cap_nfc)).append("  ")
          .append(nfcAdapter != null ? ctx.getString(nfcAdapter.isEnabled() ? R.string.hw_cap_yes_on : R.string.hw_cap_yes_off)
                                     : ctx.getString(R.string.hw_cap_no))
          .append("\n");

        sb.append(ctx.getString(R.string.hw_cap_usb_host)).append("  ")
          .append(ctx.getString(pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST) ? R.string.hw_cap_yes : R.string.hw_cap_no))
          .append("\n");

        boolean ble = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        sb.append(ctx.getString(R.string.hw_cap_bluetooth)).append("  ")
          .append(ctx.getString(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) ? R.string.hw_cap_yes : R.string.hw_cap_no))
          .append(ble ? " (BLE)" : "")
          .append("\n");

        int cams = 0;
        try {
            android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) cams = cm.getCameraIdList().length;
        } catch (Exception ignored) {}
        sb.append(ctx.getString(R.string.hw_cap_cameras)).append("  ").append(cams).append("\n");

        // Z3: BiometricManager throws on some OEM builds with a broken Keystore
        String bioStatus = "?";
        try {
            int canAuth = BiometricManager.from(ctx).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            switch (canAuth) {
                case BiometricManager.BIOMETRIC_SUCCESS:             bioStatus = ctx.getString(R.string.hw_cap_yes_enrolled); break;
                case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED: bioStatus = ctx.getString(R.string.hw_cap_yes_not_enrolled); break;
                case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:   bioStatus = ctx.getString(R.string.hw_cap_no); break;
                default: break;
            }
        } catch (Throwable ignored) {
            // keep "?" — better than crashing the whole tab
        }
        sb.append(ctx.getString(R.string.hw_cap_biometric)).append("  ").append(bioStatus);
        return sb.toString();
    }
}
