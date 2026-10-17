package com.sensolab.devicemonitor;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

/**
 * Sensors-tab state that must outlive the Fragment's view: session counters (also
 * persisted across app kill, R4), the last accelerometer/magnetometer vectors the
 * compass is computed from, and the magnetometer calibration flag. Rotation or a theme
 * change no longer resets any of it.
 */
public class SensorsViewModel extends AndroidViewModel {

    int  stepDetCount, tiltCount, sigMotCount;
    long stepCntBase = -1L;
    int  stationaryCount, motionCount;   // not persisted — per screen session
    float[] lastAccel = {0f, 0f, 9.8f};
    float[] lastMag   = {0f, 30f, 0f};
    boolean magLowAccuracy = false;

    public SensorsViewModel(@NonNull Application app) {
        super(app);
        SharedPreferences sp = AppPrefs.get(app);
        stepDetCount = sp.getInt (AppPrefs.KEY_STEP_DET_COUNT, 0);
        tiltCount    = sp.getInt (AppPrefs.KEY_TILT_COUNT,     0);
        sigMotCount  = sp.getInt (AppPrefs.KEY_SIG_MOT_COUNT,  0);
        stepCntBase  = sp.getLong(AppPrefs.KEY_STEP_CNT_BASE, -1L);
    }

    void saveCounters() {
        AppPrefs.get(getApplication()).edit()
                .putInt (AppPrefs.KEY_STEP_DET_COUNT, stepDetCount)
                .putInt (AppPrefs.KEY_TILT_COUNT,     tiltCount)
                .putInt (AppPrefs.KEY_SIG_MOT_COUNT,  sigMotCount)
                .putLong(AppPrefs.KEY_STEP_CNT_BASE,  stepCntBase)
                .apply();
    }
}
