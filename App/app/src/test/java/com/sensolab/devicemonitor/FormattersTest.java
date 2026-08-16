package com.sensolab.devicemonitor;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the pure helpers in {@link Formatters}. These cover logic that was
 * written but never executed on a device (CSV recording, recording timer,
 * benchmark history capping), so they validate behaviour without Robolectric.
 *
 * Run with: ./gradlew test
 */
public class FormattersTest {

    // ── duration() ────────────────────────────────────────────────────────
    @Test public void duration_zero()        { assertEquals("00:00", Formatters.duration(0)); }
    @Test public void duration_seconds()     { assertEquals("00:05", Formatters.duration(5)); }
    @Test public void duration_minute()      { assertEquals("01:00", Formatters.duration(60)); }
    @Test public void duration_mmss()        { assertEquals("02:34", Formatters.duration(154)); }
    @Test public void duration_underHour()   { assertEquals("59:59", Formatters.duration(3599)); }
    @Test public void duration_exactlyHour() { assertEquals("1:00:00", Formatters.duration(3600)); }
    @Test public void duration_hms()         { assertEquals("1:02:03", Formatters.duration(3723)); }
    @Test public void duration_negativeClamped() { assertEquals("00:00", Formatters.duration(-10)); }

    // ── csvField() ────────────────────────────────────────────────────────
    @Test public void csv_plain()        { assertEquals("hello", Formatters.csvField("hello")); }
    @Test public void csv_null()         { assertEquals("", Formatters.csvField(null)); }
    @Test public void csv_empty()        { assertEquals("", Formatters.csvField("")); }
    @Test public void csv_comma()        { assertEquals("\"a,b\"", Formatters.csvField("a,b")); }
    @Test public void csv_quote()        { assertEquals("\"a\"\"b\"", Formatters.csvField("a\"b")); }
    @Test public void csv_newline()      { assertEquals("\"a\nb\"", Formatters.csvField("a\nb")); }
    @Test public void csv_cr()           { assertEquals("\"a\rb\"", Formatters.csvField("a\rb")); }
    @Test public void csv_safeNameUnchanged() {
        // A typical sensor name has no special chars and must pass through verbatim
        assertEquals("LSM6DSO Accelerometer", Formatters.csvField("LSM6DSO Accelerometer"));
    }

    // ── historyPrepend() ──────────────────────────────────────────────────
    @Test public void history_firstEntry() {
        assertEquals("row1", Formatters.historyPrepend("", "row1", 5));
    }
    @Test public void history_nullExisting() {
        assertEquals("row1", Formatters.historyPrepend(null, "row1", 5));
    }
    @Test public void history_prependsNewest() {
        assertEquals("new\nold", Formatters.historyPrepend("old", "new", 5));
    }
    @Test public void history_capsAtMax() {
        String existing = "e1\ne2\ne3\ne4\ne5";          // already 5 rows
        String result = Formatters.historyPrepend(existing, "new", 5);
        assertEquals("new\ne1\ne2\ne3\ne4", result);      // oldest (e5) dropped, total 5
        assertEquals(5, result.split("\n").length);
    }
    @Test public void history_underMaxKeepsAll() {
        String result = Formatters.historyPrepend("a\nb", "c", 5);
        assertEquals("c\na\nb", result);
    }
    @Test public void history_maxOne() {
        assertEquals("new", Formatters.historyPrepend("old", "new", 1));
    }
}
