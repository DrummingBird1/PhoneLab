package com.sensolab.devicemonitor;

import androidx.fragment.app.Fragment;

/**
 * foss flavor: no Google Play Billing (a proprietary library F-Droid doesn't allow), so
 * there is no tip jar — {@link #isAvailable()} is false and the About button is hidden.
 */
public final class TipJar {

    public static boolean isAvailable() { return false; }

    public TipJar(Fragment host) {}

    public void open() {}

    public void release() {}
}
