# Publishing PhoneLab on Google Play

Google Play gets the **`play` flavor** (Play Billing tip jar). Uploads go through
[Gradle Play Publisher](https://github.com/Triple-T/gradle-play-publisher) (GPP): always to
the **internal** testing track, always as a **DRAFT** that you roll out or promote in Play
Console. Store-listing answers that GPP can't upload are in
[`Assets/PhoneLab_PlayStore_Description.md`](../../Assets/PhoneLab_PlayStore_Description.md).

| What | Where |
|---|---|
| Listing text (en-US, iw-IL, es-ES, es-419, ar) | `App/app/src/play/play/listings/<locale>/` |
| Icon, feature graphic, screenshots | `App/app/src/play/play/listings/en-US/graphics/` (from `Distribution/derive_screenshots.py`) |
| What's new | `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`, synced by `syncPlayReleaseNotes` |
| GPP config | `play { }` block in `App/app/build.gradle` |
| CI | `.github/workflows/android-release.yml` (manual) |

## 1. Create the app and upload the first bundle by hand

The Play Developer API can't create an app or make its first upload, so do this once in the
browser.

1. Play Console → **Create app**: name `PhoneLab`, default language English (United States),
   App, Free. Accept the declarations.
2. Build the bundle in the main checkout, where `App/key.properties` and the key live:
   ```powershell
   cd App
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
   .\gradlew bundlePlayRelease
   ```
   Output: `app/build/outputs/bundle/playRelease/app-play-release.aab`, plus
   `app/build/outputs/mapping/playRelease/mapping.txt`.
3. **Test and release → Testing → Internal testing → Create new release.** Keep Play App
   Signing with a Google-generated app signing key (the default), upload the AAB, then
   upload `mapping.txt` from the release's app bundle details page. Add yourself to the
   testers list and roll out.
   Play keeps that release's `versionCode` (5 for v1.3.1), so every later upload (CI or
   local) needs a higher one.

## 2. Check the upload key

Play now knows the upload key from that first bundle. In **App integrity → App signing**,
the **Upload key certificate** SHA-256 must be:

```
0E:AE:E6:4D:21:85:B1:0F:2D:3B:90:28:36:FC:DD:07:02:39:04:1B:A0:AE:3E:5D:C1:F6:5D:88:5F:FB:D2:0F
```

That is the certificate in `Distribution/Keys/PhoneLab-Key` (PKCS12, alias `idan`,
`CN=Idan`, valid until 2051), checked on 2026-10-17 with
`keytool -list -v -keystore Distribution\Keys\PhoneLab-Key`; the signed v1.2.0 bundle carries the
same certificate. Run that command yourself to re-check (it asks for the store password).
The **App signing key certificate** on the same page is Google's key. That's why
the Play build and the GitHub/F-Droid APKs can't update each other.

## 3. Service account for GPP

1. [Google Cloud console](https://console.cloud.google.com/) → create a project (e.g.
   `phonelab-publishing`), or reuse one.
2. **APIs & Services → Library → Google Play Android Developer API → Enable.**
3. **IAM & Admin → Service accounts → Create service account** (e.g. `gpp-publisher`).
   It needs no Cloud roles: skip that step and click **Done**.
4. Open the account → **Keys → Add key → Create new key → JSON**. The file downloads once;
   treat it like a password. (If key creation is blocked by an organization policy, the
   project sits in a Cloud organization; a project under a personal Gmail account isn't.)
5. Play Console → **Users and permissions → Invite new users**. Email: the service account's
   address (`gpp-publisher@<project-id>.iam.gserviceaccount.com`). **App permissions → Add
   app → PhoneLab**, then tick:
   - *Release apps to testing tracks*
   - *Manage store presence* (for `publishPlayReleaseListing`)

   No account permissions and no production rights. **Invite user**. New permissions can
   take a while to apply; if GPP says the caller lacks permission, try again later.
6. Local use: save the file as `App/play-service-account.json` in the main checkout
   (git-ignored). GPP's publish tasks only exist when that file or the
   `ANDROID_PUBLISHER_CREDENTIALS` variable is present.

## 4. GitHub Actions secrets

`android-release.yml` needs five repository secrets. Set them from the repo root in the
**main checkout** with the GitHub CLI. Commands without input open a hidden prompt, where you
paste the value yourself:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("Distribution\Keys\PhoneLab-Key")) | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
Get-Content -Raw "<path to the downloaded JSON key>" | gh secret set PLAY_SERVICE_ACCOUNT_JSON
gh secret list
```

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the keystore file, base64-encoded (first command) |
| `KEYSTORE_PASSWORD` | `storePassword` from `App/key.properties` |
| `KEY_ALIAS` | `keyAlias` from `App/key.properties` (`idan`) |
| `KEY_PASSWORD` | `keyPassword` from `App/key.properties` (same as the store password for a PKCS12 keystore) |
| `PLAY_SERVICE_ACCOUNT_JSON` | the whole JSON key from step 3 |

Same thing in the browser: repository **Settings → Secrets and variables → Actions → New
repository secret**. Delete the downloaded JSON once it's stored in both places.

## 5. Publishing a release

1. Bump `versionCode` / `versionName` in `App/app/build.gradle` and add
   `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for all four locales
   (≤ 500 characters). The publish fails if one is missing.
2. Either run **Actions → Android Release (Play internal track) → Run workflow** (tick
   *listing* to upload the store listing too), or locally:
   ```powershell
   cd App
   .\gradlew publishPlayReleaseListing   # title, descriptions, graphics, all languages
   .\gradlew publishPlayReleaseBundle    # signed AAB → internal track (DRAFT) + What's new
   ```
3. Play Console → Internal testing → review the draft → roll out. Promote to closed/open
   testing or production from there. CI keeps the AAB and `mapping.txt` as a workflow
   artifact; Play already gets the mapping file with the bundle.

**Production access:** personal developer accounts created after November 2023 must first
run a closed test with at least 12 opted-in testers for 14 days in a row. Internal testing
doesn't count toward this.

## 6. Tip-jar products (`tip_small`, `tip_medium`, `tip_large`)

`TipJar` (`App/app/src/play/`) queries these three IDs as one-time (`INAPP`) products, shows
each product's **name** with its price, and **consumes** every purchase right after it
completes. Consumable vs. non-consumable is decided by the app, not by Play Console, so a tip
can be bought again.

Before you start: a **payments profile** must be linked to the developer account
(Settings → Payments profile), and a build with Play Billing must have been uploaded to
a track (step 1 does that).

Repeat for each product:

1. **Monetize with Play → Products → One-time products → Create one-time product.**
2. **Product ID**: `tip_small` (then `tip_medium`, `tip_large`). It must match exactly, can't be changed
   later, and can't be reused after deletion.
3. **Name** and **Description** (the name is what the tip dialog lists):

   | ID | Name | Suggested price (USD) |
   |---|---|---|
   | `tip_small` | Small tip | 0.99 |
   | `tip_medium` | Medium tip | 2.99 |
   | `tip_large` | Large tip | 4.99 |

   Description for all three: *A thank-you to the developer. Unlocks nothing — every
   feature stays free.*
4. **Tax, compliance, and programs**: keep the defaults. **Next.**
5. **Purchase option**: ID `buy`, purchase type **Buy**. No rent, pre-order, discount or
   quantity options. The first Buy option is automatically *backwards compatible*, and
   that's the offer `TipJar` reads (`getOneTimePurchaseOfferDetails()`).
6. **Availability and pricing**: keep all regions. **Set prices → Bulk edit pricing →**
   select all regions → **Continue** → enter the USD price → **Apply** (Play converts it per
   country) → **Continue**.
7. **Activate.**
8. Add translations of the name and description in the product's details so the dialog
   matches the app language (otherwise the English name shows):

   | | Hebrew (iw-IL) | Spanish (es-ES, es-419) | Arabic (ar) |
   |---|---|---|---|
   | Small | טיפ קטן | Propina pequeña | إكرامية صغيرة |
   | Medium | טיפ בינוני | Propina mediana | إكرامية متوسطة |
   | Large | טיפ גדול | Propina grande | إكرامية كبيرة |
   | Description | תודה למפתח. לא פותח שום דבר — כל התכונות נשארות חינמיות. | Un agradecimiento al desarrollador. No desbloquea nada: todas las funciones siguen siendo gratis. | شكر للمطوّر. لا يفتح أي ميزة — كل الميزات تبقى مجانية. |

**Testing a tip:** add your Google account under **Settings → License testing**. License
testers get test payment methods and aren't charged. Install the app from the internal-testing
opt-in link with that account and tap *Support development (tip)* on the About tab. Debug
builds (`com.sensolab.devicemonitor.debug`) can't reach the products and say "Tips aren't
available"; the foss build has no tip button at all.
