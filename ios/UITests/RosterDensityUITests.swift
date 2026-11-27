import XCTest

/// Runs exclusively against the bundled RosterPreview fleet: a Chief of
/// Staff, a bot with three threads, one waiting on you, one working, groups
/// and named sections. No account, paired computer, or server is involved,
/// and the preview has no API client, so nothing can be sent anywhere.
final class RosterDensityUITests: XCTestCase {
    @MainActor
    private func reveal(_ element: XCUIElement, in app: XCUIApplication, timeout: TimeInterval = 10) -> Bool {
        if element.waitForExistence(timeout: timeout) { return true }
        let list = app.scrollViews["roster-list"]
        for _ in 0..<8 where !element.exists { list.swipeUp(velocity: .slow) }
        return element.waitForExistence(timeout: 2)
    }

    @MainActor
    func testSimpleHomeShowsBotsAsTilesAndNoGreetingOrChips() {
        let app = launchRoster(advanced: false)
        XCTAssertTrue(reveal(app.buttons["chat-row.roster-atlas"], in: app))
        XCTAssertTrue(app.buttons["home-search"].exists)
        XCTAssertTrue(app.buttons["new-group"].exists || reveal(app.buttons["new-group"], in: app))
        XCTAssertFalse(app.staticTexts["Good morning"].exists)
        XCTAssertFalse(app.staticTexts["Good afternoon"].exists)
        XCTAssertFalse(app.staticTexts["Good evening"].exists)
        XCTAssertFalse(app.buttons["All"].exists)
        XCTAssertFalse(app.buttons["Groups"].exists)
        XCTAssertFalse(app.buttons["threads-toggle.roster-pepper"].exists)
        recordScreenshot("Simple home", in: app)
    }

    @MainActor
    func testAdvancedHomeListsThreadsUnderTheBotNameFromTheChevron() {
        let app = launchRoster(advanced: true)
        let toggle = app.buttons["row-threads-toggle.roster-pepper"]
        XCTAssertTrue(reveal(toggle, in: app))
        XCTAssertFalse(app.buttons["thread.roster-pepper-gmail"].exists)
        toggle.tap()
        XCTAssertTrue(app.buttons["thread.roster-pepper-gmail"].waitForExistence(timeout: 5))
        toggle.tap()
        assertMissing(app.buttons["thread.roster-pepper-gmail"])
        recordScreenshot("Advanced home", in: app)
    }

    @MainActor
    func testFoldingADepartmentHidesItsBots() {
        let app = launchRoster(advanced: true)
        XCTAssertTrue(reveal(app.buttons["chat-row.roster-atlas"], in: app))
        let fold = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "group-fold-")).element(boundBy: 1)
        XCTAssertTrue(reveal(fold, in: app))
        XCTAssertEqual(fold.value as? String, "Open")
        fold.tap()
        XCTAssertEqual(fold.value as? String, "Folded")
        fold.tap()
        XCTAssertEqual(fold.value as? String, "Open")
    }

    // MARK: - Helpers

    private static let baseArguments = [
        "-store-preview", "-roster-preview",
        "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
        "-companion.prefs.islandIntro", "never",
        "-companion.onboarding.welcomeSeen", "YES",
        "-companion.onboarding.notificationsSeen", "YES",
    ]

    @MainActor
    private func launchRoster(advanced: Bool) -> XCUIApplication {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.terminate()
        app.launchArguments = Self.baseArguments + ["-companion.prefs.advancedMode", advanced ? "YES" : "NO"]
        app.launch()
        if app.buttons["Connect computer"].exists {
            app.terminate()
            app.launch()
        }
        XCTAssertTrue(app.buttons["updates-button"].waitForExistence(timeout: 10))
        return app
    }

    @MainActor
    private func assertMissing(_ element: XCUIElement) {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"), object: element
        )
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: 5), .completed)
    }

    @MainActor
    private func recordScreenshot(_ name: String, in app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
