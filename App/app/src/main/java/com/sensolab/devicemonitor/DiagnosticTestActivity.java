package com.sensolab.devicemonitor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.KeyEvent;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sensolab.devicemonitor.DiagnosticsStore.Result;
import com.sensolab.devicemonitor.databinding.ActivityDiagTestBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs one {@link DiagTest} and returns its result. Manual tests ask the user to confirm
 * (heard the tone? felt the vibration?); measurable ones decide on their own (every touch
 * cell covered, both volume keys pressed, proximity covered/uncovered, sensors reporting).
 * The result is saved to {@link DiagnosticsStore} and returned via {@link #EXTRA_RESULT}.
 */
public class DiagnosticTestActivity extends AppCompatActivity implements SensorEventListener {

    public static final String EXTRA_TEST = "test";
    public static final String EXTRA_RESULT = "result";

    private static final int[] DISPLAY_COLORS = {
            Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK, Color.GRAY};
    private static final float MIC_PASS_RANGE_DB = 15f;
    private static final long SENSOR_CHECK_MS = 3000L;

    private ActivityDiagTestBinding binding;
    private DiagTest test;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean finished = false;

    // Test-specific state
    private SensorManager sm;
    private SoundMeter soundMeter;
    private float micMin = Float.NaN, micMax = Float.NaN;
    private boolean volUp, volDown;
    private boolean proximityWasNear, proximityPassed;
    private int displayIndex = -1;
    private int multitouchMax = 0;
    private String torchCameraId;
    private AudioTrack toneTrack;
    private final List<Sensor> sensorsToCheck = new ArrayList<>();
    private final List<Integer> sensorsReported = new ArrayList<>();

    private final ActivityResultLauncher<String> micPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> { if (granted) startMicrophone(); else finishWith(Result.SKIPPED, getString(R.string.diag_note_no_permission)); });

    public static Intent intent(Context ctx, DiagTest test) {
        return new Intent(ctx, DiagnosticTestActivity.class).putExtra(EXTRA_TEST, test.name());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        String name = getIntent().getStringExtra(EXTRA_TEST);
        try { test = DiagTest.valueOf(name == null ? "" : name); }
        catch (IllegalArgumentException e) { test = DiagTest.TOUCH; }
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this); // after super.onCreate() — see MainActivity
        binding = ActivityDiagTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (test.fullscreen) {
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            c.hide(WindowInsetsCompat.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            ViewCompat.setOnApplyWindowInsetsListener(binding.diagRoot, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        binding.tvDiagTitle.setText(test.title);
        binding.btnDiagPass.setOnClickListener(v -> finishWith(Result.PASS, passNote()));
        binding.btnDiagFail.setOnClickListener(v -> finishWith(Result.FAIL, passNote()));
        binding.btnDiagSkip.setOnClickListener(v -> finishWith(Result.SKIPPED, null));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { askOnBack(); }
        });
        setUpTest();
    }

    @Override protected void onPause() {
        super.onPause();
        // Leaving mid-test (home button, call) must not leave the torch, mic or tone running.
        if (!isChangingConfigurations()) releaseHardware();
    }

    @Override protected void onResume() {
        super.onResume();
        if (finished) return;
        if (test == DiagTest.PROXIMITY || test == DiagTest.SENSORS) registerTestSensors();
        if (test == DiagTest.MICROPHONE) maybeStartMicrophone();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        releaseHardware();
        main.removeCallbacksAndMessages(null);
    }

    // ── Per-test setup ────────────────────────────────────────────────────
    private void setUpTest() {
        switch (test) {
            case TOUCH:      setUpTouch(); break;
            case MULTITOUCH: setUpMultitouch(); break;
            case DISPLAY:    setUpDisplay(); break;
            case SPEAKER:    setUpManual(R.string.diag_speaker_instructions, R.string.diag_action_play, this::playTone, true); break;
            case MICROPHONE: setUpMicrophone(); break;
            case VIBRATION:  setUpManual(R.string.diag_vibration_instructions, R.string.diag_action_vibrate, this::vibrate, true); break;
            case BUTTONS:    setUpButtons(); break;
            case PROXIMITY:  setUpProximity(); break;
            case FLASHLIGHT: setUpFlashlight(); break;
            case SENSORS:    setUpSensors(); break;
            case BATTERY:    runBatteryCheck(); break;
        }
    }

    private void setUpTouch() {
        TouchGridView grid = new TouchGridView(this);
        binding.testArea.addView(grid);
        binding.tvDiagInstructions.setText(R.string.diag_touch_instructions);
        binding.btnDiagPass.setVisibility(View.GONE);
        grid.setListener(new TouchGridView.Listener() {
            @Override public void onProgress(int touched, int total) {
                // The panel hides itself after the first touch so it doesn't cover cells.
                if (touched > 0) binding.panel.setVisibility(View.GONE);
            }
            @Override public void onComplete() {
                finishWith(Result.PASS, null);
            }
        });
    }

    private void setUpMultitouch() {
        MultiTouchView view = new MultiTouchView(this);
        binding.testArea.addView(view);
        binding.tvDiagInstructions.setText(R.string.diag_multitouch_instructions);
        showStatus(getString(R.string.diag_multitouch_status, 0));
        view.setListener((current, max) -> {
            multitouchMax = max;
            showStatus(getString(R.string.diag_multitouch_status, max));
        });
    }

    private void setUpDisplay() {
        View colorPanel = new View(this);
        colorPanel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        binding.testArea.addView(colorPanel);
        binding.tvDiagInstructions.setText(R.string.diag_display_instructions);
        binding.btnDiagPass.setVisibility(View.GONE);
        binding.btnDiagFail.setVisibility(View.GONE);
        binding.btnDiagAction.setVisibility(View.VISIBLE);
        binding.btnDiagAction.setText(R.string.diag_action_start);
        binding.btnDiagAction.setOnClickListener(v -> {
            binding.panel.setVisibility(View.GONE);
            advanceDisplayColor(colorPanel);
        });
        colorPanel.setOnClickListener(v -> advanceDisplayColor(colorPanel));
    }

    private void advanceDisplayColor(View panel) {
        displayIndex++;
        if (displayIndex < DISPLAY_COLORS.length) {
            panel.setBackgroundColor(DISPLAY_COLORS[displayIndex]);
            return;
        }
        // All colors shown — ask the question.
        panel.setBackgroundColor(Color.TRANSPARENT);
        panel.setOnClickListener(null);
        binding.panel.setVisibility(View.VISIBLE);
        binding.btnDiagAction.setVisibility(View.GONE);
        binding.tvDiagInstructions.setText(R.string.diag_display_question);
        binding.btnDiagPass.setText(R.string.diag_display_no_defects);
        binding.btnDiagFail.setText(R.string.diag_display_has_defects);
        binding.btnDiagPass.setVisibility(View.VISIBLE);
        binding.btnDiagFail.setVisibility(View.VISIBLE);
    }

    private void setUpManual(int instructionsRes, int actionRes, Runnable action, boolean runNow) {
        binding.tvDiagInstructions.setText(instructionsRes);
        binding.btnDiagAction.setVisibility(View.VISIBLE);
        binding.btnDiagAction.setText(actionRes);
        binding.btnDiagAction.setOnClickListener(v -> action.run());
        if (runNow) main.postDelayed(action, 400);
    }

    private void setUpMicrophone() {
        binding.tvDiagInstructions.setText(R.string.diag_mic_instructions);
        binding.btnDiagPass.setVisibility(View.GONE);
        showStatus(getString(R.string.value_placeholder));
        soundMeter = new SoundMeter(new SoundMeter.Listener() {
            @Override public void onLevel(double db) {
                float d = (float) db;
                micMin = Float.isNaN(micMin) ? d : Math.min(micMin, d);
                micMax = Float.isNaN(micMax) ? d : Math.max(micMax, d);
                showStatus(String.format(Locale.US, "%.0f dB  (%.0f–%.0f)", db, micMin, micMax));
                if (micMax - micMin >= MIC_PASS_RANGE_DB) finishWith(Result.PASS, micNote());
            }
            @Override public void onError(String message) { finishWith(Result.FAIL, message); }
        });
    }

    private void maybeStartMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startMicrophone();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startMicrophone() {
        if (soundMeter != null && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            soundMeter.start();
        }
    }

    private void setUpButtons() {
        binding.tvDiagInstructions.setText(R.string.diag_buttons_instructions);
        binding.btnDiagPass.setVisibility(View.GONE);
        showButtonsStatus();
    }

    private void showButtonsStatus() {
        showStatus((volUp ? "✓ " : "○ ") + getString(R.string.diag_buttons_vol_up) + "    "
                + (volDown ? "✓ " : "○ ") + getString(R.string.diag_buttons_vol_down));
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (test == DiagTest.BUTTONS && !finished
                && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) volUp = true; else volDown = true;
            showButtonsStatus();
            if (volUp && volDown) main.postDelayed(() -> finishWith(Result.PASS, null), 500);
            return true; // consume — don't change the volume during the test
        }
        return super.onKeyDown(keyCode, event);
    }

    private void setUpProximity() {
        if (sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) == null) {
            finishWith(Result.SKIPPED, getString(R.string.diag_note_no_sensor));
            return;
        }
        binding.tvDiagInstructions.setText(R.string.diag_proximity_instructions);
        binding.btnDiagPass.setVisibility(View.GONE);
        showStatus(getString(R.string.proximity_far_word));
    }

    private void setUpFlashlight() {
        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) { torchCameraId = id; break; }
            }
        } catch (CameraAccessException | RuntimeException ignored) {}
        if (torchCameraId == null) {
            finishWith(Result.SKIPPED, getString(R.string.diag_note_no_flash));
            return;
        }
        setUpManual(R.string.diag_flashlight_instructions, R.string.diag_action_flash, this::flashTorch, true);
    }

    private void setUpSensors() {
        binding.tvDiagInstructions.setText(R.string.diag_sensors_instructions);
        binding.btnDiagPass.setVisibility(View.GONE);
        binding.btnDiagFail.setVisibility(View.GONE);
        int[] types = {Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
                       Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE};
        for (int t : types) {
            Sensor s = sm.getDefaultSensor(t);
            if (s != null) sensorsToCheck.add(s);
        }
        showStatus(getString(R.string.status_waiting));
        main.postDelayed(this::finishSensorCheck, SENSOR_CHECK_MS);
    }

    private void finishSensorCheck() {
        if (finished) return;
        StringBuilder note = new StringBuilder();
        boolean allReported = true;
        for (Sensor s : sensorsToCheck) {
            boolean ok = sensorsReported.contains(s.getType());
            allReported &= ok;
            note.append(ok ? "✓ " : "✗ ").append(s.getName()).append("\n");
        }
        if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) allReported = false;
        finishWith(allReported ? Result.PASS : Result.FAIL, note.toString().trim());
    }

    private void runBatteryCheck() {
        BatteryInfo b = BatteryInfo.read(this);
        finishWith(b.isHealthy() ? Result.PASS : Result.FAIL, DiagnosticsReport.batteryNote(this, b));
    }

    // ── Sensors (proximity + sensor presence check) ──────────────────────
    private void registerTestSensors() {
        if (test == DiagTest.PROXIMITY) {
            Sensor p = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            if (p != null) sm.registerListener(this, p, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            for (Sensor s : sensorsToCheck) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override public void onSensorChanged(SensorEvent ev) {
        if (finished) return;
        if (test == DiagTest.PROXIMITY) {
            boolean near = ev.values[0] < ev.sensor.getMaximumRange() * 0.5f || ev.values[0] == 0f;
            showStatus(getString(near ? R.string.proximity_near_word : R.string.proximity_far_word));
            if (near) proximityWasNear = true;
            else if (proximityWasNear && !proximityPassed) {
                proximityPassed = true;
                finishWith(Result.PASS, null);
            }
        } else if (!sensorsReported.contains(ev.sensor.getType())) {
            sensorsReported.add(ev.sensor.getType());
            showStatus(sensorsReported.size() + " / " + sensorsToCheck.size());
            if (sensorsReported.size() == sensorsToCheck.size()) {
                main.removeCallbacksAndMessages(null);
                finishSensorCheck();
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── Actions: tone / vibration / torch ─────────────────────────────────
    /** 1 s, 1 kHz sine on the media stream. */
    private void playTone() {
        stopTone();
        int rate = 44100, n = rate;
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            double env = Math.min(1.0, Math.min(i, n - i) / (rate * 0.02)); // 20 ms fade in/out, no clicks
            pcm[i] = (short) (Math.sin(2 * Math.PI * 1000 * i / rate) * 0.8 * env * Short.MAX_VALUE);
        }
        toneTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        toneTrack.write(pcm, 0, n);
        toneTrack.play();
    }

    private void stopTone() {
        if (toneTrack != null) {
            try { toneTrack.stop(); } catch (IllegalStateException ignored) {}
            toneTrack.release();
            toneTrack = null;
        }
    }

    private void vibrate() {
        Vibrator v;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = vm == null ? null : vm.getDefaultVibrator();
        } else {
            v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (v == null || !v.hasVibrator()) {
            finishWith(Result.SKIPPED, getString(R.string.diag_note_no_vibrator));
            return;
        }
        v.vibrate(VibrationEffect.createWaveform(new long[]{0, 400, 200, 400, 200, 400}, -1));
    }

    private void flashTorch() {
        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cm.setTorchMode(torchCameraId, true);
            main.postDelayed(this::torchOff, 1500);
        } catch (CameraAccessException | RuntimeException e) {
            finishWith(Result.FAIL, String.valueOf(e.getMessage()));
        }
    }

    private void torchOff() {
        if (torchCameraId == null) return;
        try {
            ((CameraManager) getSystemService(Context.CAMERA_SERVICE)).setTorchMode(torchCameraId, false);
        } catch (CameraAccessException | RuntimeException ignored) {}
    }

    private void releaseHardware() {
        if (sm != null) sm.unregisterListener(this);
        if (soundMeter != null) soundMeter.stop();
        stopTone();
        torchOff();
    }

    // ── Finishing ─────────────────────────────────────────────────────────
    @Nullable private String passNote() {
        if (test == DiagTest.MULTITOUCH) return getString(R.string.diag_multitouch_status, multitouchMax);
        if (test == DiagTest.MICROPHONE) return micNote();
        return null;
    }

    @Nullable private String micNote() {
        if (Float.isNaN(micMin)) return null;
        return String.format(Locale.US, "%.0f–%.0f dB", micMin, micMax);
    }

    private void showStatus(String text) {
        binding.tvDiagStatus.setVisibility(View.VISIBLE);
        binding.tvDiagStatus.setText(text);
    }

    private void askOnBack() {
        if (finished) { finish(); return; }
        binding.panel.setVisibility(View.VISIBLE);
        new MaterialAlertDialogBuilder(this)
                .setTitle(test.title)
                .setMessage(R.string.diag_back_question)
                .setPositiveButton(R.string.diag_btn_fail, (d, w) -> finishWith(Result.FAIL, passNote()))
                .setNeutralButton(R.string.diag_btn_skip, (d, w) -> finishWith(Result.SKIPPED, null))
                .setNegativeButton(R.string.diag_btn_continue, null)
                .show();
    }

    private void finishWith(Result result, @Nullable String note) {
        if (finished) return;
        finished = true;
        releaseHardware();
        DiagnosticsStore.save(this, test, result, note);
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_TEST, test.name()).putExtra(EXTRA_RESULT, result.name()));
        finish();
    }
}
