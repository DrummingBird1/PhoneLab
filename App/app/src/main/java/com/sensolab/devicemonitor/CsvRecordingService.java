package com.sensolab.devicemonitor;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;

/**
 * Foreground service that owns CSV sensor recording end-to-end, independent of
 * SensorsFragment's lifecycle — recording now continues correctly whether the Sensors
 * tab is visible, another tab is open, or the app is fully backgrounded. Replaces the
 * old behaviour where leaving the Sensors tab silently stopped an active recording.
 *
 * <p>v1 scope: independently registers the "core" continuous motion/environmental
 * sensors plus GPS. It does NOT register the one-shot trigger sensors (Stationary /
 * Motion / Significant-Motion Detect) or the permission-gated ones (Heart Rate, Step
 * Detector/Counter) — duplicating {@link SensorsFragment}'s full 23-sensor registration
 * here was judged out of scope alongside the deferred SensorsFragment split. Trigger
 * events are instead forwarded into an active recording by SensorsFragment itself via
 * {@link #appendTrigger} whenever it happens to be bound — so those specific events are
 * only captured while the Sensors tab is visible, a natural and honest limitation
 * rather than a silent gap.
 */
public class CsvRecordingService extends Service implements SensorEventListener, LocationListener {

    private static final String CHANNEL_ID = "sensolab_recording";
    private static final int NOTIFICATION_ID = 2001;
    private static final String ACTION_STOP = "com.sensolab.devicemonitor.STOP_RECORDING";

    private final IBinder binder = new LocalBinder();
    private SensorManager sm;
    private LocationManager lm;
    private CsvRecorder recorder;
    private long startedAtMs;

    public class LocalBinder extends Binder {
        CsvRecordingService getService() { return CsvRecordingService.this; }
    }

    /** Starts the service and begins a new recording (no-op if one is already active). */
    public static void start(Context ctx) {
        ContextCompat.startForegroundService(ctx, new Intent(ctx, CsvRecordingService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            doStop();
            return START_NOT_STICKY;
        }
        if (recorder == null) beginRecording();
        return START_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onDestroy() {
        super.onDestroy();
        // Safety net — normally doStop() already got here via the notification/UI action.
        if (recorder != null) doStop();
    }

    // ── Recording lifecycle ──────────────────────────────────────────────

    private void beginRecording() {
        try {
            recorder = CsvRecorder.start(this);
        } catch (IOException e) {
            stopSelf();
            return;
        }
        startedAtMs = System.currentTimeMillis();
        startForeground(NOTIFICATION_ID, buildNotification());
        registerSensorsInternal();
        registerGpsInternal();
    }

    /** Stops recording, shares the file (matches the previous explicit-Stop behaviour),
     *  and shuts the service down. Reachable both from the notification's Stop action
     *  and from the bound Fragment's Stop button — factored into one path so both agree.
     *  Captures the final filename/count into lastFileName/lastSampleCount BEFORE nulling
     *  recorder, so a same-process caller (the Fragment, via the binder) can still read
     *  them immediately after calling {@link #stopRecording()}. */
    private void doStop() {
        if (sm != null) sm.unregisterListener(this);
        if (lm != null) {
            try { lm.removeUpdates(this); } catch (SecurityException ignored) {}
        }
        if (recorder != null) {
            lastFileName = recorder.file().getName();
            lastSampleCount = recorder.stopAndShare(this);
            recorder = null;
        }
        stopForeground(true);
        stopSelf();
    }

    /** Called by the bound Fragment's Stop button. */
    public void stopRecording() { doStop(); }

    public boolean isRecording()      { return recorder != null; }
    public int     getSampleCount()   { return recorder != null ? recorder.sampleCount() : 0; }
    public long    getStartedAtMs()   { return startedAtMs; }
    /** Valid immediately after {@link #stopRecording()} returns (same-process binder call). */
    public String  getLastFileName()   { return lastFileName; }
    public int     getLastSampleCount(){ return lastSampleCount; }
    private String lastFileName;
    private int    lastSampleCount;

    /** Forwarded by SensorsFragment for the trigger sensors it owns (see class doc). */
    public void appendTrigger(String sensorName, long timestampNs) {
        if (recorder != null) recorder.appendTrigger(sensorName, timestampNs);
    }

    // ── Sensors (core continuous set — see class doc for what's excluded) ──

    private void registerSensorsInternal() {
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        int ui = SensorManager.SENSOR_DELAY_UI, normal = SensorManager.SENSOR_DELAY_NORMAL;
        regIfPresent(Sensor.TYPE_ACCELEROMETER, ui);
        regIfPresent(Sensor.TYPE_MAGNETIC_FIELD, ui);
        regIfPresent(Sensor.TYPE_GYROSCOPE, ui);
        regIfPresent(Sensor.TYPE_LIGHT, ui);
        regIfPresent(Sensor.TYPE_PRESSURE, ui);
        regIfPresent(Sensor.TYPE_GRAVITY, ui);
        regIfPresent(Sensor.TYPE_LINEAR_ACCELERATION, ui);
        regIfPresent(Sensor.TYPE_ROTATION_VECTOR, ui);
        regIfPresent(Sensor.TYPE_GAME_ROTATION_VECTOR, ui);
        regIfPresent(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, ui);
        regIfPresent(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, ui);
        regIfPresent(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, ui);
        regIfPresent(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, ui);
        regIfPresent(Sensor.TYPE_PROXIMITY, normal);
        regIfPresent(Sensor.TYPE_RELATIVE_HUMIDITY, normal);
        regIfPresent(Sensor.TYPE_AMBIENT_TEMPERATURE, normal);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            regIfPresent(Sensor.TYPE_HINGE_ANGLE, normal);
        }
    }

    private void regIfPresent(int type, int delay) {
        Sensor s = sm.getDefaultSensor(type);
        if (s != null) sm.registerListener(this, s, delay);
    }

    @Override public void onSensorChanged(SensorEvent ev) {
        if (recorder != null) recorder.append(ev);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* not needed here */ }

    // ── GPS ───────────────────────────────────────────────────────────────

    private void registerGpsInternal() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return; // no permission — recording still proceeds for the sensors above
        }
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5f, this);
        } catch (Exception ignored) {
            // Provider unavailable on this device — recording proceeds without GPS rows.
        }
    }

    @Override public void onLocationChanged(@NonNull Location loc) {
        if (recorder != null) {
            recorder.appendLocation(loc.getLatitude(), loc.getLongitude(),
                    loc.hasAltitude() ? loc.getAltitude() : Double.NaN,
                    loc.getElapsedRealtimeNanos());
        }
    }
    @Override public void onProviderDisabled(@NonNull String p) {}
    @Override public void onProviderEnabled(@NonNull String p) {}
    @Override public void onStatusChanged(String p, int s, Bundle e) {}

    // ── Notification ──────────────────────────────────────────────────────

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.rec_notif_channel_name), NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(getString(R.string.rec_notif_channel_desc));
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, CsvRecordingService.class).setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_recording)
                .setContentTitle(getString(R.string.rec_notif_title))
                .setContentText(getString(R.string.rec_notif_text))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, getString(R.string.rec_notif_stop), stopPi)
                .build();
    }
}
