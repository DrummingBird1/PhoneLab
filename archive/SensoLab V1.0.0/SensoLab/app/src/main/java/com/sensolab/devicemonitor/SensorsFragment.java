package com.sensolab.devicemonitor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import android.Manifest;
import android.content.pm.PackageManager;
import java.util.Locale;

public class SensorsFragment extends Fragment implements SensorEventListener {

    private static final int TYPE_TILT_DETECTOR = 22;

    private SensorManager sensorManager;
    private Sensor sAccel, sMag, sGyro, sLight,
            sPressure, sGravity, sLinAccel, sRotVec,
            sGameRot, sStep, sTilt;

    // ── Textual mode views ────────────────────────────────────────────────
    private LinearLayout layoutTextual, layoutVisual;

    // textual
    private TextView tvAccel, tvMag, tvGyro, tvLux, tvLight,
            tvPressure, tvGravity, tvLinAccel, tvRot, tvGameRot,
            tvStep, tvTilt, tvSpeed;

    // visual cards
    private TextView visAccel, visMag, visGyro, visLight,
            visPressure, visGravity, visLinAccel, visRot, visGameRot,
            visStep, visTilt, visSpeed;
    private ProgressBar pbAccel, pbMag, pbGyro, pbLight, pbPressure;

    // data
    private int stepCount = 0;
    private int tiltCount = 0;
    private float lastSpeedMph = 0;
    private float[] lastAccel = {0,0,0};
    private float[] lastMag   = {0,0,0};
    private float[] lastGyro  = {0,0,0};

    private LocationManager locationManager;
    private boolean locationRegistered = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sensors, container, false);

        layoutTextual = view.findViewById(R.id.layout_textual);
        layoutVisual  = view.findViewById(R.id.layout_visual);

        // textual
        tvAccel   = view.findViewById(R.id.tv_accelerometer);
        tvMag     = view.findViewById(R.id.tv_magnetic_field);
        tvGyro    = view.findViewById(R.id.tv_gyroscope);
        tvLux     = view.findViewById(R.id.tv_lux);
        tvLight   = view.findViewById(R.id.tv_light);
        tvPressure= view.findViewById(R.id.tv_pressure);
        tvGravity = view.findViewById(R.id.tv_gravity);
        tvLinAccel= view.findViewById(R.id.tv_linear_accel);
        tvRot     = view.findViewById(R.id.tv_rotation_vector);
        tvGameRot = view.findViewById(R.id.tv_game_rotation);
        tvStep    = view.findViewById(R.id.tv_step_detector);
        tvTilt    = view.findViewById(R.id.tv_tilt_detector);
        tvSpeed   = view.findViewById(R.id.tv_speed);

        // visual
        visAccel    = view.findViewById(R.id.vis_accel);
        visMag      = view.findViewById(R.id.vis_mag);
        visGyro     = view.findViewById(R.id.vis_gyro);
        visLight    = view.findViewById(R.id.vis_light);
        visPressure = view.findViewById(R.id.vis_pressure);
        visGravity  = view.findViewById(R.id.vis_gravity);
        visLinAccel = view.findViewById(R.id.vis_lin_accel);
        visRot      = view.findViewById(R.id.vis_rot);
        visGameRot  = view.findViewById(R.id.vis_game_rot);
        visStep     = view.findViewById(R.id.vis_step);
        visTilt     = view.findViewById(R.id.vis_tilt);
        visSpeed    = view.findViewById(R.id.vis_speed);
        pbAccel     = view.findViewById(R.id.pb_accel);
        pbMag       = view.findViewById(R.id.pb_mag);
        pbGyro      = view.findViewById(R.id.pb_gyro);
        pbLight     = view.findViewById(R.id.pb_light);
        pbPressure  = view.findViewById(R.id.pb_pressure);

        locationManager = (LocationManager) requireActivity()
                .getSystemService(Context.LOCATION_SERVICE);

        return view;
    }

    @Override public void onResume() {
        super.onResume();
        applyDisplayMode();
        registerSensors();
        startLocationSafe();
    }

    @Override public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        stopLocation();
    }

    private void applyDisplayMode() {
        boolean visual = AppPrefs.isVisual(requireContext());
        layoutTextual.setVisibility(visual ? View.GONE  : View.VISIBLE);
        layoutVisual .setVisibility(visual ? View.VISIBLE : View.GONE);
    }

    private void registerSensors() {
        if (sensorManager == null)
            sensorManager = (SensorManager) requireActivity()
                    .getSystemService(Context.SENSOR_SERVICE);

        // unregister first to avoid double-registration
        sensorManager.unregisterListener(this);

        sAccel   = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sMag     = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sGyro    = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        sLight   = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sPressure= sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sGravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        sLinAccel= sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sRotVec  = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sGameRot = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        sStep    = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        sTilt    = sensorManager.getDefaultSensor(TYPE_TILT_DETECTOR);

        reg(sAccel,    tvAccel,   "Accelerometer");
        reg(sMag,      tvMag,     "Magnetic Field");
        reg(sGyro,     tvGyro,    "Gyroscope");
        reg(sLight,    tvLight,   "Light");
        reg(sPressure, tvPressure,"Pressure");
        reg(sGravity,  tvGravity, "Gravity");
        reg(sLinAccel, tvLinAccel,"Linear Accel");
        reg(sRotVec,   tvRot,     "Rotation Vector");
        reg(sGameRot,  tvGameRot, "Game Rotation");
        reg(sStep,     tvStep,    "Step Detector");
        reg(sTilt,     tvTilt,    "Tilt Detector");
    }

    private void reg(Sensor s, TextView tv, String name) {
        if (s != null) {
            sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
        } else if (tv != null) {
            tv.setText(name + "\nNot available");
        }
    }

    private void startLocationSafe() {
        if (!isAdded()) return;
        if (ActivityCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (!locationRegistered) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
                locationRegistered = true;
            }
        } else {
            if (tvSpeed != null) tvSpeed.setText("🚀 Speed\nGPS permission needed");
        }
    }

    private void stopLocation() {
        if (locationRegistered && locationManager != null) {
            locationManager.removeUpdates(locationListener);
            locationRegistered = false;
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            if (!isAdded()) return;
            lastSpeedMph = loc.hasSpeed() ? loc.getSpeed() * 2.23694f : 0;
            float kph = loc.hasSpeed() ? loc.getSpeed() * 3.6f : 0;
            String txt = String.format(Locale.US, "🚀 Speed\n%.1f mph  |  %.1f km/h",
                    lastSpeedMph, kph);
            if (tvSpeed != null)  tvSpeed.setText(txt);
            if (visSpeed != null) visSpeed.setText(
                    String.format(Locale.US,"🚀\n%.1f mph", lastSpeedMph));
        }
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) {}
    };

    @Override
    public void onSensorChanged(SensorEvent ev) {
        if (!isAdded()) return;
        int t = ev.sensor.getType();

        if (t == Sensor.TYPE_ACCELEROMETER) {
            lastAccel = ev.values.clone();
            float mag = magnitude(ev.values);
            if (tvAccel != null)
                tvAccel.setText(String.format(Locale.US,
                        "Accelerometer  m/s²\nX:%.3f  Y:%.3f  Z:%.3f\n|a|=%.3f",
                        ev.values[0],ev.values[1],ev.values[2], mag));
            if (visAccel != null)
                visAccel.setText(String.format(Locale.US,"📳\n%.2f m/s²", mag));
            if (pbAccel != null) pbAccel.setProgress(Math.min(100,(int)(mag*5)));

        } else if (t == Sensor.TYPE_MAGNETIC_FIELD) {
            lastMag = ev.values.clone();
            float mag = magnitude(ev.values);
            if (tvMag != null)
                tvMag.setText(String.format(Locale.US,
                        "Magnetic Field  µT\nX:%.2f  Y:%.2f  Z:%.2f\n|B|=%.2f",
                        ev.values[0],ev.values[1],ev.values[2], mag));
            // Simple compass bearing
            float[] R = new float[9], I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, lastAccel, lastMag)) {
                float[] orient = new float[3];
                SensorManager.getOrientation(R, orient);
                int bearing = (int) Math.toDegrees(orient[0]);
                if (bearing < 0) bearing += 360;
                if (visMag != null)
                    visMag.setText(compassDir(bearing) + "\n" + bearing + "°");
            } else if (visMag != null) {
                visMag.setText(String.format(Locale.US,"🧲\n%.1f µT", mag));
            }
            if (pbMag != null) pbMag.setProgress(Math.min(100,(int)(mag/2)));
            if (tvLux != null)
                tvLux.setText(String.format(Locale.US,"Mag Magnitude\n%.2f µT", mag));

        } else if (t == Sensor.TYPE_GYROSCOPE) {
            lastGyro = ev.values.clone();
            float mag = magnitude(ev.values);
            if (tvGyro != null)
                tvGyro.setText(String.format(Locale.US,
                        "Gyroscope  rad/s\nX:%.4f  Y:%.4f  Z:%.4f",
                        ev.values[0],ev.values[1],ev.values[2]));
            if (visGyro != null)
                visGyro.setText(String.format(Locale.US,"🌀\n%.3f r/s", mag));
            if (pbGyro != null) pbGyro.setProgress(Math.min(100,(int)(mag*30)));

        } else if (t == Sensor.TYPE_LIGHT) {
            float lux = ev.values[0];
            if (tvLight != null) tvLight.setText(String.format(Locale.US,"Light\n%.1f lx",lux));
            if (tvLux != null)   tvLux.setText(String.format(Locale.US,"Lux\n%.1f lx\n%s",lux,luxDesc(lux)));
            if (visLight != null) visLight.setText(luxIcon(lux)+"\n"+luxDesc(lux));
            if (pbLight != null) pbLight.setProgress(Math.min(100,(int)(lux/1000)));

        } else if (t == Sensor.TYPE_PRESSURE) {
            float hpa = ev.values[0];
            float alt  = (float)(44330*(1-Math.pow(hpa/1013.25,0.1903)));
            if (tvPressure != null)
                tvPressure.setText(String.format(Locale.US,
                        "Pressure\n%.2f hPa  /  %.4f atm\nAlt≈%.0fm", hpa, hpa/1013.25f, alt));
            if (visPressure != null)
                visPressure.setText(String.format(Locale.US,"🌡\n%.1f hPa\n≈%.0fm alt", hpa, alt));
            if (pbPressure != null) pbPressure.setProgress(Math.min(100,(int)((hpa-900)/2)));

        } else if (t == Sensor.TYPE_GRAVITY) {
            if (tvGravity != null)
                tvGravity.setText(String.format(Locale.US,
                        "Gravity  m/s²\nX:%.3f  Y:%.3f  Z:%.3f",
                        ev.values[0],ev.values[1],ev.values[2]));
            float tilt = (float) Math.toDegrees(Math.acos(
                    Math.abs(ev.values[2]) / Math.max(magnitude(ev.values), 0.001f)));
            if (visGravity != null)
                visGravity.setText(String.format(Locale.US,
                        "📐\nTilt %.1f°", tilt));

        } else if (t == Sensor.TYPE_LINEAR_ACCELERATION) {
            float mag = magnitude(ev.values);
            if (tvLinAccel != null)
                tvLinAccel.setText(String.format(Locale.US,
                        "Linear Accel  m/s²\nX:%.3f  Y:%.3f  Z:%.3f",
                        ev.values[0],ev.values[1],ev.values[2]));
            if (visLinAccel != null)
                visLinAccel.setText(String.format(Locale.US,"⚡\n%.2f m/s²", mag));

        } else if (t == Sensor.TYPE_ROTATION_VECTOR) {
            if (tvRot != null)
                tvRot.setText(String.format(Locale.US,
                        "Rotation Vector\nX:%.4f  Y:%.4f  Z:%.4f",
                        ev.values[0],ev.values[1],ev.values[2]));
            float[] mat = new float[9]; float[] orient = new float[3];
            SensorManager.getRotationMatrixFromVector(mat, ev.values);
            SensorManager.getOrientation(mat, orient);
            if (visRot != null)
                visRot.setText(String.format(Locale.US,"🔄\nP:%.1f° R:%.1f°",
                        Math.toDegrees(orient[1]), Math.toDegrees(orient[2])));

        } else if (t == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            if (tvGameRot != null)
                tvGameRot.setText(String.format(Locale.US,
                        "Game Rotation\nX:%.4f  Y:%.4f  Z:%.4f",
                        ev.values[0],ev.values[1],ev.values[2]));
            if (visGameRot != null)
                visGameRot.setText(String.format(Locale.US,"🎮\nX:%.2f  Z:%.2f",
                        ev.values[0],ev.values[2]));

        } else if (t == Sensor.TYPE_STEP_DETECTOR) {
            stepCount++;
            if (tvStep != null)  tvStep.setText("Step Detector\n🚶 " + stepCount + " steps");
            if (visStep != null) visStep.setText("🚶\n" + stepCount + "\nsteps");

        } else if (t == TYPE_TILT_DETECTOR) {
            tiltCount++;
            if (tvTilt != null)  tvTilt.setText("Tilt Detector\n📱 " + tiltCount + " tilts");
            if (visTilt != null) visTilt.setText("📱\n" + tiltCount + "\ntilts");
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── Helpers ────────────────────────────────────────────────────────────
    private float magnitude(float[] v) {
        float s = 0; for (float f : v) s += f*f; return (float)Math.sqrt(s);
    }
    private String luxDesc(float lux) {
        if (lux < 10) return "Very Dark";
        if (lux < 50) return "Dim";
        if (lux < 200) return "Indoor";
        if (lux < 1000) return "Bright Indoor";
        if (lux < 10000) return "Overcast";
        return "Sunlight";
    }
    private String luxIcon(float lux) {
        if (lux < 10) return "🌑"; if (lux < 200) return "💡";
        if (lux < 1000) return "☀"; return "🌟";
    }
    private String compassDir(int deg) {
        String[] dirs = {"N","NE","E","SE","S","SW","W","NW"};
        return dirs[(int)((deg+22.5)/45) % 8];
    }
}
