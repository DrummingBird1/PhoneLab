package com.sensolab.devicemonitor;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the pure helpers in {@link ThermalZones}. zone() itself does real file I/O
 * against /sys/class/thermal, which doesn't exist off-device — same rationale as AppPrefs's
 * Context-dependent methods being untested. These cover only the logic that doesn't touch
 * the filesystem: keyword matching, raw-value normalization, and plausibility checks.
 *
 * Run with: ./gradlew test
 */
public class ThermalZonesTest {

    // ── typeMatches() ─────────────────────────────────────────────────────
    @Test public void typeMatches_exact()          { assertTrue(ThermalZones.typeMatches("cpu", "cpu")); }
    @Test public void typeMatches_substring()      { assertTrue(ThermalZones.typeMatches("soc_thermal", "soc")); }
    @Test public void typeMatches_caseInsensitive(){ assertTrue(ThermalZones.typeMatches("CPU_Thermal", "cpu")); }
    @Test public void typeMatches_anyOfMultiple()  { assertTrue(ThermalZones.typeMatches("mtktscpu", "gpu", "mtktscpu")); }
    @Test public void typeMatches_noMatch()        { assertFalse(ThermalZones.typeMatches("battery", "cpu", "gpu")); }
    @Test public void typeMatches_emptyKeywords()  { assertFalse(ThermalZones.typeMatches("cpu")); }

    // ── normalize() ───────────────────────────────────────────────────────
    @Test public void normalize_wholeDegreesUnchanged() { assertEquals(45f,   ThermalZones.normalize(45f),   0.001f); }
    @Test public void normalize_milliDegreesConverted() { assertEquals(45f,   ThermalZones.normalize(45000f),0.001f); }
    @Test public void normalize_boundaryAt1000()        { assertEquals(1000f, ThermalZones.normalize(1000f), 0.001f); }
    @Test public void normalize_justOver1000Converts()  { assertEquals(1.001f,ThermalZones.normalize(1001f), 0.001f); }

    // ── isPlausible() ─────────────────────────────────────────────────────
    @Test public void isPlausible_normalTemp()   { assertTrue(ThermalZones.isPlausible(45f)); }
    @Test public void isPlausible_zeroRejected() { assertFalse(ThermalZones.isPlausible(0f)); }
    @Test public void isPlausible_negativeRejected() { assertFalse(ThermalZones.isPlausible(-5f)); }
    @Test public void isPlausible_justUnder200() { assertTrue(ThermalZones.isPlausible(199.9f)); }
    @Test public void isPlausible_200Rejected()  { assertFalse(ThermalZones.isPlausible(200f)); }
    @Test public void isPlausible_wayTooHigh()   { assertFalse(ThermalZones.isPlausible(999f)); }
}
