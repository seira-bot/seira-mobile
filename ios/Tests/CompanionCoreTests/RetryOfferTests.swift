import XCTest
@testable import CompanionCore

final class RetryOfferTests: XCTestCase {
    private func text(_ id: String, _ role: Message.Role, _ body: String = "hi") -> Message {
        var message = Message(id: id, role: role, kind: .text, at: 1)
        message.text = body
        return message
    }

    private func activity(_ id: String, _ name: String, claudeUpdate: Bool? = nil) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 1)
        message.tool = ToolActivity(name: name, ok: false, claudeUpdate: claudeUpdate)
        return message
    }

    func testTheNewestFailedTurnOfAnyCauseCarriesTheOffer() {
        let messages = [text("u1", .user), activity("f1", "error: Disk full.")]
        XCTAssertEqual(retryOfferMessageId(in: messages), "f1")
    }

    func testTheNewestStoppedTurnCarriesTheOffer() {
        let messages = [text("u1", .user), activity("s1", "stopped: You stopped this.")]
        XCTAssertEqual(retryOfferMessageId(in: messages), "s1")
        XCTAssertNil(switchModelOfferMessageId(in: messages))
    }

    func testTheNewerOfAFailedAndAStoppedTurnWins() {
        XCTAssertEqual(retryOfferMessageId(in: [activity("f1", "error: x"), activity("s1", "stopped: Stopped")]), "s1")
        XCTAssertEqual(retryOfferMessageId(in: [activity("s1", "stopped: Stopped"), activity("f2", "error: x")]), "f2")
    }

    func testNoOfferOnceSomethingWasSaidAfterTheTurn() {
        XCTAssertNil(retryOfferMessageId(in: [activity("s1", "stopped: Stopped"), text("u2", .user)]))
        XCTAssertNil(retryOfferMessageId(in: [activity("f1", "error: x"), text("b2", .bot)]))
        XCTAssertNil(retryOfferMessageId(in: [text("u1", .user)]))
        XCTAssertNil(retryOfferMessageId(in: [activity("f1", "error: x", claudeUpdate: true)]))
    }

    func testABotBubbleThatIsOnlyTheAbortSentenceIsAStoppedTurn() {
        let cancelled = text("b1", .bot, "The request was cancelled by the client.")
        XCTAssertTrue(isStoppedTurn(cancelled))
        XCTAssertEqual(retryOfferMessageId(in: [text("u1", .user), cancelled]), "b1")
        XCTAssertTrue(isStoppedTurn(text("b2", .bot, "provider returned a completion error: context canceled")))
        XCTAssertFalse(isStoppedTurn(text("b3", .bot, "The request was cancelled by the client, so I kept going.")))
        XCTAssertFalse(isStoppedTurn(text("u2", .user, "The request was cancelled by the client.")))
        XCTAssertFalse(isStoppedTurn(text("b4", .bot)))
    }

    func testAFailedTurnWhoseCauseIsTheAbortSentenceIsAStoppedTurn() {
        XCTAssertTrue(isStoppedTurn(activity("s1", "error: Operation was aborted.")))
        XCTAssertFalse(isStoppedTurn(activity("s2", "error: Disk full while the operation was aborted.")))
        XCTAssertFalse(isStoppedTurn(activity("s3", "Read file")))
    }

    func testACloudComputerNoticeGetsNoTryAgain() {
        let older = activity(
            "f1",
            "error: Bots here can get their own cloud desktops from Boat, a paid service. "
                + "Connect your Boat account in AI › Other services."
        )
        XCTAssertNil(retryOfferMessageId(in: [text("u1", .user), older]))
    }

    func testLastUserMessageSkipsBotRowsAndBlankText() {
        let messages = [text("u1", .user, "first"), text("u2", .user, "second"), text("b1", .bot), text("u3", .user, "  ")]
        XCTAssertEqual(lastUserMessage(in: messages)?.id, "u2")
        XCTAssertNil(lastUserMessage(in: [text("b1", .bot)]))
    }
}
