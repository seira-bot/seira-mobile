import Foundation
import XCTest
@testable import CompanionCore

/// A change of computer installs a fresh state. The operations added in 1.2.0 (pin,
/// mark unread, move to a department) fold their answer into that state, so the
/// fresh state must hold nothing of the old computer, and one answer must touch
/// only the bot it names.
final class RuntimeStateIsolationTests: XCTestCase {
    private func bot(
        _ id: String,
        section: String? = nil,
        approvalMode: String? = nil,
        pinned: Bool? = nil
    ) throws -> Bot {
        let sectionField = section.map { #","section":"\#($0)""# } ?? ""
        let approvalField = approvalMode.map { #","approvalMode":"\#($0)""# } ?? ""
        let pinnedField = pinned.map { #","pinned":\#($0)"# } ?? ""
        let json = #"""
        {"id":"\#(id)","threadId":"t-\#(id)","name":"\#(id)","title":"","description":"",
         "notifications":true,"color":"blue","unread":false\#(sectionField)\#(approvalField)\#(pinnedField),
         "modelSelection":{"instanceId":"i","model":"m"},"createdAt":1}
        """#
        return try JSONDecoder().decode(Bot.self, from: Data(json.utf8))
    }

    func testAFreshStateHoldsNothingOfTheOldComputer() throws {
        var old = CompanionState()
        old.apply(.sections(["Ops"]))
        old.apply(.bot(try bot("b1", section: "Ops", approvalMode: "full")))
        XCTAssertEqual(old.declaredSections, ["Ops"])
        XCTAssertEqual(old.bot("b1")?.approvalMode, "full")

        let next = CompanionState()

        XCTAssertNil(next.bot("b1"), "no bot of the old computer")
        XCTAssertTrue(next.bots.isEmpty)
        XCTAssertTrue(next.declaredSections.isEmpty, "no department name of the old computer")
        XCTAssertTrue(next.sidebarSections.isEmpty)
    }

    func testAnAnswerForOneBotKeepsTheApprovalLevelOfEveryOtherBot() throws {
        var state = CompanionState()
        state.apply(.bot(try bot("b1", approvalMode: "ask")))
        state.apply(.bot(try bot("b2", approvalMode: "full")))

        state.apply(.bot(try bot("b1", approvalMode: "ask", pinned: true)))

        XCTAssertEqual(state.bot("b1")?.pinned, true)
        XCTAssertEqual(state.bot("b1")?.approvalMode, "ask")
        XCTAssertEqual(state.bot("b2")?.approvalMode, "full")
        XCTAssertNotEqual(state.bot("b2")?.pinned, true)
    }

    func testAMoveToADepartmentKeepsTheApprovalLevelOfTheMovedBot() throws {
        var state = CompanionState()
        state.apply(.bot(try bot("b1", approvalMode: "ask")))

        state.apply(.bot(try bot("b1", section: "Ops", approvalMode: "ask")))
        state.apply(.sections(["Ops"]))

        XCTAssertEqual(state.bot("b1")?.section, "Ops")
        XCTAssertEqual(state.bot("b1")?.approvalMode, "ask")
        XCTAssertEqual(state.sidebarSections.map(\.name), ["Ops"])
    }
}
