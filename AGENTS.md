# Seira mobile agent notes

This repo holds the Seira phone apps: `ios/` (SwiftUI) and `android/` (Kotlin,
Compose). The server and the wire contract live in `seira-bot/seira`.

## Protocol

- The contract is `packages/phone-protocol` and
  `server/testing/fixtures/phone-jobs.json` in `seira-bot/seira`.
- Captured server responses live in `ios/Tests/CompanionCoreTests/Fixtures/`.
  The Android `:core` tests read the same folder.
- Change the server first. Then capture new fixtures with
  `node scripts/capture-companion-fixtures.mjs --out <this repo>/ios/Tests/CompanionCoreTests/Fixtures`
  in a `seira-bot/seira` clone.
- Keep `ios/Sources/CompanionCore` and `android/core` in step. The Android
  `:core` module is a port of the Swift package.

## Build and test

- iOS needs macOS and Xcode. Follow `ios/TESTING.md`.
- Android needs JDK 17 and an Android SDK. Follow `android/README.md`.
- Before you claim that a change works, run the matching list in
  `docs/verification/`.

## macOS CI minutes

A macOS minute costs ten times a Linux minute, and this private repo pays for every minute.

- Do not use a CI run to find compile errors one at a time.
- Before you start a new run, read the full log and the result bundle of the failed run.
- Fix every failure that you find. Then start one new run.
- To fix a failure, run only the failed part: `ui_test_classes`, `store_devices` or `store_tests`.
- Start no more than three manual runs of `ios.yml` on one branch on one day.
- After the third failed run, stop and report to the maintainer.
- Run the Android unit tests and the debug build on a Linux machine before you push. CI is the last check.
- Start `ios-testflight.yml` only for a build that goes to testers.

## Comments

Write a comment only for what the code cannot say:

- a license or attribution notice;
- a fact forced by something outside this code (a vendor, SDK, OS, store
  rule or protocol behaviour), with its version when it has one;
- the doc comment of a public contract;
- a link to an upstream issue;
- a security invariant or a known open risk.

Do not narrate what the next line does, restate a name, record history, point
at other files, draw banners or leave commented-out code.

## Local details stay local

A committed file must work on any machine and must not describe one person's
machine or private workflow.

- Never commit an absolute home path, a host name, an SSH alias, a private IP
  address, a user name, signing material, or a secret.
- Keep store signing keys, `google-services.json`, provisioning profiles and
  App Store Connect keys out of git. Load them from CI secrets or local files.
- Keep specs, plans, done notes and research in `internal/` (gitignored).
- Put notes for your own machine in `AGENTS.local.md` (gitignored).
- `node scripts/check-local-leaks.mjs` finds these details in the lines you
  add. The pre-commit hook in `scripts/git-hooks/` runs it on staged lines.
  Put your own user name, host aliases and LAN addresses in `.leakcheck.local`,
  one regular expression per line.
