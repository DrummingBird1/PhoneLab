# AGENTS.md

This file gives Codex the orientation it needs to work in this repository. It mirrors CLAUDE.md — keep the two in sync.

## Project

**PhoneLab** — a single-Activity Android app that surfaces every hardware sensor, system info, thermal zone, battery health, a CPU benchmark, sensor tools and a guided device check, all in real time. App id `com.sensolab.devicemonitor`; distributed on GitHub Releases (APK) and headed for Google Play, F-Droid / IzzyOnDroid and other stores. License: **GPL-3.0-only** (`LICENSE`). A companion browser dashboard ("SensoLab Web") lives in `Web/`.

Language: **Java** (not Kotlin). UI: classic Android Views + **Material 3** Material Components (no Jetpack Compose).

## Repository layout

This **is** a git repo (branch `main`). Top-level folders plus the meta files (`.git`, `.gitignore`, `CLAUDE.md`, `AGENTS.md`, `.claude/`, `.github/`):

```
PhoneLab/
├── App/                 # ★ Android app = the Gradle project root (open THIS in Android Studio)
│   ├── gradlew, build.gradle, settings.gradle, key.properties.template
│   └── app/…            # the single :app module
├── Web/                 # SensoLab Web — Vite + TypeScript PWA, deployed to Vercel from main
├── docs/                # GitHub Pages landing page (drummingbird1.github.io/PhoneLab)
├── Assets/              # store-listing materials (description, icon, feature graphic, screenshots/)
│   └── dev-docs/        # changelogs, Material3 redesign plan (developer docs, not store copy)
├── fastlane/metadata/android/  # store listing per locale (en-US, he, es, ar): text, changelogs/<versionCode>.txt, images (en-US)
├── Distribution/        # things tied to shipping
│   ├── Keys/PhoneLab-Key   # signing key (git-ignored) — see "Signing key"
│   ├── fdroid/          # F-Droid build recipe (copy into an fdroiddata MR)
│   ├── play/README.md   # Google Play: first upload, service account, CI secrets, tip-jar products
│   ├── stores/README.md # IzzyOnDroid, F-Droid, APKPure, Uptodown, … + store status table
│   └── derive_screenshots.py  # fastlane screenshots → Play (9:16, no alpha) + docs/ WebP
└── Archive/             # frozen old versions & superseded artifacts (immutable, not edited)
```

`Archive/SensoLab V1.0.0/` is kept as an immutable snapshot under its original name.

## Build & run (Android)

From `App/`. **Use JDK 21** — set `JAVA_HOME` to the Microsoft OpenJDK 21 install (`C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot` at last check; the folder name changes with updates). The system JDK and Android Studio's JBR have drifted to JDK 25, which breaks the build tooling.

```powershell
.\gradlew assembleFossDebug          # APK at app/build/outputs/apk/foss/debug/
.\gradlew assembleFossRelease        # GitHub / F-Droid / stores APK (signed only if key.properties exists)
.\gradlew bundlePlayRelease          # AAB for Google Play
.\gradlew installFossDebug           # installs over ADB
.\gradlew test                       # JUnit unit tests (no Espresso / instrumentation tests)
.\gradlew lintPlayRelease lintFossRelease  # lint; only findings not in app/lint-baseline.xml fail
.\gradlew publishPlayReleaseBundle   # Gradle Play Publisher → Play internal track (needs credentials)
```

**Two product flavors** (dimension `distribution`, `BuildConfig.PLAY_BUILD`): `play` adds Play Billing (`playImplementation`) and the real `TipJar` in `src/play/`; `foss` gets a no-op `TipJar` stub in `src/foss/`, no proprietary libraries and therefore **no `INTERNET` permission**. Everything except the tip jar and the About tab's Rate button is shared in `src/main/`. Anything Play-only goes in `src/play/` with a stub in `src/foss/` — never reference Billing from `main`.

Toolchain: **Gradle 8.14.5** (wrapper), **AGP 8.13.2**, `compileSdk 36` / `targetSdk 36` / `minSdk 26`, JDK 21 (`sourceCompatibility VERSION_21`, core-library desugaring on). AGP 9 / GPP 4 are deliberately not used yet (both are breaking upgrades).

**Release signing** comes from `key.properties` in `App/` (git-ignored; see `key.properties.template`). Without it, release builds are produced **unsigned** instead of failing.

**R8 is on** for release (`minifyEnabled true`, `shrinkResources true`). `proguard-rules.pro` only keeps file/line info — AAPT and the AndroidX/Billing libraries supply the keep rules for manifest components, XML-referenced custom views, Workers and ViewModels. Add a rule only for something reached via reflection. Upload `app/build/outputs/mapping/playRelease/mapping.txt` with each Play release.

**Gradle Play Publisher** (`com.github.triplet.play` 3.13) uploads DRAFT releases of the `play` flavor to the internal track (`playConfigs` disables it for `foss`). Credentials: `App/play-service-account.json` (git-ignored) or the `ANDROID_PUBLISHER_CREDENTIALS` env var; without either the publish tasks don't exist. The Play listing lives in `app/src/play/play/` (Triple-T layout: `listings/<locale>/` for en-US, iw-IL, es-ES, es-419, ar; graphics in en-US only) — its full description deliberately differs from fastlane's (the Play build has `INTERNET`). Play's "What's new" is synced from `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` into the git-ignored `release-notes/` by `syncPlayReleaseNotes`, which runs before GPP's `generatePlayReleasePlayResources`; a missing changelog fails the publish. Setup steps: `Distribution/play/README.md`.

**CI** (`.github/workflows/`): `android-ci.yml` runs test + assembleDebug (both flavors) + `lintPlayRelease lintFossRelease` on push/PR; `android-release.yml` (manual) builds a signed `play` bundle from secrets and publishes it to the internal track; `web-ci.yml` covers `Web/`.

## Architecture (one Activity, seven tabs)

`MainActivity` hosts a scrollable `TabLayout` (icon + label) over a `ViewPager2`. Tab indexes are constants (`MainActivity.TAB_*`) used by launcher shortcuts and notifications:

| # | Tab | Class | Role |
|---|---|---|---|
| 0 | Sensors | `SensorsFragment` | Live readings for ~25 sensors + GPS + mic; textual cards or gauges; CSV recording |
| 1 | System | `SystemFragment` | 8 thermal zones, specs (incl. GPU via `GpuInfo`), benchmark, text/JSON export |
| 2 | Hardware | `HardwareFragment` | Battery health (health, power W, est. capacity, cycles, 24 h history), CPU freqs, display, capabilities |
| 3 | Tools | `ToolsFragment` | Bubble level, compass calibration, magnetometer metal detector, vibration seismograph + FFT |
| 4 | Device check | `DiagnosticsFragment` | Guided hardware tests run in `DiagnosticTestActivity`, shareable pass/fail report |
| 5 | About | `AboutFragment` | Rate / share / tip jar / web version / privacy |
| 6 | Settings | `SettingsFragment` | Theme, Material You, app language, display mode, units, thermal + custom alerts, battery history |

**Edge-to-edge:** `EdgeToEdge.enable()` in `MainActivity` (and `DiagnosticTestActivity`); the root pads for status bar/cutout and the pager for the navigation bar. Full-panel diagnostic tests hide the system bars instead.

**Material 3:** theme parent is `Theme.Material3.DayNight.NoActionBar` with full color roles in `values/colors.xml` + `values-night/colors.xml`. The custom `?attr/colorBackground` used by layouts points at `android:colorBackground` so **dynamic color** (opt-in, `PhoneLabApp` + `AppPrefs.isDynamicColor`) recolors everything. Gauge band colors (`gauge_ok/warn/hot`) and `compass_*` colors are intentionally fixed.

**Reusable views** (all follow the `CompassView` contract: preallocated paints, internal smoothing, main thread only):
- `SensorCardView` — M3 card with label / large value + unit / detail line. `setReading()`, `setStatus()`, `setActionStatus()` (tap-to-grant).
- `GaugeCardView` — card wrapping a `RadialGaugeView` or `BarMeterView` (`app:gaugeType`, `gaugeMin/Max`, optional `gaugeWarn/Hot` bands). Gauge math is in `GaugeMath`.
- `SparklineView`, `CompassView`, `BubbleLevelView`, `SeismographView`, `FigureEightView`, `TouchGridView`, `MultiTouchView`.

Cards sit in platform `GridLayout`s whose column count is `@integer/grid_columns` (2 → 3 at w600dp → 4 at w840dp). Layout styles live in `values/styles_layout.xml` (`Widget.PhoneLab.GridCard`, `WideCard`, `Section`, `ScreenScroll`); type scale in `themes.xml` (`TextAppearance.PhoneLab.*`). Icons are vector drawables in `res/drawable/ic_*.xml` (tinted, no emoji in tab/card titles).

**Sensors tab after the split:** `SensorsFragment` only routes events. `SensorReadouts` turns raw values into `Readout`s (value/unit/detail/caption/gauge), `SoundMeter` owns the `AudioRecord` thread, `GpsTracker` owns location (GPS + NETWORK provider fallback), `RecordingController` talks to `CsvRecordingService`, and `SensorsViewModel` keeps counters / last vectors across rotation.

**ViewModels:** `SensorsViewModel` and `SystemViewModel` (benchmark runs in the ViewModel, so rotation no longer cancels it; CPU-temp history ring buffer).

**Background work:** a single periodic `ThermalCheckWorker` (WorkManager, 15 min — class name kept because WorkManager persists it) runs thermal alerts, `AlertEngine` (user-defined `AlertRule`s with hysteresis in `AlertEvaluator`, stored by `AlertRules`, notified by `AlertNotifier`), `BatteryHistory.record()`, and the CPU-temp widget refresh. `ThermalCheckWorker.reschedule()` is idempotent and enqueues only if something needs it. CSV recording is a `dataSync` foreground service (`CsvRecordingService`, handles the Android 15 `onTimeout`).

Cross-cutting:
- **`AppPrefs`** — static facade over `SharedPreferences`: keys, mode constants and *every unit conversion* (`temp`, `speed`, `altitude`, `pressure`, …). Never format °C/km/h/hPa inline.
- **`ModeAwareFragment`** — subclasses call `bindModeLayouts(textual, visual)`; display-mode and unit changes apply immediately via a prefs listener.
- **`ThermalZones`** — `/sys/class/thermal` reader with `cpu()`, `gpu()`, `skin()` keyword lists shared by the UI, worker, tile, widget and alerts.
- **`BatteryInfo`** — one snapshot of battery data (sticky broadcast + `BatteryManager`), shared by the Hardware tab, worker, alerts and diagnostics.
- **`TipJar`** — per flavor: `src/play/` = Google Play Billing consumable tips (`tip_small/medium/large` must exist in Play Console); `src/foss/` = stub, `isAvailable()` false so About hides the button.

Theme switching: `MainActivity.recreateWithTheme()` writes the pref, sets the night mode and `recreate()`s. Display-mode and unit changes don't recreate.

## Non-obvious things to know

- **`namespace` lives in `app/build.gradle`** (`com.sensolab.devicemonitor`); the manifest has no `package=`.
- **`TYPE_TILT_DETECTOR = 22`** is a hardcoded framework value with no public constant — don't "fix" it.
- **Thermal zones** are matched by keyword against `/sys/class/thermal/thermal_zone*/type`. When a zone shows N/A on a device, add a keyword to the right `ThermalZones` call; don't change the logic.
- **Permissions are contextual**: nothing is requested at launch. Cards that need a permission show "tap to grant" (`registerForActivityResult` launchers in `SensorsFragment`, `SettingsFragment`, `DiagnosticTestActivity`). Heart rate and steps are only *registered* once granted — `registerListener()` throws `SecurityException` otherwise. Keep inline `checkSelfPermission` calls next to `GpsTracker.start()` / `SoundMeter.start()` so lint's MissingPermission check sees them.
- **`OFFSCREEN_PAGE_LIMIT_DEFAULT`** is deliberate (battery): distant tabs unload so their sensors/mic/GPS stop.
- **`FileProvider` authority** is `${applicationId}.fileprovider`; exports go to `getExternalFilesDir(DOCUMENTS)`.
- **Network / privacy:** the app's own code never uses the network. The `foss` build has no `INTERNET` permission at all. **The `play` build does: Play Billing 8 pulls in `INTERNET` + `ACCESS_NETWORK_STATE` via Google's datatransport**, which uploads the Billing Library's diagnostic logs to `firebaselogging.googleapis.com`. `BillingClient` is only created when the user opens the tip jar, so nothing is sent before that — keep it that way or the policy and Data safety answers change. The privacy policy (`drummingbird1.github.io/phonelab-privacy`, separate repo `DrummingBird1/phonelab-privacy` whose `index.html` is a copy of `Assets/PhoneLab_Privacy_Policy.html`) describes the two builds separately; `Assets/PhoneLab_Play_DataSafety.md` holds the matching Play Data safety answers (one data type: Diagnostics). When a dependency, permission, backup rule or the tip jar's start-up changes, re-check both, edit the local copy, then push the same file to the privacy repo.
- **Localization**: `res/values/strings.xml` (English) + full translations in `values-iw/` (Hebrew), `values-es/`, `values-ar/` — keep all four in sync (same key set). `generateLocaleConfig` (+ `res/resources.properties`) exposes them in Android 13+ per-app language settings. Hardcoded English left in code is limited to scientific/technical tokens (`X/Y/Z`, `m/s²`, `Pitch`, `Roll`, `drift`, `bias`, export file contents).
- **CompassView colors** (`compass_*`) are theme-independent on purpose.
- **Themed icon:** the adaptive icon has a `<monochrome>` layer (reuses the foreground).

## Web dashboard (`Web/`)

Vite + TypeScript, no runtime dependencies, PWA. `pnpm run build | test | lint`. Git-linked Vercel project `sensolab-web` deploys `main` automatically (https://sensolab-web.vercel.app); `Web/vercel.json` sets the CSP and security headers.

**i18n:** `src/i18n.ts` exposes `t(english)` / `tf(english, vars)` — gettext-style, keyed by the exact English text — plus dictionaries in `src/i18n/{he,es,ar}.ts`. Card definitions stay English in source and are translated centrally in `render.ts` and the `ui.ts` setters (`setStatus`, `setField`, `addAction`, `setStateViz`, `setToggleState`). When you add or change UI text, add the same English key to all three dictionaries — `src/i18n.test.ts` scans the source for every string reaching `t()`/`tf()` or those setters (plus the card definitions) and fails on a missing, stale or diverging key; numbers/units and the tokens in its `KEEP_ENGLISH` list stay English. Dictionaries are lazy-loaded chunks (`loadDictionary()` runs before `main.ts` renders; the PWA precaches them for offline), so never call `t()` at module top level — it would run before the dictionary arrives and stay English. Hebrew/Arabic set `dir="rtl"`; use logical CSS properties (`inset-inline-*`, `margin-inline-*`, `text-align: end`). Physical instruments (axis bars, dials, level, signal bars) are pinned `direction: ltr`; mixed number/unit/word readouts use `unicode-bidi: plaintext`.

## Versioning

`app/build.gradle` is the source of truth: `versionCode 4`, `versionName "1.3.0"` (current). Releases so far, all as GitHub Releases with an APK: v1.0.0 → v1.1.0 (code 2) → v1.2.0 (code 3) → v1.3.0 (code 4; first GPL / `foss`-flavor release, published as a pre-release until QA'd). Nothing is on Google Play yet. Developer changelogs live in `Assets/dev-docs/`; each release also needs `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` (≤ 500 chars, all four locales) because F-Droid / IzzyOnDroid read them.

**Cutting a release:** bump `versionCode` + `versionName`, add the changelogs, then `.\gradlew assembleFossRelease bundlePlayRelease`; tag `v<x.y.z>` and create a GitHub release with the foss APK attached as `PhoneLab-v<x.y.z>.apk` (that name is what IzzyOnDroid / Obtainium pick up). Google Play: `.\gradlew publishPlayReleaseBundle` (or the manual *Android Release* workflow). If the screenshots change, run `uv run Distribution/derive_screenshots.py`. See `Distribution/stores/README.md` (status table — keep it current) and `Distribution/play/README.md`.

Release builds are not kept in git; local copies go in `Distribution/` (`*.apk` / `*.aab` are git-ignored).

## Signing key

The upload keystore is **`Distribution/Keys/PhoneLab-Key`** — a secret; back it up, losing it blocks updates under this app id. `Distribution/Keys/` is inside the repo working tree but fully git-ignored (`Keys/`, `*.keystore`, `*.jks`, `*.pepk`, `key.properties`, `play-service-account.json`, `*.aab`, `*.apk`). Before signing, verify it matches the Play Console upload key (`keytool -list -v -keystore Distribution\Keys\PhoneLab-Key`, compare SHA-256 with Play Console → App integrity).
