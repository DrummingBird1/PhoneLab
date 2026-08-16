package com.sensolab.devicemonitor;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.*;
import androidx.core.app.ActivityCompat;
import com.google.android.material.snackbar.Snackbar;
import com.sensolab.devicemonitor.databinding.FragmentSensorsBinding;
import java.util.Locale;

public class SensorsFragment extends ModeAwareFragment implements SensorEventListener {

    private static final int TYPE_TILT_DETECTOR = 22;

    private SensorManager sm;
    private LocationManager lm;
    private boolean locationRegistered = false;

    // Sensor handles
    private Sensor sAccel, sMag, sGyro, sLight, sPressure, sGravity,
                   sLinAccel, sRotVec, sGameRot, sStep, sStepCounter,
                   sTilt, sProximity, sHumidity, sAmbientTemp, sHeartRate,
                   sSignMotion, sGyroUncal;
    // v1.2.0: 6 additional sensors
    private Sensor sAccelUncal, sMagUncal, sGeomagRot, sHinge,
                   sStationary, sMotion;

    // Textual views (layoutTextual/layoutVisual now in ModeAwareFragment)
    private TextView tvAccel, tvGyro, tvMag, tvLight, tvPressure,
                     tvGravity, tvLinAccel, tvRot, tvGameRot,
                     tvStep, tvStepCounter, tvTilt, tvProximity,
                     tvHumidity, tvAmbientTemp, tvSignMotion, tvHeartRate,
                     tvSpeed, tvAltitude, tvSoundLevel, tvGyroUncal;
    // v1.2.0
    private TextView tvAccelUncal, tvMagUncal, tvGeomagRot, tvHingeAngle,
                     tvStationary, tvMotionDetect, tvRecordStatus;
    private Button   btnRecord;
    // Batch 4: recording is owned by CsvRecordingService, not this Fragment — it
    // continues correctly across tab switches / backgrounding. This Fragment is a
    // thin client: start/stop the service and bind to read its live state for the UI.
    private CsvRecordingService boundService;
    private boolean serviceBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            boundService = ((CsvRecordingService.LocalBinder) binder).getService();
            serviceBound = true;
            if (boundService.isRecording()) {
                mainHandler.removeCallbacks(recordingPoller);
                mainHandler.post(recordingPoller);
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            boundService = null;
            serviceBound = false;
            mainHandler.removeCallbacks(recordingPoller);
            if (btnRecord != null) btnRecord.setText(R.string.rec_btn_start);
        }
    };
    private int  stationaryCount = 0;
    private int  motionCount     = 0;

    // Visual views
    private TextView visAccel, visGyro, visLinAccel, visLight, visPressure, visGravity,
                     visProximity, visHumidity, visAmbientTemp,
                     visStep, visRot, visTilt, visSound, visHeartRate, visSigMotion,
                     visSpeed;
    private ProgressBar pbAccel, pbGyro, pbLinAccel, pbLight, pbPressure, pbGravity,
                        pbProximity, pbHumidity, pbAmbientTemp, pbStep, pbSound;

    // Live compass
    private CompassView compassView;

    // State (R4: persisted across app kill — loaded in onCreateView, saved on every change)
    private int   stepDetCount = 0;
    private long  stepCntBase  = -1;
    private int   tiltCount    = 0;
    private int   sigMotCount  = 0;

    private void loadCounters() {
        android.content.SharedPreferences sp = AppPrefs.get(requireContext());
        stepDetCount = sp.getInt (AppPrefs.KEY_STEP_DET_COUNT, 0);
        tiltCount    = sp.getInt (AppPrefs.KEY_TILT_COUNT,     0);
        sigMotCount  = sp.getInt (AppPrefs.KEY_SIG_MOT_COUNT,  0);
        stepCntBase  = sp.getLong(AppPrefs.KEY_STEP_CNT_BASE, -1L);
    }

    private void saveCounters() {
        if (!isAdded()) return;
        AppPrefs.get(requireContext()).edit()
                .putInt (AppPrefs.KEY_STEP_DET_COUNT, stepDetCount)
                .putInt (AppPrefs.KEY_TILT_COUNT,     tiltCount)
                .putInt (AppPrefs.KEY_SIG_MOT_COUNT,  sigMotCount)
                .putLong(AppPrefs.KEY_STEP_CNT_BASE,  stepCntBase)
                .apply();
    }
    private float[] lastAccel  = {0, 0, 9.8f};
    private float[] lastMag    = {0, 30, 0};

    // Sound level via AudioRecord
    private volatile AudioRecord audioRecord;          // B3: cross-thread access
    private volatile boolean soundRunning = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread soundThread;

    private FragmentSensorsBinding binding;

    // Contextual permission requests — registered in onCreate() per the Fragment
    // lifecycle contract (must happen before STARTED, not in onCreateView/onResume).
    // Each launcher re-attempts the gated feature on grant, or shows a "go to
    // Settings" Snackbar on denial.
    private ActivityResultLauncher<String> locationPermissionLauncher;
    private ActivityResultLauncher<String> audioPermissionLauncher;
    private ActivityResultLauncher<String> bodySensorsPermissionLauncher;
    private ActivityResultLauncher<String> activityRecognitionPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        binding = FragmentSensorsBinding.inflate(inf, container, false);
        bindViews();
        loadCounters(); // R4: restore session counters from prefs
        lm = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        return binding.getRoot();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void bindViews() {
        bindModeLayouts(binding.layoutTextual, binding.layoutVisual);
        // Textual
        tvAccel        = binding.tvAccelerometer;
        tvGyro         = binding.tvGyroscope;
        tvMag          = binding.tvMagneticField;
        tvLight        = binding.tvLight;
        tvPressure     = binding.tvPressure;
        tvGravity      = binding.tvGravity;
        tvLinAccel     = binding.tvLinearAccel;
        tvRot          = binding.tvRotationVector;
        tvGameRot      = binding.tvGameRotation;
        tvGyroUncal    = binding.tvGyroUncal;
        tvStep         = binding.tvStepDetector;
        tvStepCounter  = binding.tvStepCounter;
        tvTilt         = binding.tvTiltDetector;
        tvProximity    = binding.tvProximity;
        tvHumidity     = binding.tvHumidity;
        tvAmbientTemp  = binding.tvAmbientTemp;
        tvSignMotion   = binding.tvSigMotion;
        tvHeartRate    = binding.tvHeartRate;
        tvSoundLevel   = binding.tvSoundLevel;
        tvSpeed        = binding.tvSpeed;
        tvAltitude     = binding.tvAltitude;
        // Visual
        compassView    = binding.compassView;
        visAccel       = binding.visAccel;
        visGyro        = binding.visGyro;
        visLinAccel    = binding.visLinAccel;
        visLight       = binding.visLight;
        visPressure    = binding.visPressure;
        visGravity     = binding.visGravity;
        visProximity   = binding.visProximity;
        visHumidity    = binding.visHumidity;
        visAmbientTemp = binding.visAmbientTemp;
        visStep        = binding.visStep;
        visRot         = binding.visRot;
        visTilt        = binding.visTilt;
        visSound       = binding.visSound;
        visHeartRate   = binding.visHeartRate;
        visSigMotion   = binding.visSigMotion;
        visSpeed       = binding.visSpeed;
        // Bars
        pbAccel        = binding.pbAccel;
        pbGyro         = binding.pbGyro;
        pbLinAccel     = binding.pbLinAccel;
        pbLight        = binding.pbLight;
        pbPressure     = binding.pbPressure;
        pbGravity      = binding.pbGravity;
        pbProximity    = binding.pbProximity;
        pbHumidity     = binding.pbHumidity;
        pbAmbientTemp  = binding.pbAmbientTemp;
        pbStep         = binding.pbStep;
        pbSound        = binding.pbSound;
        // v1.2.0 advanced sensors + recording controls
        tvAccelUncal   = binding.tvAccelUncal;
        tvMagUncal     = binding.tvMagUncal;
        tvGeomagRot    = binding.tvGeomagRot;
        tvHingeAngle   = binding.tvHingeAngle;
        tvStationary   = binding.tvStationary;
        tvMotionDetect = binding.tvMotionDetect;
        tvRecordStatus = binding.tvRecordStatus;
        btnRecord      = binding.btnRecord;
        btnRecord.setOnClickListener(b -> toggleRecording());
    }

    @Override public void onResume() {
        super.onResume(); // ModeAwareFragment calls applyMode() + registers prefs listener
        registerSensors();
        startGps();
        startSoundMeter();
        // Batch 4: reconnect to an already-running recording (e.g. started earlier,
        // tab revisited). Flags=0 — do NOT create/start the service just by binding;
        // this only succeeds if a recording is already in progress.
        requireContext().bindService(
                new Intent(requireContext(), CsvRecordingService.class), serviceConnection, 0);
    }

    @Override public void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
        stopGps();
        stopSoundMeter();
        // Batch 4: unbind only — recording (if any) continues via the foreground
        // service regardless of this fragment's visibility. Previously this forcibly
        // stopped recording on tab-leave; the service now makes that unnecessary.
        if (serviceBound) {
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ── Sensors ───────────────────────────────────────────────────────────
    private void registerSensors() {
        sm = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        sm.unregisterListener(this);

        sAccel       = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sMag         = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sGyro        = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sLight       = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        sPressure    = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sGravity     = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sLinAccel    = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sRotVec      = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sGameRot     = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        sStep        = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        sStepCounter = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        sTilt        = sm.getDefaultSensor(TYPE_TILT_DETECTOR);
        sProximity   = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sHumidity    = sm.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        sAmbientTemp = sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        sHeartRate   = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        sSignMotion  = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        sGyroUncal   = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
        // v1.2.0
        sAccelUncal  = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED);
        sMagUncal    = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
        sGeomagRot   = sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        sStationary  = sm.getDefaultSensor(Sensor.TYPE_STATIONARY_DETECT);
        sMotion      = sm.getDefaultSensor(Sensor.TYPE_MOTION_DETECT);
        // Hinge angle (foldables); constant introduced in API 30
        sHinge       = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                       ? sm.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) : null;

        reg(sAccel,       SensorManager.SENSOR_DELAY_UI,     tvAccel,       R.string.sensor_accelerometer);
        reg(sMag,         SensorManager.SENSOR_DELAY_UI,     tvMag,         R.string.sensor_magnetometer);
        reg(sGyro,        SensorManager.SENSOR_DELAY_UI,     tvGyro,        R.string.sensor_gyroscope);
        reg(sLight,       SensorManager.SENSOR_DELAY_UI,     tvLight,       R.string.sensor_light);
        reg(sPressure,    SensorManager.SENSOR_DELAY_UI,     tvPressure,    R.string.sensor_barometer);
        reg(sGravity,     SensorManager.SENSOR_DELAY_UI,     tvGravity,     R.string.sensor_gravity);
        reg(sLinAccel,    SensorManager.SENSOR_DELAY_UI,     tvLinAccel,    R.string.sensor_linear_accel);
        reg(sRotVec,      SensorManager.SENSOR_DELAY_UI,     tvRot,         R.string.sensor_rotation_vector);
        reg(sGameRot,     SensorManager.SENSOR_DELAY_UI,     tvGameRot,     R.string.sensor_game_rotation);
        reg(sGyroUncal,   SensorManager.SENSOR_DELAY_UI,     tvGyroUncal,   R.string.sensor_gyro_uncal);
        // P1: slow sensors (humidity/ambient/proximity) → NORMAL = ~5Hz, saves battery
        // Batch 3: Step Detector/Counter are gated by ACTIVITY_RECOGNITION on API 29+ only —
        // below that, no runtime permission applies to them at all.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            regGated(sStep,        SensorManager.SENSOR_DELAY_NORMAL, tvStep,        R.string.sensor_step_detector,
                    Manifest.permission.ACTIVITY_RECOGNITION, activityRecognitionPermissionLauncher, R.string.perm_rationale_activity_recognition);
            regGated(sStepCounter, SensorManager.SENSOR_DELAY_NORMAL, tvStepCounter, R.string.sensor_step_counter,
                    Manifest.permission.ACTIVITY_RECOGNITION, activityRecognitionPermissionLauncher, R.string.perm_rationale_activity_recognition);
        } else {
            reg(sStep,        SensorManager.SENSOR_DELAY_NORMAL, tvStep,        R.string.sensor_step_detector);
            reg(sStepCounter, SensorManager.SENSOR_DELAY_NORMAL, tvStepCounter, R.string.sensor_step_counter);
        }
        reg(sTilt,        SensorManager.SENSOR_DELAY_NORMAL, tvTilt,        R.string.sensor_tilt_detector);
        reg(sProximity,   SensorManager.SENSOR_DELAY_NORMAL, tvProximity,   R.string.sensor_proximity);
        reg(sHumidity,    SensorManager.SENSOR_DELAY_NORMAL, tvHumidity,    R.string.sensor_humidity);
        reg(sAmbientTemp, SensorManager.SENSOR_DELAY_NORMAL, tvAmbientTemp, R.string.sensor_ambient_temp);
        regGated(sHeartRate, SensorManager.SENSOR_DELAY_NORMAL, tvHeartRate, R.string.sensor_heart_rate,
                Manifest.permission.BODY_SENSORS, bodySensorsPermissionLauncher, R.string.perm_rationale_body_sensors);
        // v1.2.0 advanced sensors
        reg(sAccelUncal,  SensorManager.SENSOR_DELAY_UI,     tvAccelUncal,  R.string.sensor_accel_uncal);
        reg(sMagUncal,    SensorManager.SENSOR_DELAY_UI,     tvMagUncal,    R.string.sensor_mag_uncal);
        reg(sGeomagRot,   SensorManager.SENSOR_DELAY_UI,     tvGeomagRot,   R.string.sensor_geomag_rotation);
        reg(sHinge,       SensorManager.SENSOR_DELAY_NORMAL, tvHingeAngle,  R.string.sensor_hinge_angle);

        // One-shot trigger sensors (rearm after firing)
        if (sStationary != null) {
            sm.requestTriggerSensor(stationaryListener, sStationary);
            set(tvStationary, getString(R.string.sensor_stationary) + "\n" + getString(R.string.status_waiting));
        } else {
            set(tvStationary, getString(R.string.sensor_stationary) + "\n" + getString(R.string.status_not_available));
        }
        if (sMotion != null) {
            sm.requestTriggerSensor(motionListener, sMotion);
            set(tvMotionDetect, getString(R.string.sensor_motion_detect) + "\n" + getString(R.string.status_waiting));
        } else {
            set(tvMotionDetect, getString(R.string.sensor_motion_detect) + "\n" + getString(R.string.status_not_available));
        }

        String sigLabel = getString(R.string.sig_motion_label);
        if (sSignMotion != null) {
            sm.requestTriggerSensor(sigMotListener, sSignMotion);
            set(tvSignMotion, sigLabel + "\n" + getString(R.string.status_waiting));
        } else {
            set(tvSignMotion, sigLabel + "\n" + getString(R.string.status_not_available));
            set(visSigMotion, getString(R.string.sig_motion_na_short));
        }
    }

    private void reg(Sensor s, int delay, TextView tv, int nameRes) {
        if (s != null) sm.registerListener(this, s, delay);
        else           set(tv, getString(nameRes) + "\n" + getString(R.string.status_not_available));
    }

    /** Batch 3: like reg(), but for sensors the OS gates behind a runtime permission
     *  (Heart Rate → BODY_SENSORS; Step Detector/Counter → ACTIVITY_RECOGNITION on API 29+).
     *  registerListener() throws SecurityException without the permission, so this MUST
     *  skip registration entirely when it's missing — not just skip showing data. */
    private void regGated(Sensor s, int delay, TextView tv, int nameRes,
                           String permission, ActivityResultLauncher<String> launcher, @StringRes int rationaleRes) {
        if (s == null) {
            set(tv, getString(nameRes) + "\n" + getString(R.string.status_not_available));
            return;
        }
        if (ActivityCompat.checkSelfPermission(requireActivity(), permission) == PackageManager.PERMISSION_GRANTED) {
            if (tv != null) tv.setOnClickListener(null); // clear any prior "tap to grant" handler
            sm.registerListener(this, s, delay);
        } else {
            set(tv, getString(nameRes) + "\n" + getString(R.string.status_permission_needed));
            if (tv != null) tv.setOnClickListener(v -> requestPermission(launcher, permission, rationaleRes));
        }
    }

    // v1.2.0: stationary + motion trigger sensors
    private final TriggerEventListener stationaryListener = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent ev) {
            if (!isAdded()) return;
            stationaryCount++;
            if (boundService != null) boundService.appendTrigger("STATIONARY_DETECT", ev.timestamp); // X1
            set(tvStationary, getString(R.string.sensor_stationary) + "\n"
                    + getString(R.string.state_stationary_detected, stationaryCount));
            if (sStationary != null && sm != null) sm.requestTriggerSensor(this, sStationary);
        }
    };
    private final TriggerEventListener motionListener = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent ev) {
            if (!isAdded()) return;
            motionCount++;
            if (boundService != null) boundService.appendTrigger("MOTION_DETECT", ev.timestamp); // X1
            set(tvMotionDetect, getString(R.string.sensor_motion_detect) + "\n"
                    + getString(R.string.state_motion_detected, motionCount));
            if (sMotion != null && sm != null) sm.requestTriggerSensor(this, sMotion);
        }
    };

    private final TriggerEventListener sigMotListener = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent ev) {
            if (!isAdded()) return;
            sigMotCount++;
            saveCounters(); // R4
            if (boundService != null) boundService.appendTrigger("SIGNIFICANT_MOTION", ev.timestamp); // X1
            set(tvSignMotion, getString(R.string.sig_motion_label) + "\n"
                    + getString(R.string.sig_motion_detected_format, sigMotCount));
            set(visSigMotion, getString(R.string.sig_motion_short_format, sigMotCount));
            if (sSignMotion != null && sm != null)
                sm.requestTriggerSensor(this, sSignMotion);
        }
    };

    // Batch 4: CSV recording controls — start/stop CsvRecordingService, which owns the
    // actual recording so it survives this Fragment pausing or the app backgrounding.
    private void toggleRecording() {
        if (!isAdded()) return;
        if (boundService == null || !boundService.isRecording()) {
            CsvRecordingService.start(requireContext());
            // BIND_AUTO_CREATE here is a safety net — the service is already being
            // started above; this just ensures the connection lands promptly so the
            // UI reflects "recording" immediately rather than waiting on the next resume.
            requireContext().bindService(
                    new Intent(requireContext(), CsvRecordingService.class),
                    serviceConnection, Context.BIND_AUTO_CREATE);
            String dur = formatDuration(0);
            btnRecord.setText(getString(R.string.rec_btn_stop, dur, 0));
            set(tvRecordStatus, getString(R.string.rec_status_active, dur, 0));
        } else {
            boundService.stopRecording();
            String fileName = boundService.getLastFileName();
            int n = boundService.getLastSampleCount();
            btnRecord.setText(R.string.rec_btn_start);
            String msg = getString(R.string.rec_status_saved, fileName, n);
            set(tvRecordStatus, msg);
            tvRecordStatus.announceForAccessibility(msg);
            mainHandler.removeCallbacks(recordingPoller);
        }
    }

    /** Y1: format elapsed seconds as MM:SS (or H:MM:SS past 1h) — see Formatters.duration. */
    private static String formatDuration(long elapsedSec) {
        return Formatters.duration(elapsedSec);
    }

    private final Runnable recordingPoller = new Runnable() {
        @Override public void run() {
            if (!isAdded() || boundService == null || !boundService.isRecording()) return;
            int n = boundService.getSampleCount();
            String dur = formatDuration((System.currentTimeMillis() - boundService.getStartedAtMs()) / 1000);
            if (btnRecord != null) btnRecord.setText(getString(R.string.rec_btn_stop, dur, n));
            if (tvRecordStatus != null) set(tvRecordStatus, getString(R.string.rec_status_active, dur, n));
            mainHandler.postDelayed(this, 1000);
        }
    };

    // U4: open app's system settings screen so user can grant denied permission
    private void openAppSettings() {
        if (!isAdded()) return;
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireActivity().getPackageName(), null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // ── Contextual permission requests ──────────────────────────────────────
    /** Tap-to-request entry point for a permission-gated card. Shows a rationale
     *  Snackbar first only if the system says one is warranted (i.e. the user
     *  already denied once but not permanently) — otherwise goes straight to the
     *  system dialog, since tapping a card that's already labelled "Permission
     *  needed" is self-explanatory context on the first ask. */
    private void requestPermission(ActivityResultLauncher<String> launcher, String permission, @StringRes int rationaleRes) {
        if (!isAdded()) return;
        if (shouldShowRequestPermissionRationale(permission)) {
            View root = getView();
            if (root == null) { launcher.launch(permission); return; }
            Snackbar.make(root, rationaleRes, Snackbar.LENGTH_LONG)
                    .setAction(R.string.perm_rationale_continue, v -> launcher.launch(permission))
                    .show();
        } else {
            launcher.launch(permission);
        }
    }

    private void showPermissionDeniedSnackbar() {
        if (!isAdded()) return;
        View root = getView();
        if (root == null) return;
        Snackbar.make(root, R.string.perm_denied_open_settings, Snackbar.LENGTH_LONG)
                .setAction(R.string.perm_action_settings, v -> openAppSettings())
                .show();
    }

    // ── GPS ───────────────────────────────────────────────────────────────
    private void startGps() {
        if (!isAdded()) return;
        if (ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Clear any "tap to grant" handlers set during a prior denied state
            if (tvSpeed != null)    tvSpeed.setOnClickListener(null);
            if (tvAltitude != null) tvAltitude.setOnClickListener(null);
            if (!locationRegistered) {
                // P3: 2s/5m is plenty for a UI dashboard, was 1s/0m (battery hog)
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5f, gpsListener);
                // Batch 7: NETWORK_PROVIDER as a fallback — often gives a faster fix
                // indoors/urban while GPS_PROVIDER is still acquiring a satellite lock.
                // Same listener, no extra permission needed (ACCESS_FINE_LOCATION covers it).
                try {
                    if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5f, gpsListener);
                    }
                } catch (Exception ignored) {
                    // Provider unavailable on this device — GPS_PROVIDER alone still works.
                }
                locationRegistered = true;
            }
        } else {
            String perm = getString(R.string.status_permission_needed);
            set(tvSpeed,    getString(R.string.sensor_gps_speed)    + "\n" + perm);
            set(tvAltitude, getString(R.string.sensor_gps_altitude) + "\n" + perm);
            // U4/Batch 3: tap the placeholder to request the permission contextually
            View.OnClickListener request = v -> requestPermission(
                    locationPermissionLauncher, Manifest.permission.ACCESS_FINE_LOCATION,
                    R.string.perm_rationale_location);
            if (tvSpeed != null)    tvSpeed.setOnClickListener(request);
            if (tvAltitude != null) tvAltitude.setOnClickListener(request);
        }
    }
    private void stopGps() {
        if (locationRegistered && lm != null) {
            lm.removeUpdates(gpsListener);
            locationRegistered = false;
        }
    }
    private final LocationListener gpsListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location loc) {
            if (!isAdded()) return;
            Context ctx = requireContext();
            // Batch 4: GPS recording rows (if a recording is active) are captured
            // independently by CsvRecordingService's own LocationManager registration.
            // B6: show -- when speed unknown, instead of misleading "0.0 km/h"
            if (loc.hasSpeed()) {
                float spDisp  = AppPrefs.speed(loc.getSpeed(), ctx);
                String spUnit = AppPrefs.speedUnit(ctx);
                set(tvSpeed, getString(R.string.sensor_gps_speed)
                        + String.format(Locale.US,"\n%.1f %s", spDisp, spUnit));
                set(visSpeed, String.format(Locale.US,"🚀 %.1f %s", spDisp, spUnit));
            } else {
                String spUnit = AppPrefs.speedUnit(ctx);
                set(tvSpeed,  getString(R.string.sensor_gps_speed) + "\n-- " + spUnit);
                set(visSpeed, "🚀 -- " + spUnit);
            }
            if (loc.hasAltitude()) {
                float alt = AppPrefs.altitude((float)loc.getAltitude(), ctx);
                set(tvAltitude, getString(R.string.sensor_gps_altitude)
                        + String.format(Locale.US,"\n%.1f %s", alt, AppPrefs.altUnit(ctx)));
            }
        }
        @Override public void onProviderDisabled(@NonNull String p) {
            set(tvSpeed, getString(R.string.sensor_gps_speed) + "\n" + getString(R.string.status_gps_disabled)); }
        @Override public void onProviderEnabled(@NonNull String p) {}
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
    };

    // ── Sound Level ───────────────────────────────────────────────────────
    private void startSoundMeter() {
        if (!isAdded()) return;
        // C4: prevent two recording threads if onResume fires twice without onPause
        if (soundRunning) return;
        if (ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            set(tvSoundLevel, getString(R.string.sensor_sound_level) + "\n" + getString(R.string.status_permission_needed));
            set(visSound, "🎤\n" + getString(R.string.status_na));
            // U4/Batch 3: tap to request the permission contextually
            View.OnClickListener request = v -> requestPermission(
                    audioPermissionLauncher, Manifest.permission.RECORD_AUDIO,
                    R.string.perm_rationale_audio);
            if (tvSoundLevel != null) tvSoundLevel.setOnClickListener(request);
            if (visSound != null)     visSound.setOnClickListener(request);
            return;
        }
        // Permission granted — clear any prior click handler from denied state
        if (tvSoundLevel != null) tvSoundLevel.setOnClickListener(null);
        if (visSound != null)     visSound.setOnClickListener(null);
        int bufSize = AudioRecord.getMinBufferSize(44100,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufSize == AudioRecord.ERROR_BAD_VALUE || bufSize <= 0) return;

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 4);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) return;

            soundRunning = true;
            soundThread = new Thread(() -> {
                audioRecord.startRecording();
                short[] buf = new short[bufSize];
                // R2: read continuously (no Thread.sleep). The AudioRecord.read() call
                // blocks until a buffer's worth of data is available — natural pacing,
                // no dropped audio frames. UI is throttled to ~5Hz with timestamps.
                long lastPostNs = 0L;
                final long POST_INTERVAL_NS = 200_000_000L; // 200ms
                while (soundRunning && !Thread.interrupted()) {
                    int read = audioRecord.read(buf, 0, buf.length);
                    if (read <= 0) continue;
                    long sum = 0;
                    for (int i = 0; i < read; i++) sum += (long)buf[i] * buf[i];
                    double rms = Math.sqrt((double)sum / read);
                    // Convert to dB (relative to max 32768, ref 20µPa approx)
                    double db = rms > 1 ? 20 * Math.log10(rms / 32768.0) + 90.0 : 30.0;
                    final double dbClamp = Math.max(30, Math.min(120, db));
                    long now = System.nanoTime();
                    if (now - lastPostNs < POST_INTERVAL_NS) continue;
                    lastPostNs = now;
                    final int noiseRes = dbClamp < 40 ? R.string.sound_silent :
                                         dbClamp < 55 ? R.string.sound_quiet :
                                         dbClamp < 70 ? R.string.sound_normal :
                                         dbClamp < 85 ? R.string.sound_loud : R.string.sound_very_loud;
                    final int bar = (int)((dbClamp - 30) * 100 / 90);
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        String noise = getString(noiseRes);
                        set(tvSoundLevel, getString(R.string.sensor_sound_level) + String.format(Locale.US,
                                "\n%.0f dB\n%s", dbClamp, noise));
                        set(visSound, String.format(Locale.US,
                                "🎤\n%.0fdB\n%s", dbClamp,
                                noise.contains(" ") ? noise.split(" ")[0] : noise));
                        bar(pbSound, bar);
                    });
                }
                try { audioRecord.stop(); } catch (Exception ignored) {}
                try { audioRecord.release(); } catch (Exception ignored) {}
                audioRecord = null;
            });
            soundThread.setDaemon(true);
            soundThread.start();
        } catch (Exception e) {
            set(tvSoundLevel, getString(R.string.sensor_sound_level) + "\nError: " + e.getMessage());
        }
    }

    private void stopSoundMeter() {
        soundRunning = false;
        if (soundThread != null) { soundThread.interrupt(); soundThread = null; }
    }

    // ── Sensor Events ─────────────────────────────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent ev) {
        if (!isAdded()) return;
        // Z2: defensive — some OEM drivers occasionally deliver null/empty arrays
        if (ev == null || ev.values == null || ev.values.length == 0) return;
        Context ctx = requireContext();
        int t = ev.sensor.getType();

        switch (t) {
            case Sensor.TYPE_ACCELEROMETER: {
                lastAccel = ev.values.clone();
                float mag = mag3(ev.values);
                float g   = mag / SensorManager.GRAVITY_EARTH;
                int sRes  = g < 0.05f ? R.string.state_still  :
                            g < 1.15f ? R.string.state_normal :
                            g < 2.5f  ? R.string.state_moving :
                            g < 5f    ? R.string.state_fast   : R.string.state_high_g;
                String s = getString(sRes);
                set(tvAccel, getString(R.string.sensor_accelerometer) + String.format(Locale.US,
                        "  m/s²\nX:%+.3f  Y:%+.3f  Z:%+.3f\n|a|=%.3f (%.2fg)  %s",
                        ev.values[0], ev.values[1], ev.values[2], mag, g, s));
                set(visAccel, String.format(Locale.US,"📳\n%.2fg\n%s", g, s));
                bar(pbAccel, (int)(Math.min(g, 5f) * 20));
                break;
            }
            case Sensor.TYPE_MAGNETIC_FIELD: {
                lastMag = ev.values.clone();
                float mag = mag3(ev.values);
                String compass = computeCompass();
                // Update live compass
                if (compassView != null) {
                    float[] R = new float[9], I = new float[9];
                    if (SensorManager.getRotationMatrix(R, I, lastAccel, lastMag)) {
                        float[] o = new float[3];
                        SensorManager.getOrientation(R, o);
                        float bearing = (float)((Math.toDegrees(o[0]) + 360) % 360);
                        compassView.setBearing(bearing);
                    }
                }
                int qRes = mag < 25 ? R.string.state_weak   :
                           mag < 65 ? R.string.state_normal :
                           mag < 200? R.string.state_strong : R.string.state_interference;
                String q = getString(qRes);
                set(tvMag, getString(R.string.sensor_magnetometer) + String.format(Locale.US,
                        "  µT\nX:%+.2f  Y:%+.2f  Z:%+.2f\n|B|=%.2f (%s)\n%s: %s",
                        ev.values[0], ev.values[1], ev.values[2], mag, q,
                        getString(R.string.sensor_compass_label), compass));
                break;
            }
            case Sensor.TYPE_GYROSCOPE: {
                float mag = mag3(ev.values);
                float dps = (float)Math.toDegrees(mag);
                int sRes  = dps < 1   ? R.string.state_still     :
                            dps < 30  ? R.string.state_slow      :
                            dps < 120 ? R.string.state_medium    : R.string.state_fast_plain;
                String s = getString(sRes);
                set(tvGyro, getString(R.string.sensor_gyroscope) + String.format(Locale.US,
                        "  rad/s\nX:%+.4f  Y:%+.4f  Z:%+.4f\nω=%.2f rad/s (%.0f°/s)  %s",
                        ev.values[0], ev.values[1], ev.values[2], mag, dps, s));
                set(visGyro, String.format(Locale.US,"🌀\n%.0f°/s\n%s", dps, s));
                bar(pbGyro, (int)Math.min(dps / 3, 100));
                break;
            }
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED: {
                // B8: defensive check — spec promises 6, but driver bugs happen
                if (ev.values.length < 6) break;
                // values[0-2]=raw, values[3-5]=drift estimate
                float drift = mag3(new float[]{ev.values[3],ev.values[4],ev.values[5]});
                set(tvGyroUncal, getString(R.string.sensor_gyro_uncal) + String.format(Locale.US,
                        "  rad/s\nX:%+.4f  Y:%+.4f  Z:%+.4f\nDrift: %.4f rad/s",
                        ev.values[0], ev.values[1], ev.values[2], drift));
                break;
            }
            case Sensor.TYPE_LIGHT: {
                float lux = ev.values[0];
                String icon = luxIcon(lux), desc = luxDesc(lux);
                set(tvLight, getString(R.string.sensor_light) + String.format(Locale.US,
                        "\n%.1f lx\n%s %s", lux, icon, desc));
                set(visLight, String.format(Locale.US,"%s\n%.0flx\n%s", icon, lux, desc));
                bar(pbLight, (int)(Math.min(Math.log10(Math.max(lux,1))/5.0,1)*100));
                break;
            }
            case Sensor.TYPE_PRESSURE: {
                float hpa   = ev.values[0];
                float altM  = (float)(44330*(1-Math.pow(hpa/1013.25,0.1903)));
                float pDisp = AppPrefs.pressure(hpa, ctx);
                float aDisp = AppPrefs.altitude(altM, ctx);
                int wxRes = hpa>1022 ? R.string.weather_clear  :
                            hpa>1008 ? R.string.weather_normal :
                            hpa>995  ? R.string.weather_cloudy : R.string.weather_storm;
                String wx = getString(wxRes);
                set(tvPressure, getString(R.string.sensor_barometer) + String.format(Locale.US,
                        "\n%.2f %s\nAlt≈%.0f %s\n%s",
                        pDisp, AppPrefs.pressureUnit(ctx), aDisp, AppPrefs.altUnit(ctx), wx));
                set(visPressure, String.format(Locale.US,"🌡\n%.1f\n%s",pDisp,AppPrefs.pressureUnit(ctx)));
                bar(pbPressure, (int)Math.max(0,Math.min(100,(hpa-950)/1.2f)));
                break;
            }
            case Sensor.TYPE_GRAVITY: {
                float mag  = mag3(ev.values);
                float tilt = (float)Math.toDegrees(Math.acos(
                        Math.abs(ev.values[2])/Math.max(mag,0.001f)));
                int oRes = tilt<8  ? R.string.orient_flat     :
                           tilt<45 ? R.string.orient_tilted   :
                           tilt<80 ? R.string.orient_standing : R.string.orient_sideways;
                String o = getString(oRes);
                set(tvGravity, getString(R.string.sensor_gravity) + String.format(Locale.US,
                        "  m/s²\nX:%+.3f  Y:%+.3f  Z:%+.3f\nTilt:%.1f°  %s",
                        ev.values[0], ev.values[1], ev.values[2], tilt, o));
                set(visGravity, String.format(Locale.US,"📐\n%.0f°\n%s",tilt,o));
                bar(pbGravity, (int)Math.min(tilt,100));
                break;
            }
            case Sensor.TYPE_LINEAR_ACCELERATION: {
                float mag = mag3(ev.values);
                int sRes = mag<0.05f ? R.string.state_still  :
                           mag<1f    ? R.string.state_slight :
                           mag<5f    ? R.string.state_moving : R.string.state_fast;
                String s = getString(sRes);
                set(tvLinAccel, getString(R.string.sensor_linear_accel) + String.format(Locale.US,
                        "\nX:%+.3f  Y:%+.3f  Z:%+.3f\n|a|=%.3f  %s",
                        ev.values[0], ev.values[1], ev.values[2], mag, s));
                set(visLinAccel, String.format(Locale.US,"⚡\n%.2f\nm/s²",mag));
                bar(pbLinAccel, (int)Math.min(mag*10,100));
                break;
            }
            case Sensor.TYPE_ROTATION_VECTOR: {
                float[] mat=new float[9],ang=new float[3];
                SensorManager.getRotationMatrixFromVector(mat, ev.values);
                SensorManager.getOrientation(mat, ang);
                int az=((int)Math.toDegrees(ang[0])+360)%360;
                int pt=(int)Math.toDegrees(ang[1]);
                int rl=(int)Math.toDegrees(ang[2]);
                set(tvRot, getString(R.string.sensor_rotation_vector) + String.format(Locale.US,
                        "\nAzimuth:%d° (%s)  Pitch:%d°  Roll:%d°",
                        az,compassLabel(az),pt,rl));
                set(visRot, String.format(Locale.US,"🔄\n%s\nP:%d° R:%d°",compassLabel(az),pt,rl));
                break;
            }
            case Sensor.TYPE_GAME_ROTATION_VECTOR: {
                float[] mat=new float[9],ang=new float[3];
                SensorManager.getRotationMatrixFromVector(mat, ev.values);
                SensorManager.getOrientation(mat, ang);
                set(tvGameRot, getString(R.string.sensor_game_rotation) + String.format(Locale.US,
                        "\nAz:%.1f°  Pitch:%.1f°  Roll:%.1f°",
                        Math.toDegrees(ang[0]),Math.toDegrees(ang[1]),Math.toDegrees(ang[2])));
                break;
            }
            case Sensor.TYPE_STEP_DETECTOR: {
                stepDetCount++;
                saveCounters(); // R4
                set(tvStep,  getString(R.string.sensor_step_detector) + "\n"
                        + getString(R.string.step_det_session_format, stepDetCount));
                set(visStep, getString(R.string.step_det_short_format, stepDetCount));
                bar(pbStep, Math.min(stepDetCount * 10, 10000));
                break;
            }
            case Sensor.TYPE_STEP_COUNTER: {
                long total=(long)ev.values[0];
                // C1: detect device reboot — Step Counter resets to 0 across boots
                if (stepCntBase < 0 || total < stepCntBase) {
                    stepCntBase = total;
                    saveCounters(); // R4: persist new base
                }
                long session=total-stepCntBase;
                set(tvStepCounter, getString(R.string.sensor_step_counter) + "\n"
                        + getString(R.string.step_counter_format, session, total));
                break;
            }
            case Sensor.TYPE_PROXIMITY: {
                float dist=ev.values[0], maxD=ev.sensor.getMaximumRange();
                boolean near=dist<(maxD*0.5f)||dist==0f;
                set(tvProximity, getString(R.string.sensor_proximity) + String.format(Locale.US,
                        "\n%s\nValue:%.1f  Max:%.1f",
                        getString(near?R.string.proximity_near:R.string.proximity_far), dist, maxD));
                set(visProximity, getString(near?R.string.proximity_near_short:R.string.proximity_far_short));
                bar(pbProximity, near?100:5);
                break;
            }
            case Sensor.TYPE_RELATIVE_HUMIDITY: {
                float rh=ev.values[0];
                int dRes = rh<20 ? R.string.humidity_very_dry    :
                           rh<40 ? R.string.humidity_dry         :
                           rh<60 ? R.string.humidity_comfortable :
                           rh<75 ? R.string.humidity_humid       : R.string.humidity_very_humid;
                String d = getString(dRes);
                set(tvHumidity, getString(R.string.sensor_humidity) + String.format(Locale.US,"\n%.1f%%\n%s",rh,d));
                set(visHumidity, String.format(Locale.US,"💧\n%.0f%%",rh));
                bar(pbHumidity, (int)rh);
                break;
            }
            case Sensor.TYPE_AMBIENT_TEMPERATURE: {
                float c=ev.values[0];
                // Some devices report a bogus sentinel (e.g. uninitialized float) instead of
                // omitting the sensor entirely — reject anything outside a plausible ambient
                // range, matching ThermalZones.isPlausible()'s guard for the same OEM quirk.
                if (c < -40f || c > 85f) {
                    set(tvAmbientTemp, getString(R.string.sensor_ambient_temp) + "\n" + getString(R.string.status_not_available));
                    break;
                }
                float disp=AppPrefs.temp(c,ctx);
                String u=AppPrefs.tempUnit(ctx);
                int dRes = c<5  ? R.string.ambient_cold        :
                           c<15 ? R.string.ambient_cool        :
                           c<22 ? R.string.ambient_comfortable :
                           c<30 ? R.string.ambient_warm        : R.string.ambient_hot;
                String d = getString(dRes);
                set(tvAmbientTemp, getString(R.string.sensor_ambient_temp) + String.format(Locale.US,"\n%.1f %s\n%s",disp,u,d));
                set(visAmbientTemp, String.format(Locale.US,"🌡\n%.0f%s",disp,u));
                bar(pbAmbientTemp, (int)Math.max(0,Math.min(100,(c+10)*2)));
                break;
            }
            case Sensor.TYPE_HEART_RATE: {
                if (ev.values.length < 1) break; // B8 defensive
                int bpm=(int)ev.values[0];
                if(ev.accuracy<1||bpm<=0){
                    set(tvHeartRate, getString(R.string.sensor_heart_rate) + "\n" + getString(R.string.hr_place_finger));
                    set(visHeartRate,"❤️\n--bpm");
                } else {
                    int zRes = bpm<60  ? R.string.hr_zone_low      :
                               bpm<100 ? R.string.hr_zone_normal   :
                               bpm<140 ? R.string.hr_zone_elevated : R.string.hr_zone_high;
                    String z = getString(zRes);
                    set(tvHeartRate, getString(R.string.sensor_heart_rate) + String.format(Locale.US,"\n%d bpm\n%s",bpm,z));
                    set(visHeartRate, String.format(Locale.US,"❤️\n%dbpm",bpm));
                }
                break;
            }
            // v1.2.0 — uncalibrated accel: values[0-2] = raw, values[3-5] = bias
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED: {
                if (ev.values.length < 6) break;
                float bias = mag3(new float[]{ev.values[3],ev.values[4],ev.values[5]});
                set(tvAccelUncal, getString(R.string.sensor_accel_uncal) + String.format(Locale.US,
                        "  m/s²\nX:%+.3f  Y:%+.3f  Z:%+.3f\nBias:%.3f",
                        ev.values[0], ev.values[1], ev.values[2], bias));
                break;
            }
            // v1.2.0 — uncalibrated mag: values[0-2] = raw, values[3-5] = hard-iron bias
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED: {
                if (ev.values.length < 6) break;
                float bias = mag3(new float[]{ev.values[3],ev.values[4],ev.values[5]});
                set(tvMagUncal, getString(R.string.sensor_mag_uncal) + String.format(Locale.US,
                        "  µT\nX:%+.2f  Y:%+.2f  Z:%+.2f\nHard-iron bias:%.2f",
                        ev.values[0], ev.values[1], ev.values[2], bias));
                break;
            }
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR: {
                float[] mat = new float[9], ang = new float[3];
                SensorManager.getRotationMatrixFromVector(mat, ev.values);
                SensorManager.getOrientation(mat, ang);
                int az = ((int)Math.toDegrees(ang[0]) + 360) % 360;
                set(tvGeomagRot, getString(R.string.sensor_geomag_rotation) + String.format(Locale.US,
                        "\nAzimuth:%d° (%s)\nNo gyro — lower battery", az, compassLabel(az)));
                break;
            }
            default: {
                if(t==TYPE_TILT_DETECTOR){
                    tiltCount++;
                    saveCounters(); // R4
                    set(tvTilt, getString(R.string.sensor_tilt_detector) + "\n"
                            + getString(R.string.tilt_count_format, tiltCount));
                    set(visTilt, getString(R.string.tilt_count_short_format, tiltCount));
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && t == Sensor.TYPE_HINGE_ANGLE) {
                    if (ev.values.length < 1) break;
                    float deg = ev.values[0];
                    int stateRes = deg < 5  ? R.string.hinge_state_closed :
                                   deg > 175 ? R.string.hinge_state_flat   : R.string.hinge_state_open;
                    set(tvHingeAngle, getString(R.string.sensor_hinge_angle) + "\n"
                            + getString(R.string.hinge_angle_format, deg, getString(stateRes)));
                }
                break;
            }
        }
        // Batch 4: CSV recording (if active) is captured independently by
        // CsvRecordingService's own sensor registration — no forwarding needed here.
    }

    /** Q2: when the magnetometer loses calibration (e.g. near metal), badge the compass
     *  text so the user understands why the heading drifts. Other sensors don't have a
     *  prominent place to display it. */
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (!isAdded() || sensor == null) return;
        if (sensor.getType() != Sensor.TYPE_MAGNETIC_FIELD) return;
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            // Don't overwrite a real reading — append, but only if mag text already exists
            if (tvMag != null && tvMag.getText().length() > 0) {
                tvMag.setText(tvMag.getText() + "\n" + getString(R.string.accuracy_low_warning));
                // Q5: announce to TalkBack so users with screen readers know
                tvMag.announceForAccessibility(getString(R.string.accuracy_low_warning));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private float mag3(float[] v){return(float)Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);}
    private void set(TextView tv,String t){if(tv!=null)tv.setText(t);}
    private void bar(ProgressBar pb,int val){if(pb!=null)pb.setProgress(Math.max(0,Math.min(pb.getMax(),val)));}

    private String computeCompass(){
        float[]R=new float[9],I=new float[9];
        if(SensorManager.getRotationMatrix(R,I,lastAccel,lastMag)){
            float[]o=new float[3];SensorManager.getOrientation(R,o);
            int deg=(int)((Math.toDegrees(o[0])+360)%360);
            return compassLabel(deg)+" "+deg+"°";
        } return "--";
    }
    private String compassLabel(int deg){
        String[]d={"N","NNE","NE","ENE","E","ESE","SE","SSE",
                   "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        return d[Math.round(deg/22.5f)%16];
    }
    private String luxIcon(float lux){
        if(lux<1)return"⬛";if(lux<10)return"🌑";if(lux<50)return"🕯️";
        if(lux<200)return"💡";if(lux<1000)return"🔆";if(lux<10000)return"⛅";return"☀️";
    }
    private String luxDesc(float lux){
        int res = lux<1     ? R.string.light_pitch_dark    :
                  lux<10    ? R.string.light_very_dark     :
                  lux<50    ? R.string.light_dim           :
                  lux<200   ? R.string.light_indoor        :
                  lux<1000  ? R.string.light_bright_indoor :
                  lux<10000 ? R.string.light_overcast      :
                  lux<40000 ? R.string.light_daylight      : R.string.light_direct_sun;
        return getString(res);
    }
}
