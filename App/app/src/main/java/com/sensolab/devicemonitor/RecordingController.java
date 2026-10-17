package com.sensolab.devicemonitor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * UI-side client for {@link CsvRecordingService}, extracted from SensorsFragment.
 * The service owns the recording (it survives tab switches and backgrounding); this
 * class only starts/stops it, binds to read live progress, and pushes that to a
 * {@link Ui} once a second.
 */
public final class RecordingController {

    public interface Ui {
        void showIdle();
        void showRecording(String elapsed, int samples);
        void showSaved(String fileName, int samples);
    }

    private final Context appCtx;
    private final Ui ui;
    private final Handler main = new Handler(Looper.getMainLooper());
    private CsvRecordingService service;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((CsvRecordingService.LocalBinder) binder).getService();
            if (service.isRecording()) startTicking();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            stopTicking();
            ui.showIdle();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (service == null || !service.isRecording()) return;
            long elapsedSec = (System.currentTimeMillis() - service.getStartedAtMs()) / 1000;
            ui.showRecording(Formatters.duration(elapsedSec), service.getSampleCount());
            main.postDelayed(this, 1000);
        }
    };

    public RecordingController(Context ctx, Ui ui) {
        this.appCtx = ctx.getApplicationContext();
        this.ui = ui;
    }

    /** Reconnects to an already-running recording. Flags=0: binding alone never starts one. */
    public void attach() {
        if (bound) return;
        bound = appCtx.bindService(new Intent(appCtx, CsvRecordingService.class), connection, 0);
    }

    public void detach() {
        stopTicking();
        if (bound) {
            appCtx.unbindService(connection);
            bound = false;
        }
        service = null;
    }

    public boolean isRecording() { return service != null && service.isRecording(); }

    public void toggle() {
        if (!isRecording()) {
            CsvRecordingService.start(appCtx);
            if (bound) appCtx.unbindService(connection);
            // BIND_AUTO_CREATE so the connection lands promptly and the UI flips to
            // "recording" immediately instead of on the next resume.
            bound = appCtx.bindService(new Intent(appCtx, CsvRecordingService.class),
                    connection, Context.BIND_AUTO_CREATE);
            ui.showRecording(Formatters.duration(0), 0);
        } else {
            service.stopRecording();
            stopTicking();
            ui.showSaved(service.getLastFileName(), service.getLastSampleCount());
        }
    }

    /** Trigger sensors are owned by the fragment; forward their events into a live recording. */
    public void forwardTrigger(String sensorName, long timestampNs) {
        if (service != null) service.appendTrigger(sensorName, timestampNs);
    }

    private void startTicking() {
        main.removeCallbacks(ticker);
        main.post(ticker);
    }

    private void stopTicking() {
        main.removeCallbacks(ticker);
    }
}
