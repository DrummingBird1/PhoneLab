package com.sensolab.devicemonitor;

/** Pure math shared by the gauge views — no Android types, so it stays unit-testable. */
public final class GaugeMath {
    private GaugeMath() {}

    public static final int BAND_NONE = 0, BAND_OK = 1, BAND_WARN = 2, BAND_HOT = 3;

    /** Maps value into 0..1 across [min, max], clamped. A degenerate range returns 0. */
    public static float fraction(float value, float min, float max) {
        if (Float.isNaN(value) || max <= min) return 0f;
        float f = (value - min) / (max - min);
        return Math.max(0f, Math.min(1f, f));
    }

    /** Which color band a value falls in. NaN thresholds mean "no bands configured". */
    public static int band(float value, float warn, float hot) {
        if (Float.isNaN(warn) && Float.isNaN(hot)) return BAND_NONE;
        if (!Float.isNaN(hot) && value >= hot) return BAND_HOT;
        if (!Float.isNaN(warn) && value >= warn) return BAND_WARN;
        return BAND_OK;
    }

    /** One smoothing step toward target; snaps once close enough to stop animating. */
    public static float approach(float current, float target, float factor) {
        float next = current + (target - current) * factor;
        return Math.abs(target - next) < 0.001f ? target : next;
    }
}
