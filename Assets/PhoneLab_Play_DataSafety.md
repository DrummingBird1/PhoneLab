# PhoneLab: Google Play Data safety answers

Answers for **Play Console → App content → Data safety**, for the **`play` flavor** only (the `foss` build never goes to Google Play). They match the published privacy policy (https://drummingbird1.github.io/phonelab-privacy/, effective October 17, 2026). If one changes, update the other.

Prepared 2026-10-17 against `versionCode 4` / `versionName 1.3.0`, Play Billing Library **8.3.0**.

---

## What the play build sends off the device, and why

These are the facts the answers rest on. They were checked against the merged release manifest (`App/app/build/intermediates/merged_manifest/playRelease/`) and the source.

| Source | Leaves the device? | Notes |
|---|---|---|
| PhoneLab's own code (`src/main`, `src/play`) | **No** | No network calls anywhere in the app's code. |
| Sensors, location, microphone, heart rate, steps, system info | **No** | Read and processed on-device only. |
| CSV recordings, spec exports, Device check report | **Only if the user shares them** | Saved in app-specific storage; sent only through the Android share sheet, which the user starts. |
| Settings, alert rules, battery history (`sensolab_prefs.xml`) | Android Auto Backup only | Included in the user's own Android backup if they enabled it. That backup is end-to-end encrypted on Android 9+ and the developer can't read it. |
| **Google Play Billing Library 8.3.0** (`TipJar`) | **Yes, when the tip jar is opened** | Talks to the Play Store app to get prices, launch the purchase and consume the tip. Payment details go directly to Google. |
| **`com.google.android.datatransport` (transport-backend-cct 3.1.8)** | **Yes, after the tip jar is opened** | Billing Library logging (added in PBL 6: "API usage (such as success and failure) and service connection issues", per the PBL release notes). Uploads go to `https://firebaselogging.googleapis.com/…` (HTTPS, checked in `CCTDestination`). This dependency is where `INTERNET` and `ACCESS_NETWORK_STATE` come from. |

`BillingClient` is created only in `TipJar`'s constructor, which runs only when the user taps **Support development (tip)** (`AboutFragment`). No datatransport `ContentProvider` auto-initializes, so a user who never opens the tip jar generates no network traffic from the app.

---

## Form answers

### Section 1: Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes**, for the Billing Library diagnostics only (see Section 2). |
| Is all of the user data collected by your app encrypted in transit? | **Yes.** Billing traffic goes through the Play Store app over TLS, and datatransport uploads over HTTPS. |
| Which of the following methods of account creation does your app support? | **My app does not allow users to create an account.** |
| Do you provide a way for users to request that their data is deleted? | **No.** The developer receives and stores no user data, so there's nothing to delete on the developer's side. The diagnostics are held by Google. On-device data is deleted by uninstalling or clearing storage (the policy says this). See judgment call 3. |
| Has your app been independently validated against a global security standard (MASA)? | **No.** |

### Section 2: Data types

Declare **exactly one** data type:

**App info and performance → Diagnostics**

| Question | Answer |
|---|---|
| Is this data collected, shared, or both? | **Collected** (not shared; see judgment call 1) |
| Is this data processed ephemerally? | **No** |
| Is this data required for your app, or can users choose whether it's collected? | **Users can choose whether this data is collected.** It is only produced if the user opens the optional tip jar. |
| Why is this user data collected? | **Analytics.** Google uses it to monitor and improve the Play Billing Library and to diagnose billing errors. |

Leave every other data type **unselected**:

| Data type | Why it's not declared |
|---|---|
| Location (approximate, precise) | Accessed on-device only (GPS speed/altitude; lat/long only inside a CSV the user creates). On-device access isn't "collection". Exports leave only through user-initiated sharing. |
| Audio (voice or sound recordings, other audio) | The microphone is analyzed in memory for loudness; nothing is recorded or transmitted. |
| Health and fitness (health info, fitness info) | Heart rate and steps are shown on screen only and never stored or transmitted. |
| Financial info (purchase history, payment info, …) | The **payment-service exception**: you don't need to declare data that a payment service such as Google Play's billing system collects directly from the user, if your app never accesses it. The app only handles the purchase token on-device to consume the tip. It stores nothing and sends nothing to a developer back end, because there isn't one. Play Console order records come from Google under the Developer Distribution Agreement, not from the app. See judgment call 2. |
| Device or other IDs | The app reads no identifiers (no Android ID, advertising ID, IMEI or serial). The spec export names the device model, but it never leaves the device unless the user shares it. |
| App activity, App info: crash logs, Files and docs, Personal info, Messages, Photos and videos, Contacts, Calendar, Web browsing | None of these are collected. There is no crash-reporting or analytics SDK. |

### Preview: what the store listing should then show

- **No data shared with third parties**
- **Data collected**: App info and performance (Diagnostics), optional
- **Data is encrypted in transit**
- Data can't be deleted (follows from the deletion answer)

---

## Judgment calls

1. **Diagnostics are "collected", not "shared".** Data that an SDK sends off the device counts as collection by the app. That's why Diagnostics is declared at all instead of "No data collected". Transfers to a *service provider* that processes data on the developer's behalf aren't "sharing", and Google acts as the developer's payment processor here. The billing logs serve that service ("improve the performance of the Play Billing Library and provide better support for errors"). If a Play reviewer disagrees, the fallback is to tick **Shared** as well. That changes the listing but not the policy, which already says Google receives the logs.
2. **No "Purchase history".** This relies on the payment-service exception above. If PhoneLab ever adds its own server-side receipt validation, a purchase log or an analytics SDK that sees purchases, **Financial info → Purchase history** must be declared and the policy updated.
3. **Deletion = No.** The honest answer, since the developer holds nothing. "Yes" would promise a request mechanism the developer can't fulfil for Google-held logs. The policy points users to their Google Account controls instead.
4. **"Optional" collection.** This is valid because the tip jar is optional and `BillingClient` isn't created until it's opened. If the Billing Library is ever started at app launch (for example, to restore purchases), change this to **Required** and update the policy's "only starts once you open the tip jar" sentence.

---

## Related App content entries (same Play Console page group)

- **Privacy policy**: `https://drummingbird1.github.io/phonelab-privacy/`
- **Ads**: *No, my app does not contain ads.*
- **Foreground service permissions** (required for `targetSdk 34+`): declare `FOREGROUND_SERVICE_DATA_SYNC` for `CsvRecordingService` (user-started sensor recording, visible ongoing notification, stopped by the user). Play asks for a short video of the feature.
- **Location**: no `ACCESS_BACKGROUND_LOCATION`, so no background-location declaration is needed.
- **Health**: if Play Console asks about health features, the answer is that PhoneLab shows heart rate and step readings from on-device sensors and doesn't store or transmit them.

## Keeping this in sync

Re-check this file and the policy whenever any of these change:
- a new `playImplementation` / `implementation` dependency, or a Billing Library major version. Re-run `.\gradlew processPlayReleaseManifest processFossReleaseManifest` and diff the `<uses-permission>` lists
- `TipJar` starting earlier than the tip-button tap
- `data_extraction_rules.xml` / `backup_rules.xml`
- any code that writes files, reads identifiers or uses the network
