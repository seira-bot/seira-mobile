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

    func testEveryOptionShowsItsOwnText() {
        let card = OptionCard(
            title: "Run?", subtitle: "", options: ["Always allow", "Allow once", "Deny"],
            requestId: "r1", tool: "Bash", requestType: "permission"
        )
        XCTAssertEqual(
            InboxLogic.action(for: card),
            .options(labels: ["Always allow", "Allow once", "Deny"], primary: "Allow once")
        )
    }

    func testPlainAllowIsPrimary() {
        let card = OptionCard(
            title: "Run?", subtitle: "", options: ["Always allow", "Allow", "Deny"],
            requestId: "r1", tool: "Bash", requestType: "permission"
        )
        XCTAssertEqual(
            InboxLogic.action(for: card),
            .options(labels: ["Always allow", "Allow", "Deny"], primary: "Allow")
        )
    }

    func testAStandingGrantIsNeverPrimary() {
        let card = OptionCard(
            title: "Run?", subtitle: "", options: ["Always allow", "Deny"],
            requestId: "r1", tool: "Bash", requestType: "permission"
        )
        XCTAssertEqual(InboxLogic.action(for: card), .options(labels: ["Always allow", "Deny"], primary: nil))
    }

    func testCardsThatNeedMoreThanAButtonGoToTheChat() {
        let base = OptionCard(title: "Run?", subtitle: "", options: ["Allow", "Deny"], requestId: "r1", tool: "Bash")
        var outbound = base
        outbound.outboundRequest = OutboundRequest(tool: "send")
        var memory = base
        memory.teamMemoryRequest = TeamMemoryRequest(section: "notes")
        var quota = base
        quota.quotaSwitch = QuotaSwitchCard(messageId: "m1", rootId: nil)
        var asked = base
        asked.questionRequest = QuestionRequestCardData(
            questions: [AskQuestion(question: "Which?", options: [AskQuestionOption(label: "One")])]
        )
        for card in [outbound, memory, quota, asked] {
            XCTAssertEqual(InboxLogic.action(for: card), .answerInChat)
        }
    }

    func testAQuestionShowsItsOwnOptions() {
        let question = OptionCard(title: "Which?", subtitle: "", options: ["First", "Second"], requestId: "q1")
        XCTAssertEqual(InboxLogic.action(for: question), .options(labels: ["First", "Second"], primary: nil))
    }

    func testAnUpdateWithoutACardGoesToTheChat() {
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
