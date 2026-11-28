import XCTest

extension XCUIApplication {
    /// Opens the thread list from the chat's more menu. Each wait looks
    /// first: `waitForExistence` makes its first check about a second in,
    /// even for an element that is already there.
    @MainActor
    func openAllThreads(file: StaticString = #filePath, line: UInt = #line) {
        let menu = buttons["chat-menu"]
        XCTAssertTrue(menu.exists || menu.waitForExistence(timeout: 10), file: file, line: line)
        menu.tap()
        let item = buttons["menu-allThreads"]
        XCTAssertTrue(item.exists || item.waitForExistence(timeout: 5), file: file, line: line)
        item.tap()
    }
}
