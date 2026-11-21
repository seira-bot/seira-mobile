# iOS AI usage and model switch

Use a disposable simulator or a test phone and a test computer. Do not pair
these checks with a computer that holds real work.

## Core

From `ios/`, run `swift test`. `EngineUsageTests` covers the usage report:

- decoding with and without `windows`, and a window name this build does not know;
- the `/api/limit-resets` path and the `refresh=1` query;
- a 403 or 404 answer reads as no usage, and a 500 still fails;
- the share left, the at-limit state and the empty list;
- which failed turn offers "Switch model".

## Owner phone

Pair with `seira pair` (the default pairing, admin scope) to a computer that
serves usage.

1. Open Settings. The Workspace group shows "AI usage".
2. Open "AI usage". Each AI that reports usage shows its windows, the share left
   and the reset time. An AI at its limit shows "At limit".
3. Tap "Check now". The screen reads the providers again once.
4. Open a bot, then Bot settings. The Provider picker shows the usage, for
   example "Claude · at limit until 3:40 PM".
5. Make a bot turn fail (stop the provider's sign-in, or use a provider at its
   limit). The failed row shows "Switch model".
6. Tap "Switch model". Bot settings opens on the Model section.
7. Pick another model and tap "Apply model". Close the sheet. The failed row
   reads "Now on {model}. Send your message again to continue."
8. Send a message. The card goes away.
9. When a usage-limit offer card is open in the chat, the failed row shows no
   "Switch model".

## `--client` phone

Pair with `seira pair --client` (no admin scope).

1. Settings shows no "AI usage" row.
2. A failed row shows no "Switch model".
3. Bot settings shows no Model section.

## Old computer

Pair with a computer that answers 404 for `/api/limit-resets`, or a desktop
companion from before the route was allowed (403).

1. Settings shows no "AI usage" row, and no error appears.
2. The Provider picker shows plain names.
3. "Switch model" still works on a failed row.

## Empty usage

Pair with a computer whose AIs are all API keys or local models. "AI usage"
opens a screen that says no usage can be shown.
