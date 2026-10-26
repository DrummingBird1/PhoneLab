# PhoneLab roadmap

> עברית: מפת דרכים בקצרה: מה יצא, מה בעבודה, מה מתוכנן ורעיונות. אין התחייבות לתאריכים.

This is a plain overview of where PhoneLab stands. It lists what has shipped, what is in progress, what is planned and what is only an idea. It makes no promises about dates, and items can change or be dropped.

## Shipped

- **v1.0.0 to v1.2.0**: the original sensor dashboard, system and thermal info, CPU benchmark, light and dark themes, unit conversion, spec export and CSV recording. See [`Assets/dev-docs`](Assets/dev-docs) for the changelogs.
- **v1.3.0** (published as a pre-release): the first GPL-3.0 release, with a `foss` build that has no `INTERNET` permission.
- **Material 3 redesign**: Material 3 theme with optional dynamic color, a shared card component, a three-level type scale, real gauges (radial and bar) in visual mode, vector icons instead of emoji, and scrollable tabs that fit narrow screens.
- **New tabs and features**: Hardware (battery health, charging power, estimated capacity, cycles, 24 hour history), Tools (bubble level, compass calibration, metal detector, vibration analyzer), Device check (11 guided hardware tests with a shareable report), custom sensor alerts, and an optional tip jar in the Google Play build only.
- **Platform work**: target SDK 36, edge-to-edge layout, themed icon, per-app language, release builds with R8, Gradle Play Publisher setup, and CI with lint.
- **Languages**: English, Hebrew, Spanish and Arabic in the app and in the web dashboard.
- **SensoLab Web**: a browser dashboard (PWA) with the same four languages and right-to-left layout.

## In progress

- Real-device testing of everything above: all tabs in light, dark and Material You themes, edge-to-edge on Android 15 and later, each Device check test, background alerts, right-to-left layouts, and release builds with R8.
- Preparing the app for more distribution channels, including Google Play, F-Droid and IzzyOnDroid. Store status is tracked in [`Distribution/stores/README.md`](Distribution/stores/README.md).

## Planned

- Promote v1.3.0 from pre-release to a regular release once testing is done.
- Fix whatever real-device testing turns up.
- Keep translations and the privacy policy in step with each release.

## Ideas

These are not commitments.

- More sensors and more thermal zones as devices expose them.
- Further Device check tests.
- More tools on the Tools tab.
- Upgrades that need a migration, such as newer Android Gradle Plugin and Gradle Play Publisher versions.

## Suggest something

Open an [issue](https://github.com/DrummingBird1/PhoneLab/issues) to suggest a feature or report a bug.
