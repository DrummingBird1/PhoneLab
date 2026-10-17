package com.sensolab.devicemonitor;

import android.content.Context;
import android.hardware.SensorManager;
import android.location.Location;
import androidx.annotation.Nullable;
import java.util.Locale;

/**
 * Turns raw sensor values into display-ready {@link Readout}s (value / unit / detail /
 * caption / gauge value), extracted from SensorsFragment's old 300-line switch so the
 * Fragment only routes events to cards. Stateless; every unit conversion goes through
 * {@link AppPrefs}. Numbers use Locale.US so digits stay ASCII in Arabic (X6).
 */
public final class SensorReadouts {
    private SensorReadouts() {}

    /** One formatted reading. {@code gauge} is in the gauge's canonical (SI/metric) scale. */
    public static final class Readout {
        public final String value, unit, detail, caption;
        public final float gauge;
        Readout(String value, String unit, String detail, String caption, float gauge) {
            this.value = value; this.unit = unit; this.detail = detail; this.caption = caption; this.gauge = gauge;
        }
    }

    private static String f(String fmt, Object... args) { return String.format(Locale.US, fmt, args); }

    static float mag3(float x, float y, float z) { return (float) Math.sqrt(x * x + y * y + z * z); }

    static String xyz(float[] v, String fmt) {
        return f("X " + fmt + "  Y " + fmt + "  Z " + fmt, v[0], v[1], v[2]);
    }

    public static Readout accelerometer(Context c, float[] v) {
        float mag = mag3(v[0], v[1], v[2]);
        float g = mag / SensorManager.GRAVITY_EARTH;
        int sRes = g < 0.05f ? R.string.state_still  :
                   g < 1.15f ? R.string.state_normal :
                   g < 2.5f  ? R.string.state_moving :
                   g < 5f    ? R.string.state_fast   : R.string.state_high_g;
        String state = c.getString(sRes);
        return new Readout(f("%.2f", mag), "m/s²", xyz(v, "%+.2f") + "\n" + f("%.2f g", g),
                state, g);
    }

    public static Readout gyroscope(Context c, float[] v) {
        float mag = mag3(v[0], v[1], v[2]);
        float dps = (float) Math.toDegrees(mag);
        int sRes = dps < 1   ? R.string.state_still  :
                   dps < 30  ? R.string.state_slow   :
                   dps < 120 ? R.string.state_medium : R.string.state_fast_plain;
        return new Readout(f("%.0f", dps), "°/s", xyz(v, "%+.3f") + " rad/s", c.getString(sRes), dps);
    }

    public static Readout linearAccel(Context c, float[] v) {
        float mag = mag3(v[0], v[1], v[2]);
        int sRes = mag < 0.05f ? R.string.state_still  :
                   mag < 1f    ? R.string.state_slight :
                   mag < 5f    ? R.string.state_moving : R.string.state_fast;
        return new Readout(f("%.2f", mag), "m/s²", xyz(v, "%+.2f"), c.getString(sRes), mag);
    }

    public static Readout gravity(Context c, float[] v) {
        float mag = mag3(v[0], v[1], v[2]);
        float tilt = (float) Math.toDegrees(Math.acos(Math.abs(v[2]) / Math.max(mag, 0.001f)));
        int oRes = tilt < 8  ? R.string.orient_flat     :
                   tilt < 45 ? R.string.orient_tilted   :
                   tilt < 80 ? R.string.orient_standing : R.string.orient_sideways;
        return new Readout(f("%.0f", tilt), "°", xyz(v, "%+.2f") + " m/s²", c.getString(oRes), tilt);
    }

    /** values[0-2] = raw rate, values[3-5] = drift estimate. Null when the driver sends fewer than 6. */
    @Nullable public static Readout gyroUncalibrated(float[] v) {
        if (v.length < 6) return null;
        float raw = mag3(v[0], v[1], v[2]);
        float drift = mag3(v[3], v[4], v[5]);
        return new Readout(f("%.3f", raw), "rad/s", xyz(v, "%+.4f") + "\n" + f("drift %.4f", drift), null, raw);
    }

    /** values[0-2] = raw, values[3-5] = bias. */
    @Nullable public static Readout accelUncalibrated(float[] v) {
        if (v.length < 6) return null;
        float raw = mag3(v[0], v[1], v[2]);
        float bias = mag3(v[3], v[4], v[5]);
        return new Readout(f("%.2f", raw), "m/s²", xyz(v, "%+.3f") + "\n" + f("bias %.3f", bias), null, raw);
    }

    /** values[0-2] = raw, values[3-5] = hard-iron bias. */
    @Nullable public static Readout magUncalibrated(float[] v) {
        if (v.length < 6) return null;
        float raw = mag3(v[0], v[1], v[2]);
        float bias = mag3(v[3], v[4], v[5]);
        return new Readout(f("%.1f", raw), "µT", xyz(v, "%+.1f") + "\n" + f("hard-iron bias %.2f", bias), null, raw);
    }

    public static Readout magnetometer(Context c, float[] v, String compassText, boolean lowAccuracy) {
        float mag = mag3(v[0], v[1], v[2]);
        int qRes = mag < 25  ? R.string.state_weak   :
                   mag < 65  ? R.string.state_normal :
                   mag < 200 ? R.string.state_strong : R.string.state_interference;
        String detail = xyz(v, "%+.1f") + "\n" + c.getString(R.string.sensor_compass_label) + ": " + compassText;
        if (lowAccuracy) detail += "\n" + c.getString(R.string.accuracy_low_warning);
        return new Readout(f("%.1f", mag), "µT", detail, c.getString(qRes), mag);
    }

    /** Azimuth/pitch/roll from a rotation-vector style sensor. */
    public static Readout rotation(float[] v, @Nullable String extraDetail) {
        float[] mat = new float[9], ang = new float[3];
        SensorManager.getRotationMatrixFromVector(mat, v);
        SensorManager.getOrientation(mat, ang);
        int az = ((int) Math.toDegrees(ang[0]) + 360) % 360;
        int pitch = (int) Math.toDegrees(ang[1]);
        int roll  = (int) Math.toDegrees(ang[2]);
        String detail = f("Pitch %d°  Roll %d°", pitch, roll);
        if (extraDetail != null) detail += "\n" + extraDetail;
        return new Readout(f("%d°", az), compassLabel(az), detail, null, az);
    }

    public static Readout light(Context c, float lux) {
        int res = lux < 1     ? R.string.light_pitch_dark    :
                  lux < 10    ? R.string.light_very_dark     :
                  lux < 50    ? R.string.light_dim           :
                  lux < 200   ? R.string.light_indoor        :
                  lux < 1000  ? R.string.light_bright_indoor :
                  lux < 10000 ? R.string.light_overcast      :
                  lux < 40000 ? R.string.light_daylight      : R.string.light_direct_sun;
        String desc = c.getString(res);
        // Gauge on a log scale: 1 lx → 0, 100 000 lx → 5.
        float logLux = (float) Math.log10(Math.max(lux, 1f));
        return new Readout(f("%.0f", lux), "lx", desc, desc, logLux);
    }

    public static Readout pressure(Context c, float hpa) {
        float altM = (float) (44330 * (1 - Math.pow(hpa / 1013.25, 0.1903)));
        int wxRes = hpa > 1022 ? R.string.weather_clear  :
                    hpa > 1008 ? R.string.weather_normal :
                    hpa > 995  ? R.string.weather_cloudy : R.string.weather_storm;
        String wx = c.getString(wxRes);
        String detail = f("Alt ≈ %.0f %s", AppPrefs.altitude(altM, c), AppPrefs.altUnit(c)) + "\n" + wx;
        return new Readout(f("%.1f", AppPrefs.pressure(hpa, c)), AppPrefs.pressureUnit(c), detail, wx, hpa);
    }

    public static Readout humidity(Context c, float rh) {
        int dRes = rh < 20 ? R.string.humidity_very_dry    :
                   rh < 40 ? R.string.humidity_dry         :
                   rh < 60 ? R.string.humidity_comfortable :
                   rh < 75 ? R.string.humidity_humid       : R.string.humidity_very_humid;
        String d = c.getString(dRes);
        return new Readout(f("%.0f", rh), "%", d, d, rh);
    }

    /** Null when the reading is an implausible OEM sentinel (matches ThermalZones' guard). */
    @Nullable public static Readout ambientTemp(Context c, float celsius) {
        if (celsius < -40f || celsius > 85f) return null;
        int dRes = celsius < 5  ? R.string.ambient_cold        :
                   celsius < 15 ? R.string.ambient_cool        :
                   celsius < 22 ? R.string.ambient_comfortable :
                   celsius < 30 ? R.string.ambient_warm        : R.string.ambient_hot;
        String d = c.getString(dRes);
        return new Readout(f("%.1f", AppPrefs.temp(celsius, c)), AppPrefs.tempUnit(c), d, d, celsius);
    }

    public static Readout proximity(Context c, float dist, float max) {
        boolean near = dist < (max * 0.5f) || dist == 0f;
        String word = c.getString(near ? R.string.proximity_near_word : R.string.proximity_far_word);
        return new Readout(word, null, f("%.1f / %.1f", dist, max), word, near ? 1f : 0f);
    }

    /** Null when the sensor reports no skin contact yet. */
    @Nullable public static Readout heartRate(Context c, int bpm, int accuracy) {
        if (accuracy < 1 || bpm <= 0) return null;
        int zRes = bpm < 60  ? R.string.hr_zone_low      :
                   bpm < 100 ? R.string.hr_zone_normal   :
                   bpm < 140 ? R.string.hr_zone_elevated : R.string.hr_zone_high;
        String z = c.getString(zRes);
        return new Readout(f("%d", bpm), "bpm", z, z, bpm);
    }

    public static Readout hinge(Context c, float deg) {
        int stateRes = deg < 5   ? R.string.hinge_state_closed :
                       deg > 175 ? R.string.hinge_state_flat   : R.string.hinge_state_open;
        String s = c.getString(stateRes);
        return new Readout(f("%.0f", deg), "°", s, s, deg);
    }

    public static Readout sound(Context c, double db) {
        int noiseRes = db < 40 ? R.string.sound_silent :
                       db < 55 ? R.string.sound_quiet  :
                       db < 70 ? R.string.sound_normal :
                       db < 85 ? R.string.sound_loud   : R.string.sound_very_loud;
        String noise = c.getString(noiseRes);
        return new Readout(f("%.0f", db), "dB", noise, noise, (float) db);
    }

    /** B6: "--" when speed is unknown instead of a misleading 0. Gauge is km/h. */
    public static Readout speed(Context c, Location loc) {
        String accuracy = loc.hasAccuracy() ? f("%s · ±%.0f m", loc.getProvider(), loc.getAccuracy()) : loc.getProvider();
        if (!loc.hasSpeed()) {
            return new Readout("--", AppPrefs.speedUnit(c), accuracy, null, 0f);
        }
        return new Readout(f("%.1f", AppPrefs.speed(loc.getSpeed(), c)), AppPrefs.speedUnit(c),
                accuracy, null, loc.getSpeed() * 3.6f);
    }

    @Nullable public static Readout altitude(Context c, Location loc) {
        if (!loc.hasAltitude()) return null;
        String detail = loc.hasVerticalAccuracy()
                ? f("±%.0f %s", AppPrefs.altitude(loc.getVerticalAccuracyMeters(), c), AppPrefs.altUnit(c))
                : null;
        return new Readout(f("%.0f", AppPrefs.altitude((float) loc.getAltitude(), c)), AppPrefs.altUnit(c),
                detail, null, (float) loc.getAltitude());
    }

    /** Compass heading from the latest accelerometer + magnetometer vectors, or NaN. */
    public static float heading(float[] accel, float[] mag) {
        float[] r = new float[9], i = new float[9];
        if (!SensorManager.getRotationMatrix(r, i, accel, mag)) return Float.NaN;
        float[] o = new float[3];
        SensorManager.getOrientation(r, o);
        return (float) ((Math.toDegrees(o[0]) + 360) % 360);
    }

    public static String compassLabel(int deg) {
        String[] d = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                      "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return d[Math.round(((deg % 360) + 360) % 360 / 22.5f) % 16];
    }
}
