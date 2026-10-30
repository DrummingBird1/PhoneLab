# PhoneLab: Google Play Data safety answers

Answers for **Play Console → App content → Data safety**, for the **`play` flavor** only (the `foss` build never goes to Google Play). They match the published privacy policy (https://drummingbird1.github.io/phonelab-privacy/, effective October 30, 2026). If one changes, update the other.

Prepared 2026-10-30 against `versionCode 5` / `versionName 1.3.1`, after the tip jar was removed: the `play` build has no Play Billing, no in-app purchases and no proprietary libraries. Its merged release manifest requests exactly the same permissions as `foss`, with **no `INTERNET` permission**.

---

## What the play build sends off the device

These are the facts the answers rest on. They were checked against the merged release manifests (`App/app/build/intermediates/merged_manifest/{play,foss}Release/`, identical `<uses-permission>` lists) and the source.

| Source | Leaves the device? | Notes |
|---|---|---|
| PhoneLab's own code and every library in it | **No** | No `INTERNET` permission, so Android blocks every socket. The libraries are AndroidX / Material Components only. |
| Sensors, location, microphone, heart rate, steps, system info | **No** | Read and processed on-device only. |
| CSV recordings, spec exports, Device check report | **Only if the user shares them** | Saved in app-specific storage; sent only through the Android share sheet, which the user starts. |
| Settings, alert rules, battery history (`sensolab_prefs.xml`) | Android Auto Backup only | Included in the user's own Android backup if they enabled it. That backup is end-to-end encrypted on Android 9+, run by Android, and the developer can't read it. |

---

## Form answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No.** |
| Which of the following methods of account creation does your app support? (if asked) | **My app does not allow users to create an account.** |

Answering **No** ends the data-type part of the form. The store listing then shows **"No data collected"** and **"No data shared with third parties"**.

Why "No" holds:
1. **On-device access isn't collection.** Location, audio levels, heart rate and steps are read and shown on the device; Google's definition of "collected" is data transmitted off the device.
2. **User-initiated sharing isn't collection.** A CSV or spec export leaves the device only when the user picks a target in the Android share sheet.
3. **Android backup is Android's.** The app doesn't send it; Android does, into the user's own account, encrypted.

If PhoneLab ever adds a library or feature that needs `INTERNET` (crash reporting, analytics, ads, billing, a web request), this answer changes and the policy has to say what is sent.

---

## Related App content entries (same Play Console page group)

- **Privacy policy**: `https://drummingbird1.github.io/phonelab-privacy/`
- **Ads**: *No, my app does not contain ads.*
- **In-app purchases**: none. No payments profile is needed for this app.
- **Foreground service permissions** (required for `targetSdk 34+`): declare both types for `CsvRecordingService` (user-started sensor recording, visible ongoing notification, stopped by the user). Play asks for a short video of each.
  - `FOREGROUND_SERVICE_DATA_SYNC`: writes the sensor readings to a local CSV file while the user is in other apps.
  - `FOREGROUND_SERVICE_LOCATION`: added to the same service only when fine location is granted, so GPS position rows keep being written to that CSV while the user is in other apps. Android 10+ stops delivering while-in-use location to a backgrounded app unless it runs a `location` foreground service. Video: grant location, start a recording on the Sensors tab, switch to another app with the notification visible, come back and stop.
- **Location**: no `ACCESS_BACKGROUND_LOCATION`, so no background-location declaration is needed. A `location` foreground service started from a visible activity counts as while-in-use (foreground) access.
- **Health permissions** (Play reviews every body-sensor and health-permission request): declare `BODY_SENSORS` (Android 15 and older) and `android.permission.health.READ_HEART_RATE` (Android 16+, which replaced it for apps targeting API 36). Use case: real-time heart-rate readout from the device's own sensor on the Sensors tab, shown on screen only, never stored, recorded or transmitted. No Health Connect data is read or written. The permission-rationale screen Android asks for opens the privacy policy.
- **Health apps declaration**: PhoneLab isn't a health app; it shows heart rate and step readings from on-device sensors and doesn't store or transmit them.
- **EU trader status (Digital Services Act)**: the app is free with no ads and no in-app purchases, which is what a *non-trader* declaration requires. A trader's name, address, phone and email are shown to EU users; a non-trader's aren't. The declaration is the developer's own legal statement, so the account owner decides.

## Keeping this in sync

Re-check this file and the policy whenever any of these change:
- any new `implementation` / `playImplementation` dependency. Re-run `.\gradlew processPlayReleaseMainManifest processFossReleaseMainManifest` and diff the `<uses-permission>` lists: they should be identical, with no `INTERNET`
- `data_extraction_rules.xml` / `backup_rules.xml`
- a foreground service type in `AndroidManifest.xml` (Play needs a declaration per type)
- a body-sensor or `android.permission.health.*` permission (Play reviews each one)
- any code that writes files, reads identifiers or uses the network
