# Android Glance notification

Glance shows one ongoing notification for the focus bot. On Android 16 and later the phone can show it as a Live Update.

## What Glance shows

- The bot avatar, the bot name, and one state word: Working, Needs you, Has a question, Failed, or Finished.
- While the bot works, the notification shows the last line of the bot's own reply and an indeterminate progress bar.
- A plain tool approval shows its title and the whole text it covers.
- Deny, Allow once, and Open chat show only when the whole text fits the notification and the layout measures at most six lines.
- Otherwise the notification offers Deny and Open chat. A question, a review card, or a failed send offers Open chat only.
- Allow once needs the phone unlocked. It answers once and never stores "Always allow".
- If a send fails, the notification keeps the ask and shows "Could not send. Open the chat to answer."

## Covered by unit tests

Run these on a build host (`<host>`), with a memory cap:

```bash
ssh <host> 'bash -lc "cd <repo>/android && systemd-run --user --scope -q -p MemoryMax=8G -p MemorySwapMax=0 ./gradlew --no-daemon --max-workers=4 :core:test --tests \"*Glance*\" :app:testDebugUnitTest --tests \"*Glance*\" --tests \"*Notification*\""'
```

| Class | What it proves |
|-------|----------------|
| `GlanceNotificationTest` | Title, state word, line, avatar, channel, actions per state, no number, no command text, authentication flag, Live Update request |
| `GlanceNotifierTest` | One notification only, none while not live, done entry leaves after four seconds, a fast line posts at most once a second |
| `GlanceActionsTest` | Allow and Deny answers, no answer for a changed request, an empty detail, or a long detail, failed send keeps the ask |

Robolectric 4.16.1 does not run SDK 36. The Live Update test runs on SDK 35 and passes the promotion level as a parameter.
Robolectric also does not measure text widths. The layout test uses hard line breaks to prove the line limit.

## Check on an emulator (Android 16)

1. Build and install the debug build: `./gradlew :app:assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
2. Grant the permission: `adb shell pm grant bot.seira.companion android.permission.POST_NOTIFICATIONS`.
3. Post a sample for each state. The sample receiver exists in debug builds only. It reads no session and uses no network.

```bash
adb shell am broadcast -n bot.seira.companion/bot.seira.companion.glance.GlanceDemoReceiver --es state needs
```

States: `working`, `needs`, `needs_long`, `question`, `failed`, `done`, `could_not_send`, `clear`.

4. Open the shade: `adb shell cmd statusbar expand-notifications`. Capture with `adb exec-out screencap -p`.

## Screenshots

| State | Image |
|-------|-------|
| Working, status bar chip | `images/android-glance-working-statusbar.png` |
| Working, shade | `images/android-glance-working.png` |
| Needs you, three actions | `images/android-glance-needs.png` |
| Needs you, long text, two actions | `images/android-glance-needs_long.png` |
| Has a question | `images/android-glance-question.png` |
| Could not send | `images/android-glance-could_not_send.png` |

## Needs a real device

1. Allow once on the lock screen asks to unlock first.
2. Allow once with no network shows the could-not-send text.
3. Turn on large font sizes. Allow once must disappear for a long text.
