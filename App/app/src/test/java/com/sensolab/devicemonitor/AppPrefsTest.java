package com.sensolab.devicemonitor;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Local JUnit tests for the pure unit-conversion methods in AppPrefs.
 * Context-dependent methods (temp(ctx), speed(ctx), ...) are not covered here
 * — those need Robolectric or instrumented tests.
 *
 * Run with: ./gradlew test
 */
public class AppPrefsTest {

    private static final float EPS = 0.001f;

    // ── Temperature: °C → °F ──────────────────────────────────────────────
    @Test public void cToF_freezing()    { assertEquals(  32f, AppPrefs.cToF(  0f), EPS); }
    @Test public void cToF_body()        { assertEquals(  98.6f, AppPrefs.cToF( 37f), 0.05f); }
    @Test public void cToF_boiling()     { assertEquals( 212f, AppPrefs.cToF(100f), EPS); }
    @Test public void cToF_negative()    { assertEquals(  -40f, AppPrefs.cToF(-40f), EPS); } // famous fixed point

    // ── Speed: m/s → km/h, mph ────────────────────────────────────────────
    @Test public void mpsToKmh_basic()   { assertEquals( 36f, AppPrefs.mpsToKmh(10f), EPS); }
    @Test public void mpsToKmh_zero()    { assertEquals(  0f, AppPrefs.mpsToKmh( 0f), EPS); }
    @Test public void mpsToMph_basic()   { assertEquals( 22.3694f, AppPrefs.mpsToMph(10f), 0.001f); }
    @Test public void mpsToMph_zero()    { assertEquals(  0f, AppPrefs.mpsToMph( 0f), EPS); }

    // ── Length: m → ft ────────────────────────────────────────────────────
    @Test public void mToFt_basic()      { assertEquals(  3.28084f, AppPrefs.mToFt(1f),     0.001f); }
    @Test public void mToFt_km()         { assertEquals(3280.84f,   AppPrefs.mToFt(1000f),  0.1f);   }

    // ── Pressure: hPa → inHg ──────────────────────────────────────────────
    @Test public void hpaToInHg_basic()  { assertEquals( 29.530f, AppPrefs.hpaToInHg(1000f), 0.01f); }
    @Test public void hpaToInHg_standard() {
        // 1013.25 hPa (standard atmosphere) ≈ 29.92 inHg
        assertEquals(29.92f, AppPrefs.hpaToInHg(1013.25f), 0.01f);
    }

    // ── Sanity / regression guards ───────────────────────────────────────
    @Test public void noNegativeFromPositive() {
        assertTrue(AppPrefs.mpsToKmh(10f) > 0f);
        assertTrue(AppPrefs.mToFt(1f) > 0f);
        assertTrue(AppPrefs.hpaToInHg(1000f) > 0f);
    }

    @Test public void identities() {
        // Round-trip through unit constants should be lossless to a few digits
        float c = 25f;
        float f = AppPrefs.cToF(c);
        float c2 = (f - 32f) * 5f / 9f;
        assertEquals(c, c2, 0.001f);
    }
}
