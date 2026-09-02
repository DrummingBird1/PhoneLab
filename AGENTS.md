# AGENTS.md

This file gives Codex the orientation it needs to work in this repository.

## Project

**PhoneLab** — a single-Activity Android app that surfaces every hardware sensor, system info, thermal zone, and a CPU benchmark in real time. Published to Google Play under `com.sensolab.devicemonitor`.

Language: **Java** (not Kotlin). UI: classic Android Views + Material Components (no Jetpack Compose).

## Repository layout

This **is** a git repo (branch `main`). The root was reorganised into four top-level folders plus the meta files (`.git`, `.gitignore`, `CLAUDE.md`, `AGENTS.md`, `.claude/`):

```
PhoneLab/
├── App/                 # ★ active dev source = the Gradle project root (open THIS in Android Studio)
│   ├── gradlew, build.gradle, settings.gradle, key.properties.template
│   └── app/…            # the single :app module
├── Assets/              # store-listing materials (used when filling app info on Play & other stores)
│   ├── PhoneLab_PlayStore_Description.md
│   ├── PhoneLab_icon_512x512.png, PhoneLab_feature_1024x500.png
│   ├── screenshots/     # store screenshots
│   └── dev-docs/        # changelog, Material3 redesign plan (developer docs, not store copy)
├── Distribution/        # things tied to shipping
│   └── Keys/PhoneLab-Key   # signing key (git-ignored) — see "Signing key"
│   └── (built release APK/AAB you upload go here)
└── Archive/             # frozen old versions & superseded artifacts
    ├── SensoLab V1.0.0/    # frozen v1.0.0 source snapshot (immutable, not edited — kept under its original name)
    ├── app-release-v1.0.0-shipped.aab
    └── app-release-v1.1.0-code2-stale.aab
```

**The Gradle project root is `App/`.** All active work happens there. `Archive/SensoLab V1.0.0/` is kept as an immutable snapshot, not edited — including its old name, since the folder itself is frozen and out of scope for the SensoLab→PhoneLab rename.

## Build & run

From `App/` (the Gradle wrapper is present and CLI-buildable):

```powershell
.\gradlew assembleDebug          # APK at app/build/outputs/apk/debug/ (verified working)
.\gradlew assembleRelease        # APK (signed only if key.properties exists — see below)
.\gradlew bundleRelease          # AAB for Play Store
.\gradlew installDebug           # installs over ADB
.\gradlew test                   # JUnit unit tests (junit:4.13.2)
```

The Gradle wrapper pins **Gradle 8.13** (`gradle/wrapper/gradle-wrapper.properties`). It was regenerated from a cached 8.13 distribution after the tree was found missing `gradlew`/`gradle-wrapper.jar`; 8.13 is compatible with AGP 8.7.3. The project also builds through Android Studio's bundled Gradle.

Build prerequisites: JDK 21 (sourceCompatibility = VERSION_21), Android SDK 35, AGP 8.7.3, Gradle 8.13 (bundled via wrapper).

**Release signing** is driven by `key.properties` in the module root (git-ignored; see `key.properties.template`). It supplies `storeFile` / `storePassword` / `keyAlias` / `keyPassword`. If the file is absent, `assembleRelease`/`bundleRelease` still configure but produce an **unsigned** artifact rather than failing — so debug work and CI don't need the key.

Tests: there **are** JUnit unit tests (`testImplementation 'junit:junit:4.13.2'`), run via `.\gradlew test` — no Espresso / instrumentation tests.

## Architecture (one screen, five tabs)

`MainActivity` hosts a `ViewPager2` + `TabLayout` with five `Fragment`s (`App/app/src/main/java/com/sensolab/devicemonitor/MainActivity.java`):

| Tab | Class | Role |
|---|---|---|
| 📡 Sensors | `SensorsFragment` | Live readings for ~23 sensors + GPS + mic |
| ⚙️ System | `SystemFragment` | Device info, thermal zones, exporter, benchmark |
| 🔧 Hardware | `HardwareFragment` | Hardware capabilities (added v1.2.0-dev; uses `androidx.biometric` for biometric availability) |
| ℹ️ About | `AboutFragment` | Rate / share / email |
| ⚙️ Settings | `SettingsFragment` | Theme · Display mode · Unit system |

Cross-cutting:
- **`AppPrefs`** — single static facade over `SharedPreferences`. Holds keys, mode constants, and *every unit-conversion function* (`temp`, `speed`, `altitude`, `pressure`, `distUnit`, …). All sensor readouts must go through these helpers; never format °C/km/h/hPa inline.
- **`CompassView`** — custom `View` that draws the compass rose and rotating needle. Updated from `SensorsFragment` via `setBearing(degrees)`; uses internal lerp (`0.25f`) for smoothing, so don't call it on a separate thread.

Display-mode pattern: each fragment inflates **both** a textual `LinearLayout` and a visual `LinearLayout`, then toggles `View.VISIBLE` / `View.GONE` in `applyMode()` based on `AppPrefs.isVisual()`. This is now factored into a shared base class **`ModeAwareFragment`** — subclasses call `bindModeLayouts(textual, visual)` from `onCreateView`, and the base registers a `SharedPreferences` listener so a display-mode toggle re-applies **immediately** (previously it only took effect on tab re-entry) and a unit-system change calls the `onUnitsChanged()` hook.

Helper classes worth knowing: **`ThermalAlerts`** (+ **`ThermalSilenceReceiver`**) posts thermal-warning notifications via a pre-created channel; **`CsvRecorder`** handles the sensor-log export; **`Formatters`** centralises value formatting; **`NamedThreads`** provides named daemon threads for the mic/GPS/benchmark workers.

Theme switching: `MainActivity.recreateWithTheme()` writes the pref, calls `AppCompatDelegate.setDefaultNightMode(...)`, and `recreate()`s the activity. Display-mode and unit changes do **not** recreate — they take effect when the user re-enters a tab. This is documented in the settings screen.

## Non-obvious things to know

- **`namespace` lives in `app/build.gradle`** (`com.sensolab.devicemonitor`). `AndroidManifest.xml` no longer carries a `package="..."` attribute — the namespace is the single source of the package id.
- **`SensorsFragment.TYPE_TILT_DETECTOR = 22`** is a hardcoded magic number. `Sensor.TYPE_TILT_DETECTOR` has no public constant; 22 is the framework value. Don't "fix" it to a named constant — it doesn't exist.
- **Thermal zones are read directly from `/sys/class/thermal/thermal_zone*`**. The `zone(String... keywords)` helper in `SystemFragment` matches by sub-string against `type`. OEMs name zones differently (`soc_thermal`, `mtktscpu`, `tsens_tz_sensor0`, …) — when a zone reports `N/A` on a specific device, the fix is *almost always* to add another keyword to the right `zone(...)` call, not to change the logic.
- **`Sound Level` uses `AudioRecord` directly** (not `MediaRecorder`), on a daemon thread that posts dB values back through `mainHandler`. Polling is `Thread.sleep(200)`; the thread is interrupted on `onPause`.
- **GPS uses only `GPS_PROVIDER`** (no Google Play Services / FusedLocation). Indoors / without GPS lock, speed and altitude stay blank.
- **`MainActivity` uses `setOffscreenPageLimit(OFFSCREEN_PAGE_LIMIT_DEFAULT)`** (was `4`). Only neighbouring tabs stay resident; distant tabs unload — this was a deliberate battery fix (P2) so the mic/GPS/sensor listeners of far-off tabs stop when you move away, at the cost of a rebuild on return.
- **`FileProvider` authority** is `${applicationId}.fileprovider`. Exports land in `getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)` and are shared via `Intent.ACTION_SEND` — no `WRITE_EXTERNAL_STORAGE` permission needed on API 26+.
- **ProGuard is disabled** in the release build (`minifyEnabled false`). `proguard-rules.pro` is effectively empty.
- **Localization**: 241 user-visible strings live in `res/values/strings.xml` (English, default) plus three full translations — `res/values-iw/` (Hebrew, ISO-639-1 "iw" which Android maps to modern "he"), `res/values-es/` (Spanish), `res/values-ar/` (Arabic). Keep all four in sync when adding/renaming/removing a string — it's easy to touch only English and Hebrew and forget these other two exist. Java code uses `getString(R.string.X)` or `getString(R.string.X, args)` for format strings; layouts use `@string/X`. RTL is enabled via `android:supportsRtl="true"` (relevant for both Hebrew and Arabic). **The remaining hardcoded English fragments are scientific format codes** ("X:%+.3f Y:%+.3f Z:%+.3f", "m/s²", "rad/s", "Pitch", "Roll", "Azimuth", date formats, etc.) — these are universal/technical and intentionally not translated. The benchmark result template and CSV-style export file are also English-only on purpose (developer-oriented).
- **CompassView colors** live in `colors.xml` (named `compass_*`) and are theme-independent — the compass is meant to look like a fixed instrument regardless of light/dark mode.

## Permissions

Manifest declares 6 runtime permissions; `MainActivity.requestPermissions()` requests them at startup. `ACTIVITY_RECOGNITION` is only requested on API 29+, and `POST_NOTIFICATIONS` only on API 33+.

| Permission | Used by |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS speed & altitude |
| `ACTIVITY_RECOGNITION` | Step Counter / Step Detector on API 29+ |
| `BODY_SENSORS` | Heart Rate sensor |
| `RECORD_AUDIO` | Sound-level meter |
| `POST_NOTIFICATIONS` | Thermal-alert notifications on API 33+ (`ThermalAlerts`) |

All `<uses-feature>` entries are `required="false"` so the app installs on devices missing any sensor — the fragments fall back to "Not available" strings.

## Versioning

`app/build.gradle` is the single source of truth:
- `versionCode 3`
- `versionName "1.2.0"`
- `minSdk 26`, `targetSdk 35`, `compileSdk 35`

Version history: v1.0.0 (shipped) → v1.1.0 (`versionCode 2`) → **v1.2.0 (`versionCode 3`, current)** — the v1.2.0 work added the Hardware tab, 6 more sensors, CSV recording, and thermal alerts. Changelog v1.0.0 → v1.1.0 lives at `Assets/dev-docs/PhoneLab_Changelog_v1.0.0_to_v1.1.0.md`.

⚠️ There is **no current release build on disk** — the only built bundles are in `Archive/` and are both stale (`app-release-v1.0.0-shipped.aab`, and `app-release-v1.1.0-code2-stale.aab` at `versionCode 2`). Build the v1.2.0 release fresh with `.\gradlew bundleRelease` from `App/` (after filling `key.properties`), and put the output in `Distribution/` before uploading.

## Signing key

The PhoneLab upload keystore is the file **`Distribution/Keys/PhoneLab-Key`**. It contains the private signing key — treat it as a secret and back it up; if lost you cannot publish updates to Google Play under this app id.

⚠️ `Distribution/Keys/` lives **inside the git repo**. `.gitignore` blocks the whole `Keys/` folder (plus `PhoneLab-Key`, `*.keystore`, `*.jks`, `*.pepk`, `key.properties`, `*.aab`, `*.apk`), and nothing under it is tracked — but keeping signing keys inside a repo working tree is inherently riskier than outside it. `key.properties` points `storeFile` at an absolute path on the machine that builds releases, so the key can be relocated outside the repo without code changes.

The filename mentions "PhoneLab", which doesn't match the app id `com.sensolab.devicemonitor`. Before signing the next release, verify the keystore actually corresponds to the upload key registered on Google Play (run `keytool -list -v -keystore Distribution\Keys\PhoneLab-Key` and compare the SHA-1/SHA-256 fingerprint against the one shown in the Play Console → App integrity → Upload key certificate).
