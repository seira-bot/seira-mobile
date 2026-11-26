import XCTest
@testable import CompanionCore

final class HomeLogicTests: XCTestCase {
    private func makeBot(_ id: String, name: String, title: String = "Helper", hidden: Bool? = nil) -> Bot {
        var bot = Bot(
            id: id, threadId: "\(id)-thread", name: name, title: title,
            description: "", notifications: true, color: "blue", unread: false,
            modelSelection: ModelSelection(instanceId: "i", model: "m"), createdAt: 1
        )
        bot.hidden = hidden
        return bot
    }

    func testChatFilterShowsTheRightKinds() {
        XCTAssertTrue(HomeChatFilter.all.showsBots)
        XCTAssertTrue(HomeChatFilter.all.showsGroups)
        XCTAssertTrue(HomeChatFilter.bots.showsBots)
        XCTAssertFalse(HomeChatFilter.bots.showsGroups)
        XCTAssertFalse(HomeChatFilter.groups.showsBots)
        XCTAssertTrue(HomeChatFilter.groups.showsGroups)
    }

    func testSearchFilterShowsTheRightSections() {
        XCTAssertTrue(HomeSearchFilter.all.showsMessages && HomeSearchFilter.all.showsRoutines)
        XCTAssertTrue(HomeSearchFilter.messages.showsMessages)
        XCTAssertFalse(HomeSearchFilter.messages.showsBots)
        XCTAssertTrue(HomeSearchFilter.bots.showsBots)
        XCTAssertFalse(HomeSearchFilter.bots.showsGroups)
        XCTAssertTrue(HomeSearchFilter.groups.showsGroups)
        XCTAssertTrue(HomeSearchFilter.routines.showsRoutines)
        XCTAssertFalse(HomeSearchFilter.routines.showsMessages)
    }

    func testBotSearchMatchesNameAndTitleAndSkipsHiddenBots() {
        let bots = [
            makeBot("a", name: "Ada", title: "Researcher"),
            makeBot("b", name: "Ben", title: "Designer"),
            makeBot("c", name: "Adam", hidden: true),
        ]
        XCTAssertEqual(HomeSearch.bots(bots, query: "ad").map(\.id), ["a"])
        XCTAssertEqual(HomeSearch.bots(bots, query: "design").map(\.id), ["b"])
        XCTAssertTrue(HomeSearch.bots(bots, query: "  ").isEmpty)
    }

    func testAllowAndDenyComeFromTheCard() {
        let card = OptionCard(
            title: "Run?", subtitle: "", options: ["Always allow", "Allow", "Deny"],
            requestId: "r1", tool: "Bash", requestType: "permission"
        )
        XCTAssertEqual(InboxLogic.action(for: card), .decide(allow: "Allow", deny: "Deny"))
    }

    func testAQuestionIsAnsweredInTheChat() {
        let question = OptionCard(title: "Which?", subtitle: "", options: ["First", "Second"], requestId: "q1")
        XCTAssertEqual(InboxLogic.action(for: question), .answerInChat)
        XCTAssertEqual(InboxLogic.action(for: nil), .answerInChat)
    }

    func testAnAnsweredCardOffersNoDecision() {
        var card = OptionCard(title: "Run?", subtitle: "", options: ["Allow", "Deny"], requestId: "r1", tool: "Bash")
        card.answered = "Allow"
        XCTAssertEqual(InboxLogic.action(for: card), .answerInChat)
    }

    func testNewBotNeedsAName() {
        XCTAssertFalse(NewBotDraft(name: "   ", job: "Writes").canCreate)
        XCTAssertTrue(NewBotDraft(name: "Mira").canCreate)
    }

    func testNewBotSpecCarriesNameAndJob() {
        let spec = NewBotDraft(name: " Mira ", job: " Writes copy ").spec
        XCTAssertEqual(spec.name, "Mira")
        XCTAssertEqual(spec.title, "Writes copy")
        XCTAssertNil(NewBotDraft(name: "Mira", job: " ").spec.title)
    }
}
