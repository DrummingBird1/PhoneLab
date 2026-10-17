package com.sensolab.devicemonitor;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * One snapshot of everything the platform exposes about the battery, shared by the
 * Hardware tab, the background worker (history + alerts) and the diagnostics report.
 * Reading it is cheap: a sticky-broadcast lookup plus a few BatteryManager properties.
 */
public final class BatteryInfo {

    public int levelPct = -1;
    public float tempC = Float.NaN;
    public int voltageMv = -1;
    /** Instantaneous current in mA; positive = charging on most devices (sign is OEM-specific). */
    public float currentMa = Float.NaN;
    public int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
    public int health = BatteryManager.BATTERY_HEALTH_UNKNOWN;
    public int plugged = 0;
    public int cycleCount = -1;
    public int chargeCounterMah = -1;
    public long energyMwh = -1;
    public String technology = "";

    private BatteryInfo() {}

    public static BatteryInfo read(Context ctx) {
        BatteryInfo b = new BatteryInfo();
        Intent i = ctx.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i != null) {
            int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (lv >= 0 && sc > 0) b.levelPct = lv * 100 / sc;
            int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (t != Integer.MIN_VALUE) b.tempC = t / 10f;
            b.voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            b.status    = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            b.health    = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
            b.plugged   = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            String tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            b.technology = tech == null ? "" : tech;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                b.cycleCount = i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1);
            }
        }
        BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (counter != Integer.MIN_VALUE && counter > 0) b.chargeCounterMah = counter / 1000;
            int cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (cur != Integer.MIN_VALUE && cur != 0) b.currentMa = normalizeCurrentMa(cur);
            long energy = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
            if (energy != Long.MIN_VALUE && energy > 0) b.energyMwh = energy / 1_000_000L;
        }
        return b;
    }

    /** CURRENT_NOW is specified in µA, but some OEMs report mA. Phone currents are tens to
     *  thousands of mA, so anything under 20 000 is almost certainly already mA. */
    static float normalizeCurrentMa(int raw) {
        return Math.abs(raw) > 20_000 ? raw / 1000f : raw;
    }

    public boolean isCharging() {
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    /** |V × I| in watts, or NaN if either is unknown. */
    public float powerW() {
        if (voltageMv <= 0 || Float.isNaN(currentMa)) return Float.NaN;
        return Math.abs(voltageMv / 1000f * currentMa / 1000f);
    }

    /** Rough full-charge capacity: remaining charge ÷ level. Useful as a wear indicator
     *  when compared with the battery's rated capacity. -1 if not computable. */
    public int estimatedFullCapacityMah() {
        if (chargeCounterMah <= 0 || levelPct < 20) return -1; // too noisy when nearly empty
        return Math.round(chargeCounterMah * 100f / levelPct);
    }

    @StringRes public int healthLabel() {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:                return R.string.batt_health_good;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:            return R.string.batt_health_overheat;
            case BatteryManager.BATTERY_HEALTH_DEAD:                return R.string.batt_health_dead;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:        return R.string.batt_health_over_voltage;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return R.string.batt_health_failure;
            case BatteryManager.BATTERY_HEALTH_COLD:                return R.string.batt_health_cold;
            default:                                                return R.string.batt_health_unknown;
        }
    }

    public boolean isHealthy() {
        return health == BatteryManager.BATTERY_HEALTH_GOOD || health == BatteryManager.BATTERY_HEALTH_UNKNOWN;
    }

    @StringRes public int statusLabel() {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:     return R.string.batt_status_charging;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:  return R.string.batt_status_discharging;
            case BatteryManager.BATTERY_STATUS_FULL:         return R.string.batt_status_full;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return R.string.batt_status_not_charging;
            default:                                         return R.string.batt_health_unknown;
        }
    }

    @Nullable public String plugLabel(Context ctx) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_USB:      return ctx.getString(R.string.charge_usb);
            case BatteryManager.BATTERY_PLUGGED_AC:       return ctx.getString(R.string.charge_ac);
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return ctx.getString(R.string.charge_wireless);
            default: return null;
        }
    }
}
