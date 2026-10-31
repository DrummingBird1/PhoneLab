# PhoneLab — Changelog v1.3.1 → v1.3.2

`versionCode 5` → `versionCode 6`

v1.3.1 went to Google Play production on 2026-10-30 with the Play Billing tip jar still in the
`play` build. v1.3.2 is the same app without it, so the Play build matches the published privacy
policy (no internet permission, no purchases). The `foss` build gains nothing new over v1.3.1;
its users get the v1.3.1 fixes, so the fastlane changelog repeats them.

## Removed

- **Tip jar (Google Play build)** — the Play Billing tip jar, its About button and strings, and the `playImplementation` Billing dependency are gone. Selling anything on Play needs a payments profile, whose address Google shows on the listing, and usually EU trader status. Without Billing, the `play` build has no `INTERNET` permission and no Google telemetry: its merged manifest requests exactly the same permissions as `foss`, and the Data safety answer is "no data collected or shared".

## Before the Play upload

Play refuses updates until the two overdue App content declarations are submitted:
foreground-service permissions and health apps. Ready answers: `Distribution/play/console-answers.md`.
