import XCTest
@testable import CompanionCore

final class BotActionRulesTests: XCTestCase {
    private func bot(threadId: String, pinned: Bool? = nil) -> Bot {
        var bot = Bot(
            id: "b", threadId: threadId, name: "Scout", title: "", description: "",
            notifications: true, color: "green", unread: false,
            modelSelection: ModelSelection(instanceId: "codex", model: "m"), createdAt: 1
        )
        bot.pinned = pinned
        return bot
    }

    func testCopyIsHiddenWhenTheBotHasNoConversation() {
        XCTAssertEqual(BotActionRules.visible(for: bot(threadId: "")), [.pin, .markUnread, .markAllRead, .move])
        XCTAssertTrue(BotActionRules.visible(for: bot(threadId: "t1")).contains(.copyID))
    }

    func testPinReadsFromTheBot() {
        XCTAssertFalse(BotActionRules.isPinned(bot(threadId: "t1")))
        XCTAssertTrue(BotActionRules.isPinned(bot(threadId: "t1", pinned: true)))
    }
}
