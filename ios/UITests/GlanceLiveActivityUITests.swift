import XCTest

/// Runs against the bundled ApprovalPreview fleet with `-glance-preview`: one bot asks to run a
/// short command. The app starts the Glance Live Activity; the test sends the app to the Home
/// screen and photographs the Dynamic Island and the lock screen. Nothing is answered.
final class GlanceLiveActivityUITests: XCTestCase {
    @MainActor
    func testGlanceShowsOneActivityInTheIslandAndOnTheLockScreen() {
        continueAfterFailure = true
        let app = XCUIApplication()
        app.terminate()
        app.launchArguments = [
            "-store-preview", "-approval-preview", "-glance-preview",
            "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
            "-companion.prefs.islandIntro", "never",
            "-companion.onboarding.welcomeSeen", "YES",
            "-companion.onboarding.notificationsSeen", "YES"
        ]
        app.launch()
        XCTAssertTrue(app.buttons["chat-row.preview-mochi"].waitForExistence(timeout: 15), "the preview bot is on the roster")
        pause(6)
        capture("In the app")

        XCUIDevice.shared.press(.home)
        pause(3)
        capture("Dynamic Island compact")

        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.035)).press(forDuration: 1.5)
        pause(2)
        capture("Dynamic Island expanded")

        XCUIDevice.shared.press(.home)
        pause(1)
        pressSideButton()
        pause(2)
        pressSideButton()
        pause(3)
        capture("Lock screen")
        XCUIDevice.shared.press(.home)
    }

    /// The first press turns the screen off. The second press wakes it on the lock screen.
    private func pressSideButton() {
        let lock = NSSelectorFromString("pressLockButton")
        if XCUIDevice.shared.responds(to: lock) {
            _ = XCUIDevice.shared.perform(lock)
        }
    }

    private func pause(_ seconds: TimeInterval) {
        let wait = expectation(description: "pause")
        wait.isInverted = true
        waitForExpectations(timeout: seconds)
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
