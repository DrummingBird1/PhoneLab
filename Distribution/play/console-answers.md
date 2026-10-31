# Play Console answers for PhoneLab

Copy-paste answers for the App content declarations that Play asks for, kept with the code so they
stay true. Field names follow the English Play Console; the Hebrew UI shows the same fields.

## Foreground service permissions

*App content → Foreground service permissions.* Required because `CsvRecordingService` declares
`foregroundServiceType="dataSync|location"` (`App/app/src/main/AndroidManifest.xml`).

| Field | Answer |
|---|---|
| Types used | **Data sync** and **Location** |
| Task / use case (data sync) | User-initiated recording of the phone's sensor readings to a local CSV file |
| Task / use case (location) | Adds GPS position rows to that same user-started recording while the user is in other apps |
| Video | One video covers both types: `E:\AI\media\phonelab\play-fgs\PhoneLab_FGS_dataSync_location.mp4` (37 s). Upload it, e.g. unlisted on YouTube, and paste the link for each type |

**Description (data sync)**

> The user taps "Start Recording" on the Sensors tab. PhoneLab then writes the device's sensor readings to a CSV file in the app's own storage until the user taps Stop, in the app or in the ongoing notification. The foreground service keeps the recording running while the user switches to other apps, and the notification stays visible the whole time. Nothing is uploaded; the file leaves the device only if the user shares it.

**Impact if deferred or interrupted (data sync)**

> The recording stops: readings taken while the app is in the background are lost and the CSV file has a gap. The user started the recording explicitly and expects it to continue until they stop it.

**Description (location)**

> The location type is added to the same recording service only when the user has granted precise location. It lets GPS position rows keep being written to the user's CSV recording while they use other apps; Android stops delivering location to a backgrounded app without it. The recording is always started from the visible app, and PhoneLab never requests background location.

**Impact if deferred or interrupted (location)**

> Position rows stop as soon as the user leaves the app, so the recording's GPS track is incomplete. Sensor rows continue.

## Health apps

*App content → Health apps.* Every developer must submit this. PhoneLab is a hardware and sensor
diagnostics tool, not a health app:

- It shows the device's own heart-rate sensor (where present) and step counter live on the Sensors tab, next to ~20 other sensors.
- It doesn't store, record, analyze or transmit health data, set health goals, give health advice, or use Health Connect.

Pick the option closest to *"My app does not have health features"*. If the form insists on a
category for the heart-rate and step readouts, choose the fitness or activity one and describe it
as above. The exact option wording changes; check it against the form, and the account owner
makes the declaration.

Separately, uploading a bundle with `BODY_SENSORS` / `android.permission.health.READ_HEART_RATE`
can bring up a **health permissions** request (Play reviews it). Use case: real-time heart-rate
readout from the device's own sensor, shown on screen only (see `Assets/PhoneLab_Play_DataSafety.md`).

## Before uploading v1.3.2 (versionCode 6)

1. Submit both declarations above. Until then Play refuses any update.
2. Data safety: "No" to collecting or sharing user data (`Assets/PhoneLab_Play_DataSafety.md`).
3. Store listing: the default listing still has the v1.0 "SensoLab" text. Replace it from
   `App/app/src/play/play/listings/<locale>/` (title, short and full description, all five locales) and
   the graphics in `listings/en-US/graphics/`.
4. Production → Create new release → upload `Distribution/PhoneLab-v1.3.2-play.aab` and paste
   `fastlane/metadata/android/<locale>/changelogs/6.txt` as release notes.
5. EU trader status: non-trader (free, no ads, no purchases), declared by the account owner.
