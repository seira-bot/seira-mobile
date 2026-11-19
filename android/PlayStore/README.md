# Seira Google Play release preparation

Use this guide with the [release checklist](CHECKLIST.md). It records repository
inputs and checks to perform for each release; it does not supply approved Play
Console declarations or establish that a release is ready to publish.

## Release identity and build

The Android app and the desktop app are both **Seira**.
The Android application ID is `bot.seira.companion`.

Use the selected release commit's [Gradle configuration](../app/build.gradle.kts)
for `appVersionName`, the derived `versionCode`, and SDK versions. The code is
calculated as `MAJOR * 10000 + MINOR * 100 + PATCH`, with each part in `0..99`.
Confirm the intended version against existing Console uploads before building.

With JDK 17 and the Android SDK configured, run from the repository root:

```sh
cd android
./gradlew :app:bundleRelease
```

The bundle output, relative to the repository root, is
`android/app/build/outputs/bundle/release/app-release.aab`.
`:app:assembleRelease` builds an APK instead.

Gradle reads signing material from the environment or the gitignored
`android/keystore.properties`; environment values take precedence. The required
settings are `OPENMAUSBOT_KEYSTORE_FILE`, `OPENMAUSBOT_KEYSTORE_PASSWORD`, and
`OPENMAUSBOT_KEY_ALIAS`. `OPENMAUSBOT_KEY_PASSWORD` is optional and defaults to
the store password. File equivalents are `storeFile`, `storePassword`,
`keyAlias`, and `keyPassword`.

Keep keys and passwords outside version control and command history. Without
signing material the release is unsigned; partial configuration fails the build.
Before upload, verify the resulting bundle's signature and certificate identity.
The release owner must confirm the Console's upload/app signing configuration
and test compatibility with any supported existing installations. Do not infer
key ownership, upgrade compatibility, or recovery options from this guide.

Record the commit, version, artifact hash, certificate identity, and verification
results together. A successful build alone does not verify app workflows.

## Listing copy and assets

The listing files are drafts for the release owner to review. They are not an
approved upload set.

| Item | Repository file | Review needed |
| --- | --- | --- |
| Copy, English (United States), default | [listing/en-US.md](listing/en-US.md) | Check each claim against the release build. The file maps each claim to the app. |
| Copy, Indonesian | [listing/id-ID.md](listing/id-ID.md) | Same claims as en-US. It states that the app screens are in English. |
| Phone screenshots, 1080×1920 (9:16) | [frames/en-US/](frames/en-US/), [frames/id-ID/](frames/id-ID/) | Six images per language. Compare with the release build. |
| Feature graphic, 1024×500 | [graphics/feature-graphic-en-US.png](graphics/feature-graphic-en-US.png), [graphics/feature-graphic-id-ID.png](graphics/feature-graphic-id-ID.png) | Check Seira branding. |
| Icon, 512×512 | [assets/play-icon-512.png](assets/play-icon-512.png) | Use the icon that matches the release launcher icon. |
| Declarations | [declarations-proposal.md](declarations-proposal.md) | Proposed answers only. The release owner decides and enters them. |

Run `node android/PlayStore/tools/check-listing.mjs` from the repository root.
It checks the Console length limits and words that the copy must not use.

Do not advertise Android call mode or on-device voice key setup. This
preparation work does not establish them (#739, #1531). Composer dictation is a
separate feature. The old screenshots, including the call screen, are removed.

### Screenshots

Each screenshot is a real render of the app with synthetic data, in a frame
with a short caption. The frame never draws or changes app UI.

The renders need the Seira character work: the 3D character heads
(`claude/android-character`) and the Change look sheet
(`claude/android-character-sheet`). Screenshot 2 and the listing line about
each bot's look show that sheet. Do not submit them unless the release build
has both.

1. Render the app screens and the cast portraits. `StoreScreenshotTest` and
   `CastPortraitTest` on branch `claude/play-store-screens-3d` (built on the
   redesign, PR #3, and the character work) render them with Robolectric at
   411×731 dp (9:16). From `android/`, run
   `./gradlew :app:testDebugUnitTest --tests '*StoreScreenshotTest' --tests '*CastPortraitTest'`.
2. Copy `android/app/build/design-screens/store-*.png` to [raw/](raw/). Copy
   the portraits that [frames/captions.json](frames/captions.json) names to
   [raw/cast/](raw/cast/).
3. Edit captions and mascot placement in
   [frames/captions.json](frames/captions.json) if needed.
4. From the repository root, run `python3 android/PlayStore/tools/frame.py`.
   It rebuilds every framed screenshot and both feature graphics, and checks
   size, color mode, and file size. It needs Pillow.

Where each part of an image comes from:

| Part | Source |
| --- | --- |
| Phone screen | The app render in `raw/` |
| Mascots | The app's own character renderer (`CastPortraitTest`), so they match the avatars in the app |
| Background | Generated decoration in [graphics/source/bg/](graphics/source/bg/): confetti, stars, and color only, no UI and no text |
| Caption | [frames/captions.json](frames/captions.json), drawn by `frame.py` |

The render test hides every call entry point (the Home Talk pill, the chat call
button, and the composer talk button). Check each image again when the UI changes.

`raw/` also holds tablet renders (1920×1080 and 2560×1440) and extra Change look
renders. They are not framed and not in the upload set.

The frame font is Geist, under the SIL Open Font License
([tools/fonts/OFL.txt](tools/fonts/OFL.txt)).

## Manual Console and privacy review

The release owner must complete current Console forms using the shipped app,
its dependencies, and the deployed services. Review all applicable declarations,
including data safety, permissions, content rating, audience, ads, and app access.
[declarations-proposal.md](declarations-proposal.md) holds proposed answers with
their sources. They are not approved answers.

Account for each supported connection, provider, and hosting configuration:

- [Network configuration](../app/src/main/res/xml/network_security_config.xml)
  permits cleartext local LAN traffic. Do not claim all traffic is encrypted in
  transit based on the availability of HTTPS or Tailscale routes.
- [Composer dictation](../app/src/main/kotlin/com/openmausbot/companion/dictation/SpeechDictation.kt)
  can fall back to a platform speech recognizer that uses the network. Review
  that processing alongside messages, attachments, credentials, identifiers,
  optional hosted sign-in, and any other enabled data flows.
- Review the release's merged manifest and dependencies against the source
  [manifest](../app/src/main/AndroidManifest.xml). Explain the actual permission
  use, including QR pairing, dictation, notifications, and background connection.

Manually confirm the privacy policy URL, disclosures, retention and deletion
processes, support contacts, and any signup/account requirements. A URL alone
does not establish a working deletion process. Determine collection and sharing
answers from the complete data flows rather than assuming self-hosted or
transient processing is excluded.

Prepare reviewer access instructions for the actual release: required desktop
setup, reachable test environment, authentication, and enabled server settings.
Verify those instructions on an isolated test setup. Do not promise account-free
access, a demo video, or a server configuration that has not been arranged.

Choose pricing, category, countries, testing track, signing setup, and rollout
scope explicitly in the Console. Check the account's current requirements and
resolve validation messages before submitting. Preserve the reviewed listing,
declarations, artifact identity, and test evidence with the release record.
