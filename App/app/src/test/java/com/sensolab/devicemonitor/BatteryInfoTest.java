package com.sensolab.devicemonitor;

import org.junit.Test;
import static org.junit.Assert.*;

/** Capacity estimate and current-unit heuristic in {@link BatteryInfo} (pure math, no Android calls). */
public class BatteryInfoTest {

    // ── estimateFullMah() ─────────────────────────────────────────────────
    @Test public void estimate_halfCharged()   { assertEquals(4000, BatteryInfo.estimateFullMah(2000, 50)); }
    @Test public void estimate_full()          { assertEquals(4500, BatteryInfo.estimateFullMah(4500, 100)); }
    @Test public void estimate_tooLowLevel()   { assertEquals(-1, BatteryInfo.estimateFullMah(500, 10)); }
    @Test public void estimate_unknownCharge() { assertEquals(-1, BatteryInfo.estimateFullMah(-1, 80)); }
    @Test public void estimate_unknownLevel()  { assertEquals(-1, BatteryInfo.estimateFullMah(3000, -1)); }
    @Test public void estimate_emulatorCounter() {
        // The emulator reports 10 000 µAh at 100 % — a "10 mAh battery" is not a real reading
        assertEquals(-1, BatteryInfo.estimateFullMah(10, 100));
    }
    @Test public void estimate_bogusLarge()    { assertEquals(-1, BatteryInfo.estimateFullMah(60_000, 100)); }

    // ── normalizeCurrentMa() ──────────────────────────────────────────────
    @Test public void current_microAmps()      { assertEquals(-450f, BatteryInfo.normalizeCurrentMa(-450_000), 0.001f); }
    @Test public void current_milliAmps()      { assertEquals(1200f, BatteryInfo.normalizeCurrentMa(1200), 0.001f); }
}
