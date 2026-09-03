<div align="center">

<img src="Assets/PhoneLab_icon_512x512.png" width="96" alt="PhoneLab icon">

# PhoneLab

**Every hardware sensor, system stat, and thermal zone on your phone — live, in one screen.**

[![Android CI](https://github.com/DrummingBird1/PhoneLab/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DrummingBird1/PhoneLab/actions/workflows/android-ci.yml)
[![Latest release](https://img.shields.io/github/v/release/DrummingBird1/PhoneLab)](https://github.com/DrummingBird1/PhoneLab/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/DrummingBird1/PhoneLab/total)](https://github.com/DrummingBird1/PhoneLab/releases)

**Read this in:** English · [עברית](README.he.md) · [Español](README.es.md) · [العربية](README.ar.md)

[**⬇ Download the latest APK**](https://github.com/DrummingBird1/PhoneLab/releases/latest) · [Website](https://drummingbird1.github.io/PhoneLab/) · [Privacy Policy](https://drummingbird1.github.io/phonelab-privacy/)

</div>

---

## What is PhoneLab?

PhoneLab is a single-screen Android app for anyone who wants to know exactly what their device's hardware is doing right now — developers testing sensor behavior, tech enthusiasts, gamers watching thermals, or anyone just curious what's inside their phone.

No ads. No account. No background services when you're not using it. The app only ever reads sensors and system files already exposed to any Android app — it never talks to the network. See the [Privacy Policy](https://drummingbird1.github.io/phonelab-privacy/) for the full picture.

## Screenshots

<div align="center">
<img src="Assets/screenshots/PhoneLab_screenshot_1_sensors.png" width="200" alt="Sensors tab">
<img src="Assets/screenshots/PhoneLab_screenshot_2_system.png" width="200" alt="System tab">
<img src="Assets/screenshots/PhoneLab_screenshot_3_hardware.png" width="200" alt="Hardware tab">
<img src="Assets/screenshots/PhoneLab_screenshot_4_about.png" width="200" alt="About tab">
<img src="Assets/screenshots/PhoneLab_screenshot_5_settings.png" width="200" alt="Settings tab">
</div>

## Features

**📡 Sensors tab** — live readings from 24 sensors (accelerometer, gyroscope, magnetometer, gravity, rotation vectors, barometer, light, proximity, humidity, ambient temperature, heart rate, step counter, tilt/motion detectors, and more), plus GPS speed and a live sound-level meter. Sensor availability varies by device — the app clearly labels what your hardware doesn't have.

**⚙️ System tab** — device model, Android version, CPU/RAM/storage, thermal-zone temperatures (CPU/GPU/battery/skin and more) with color-coded alerts, a 4-phase performance benchmark, CSV session recording that keeps running in the background via a foreground service, and spec export to a text file.

**🔧 Hardware tab** — per-core live CPU frequency, detailed battery stats, display capabilities (refresh rate, HDR), and hardware capability flags (NFC, Bluetooth, camera count, biometric enrollment).

**🏠 Home screen widget & Quick Settings tile** — check CPU temperature without opening the app.

**🔔 Thermal alerts** — an optional notification when CPU temperature crosses a threshold, with hysteresis so it won't spam you.

**🎨 Two display modes** — Textual (raw numbers, developer-friendly) or Visual (icons, gauges, progress bars) — switch with one tap.

**🌙 Dark & light themes**, **🌐 4 languages** (English, Hebrew, Spanish, Arabic, with full RTL support), **📐 Metric/Imperial units**.

## PhoneLab Web

A companion browser-based sensor dashboard lives in this repo under [`Web/`](Web/) and runs live at **[sensolab-web-app.vercel.app](https://sensolab-web-app.vercel.app)** — no install needed. It mirrors what the Web Platform exposes on your current browser/device: motion & orientation sensors, GPS, ambient light, an internet speed test, and CSV/PNG export of everything it reads. Everything runs client-side; nothing is sent anywhere.

## Download

Grab the latest signed APK from the **[Releases page](https://github.com/DrummingBird1/PhoneLab/releases)** — every release lists what changed in plain language and ships a ready-to-install APK. Google Play distribution is planned; APKs are the fastest way to get the current build today.

## Building from source

Requirements: **JDK 21**, Android SDK 35, and the Gradle wrapper (bundled — no separate Gradle install needed).

```bash
cd App
./gradlew assembleDebug      # unsigned debug APK → app/build/outputs/apk/debug/
./gradlew test                # JUnit unit tests
```

A release build needs a signing key: copy `App/key.properties.template` to `App/key.properties` and fill in your own keystore details, then run `./gradlew bundleRelease` or `./gradlew assembleRelease`. Without `key.properties`, release builds still compile — just unsigned.

See [CLAUDE.md](CLAUDE.md) for the full architecture walkthrough (fragments, helper classes, permission model, non-obvious quirks) — it's the same orientation doc used for AI-assisted development on this repo, and doubles as living technical documentation.

## Tech stack

Java, classic Android Views + Material Components (no Compose), `ViewPager2` + `TabLayout`, `WorkManager` for background thermal checks, a foreground `Service` for CSV recording, `TileService` for the Quick Settings tile, `AppWidgetProvider` for the home screen widget. The Web dashboard is Vite + TypeScript, no framework.

## Project layout

```
PhoneLab/
├── App/            the Gradle project — open this in Android Studio
├── Web/            the browser dashboard (Vite + TypeScript)
├── Assets/         store listing copy, icons, screenshots, changelogs
├── Distribution/   local build output (git-ignored)
└── Archive/        frozen snapshots of older versions
```

## License

No open-source license is granted at this time — the source is public for transparency, but all rights are reserved. Open an issue if you'd like to discuss reuse.
