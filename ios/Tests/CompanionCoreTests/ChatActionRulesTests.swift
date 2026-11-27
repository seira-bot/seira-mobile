import XCTest
@testable import CompanionCore

final class ChatActionRulesTests: XCTestCase {
    private func text(_ id: String, role: Message.Role = .bot, turn: String? = nil, terminal: Bool? = nil) -> Message {
        var message = Message(id: id, role: role, kind: .text, at: 1)
        message.text = "Words"
        message.turnId = turn
        message.turnTerminal = terminal
        return message
    }

    private func step(_ id: String, ok: Bool? = true) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 1)
        message.tool = ToolActivity(name: id, ok: ok)
        return message
    }

    // MARK: - Message row

    func testTheRowShowsUnderAFinishedBotReply() {
        XCTAssertTrue(MessageActionRules.showsRow(text("a")))
        XCTAssertTrue(MessageActionRules.showsRow(text("b", turn: "t", terminal: true)))
    }

    func testNarrationInARunningTurnHasNoRow() {
        XCTAssertFalse(MessageActionRules.showsRow(text("a", turn: "t", terminal: false)))
        XCTAssertFalse(MessageActionRules.showsRow(text("a", turn: "t", terminal: nil)))
    }

    func testAPersonsOwnMessageAndAStepHaveNoRow() {
        XCTAssertFalse(MessageActionRules.showsRow(text("a", role: .user)))
        XCTAssertFalse(MessageActionRules.showsRow(step("s")))
    }

    // MARK: - Approval buttons

    private func card(_ options: [String], tool: String? = "Bash") -> OptionCard {
        var card = OptionCard(title: "Approval needed", subtitle: "git status", options: options)
        card.requestId = "req"
        card.tool = tool
        card.allowKey = "Bash:git"
        return card
    }

    func testAlwaysAllowShowsOnceWhenTheSeparateButtonIsOffered() {
        let approval = card(["Allow", "Always allow", "Deny"])
        XCTAssertEqual(ApprovalButtons.options(approval, showsAlwaysButton: true), ["Allow", "Deny"])
        XCTAssertEqual(ApprovalButtons.options(approval, showsAlwaysButton: false), ["Allow", "Always allow", "Deny"])
    }

    func testAlwaysAllowStaysWhenItIsTheOnlyWayToSayYes() {
        let approval = card(["Always allow", "Deny"])
        XCTAssertEqual(ApprovalButtons.options(approval, showsAlwaysButton: true), ["Always allow", "Deny"])
    }

    func testAStackedCardKeepsEveryOption() {
        var question = card(["Allow", "Always allow", "Deny"], tool: nil)
        question.requestType = "question"
        XCTAssertTrue(question.stacksOptions)
        XCTAssertEqual(
            ApprovalButtons.options(question, showsAlwaysButton: true),
            ["Allow", "Always allow", "Deny"]
        )
    }

    // MARK: - Work fold

    func testTwoFinishedStepsFoldAndOneStaysAlone() {
        let rows = WorkFold.foldSteps([.message(step("a")), .message(step("b")), .message(text("t")), .message(step("c"))])
        XCTAssertEqual(rows.map(\.id), ["run.a", "t", "c"])
    }

    func testAFailureNeverFolds() {
        let rows = WorkFold.foldSteps([.message(step("a")), .message(step("x", ok: false)), .message(step("b"))])
        XCTAssertEqual(rows.map(\.id), ["a", "x", "b"])
    }

    func testOnlyFullDetailFoldsAndStartsOpen() {
        let messages = [step("a"), step("b")]
        XCTAssertEqual(WorkFold.rows(messages, detail: .full).map(\.id), ["run.a"])
        XCTAssertEqual(WorkFold.rows(messages, detail: .hidden).map(\.id), [])
        XCTAssertTrue(WorkFold.startsOpen(.full))
        XCTAssertFalse(WorkFold.startsOpen(.reduced))
    }

    // MARK: - Computers

    func testBotScreensListCloudBotsWithTheirState() {
        func bot(_ id: String, computer: String?, busy: Bool? = nil, waiting: Bool? = nil, hidden: Bool? = nil) -> Bot {
            var bot = Bot(
                id: id, threadId: "t", name: id, title: "", description: "", notifications: true,
                color: "blue", unread: false, modelSelection: ModelSelection(instanceId: "i", model: "m"), createdAt: 1
            )
            bot.computer = computer
            bot.busy = busy
            bot.waitingOnTeammate = waiting
            bot.hidden = hidden
            return bot
        }
        let entries = ComputersRules.botScreens([
            bot("a", computer: "cloud", busy: true),
            bot("b", computer: "cloud", waiting: true),
            bot("c", computer: "cloud"),
            bot("d", computer: nil),
            bot("e", computer: "cloud", hidden: true),
        ])
        XCTAssertEqual(entries.map(\.id), ["a", "b", "c"])
        XCTAssertEqual(entries.map(\.state), [.working, .waiting, .idle])
    }
}
