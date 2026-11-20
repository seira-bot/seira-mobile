import XCTest
@testable import CompanionCore

final class AlertAnswerTests: XCTestCase {
    private let permission = """
    {"title":"Run shell?","subtitle":"echo hi","options":["Allow","Deny"],
     "requestId":"req-1","tool":"Bash","requestType":"permission"}
    """

    /// One bot, one thread `t-1`, one message that carries `card`.
    private func fleetState(card: String) throws -> CompanionState {
        let json = """
        {"bots":[{"id":"bot-1","threadId":"t-1","name":"Scout","title":"","description":"",
          "notifications":true,"color":"green","unread":false,
          "modelSelection":{"instanceId":"","model":""},"createdAt":1,
          "tasks":[{"threadId":"t-1","title":"Task","createdAt":1}],
          "messages":[{"id":"m-1","at":10,"parentId":null,"role":"bot","kind":"options","card":\(card)}],
          "activeLeafId":"m-1"}],"groups":[]}
        """
        let fleet = try JSONDecoder().decode(Fleet.self, from: Data(json.utf8))
        var state = CompanionState()
        state.hydrate(fleet)
        return state
    }

    private func target(
        connectionId: String = "conn-1", threadId: String = "t-1", requestId: String = "req-1"
    ) throws -> AlertAnswerTarget {
        let frame = NotificationFrame(
            kind: "approval", botId: "bot-1", botName: "Scout", threadId: threadId,
            title: "Scout needs approval", body: "echo hi", requestId: requestId
        )
        return try XCTUnwrap(AlertAnswerTarget(notification: frame, connectionId: connectionId))
    }

    // MARK: - The match rule

    func testTheSameRequestInTheSameThreadOnTheSameConnectionAnswers() throws {
        let state = try fleetState(card: permission)
        XCTAssertEqual(state.alertAnswerDecision(for: try target(), activeConnectionId: "conn-1"), .answer)
    }

    func testAnotherRequestIdOpensTheChat() throws {
        let state = try fleetState(card: permission)
        XCTAssertEqual(
            state.alertAnswerDecision(for: try target(requestId: "req-2"), activeConnectionId: "conn-1"),
            .openChat
        )
    }

    func testAnotherThreadOpensTheChat() throws {
        let state = try fleetState(card: permission)
        XCTAssertEqual(
            state.alertAnswerDecision(for: try target(threadId: "t-2"), activeConnectionId: "conn-1"),
            .openChat
        )
    }

    func testAnotherOrUnknownConnectionOpensTheChat() throws {
        let state = try fleetState(card: permission)
        XCTAssertEqual(state.alertAnswerDecision(for: try target(), activeConnectionId: "conn-2"), .openChat)
        XCTAssertEqual(state.alertAnswerDecision(for: try target(), activeConnectionId: nil), .openChat)
    }

    func testAnEmptyStateOpensTheChat() throws {
        XCTAssertEqual(
            CompanionState().alertAnswerDecision(for: try target(), activeConnectionId: "conn-1"),
            .openChat
        )
    }

    func testASettledCardOpensTheChat() throws {
        let settled = """
        {"title":"Run shell?","subtitle":"echo hi","options":["Allow","Deny"],
         "requestId":"req-1","tool":"Bash","requestType":"permission","answered":"Allow"}
        """
        let state = try fleetState(card: settled)
        XCTAssertEqual(state.alertAnswerDecision(for: try target(), activeConnectionId: "conn-1"), .openChat)
    }

    func testAQuestionCardOpensTheChatEvenWithTheSameRequestId() throws {
        let question = """
        {"title":"Which?","subtitle":"","options":["First","Second"],
         "requestId":"req-1","requestType":"question"}
        """
        let state = try fleetState(card: question)
        XCTAssertEqual(state.alertAnswerDecision(for: try target(), activeConnectionId: "conn-1"), .openChat)
    }

    func testASkillCardOpensTheChatEvenWithTheSameRequestId() throws {
        let skill = """
        {"title":"Enable skill?","subtitle":"Files expenses.","options":["Enable","Deny"],
         "requestId":"req-1","tool":"stage_skill",
         "skillRequest":{"version":1,"requestId":"req-1","botId":"bot-1","threadId":"t-1",
          "stagedId":"staged-1","action":"create","name":"file-expense","gist":"Files expenses.",
          "warnings":[],"createdAt":1}}
        """
        let state = try fleetState(card: skill)
        XCTAssertEqual(state.alertAnswerDecision(for: try target(), activeConnectionId: "conn-1"), .openChat)
    }

    func testAnOutboundCardOpensTheChatEvenWithTheSameRequestId() throws {
        let outbound = """
        {"title":"Send on your behalf?","subtitle":"Gmail","options":["Allow","Deny"],
         "requestId":"req-1","tool":"GMAIL_SEND_EMAIL",
         "outboundRequest":{"tool":"GMAIL_SEND_EMAIL","app":"Gmail"}}
        """
        let state = try fleetState(card: outbound)
        XCTAssertEqual(state.alertAnswerDecision(for: try target(), activeConnectionId: "conn-1"), .openChat)
    }

    func testACardThatOffersOnlyAStandingGrantOpensTheChat() throws {
        let grantOnly = """
        {"title":"Run shell?","subtitle":"echo hi","options":["Always allow","Deny"],
         "requestId":"req-1","tool":"Bash","requestType":"permission"}
        """
        let state = try fleetState(card: grantOnly)
        XCTAssertEqual(state.alertAnswerDecision(for: try target(), activeConnectionId: "conn-1"), .openChat)
    }

    // MARK: - Which alerts carry a target

    func testOnlyAnApprovalThatNamesItsRequestCarriesATarget() {
        func frame(kind: String, requestId: String?) -> NotificationFrame {
            NotificationFrame(
                kind: kind, botId: "bot-1", botName: "Scout", threadId: "t-1",
                title: "Title", body: "Body", requestId: requestId
            )
        }
        XCTAssertNotNil(AlertAnswerTarget(notification: frame(kind: "approval", requestId: "req-1"), connectionId: "conn-1"))
        XCTAssertNil(AlertAnswerTarget(notification: frame(kind: "approval", requestId: nil), connectionId: "conn-1"))
        XCTAssertNil(AlertAnswerTarget(notification: frame(kind: "approval", requestId: " "), connectionId: "conn-1"))
        XCTAssertNil(AlertAnswerTarget(notification: frame(kind: "approval", requestId: "req-1"), connectionId: nil))
        for kind in ["question", "secret", "sign-in", "done", "routine-failed"] {
            XCTAssertNil(
                AlertAnswerTarget(notification: frame(kind: kind, requestId: "req-1"), connectionId: "conn-1"),
                "\(kind) must open the chat"
            )
        }
    }

    func testTheTargetSurvivesTheAlertsUserInfo() throws {
        let original = try target()
        XCTAssertEqual(AlertAnswerTarget(payload: original.payload), original)
        XCTAssertEqual(original.notificationTarget, NotificationTarget(botId: "bot-1", threadId: "t-1"))
    }

    func testAUserInfoWithoutTheRequestOrTheConnectionCarriesNoTarget() throws {
        var payload = try target().payload
        payload["requestId"] = nil
        XCTAssertNil(AlertAnswerTarget(payload: payload))

        payload = try target().payload
        payload["connectionId"] = nil
        XCTAssertNil(AlertAnswerTarget(payload: payload))

        payload = try target().payload
        payload["kind"] = "question"
        XCTAssertNil(AlertAnswerTarget(payload: payload))
    }

    func testEachChoiceMapsToOneBehavior() {
        XCTAssertEqual(AlertChoice.approve.behavior, "allow")
        XCTAssertEqual(AlertChoice.deny.behavior, "deny")
    }
}
