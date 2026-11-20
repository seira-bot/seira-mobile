# Seira mobile

The iOS and Android apps for [Seira](https://github.com/seira-bot/seira).
Pair a phone with a computer or a Seira Cloud. Then read your bots, answer
their approvals, and send them the next task from the phone.

The computer keeps the bots, credentials and transcripts. The phone keeps no
copy of them.

## Status

The code comes from the companion apps as they were on 2026-10-06, with
their full history. App names, store IDs and icons are Seira. Internal code
names (Swift and Kotlin types, packages, the Android `namespace`, the
XcodeGen project) keep their old names. Protocol identifiers (the pairing
URL scheme, the mDNS service name and the well-known path) use the Seira
names of desktop 0.1.182 and later.

## Layout

| Folder | Contents |
|--------|----------|
| `ios/` | SwiftUI app, share extension, widgets, `CompanionCore` Swift package |
| `android/` | Kotlin and Compose app (`:app`) and protocol module (`:core`) |
| `docs/verification/` | Manual and simulator check lists for each app |
| `scripts/` | iOS string and view checks, leak check, git hooks |

## Build

iOS needs macOS, Xcode and [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```sh
cd ios
xcodegen generate
open OpenMausCompanion.xcodeproj
```

Android needs JDK 17 and an Android SDK:

```sh
cd android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

More detail is in [`ios/README.md`](ios/README.md), [`ios/TESTING.md`](ios/TESTING.md)
and [`android/README.md`](android/README.md).

## Protocol

The apps talk to the Seira server. The wire contract lives in the main repo:
`packages/phone-protocol` and `server/testing/fixtures/phone-jobs.json` in
[seira-bot/seira](https://github.com/seira-bot/seira).

Both apps test against captured server responses in
`ios/Tests/CompanionCoreTests/Fixtures/`. To refresh them, run
`scripts/capture-companion-fixtures.mjs --out <folder>` in a Seira clone.

## Git hooks

Turn on the leak check once per clone:

```sh
git config core.hooksPath scripts/git-hooks
```

## License

Apache License 2.0. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
