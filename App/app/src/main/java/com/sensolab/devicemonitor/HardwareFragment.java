package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.nfc.NfcAdapter;
import android.os.BatteryManager;
import android.os.Build;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Hardware info tab (v1.2.0). Pulls live data:
 *   • Per-core CPU current frequency from /sys/devices/system/cpu/cpu*​/cpufreq/scaling_cur_freq
 *   • Battery capacity / current / energy / cycle count via BatteryManager
 *   • Display modes (refresh rates), HDR types
 *   • Capability flags: NFC, USB host, Biometric, Bluetooth, Camera count
 *
 * All file I/O is on a background executor. Refresh cadence: 2 seconds.
 *
 * <p><b>Z5 note:</b> deliberately does NOT extend {@link ModeAwareFragment} — there is no
 * "visual" variant of this tab; the data is too dense to render as gauges/bars.
 * If a visual mode is ever added, switch to ModeAwareFragment and bind two layouts.
 */
public class HardwareFragment extends Fragment {

    private static final long POLL_MS = 2000L;

    private FragmentHardwareBinding binding;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExecutorService io;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHardwareBinding.inflate(inflater, container, false);
        // Z4: cache capabilities — static info, render once
        if (cachedCaps == null) cachedCaps = renderCapabilities(requireContext());
        binding.tvHwCaps.setText(cachedCaps);
        // X2: display info updates per poll (refresh rate can change at runtime)
        binding.tvHwDisplay.setText(renderDisplay(requireContext()));
        return binding.getRoot();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Z4: capabilities are static — cache across view recreations */
    private static String cachedCaps;

    @Override public void onResume() {
        super.onResume();
        if (io == null || io.isShutdown()) io = NamedThreads.singleThread("Hw"); // Z1
        ui.post(poller);
    }

    @Override public void onPause() {
        super.onPause();
        ui.removeCallbacksAndMessages(null);
        if (io != null) { io.shutdownNow(); io = null; }
    }

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (!isAdded()) return;
            final ExecutorService ex = io;
            if (ex != null && !ex.isShutdown()) {
                ex.execute(() -> {
                    final String cpuText = readCpuFrequencies();
                    final Context c = getContext();
                    final String battText = renderBattery(c);
                    final String dispText = c == null ? "" : renderDisplay(c); // X2
                    ui.post(() -> {
                        if (!isAdded() || binding == null) return;
                        binding.tvHwCpu.setText(cpuText);
                        binding.tvHwBattery.setText(battText);
                        if (!dispText.isEmpty()) binding.tvHwDisplay.setText(dispText);
                    });
                });
            }
            ui.postDelayed(this, POLL_MS);
        }
    };

    // ── CPU per-core frequency ───────────────────────────────────────────
    private String readCpuFrequencies() {
        int cores = Runtime.getRuntime().availableProcessors();
        StringBuilder sb = new StringBuilder(256);
        for (int i = 0; i < cores; i++) {
            sb.append(getString(R.string.hw_cpu_core, i)).append("  ");
            long cur = readKHz("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            long max = readKHz("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (cur > 0) {
                sb.append(String.format(Locale.US, "%.2f GHz", cur / 1_000_000.0));
                if (max > 0) sb.append(String.format(Locale.US, " / %.2f GHz max", max / 1_000_000.0));
            } else {
                sb.append("—");
            }
            if (i < cores - 1) sb.append("\n");
        }
        return sb.toString();
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

    // ── Battery details ──────────────────────────────────────────────────
    private static String renderBattery(Context ctx) {
        if (ctx == null) return "—";
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return ctx.getString(R.string.status_not_available);

        StringBuilder sb = new StringBuilder();
        int capacity      = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        int chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        int currentNow    = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long energyCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);

        sb.append(ctx.getString(R.string.hw_batt_level)).append("  ")
          .append(capacity).append("%\n");

        if (chargeCounter != Integer.MIN_VALUE) {
            sb.append(ctx.getString(R.string.hw_batt_charge_counter)).append("  ")
              .append(chargeCounter / 1000).append(" mAh\n");
        }
        if (currentNow != Integer.MIN_VALUE) {
            // Current is reported in microA. Sign depends on OEM (some flip).
            double mA = currentNow / 1000.0;
            sb.append(ctx.getString(R.string.hw_batt_current))
              .append("  ").append(String.format(Locale.US, "%+.0f mA\n", mA));
        }
        if (energyCounter != Long.MIN_VALUE && energyCounter != 0) {
            // Reported in nanowatt-hours
            sb.append(ctx.getString(R.string.hw_batt_energy))
              .append("  ").append(String.format(Locale.US, "%.0f mWh\n",
                      energyCounter / 1_000_000.0));
        }
        // Cycle count requires API 34+ and isn't always populated
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // BATTERY_PROPERTY_CYCLE_COUNT is not a public constant; use EXTRA_CYCLE_COUNT from sticky intent
            Intent intent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent != null) {
                int cycles = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
                if (cycles >= 0) {
                    sb.append(ctx.getString(R.string.hw_batt_cycles)).append("  ").append(cycles);
                }
            }
        }
        return sb.toString().trim();
    }

    // ── Display ──────────────────────────────────────────────────────────
    private static String renderDisplay(Context ctx) {
        DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (d == null) return ctx.getString(R.string.status_not_available);
        StringBuilder sb = new StringBuilder();
        Display.Mode cur = d.getMode();
        sb.append(ctx.getString(R.string.hw_disp_current)).append("  ")
          .append(cur.getPhysicalWidth()).append("×").append(cur.getPhysicalHeight())
          .append(" @ ").append(String.format(Locale.US, "%.0f Hz", cur.getRefreshRate())).append("\n");

        Display.Mode[] modes = d.getSupportedModes();
        sb.append(ctx.getString(R.string.hw_disp_supported)).append("  ");
        for (int i = 0; i < modes.length; i++) {
            sb.append(String.format(Locale.US, "%.0fHz", modes[i].getRefreshRate()));
            if (i < modes.length - 1) sb.append(", ");
        }
        sb.append("\n");

        Display.HdrCapabilities hdr = d.getHdrCapabilities();
        int[] types = (hdr != null) ? hdr.getSupportedHdrTypes() : null;
        sb.append(ctx.getString(R.string.hw_disp_hdr)).append("  ");
        if (types == null || types.length == 0) {
            sb.append(ctx.getString(R.string.hw_disp_none));
        } else {
            for (int i = 0; i < types.length; i++) {
                sb.append(hdrTypeName(types[i]));
                if (i < types.length - 1) sb.append(", ");
            }
        }
        return sb.toString();
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

    // ── Capabilities (static info) ───────────────────────────────────────
    private static String renderCapabilities(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        StringBuilder sb = new StringBuilder();

        boolean nfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
                && NfcAdapter.getDefaultAdapter(ctx) != null;
        boolean nfcEnabled = nfc && NfcAdapter.getDefaultAdapter(ctx).isEnabled();
        sb.append(ctx.getString(R.string.hw_cap_nfc)).append("  ")
          .append(nfc ? (nfcEnabled ? ctx.getString(R.string.hw_cap_yes_on)
                                    : ctx.getString(R.string.hw_cap_yes_off))
                      : ctx.getString(R.string.hw_cap_no))
          .append("\n");

        boolean usbHost = pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST);
        sb.append(ctx.getString(R.string.hw_cap_usb_host)).append("  ")
          .append(usbHost ? ctx.getString(R.string.hw_cap_yes) : ctx.getString(R.string.hw_cap_no))
          .append("\n");

        boolean bluetooth = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        boolean ble       = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        sb.append(ctx.getString(R.string.hw_cap_bluetooth)).append("  ")
          .append(bluetooth ? ctx.getString(R.string.hw_cap_yes) : ctx.getString(R.string.hw_cap_no))
          .append(ble ? " (BLE)" : "")
          .append("\n");

        int cams = 0;
        try {
            android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) cams = cm.getCameraIdList().length;
        } catch (Exception ignored) {}
        sb.append(ctx.getString(R.string.hw_cap_cameras)).append("  ").append(cams).append("\n");

        // Z3: BiometricManager throws on some OEM builds with broken Keystore
        String bioStatus = "?";
        try {
            BiometricManager bm = BiometricManager.from(ctx);
            int canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                                           | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            switch (canAuth) {
                case BiometricManager.BIOMETRIC_SUCCESS:               bioStatus = ctx.getString(R.string.hw_cap_yes_enrolled); break;
                case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:   bioStatus = ctx.getString(R.string.hw_cap_yes_not_enrolled); break;
                case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:     bioStatus = ctx.getString(R.string.hw_cap_no); break;
                default: /* keep "?" */ break;
            }
        } catch (Throwable ignored) {
            // Keep "?" — better than crashing the whole Hardware tab.
        }
        sb.append(ctx.getString(R.string.hw_cap_biometric)).append("  ").append(bioStatus);

        return sb.toString();
    }
}
