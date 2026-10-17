package com.sensolab.devicemonitor;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

/**
 * Location updates for the speed / altitude cards, extracted from SensorsFragment.
 * Registers GPS_PROVIDER plus NETWORK_PROVIDER as a fallback (network often gives a
 * faster first fix indoors while GPS is still acquiring) — both feed the same listener.
 * 2 s / 5 m is plenty for a dashboard and far cheaper than 1 s / 0 m (P3).
 */
public final class GpsTracker {

    public interface Listener {
        void onLocation(@NonNull Location location);
        void onGpsDisabled();
    }

    private static final long MIN_TIME_MS = 2000L;
    private static final float MIN_DISTANCE_M = 5f;

    private final LocationManager lm;
    private final Listener listener;
    private boolean registered = false;

    private final LocationListener systemListener = new LocationListener() {
        @Override public void onLocationChanged(@NonNull Location loc) { listener.onLocation(loc); }
        @Override public void onProviderDisabled(@NonNull String provider) {
            if (LocationManager.GPS_PROVIDER.equals(provider)) listener.onGpsDisabled();
        }
        @Override public void onProviderEnabled(@NonNull String provider) {}
        @Override public void onStatusChanged(String p, int s, Bundle e) {}
    };

    public GpsTracker(Context ctx, Listener listener) {
        this.lm = (LocationManager) ctx.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        this.listener = listener;
    }

    /** Caller must have checked ACCESS_FINE_LOCATION. No-op if already registered. */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    public void start() {
        if (registered || lm == null) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, systemListener);
        } catch (IllegalArgumentException ignored) {
            // No GPS provider on this device — network may still work below.
        }
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_MS, MIN_DISTANCE_M, systemListener);
            }
        } catch (IllegalArgumentException ignored) {
            // Provider unavailable on this device — GPS_PROVIDER alone still works.
        }
        registered = true;
    }

    public void stop() {
        if (!registered || lm == null) return;
        lm.removeUpdates(systemListener);
        registered = false;
    }
}
