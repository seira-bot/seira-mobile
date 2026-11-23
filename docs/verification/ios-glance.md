# iOS Glance Live Activity

Use a disposable simulator and synthetic data. Do not pair these checks with a
running desktop.

## Model and content

From `ios/`, run `swift test`. `GlanceTests` ports the shared Glance rules:
state rank, hold times, one ask per bot, text limits and the Allow once budget.
`GlanceContentTests` covers `GlanceContent.canAnswer`.

Allow once shows only for an approval whose whole title and detail are printable
ASCII and fit the budget. A stale request, or an ask that is not an approval,
sends nothing.

## Live Activity layout

`GlanceActivityWidget` shows one activity for all bots: lock screen, expanded
island, compact and minimal. The expanded island and the lock screen use
`ViewThatFits(in: .vertical)`. If the full text does not fit, the title shows
with Deny and Open chat, and Allow once is hidden.

## Native UI

Run the workflow `ios.yml` with `ui_tests=true` and
`ui_test_classes=GlanceLiveActivityUITests`. The job creates a simulator of the
newest numbered iPhone Pro, so the Dynamic Island is there. The test launches
with `-store-preview -approval-preview -glance-preview`, sends the app home,
opens the island, and wakes the lock screen. The job uploads the captures as
the artifact `glance-screenshots`.

## Screenshots

The preview bot asks to run `git push origin fix/sidebar-search-collapse`.

| View | What it shows | Image |
|------|---------------|-------|
| Lock screen | Name, state word, title, the whole text, Deny, Allow once, Open chat | `images/ios-glance-lock-screen.png` |
| Expanded island | The text does not fit the island height, so only the title, Deny, and Open chat show | `images/ios-glance-island-expanded.png` |
| Compact island | Bot head and a state dot, no number | `images/ios-glance-island-compact.png` |

## Needs a real device

1. Allow once on the lock screen asks to unlock first.
2. Allow once and Deny reach the computer, and the activity updates.
3. A long approval shows only Deny and Open chat on every view.
4. The activity stays exact only while the app is alive. There is no push path.
