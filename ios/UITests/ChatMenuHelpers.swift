import XCTest

extension XCUIApplication {
    /// Opens the thread list from the chat's more menu.
    @MainActor
    func openAllThreads(file: StaticString = #filePath, line: UInt = #line) {
        let menu = buttons["chat-menu"]
        XCTAssertTrue(menu.waitForExistence(timeout: 10), file: file, line: line)
        menu.tap()
        let item = buttons["menu-allThreads"]
        XCTAssertTrue(item.waitForExistence(timeout: 5), file: file, line: line)
        item.tap()
    }
}
