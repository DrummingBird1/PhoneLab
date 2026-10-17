package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.sensolab.devicemonitor.databinding.FragmentToolsBinding;
import java.util.Locale;

/**
 * Sensor-powered tools: bubble level, compass calibration helper, magnetometer metal
 * detector, and a vibration seismograph with FFT. Only the selected tool registers its
 * sensor, and only while the tab is resumed.
 */
public class ToolsFragment extends Fragment implements SensorEventListener {

    private static final int TOOL_LEVEL = 0, TOOL_COMPASS = 1, TOOL_METAL = 2, TOOL_VIBRATION = 3;
    private static final String STATE_TOOL = "tool";
    private static final String KEY_LEVEL_ROLL = "level_zero_roll", KEY_LEVEL_PITCH = "level_zero_pitch";

    private static final float METAL_GAUGE_MAX = 200f;   // µT above baseline
    private static final float METAL_BEEP_DELTA = 30f;
    private static final long METAL_BEEP_GAP_MS = 350L;
    private static final int FFT_SIZE = 256;
    private static final long STATS_INTERVAL_MS = 250L;

    private FragmentToolsBinding binding;
    private SensorManager sm;
    private int tool = TOOL_LEVEL;

    // Level
    private float rawRoll, rawPitch;
    // Metal detector
    private float metalBaseline = Float.NaN;
    private ToneGenerator tone;
    private long lastBeepMs;
    // Vibration
    private final float[] vibSamples = new float[FFT_SIZE];
    private final long[] vibTimes = new long[FFT_SIZE];
    private int vibCount = 0, vibHead = 0;
    private float vibPeak = 0f;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            updateVibrationStats();
            ui.postDelayed(this, STATS_INTERVAL_MS);
        }
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentToolsBinding.inflate(inf, c, false);
        sm = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (s != null) tool = s.getInt(STATE_TOOL, TOOL_LEVEL);
        binding.toolChips.check(chipFor(tool));
        binding.toolChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            selectTool(toolFor(ids.get(0)));
        });
        binding.btnLevelZero.setOnClickListener(v -> saveLevelZero(rawRoll, rawPitch));
        binding.btnLevelReset.setOnClickListener(v -> saveLevelZero(0f, 0f));
        binding.btnMetalZero.setOnClickListener(v -> metalBaseline = Float.NaN);
        binding.btnVibReset.setOnClickListener(v -> resetVibration());
        binding.gaugeMetal.setRange(0f, METAL_GAUGE_MAX);
        binding.gaugeMetal.setBands(METAL_BEEP_DELTA, 80f);
        showPanel(tool);
        return binding.getRoot();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TOOL, tool);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override public void onResume() {
        super.onResume();
        startTool();
    }

    @Override public void onPause() {
        super.onPause();
        stopTool();
    }

    private void selectTool(int newTool) {
        if (newTool == tool) return;
        stopTool();
        tool = newTool;
        showPanel(tool);
        if (isResumed()) startTool();
    }

    private void showPanel(int t) {
        binding.panelLevel.setVisibility(t == TOOL_LEVEL ? View.VISIBLE : View.GONE);
        binding.panelCompass.setVisibility(t == TOOL_COMPASS ? View.VISIBLE : View.GONE);
        binding.panelMetal.setVisibility(t == TOOL_METAL ? View.VISIBLE : View.GONE);
        binding.panelVibration.setVisibility(t == TOOL_VIBRATION ? View.VISIBLE : View.GONE);
        binding.tvToolMissing.setVisibility(View.GONE);
    }

    private void startTool() {
        Sensor s;
        int rate = SensorManager.SENSOR_DELAY_UI;
        switch (tool) {
            case TOOL_LEVEL:
                s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
                if (s == null) s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                break;
            case TOOL_COMPASS:
            case TOOL_METAL:
                s = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                rate = SensorManager.SENSOR_DELAY_GAME;
                break;
            default:
                s = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
                if (s == null) s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                rate = SensorManager.SENSOR_DELAY_FASTEST;
                break;
        }
        if (s == null) {
            binding.tvToolMissing.setText(R.string.tool_sensor_missing);
            binding.tvToolMissing.setVisibility(View.VISIBLE);
            return;
        }
        sm.registerListener(this, s, rate);
        if (tool == TOOL_METAL && tone == null) {
            try { tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60); }
            catch (RuntimeException ignored) { tone = null; } // no audio routing available
        }
        if (tool == TOOL_VIBRATION) ui.post(statsTick);
        if (tool == TOOL_COMPASS) binding.figureEight.setAnimating(true);
    }

    private void stopTool() {
        if (sm != null) sm.unregisterListener(this);
        ui.removeCallbacks(statsTick);
        if (tone != null) { tone.release(); tone = null; }
    }

    // ── Sensor events ─────────────────────────────────────────────────────
    @Override public void onSensorChanged(SensorEvent ev) {
        if (binding == null || ev.values == null || ev.values.length < 3) return;
        switch (tool) {
            case TOOL_LEVEL:     onLevel(ev.values); break;
            case TOOL_COMPASS:   onCompass(ev.values, ev.accuracy); break;
            case TOOL_METAL:     onMetal(ev.values); break;
            case TOOL_VIBRATION: onVibration(ev); break;
            default: break;
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (tool == TOOL_COMPASS && binding != null && sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            showAccuracy(accuracy);
        }
    }

    private void onLevel(float[] g) {
        // At rest the accelerometer reads "up" in device axes; right edge down → x < 0.
        rawRoll  = (float) Math.toDegrees(Math.atan2(-g[0], g[2]));
        rawPitch = (float) Math.toDegrees(Math.atan2(-g[1], g[2]));
        SharedPreferences sp = AppPrefs.get(requireContext());
        float roll  = rawRoll  - sp.getFloat(KEY_LEVEL_ROLL, 0f);
        float pitch = rawPitch - sp.getFloat(KEY_LEVEL_PITCH, 0f);
        binding.levelView.setTilt(roll, pitch);
        String angles = String.format(Locale.US, "Roll %+.1f°  Pitch %+.1f°", roll, pitch);
        if (Math.abs(roll) <= 1f && Math.abs(pitch) <= 1f) angles += "  ✓";
        binding.tvLevelAngles.setText(angles);
    }

    private void saveLevelZero(float roll, float pitch) {
        AppPrefs.get(requireContext()).edit()
                .putFloat(KEY_LEVEL_ROLL, roll)
                .putFloat(KEY_LEVEL_PITCH, pitch)
                .apply();
    }

    private void onCompass(float[] b, int accuracy) {
        showAccuracy(accuracy);
        float mag = SensorReadouts.mag3(b[0], b[1], b[2]);
        // Earth's field is roughly 25–65 µT; anything outside means nearby metal/magnets.
        boolean normal = mag >= 25f && mag <= 65f;
        binding.tvCompassField.setText(getString(R.string.compass_field_fmt,
                String.format(Locale.US, "%.1f", mag),
                getString(normal ? R.string.compass_field_normal : R.string.compass_field_interference)));
    }

    private void showAccuracy(int accuracy) {
        int textRes, colorRes;
        switch (accuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:   textRes = R.string.compass_acc_high;   colorRes = R.color.gauge_ok;   break;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM: textRes = R.string.compass_acc_medium; colorRes = R.color.gauge_warn; break;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:    textRes = R.string.compass_acc_low;    colorRes = R.color.gauge_hot;  break;
            default:                                          textRes = R.string.compass_acc_unreliable; colorRes = R.color.gauge_hot; break;
        }
        binding.tvCompassAccuracy.setText(textRes);
        binding.tvCompassAccuracy.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        binding.figureEight.setAnimating(accuracy != SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
    }

    private void onMetal(float[] b) {
        float mag = SensorReadouts.mag3(b[0], b[1], b[2]);
        if (Float.isNaN(metalBaseline)) metalBaseline = mag;
        float delta = Math.max(0f, mag - metalBaseline);
        binding.gaugeMetal.setReading(delta, String.format(Locale.US, "%.0f", delta), "µT");
        binding.tvMetalField.setText(getString(R.string.metal_field_fmt,
                String.format(Locale.US, "%.1f", mag), String.format(Locale.US, "%.1f", metalBaseline)));
        if (tone != null && binding.switchMetalBeep.isChecked() && delta >= METAL_BEEP_DELTA) {
            long now = SystemClock.uptimeMillis();
            // Beep faster the stronger the signal, like a real detector.
            long gap = (long) (METAL_BEEP_GAP_MS * Math.max(0.25f, 1f - delta / METAL_GAUGE_MAX));
            if (now - lastBeepMs >= gap) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60);
                lastBeepMs = now;
            }
        }
    }

    private void onVibration(SensorEvent ev) {
        float[] v = ev.values;
        float sample = ev.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION
                ? SensorReadouts.mag3(v[0], v[1], v[2])
                : SensorReadouts.mag3(v[0], v[1], v[2]) - SensorManager.GRAVITY_EARTH;
        vibSamples[vibHead] = sample;
        vibTimes[vibHead] = ev.timestamp;
        vibHead = (vibHead + 1) % FFT_SIZE;
        if (vibCount < FFT_SIZE) vibCount++;
        vibPeak = Math.max(vibPeak, Math.abs(sample));
        binding.seismograph.push(sample);
        binding.seismograph.invalidate();
    }

    private void updateVibrationStats() {
        if (binding == null) return;
        binding.cardVibPeak.setValue(String.format(Locale.US, "%.2f", vibPeak), "m/s²");
        if (vibCount < 16) {
            binding.cardVibRms.setValue(getString(R.string.value_placeholder), null);
            binding.cardVibFreq.setValue(getString(R.string.value_placeholder), null);
            return;
        }
        int start = (vibCount < FFT_SIZE) ? 0 : vibHead;
        double sumSq = 0;
        for (int i = 0; i < vibCount; i++) {
            float s = vibSamples[(start + i) % FFT_SIZE];
            sumSq += s * s;
        }
        binding.cardVibRms.setValue(String.format(Locale.US, "%.2f", Math.sqrt(sumSq / vibCount)), "m/s²");
        if (vibCount == FFT_SIZE) {
            long spanNs = vibTimes[(start + FFT_SIZE - 1) % FFT_SIZE] - vibTimes[start];
            if (spanNs > 0) {
                float rateHz = (FFT_SIZE - 1) / (spanNs / 1e9f);
                float[] ordered = new float[FFT_SIZE];
                for (int i = 0; i < FFT_SIZE; i++) ordered[i] = vibSamples[(start + i) % FFT_SIZE];
                float hz = Fft.dominantFrequency(ordered, rateHz);
                binding.cardVibFreq.setValue(String.format(Locale.US, "%.1f", hz), "Hz");
                binding.cardVibFreq.setDetail(String.format(Locale.US, "%.0f Hz sampling", rateHz));
            }
        }
    }

    private void resetVibration() {
        vibCount = 0;
        vibHead = 0;
        vibPeak = 0f;
        binding.seismograph.clear();
    }

    // ── Chip ↔ tool mapping ───────────────────────────────────────────────
    private static int chipFor(int t) {
        switch (t) {
            case TOOL_COMPASS:   return R.id.chip_compass;
            case TOOL_METAL:     return R.id.chip_metal;
            case TOOL_VIBRATION: return R.id.chip_vibration;
            default:             return R.id.chip_level;
        }
    }

    private static int toolFor(int chipId) {
        if (chipId == R.id.chip_compass)   return TOOL_COMPASS;
        if (chipId == R.id.chip_metal)     return TOOL_METAL;
        if (chipId == R.id.chip_vibration) return TOOL_VIBRATION;
        return TOOL_LEVEL;
    }
}
