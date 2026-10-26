<div align="center">

<img src="Assets/PhoneLab_icon_512x512.png" width="96" alt="PhoneLab icon">

# PhoneLab

**Every hardware sensor, system stat, and thermal zone on your phone — live, in one screen.**

[![Android CI](https://github.com/DrummingBird1/PhoneLab/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DrummingBird1/PhoneLab/actions/workflows/android-ci.yml)
[![Latest release](https://img.shields.io/github/v/release/DrummingBird1/PhoneLab)](https://github.com/DrummingBird1/PhoneLab/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/DrummingBird1/PhoneLab/total)](https://github.com/DrummingBird1/PhoneLab/releases)

**Read this in:** English · [עברית](README.he.md) · [Español](README.es.md) · [العربية](README.ar.md)

**Roadmap:** see [ROADMAP.md](ROADMAP.md)

[**⬇ Download the latest APK**](https://github.com/DrummingBird1/PhoneLab/releases/latest) · [Website](https://drummingbird1.github.io/PhoneLab/) · [Privacy Policy](https://drummingbird1.github.io/phonelab-privacy/)

</div>

---

## What is PhoneLab?

PhoneLab is a single-screen Android app for anyone who wants to know exactly what their device's hardware is doing right now — developers testing sensor behavior, tech enthusiasts, gamers watching thermals, or anyone just curious what's inside their phone.

No ads. No account. No background services when you're not using it. The app only ever reads sensors and system files already exposed to any Android app — the GitHub / F-Droid build has no internet permission at all (the Google Play build adds Google Play Billing for the optional tip jar: Google processes tips and receives the billing library's diagnostic logs, while PhoneLab's own code still never uses the network). See the [Privacy Policy](https://drummingbird1.github.io/phonelab-privacy/) for the full picture.

## Screenshots

<div align="center">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_sensors.png" width="180" alt="Sensors tab (visual mode)">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_hardware.png" width="180" alt="Hardware tab">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_device_check.png" width="180" alt="Device check">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_tools.png" width="180" alt="Tools tab">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_system.png" width="180" alt="System tab (visual mode)">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6_settings.png" width="180" alt="Settings tab">
</div>

## Features

**📡 Sensors tab** — live readings from 24 sensors (accelerometer, gyroscope, magnetometer, gravity, rotation vectors, barometer, light, proximity, humidity, ambient temperature, heart rate, step counter, tilt/motion detectors, and more), plus GPS speed and a live sound-level meter. Sensor availability varies by device — the app clearly labels what your hardware doesn't have.

**⚙️ System tab** — device model, Android version, CPU/RAM/storage, thermal-zone temperatures (CPU/GPU/battery/skin and more) with color-coded alerts, a 4-phase performance benchmark, CSV session recording that keeps running in the background via a foreground service, and spec export to a text file.

**🔧 Hardware tab** — per-core live CPU frequency, detailed battery stats, display capabilities (refresh rate, HDR), and hardware capability flags (NFC, Bluetooth, camera count, biometric enrollment).

**🧰 Tools tab** — bubble level, compass calibration, a magnetometer metal detector, and a vibration seismograph with frequency analysis.

**✅ Device check** — 11 guided hardware tests (touch screen, multi-touch, display, speaker, microphone, vibration, buttons, proximity, flashlight, motion sensors, battery) with a shareable pass/fail report — handy when buying or selling a used phone.

**🔋 Battery health** — health status, charging power in watts, estimated capacity, charge cycles and a 24-hour level history.

**🏠 Home screen widget & Quick Settings tile** — check CPU temperature without opening the app.

**🔔 Alerts** — custom rules for any reading ("notify me when X goes above Y") plus CPU-temperature alerts, all with hysteresis so they won't spam you.

**🎨 Two display modes** — Textual (raw numbers, developer-friendly) or Visual (icons, gauges, progress bars) — switch with one tap.

**✨ Material 3** with optional Material You colors, **🌙 dark & light themes**, **🌐 4 languages** (English, Hebrew, Spanish, Arabic, with full RTL support), **📐 Metric/Imperial units**.

## PhoneLab Web

A companion browser-based sensor dashboard lives in this repo under [`Web/`](Web/) and runs live at **[sensolab-web.vercel.app](https://sensolab-web.vercel.app)** — no install needed. It mirrors what the Web Platform exposes on your current browser/device: motion & orientation sensors, GPS, ambient light, an internet speed test, and CSV/PNG export of everything it reads. Everything runs client-side; nothing is sent anywhere.

## Download

- **APK:** grab `PhoneLab-v<version>.apk` from the **[Releases page](https://github.com/DrummingBird1/PhoneLab/releases)** — every release lists what changed in plain language.
- **Obtainium:** add `https://github.com/DrummingBird1/PhoneLab` to get updates straight from GitHub releases.
- **F-Droid / IzzyOnDroid:** submissions in progress.
- **Google Play:** coming soon.

The GitHub / F-Droid builds (the `foss` flavor) contain no proprietary libraries and have no internet permission. The Google Play build is signed by Google, so switching between Play and an APK install requires uninstalling first.

## Building from source

Requirements: **JDK 21**, Android SDK 36, and the Gradle wrapper (bundled — no separate Gradle install needed).

The app has two flavors: `foss` (GitHub, F-Droid and other stores — no proprietary libraries) and `play` (Google Play, adds the Play Billing tip jar).

```bash
cd App
./gradlew assembleFossDebug    # debug APK → app/build/outputs/apk/foss/debug/
./gradlew assembleFossRelease  # release APK → app/build/outputs/apk/foss/release/
./gradlew test                 # JUnit unit tests
```

A signed release build needs a signing key: copy `App/key.properties.template` to `App/key.properties` and fill in your own keystore details. Without `key.properties`, release builds still compile — just unsigned.

See [CLAUDE.md](CLAUDE.md) for the full architecture walkthrough (fragments, helper classes, permission model, non-obvious quirks) — it's the same orientation doc used for AI-assisted development on this repo, and doubles as living technical documentation.

## Tech stack

Java, classic Android Views + Material Components (no Compose), `ViewPager2` + `TabLayout`, `WorkManager` for background thermal checks, a foreground `Service` for CSV recording, `TileService` for the Quick Settings tile, `AppWidgetProvider` for the home screen widget. The Web dashboard is Vite + TypeScript, no framework.

## Project layout

```
PhoneLab/
├── App/            the Gradle project — open this in Android Studio
├── Web/            the browser dashboard (Vite + TypeScript)
├── Assets/         store listing copy, icons, screenshots, changelogs
├── fastlane/       store listing text, icon and screenshots (F-Droid / IzzyOnDroid)
├── Distribution/   F-Droid recipe and store guide (builds and keys are git-ignored)
└── Archive/        frozen snapshots of older versions
```

## License

PhoneLab is free software, licensed under the **[GNU General Public License v3.0](LICENSE)** (GPL-3.0-only). You may use, study, share and modify it; distributed modified versions must stay under the same license with their source available.
