import XCTest
@testable import CompanionCore

final class GlanceContentTests: XCTestCase {
    private func fixture(card: OptionCard) -> CompanionState {
        let bot = Bot(
            id: "a", threadId: "a-thread", name: "Bot a", title: "Helper",
            description: "", notifications: true, color: "blue", unread: false,
            modelSelection: ModelSelection(instanceId: "i", model: "m"), createdAt: 1
        )
        var message = Message(id: "m1", role: .bot, kind: .options, at: 1)
        message.card = card
        var state = CompanionState()
        state.bots = [bot]
        state.messages = ["a-thread": [message]]
        return state
    }

    private func permission(_ detail: String, requestId: String = "r1") -> OptionCard {
        OptionCard(
            title: "Approval needed", subtitle: detail, options: ["Allow", "Deny"],
            requestId: requestId, tool: "Bash", requestType: "permission"
        )
    }

    func testAnApprovalWithAShortDetailCanBeDeniedAndAllowedOnce() {
        let state = fixture(card: permission("git push"))
        XCTAssertTrue(GlanceContent.canAnswer(state: state, botId: "a", requestId: "r1", allow: true))
        XCTAssertTrue(GlanceContent.canAnswer(state: state, botId: "a", requestId: "r1", allow: false))
    }

    func testAStaleRequestCannotBeAnswered() {
        let state = fixture(card: permission("git push", requestId: "new"))
        XCTAssertFalse(GlanceContent.canAnswer(state: state, botId: "a", requestId: "old", allow: true))
        XCTAssertFalse(GlanceContent.canAnswer(state: state, botId: "a", requestId: "old", allow: false))
        XCTAssertFalse(GlanceContent.canAnswer(state: state, botId: "other", requestId: "new", allow: false))
    }

    func testAnEmptyDetailCannotBeAllowedButCanBeDenied() {
        let state = fixture(card: permission("  "))
        XCTAssertFalse(GlanceContent.canAnswer(state: state, botId: "a", requestId: "r1", allow: true))
        XCTAssertTrue(GlanceContent.canAnswer(state: state, botId: "a", requestId: "r1", allow: false))
    }

    func testADetailThatDoesNotFitTheSurfaceCannotBeAllowedButCanBeDenied() {
        let state = fixture(card: permission(String(repeating: "a", count: 28 * 4 + 1)))
        XCTAssertFalse(GlanceContent.canAnswer(state: state, botId: "a", requestId: "r1", allow: true))
        XCTAssertTrue(GlanceContent.canAnswer(state: state, botId: "a", requestId: "r1", allow: false))
        let fits = fixture(card: permission(String(repeating: "a", count: 28 * 4)))
        XCTAssertTrue(GlanceContent.canAnswer(state: fits, botId: "a", requestId: "r1", allow: true))
    }

    func testAQuestionAndAReviewCardCannotBeAnswered() {
        var question = permission("Which file?")
        question.tool = nil
        question.requestType = "question"
        var skill = permission("skill")
        skill.tool = nil
        for card in [question, skill] {
            let state = fixture(card: card)
            XCTAssertFalse(GlanceContent.canAnswer(state: state, botId: "a", requestId: "r1", allow: true))
            XCTAssertFalse(GlanceContent.canAnswer(state: state, botId: "a", requestId: "r1", allow: false))
        }
    }

    func testTheContentOffersButtonsFromTheFocusAsk() throws {
        let focus = try XCTUnwrap(Glance.step(GlanceMemory(), bots: Glance.bots(fixture(card: permission("git push"))), now: 0).1.focus)
        let content = GlanceContent(entry: focus)
        XCTAssertEqual(content.state, .needs)
        XCTAssertEqual(content.detail, "git push")
        XCTAssertTrue(content.showsDeny)
        XCTAssertTrue(content.showsAllow)

        let failed = GlanceContent(entry: focus, couldNotSend: true)
        XCTAssertFalse(failed.showsDeny)
        XCTAssertFalse(failed.showsAllow)
        XCTAssertTrue(failed.couldNotSend)

        let long = try XCTUnwrap(Glance.step(GlanceMemory(), bots: Glance.bots(fixture(card: permission(String(repeating: "a", count: 200)))), now: 0).1.focus)
        let cut = GlanceContent(entry: long)
        XCTAssertEqual(cut.detail, "")
        XCTAssertTrue(cut.showsDeny)
        XCTAssertFalse(cut.showsAllow)
    }

    func testAFullSizeContentEncodesUnderFourKilobytes() throws {
        let ask = GlanceAsk(
            kind: .approval, requestId: String(repeating: "r", count: 64), threadId: String(repeating: "t", count: 64),
            title: String(repeating: "あ", count: Glance.titleLimit), detail: String(repeating: "い", count: Glance.detailLimit), at: 1
        )
        let bot = GlanceBot(
            botId: String(repeating: "b", count: 64), threadId: String(repeating: "t", count: 64),
            name: String(repeating: "う", count: Glance.nameLimit), color: "blue", state: .needs, line: "", ask: ask,
            mascotBody: "cursor", mascotFaceColor: "white", mascotAccessories: Array(repeating: "accessory-name", count: 6)
        )
        let content = GlanceContent(entry: GlanceEntry(bot: bot, startedAt: 0), budget: GlanceBudget(lineChars: 1_000, lines: 10))
        XCTAssertEqual(content.detail.unicodeScalars.count, Glance.detailLimit)
        let size = try JSONEncoder().encode(content).count
        XCTAssertLessThan(size, 4_096)
    }
}
