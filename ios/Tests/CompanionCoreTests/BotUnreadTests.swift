import XCTest
@testable import CompanionCore

final class BotUnreadTests: XCTestCase {
    private func bot(unread: Bool, tasks: [BotTask]? = nil) -> Bot {
        var bot = Bot(
            id: "b1", threadId: "selected", name: "Scout", title: "", description: "",
            notifications: true, color: "green", unread: unread,
            modelSelection: ModelSelection(instanceId: "codex", model: "m"), createdAt: 1
        )
        bot.tasks = tasks
        return bot
    }

    private func task(_ id: String, unread: Bool?) -> BotTask {
        var task = BotTask(threadId: id, title: id, createdAt: 1)
        task.unread = unread
        return task
    }

    func testABotWithoutTasksCountsItsSelectedThread() {
        XCTAssertEqual(bot(unread: true).unreadThreadIds, ["selected"])
        XCTAssertEqual(bot(unread: false).unreadThreadIds, [])
        XCTAssertEqual(bot(unread: true, tasks: []).unreadThreadIds, ["selected"])
    }

    func testATaskFlagWinsAndAnUnflaggedTaskFollowsTheBotOnlyWhenSelected() {
        let tasks = [task("selected", unread: nil), task("filed", unread: true), task("other", unread: nil), task("done", unread: false)]
        XCTAssertEqual(bot(unread: true, tasks: tasks).unreadThreadIds, ["selected", "filed"])
        XCTAssertEqual(bot(unread: false, tasks: tasks).unreadThreadIds, ["filed"])
    }
}
