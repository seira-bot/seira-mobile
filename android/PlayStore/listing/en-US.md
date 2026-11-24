# Google Play listing: English (United States), default

Status: draft for the release owner to review. Paste each text block into
Play Console exactly as written. `node android/PlayStore/tools/check-listing.mjs`
checks the Console length limits, the screenshot captions, and the alt texts.

Screenshot captions and alt texts live in [`../frames/shots.json`](../frames/shots.json).

## App name

```text
Seira: AI bot companion
```

## Short description

```text
Your Seira bots work on your computer. Answer and allow their steps from here.
```

## Full description

```text
Your Seira bots work on your computer. When you step away from your desk, you can keep up with them from your phone: read their replies, answer their questions, and allow the steps that matter.

What you can do
• See every bot on one screen. Chats that need you come first.
• Allow or deny a step when a bot asks before it acts.
• Read replies while the bot writes them, with tables and formatted text.
• While Seira is open, one notification shows the bot that needs you most. You can answer some requests right from it.
• Add the Updates widget to your home screen to see the chats that need you.
• Give each bot its own face: pick a Seira character and change its hair, face, extras, and color, or upload your own picture.
• Send a message or a new task. Type it, or use dictation.
• Share a link, a photo, or a document from another app to a bot.
• Pair more than one computer and switch between them.
• Use the light or the dark theme.

Before you start
Seira for Android is a companion to Seira on your computer. It does not run bots by itself.
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

The release owner writes the notes for each version in
[`../declarations-proposal.md`](../declarations-proposal.md). This block is the
fallback text for a release that has no notes of its own.

```text
Seira for Android: pair your phone with Seira on your computer, then read and answer your bots from your phone.
```

## Claims and their source

Each claim maps to code in `main` after Android 1.0.6 (PR #70, #64, #49, and
the redesign). The screenshots come from the same code (`StoreScreenshotTest`
and the Glance demo receiver in debug builds). Verify each claim again on the
signed release build.

| Claim | Where it is in the app |
| --- | --- |
| Chats that need you come first | Home, "Needs attention" group |
| Allow or deny a step | Chat approval card ("Needs your OK", Allow, Deny) |
| Replies while the bot writes them, tables, formatted text | Chat screen, streamed Markdown replies |
| One notification while Seira is open, answer some requests from it | Glance (FR-105, FR-107): one notification while a bot is active and the session is live; Deny, Allow once, Open chat when the text fits |
| Updates widget | `UpdatesWidgetReceiver` in the release manifest, label "Updates" |
| Own face for each bot | Avatar sheet: tabs Seira, Generate, Upload; chips Hair, Face, Extras, Colour |
| Dictation | Composer dictation button |
| Share from another app | Release manifest `SEND` / `SEND_MULTIPLE` targets (text, images, PDF, office files) |
| More than one computer, switch | Settings "Connect another computer"; computer switch in the Chats heading |
| Light and dark theme | Settings theme choice |
| Pairing path | App hint "On your computer, open Seira → Settings → Phone." |
| Wi-Fi, Tailscale, secure address | Pairing screen hints |
| Android 8.0 or later | `minSdk 26` |

Not claimed on purpose: live calls, the voice key, watching the bot's computer,
notifications while the app is closed, picture generation (it needs setup on the
computer), and anything about Seira Cloud.
