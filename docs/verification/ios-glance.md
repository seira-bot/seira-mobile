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
`ui_test_classes=GlanceLiveActivityUITests`. The test launches with
`-store-preview -approval-preview -glance-preview` and captures four
screenshots. The job uploads them as the artifact `glance-screenshots`.

## Known limit

In the hosted simulator run (CI run 45162909310), the test passed and the
screenshots were saved. The Live Activity did not draw on the home screen, and
the lock screen capture was black. The simulator does not show Live Activities
reliably in this setup. Check the layout on a device before a release.
