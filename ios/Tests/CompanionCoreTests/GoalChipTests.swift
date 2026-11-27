import XCTest
@testable import CompanionCore

final class GoalChipTests: XCTestCase {
    private func run(_ id: String, status: String, goal: String) -> Message {
        var message = Message(id: id, role: .bot, kind: .unknown, at: 1)
        message.goalRun = GoalRunCard(runId: id, goal: goal, status: status)
        return message
    }

    func testARunningGoalShowsItsWords() {
        XCTAssertEqual(GoalChip.text([run("a", status: "working", goal: "Ship the report")]), "Ship the report")
    }

    func testAFinishedGoalOrNoGoalShowsNothing() {
        XCTAssertNil(GoalChip.text([run("a", status: "completed", goal: "Ship the report")]))
        XCTAssertNil(GoalChip.text([]))
    }

    func testTheNewestRunningGoalWins() {
        let messages = [run("a", status: "working", goal: "Old"), run("b", status: "working", goal: "New")]
        XCTAssertEqual(GoalChip.text(messages), "New")
    }

    func testALongGoalIsCut() {
        let text = GoalChip.text([run("a", status: "working", goal: String(repeating: "x", count: 200))])
        XCTAssertLessThanOrEqual(text?.count ?? 99, 36)
    }

    func testAGoalRunCardDecodesLeniently() throws {
        let json = #"{"runId":"r","goal":"Ship","status":"working","turnCount":2,"coordinatorName":"Scout"}"#
        let card = try JSONDecoder().decode(GoalRunCard.self, from: Data(json.utf8))
        XCTAssertEqual(card.status, "working")
        XCTAssertTrue(card.isWorking)
    }
}
