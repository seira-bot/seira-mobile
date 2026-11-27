# App Review notes

Paste the text block into App Store Connect, **App Review Information → Notes**.
Replace `[REVIEW PAGE]`, `[PASSPHRASE]`, and `[SERVER ADDRESS]` there. Keep the
real values in App Store Connect only. Do not commit them.

The review server runs Seira with a bot named "Demo Assistant" and no personal
data. Keep it running, and keep its passphrase unchanged, while a review is
open. Its reviewer page mints a new single-use pairing code for each visit.
Google Play App access uses the same server.

```text
Seira for iPhone and iPad is a remote control for the Seira app on the user's own computer. The app has no account and no sign-in screen. We run a review server with Seira, so you can pair without a computer of your own.

1. In a browser, open [REVIEW PAGE] and enter this passphrase: [PASSPHRASE]
   The page shows a QR code and a code like ABCD-EFGH-JKLM.
2. Open the app. On "Connect your computer", point the camera at the QR code.
   Without a camera: type the address [SERVER ADDRESS], tap Continue, type the code, and confirm.
3. Open the bot "Demo Assistant" and send: Create a file named note.txt with the text hello.
4. The bot asks before it acts. Tap Allow.

Each code works once. Tap "Get a new code" on the page for another one.

You can also share a link from Safari to Seira with the Share sheet, and change a bot's look on its page.

There is no purchase and no subscription. The app sends data only to the paired computer (here, the review server).
```

Set **Sign-in required** to off: the app has no account.
