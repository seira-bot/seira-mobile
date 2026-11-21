# Android release build with R8

The release build runs R8 (`isMinifyEnabled` and `isShrinkResources` in
`android/app/build.gradle.kts`, rules in `android/app/proguard-rules.pro`).
Unit tests run the debug build, so they do not see R8. Use this list after a
change to the keep rules, to a library version, or to code that uses
reflection, JNI or `kotlinx.serialization` in a new way.

## 1. Build

Run from `android/`:

```sh
./gradlew :app:assembleRelease
```

- The build must not write
  `app/build/outputs/mapping/release/missing_rules.txt`.
- Keep `app/build/outputs/mapping/release/mapping.txt` for the release.

## 2. Sign the APK for a test install

Sign a copy with a debug key. Do not use the release key for a test install.

```sh
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/* | tail -1)/apksigner"
"$APKSIGNER" sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --out app-release-test.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

## 3. Start a disposable Seira server

Use a Seira clone with its own data folder and port. Never use the Seira
that you use every day, its data folder, or its port.

```sh
SEIRA_DATA_DIR="$(mktemp -d)" SEIRA_PORT=19871 SEIRA_WEBHOOK_PORT=19872 \
  node server/index.ts
```

Give the server two engines: one that answers, and one that asks for
permission, so that a message makes an approval card. The fake engines in
`server/testing/` of the Seira repository do both
(`fake-claude-cli.ts`, and `fake-acp-cli.ts` with `FAKE_ACP_MODE=permission`
on a bot with approval mode "ask").

On an emulator, the phone reaches the computer at `http://10.0.2.2:19871`.

## 4. Test on an emulator or a phone

Install `app-release-test.apk`. Do each step on a clean install
(`adb shell pm clear bot.seira.companion` between pairings).

| Step | Pass when |
|------|-----------|
| Open the app | The welcome screen and the Seira character show. No crash. |
| Pair with the QR scanner | The camera view starts, and "Scanner unavailable" does not show. On a phone, scan the QR: the confirmation screen names the computer, and pairing finishes. |
| Pair with the code | The twelve-character code and the address pair the phone. |
| Pair with a link | `adb shell am start -a android.intent.action.VIEW -d '<pairing link>'` opens the confirmation screen, and pairing finishes. |
| Chat | A message reaches the bot, and the reply shows in the chat. |
| Approval card | The card shows. "Allow" settles it, and the bot finishes its turn. |
| Restart | Force-stop and open the app. It stays paired and shows the chat. |

An emulator cannot do the QR scan itself: the virtual scene of emulator
37.3 did not show the poster image. The scanner gives the scanned link to
the same function as a pairing link, so the link test covers the steps
after the scan.

Watch the log during the test:

```sh
adb logcat -b crash -b main '*:E' | grep -E 'FATAL|Serialization|ClassNotFound|NoSuchMethod|UnsatisfiedLink'
```

A `SerializationException`, `ClassNotFoundException`, `NoSuchMethodError` or
`UnsatisfiedLinkError` means a keep rule is missing. Retrace the stack trace
with the mapping file:

```sh
"$ANDROID_HOME"/cmdline-tools/latest/bin/retrace \
  app/build/outputs/mapping/release/mapping.txt stacktrace.txt
```
