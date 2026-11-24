import XCTest
@testable import CompanionCore

final class QuickReplyContextTests: XCTestCase {
    private func text(_ id: String) -> Message {
        var message = Message(id: id, role: .bot, kind: .text, at: 1)
        message.text = "hello"
        return message
    }

    private func tool(_ id: String, _ name: String, notice: Bool? = nil) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 1)
        message.tool = ToolActivity(name: name, ok: true)
        message.tool?.notice = notice
        return message
    }

    private func titles(_ replies: [QuickReply]) -> [String] { replies.map(\.title) }

    func testNoToolActivityIsNoCodeActivity() {
        XCTAssertFalse(QuickReplyContext.hasCodeActivity([]))
        XCTAssertFalse(QuickReplyContext.hasCodeActivity([text("a")]))
    }

    func testACommandOrAFileChangeCountsAsCodeActivity() {
        for name in ["Bash", "Edit", "MultiEdit", "Write", "NotebookEdit", "apply_patch",
                     "Ran a command", "Edited a file", "Wrote a file", "/bin/bash -lc 'git status'"] {
            XCTAssertTrue(QuickReplyContext.hasCodeActivity([tool("t", name)]), name)
        }
    }

    func testReadingSearchingAndNoticesDoNotCount() {
        for name in ["Read", "Glob", "WebSearch", "Searched the web", "Sent an email", "notice: Resumed after a restart"] {
            XCTAssertFalse(QuickReplyContext.hasCodeActivity([tool("t", name)]), name)
        }
        XCTAssertFalse(QuickReplyContext.hasCodeActivity([tool("t", "Bash", notice: true)]))
    }

    func testStockChipsHideDiffAndTestsUntilThereIsCodeActivity() {
        let shown = QuickReplyContext.chips(QuickReply.defaults, codeActivity: false)
        XCTAssertEqual(titles(shown), ["Explain steps", "What's next?"])
        XCTAssertEqual(QuickReplyContext.chips(QuickReply.defaults, codeActivity: true), QuickReply.defaults)
    }

    func testAChipTheUserWroteAlwaysShows() {
        let mine = QuickReply(id: "mine", title: "Show diff", prompt: "Show the diff", icon: "arrow.triangle.pull")
        XCTAssertEqual(QuickReplyContext.chips([mine] + QuickReply.defaults, codeActivity: false).first, mine)
    }

    func testAStockChipTheUserEditedCountsAsTheirOwn() {
        var edited = QuickReply.defaults.first { $0.id == "default.tests" }!
        edited.prompt = "Run the unit tests"
        let saved = QuickReply.defaults.map { $0.id == edited.id ? edited : $0 }
        let shown = QuickReplyContext.chips(saved, codeActivity: false)
        XCTAssertTrue(shown.contains(edited))
        XCTAssertFalse(shown.contains { $0.id == "default.diff" })
    }

    func testAnEmptySavedListStaysEmpty() {
        XCTAssertEqual(QuickReplyContext.chips([], codeActivity: false), [])
    }
}
