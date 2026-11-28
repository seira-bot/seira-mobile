# Seira Android companion

The Android counterpart to the iOS companion app: pair a phone with a computer
running Seira, then read and answer from the phone.

- `applicationId` — `bot.seira.companion`
- `minSdk` 26 (Android 8.0), `targetSdk` / `compileSdk` 37
- Deep-link scheme — `openmausbot`
- Two modules: `:core` (protocol, ported from `ios/Sources/CompanionCore`) and
  `:app` (Compose UI, Android platform)

## Build and test

Requires **JDK 17** and an Android SDK. Gradle arrives through the wrapper, so
there is nothing to install for it.

```sh
cd android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

CI runs the same three tasks (`.github/workflows/android.yml`).
Gradle caches aggressively — a suspiciously fast `BUILD SUCCESSFUL` usually means nothing ran.
Prefix `cleanTest` when a test count matters:

```sh
./gradlew cleanTest :core:test :app:testDebugUnitTest :app:assembleDebug
```

## Languages

The language list comes from the folders under `app/src/main/res`: the build writes
the per-app language list, so there is no list to edit. English strings live in
`res/values`; add or change one there, then update the other languages.

```sh
node scripts/android-locale.mjs export --out <dir>      # <dir>/en.json, the text to translate
node scripts/android-locale.mjs import --lang de --in de.json
node scripts/android-locale.mjs check                    # missing, extra or stale keys
```

`android/l10n/glossary.json` holds the desktop words for place and feature names. Use them so
the phone names a screen the same way the computer does. `LocaleCatalogTest` fails when a
language lacks a key, changes a format argument, or lacks a plural form the language needs.

## Installable threads preview

```sh
cd android
./gradlew :app:assemblePreview
```

Install `app/build/outputs/apk/preview/app-preview.apk`. Its launcher name is
**Seira Preview**, its application ID is `bot.seira.companion.preview`,
and its version ends in `-threads-preview`. Gradle signs it with the local debug
key, so no release signing material is needed. It installs beside the released
app with separate pairing, preferences and messages; it does not update that app.

Open Preview and pair using its QR scanner or manual address form. Preview does
not register the release's `openmausbot://` and `seira://` pairing links or system share targets.
Its file-sharing provider uses the preview application ID too.

Debug keys can differ between machines. Android requires the same key to
update an existing preview installation. A fresh installation after removing a
differently signed preview loses only that preview's local data.

Verify conversation changes against an isolated fixture as described in
[`docs/verification/README.md`](../docs/verification/README.md), using a disposable
emulator. These builds do not exercise live pairing or a physical phone by
themselves.

## Building a release APK

```sh
cd android
./gradlew :app:assembleRelease
```

With no signing material configured this writes an **unsigned** APK:

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

That is a supported outcome, not a degraded one — it is the artifact to hand to
whoever holds the release key. The filename says which of the two happened: a
signed build writes `app-release.apk` instead.

The APK is already zip-aligned by the Android Gradle Plugin, and `apksigner`
preserves that alignment. **Do not run `zipalign` or `jarsigner`.**

Release builds leave out live calls. The `LIVE_CALLS` build flag is `false` in
`release` and `true` in every other build type. The release manifest removes the
call service, the Talk tile, the Talk shortcut and the microphone foreground
service permission, and the call buttons and settings do not show.

## Signing it

The signing key belongs to the maintainer and never enters this repository.
`.gitignore` refuses `*.jks`, `*.keystore` and `*.p12` repo-wide, and
`android/keystore.properties` alongside them.

Create a key once, outside any clone of this repository:

```sh
keytool -genkeypair -v -keystore ~/openmausbot-release.jks \
  -storetype PKCS12 -alias openmausbot -keyalg RSA -keysize 4096 -validity 10000
```

Then, for each release:

```sh
# whichever build-tools version is installed; any recent one works
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/* | tail -1)/apksigner"

"$APKSIGNER" sign --ks ~/openmausbot-release.jks --ks-key-alias openmausbot \
  --out app-release.apk app-release-unsigned.apk

"$APKSIGNER" verify --verbose --print-certs app-release.apk
```

Without `--ks-pass`, `apksigner` prompts for the password, which keeps it out of
the shell history. `verify` should report `Verifies` along with v2 and v3
signature scheme lines.

Keep that keystore file and its passwords backed up. Google Play ties an app to
the key that first signed it; losing it means the listing cannot be updated.

### Signing from Gradle instead

To have `assembleRelease` produce a signed APK directly, supply the key material
one of two ways. The environment wins over the file, so a CI runner cannot
silently inherit a stale `keystore.properties` from a cached workspace.

`android/keystore.properties` (gitignored):

```properties
storeFile=/absolute/path/to/openmausbot-release.jks
storePassword=…
keyAlias=openmausbot
keyPassword=…
```

or the environment, for CI secrets:

```
OPENMAUSBOT_KEYSTORE_FILE
OPENMAUSBOT_KEYSTORE_PASSWORD
OPENMAUSBOT_KEY_ALIAS
OPENMAUSBOT_KEY_PASSWORD   # optional; PKCS12 reuses the store password
```

Supply all of it or none of it. A build handed only part of the material stops
and names what is missing, rather than quietly producing an unsigned APK that
looks finished and cannot be published.

Both paths declare v2 and v3 signature schemes. v3 is what makes the key
rotatable later: without a v3 block there is no signing lineage for a new key to
prove it descends from the old one. v1 stays off — it is JAR signing, for
Android 6 and below, which `minSdk 26` already excludes.

## Publishing to Google Play from CI

The workflow `.github/workflows/android-play.yml` builds the signed release bundle
and sends it to Google Play. It uses `scripts/play-publish.mjs`, which calls the
Google Play Android Developer API and needs only Node.

To release:

1. Change `appVersionName` in `app/build.gradle.kts`.
2. Add the release notes in `android/PlayStore/release-notes/<version>/`.
   Follow `android/PlayStore/release-notes/README.md`.
3. Merge the change into `main`.
4. Push the tag. The tag must match `appVersionName`. It starts a release to the `production` track.

```sh
git tag android-vX.Y.Z && git push origin android-vX.Y.Z
```

To pick the track, the status, a rollout percent or a dry run, start it by hand:

```sh
gh workflow run android-play.yml -f track=internal
```

The workflow builds only the commit of the branch or tag that it runs on.
Let the environment `play-store` accept only `main` and tags `android-v*`.
The workflow keeps the bundle and `mapping.txt` as run artifacts for 90 days.

Set these secrets in the GitHub environment `play-store`:

| Secret | Content |
|--------|---------|
| `ANDROID_UPLOAD_KEYSTORE_BASE64` | The upload keystore file, encoded in base64 |
| `ANDROID_UPLOAD_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_UPLOAD_KEY_ALIAS` | The key alias in the keystore |
| `ANDROID_UPLOAD_KEY_PASSWORD` | The key password. Optional: leave it out when it equals the keystore password |
| `ANDROID_GOOGLE_SERVICES_JSON` | The content of `google-services.json` |
| `PLAY_SERVICE_ACCOUNT_JSON` | The JSON key of the Google Cloud service account |

With none of the five required secrets, the workflow builds an unsigned bundle and uploads
nothing. With only some of them, it fails and names the missing ones.

One-time setup in Google:

1. Create a Google Cloud service account and enable the Google Play Android Developer API in its project.
2. In Play Console, open Users and permissions and invite the service account.
3. Give it release permissions for this app.

## Versioning

There is one line to edit per release, in `app/build.gradle.kts`:

```kotlin
private val appVersionName = "1.0.0"
```

`versionCode` is derived from it as `MAJOR * 10000 + MINOR * 100 + PATCH`, so
`1.0.0` is `10000`, `1.0.1` is `10001`, `2.0.0` is `20000`. Each part must be in
`0..99`; a name outside `X.Y.Z` fails the build rather than resolving to a number
nobody chose. There is no second number to remember to bump.

Play orders uploads by `versionCode` alone and refuses one it has already
accepted, so the code has to rise whenever the name does.

## R8 in the release build

The release build runs R8: it shrinks, optimizes and obfuscates the code, and
it removes unused resources. Debug and preview builds do not run R8.

- Keep rules: `app/proguard-rules.pro`. The libraries bring their own rules
  for kotlinx.serialization, OkHttp, coroutines, Tink and ML Kit.
- Mapping file: `app/build/outputs/mapping/release/mapping.txt`. Keep the
  file from each release build. Google Play needs it to turn obfuscated
  crash stack traces back into class names and line numbers.
- After an upload, open the version in Play Console (App bundle explorer,
  Downloads) and check that it has a ReTrace mapping file. If it has none,
  upload `mapping.txt` there.

A unit test cannot find a missing keep rule. The failure shows only in a
minified build, and often only when the phone talks to the computer. Before
you change the rules or add a library that uses reflection or JNI, follow
[`docs/verification/android-release-r8.md`](../docs/verification/android-release-r8.md).
