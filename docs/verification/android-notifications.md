# Android notifications, shortcuts and Talk tile

## When Approve appears

An approval notification always has **Review**. It has **Approve** only when
all of these are true at the time the notification is posted and again when the
button is tapped:

- The thread has exactly one pending card.
- The card is a tool permission, not a question, an outbound send or a team
  memory request.
- The card has no skill request.
- The card offers a plain allow choice (Allow, Approve, Yes or Allow once).
- The card is the same request that the notification was posted for.
- The chat of the thread exists in the session.

Approve answers `allow` for that one request. It never stores "always allow".
The phone must be unlocked.

## Covered by unit tests

Run `:app:testDebugUnitTest`. These classes cover the area:

| Class | What it proves |
|-------|----------------|
| `ApprovalNotificationTest` | Approve and Review rules, authentication flag, immutable intent, one request code per notification, answer on the wire, no grant written, failure text, Review kept |
| `ConversationNotificationTest` | MessagingStyle with the bot as sender, shortcut id and locus id, Reply input flags, reply sent and added to the notification, failure text |
| `BotShortcutsTest` | Long-lived shortcuts, four most recent bots, stale and other-computer shortcuts dropped, removal on unpair, generated faces |
| `TalkTileTest` | Talk intent, tile click (unlocked and locked), tile state, chief request, request expiry, `MainActivity` TALK action |
| `NativeSurfacesManifestTest` | Receiver not exported, tile bind permission, static shortcut |
| `ChineseLocalizationTest` | Same keys and format arguments in all three `strings_native.xml` files |

## Needs a real device

Robolectric does not run the system UI or the shortcut service. Check these on
a phone (API 34 or newer, and one phone on API 26 to 30):

1. Approve on the lock screen asks to unlock first, then answers once.
2. Approve with no network shows the failure text and keeps Review.
3. Reply from the shade and from a smartwatch sends the text and shows it in
   the notification.
4. A bot notification appears in the Conversations section with the bot face.
5. Long-press the app icon: the four most recent bots and Talk appear.
6. Sign out: the bot shortcuts disappear.
7. Add the Talk tile in Quick Settings. It is unavailable before pairing.
8. Tap the tile on a locked phone: unlock, then the chief of staff chat opens
   and the call starts after the usual consent steps.
9. Talk-back reads the Approve, Review and Reply buttons.
