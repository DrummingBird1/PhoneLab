# Publishing PhoneLab outside Google Play

Google Play (the `play` flavor) has its own guide: [`../play/README.md`](../play/README.md).

Everything outside Google Play ships the **`foss` flavor**: no proprietary libraries and
no `INTERNET` permission (the `play` build is the same app plus a Rate button). Build it with:

```powershell
cd App
.\gradlew assembleFossRelease   # → app/build/outputs/apk/foss/release/app-foss-release.apk
```

Every GitHub release attaches that APK as `PhoneLab-v<version>.apk`. Store listing text,
icon, feature graphic, screenshots and per-version changelogs live in
[`fastlane/metadata/android/`](../../fastlane/metadata/android/) (en-US, he, es, ar) —
F-Droid and IzzyOnDroid read them straight from the repo; for other stores, copy from there.

**Upload-key certificate (SHA-256):**
`0eaee64d2185b10f2d3b902836fcdd070239041ba0ae3e5dc1f65d885ffbd20f` (CN=Idan)

## Signatures: users can't switch sources freely

| Source | Signed with |
|---|---|
| Google Play | Google's app-signing key (Play App Signing) |
| GitHub Releases, IzzyOnDroid, APKPure, Uptodown, Aptoide, … | the upload key above |
| F-Droid main repo | F-Droid's own key (unless reproducible builds are set up) |

Android refuses to update an app signed with a different key, so moving between these
groups means uninstalling first (local data is lost). Say so in store descriptions where
it matters.

## Status

Last updated 2026-10-17. Update this table whenever a submission moves.

| Store | Who | Status |
|---|---|---|
| GitHub Releases | you (tag + release) | ✅ v1.3.2 is the Latest release (2026-10-31, `PhoneLab-v1.3.2.apk`); v1.3.0 stays a pre-release |
| Obtainium | nothing to submit | ✅ users add `https://github.com/DrummingBird1/PhoneLab` |
| Google Play | you (Play Console) | ✅ production: v1.3.2 (code 6) since 2026-10-31, no tip jar and no `INTERNET` (v1.3.1, code 5, was live 2026-10-30 with the tip jar). Listing text + foreground-service declaration sent for review 2026-10-31 ([guide](../play/README.md), [answers](../play/console-answers.md)) |
| IzzyOnDroid | you (GitLab account) | ⏸ wait until a v1.3.x GitHub release is no longer a pre-release, then request inclusion |
| F-Droid | you (GitLab account) | ⬜ recipe ready in [`../fdroid/`](../fdroid/); merge request to fdroiddata not opened |
| APKPure | you (developer account) | ⬜ |
| Uptodown | you (developer account) | ⬜ |
| Aptoide | you (developer account) | ⬜ |
| Samsung Galaxy Store | you (seller account) | ⬜ optional |
| Huawei AppGallery | you (verified developer) | ⬜ optional |
| Amazon Appstore | you (developer account) | ⬜ optional — Fire tablets only now |

## IzzyOnDroid (fastest F-Droid-compatible route)

Usually live within days; F-Droid clients (F-Droid, Droid-ify, Neo Store) can add the repo.
It picks the APK from GitHub releases, so new versions show up automatically. Submit only
once the first GPL `foss` release (v1.3.x) is a regular GitHub release, not a pre-release.

1. Open an issue at <https://gitlab.com/IzzyOnDroid/repo/-/issues> using the
   *"App inclusion request"* template.
2. Repo: `https://github.com/DrummingBird1/PhoneLab` · package `com.sensolab.devicemonitor` ·
   license GPL-3.0 · APK asset `PhoneLab-v*.apk` (the foss build) · metadata in `fastlane/`.
3. Mention the signing-cert SHA-256 above.
4. After it's live, add the IzzyOnDroid badge to the README.

## F-Droid (main repository)

Slower (reviews take weeks) and F-Droid builds the APK itself from the tag.

1. Fork <https://gitlab.com/fdroid/fdroiddata>.
2. Copy [`../fdroid/com.sensolab.devicemonitor.yml`](../fdroid/com.sensolab.devicemonitor.yml)
   to `metadata/com.sensolab.devicemonitor.yml` in the fork.
3. Optional local check (Linux/WSL, with `fdroidserver` installed):
   `fdroid readmeta && fdroid lint com.sensolab.devicemonitor && fdroid build -v -l com.sensolab.devicemonitor`
4. Commit, push and open a merge request with the *"App inclusion"* template. The fdroiddata
   CI builds it; reply to reviewer comments there.
5. Later releases: just push a new `v<x.y.z>` tag with a bumped `versionCode` — the recipe's
   `UpdateCheckMode: Tags` + `AutoUpdateMode: Version` picks it up. Add
   `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` for each release.

Recipe notes: it builds `App/app` with the `foss` flavor; the `prebuild` line deletes the
`playImplementation` Billing dependency so F-Droid's scanner never sees a non-free library.

## APKPure / Uptodown / Aptoide

All three accept a plain APK through a free developer console:

- APKPure — <https://developer.apkpure.com>
- Uptodown — <https://developer.uptodown.com>
- Aptoide — <https://connect.aptoide.com>

Upload `PhoneLab-v<version>.apk` from the GitHub release, then paste the title, short and
full description from `fastlane/metadata/android/<locale>/`, the 512×512 icon, the
1024×500 feature graphic and the screenshots from `en-US/images/`. Category: *Tools*.
Some of them ask you to prove you own the package name (usually by uploading an APK signed
with the same key, or a signed file) — the upload key above covers that.

## Samsung Galaxy Store / Huawei AppGallery / Amazon Appstore (optional)

- Samsung — <https://seller.samsungapps.com> (seller account; reviews take ~1 week).
- Huawei — <https://developer.huawei.com/consumer/en/appgallery> (identity verification).
- Amazon — <https://developer.amazon.com/apps-and-games> — since Aug 2025 the Amazon
  Appstore only serves Fire devices, so the audience is Fire tablets.

Each needs its own account; upload the same foss APK and the fastlane listing text.
