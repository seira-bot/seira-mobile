import Foundation
import XCTest
@testable import CompanionCore

final class DepartmentSectionsTests: XCTestCase {
    private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try JSONDecoder().decode(type, from: Data(json.utf8))
    }

    private func bot(_ id: String, section: String? = nil) throws -> Bot {
        let sectionField = section.map { #","section":"\#($0)""# } ?? ""
        let json = #"""
        {"id":"\#(id)","threadId":"t-\#(id)","name":"\#(id)","title":"","description":"",
         "notifications":true,"color":"blue","unread":false\#(sectionField),
         "modelSelection":{"instanceId":"i","model":"m"},"createdAt":1}
        """#
        return try decode(Bot.self, json)
    }

    func testTheFleetCarriesTheDepartmentNamesInOrder() throws {
        let fleet = try decode(Fleet.self, #"{"bots":[],"groups":[],"sections":["Work","Empty","Content"]}"#)
        XCTAssertEqual(fleet.sections, ["Work", "Empty", "Content"])
    }

    func testAnOlderComputerLeavesTheNamesAbsent() throws {
        let fleet = try decode(Fleet.self, #"{"bots":[],"groups":[]}"#)
        XCTAssertNil(fleet.sections)
    }

    func testAListThatIsNotNamesReadsAsAbsent() throws {
        let fleet = try decode(Fleet.self, #"{"bots":[],"groups":[],"sections":["Work",7]}"#)
        XCTAssertNil(fleet.sections)
    }

    func testTheSectionsFrameDecodes() throws {
        let stream = try decode(StreamFrame.self, #"{"kind":"sections","sections":["A","B"],"seq":4}"#)
        guard case let .sections(names) = stream.frame else { return XCTFail("expected .sections, got \(stream.frame)") }
        XCTAssertEqual(names, ["A", "B"])
        XCTAssertEqual(stream.seq, 4)
    }

    func testABrokenSectionsFrameIsUnknown() throws {
        let stream = try decode(StreamFrame.self, #"{"kind":"sections","sections":"A"}"#)
        guard case let .unknown(kind) = stream.frame else { return XCTFail("expected .unknown, got \(stream.frame)") }
        XCTAssertEqual(kind, "sections")
    }

    func testTheAssignAnswerCarriesTheNames() throws {
        let response = try decode(
            SidebarSectionResponse.self,
            #"{"section":"Research","sections":["Research","Empty"],"bots":[]}"#
        )
        XCTAssertEqual(response.sections, ["Research", "Empty"])
    }

    func testTheAssignAnswerWithoutNamesLeavesThemAbsent() throws {
        let response = try decode(SidebarSectionResponse.self, #"{"section":"Research","bots":[]}"#)
        XCTAssertNil(response.sections)
    }

    func testTheAssignAnswerWithBrokenNamesStillGivesTheSection() throws {
        let response = try decode(SidebarSectionResponse.self, #"{"section":"Research","sections":"x","bots":[]}"#)
        XCTAssertNil(response.sections)
        XCTAssertEqual(response.section, "Research")
    }

    func testAnEmptyDepartmentShowsInTheSidebarSections() {
        var state = CompanionState()
        state.hydrate(Fleet(bots: [], groups: [], sections: ["Work", "Empty"]))
        XCTAssertEqual(state.sidebarSections.map(\.name), ["Work", "Empty"])
        XCTAssertEqual(state.sidebarSections.last?.bots, [])
    }

    func testAFleetWithoutNamesKeepsTheNamesAlreadyKnown() {
        var state = CompanionState()
        state.apply(.sections(["Work"]))
        state.hydrate(Fleet(bots: [], groups: []))
        XCTAssertEqual(state.declaredSections, ["Work"])
    }

    func testTheFrameReplacesTheNamesAndDropsBlankAndRepeatedOnes() {
        var state = CompanionState()
        state.apply(.sections(["Work", "  ", "Work", " Ops "]))
        XCTAssertEqual(state.declaredSections, ["Work", "Ops"])
    }

    func testADepartmentWithBotsComesAfterTheListedNamesWithoutRepeating() throws {
        var state = CompanionState()
        state.apply(.sections(["Work"]))
        state.apply(.bot(try bot("b1", section: "Work")))
        state.apply(.bot(try bot("b2", section: "Sales")))
        XCTAssertEqual(state.sidebarSections.map(\.name), ["Work", "Sales"])
        XCTAssertEqual(state.sidebarSections.first?.bots.map(\.id), ["b1"])
    }
}
