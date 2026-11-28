import XCTest

extension XCUIApplication {
    /// Waits for the software keyboard that a tap raised, with its keys
    /// free. A new simulator covers its first keyboard with the
    /// slide-to-type intro, and keys under it get no text. That intro can
    /// come up seconds late on a slow machine, and a tap on its Continue
    /// can come before it takes taps (CI, 2026-11-27): tap until it goes.
    @MainActor @discardableResult
    func waitForKeyboardKeys(file: StaticString = #filePath, line: UInt = #line) -> Bool {
        let keyboard = keyboards.firstMatch
        let intro = buttons["Continue"]
        let deadline = Date().addingTimeInterval(10)
        while !keyboard.exists, !intro.exists, Date() < deadline {
            _ = keyboard.waitForExistence(timeout: 1)
        }
        if !intro.exists { _ = intro.waitForExistence(timeout: 2) }
        for _ in 0..<3 where intro.exists {
            intro.tap()
            let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: intro)
            _ = XCTWaiter().wait(for: [gone], timeout: 5)
        }
        XCTAssertFalse(intro.exists, "the slide-to-type intro still covers the keys", file: file, line: line)
        XCTAssertTrue(keyboard.exists, "no keyboard came up", file: file, line: line)
        return keyboard.exists && !intro.exists
    }
}
