# Google Play listing: English (United States), default

Status: draft for the release owner to review. Paste each text block into
Play Console exactly as written. `node android/PlayStore/tools/check-listing.mjs`
checks the Console length limits.

Screenshot captions live in [`../frames/captions.json`](../frames/captions.json).

## App name

```text
Seira: AI bot companion
```

## Short description

```text
Answer your Seira bots from your phone while they work on your computer.
```

## Full description

```text
Your Seira bots work on your computer. When a bot needs an answer, you might be away from your desk. With Seira for Android, you answer from your phone and the bot continues its work.

What you can do
• See all your bots in one list. Bots that need your attention come first.
• Give each bot its own look: a character, hair, color, and extras.
• Read each conversation as the reply comes in, with tables and formatted text.
• Allow or deny a request when a bot asks before it acts.
• Send a message or a new task. Type it, or use dictation.
• Share a link, a photo, or a document from another app to a bot.
• Pair more than one computer and choose the one to use.
• Use the light or the dark theme.

Before you start
Seira for Android is a companion to the Seira desktop app. It does not run bots by itself.
• Install Seira on your computer and set up at least one bot.
• Your phone must reach that computer: on the same Wi-Fi network, through Tailscale, or at a secure address that Seira shows.
• Android 8.0 or later.

How to pair
1. On your computer, open Seira, then Settings, then Phone.
2. Open Seira on your phone and scan the QR code.
3. Check the computer name, then confirm.

Your bots stay on your computer. Your phone shows their work and sends your answers back.
```

## Release notes

```text
First release of Seira for Android. Pair your phone with Seira on your computer, then read and answer your bots from your phone.
```

## Claims and their source

Each feature claim above maps to code in the release candidate: PR #3
(`claude/android-redesign-d4dc4b`) plus the Seira character work
(`claude/android-character` and `claude/android-character-sheet`). The
screenshots also come from that combined code. Do not submit until all three
are in the release build. Verify each claim again on the signed release build.

| Claim | Where it is in the app |
| --- | --- |
| Bots that need attention come first | Home roster, "Needs attention" group |
| Each bot its own look | Change look sheet (`ChangeLookSheet.kt`): Characters, Hair, Colour, Face, Extras |
| Reply appears as it comes in, tables, formatted text | Chat screen, streamed Markdown replies |
| Allow or deny a request | Chat approval card ("Needs your OK") |
| Dictation | Composer dictation button |
| Share from another app | Release manifest `SEND` / `SEND_MULTIPLE` targets (text, images, PDF, office files) |
| More than one computer | Settings, "Connect another computer", "Use %1$s" |
| Light and dark theme | Settings theme choice |
| Pairing path | App hint "On your computer, open Seira → Settings → Phone." |
| Wi-Fi, Tailscale, secure address | Pairing screen hints |
| Android 8.0 or later | `minSdk 26` |

Not claimed on purpose: live calls, the voice key, watching the bot's computer,
notifications while the app is closed, and anything about Seira Cloud.
