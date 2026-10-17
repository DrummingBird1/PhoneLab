# PhoneLab — Changelog v1.2.0 → v1.3.0

`versionCode 3` → `versionCode 4`

v1.2.0 was never published to Google Play; its GitHub release is the last public build.
v1.3.0 is the first release with two distribution flavors and an open-source license.

## New

- **Tools tab** — bubble level (with "set as zero"), compass calibration (figure-eight guide), magnetometer metal detector, and a vibration seismograph with an FFT frequency readout.
- **Device check tab** — 11 guided hardware tests (touch grid, multi-touch, display colors, speaker, microphone, vibration, volume buttons, proximity, flashlight, motion sensors, battery) run in a dedicated `DiagnosticTestActivity`, with a shareable pass/fail report.
- **Battery health** on the Hardware tab — health status, charging power in watts, estimated full capacity, charge cycles and a 24-hour battery-level history sampled by the background worker.
- **Custom alerts** — user-defined rules ("notify me when X goes above/below Y") with hysteresis, evaluated by the existing periodic worker.
- **System tab** — GPU model / renderer info in the specs (and in both exports) and a CPU-temperature trend sparkline.
- **Material You** (opt-in) dynamic colors, and an in-app **App language** setting backed by Android 13+ per-app languages.
- **Launcher shortcuts** for Device check and Tools (replacing the Hardware one), next to Record and Benchmark.
- **Tip jar** (Google Play build only) — consumable Play Billing tips in About.
- **Source code button** in About.

## Changed

- **Material 3 redesign** — `Theme.Material3.DayNight` with full color roles; every reading is a reusable `SensorCardView` / `GaugeCardView` in responsive grids (2 → 3 → 4 columns on wider screens); vector icons on tabs and cards; themed (monochrome) launcher icon.
- **Visual mode** now uses radial gauges and bar meters with ok / warn / hot bands.
- **Edge-to-edge** layout on all Android versions.
- The benchmark runs in a `ViewModel`, so rotating the screen no longer cancels it; sensor counters survive rotation too.
- GPS falls back to the network location provider when GPS has no fix.
- `SensorsFragment` split into `SensorReadouts`, `SoundMeter`, `GpsTracker` and `RecordingController`.
- Release builds are minified with R8 (`minifyEnabled` + `shrinkResources`).

## Distribution

- **Two flavors:** `play` (Google Play, with the tip jar) and `foss` (GitHub Releases, F-Droid, IzzyOnDroid and other stores — no proprietary libraries and **no `INTERNET` permission**).
- **License:** the source is now **GPL-3.0** (previously all rights reserved).
- **Store metadata** in `fastlane/metadata/android/` (en-US, he, es, ar) for F-Droid / IzzyOnDroid.
- Gradle Play Publisher uploads the `play` bundle to the internal track (`publishPlayReleaseBundle`); CI lints both flavors.

## Fixed

- CSV recording handles the Android 15 `dataSync` foreground-service time limit (`onTimeout`) by saving and stopping cleanly.
- Status-bar icons are now light on dark when the app theme differs from the system theme (edge-to-edge was set up before AppCompat applied the app's night mode).
- Sparkline and accent colors follow Material You dynamic colors.
