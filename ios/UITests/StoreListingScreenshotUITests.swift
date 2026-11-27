import XCTest

/// Photographs the App Store listing from the bundled ListingPreview fleets. The workflow sets
/// STORE_DEVICE (`iphone` or `ipad`) through TEST_RUNNER_STORE_DEVICE and prepares the simulator.
/// Each attachment is named `<device>-<language>-<shot>`.
final class StoreListingScreenshotUITests: XCTestCase {
    private var device: String {
        ProcessInfo.processInfo.environment["STORE_DEVICE"] ?? "iphone"
    }

    override func setUp() {
        continueAfterFailure = true
        XCUIDevice.shared.orientation = .portrait
        addUIInterruptionMonitor(withDescription: "Camera access") { alert in
            guard alert.staticTexts.containing(NSPredicate(format: "label CONTAINS 'camera'")).firstMatch.exists else {
                return false
            }
            alert.buttons.matching(identifier: "Allow").firstMatch.tap()
            return true
        }
    }

    @MainActor func testEnglishListing() { photographListing(language: "en") }

    @MainActor func testIndonesianListing() { photographListing(language: "id") }

    @MainActor
    private func photographListing(language: String) {
        let home = launchFleet(language: language)
        capture("01-home", language: language)
        home.terminate()

        let approval = launchFleet(language: language)
        openChat("Scout", in: approval)
        selectThread("preview-thread-scout", in: approval)
        _ = approval.buttons["Allow"].waitForExistence(timeout: 10)
        pause(1)
        capture("02-approval", language: language)

        if selectThread("preview-thread-invoices", in: approval) {
            _ = approval.staticTexts.containing(NSPredicate(format: "label CONTAINS 'INV-1042'")).firstMatch
                .waitForExistence(timeout: 10)
            pause(2)
            capture("04-live", language: language)
        }
        approval.terminate()

        let look = launchFleet(language: language)
        openChangeLook("Scout", in: look)
        pause(2)
        capture("05-look", language: language)
        look.terminate()

        photographGlance(language: language)
        photographPairing(language: language)
    }

    @MainActor
    private func photographGlance(language: String) {
        let app = launchFleet(language: language)
        _ = app.staticTexts["Scout"].firstMatch.waitForExistence(timeout: 15)
        pause(6)
        XCUIDevice.shared.press(.home)
        pause(3)
        allowLiveActivities()
        pause(3)
        if device == "iphone" {
            let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
            springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.035)).press(forDuration: 1.5)
            pause(2)
            capture("03-glance-island", language: language)
            XCUIDevice.shared.press(.home)
            pause(1)
        }
        pressSideButton()
        pause(2)
        pressSideButton()
        pause(3)
        allowLiveActivities()
        pause(2)
        capture("03-glance-lock", language: language)
        XCUIDevice.shared.press(.home)
        app.terminate()
    }

    private func collapseIsland() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let from = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1))
        from.press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)))
        pause(2)
    }

    private func allowLiveActivities() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for label in ["Always Allow", "Allow"] {
            let button = springboard.buttons.matching(identifier: label).firstMatch
            if button.waitForExistence(timeout: 3) {
                button.tap()
                return
            }
        }
    }

    @MainActor
    private func photographPairing(language: String) {
        let app = XCUIApplication()
        app.terminate()
        app.launchArguments = baseArguments(language: language)
        app.launch()
        let connect = app.buttons["Connect computer"]
        if connect.waitForExistence(timeout: 15) {
            connect.tap()
            pause(3)
            app.tap()
            pause(3)
            capture("06-pair", language: language)
        } else {
            XCTFail("The unpaired screen has no Connect computer button")
        }
        app.terminate()
    }

    @MainActor
    private func launchFleet(language: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.terminate()
        var arguments = baseArguments(language: language)
        arguments += ["-store-preview", "-listing-preview"]
        if language == "id" { arguments.append("-listing-id") }
        app.launchArguments = arguments
        app.launch()
        if app.buttons["Connect computer"].exists {
            app.terminate()
            app.launch()
        }
        XCTAssertTrue(app.staticTexts["Scout"].firstMatch.waitForExistence(timeout: 20), "Scout is on the roster")
        pause(8)
        if device == "iphone" { collapseIsland() }
        return app
    }

    private func baseArguments(language: String) -> [String] {
        [
            "-AppleLanguages", "(en)", "-AppleLocale", "en_US",
            "-companion.prefs.islandIntro", "never",
            "-companion.prefs.rosterDensity", "comfortable",
            "-companion.onboarding.welcomeSeen", "YES",
            "-companion.onboarding.notificationsSeen", "YES"
        ]
    }

    @MainActor
    private func openChat(_ name: String, in app: XCUIApplication) {
        let input = app.descendants(matching: .any)["message-input"]
        let row = app.staticTexts[name].firstMatch
        for _ in 0..<3 where !input.exists {
            if row.exists { row.tap() }
            _ = input.waitForExistence(timeout: 4)
        }
        XCTAssertTrue(input.exists, "\(name)'s chat opened")
    }

    @MainActor @discardableResult
    private func selectThread(_ id: String, in app: XCUIApplication) -> Bool {
        let header = app.buttons["header-threads"]
        guard header.waitForExistence(timeout: 10) else {
            XCTFail("The chat has no thread button")
            return false
        }
        header.tap()
        let thread = app.buttons["thread-\(id)"]
        guard thread.waitForExistence(timeout: 5) else {
            XCTFail("\(id) is not in the thread list")
            return false
        }
        thread.tap()
        pause(2)
        return true
    }

    @MainActor
    private func openChangeLook(_ name: String, in app: XCUIApplication) {
        let candidates = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", name))
            .allElementsBoundByIndex
            .filter { $0.frame.width <= 100 }
        guard let item = candidates.min(by: { $0.frame.minY < $1.frame.minY }) else {
            XCTFail("\(name) is not on the team rail")
            return
        }
        item.press(forDuration: 1.2)
        let change = app.buttons["Change look"]
        if change.waitForExistence(timeout: 5) {
            change.tap()
            let hair = app.buttons["Hair"]
            if hair.waitForExistence(timeout: 5) { hair.tap() }
        } else {
            XCTFail("The context menu has no Change look")
        }
    }

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

    private func capture(_ shot: String, language: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "\(device)-\(language)-\(shot)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
