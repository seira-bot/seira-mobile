# Seira Google Play release preparation

Use this guide with the [release checklist](CHECKLIST.md). It records repository
inputs and checks to perform for each release. It does not establish that a
release is ready to publish.

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
| Phone screenshots, 1080×1920 (9:16) | [frames/en-US/](frames/en-US/), [frames/id-ID/](frames/id-ID/) | Six images per language. Alt texts are in [frames/shots.json](frames/shots.json). Compare with the release build. |
| Feature graphic, 1024×500 | [graphics/feature-graphic-en-US.png](graphics/feature-graphic-en-US.png), [graphics/feature-graphic-id-ID.png](graphics/feature-graphic-id-ID.png) | Alt text is in `shots.json` (`feature`). Check Seira branding. |
| Icon, 512×512 | [assets/play-icon-512.png](assets/play-icon-512.png) | Use the icon that matches the release launcher icon. |
| Declarations | [declarations-proposal.md](declarations-proposal.md) | Answers saved in Play Console for release 1.0.0, and the open steps. Check them again when the release build changes. |

Run `node android/PlayStore/tools/check-listing.mjs` from the repository root.
It checks the Console length limits, the screenshot captions and alt texts,
and words that the copy must not use.

Do not advertise Android call mode or on-device voice key setup. This
preparation work does not establish them (#739, #1531). Composer dictation is a
separate feature. The old screenshots, including the call screen, are removed.

The release build leaves out live calls and the always-on background
connection (`LIVE_CALLS` and `ALWAYS_ON` are false, and
`src/release/AndroidManifest.xml` removes their services and permissions). The
release has no foreground service, so the Console foreground service form does
not apply. Do not advertise notifications while the app is closed.

### Screenshots and feature graphics

Each screenshot is a real render of the app with synthetic data. The page puts
the render on a night background with a short caption. It never draws or
changes app UI. Play rules (read 2026-11-24): no device frame, no tilt, no
ranking or price words, and captions take less than 20% of the image.

The six shots tell one story, and one red line runs across all of them:

| Shot | Shows |
| --- | --- |
| 01-home | Home: the team, and chats that need you first |
| 02-approval | The approval card in a chat, lifted out of the screen |
| 03-glance | The Glance notification over Home |
| 04-live | A reply with a table, lifted out of the screen |
| 05-look | The avatar sheet with the hair choices |
| 06-pair | The pairing screen after a QR scan |

1. Render the app screens and the cast heads. From `android/`, run
   `./gradlew :app:testDebugUnitTest --tests '*StoreScreenshotTest' --tests '*CastPortraitTest'`.
   The tests render with Robolectric at 411×731 dp and write
   `android/app/build/design-screens/store-*.png`.
2. Copy the `store-*.png` files that [frames/shots.json](frames/shots.json)
   names to [raw/](raw/). Copy the cast heads it names to [raw/cast/](raw/cast/).
3. Capture the Glance notification on an emulator with a debug build in dark
   theme. Send a broadcast to `.glance.GlanceDemoReceiver` with the extras
   `state=needs`, `name=Scout`, `detail=Send the reminder email to Dina Pratama`,
   and `avatar=lumi`. Save the capture as `raw/store-glance-needs-dark.png`.
4. Edit captions, alt texts, and crop boxes in
   [frames/shots.json](frames/shots.json) if needed.
5. From the repository root, on a build host, run
   `bash android/PlayStore/tools/render-store.sh`. It needs node, Chromium,
   and Pillow. It writes every screenshot and both feature graphics, and checks
   size, color mode, and file size.
6. Run `node android/PlayStore/tools/check-listing.mjs`. It also checks the
   captions and the alt texts (140 characters at most).

On 2026-11-24 the Play Console store listing editor had no field for alt text.
Keep the alt texts in `shots.json` for the web pages and for a later Console field.

Where each part of an image comes from:

| Part | Source |
| --- | --- |
| Phone screen and lifted cards | The app render in `raw/`, cropped by the boxes in `shots.json` |
| Heads | The app's own character renderer (`CastPortraitTest`) |
| Background | AI-generated night plates in [graphics/source/bg/](graphics/source/bg/): color and light only, no UI and no text. Play Console labels each image that uses them as AI-edited. |
| Red line and captions | [tools/compose.mjs](tools/compose.mjs), from `shots.json` |

The feature graphic uses the Lumi head, not the Seira head of the app icon.

The render test hides every call entry point (the Home Talk pill, the chat call
button, and the composer talk button). Check each image again when the UI changes.

`raw/` also holds tablet renders (1920×1080 and 2560×1440) and extra avatar
sheet renders. They are not in the upload set.

The caption font is Geist, under the SIL Open Font License
([tools/fonts/OFL.txt](tools/fonts/OFL.txt)).

## Manual Console and privacy review

The release owner must complete current Console forms using the shipped app,
its dependencies, and the deployed services. Review all applicable declarations,
including data safety, permissions, content rating, audience, ads, and app access.
[declarations-proposal.md](declarations-proposal.md) records the answers that
are saved in Play Console for release 1.0.0, with their sources and open steps.

Account for each supported connection, provider, and hosting configuration:

- [Network configuration](../app/src/main/res/xml/network_security_config.xml)
  permits cleartext local LAN traffic. Do not claim all traffic is encrypted in
  transit based on the availability of HTTPS or Tailscale routes.
- [Composer dictation](../app/src/main/kotlin/com/openmausbot/companion/dictation/SpeechDictation.kt)
  can fall back to a platform speech recognizer that uses the network. Review
  that processing alongside messages, attachments, credentials, identifiers,
  optional hosted sign-in, and any other enabled data flows.
- Review the release's merged manifest and dependencies against the source
  [manifest](../app/src/main/AndroidManifest.xml) and the
  [release manifest](../app/src/release/AndroidManifest.xml). Explain the actual
  permission use, including QR pairing, dictation, and notifications.

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
