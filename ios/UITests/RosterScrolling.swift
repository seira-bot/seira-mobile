import XCTest

extension XCUIApplication {
    /// Scrolls the Home roster until `element` is fully above the tab bar.
    /// On a small phone the Chief of Staff card pushes later rows below the
    /// screen, and the tab bar covers the last visible rows.
    @MainActor
    func revealOnRoster(_ element: XCUIElement, timeout: TimeInterval = 10) -> Bool {
        let list = scrollViews["roster-list"]
        guard list.waitForExistence(timeout: timeout) else { return false }
        for _ in 0..<10 {
            if isClearOfRosterChrome(element) { return true }
            list.swipeUp(velocity: .slow)
        }
        return isClearOfRosterChrome(element)
    }

    @MainActor
    private func isClearOfRosterChrome(_ element: XCUIElement) -> Bool {
        guard element.exists, element.isHittable else { return false }
        var chromeTop = frame.maxY
        for control in [buttons["tab-chats"]] where control.exists {
            chromeTop = min(chromeTop, control.frame.minY)
        }
        return element.frame.maxY <= chromeTop
    }
}
