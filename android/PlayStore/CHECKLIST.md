# Seira Play release checklist

Use with the [release preparation guide](README.md). Complete this for the exact
commit and artifact being submitted; unchecked items remain release work.

## Build and verification

- [ ] Record the release commit and intended version from
  `android/app/build.gradle.kts`; confirm the version against Console uploads.
- [ ] Confirm Android branding and desktop references say Seira.
- [ ] Confirm application ID `bot.seira.companion` and the release SDK settings.
- [ ] Have the release owner confirm signing configuration, certificate identity,
  key backups, and supported installation/upgrade paths.
- [ ] Configure signing securely and run `./gradlew :app:bundleRelease` from
  `android/` with JDK 17 and the Android SDK configured.
- [ ] Verify the signature of
  `android/app/build/outputs/bundle/release/app-release.aab`; record its hash,
  version, and certificate identity.
- [ ] Run the applicable [Android checks](../../docs/verification/android-threads.md)
  and manual release workflows using an isolated fixture and disposable emulator.
  Record results and gaps; do not use the user's live app or data.
- [ ] Test installation/upgrade and every advertised workflow on the release
  build, including the connection methods and device types being claimed.

## Listing and reviewer access

- [ ] Review the drafts in `listing/en-US.md` and `listing/id-ID.md` against
  features verified in this release, including its Seira desktop dependency.
  Run `node android/PlayStore/tools/check-listing.mjs`.
- [ ] Exclude unmerged call-mode and voice-key setup claims (#739/#1531).
- [ ] Review icon, feature graphic, and each selected screenshot against the
  release, with Seira branding and no private data.
- [ ] Use only the framed screenshots in `frames/<language>/`. Phone images must
  be 9:16 or 16:9, each side 320–3840 px, at most 8 MB, 2 to 8 per language
  (Console rules, read 2026-11-19). Show no call entry point.
- [ ] Render the screenshots again on the release UI (see README, "Screenshots"),
  then run `python3 android/PlayStore/tools/frame.py`.
- [ ] Confirm the release build has the Seira character heads and the Change
  look sheet. Screenshot 2 and the listing line about each bot's look need them.
- [ ] Use the 512×512 icon that matches the release launcher icon.
- [ ] Check current Console asset requirements and use only reviewed images.
- [ ] Confirm support contacts, website, privacy policy URL, category, pricing,
  and distribution choices with the release owner.
- [ ] Prepare and test reviewer setup/access instructions, including the actual
  signup, authentication, desktop, and server-settings requirements.

## Declarations and submission

- [ ] Review current Console requirements for the account and release track.
  The account is an Organization account, so the 12-tester, 14-day closed test
  rule does not apply (checked 2026-11-19). The account owner must declare
  trader status before February 2027.
- [ ] Decide the open items D-1 to D-5 in `declarations-proposal.md`.
- [ ] Complete content, age-rating, target-audience, ads, permission, and other
  applicable forms from the shipped behavior; this checklist supplies no answers.
- [ ] Review data flows across local connections, hosted access, model providers,
  speech recognition, attachments, identifiers, and enabled services before
  answering collection, sharing, retention, or deletion questions.
- [ ] Account for supported cleartext LAN HTTP when assessing transit encryption.
- [ ] Verify the privacy policy and any stated deletion process; confirm they
  match the release's actual behavior and deployed services.
- [ ] Compare Console-detected package/version and signing details with the
  recorded artifact, then resolve validation messages and required testing.
- [ ] Have the release owner select countries, track, rollout scope, and timing
  and review the complete submission before publishing.
- [ ] Preserve the submitted copy, declarations, artifact identity, and test
  evidence. Monitor review feedback and release health after submission.
