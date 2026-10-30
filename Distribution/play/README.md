# Publishing PhoneLab on Google Play

Google Play gets the **`play` flavor** (the same app plus a Rate button; no in-app purchases). Uploads go through
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

## 6. No in-app purchases, and what Play shows about you

PhoneLab has no in-app purchases and no ads (the Play Billing tip jar was removed in v1.3.1),
so:

- **No payments profile** needs to be linked to the developer account. Play shows the full
  address from the payments profile on the listing of any developer who sells something;
  without one, that doesn't apply.
- **EU trader status:** Play Console asks every developer to declare it under the EU Digital
  Services Act. A free app with no ads and no purchases is what a *non-trader* declaration is
  for. A trader's name, address, phone and email are shown to EU users. The declaration is your
  own legal statement, so you decide.
- **Shown publicly anyway:** the developer name and the store-listing contact email (required).
  Use an address made for the app rather than a personal one. Phone and website are optional.
  Play Console's *Developer profile* page previews everything that can appear on Google Play.

If in-app purchases or ads ever come back, all of this changes: a payments profile (address
shown), most likely trader status (address, phone and email shown in the EU), `INTERNET` in the
`play` build, and a new privacy policy and Data safety answer.
