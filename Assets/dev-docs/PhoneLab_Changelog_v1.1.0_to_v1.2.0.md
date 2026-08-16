# PhoneLab — Changelog v1.1.0 → v1.2.0

`versionCode 2` → `versionCode 3`

## New

- **Hardware tab** — new fifth tab surfacing per-core live CPU frequency, detailed battery stats (capacity, charge counter, current, energy, cycle count on API 34+), display info (current mode, supported refresh rates, HDR types), and capability flags (NFC, USB host, Bluetooth/BLE, camera count, biometric enrollment via `androidx.biometric`).
- **6 additional sensors** on the Sensors tab: Accelerometer Uncalibrated, Magnetometer Uncalibrated, Geomagnetic Rotation Vector, Stationary Detect, Motion Detect, and Hinge Angle (foldables, API 30+).
- **CSV sensor recording** — record a live session of sensor + GPS + trigger-sensor events to a timestamped CSV file, then share it via the system share sheet.
- **Thermal alerts** — a system notification fires when CPU temperature crosses 80°C, with hysteresis (won't re-fire until it cools to 65°C) and a "Don't show again" action. Toggle lives in Settings.
- **Copy specs to clipboard** and **JSON-free plain-text spec export**, alongside the existing file export.

## Changed

- Display-mode (textual/visual) toggling and unit-system changes now apply **immediately**, not just on tab re-entry, via a shared `ModeAwareFragment` base class.
- `MainActivity` now uses the default off-screen page limit instead of keeping all tabs resident — distant tabs unload their sensor/GPS/mic listeners when you're not on them, improving battery life.
- GPS location update interval relaxed to 2s/5m (was 1s/0m) — plenty for a dashboard, meaningfully lighter on battery.
- Sound-level meter now reads continuously instead of polling with `Thread.sleep`, eliminating dropped audio frames.

## Fixed

- Battery broadcast receiver now explicitly declares `RECEIVER_NOT_EXPORTED`, required on Android 14+.
- CSV export correctly escapes commas/quotes/newlines in field values (RFC 4180).
- Step Counter correctly detects and re-bases across a device reboot instead of reporting an inflated session count.
- Various null/empty-array defensive checks against OEM sensor driver quirks.
