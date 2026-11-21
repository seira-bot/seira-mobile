# Seira Play release checklist

Use with the [release preparation guide](README.md). Complete this for the exact
commit and artifact being submitted; unchecked items remain release work.

## Build and verification

- [x] Record the release commit and intended version from
  `android/app/build.gradle.kts`; confirm the version against Console uploads.
  Done 2026-11-20: 1.0.1 (10001), built from `main` with PR #16. Console shows 10001 (1.0.1).
- [x] Confirm Android branding and desktop references say Seira.
  Done 2026-11-20: app name, icons, and copy say Seira. Wire ids follow the desktop release.
- [x] Confirm application ID `bot.seira.companion` and the release SDK settings.
  Done 2026-11-20: `bot.seira.companion`, minSdk 26, targetSdk 37, compileSdk 37.
- [x] Have the release owner confirm signing configuration, certificate identity,
  key backups, and supported installation/upgrade paths.
  Done 2026-11-20: Play App Signing is on. Upload key `FD:4C:…:C1:FD`, backed up on two
  release-owner machines (mode 600). Upgrades go through Play only.
- [x] Configure signing securely and run `./gradlew :app:bundleRelease` from
  `android/` with JDK 17 and the Android SDK configured.
  Done 2026-11-20 for 10001 and 10002. A test build from PR #11 (now 1.0.3) also passed.
- [x] Verify the signature of
  `android/app/build/outputs/bundle/release/app-release.aab`; record its hash,
  version, and certificate identity.
  Done 2026-11-20: 10001, sha256 `f334454d…ba36f6457e50c47`, upload key `FD:4C:…:C1:FD`.
  Done 2026-11-20: 10002, sha256 `35966bc1…a98bc1dc888a1e20c9ecce69415bf4f`, same upload key.
  Done 2026-11-21: 10003 (1.0.3) from `ae435d9`, sha256 `c20c1c43…0b9b050bb224c53fc1`, same upload key.
  Done 2026-11-21: 10004 (1.0.4) from `0e5daef4`, sha256 `d2e128f2…cf2438c3bdcf13a30e7c7`, same upload key.
  Done 2026-11-21: 10005 (1.0.5) from `b5d8186e`, sha256 `ec10e06b…13445676ad169dc3254469302bd9afb605f1e98bd3`, same upload key.
- [ ] Run the applicable [Android checks](../../docs/verification/android-threads.md)
  and manual release workflows using an isolated fixture and disposable emulator.
  Record results and gaps; do not use the user's live app or data.
  Partial 2026-11-20: a PR #11 test build passed typed-code and link pairing against an
  isolated fixture. The 1.0.1 build passed pairing, the approval card ("Allow" and
  "Deny"), and a bot reply against the review server. The signed 10002 build
  started on an emulator and showed the 3D character without a crash. Pairing
  on 10002 is not tested again.
  Partial 2026-11-21: the 1.0.4 release APK passed typed-code pairing, chat, send and a
  bot reply against an isolated fixture, with no crash. Its bundle has the same sha256
  as the submitted 10004 bundle. Push, the home screen widget and the credential card
  are not tested on a real device.
  Partial 2026-11-21: the 1.0.5 release APK passed pairing, chat with a bot reply, pairing
  two more computers from the Chats heading, switching between three computers, and a
  restart, against isolated fixtures, with no crash. A second server at the same address
  stayed a separate computer. Its bundle has the same sha256 as the submitted 10005 bundle.
- [ ] Test installation/upgrade and every advertised workflow on the release
  build, including the connection methods and device types being claimed.
  Partial 2026-11-20: the upgrade from 1.0.1 to a PR #11 test build did not
  crash. Repeat with the signed 10002 build.

## Listing and reviewer access

- [x] Review the drafts in `listing/en-US.md` and `listing/id-ID.md` against
  features verified in this release, including its Seira desktop dependency.
  Run `node android/PlayStore/tools/check-listing.mjs`.
  Done 2026-11-19; `check-listing.mjs` passes on 2026-11-20.
- [x] Exclude unmerged call-mode and voice-key setup claims (#739/#1531).
  Done: the release build also leaves out the always-on background connection, which the listing does not claim.
- [x] Review icon, feature graphic, and each selected screenshot against the
  release, with Seira branding and no private data.
- [x] Use only the framed screenshots in `frames/<language>/`. Phone images must
  be 9:16 or 16:9, each side 320–3840 px, at most 8 MB, 2 to 8 per language
  (Console rules, read 2026-11-19). Show no call entry point.
- [x] Render the screenshots again on the release UI (see README, "Screenshots"),
  then run `python3 android/PlayStore/tools/frame.py`.
- [x] Confirm the release build has the Seira character heads and the Change
  look sheet. Screenshot 2 and the listing line about each bot's look need them.
  Done: in `main` through the 1.0.0 release candidate merge.
- [x] Use the 512×512 icon that matches the release launcher icon.
- [x] Check current Console asset requirements and use only reviewed images.
- [x] Confirm support contacts, website, privacy policy URL, category, pricing,
  and distribution choices with the release owner.
  Saved in Console on 2026-11-19 (see `declarations-proposal.md`).
- [x] Prepare and test reviewer setup/access instructions, including the actual
  signup, authentication, desktop, and server-settings requirements.
  Done 2026-11-20: App access saved with a tested review server (see `declarations-proposal.md`).

## Declarations and submission

- [x] Review current Console requirements for the account and release track.
  The account is an Organization account, so the 12-tester, 14-day closed test
  rule does not apply (checked 2026-11-19). The account owner must declare
  trader status before February 2027.
  Done 2026-11-20: the app dashboard asks only for the release review and the send step.
- [x] Compare the saved answers in `declarations-proposal.md` with the release
  build. Complete its "Langkah yang tersisa" list.
  Done 2026-11-20: Console showed no permission or FGS error for 10001. The list now holds the 1.0.2 steps.
- [x] Complete content, age-rating, target-audience, ads, permission, and other
  applicable forms from the shipped behavior; this checklist supplies no answers.
  Done: saved on 2026-11-19. The release build has no foreground service, so no permission form applies.
- [x] Review data flows across local connections, hosted access, model providers,
  speech recognition, attachments, identifiers, and enabled services before
  answering collection, sharing, retention, or deletion questions.
  Done 2026-11-19: Data safety in `declarations-proposal.md`.
- [x] Account for supported cleartext LAN HTTP when assessing transit encryption.
  Done 2026-11-19: "encrypted in transit" is answered No.
- [x] Verify the privacy policy and any stated deletion process; confirm they
  match the release's actual behavior and deployed services.
  Done 2026-11-19: `https://seira.bot/privacy` has the "Seira phone apps" section.
- [x] Compare Console-detected package/version and signing details with the
  recorded artifact, then resolve validation messages and required testing.
  Done 2026-11-20: Console shows `bot.seira.companion` 10001 (1.0.1). The required Wi-Fi
  feature message was ignored by the release owner; PR #19 fixes it in 1.0.2.
- [x] Have the release owner select countries, track, rollout scope, and timing
  and review the complete submission before publishing.
  Done 2026-11-20: Production, 178 countries, 100% rollout, sent for review on the owner's go.
  Done 2026-11-21 for 1.0.4: Production, all targeted countries, 100% rollout, sent for
  review on the owner's go.
  Done 2026-11-21 for 1.0.5: Production, all targeted countries, 100% rollout, sent for
  review on the owner's go.
- [ ] Preserve the submitted copy, declarations, artifact identity, and test
  evidence. Monitor review feedback and release health after submission.
  Preserved 2026-11-20 in `declarations-proposal.md`. Google approved 1.0.1 on
  2026-11-20. Google approved 1.0.2 (10002) on 2026-11-20: available on Google
  Play, 178 countries, 100% rollout. Monitor release health.
  Google approved 1.0.3 (10003) on 2026-11-21: available on Google Play, 178
  countries, 100% rollout.
  Console warning on 1.0.2: DEX code optimization is below the threshold
  (Obfuscation 0%). Fix by February 2027: turn on R8 with tested keep rules.
  PR #33 turned on R8 on `main` (`92d1a82`) on 2026-11-21. 1.0.3 does not have it.
  1.0.4 (10004) is the first release with R8. Its bundle carries the R8 mapping file.
  1.0.4 was sent for review on 2026-11-21.
  Google approved 1.0.4 (10004) on 2026-11-21: available on Google Play, 178
  countries, 100% rollout.
  1.0.5 (10005) was sent for review on 2026-11-21.
