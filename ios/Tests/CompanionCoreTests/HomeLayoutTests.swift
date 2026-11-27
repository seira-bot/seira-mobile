import Foundation
import XCTest
@testable import CompanionCore

final class HomeLayoutTests: XCTestCase {
    private func bot(_ id: String, _ extra: String = "") -> Bot {
        let json = #"""
        {"id":"\#(id)","threadId":"t-\#(id)","name":"\#(id)","title":"","description":"",
         "notifications":true,"color":"blue","unread":false\#(extra),
         "modelSelection":{"instanceId":"i","model":"m"},"createdAt":1}
        """#
        return try! JSONDecoder().decode(Bot.self, from: Data(json.utf8))
    }

    private func room(_ id: String, dm: Bool = false, section: String? = nil) -> Room {
        var extra = dm ? ",\"dm\":true" : ""
        if let section { extra += ",\"section\":\"\(section)\"" }
        let json = #"""
        {"id":"\#(id)","threadId":"t-\#(id)","name":"\#(id)","memberIds":[],
         "defaultResponder":{"kind":"auto"},"bulletin":"","unread":false,"createdAt":0\#(extra)}
        """#
        return try! JSONDecoder().decode(Room.self, from: Data(json.utf8))
    }

    private var state: CompanionState {
        var state = CompanionState()
        state.bots = [
            bot("chief", #","chiefOfStaff":true"#),
            bot("plain"),
            bot("pinned", #","pinned":true"#),
            bot("ops-bot", #","section":"Ops""#),
            bot("ops-chief", #","section":"Ops","chiefOfStaff":true"#),
            bot("hidden", #","hidden":true"#),
        ]
        state.rooms = [room("group"), room("direct", dm: true)]
        state.setDeclaredSections(["Ops", "Empty"])
        return state
    }

    func testSimpleModeShowsBotsEachDepartmentInOrderThenGroupChats() {
        let layout = HomeLayoutRules.layout(state, advanced: false, department: nil)
        XCTAssertEqual(layout.groups.map(\.key), ["bots", "department:Ops", "department:Empty", "groups"])
        XCTAssertNil(layout.chief)
    }

    func testSimpleModePutsSeiraFirstThenPinnedThenTheRest() {
        let bots = HomeLayoutRules.layout(state, advanced: false, department: nil).groups[0]
        XCTAssertEqual(bots.bots.map(\.id), ["chief", "pinned", "plain"])
    }

    func testADepartmentWithNoBotStillShows() {
        let groups = HomeLayoutRules.layout(state, advanced: false, department: nil).groups
        XCTAssertEqual(groups.first { $0.name == "Empty" }?.bots, [])
    }

    func testADepartmentListsItsChiefFirst() {
        let groups = HomeLayoutRules.layout(state, advanced: false, department: nil).groups
        XCTAssertEqual(groups.first { $0.name == "Ops" }?.bots.map(\.id), ["ops-chief", "ops-bot"])
    }

    func testADepartmentABotNamesShowsEvenWhenNotListed() {
        var other = state
        other.bots.append(bot("extra", #","section":"Sales""#))
        let names = HomeLayoutRules.layout(other, advanced: false, department: nil).groups.compactMap(\.name)
        XCTAssertEqual(names, ["Ops", "Empty", "Sales"])
    }

    func testGroupChatsHoldChannelsOutsideDepartmentsThenBotChats() {
        var other = state
        other.rooms.append(room("ops-room", section: "Ops"))
        let last = HomeLayoutRules.layout(other, advanced: false, department: nil).groups.last
        XCTAssertEqual(last?.kind, .groups)
        XCTAssertEqual(last?.rooms.map(\.id), ["group", "direct"])
    }

    func testADepartmentShowsItsChannelsAfterItsBots() {
        var other = state
        other.rooms.append(room("ops-room", section: "Ops"))
        let ops = HomeLayoutRules.layout(other, advanced: false, department: nil).groups.first { $0.name == "Ops" }
        XCTAssertEqual(ops?.rooms.map(\.id), ["ops-room"])
        XCTAssertEqual(ops?.bots.map(\.id), ["ops-chief", "ops-bot"])
    }

    func testASectionOnlyChannelsNameIsNotADepartment() {
        var other = state
        other.rooms.append(room("lone", section: "Lonely"))
        let layout = HomeLayoutRules.layout(other, advanced: false, department: nil)
        XCTAssertFalse(layout.groups.contains { $0.name == "Lonely" })
        XCTAssertFalse(layout.groups.last?.rooms.contains { $0.id == "lone" } ?? true)
    }

    func testAdvancedModeGivesTheChiefItsOwnRow() {
        let layout = HomeLayoutRules.layout(state, advanced: true, department: nil)
        XCTAssertEqual(layout.chief?.id, "chief")
        XCTAssertEqual(layout.groups[0].bots.map(\.id), ["pinned", "plain"])
    }

    func testAdvancedModeLeavesOutAnEmptyBotsGroup() {
        var onlyChief = CompanionState()
        onlyChief.bots = [bot("chief", #","chiefOfStaff":true"#)]
        let layout = HomeLayoutRules.layout(onlyChief, advanced: true, department: nil)
        XCTAssertFalse(layout.groups.contains { $0.kind == .bots })
        XCTAssertEqual(layout.chief?.id, "chief")
    }

    func testADepartmentFilterKeepsOnlyThatDepartment() {
        let layout = HomeLayoutRules.layout(state, advanced: false, department: "Ops")
        XCTAssertEqual(layout.groups.map(\.key), ["department:Ops"])
        XCTAssertEqual(layout.department, "Ops")
        XCTAssertNil(layout.chief)
        XCTAssertTrue(HomeLayoutRules.layout(state, advanced: false, department: "Nowhere").groups.isEmpty)
    }

    func testAnEmptyFleetShowsNeitherBotsNorGroupChats() {
        for advanced in [false, true] {
            XCTAssertTrue(HomeLayoutRules.layout(CompanionState(), advanced: advanced, department: nil).groups.isEmpty)
        }
    }

    func testAFleetWithOnlyADeclaredDepartmentStillShowsGroupChatsToStartOne() {
        var only = CompanionState()
        only.setDeclaredSections(["Ops"])
        let keys = HomeLayoutRules.layout(only, advanced: false, department: nil).groups.map(\.key)
        XCTAssertEqual(keys, ["bots", "department:Ops", "groups"])
    }

    func testAPinnedBotStaysInBotsAndNotInItsDepartment() {
        var other = state
        other.bots.append(bot("pinned-ops", #","section":"Ops","pinned":true"#))
        let groups = HomeLayoutRules.layout(other, advanced: false, department: nil).groups
        XCTAssertTrue(groups[0].bots.contains { $0.id == "pinned-ops" })
        XCTAssertFalse(groups.first { $0.name == "Ops" }?.bots.contains { $0.id == "pinned-ops" } ?? true)
    }

    func testAFleetWithNothingToListIsEmpty() {
        XCTAssertTrue(HomeLayoutRules.layout(CompanionState(), advanced: false, department: nil).isEmpty)
        XCTAssertTrue(HomeLayoutRules.layout(CompanionState(), advanced: true, department: nil).isEmpty)
        XCTAssertFalse(HomeLayoutRules.layout(state, advanced: false, department: nil).isEmpty)
    }

    func testTilesComeFourToARowAndTheLastRowMayBeShort() {
        let rows = HomeLayoutRules.tileRows((0 ..< 9).map { bot("b\($0)") })
        XCTAssertEqual(rows.map(\.count), [4, 4, 1])
    }

    func testANewGroupStartsOpenAndAToggleFoldsThenOpensIt() {
        let none = Set<String>()
        XCTAssertTrue(FoldRules.isOpen(none, "department:Ops"))
        let folded = FoldRules.toggle(none, "department:Ops")
        XCTAssertFalse(FoldRules.isOpen(folded, "department:Ops"))
        XCTAssertTrue(FoldRules.isOpen(FoldRules.toggle(folded, "department:Ops"), "department:Ops"))
        XCTAssertTrue(FoldRules.isOpen(folded, "department:Sales"))
    }

    func testPruningDropsTheFoldOfADepartmentThatIsGone() {
        let folded: Set = ["department:Ops", "department:Gone"]
        XCTAssertEqual(FoldRules.prune(folded, ["department:Ops", "bots"]), ["department:Ops"])
    }

    func testFoldStateSurvivesSaveAndRestore() {
        let folded: Set = ["department:Ops", "department:Sales team", "groups"]
        XCTAssertEqual(FoldRules.decode(FoldRules.encode(folded)), folded)
        XCTAssertEqual(FoldRules.decode(""), [])
    }

    func testTheDotShowsTheTurnFirstThenAProblemThenWork() {
        let plain = bot("plain")
        XCTAssertEqual(HomeDotRules.of(plain, .waitingOnYou), .waiting)
        XCTAssertEqual(HomeDotRules.of(bot("x", #","activity":"failed""#), .idle), .problem)
        XCTAssertEqual(HomeDotRules.of(plain, .working), .working)
        XCTAssertEqual(HomeDotRules.of(plain, .idle), HomeDot.none)
    }

    func testATileShowsADotOnlyForAWaitOrAProblem() {
        XCTAssertTrue(HomeDotRules.tileShows(.waiting))
        XCTAssertTrue(HomeDotRules.tileShows(.problem))
        XCTAssertFalse(HomeDotRules.tileShows(.working))
        XCTAssertFalse(HomeDotRules.tileShows(.none))
    }

    func testAFoldedDepartmentCountsItsBotsThatWait() {
        let bots = [bot("a"), bot("b"), bot("c")]
        XCTAssertEqual(HomeDotRules.waitingCount(bots, waiting: ["a", "c", "other"]), 2)
        XCTAssertEqual(HomeDotRules.waitingCount(bots, waiting: []), 0)
    }

    func testTheAddBotsListLeavesOutHiddenBotsAndTheChief() {
        XCTAssertEqual(DepartmentRules.selectable(state).map(\.id), ["plain", "pinned", "ops-bot"])
    }

    func testTheBotsOfADepartmentStartChecked() {
        XCTAssertEqual(DepartmentRules.members(DepartmentRules.selectable(state), "Ops"), ["ops-bot"])
    }

    func testASaveWithNothingTickedHasNoCalls() {
        let bots = DepartmentRules.selectable(state)
        let placement = DepartmentEdits().placement(bots, members: DepartmentRules.members(bots, "Ops"))
        XCTAssertTrue(placement.isEmpty)
        XCTAssertTrue(placement.calls("Ops").isEmpty)
        XCTAssertFalse(DepartmentRules.canSave(placement, saving: false))
    }

    func testTheEditsNameTheBotsAddedAndRemoved() {
        let bots = DepartmentRules.selectable(state)
        let members = DepartmentRules.members(bots, "Ops")
        var edits = DepartmentEdits()
        edits.toggle("plain", members: members)
        edits.toggle("ops-bot", members: members)
        let placement = edits.placement(bots, members: members)
        XCTAssertEqual(placement.added, ["plain"])
        XCTAssertEqual(placement.removed, ["ops-bot"])
    }

    func testTickingABotTwiceLeavesItAsItWas() {
        let bots = DepartmentRules.selectable(state)
        let members = DepartmentRules.members(bots, "Ops")
        var edits = DepartmentEdits()
        edits.toggle("plain", members: members)
        edits.toggle("plain", members: members)
        edits.toggle("ops-bot", members: members)
        edits.toggle("ops-bot", members: members)
        XCTAssertTrue(edits.placement(bots, members: members).isEmpty)
        XCTAssertEqual(edits, DepartmentEdits())
    }

    func testAChangeMadeOnTheComputerWhileTheSheetIsOpenIsNotUndone() {
        let bots = DepartmentRules.selectable(state)
        var edits = DepartmentEdits()
        let before = DepartmentRules.members(bots, "Ops")
        edits.toggle("plain", members: before)
        // The computer files "pinned" into Ops and takes "ops-bot" out while the sheet is open.
        let live: Set<String> = ["pinned"]
        XCTAssertTrue(edits.isChecked("pinned", members: live))
        XCTAssertFalse(edits.isChecked("ops-bot", members: live))
        let placement = edits.placement(bots, members: live)
        XCTAssertEqual(placement.added, ["plain"])
        XCTAssertEqual(placement.removed, [])
    }

    func testABotThePersonAddedAndTheComputerAlreadyAddedSendsNothing() {
        let bots = DepartmentRules.selectable(state)
        var edits = DepartmentEdits()
        edits.toggle("plain", members: [])
        XCTAssertTrue(edits.placement(bots, members: ["plain"]).isEmpty)
    }

    func testASaveSendsOneCallToAddAndOneWithAnEmptyNameToRemove() {
        let placement = DepartmentPlacement(added: ["a", "b"], removed: ["c"])
        XCTAssertEqual(placement.calls("Ops"), [SectionCall("Ops", ["a", "b"]), SectionCall("", ["c"])])
        XCTAssertEqual(DepartmentPlacement(added: ["a"], removed: []).calls("Ops"), [SectionCall("Ops", ["a"])])
        XCTAssertEqual(DepartmentPlacement(added: [], removed: ["c"]).calls("Ops"), [SectionCall("", ["c"])])
    }

    func testSaveWaitsWhileASaveRuns() {
        let placement = DepartmentPlacement(added: ["a"], removed: [])
        XCTAssertTrue(DepartmentRules.canSave(placement, saving: false))
        XCTAssertFalse(DepartmentRules.canSave(placement, saving: true))
    }

    func testADepartmentNameIsTrimmedAndRefusedWhenEmptyOrUsed() {
        let existing = ["Ops", "Sales"]
        XCTAssertEqual(DepartmentRules.nameProblem("   ", existing), .empty)
        XCTAssertEqual(DepartmentRules.nameProblem(" Ops ", existing), .exists)
        XCTAssertNil(DepartmentRules.nameProblem(" Marketing ", existing))
    }

    func testADepartmentNameStopsAtSixtyCharacters() {
        XCTAssertEqual(DepartmentRules.clampName(String(repeating: "x", count: 80)).count, 60)
    }
}
