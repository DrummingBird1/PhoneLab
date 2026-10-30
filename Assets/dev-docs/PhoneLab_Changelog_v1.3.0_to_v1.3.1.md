# PhoneLab — Changelog v1.3.0 → v1.3.1

`versionCode 4` → `versionCode 5`

v1.3.1 is the first build meant for Google Play: it fixes what the v1.3.0 emulator QA and the
Android 16 / Play policy review turned up. Not yet checked on a real device.

## Fixed

- **Heart rate on Android 16** — for apps targeting API 36, Android 16 gates `Sensor.TYPE_HEART_RATE` behind `android.permission.health.READ_HEART_RATE` instead of `BODY_SENSORS`, so the card could never be granted there. The manifest caps `BODY_SENSORS` at API 35 and adds `READ_HEART_RATE`; `SensorsFragment` asks for the one the running release uses. `PermissionsRationaleActivity` (behind the `ViewPermissionUsageActivity` alias) is the rationale screen Android requires for health permissions; it opens the privacy policy.
- **GPS in background CSV recordings** — `CsvRecordingService` adds the `location` foreground-service type when fine location is granted (`FOREGROUND_SERVICE_LOCATION`), so position rows keep coming while the user is in other apps.
- **No sticky restarts** — the recording service returns `START_NOT_STICKY`, so a process death no longer starts a second, unrequested CSV from the background. The Sensors tab no longer leaves "Stop · mm:ss" on screen after a recording ends from the notification or the Android 15 timeout.
- **Storage sizes** — "420 MB" below 1 GB and one decimal below 10 GB instead of "0 GB free"; "free" in the RAM and Storage cards is translated.
- **Implausible readings hidden** — remaining charge / estimated capacity when they imply a battery under 300 mAh or over 50 000 mAh, and a CPU "max" clock below the current one.
- **Power vs. Level wording** — both cards use the same charging-status label.

## Removed

- **Tip jar (Google Play build)** — the Play Billing tip jar, its About button and strings, and the `playImplementation` Billing dependency are gone. Selling anything on Play needs a payments profile, whose address Google shows on the listing, and usually EU trader status. Without Billing, the `play` build has no `INTERNET` permission and no Google telemetry: its merged manifest requests exactly the same permissions as `foss`, and the Data safety answer is "no data collected or shared". It was never published, so no user loses anything.

## Changed

- Shorter card titles ("Linear Accel", "Game Rotation", "Sound Level"); the qualifier moved to the detail line. Axis values wrap between axes instead of inside a unit.
- Sensors that exist but haven't reported yet show "Waiting…" instead of a dash.
- Continuous sensors refresh their card at most every 66 ms (they arrived at 50–100 Hz), which cuts CPU use on the Sensors tab. Proximity, steps and tilt still see every event.

## Docs

- Privacy policy copy and Play Data safety notes list `FOREGROUND_SERVICE_LOCATION` and `READ_HEART_RATE`, and the health-permission declaration Play reviews. The published policy (`DrummingBird1/phonelab-privacy`) needs the same table before the Play submission.
