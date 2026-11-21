import XCTest
@testable import CompanionCore

final class CloudComputerNoticeTests: XCTestCase {
    // The error text of desktop releases before 0.1.186, which the phone must still recognise.
    private let olderServerError =
        "error: Bots here can get their own cloud desktops from Boat, a paid service. "
        + "Connect your Boat account in AI › Other services."

    private func failed(_ id: String, name: String? = nil, place: String? = nil, partial: Bool? = nil) -> Message {
        var message = Message(id: id, role: .bot, kind: .activity, at: 1)
        message.tool = ToolActivity(
            name: name ?? olderServerError, ok: false, partial: partial,
            place: place.map { FailedPlace(state: $0) }
        )
        return message
    }

    private func user(_ id: String) -> Message {
        var message = Message(id: id, role: .user, kind: .text, at: 1)
        message.text = "halo"
        return message
    }

    private func reply(_ id: String) -> Message {
        var message = Message(id: id, role: .bot, kind: .text, at: 1)
        message.text = "hi"
        return message
    }

    func testAnOlderServersErrorTextIsRecognisedAndWordedWithoutTheProvider() throws {
        let tool = try XCTUnwrap(failed("f1").tool)
        XCTAssertTrue(CloudComputerNotice.matches(tool))
        XCTAssertEqual(tool.label, CloudComputerNotice.text)
        XCTAssertFalse(tool.label.contains("Boat"))
    }

    func testTheStoredPlaceStateAloneIsEnough() throws {
        let tool = try XCTUnwrap(failed("f1", name: "error: something reworded", place: "cc-needs-key").tool)
        XCTAssertTrue(CloudComputerNotice.matches(tool))
    }

    func testOtherFailuresAndOrdinaryRowsAreLeftAlone() throws {
        let other = ToolActivity(name: "error: Claude is overloaded right now.", ok: false)
        XCTAssertFalse(CloudComputerNotice.matches(other))
        XCTAssertEqual(other.label, "Claude is overloaded right now.")
        XCTAssertFalse(CloudComputerNotice.matches(ToolActivity(name: "Connect your account", ok: true)))
        XCTAssertFalse(CloudComputerNotice.matches(try XCTUnwrap(failed("f2", name: "error: asleep", place: "cc-asleep").tool)))
    }

    func testOneNoticePerThreadWhileMessagesKeepFailing() {
        let thread = [user("u1"), failed("f1"), user("u2"), failed("f2"), user("u3"), failed("f3")]
        XCTAssertEqual(CloudComputerNotice.repeatedIds(thread), ["f2", "f3"])
    }

    func testAFailureAfterTheBotAnsweredIsAnnouncedAgain() {
        let thread = [failed("f1"), user("u2"), failed("f2"), user("u3"), reply("r1"), user("u4"), failed("f3"), failed("f4")]
        XCTAssertEqual(CloudComputerNotice.repeatedIds(thread), ["f2", "f4"])
    }

    func testOtherFailuresNeitherCountNorBreakARun() {
        var other = Message(id: "x", role: .bot, kind: .activity, at: 1)
        other.tool = ToolActivity(name: "error: offline", ok: false)
        XCTAssertEqual(CloudComputerNotice.repeatedIds([failed("f1"), other, failed("f2")]), ["f2"])
    }

    func testTheTranscriptDropsTheRepeatedNotices() {
        let rows = transcriptRows([user("u1"), failed("f1"), user("u2"), failed("f2")], detail: .full)
        let ids: [String] = rows.compactMap {
            if case let .message(message) = $0 { return message.id }
            return nil
        }
        XCTAssertEqual(ids, ["u1", "f1", "u2"])
    }

    func testANotificationBodyWithTheOldErrorUsesTheSameWords() {
        let body = String(olderServerError.dropFirst("error: ".count))
        XCTAssertEqual(CloudComputerNotice.words(body), CloudComputerNotice.text)
        XCTAssertEqual(CloudComputerNotice.words("Done"), "Done")
    }

    func testTheChipsStayAwayUntilTheBotAnswers() {
        XCTAssertTrue(lastTurnFailed(in: [user("u1"), failed("f1")]))
        XCTAssertTrue(lastTurnFailed(in: [failed("f1"), user("u2")]))
        XCTAssertFalse(lastTurnFailed(in: [failed("f1"), user("u2"), reply("r1")]))
        XCTAssertFalse(lastTurnFailed(in: [user("u1"), reply("r1")]))
        XCTAssertFalse(lastTurnFailed(in: []))
    }

    func testATurnWhoseReplyCameBackDidNotFail() {
        XCTAssertFalse(lastTurnFailed(in: [user("u1"), reply("r1"), failed("p1", partial: true)]))
    }

    func testThePlaceStateDecodesAndItsAbsenceDoes() throws {
        let with = Data(#"{"name":"error: x","ok":false,"place":{"state":"cc-needs-key","params":{},"source":"harness"}}"#.utf8)
        let without = Data(#"{"name":"error: x","ok":false}"#.utf8)
        XCTAssertEqual(try JSONDecoder().decode(ToolActivity.self, from: with).place?.state, "cc-needs-key")
        XCTAssertNil(try JSONDecoder().decode(ToolActivity.self, from: without).place)
    }

    private let serverNotice = Data("""
    {"id":"n1","role":"bot","kind":"activity","at":1,
     "tool":{"name":"error: Scout answered without a computer because no cloud desktop is set up.",
             "ok":false,"notice":true,
             "place":{"state":"cc-not-set-up","params":{"bot":"Scout"},"source":"works-on"}}}
    """.utf8)

    func testANoticeFromAServerThatRanTheTurnIsNotAFailure() throws {
        let message = try JSONDecoder().decode(Message.self, from: serverNotice)
        XCTAssertEqual(message.tool?.place?.state, "cc-not-set-up")
        XCTAssertTrue(isTurnNotice(message))
        XCTAssertFalse(isFailedTurn(message))
        XCTAssertEqual(message.tool?.statusOk, true)
        XCTAssertEqual(message.tool?.label, "Scout answered without a computer because no cloud desktop is set up.")
        XCTAssertFalse(lastTurnFailed(in: [user("u1"), message]))
    }

    func testANoticeStaysInTheTranscriptWhenActivityIsHidden() throws {
        let message = try JSONDecoder().decode(Message.self, from: serverNotice)
        let rows = transcriptRows([user("u1"), message, reply("r1")], detail: .hidden)
        let ids: [String] = rows.compactMap {
            if case let .message(row) = $0 { return row.id }
            return nil
        }
        XCTAssertEqual(ids, ["u1", "n1", "r1"])
    }
}
