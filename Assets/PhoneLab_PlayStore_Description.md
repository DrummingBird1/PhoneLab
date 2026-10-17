# PhoneLab — Google Play listing sheet (v1.3.0)

Everything Play Console asks for, in one place. The listing **text and graphics are not
kept here**: Gradle Play Publisher uploads them from
[`App/app/src/play/play/`](../App/app/src/play/play/) (`.\gradlew publishPlayReleaseListing`),
and the "What's new" text comes from `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.
Edit those files, not a copy in this document. Publishing steps:
[`Distribution/play/README.md`](../Distribution/play/README.md).

---

## Store listing (uploaded by GPP)

| Field | Limit | Source |
|---|---|---|
| App name | 30 | `listings/<locale>/title.txt` — `PhoneLab` |
| Short description | 80 | `listings/<locale>/short-description.txt` |
| Full description | 4000 | `listings/<locale>/full-description.txt` |
| App icon | 512×512 PNG | `listings/en-US/graphics/icon/` |
| Feature graphic | 1024×500, no alpha | `listings/en-US/graphics/feature-graphic/` |
| Phone screenshots | 2–8, ≤ 2:1, no alpha | `listings/en-US/graphics/phone-screenshots/` (6) |
| What's new | 500 | fastlane `changelogs/<versionCode>.txt` (synced at publish time) |

Languages: `en-US` (default), `iw-IL` (Hebrew), `es-ES` and `es-419` (same Spanish text), `ar`.
Graphics exist only in en-US; Play shows the default-language graphics for the others.

**Short description (en-US):** Live sensors, temperatures, battery health, tools and a full device check

**Why Play has its own full description:** the fastlane text (F-Droid, IzzyOnDroid, GitHub)
says the app has no internet permission, which is true only for the `foss` build. The Play
text instead says the app's own code never goes online, that Google Play Billing is the only
network access, and that tips are optional and unlock nothing. Everything above the Privacy
section is the same as the fastlane text, with `<ul>`/`<li>` turned into `•` lines
(Play reliably renders only `<b>`-style formatting).

**Screenshots:** derived from the fastlane ones by
[`Distribution/derive_screenshots.py`](../Distribution/derive_screenshots.py) — the 20:9
captures are padded to 9:16 with the app background and their alpha channel removed, because
Play rejects screenshots longer than 2:1 or with transparency. Replace the fastlane
screenshots, then run `uv run Distribution/derive_screenshots.py`.

**Feature highlights for v1.3.0** (for the listing text, promo copy, or a release post):
- Sensors tab — live readings from up to 24 sensors, GPS speed/altitude, sound level; textual cards or gauges; CSV recording in the background
- System tab — 8 thermal zones with a CPU temperature trend, specs incl. GPU, text/JSON export, 4-phase benchmark
- Hardware tab — battery health (power in W, estimated capacity, cycles, 24 h history), per-core CPU frequency, display, capabilities
- Tools tab — bubble level, compass calibration, metal detector, vibration seismograph with FFT
- Device check — 11 guided hardware tests with a shareable pass/fail report
- Custom alerts for any reading, CPU temperature alerts, home-screen widget, Quick Settings tile
- Material 3, Material You colors, dark/light themes, English/Hebrew/Spanish/Arabic, metric/imperial

---

## Play Console fields GPP does not fill

Set these by hand in Play Console.

**App details** (Grow users → Store presence → Store settings)
- App or game: **App** · Category: **Tools**
- Tags (up to 5, chosen from Play's list): pick the closest matches to *device info*, *system monitor*, *sensors*, *battery*, *benchmark*.
- Contact email: **required and shown publicly** — choose which address to publish.
- Website: `https://drummingbird1.github.io/PhoneLab/`

**App content** (Policy → App content)
- Privacy policy: `https://drummingbird1.github.io/phonelab-privacy/`
  ⚠️ The published policy still says the app has no `INTERNET` permission. That is false for
  the Play build (Play Billing adds `INTERNET` and Google's datatransport telemetry), so
  update it to describe the two builds separately **before** submitting.
- Ads: **No, my app does not contain ads.**
- App access: **All functionality is available without special access.**
- Target audience: **13+** or older, and "not designed to appeal to children" (picking an
  under-13 age group brings in the Families policy).
- Content rating (IARC questionnaire): category *Utility, Productivity, Communication, or
  Other*; no violence, sexuality, language, controlled substances, gambling or user
  interaction. It does let users buy digital goods (tips). Expected: Everyone / PEGI 3 /
  USK 0, with an "In-app purchases" notice.
- Health apps declaration (if asked): the app shows heart-rate and step readings from the
  device's own sensors but is not a health or medical app.
- Data safety — facts to answer from:
  - PhoneLab's own code sends nothing off the device. Sensor, location, microphone and
    battery readings are processed on the device only, and exports go only where the user
    shares them. Google's form defines "collection" as sending data off the device, so on-device
    use alone isn't collection.
  - The Play build also contains the Google Play Billing Library, which talks to Google Play
    and brings in Google's datatransport telemetry. Declare what Google's own guidance for
    that SDK lists (Play SDK Index / Billing docs). PhoneLab has no server and keeps no
    purchase records.
  - Encryption in transit: Billing traffic is HTTPS. Account creation: none. Data deletion
    requests: there's no account and no server-side data.
- Permissions declarations (if Play asks): location (GPS speed/altitude on the Sensors tab,
  foreground only), `BODY_SENSORS` (heart rate), `ACTIVITY_RECOGNITION` (step counter),
  `RECORD_AUDIO` (sound meter and microphone test), foreground service `dataSync` (CSV
  recording the user starts and stops).

**Monetization**
- Price: **Free**. In-app products: `tip_small`, `tip_medium`, `tip_large` (consumable
  tips; setup in `Distribution/play/README.md`).

---

## ASO notes

- Keep the title as the brand (`PhoneLab`); Play penalises keyword-stuffed titles.
- Keywords the descriptions already cover naturally: sensor, hardware monitor, device info,
  CPU temperature, battery health, benchmark, compass, bubble level, metal detector,
  phone test, used phone check.
- Don't mention other stores (F-Droid, GitHub APK) in the Play text.
