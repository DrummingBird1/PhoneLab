# CLAUDE.md

This file gives Claude Code the orientation it needs to work in this repository.

## Project

**SensoLab** — a single-Activity Android app that surfaces every hardware sensor, system info, thermal zone, and a CPU benchmark in real time. Published to Google Play under `com.sensolab.devicemonitor`.

Language: **Java** (not Kotlin). UI: classic Android Views + Material Components (no Jetpack Compose).

## Repository layout

This is **not** a git repo and **not** a single-module Gradle project. The root is a folder of artifacts:

```
SensoLab/
├── Builds/
│   ├── SensoLab V1.0.0/SensoLab/      # frozen v1.0.0 source snapshot
│   └── SensoLab V1.1.0/SensoLab/      # ★ active dev source (Gradle project root)
├── Docs/                              # Play Store copy
├── Icons/                             # store screenshots + icons
└── Releases/release/app-release.aab   # shipped v1.0.0 bundle
```

The signing keystore (`PhoneLab-Key`) was moved out of the project to **`D:\AI\Keys\PhoneLab-Key`** — see "Signing key" section below.

**The Gradle project root is `Builds/SensoLab V1.1.0/SensoLab/`** — that path has the space in it; quote it in commands. All later work should happen there. Older versions under `Builds/` are kept as immutable snapshots, not edited.

## Build & run

From `Builds/SensoLab V1.1.0/SensoLab/`:

```powershell
.\gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
.\gradlew assembleRelease        # APK
.\gradlew bundleRelease          # AAB for Play Store
.\gradlew installDebug           # installs over ADB
```

Build prerequisites: JDK 21 (sourceCompatibility = VERSION_21), Android SDK 35, AGP 8.7.3, Gradle wrapper bundled.

There is **no test task** — the project has no JUnit / Espresso tests.

## Architecture (one screen, four tabs)

`MainActivity` hosts a `ViewPager2` + `TabLayout` with four `Fragment`s:

| Tab | Class | Role |
|---|---|---|
| 📡 Sensors | `SensorsFragment` | Live readings for 17 sensors + GPS + mic |
| ⚙️ System | `SystemFragment` | Device info, 8 thermal zones, exporter, benchmark |
| ℹ️ About | `AboutFragment` | Rate / share / email |
| 🔧 Settings | `SettingsFragment` | Theme · Display mode · Unit system |

Cross-cutting:
- **`AppPrefs`** — single static facade over `SharedPreferences`. Holds keys, mode constants, and *every unit-conversion function* (`temp`, `speed`, `altitude`, `pressure`, `distUnit`, …). All sensor readouts must go through these helpers; never format °C/km/h/hPa inline.
- **`CompassView`** — custom `View` that draws the compass rose and rotating needle. Updated from `SensorsFragment` via `setBearing(degrees)`; uses internal lerp (`0.25f`) for smoothing, so don't call it on a separate thread.

Display-mode pattern: each fragment inflates **both** a textual `LinearLayout` and a visual `LinearLayout`, then toggles `View.VISIBLE` / `View.GONE` in `applyMode()` based on `AppPrefs.isVisual()`. There is no shared base class — the pattern is duplicated in `SensorsFragment` and `SystemFragment`.

Theme switching: `MainActivity.recreateWithTheme()` writes the pref, calls `AppCompatDelegate.setDefaultNightMode(...)`, and `recreate()`s the activity. Display-mode and unit changes do **not** recreate — they take effect when the user re-enters a tab. This is documented in the settings screen.

## Non-obvious things to know

- **`namespace` lives in `app/build.gradle`** (`com.sensolab.devicemonitor`). `AndroidManifest.xml` still has a redundant `package="..."` attribute — this is deprecated since AGP 7.0 but currently harmless.
- **`SensorsFragment.TYPE_TILT_DETECTOR = 22`** is a hardcoded magic number. `Sensor.TYPE_TILT_DETECTOR` has no public constant; 22 is the framework value. Don't "fix" it to a named constant — it doesn't exist.
- **Thermal zones are read directly from `/sys/class/thermal/thermal_zone*`**. The `zone(String... keywords)` helper in `SystemFragment` matches by sub-string against `type`. OEMs name zones differently (`soc_thermal`, `mtktscpu`, `tsens_tz_sensor0`, …) — when a zone reports `N/A` on a specific device, the fix is *almost always* to add another keyword to the right `zone(...)` call, not to change the logic.
- **`Sound Level` uses `AudioRecord` directly** (not `MediaRecorder`), on a daemon thread that posts dB values back through `mainHandler`. Polling is `Thread.sleep(200)`; the thread is interrupted on `onPause`.
- **GPS uses only `GPS_PROVIDER`** (no Google Play Services / FusedLocation). Indoors / without GPS lock, speed and altitude stay blank.
- **`MainActivity.setOffscreenPageLimit(4)`** keeps all four fragments resident. Sensors and timers therefore keep running while user is on the About tab, which is intentional (instant tab switching) but means the app is never fully idle.
- **`FileProvider` authority** is `${applicationId}.fileprovider`. Exports land in `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)` and are shared via `Intent.ACTION_SEND` — no `WRITE_EXTERNAL_STORAGE` permission needed on API 26+.
- **ProGuard is disabled** in the release build (`minifyEnabled false`). `proguard-rules.pro` is effectively empty.
- All user-visible strings are **hardcoded** in Java and XML; only `app_name` is in `strings.xml`. No localization is in place.

## Permissions

Manifest declares 5 runtime permissions; `MainActivity.requestPermissions()` requests them at startup. `ACTIVITY_RECOGNITION` is only requested on API 29+.

| Permission | Used by |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS speed & altitude |
| `ACTIVITY_RECOGNITION` | Step Counter / Step Detector on API 29+ |
| `BODY_SENSORS` | Heart Rate sensor |
| `RECORD_AUDIO` | Sound-level meter |

All `<uses-feature>` entries are `required="false"` so the app installs on devices missing any sensor — the fragments fall back to "Not available" strings.

## Versioning

`app/build.gradle` is the single source of truth:
- `versionCode 2`
- `versionName "1.1.0"`
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`

Changelog v1.0.0 → v1.1.0 lives at `Builds/SensoLab V1.1.0/SensoLab_Changelog_v1.0.0_to_v1.1.0.md`. The `Releases/` folder still holds the v1.0.0 AAB; the v1.1.0 AAB is at `Builds/SensoLab V1.1.0/SensoLab/app/release/app-release.aab`.

## Signing key

The Java keystore lives **outside the repo** at `D:\AI\Keys\PhoneLab-Key`. It contains the private signing key — treat it as a secret and back it up; if lost you cannot publish updates to Google Play under this app id.

The filename mentions "PhoneLab", which doesn't match the app id `com.sensolab.devicemonitor`. Before signing the next release, verify the keystore actually corresponds to the upload key registered on Google Play (run `keytool -list -v -keystore D:\AI\Keys\PhoneLab-Key` and compare the SHA-1/SHA-256 fingerprint against the one shown in the Play Console → App integrity → Upload key certificate).

`.gitignore` blocks `PhoneLab-Key`, `*.keystore`, `*.jks`, `*.pepk`, and `key.properties` — keep it that way.
