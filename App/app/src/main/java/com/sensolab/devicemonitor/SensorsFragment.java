package com.sensolab.devicemonitor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.snackbar.Snackbar;
import com.sensolab.devicemonitor.SensorReadouts.Readout;
import com.sensolab.devicemonitor.databinding.FragmentSensorsBinding;
import java.util.Locale;

/**
 * Live readings for every hardware sensor, GPS and the microphone. After the split this
 * Fragment only wires things together: {@link SensorReadouts} formats values,
 * {@link SoundMeter} / {@link GpsTracker} own the mic and location plumbing,
 * {@link RecordingController} talks to the CSV recording service, and
 * {@link SensorsViewModel} keeps state across configuration changes.
 */
public class SensorsFragment extends ModeAwareFragment implements SensorEventListener {

    /** Sensor.TYPE_TILT_DETECTOR has no public constant; 22 is the framework value. */
    private static final int TYPE_TILT_DETECTOR = 22;

    private FragmentSensorsBinding binding;
    private SensorsViewModel vm;
    private SensorManager sm;
    private Sensor sStationary, sMotion, sSignMotion;
    private SoundMeter soundMeter;
    private GpsTracker gpsTracker;
    private RecordingController recording;

    // Contextual permission requests — registered in onCreate() per the Fragment
    // lifecycle contract. Each re-attempts the gated feature on grant, or offers a
    // "go to Settings" Snackbar on denial.
    private ActivityResultLauncher<String> locationPermissionLauncher;
    private ActivityResultLauncher<String> audioPermissionLauncher;
    private ActivityResultLauncher<String> bodySensorsPermissionLauncher;
    private ActivityResultLauncher<String> activityRecognitionPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(this).get(SensorsViewModel.class);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) startGps(); else showPermissionDeniedSnackbar(); });
        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) startSoundMeter(); else showPermissionDeniedSnackbar(); });
        bodySensorsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) registerSensors(); else showPermissionDeniedSnackbar(); });
        activityRecognitionPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> { if (granted) registerSensors(); else showPermissionDeniedSnackbar(); });
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentSensorsBinding.inflate(inf, container, false);
        bindModeLayouts(binding.layoutTextual, binding.layoutVisual);
        Context ctx = requireContext();
        sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        soundMeter = new SoundMeter(new SoundMeter.Listener() {
            @Override public void onLevel(double db) { showSound(db); }
            @Override public void onError(String message) {
                if (binding != null) binding.cardSound.setStatus(message);
            }
        });
        gpsTracker = new GpsTracker(ctx, new GpsTracker.Listener() {
            @Override public void onLocation(@NonNull Location loc) { showLocation(loc); }
            @Override public void onGpsDisabled() {
                if (binding != null) binding.cardSpeed.setStatus(getString(R.string.status_gps_disabled));
            }
        });
        recording = new RecordingController(ctx, recordingUi);
        binding.btnRecord.setOnClickListener(v -> recording.toggle());
        showCounters();
        return binding.getRoot();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override public void onResume() {
        super.onResume(); // ModeAwareFragment applies the display mode + listens for changes
        registerSensors();
        startGps();
        startSoundMeter();
        recording.attach();
    }

    @Override public void onPause() {
        super.onPause();
        if (sm != null) {
            sm.unregisterListener(this);
            if (sStationary != null) sm.cancelTriggerSensor(stationaryListener, sStationary);
            if (sMotion != null)     sm.cancelTriggerSensor(motionListener, sMotion);
            if (sSignMotion != null) sm.cancelTriggerSensor(sigMotListener, sSignMotion);
        }
        gpsTracker.stop();
        soundMeter.stop();
        recording.detach(); // the recording itself keeps running in its foreground service
    }

    @Override protected void onUnitsChanged() {
        // Every reading is re-formatted on its next sensor event; nothing cached here.
    }

    // ── Sensors ───────────────────────────────────────────────────────────
    private void registerSensors() {
        if (binding == null || sm == null) return;
        sm.unregisterListener(this);
        int ui = SensorManager.SENSOR_DELAY_UI, normal = SensorManager.SENSOR_DELAY_NORMAL;
        reg(Sensor.TYPE_ACCELEROMETER,              ui,     binding.cardAccel,         binding.gaugeAccel);
        reg(Sensor.TYPE_MAGNETIC_FIELD,             ui,     binding.cardMag,           null);
        reg(Sensor.TYPE_GYROSCOPE,                  ui,     binding.cardGyro,          binding.gaugeGyro);
        reg(Sensor.TYPE_LIGHT,                      ui,     binding.cardLight,         binding.gaugeLight);
        reg(Sensor.TYPE_PRESSURE,                   ui,     binding.cardPressure,      binding.gaugePressure);
        reg(Sensor.TYPE_GRAVITY,                    ui,     binding.cardGravity,       binding.gaugeTilt);
        reg(Sensor.TYPE_LINEAR_ACCELERATION,        ui,     binding.cardLinAccel,      binding.gaugeLinAccel);
        reg(Sensor.TYPE_ROTATION_VECTOR,            ui,     binding.cardRotation,      null);
        reg(Sensor.TYPE_GAME_ROTATION_VECTOR,       ui,     binding.cardGameRotation,  null);
        reg(Sensor.TYPE_GYROSCOPE_UNCALIBRATED,     ui,     binding.cardGyroUncal,     null);
        reg(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, ui,     binding.cardAccelUncal,    null);
        reg(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,ui,     binding.cardMagUncal,      null);
        reg(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,ui,     binding.cardGeomagRotation,null);
        // P1: slow-changing sensors at NORMAL (~5 Hz) to save battery
        reg(Sensor.TYPE_PROXIMITY,                  normal, binding.cardProximity,     binding.gaugeProximity);
        reg(Sensor.TYPE_RELATIVE_HUMIDITY,          normal, binding.cardHumidity,      binding.gaugeHumidity);
        reg(Sensor.TYPE_AMBIENT_TEMPERATURE,        normal, binding.cardAmbientTemp,   binding.gaugeAmbientTemp);
        reg(TYPE_TILT_DETECTOR,                     normal, binding.cardTilt,          null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            reg(Sensor.TYPE_HINGE_ANGLE,            normal, binding.cardHinge,         null);
        } else {
            binding.cardHinge.setStatus(getString(R.string.status_not_available));
        }

        // Batch 3: registerListener() throws SecurityException for these without the
        // permission, so they're only registered once it's granted (API 29+ for steps).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            regGated(Sensor.TYPE_STEP_DETECTOR, binding.cardStepDetector, binding.gaugeSteps,
                    Manifest.permission.ACTIVITY_RECOGNITION, activityRecognitionPermissionLauncher,
                    R.string.perm_rationale_activity_recognition);
            regGated(Sensor.TYPE_STEP_COUNTER, binding.cardStepCounter, null,
                    Manifest.permission.ACTIVITY_RECOGNITION, activityRecognitionPermissionLauncher,
                    R.string.perm_rationale_activity_recognition);
        } else {
            reg(Sensor.TYPE_STEP_DETECTOR, normal, binding.cardStepDetector, binding.gaugeSteps);
            reg(Sensor.TYPE_STEP_COUNTER,  normal, binding.cardStepCounter,  null);
        }
        regGated(Sensor.TYPE_HEART_RATE, binding.cardHeartRate, binding.gaugeHeartRate,
                Manifest.permission.BODY_SENSORS, bodySensorsPermissionLauncher,
                R.string.perm_rationale_body_sensors);

        // One-shot trigger sensors re-arm themselves after each event.
        sStationary = sm.getDefaultSensor(Sensor.TYPE_STATIONARY_DETECT);
        sMotion     = sm.getDefaultSensor(Sensor.TYPE_MOTION_DETECT);
        sSignMotion = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        armTrigger(sStationary, stationaryListener, binding.cardStationary);
        armTrigger(sMotion,     motionListener,     binding.cardMotionDetect);
        armTrigger(sSignMotion, sigMotListener,     binding.cardSigMotion);
        showCounters();
    }

    private void reg(int type, int delay, SensorCardView card, @Nullable GaugeCardView gauge) {
        Sensor s = sm.getDefaultSensor(type);
        if (s != null) {
            sm.registerListener(this, s, delay);
        } else {
            String na = getString(R.string.status_not_available);
            card.setStatus(na);
            if (gauge != null) gauge.setUnavailable(na);
        }
    }

    private void regGated(int type, SensorCardView card, @Nullable GaugeCardView gauge, String permission,
                          ActivityResultLauncher<String> launcher, @StringRes int rationaleRes) {
        Sensor s = sm.getDefaultSensor(type);
        if (s == null) {
            String na = getString(R.string.status_not_available);
            card.setStatus(na);
            if (gauge != null) gauge.setUnavailable(na);
            return;
        }
        if (hasPermission(permission)) {
            card.setOnClickListener(null);
            card.setClickable(false);
            if (gauge != null) { gauge.setOnClickListener(null); gauge.setClickable(false); }
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            View.OnClickListener ask = v -> requestPermission(launcher, permission, rationaleRes);
            String msg = getString(R.string.status_tap_to_grant);
            card.setActionStatus(msg, ask);
            if (gauge != null) gauge.setActionStatus(msg, ask);
        }
    }

    private void armTrigger(@Nullable Sensor s, TriggerEventListener l, SensorCardView card) {
        if (s == null) {
            card.setStatus(getString(R.string.status_not_available));
            return;
        }
        sm.requestTriggerSensor(l, s);
    }

    private final TriggerEventListener stationaryListener = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent ev) {
            vm.stationaryCount++;
            recording.forwardTrigger("STATIONARY_DETECT", ev.timestamp);
            showCounters();
            if (sStationary != null && sm != null) sm.requestTriggerSensor(this, sStationary);
        }
    };
    private final TriggerEventListener motionListener = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent ev) {
            vm.motionCount++;
            recording.forwardTrigger("MOTION_DETECT", ev.timestamp);
            showCounters();
            if (sMotion != null && sm != null) sm.requestTriggerSensor(this, sMotion);
        }
    };
    private final TriggerEventListener sigMotListener = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent ev) {
            vm.sigMotCount++;
            vm.saveCounters(); // R4
            recording.forwardTrigger("SIGNIFICANT_MOTION", ev.timestamp);
            showCounters();
            if (sSignMotion != null && sm != null) sm.requestTriggerSensor(this, sSignMotion);
        }
    };

    /** Event-count cards (trigger sensors, tilt, step detector) from ViewModel state. */
    private void showCounters() {
        if (binding == null || sm == null) return;
        String waiting = getString(R.string.status_waiting);
        String events = getString(R.string.trigger_events_detected);
        if (sStationary != null) showCount(binding.cardStationary, vm.stationaryCount, waiting, events);
        if (sMotion != null)     showCount(binding.cardMotionDetect, vm.motionCount, waiting, events);
        if (sSignMotion != null) showCount(binding.cardSigMotion, vm.sigMotCount, waiting, events);
        if (sm.getDefaultSensor(TYPE_TILT_DETECTOR) != null) {
            showCount(binding.cardTilt, vm.tiltCount, waiting, events);
        }
    }

    private void showCount(SensorCardView card, int count, String waiting, String events) {
        if (count == 0) card.setReading("0", null, waiting);
        else card.setReading(String.valueOf(count), null, events);
    }

    // ── Sensor events ─────────────────────────────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent ev) {
        // Z2: some OEM drivers occasionally deliver null/empty arrays
        if (binding == null || ev == null || ev.values == null || ev.values.length == 0) return;
        Context c = requireContext();
        float[] v = ev.values;
        switch (ev.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER: {
                vm.lastAccel = v.clone();
                Readout r = SensorReadouts.accelerometer(c, v);
                show(binding.cardAccel, r);
                binding.gaugeAccel.setReading(r.gauge, String.format(Locale.US, "%.2f", r.gauge), "g", r.caption);
                break;
            }
            case Sensor.TYPE_MAGNETIC_FIELD: {
                vm.lastMag = v.clone();
                float heading = SensorReadouts.heading(vm.lastAccel, vm.lastMag);
                String compass = Float.isNaN(heading) ? "--"
                        : SensorReadouts.compassLabel((int) heading) + " " + (int) heading + "°";
                if (!Float.isNaN(heading)) binding.compassView.setBearing(heading);
                show(binding.cardMag, SensorReadouts.magnetometer(c, v, compass, vm.magLowAccuracy));
                break;
            }
            case Sensor.TYPE_GYROSCOPE: {
                Readout r = SensorReadouts.gyroscope(c, v);
                show(binding.cardGyro, r);
                binding.gaugeGyro.setReading(r.gauge, r.value, r.unit, r.caption);
                break;
            }
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                showNullable(binding.cardGyroUncal, SensorReadouts.gyroUncalibrated(v));
                break;
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                showNullable(binding.cardAccelUncal, SensorReadouts.accelUncalibrated(v));
                break;
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                showNullable(binding.cardMagUncal, SensorReadouts.magUncalibrated(v));
                break;
            case Sensor.TYPE_LIGHT: {
                Readout r = SensorReadouts.light(c, v[0]);
                show(binding.cardLight, r);
                binding.gaugeLight.setReading(r.gauge, r.value, r.unit, r.caption);
                break;
            }
            case Sensor.TYPE_PRESSURE: {
                Readout r = SensorReadouts.pressure(c, v[0]);
                show(binding.cardPressure, r);
                binding.gaugePressure.setReading(r.gauge, r.value, r.unit, r.caption);
                break;
            }
            case Sensor.TYPE_GRAVITY: {
                Readout r = SensorReadouts.gravity(c, v);
                show(binding.cardGravity, r);
                binding.gaugeTilt.setReading(r.gauge, r.value, r.unit, r.caption);
                break;
            }
            case Sensor.TYPE_LINEAR_ACCELERATION: {
                Readout r = SensorReadouts.linearAccel(c, v);
                show(binding.cardLinAccel, r);
                binding.gaugeLinAccel.setReading(r.gauge, r.value, r.unit, r.caption);
                break;
            }
            case Sensor.TYPE_ROTATION_VECTOR:
                show(binding.cardRotation, SensorReadouts.rotation(v, null));
                break;
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                show(binding.cardGameRotation, SensorReadouts.rotation(v, null));
                break;
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                show(binding.cardGeomagRotation, SensorReadouts.rotation(v, getString(R.string.geomag_detail)));
                break;
            case Sensor.TYPE_STEP_DETECTOR: {
                vm.stepDetCount++;
                vm.saveCounters(); // R4
                String n = String.valueOf(vm.stepDetCount);
                binding.cardStepDetector.setReading(n, null, getString(R.string.steps_this_session));
                binding.gaugeSteps.setReading(vm.stepDetCount, n, null, getString(R.string.steps_this_session));
                break;
            }
            case Sensor.TYPE_STEP_COUNTER: {
                long total = (long) v[0];
                // C1: Step Counter resets to 0 on reboot
                if (vm.stepCntBase < 0 || total < vm.stepCntBase) {
                    vm.stepCntBase = total;
                    vm.saveCounters();
                }
                long session = total - vm.stepCntBase;
                binding.cardStepCounter.setReading(String.valueOf(session), null,
                        getString(R.string.step_counter_format, session, total));
                break;
            }
            case Sensor.TYPE_PROXIMITY: {
                Readout r = SensorReadouts.proximity(c, v[0], ev.sensor.getMaximumRange());
                show(binding.cardProximity, r);
                binding.gaugeProximity.setReading(r.gauge, r.value, null, null);
                break;
            }
            case Sensor.TYPE_RELATIVE_HUMIDITY: {
                Readout r = SensorReadouts.humidity(c, v[0]);
                show(binding.cardHumidity, r);
                binding.gaugeHumidity.setReading(r.gauge, r.value, r.unit, r.caption);
                break;
            }
            case Sensor.TYPE_AMBIENT_TEMPERATURE: {
                Readout r = SensorReadouts.ambientTemp(c, v[0]);
                if (r == null) {
                    String na = getString(R.string.status_not_available);
                    binding.cardAmbientTemp.setStatus(na);
                    binding.gaugeAmbientTemp.setUnavailable(na);
                } else {
                    show(binding.cardAmbientTemp, r);
                    binding.gaugeAmbientTemp.setReading(r.gauge, r.value, r.unit, r.caption);
                }
                break;
            }
            case Sensor.TYPE_HEART_RATE: {
                Readout r = SensorReadouts.heartRate(c, (int) v[0], ev.accuracy);
                if (r == null) {
                    String hint = getString(R.string.hr_place_finger);
                    binding.cardHeartRate.setStatus(hint);
                    binding.gaugeHeartRate.setUnavailable(hint);
                } else {
                    show(binding.cardHeartRate, r);
                    binding.gaugeHeartRate.setReading(r.gauge, r.value, r.unit, r.caption);
                }
                break;
            }
            default: {
                int t = ev.sensor.getType();
                if (t == TYPE_TILT_DETECTOR) {
                    vm.tiltCount++;
                    vm.saveCounters(); // R4
                    showCounters();
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && t == Sensor.TYPE_HINGE_ANGLE) {
                    show(binding.cardHinge, SensorReadouts.hinge(c, v[0]));
                }
                break;
            }
        }
        // CSV recording (if active) is captured independently by CsvRecordingService.
    }

    /** Q2: badge the magnetometer card while it's uncalibrated (e.g. near metal). */
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor == null || sensor.getType() != Sensor.TYPE_MAGNETIC_FIELD) return;
        boolean low = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                   || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW;
        if (low && !vm.magLowAccuracy && binding != null) {
            // Q5: TalkBack users hear why the heading drifts
            binding.cardMag.announceForAccessibility(getString(R.string.accuracy_low_warning));
        }
        vm.magLowAccuracy = low;
    }

    private void show(SensorCardView card, Readout r) {
        card.setReading(r.value, r.unit, joinDetail(r));
    }

    private void showNullable(SensorCardView card, @Nullable Readout r) {
        if (r != null) show(card, r);
    }

    private static String joinDetail(Readout r) {
        if (r.caption == null || r.caption.equals(r.detail)) return r.detail;
        if (r.detail == null) return r.caption;
        return r.detail + "\n" + r.caption;
    }

    // ── Sound level ───────────────────────────────────────────────────────
    private void startSoundMeter() {
        if (binding == null || soundMeter.isRunning()) return;
        // Inline check (not hasPermission()) so lint sees the guard on soundMeter.start().
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            View.OnClickListener ask = v -> requestPermission(audioPermissionLauncher,
                    Manifest.permission.RECORD_AUDIO, R.string.perm_rationale_audio);
            String msg = getString(R.string.status_tap_to_grant);
            binding.cardSound.setActionStatus(msg, ask);
            binding.gaugeSound.setActionStatus(msg, ask);
            return;
        }
        binding.cardSound.setOnClickListener(null);
        binding.cardSound.setClickable(false);
        binding.gaugeSound.setOnClickListener(null);
        binding.gaugeSound.setClickable(false);
        soundMeter.start();
    }

    private void showSound(double db) {
        if (binding == null || !isAdded()) return;
        Readout r = SensorReadouts.sound(requireContext(), db);
        show(binding.cardSound, r);
        binding.gaugeSound.setReading(r.gauge, r.value, r.unit, r.caption);
    }

    // ── GPS ───────────────────────────────────────────────────────────────
    private void startGps() {
        if (binding == null) return;
        // Inline check (not hasPermission()) so lint sees the guard on gpsTracker.start().
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            binding.cardSpeed.setOnClickListener(null);
            binding.cardSpeed.setClickable(false);
            binding.cardAltitude.setOnClickListener(null);
            binding.cardAltitude.setClickable(false);
            String waiting = getString(R.string.status_waiting);
            if (binding.cardSpeed.getTag() == null) binding.cardSpeed.setStatus(waiting);
            if (binding.cardAltitude.getTag() == null) binding.cardAltitude.setStatus(waiting);
            gpsTracker.start();
        } else {
            View.OnClickListener ask = v -> requestPermission(locationPermissionLauncher,
                    Manifest.permission.ACCESS_FINE_LOCATION, R.string.perm_rationale_location);
            String msg = getString(R.string.status_tap_to_grant);
            binding.cardSpeed.setActionStatus(msg, ask);
            binding.cardAltitude.setActionStatus(msg, ask);
            binding.gaugeSpeed.setActionStatus(msg, ask);
        }
    }

    private void showLocation(Location loc) {
        if (binding == null || !isAdded()) return;
        Context c = requireContext();
        Readout speed = SensorReadouts.speed(c, loc);
        show(binding.cardSpeed, speed);
        binding.cardSpeed.setTag(Boolean.TRUE); // has a fix — don't reset to "Waiting…"
        binding.gaugeSpeed.setReading(speed.gauge, speed.value, speed.unit, null);
        Readout alt = SensorReadouts.altitude(c, loc);
        if (alt != null) {
            show(binding.cardAltitude, alt);
            binding.cardAltitude.setTag(Boolean.TRUE);
        }
    }

    // ── CSV recording UI ──────────────────────────────────────────────────
    private final RecordingController.Ui recordingUi = new RecordingController.Ui() {
        @Override public void showIdle() {
            if (binding == null) return;
            binding.btnRecord.setText(R.string.rec_btn_start);
            binding.tvRecordStatus.setText(R.string.rec_status_idle);
        }
        @Override public void showRecording(String elapsed, int samples) {
            if (binding == null || !isAdded()) return;
            binding.btnRecord.setText(getString(R.string.rec_btn_stop, elapsed, samples));
            binding.tvRecordStatus.setText(getString(R.string.rec_status_active, elapsed, samples));
        }
        @Override public void showSaved(String fileName, int samples) {
            if (binding == null || !isAdded()) return;
            binding.btnRecord.setText(R.string.rec_btn_start);
            binding.tvRecordStatus.setText(getString(R.string.rec_status_saved, fileName, samples));
        }
    };

    // ── Permissions ───────────────────────────────────────────────────────
    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Shows a rationale Snackbar first only when the system says one is warranted (the user
     *  already denied once, not permanently); otherwise goes straight to the system dialog. */
    private void requestPermission(ActivityResultLauncher<String> launcher, String permission, @StringRes int rationaleRes) {
        if (!isAdded()) return;
        View root = getView();
        if (shouldShowRequestPermissionRationale(permission) && root != null) {
            Snackbar.make(root, rationaleRes, Snackbar.LENGTH_LONG)
                    .setAction(R.string.perm_rationale_continue, v -> launcher.launch(permission))
                    .show();
        } else {
            launcher.launch(permission);
        }
    }

    private void showPermissionDeniedSnackbar() {
        View root = getView();
        if (root == null) return;
        Snackbar.make(root, R.string.perm_denied_open_settings, Snackbar.LENGTH_LONG)
                .setAction(R.string.perm_action_settings, v -> openAppSettings())
                .show();
    }

    // U4: open the app's system settings so a permanently denied permission can be granted
    private void openAppSettings() {
        if (!isAdded()) return;
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireActivity().getPackageName(), null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }
}
