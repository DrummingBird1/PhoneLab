package com.sensolab.devicemonitor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.*;
import android.location.*;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import java.util.Locale;

public class SensorsFragment extends Fragment implements SensorEventListener {

    private static final int TYPE_TILT_DETECTOR = 22;

    private SensorManager sm;
    private LocationManager lm;
    private boolean locationRegistered = false;

    // Sensor handles
    private Sensor sAccel, sMag, sGyro, sLight, sPressure, sGravity,
                   sLinAccel, sRotVec, sGameRot, sStep, sStepCounter,
                   sTilt, sProximity, sHumidity, sAmbientTemp, sHeartRate,
                   sSignMotion, sGyroUncal;

    // Textual views
    private LinearLayout layoutTextual, layoutVisual;
    private TextView tvAccel, tvGyro, tvMag, tvLight, tvPressure,
                     tvGravity, tvLinAccel, tvRot, tvGameRot,
                     tvStep, tvStepCounter, tvTilt, tvProximity,
                     tvHumidity, tvAmbientTemp, tvSignMotion, tvHeartRate,
                     tvSpeed, tvAltitude, tvSoundLevel, tvGyroUncal;

    // Visual views
    private TextView visAccel, visGyro, visLinAccel, visLight, visPressure, visGravity,
                     visProximity, visHumidity, visAmbientTemp,
                     visStep, visRot, visTilt, visSound, visHeartRate, visSigMotion,
                     visSpeed;
    private ProgressBar pbAccel, pbGyro, pbLinAccel, pbLight, pbPressure, pbGravity,
                        pbProximity, pbHumidity, pbAmbientTemp, pbStep, pbSound;

    // Live compass
    private CompassView compassView;

    // State
    private int   stepDetCount = 0;
    private long  stepCntBase  = -1;
    private int   tiltCount    = 0;
    private int   sigMotCount  = 0;
    private float[] lastAccel  = {0, 0, 9.8f};
    private float[] lastMag    = {0, 30, 0};

    // Sound level via AudioRecord
    private AudioRecord audioRecord;
    private volatile boolean soundRunning = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread soundThread;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        View v = inf.inflate(R.layout.fragment_sensors, container, false);
        bindViews(v);
        lm = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        return v;
    }

    private void bindViews(View v) {
        layoutTextual  = v.findViewById(R.id.layout_textual);
        layoutVisual   = v.findViewById(R.id.layout_visual);
        // Textual
        tvAccel        = v.findViewById(R.id.tv_accelerometer);
        tvGyro         = v.findViewById(R.id.tv_gyroscope);
        tvMag          = v.findViewById(R.id.tv_magnetic_field);
        tvLight        = v.findViewById(R.id.tv_light);
        tvPressure     = v.findViewById(R.id.tv_pressure);
        tvGravity      = v.findViewById(R.id.tv_gravity);
        tvLinAccel     = v.findViewById(R.id.tv_linear_accel);
        tvRot          = v.findViewById(R.id.tv_rotation_vector);
        tvGameRot      = v.findViewById(R.id.tv_game_rotation);
        tvGyroUncal    = v.findViewById(R.id.tv_gyro_uncal);
        tvStep         = v.findViewById(R.id.tv_step_detector);
        tvStepCounter  = v.findViewById(R.id.tv_step_counter);
        tvTilt         = v.findViewById(R.id.tv_tilt_detector);
        tvProximity    = v.findViewById(R.id.tv_proximity);
        tvHumidity     = v.findViewById(R.id.tv_humidity);
        tvAmbientTemp  = v.findViewById(R.id.tv_ambient_temp);
        tvSignMotion   = v.findViewById(R.id.tv_sig_motion);
        tvHeartRate    = v.findViewById(R.id.tv_heart_rate);
        tvSoundLevel   = v.findViewById(R.id.tv_sound_level);
        tvSpeed        = v.findViewById(R.id.tv_speed);
        tvAltitude     = v.findViewById(R.id.tv_altitude);
        // Visual
        compassView    = v.findViewById(R.id.compass_view);
        visAccel       = v.findViewById(R.id.vis_accel);
        visGyro        = v.findViewById(R.id.vis_gyro);
        visLinAccel    = v.findViewById(R.id.vis_lin_accel);
        visLight       = v.findViewById(R.id.vis_light);
        visPressure    = v.findViewById(R.id.vis_pressure);
        visGravity     = v.findViewById(R.id.vis_gravity);
        visProximity   = v.findViewById(R.id.vis_proximity);
        visHumidity    = v.findViewById(R.id.vis_humidity);
        visAmbientTemp = v.findViewById(R.id.vis_ambient_temp);
        visStep        = v.findViewById(R.id.vis_step);
        visRot         = v.findViewById(R.id.vis_rot);
        visTilt        = v.findViewById(R.id.vis_tilt);
        visSound       = v.findViewById(R.id.vis_sound);
        visHeartRate   = v.findViewById(R.id.vis_heart_rate);
        visSigMotion   = v.findViewById(R.id.vis_sig_motion);
        visSpeed       = v.findViewById(R.id.vis_speed);
        // Bars
        pbAccel        = v.findViewById(R.id.pb_accel);
        pbGyro         = v.findViewById(R.id.pb_gyro);
        pbLinAccel     = v.findViewById(R.id.pb_lin_accel);
        pbLight        = v.findViewById(R.id.pb_light);
        pbPressure     = v.findViewById(R.id.pb_pressure);
        pbGravity      = v.findViewById(R.id.pb_gravity);
        pbProximity    = v.findViewById(R.id.pb_proximity);
        pbHumidity     = v.findViewById(R.id.pb_humidity);
        pbAmbientTemp  = v.findViewById(R.id.pb_ambient_temp);
        pbStep         = v.findViewById(R.id.pb_step);
        pbSound        = v.findViewById(R.id.pb_sound);
    }

    @Override public void onResume() {
        super.onResume();
        applyMode();
        registerSensors();
        startGps();
        startSoundMeter();
    }

    @Override public void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
        stopGps();
        stopSoundMeter();
        mainHandler.removeCallbacksAndMessages(null);
    }

    private void applyMode() {
        boolean vis = AppPrefs.isVisual(requireContext());
        layoutTextual.setVisibility(vis ? View.GONE  : View.VISIBLE);
        layoutVisual .setVisibility(vis ? View.VISIBLE : View.GONE);
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

        reg(sAccel,       SensorManager.SENSOR_DELAY_UI,     tvAccel,       "Accelerometer");
        reg(sMag,         SensorManager.SENSOR_DELAY_UI,     tvMag,         "Magnetometer");
        reg(sGyro,        SensorManager.SENSOR_DELAY_UI,     tvGyro,        "Gyroscope");
        reg(sLight,       SensorManager.SENSOR_DELAY_UI,     tvLight,       "Light Sensor");
        reg(sPressure,    SensorManager.SENSOR_DELAY_UI,     tvPressure,    "Barometer");
        reg(sGravity,     SensorManager.SENSOR_DELAY_UI,     tvGravity,     "Gravity");
        reg(sLinAccel,    SensorManager.SENSOR_DELAY_UI,     tvLinAccel,    "Linear Accel");
        reg(sRotVec,      SensorManager.SENSOR_DELAY_UI,     tvRot,         "Rotation Vector");
        reg(sGameRot,     SensorManager.SENSOR_DELAY_UI,     tvGameRot,     "Game Rotation");
        reg(sGyroUncal,   SensorManager.SENSOR_DELAY_UI,     tvGyroUncal,   "Gyro Uncalibrated");
        reg(sStep,        SensorManager.SENSOR_DELAY_NORMAL, tvStep,        "Step Detector");
        reg(sStepCounter, SensorManager.SENSOR_DELAY_NORMAL, tvStepCounter, "Step Counter");
        reg(sTilt,        SensorManager.SENSOR_DELAY_NORMAL, tvTilt,        "Tilt Detector");
        reg(sProximity,   SensorManager.SENSOR_DELAY_UI,     tvProximity,   "Proximity");
        reg(sHumidity,    SensorManager.SENSOR_DELAY_UI,     tvHumidity,    "Humidity");
        reg(sAmbientTemp, SensorManager.SENSOR_DELAY_UI,     tvAmbientTemp, "Ambient Temp");
        reg(sHeartRate,   SensorManager.SENSOR_DELAY_NORMAL, tvHeartRate,   "Heart Rate");

        if (sSignMotion != null) {
            sm.requestTriggerSensor(sigMotListener, sSignMotion);
            set(tvSignMotion, "Significant Motion\nWaiting…");
        } else {
            set(tvSignMotion, "Significant Motion\nNot available");
            set(visSigMotion, "🏃\nN/A");
        }
    }

    private void reg(Sensor s, int delay, TextView tv, String name) {
        if (s != null) sm.registerListener(this, s, delay);
        else           set(tv, name + "\nNot available");
    }

    private final TriggerEventListener sigMotListener = new TriggerEventListener() {
        @Override public void onTrigger(TriggerEvent ev) {
            if (!isAdded()) return;
            sigMotCount++;
            set(tvSignMotion, "Significant Motion\n🏃 Detected × " + sigMotCount);
            set(visSigMotion, "🏃\n×" + sigMotCount);
            if (sSignMotion != null && sm != null)
                sm.requestTriggerSensor(this, sSignMotion);
        }
    };

    // ── GPS ───────────────────────────────────────────────────────────────
    private void startGps() {
        if (!isAdded()) return;
        if (ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (!locationRegistered) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, gpsListener);
                locationRegistered = true;
            }
        } else {
            set(tvSpeed, "GPS Speed\nPermission needed");
            set(tvAltitude, "GPS Altitude\nPermission needed");
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
            float spDisp = AppPrefs.speed(loc.hasSpeed() ? loc.getSpeed() : 0f, ctx);
            String spUnit = AppPrefs.speedUnit(ctx);
            set(tvSpeed,  String.format(Locale.US,"🚀 GPS Speed\n%.1f %s", spDisp, spUnit));
            set(visSpeed, String.format(Locale.US,"🚀 %.1f %s", spDisp, spUnit));
            if (loc.hasAltitude()) {
                float alt = AppPrefs.altitude((float)loc.getAltitude(), ctx);
                set(tvAltitude, String.format(Locale.US,"⛰️ GPS Altitude\n%.1f %s",
                        alt, AppPrefs.altUnit(ctx)));
            }
        }
        @Override public void onProviderDisabled(@NonNull String p) {
            set(tvSpeed, "GPS Speed\nGPS disabled"); }
        @Override public void onProviderEnabled(@NonNull String p) {}
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
    };

    // ── Sound Level ───────────────────────────────────────────────────────
    private void startSoundMeter() {
        if (!isAdded()) return;
        if (ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            set(tvSoundLevel, "🎤 Sound Level\nPermission needed");
            set(visSound, "🎤\nN/A");
            return;
        }
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
                while (soundRunning && !Thread.interrupted()) {
                    int read = audioRecord.read(buf, 0, buf.length);
                    if (read > 0) {
                        long sum = 0;
                        for (int i = 0; i < read; i++) sum += (long)buf[i] * buf[i];
                        double rms = Math.sqrt((double)sum / read);
                        // Convert to dB (relative to max 32768, ref 20µPa approx)
                        double db = rms > 1 ? 20 * Math.log10(rms / 32768.0) + 90.0 : 30.0;
                        double dbClamp = Math.max(30, Math.min(120, db));
                        String noise = dbClamp < 40 ? "🤫 Silent" :
                                       dbClamp < 55 ? "🔈 Quiet" :
                                       dbClamp < 70 ? "🔉 Normal" :
                                       dbClamp < 85 ? "🔊 Loud" : "📢 Very loud!";
                        int bar = (int)((dbClamp - 30) * 100 / 90);
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            set(tvSoundLevel, String.format(Locale.US,
                                    "🎤 Sound Level\n%.0f dB\n%s", dbClamp, noise));
                            set(visSound, String.format(Locale.US,
                                    "🎤\n%.0fdB\n%s", dbClamp,
                                    noise.contains(" ") ? noise.split(" ")[0] : noise));
                            bar(pbSound, bar);
                        });
                    }
                    try { Thread.sleep(200); } catch (InterruptedException e) { break; }
                }
                try { audioRecord.stop(); } catch (Exception ignored) {}
                try { audioRecord.release(); } catch (Exception ignored) {}
                audioRecord = null;
            });
            soundThread.setDaemon(true);
            soundThread.start();
        } catch (Exception e) {
            set(tvSoundLevel, "🎤 Sound Level\nError: " + e.getMessage());
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
        Context ctx = requireContext();
        int t = ev.sensor.getType();

        switch (t) {
            case Sensor.TYPE_ACCELEROMETER: {
                lastAccel = ev.values.clone();
                float mag = mag3(ev.values);
                float g   = mag / SensorManager.GRAVITY_EARTH;
                String s  = g < 0.05f ? "Still" : g < 1.15f ? "Normal" :
                            g < 2.5f  ? "Moving" : g < 5f ? "Fast!" : "High-G!";
                set(tvAccel, String.format(Locale.US,
                        "Accelerometer  m/s²\nX:%+.3f  Y:%+.3f  Z:%+.3f\n|a|=%.3f (%.2fg)  %s",
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
                String q = mag < 25 ? "Weak" : mag < 65 ? "Normal" : mag < 200 ? "Strong" : "⚠️ Interference?";
                set(tvMag, String.format(Locale.US,
                        "Magnetometer  µT\nX:%+.2f  Y:%+.2f  Z:%+.2f\n|B|=%.2f (%s)\nCompass: %s",
                        ev.values[0], ev.values[1], ev.values[2], mag, q, compass));
                break;
            }
            case Sensor.TYPE_GYROSCOPE: {
                float mag = mag3(ev.values);
                float dps = (float)Math.toDegrees(mag);
                String s  = dps < 1 ? "Still" : dps < 30 ? "Slow" :
                            dps < 120 ? "Medium" : "Fast";
                set(tvGyro, String.format(Locale.US,
                        "Gyroscope  rad/s\nX:%+.4f  Y:%+.4f  Z:%+.4f\nω=%.2f rad/s (%.0f°/s)  %s",
                        ev.values[0], ev.values[1], ev.values[2], mag, dps, s));
                set(visGyro, String.format(Locale.US,"🌀\n%.0f°/s\n%s", dps, s));
                bar(pbGyro, (int)Math.min(dps / 3, 100));
                break;
            }
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED: {
                // values[0-2]=raw, values[3-5]=drift estimate
                float drift = mag3(new float[]{ev.values[3],ev.values[4],ev.values[5]});
                set(tvGyroUncal, String.format(Locale.US,
                        "Gyro Uncalibrated  rad/s\nX:%+.4f  Y:%+.4f  Z:%+.4f\nDrift: %.4f rad/s",
                        ev.values[0], ev.values[1], ev.values[2], drift));
                break;
            }
            case Sensor.TYPE_LIGHT: {
                float lux = ev.values[0];
                String icon = luxIcon(lux), desc = luxDesc(lux);
                set(tvLight, String.format(Locale.US,
                        "Light Sensor\n%.1f lx\n%s %s", lux, icon, desc));
                set(visLight, String.format(Locale.US,"%s\n%.0flx\n%s", icon, lux, desc));
                bar(pbLight, (int)(Math.min(Math.log10(Math.max(lux,1))/5.0,1)*100));
                break;
            }
            case Sensor.TYPE_PRESSURE: {
                float hpa   = ev.values[0];
                float altM  = (float)(44330*(1-Math.pow(hpa/1013.25,0.1903)));
                float pDisp = AppPrefs.pressure(hpa, ctx);
                float aDisp = AppPrefs.altitude(altM, ctx);
                String wx = hpa>1022?"☀️ Clear":hpa>1008?"🌤 Normal":hpa>995?"🌧 Cloudy":"⛈ Storm";
                set(tvPressure, String.format(Locale.US,
                        "Barometer\n%.2f %s\nAlt≈%.0f %s\n%s",
                        pDisp, AppPrefs.pressureUnit(ctx), aDisp, AppPrefs.altUnit(ctx), wx));
                set(visPressure, String.format(Locale.US,"🌡\n%.1f\n%s",pDisp,AppPrefs.pressureUnit(ctx)));
                bar(pbPressure, (int)Math.max(0,Math.min(100,(hpa-950)/1.2f)));
                break;
            }
            case Sensor.TYPE_GRAVITY: {
                float mag  = mag3(ev.values);
                float tilt = (float)Math.toDegrees(Math.acos(
                        Math.abs(ev.values[2])/Math.max(mag,0.001f)));
                String o = tilt<8?"Flat":tilt<45?"Tilted":tilt<80?"Standing":"Sideways";
                set(tvGravity, String.format(Locale.US,
                        "Gravity Vector  m/s²\nX:%+.3f  Y:%+.3f  Z:%+.3f\nTilt:%.1f°  %s",
                        ev.values[0], ev.values[1], ev.values[2], tilt, o));
                set(visGravity, String.format(Locale.US,"📐\n%.0f°\n%s",tilt,o));
                bar(pbGravity, (int)Math.min(tilt,100));
                break;
            }
            case Sensor.TYPE_LINEAR_ACCELERATION: {
                float mag = mag3(ev.values);
                String s  = mag<0.05f?"Still":mag<1f?"Slight":mag<5f?"Moving":"Fast!";
                set(tvLinAccel, String.format(Locale.US,
                        "Linear Accel (no gravity)\nX:%+.3f  Y:%+.3f  Z:%+.3f\n|a|=%.3f  %s",
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
                set(tvRot, String.format(Locale.US,
                        "Rotation Vector\nAzimuth:%d° (%s)  Pitch:%d°  Roll:%d°",
                        az,compassLabel(az),pt,rl));
                set(visRot, String.format(Locale.US,"🔄\n%s\nP:%d° R:%d°",compassLabel(az),pt,rl));
                break;
            }
            case Sensor.TYPE_GAME_ROTATION_VECTOR: {
                float[] mat=new float[9],ang=new float[3];
                SensorManager.getRotationMatrixFromVector(mat, ev.values);
                SensorManager.getOrientation(mat, ang);
                set(tvGameRot, String.format(Locale.US,
                        "Game Rotation (no mag)\nAz:%.1f°  Pitch:%.1f°  Roll:%.1f°",
                        Math.toDegrees(ang[0]),Math.toDegrees(ang[1]),Math.toDegrees(ang[2])));
                break;
            }
            case Sensor.TYPE_STEP_DETECTOR: {
                stepDetCount++;
                set(tvStep,  "Step Detector\n🚶 "+stepDetCount+" steps this session");
                set(visStep, "🚶\n"+stepDetCount+"\nsteps");
                bar(pbStep, Math.min(stepDetCount * 10, 10000));
                break;
            }
            case Sensor.TYPE_STEP_COUNTER: {
                long total=(long)ev.values[0];
                if(stepCntBase<0) stepCntBase=total;
                long session=total-stepCntBase;
                set(tvStepCounter, String.format(Locale.US,
                        "Step Counter\nSession:%d  Device total:%,d",session,total));
                break;
            }
            case Sensor.TYPE_PROXIMITY: {
                float dist=ev.values[0], maxD=ev.sensor.getMaximumRange();
                boolean near=dist<(maxD*0.5f)||dist==0f;
                set(tvProximity, String.format(Locale.US,
                        "Proximity\n%s\nValue:%.1f  Max:%.1f",
                        near?"🤚 Near/Covered":"↔️ Far/Clear", dist, maxD));
                set(visProximity, near?"🤚\nNear":"↔️\nFar");
                bar(pbProximity, near?100:5);
                break;
            }
            case Sensor.TYPE_RELATIVE_HUMIDITY: {
                float rh=ev.values[0];
                String d=rh<20?"🏜️ Very dry":rh<40?"🌵 Dry":rh<60?"🌿 Comfortable":rh<75?"💦 Humid":"🌊 Very humid";
                set(tvHumidity, String.format(Locale.US,"Humidity\n%.1f%%\n%s",rh,d));
                set(visHumidity, String.format(Locale.US,"💧\n%.0f%%",rh));
                bar(pbHumidity, (int)rh);
                break;
            }
            case Sensor.TYPE_AMBIENT_TEMPERATURE: {
                float c=ev.values[0], disp=AppPrefs.temp(c,ctx);
                String u=AppPrefs.tempUnit(ctx);
                String d=c<5?"❄️ Cold":c<15?"🧊 Cool":c<22?"🌿 Comfortable":c<30?"☀️ Warm":"🔥 Hot";
                set(tvAmbientTemp, String.format(Locale.US,"Ambient Temp\n%.1f %s\n%s",disp,u,d));
                set(visAmbientTemp, String.format(Locale.US,"🌡\n%.0f%s",disp,u));
                bar(pbAmbientTemp, (int)Math.max(0,Math.min(100,(c+10)*2)));
                break;
            }
            case Sensor.TYPE_HEART_RATE: {
                int bpm=(int)ev.values[0];
                if(ev.accuracy<1||bpm<=0){
                    set(tvHeartRate,"Heart Rate\nPlace finger on sensor…");
                    set(visHeartRate,"❤️\n--bpm");
                } else {
                    String z=bpm<60?"💤 Low":bpm<100?"💚 Normal":bpm<140?"💛 Elevated":"🔴 High";
                    set(tvHeartRate, String.format(Locale.US,"Heart Rate\n%d bpm\n%s",bpm,z));
                    set(visHeartRate, String.format(Locale.US,"❤️\n%dbpm",bpm));
                }
                break;
            }
            default: {
                if(t==TYPE_TILT_DETECTOR){
                    tiltCount++;
                    set(tvTilt,"Tilt Detector\n📱 "+tiltCount+" tilt events");
                    set(visTilt,"📱\n"+tiltCount+"\ntilts");
                }
                break;
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
        if(lux<1)return"Pitch dark";if(lux<10)return"Very dark";
        if(lux<50)return"Dim";if(lux<200)return"Indoor";
        if(lux<1000)return"Bright indoor";if(lux<10000)return"Overcast";
        if(lux<40000)return"Daylight";return"Direct sun";
    }
}
