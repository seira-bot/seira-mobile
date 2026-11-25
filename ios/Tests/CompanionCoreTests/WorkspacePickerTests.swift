import Foundation
import XCTest
@testable import CompanionCore

/// The state of the read-only workspace picker. The Kotlin twin is `WorkspacePickerTest`.
@MainActor
final class WorkspacePickerTests: XCTestCase {
    private final class FakeSource: WorkspaceSource {
        var activePairingID: String? = "p1"
        var list: [PhoneWorkspace]?
        var listFails = false
        var reads: [String] = []
        var changes: [String] = []
        /// Runs inside a read, before it answers: a place to change the active pairing mid-read.
        var duringRead: () -> Void = {}

        init(_ list: [PhoneWorkspace]?) {
            self.list = list
        }

        func workspaces(pairingID: String) async throws -> [PhoneWorkspace]? {
            reads.append(pairingID)
            duringRead()
            if listFails { throw URLError(.notConnectedToInternet) }
            return list
        }

        func workspaceChanged(pairingID: String) async {
            changes.append(pairingID)
        }
    }

    private let home = PhoneWorkspace(id: "home", name: "Home", current: true)
    private let trading = PhoneWorkspace(id: "trading", name: "Trading")
    private let pentest = PhoneWorkspace(id: "pentest", name: "Pentest", state: "waiting", waitingFor: "Nadia")
    private let locked = PhoneWorkspace(id: "locked", name: "Locked", state: "unavailable")

    func testOneEntryGetsNoPicker() async {
        let m = WorkspacePickerModel(source: FakeSource([home]))
        await m.refresh(pairingID: "p1")
        XCTAssertFalse(m.state.visible)
    }

    func testNoListGetsNoPicker() async {
        let m = WorkspacePickerModel(source: FakeSource(nil))
        await m.refresh(pairingID: "p1")
        XCTAssertFalse(m.state.visible)
        XCTAssertTrue(m.state.workspaces.isEmpty)
    }

    func testNoRowOffersAnAction() async throws {
        let m = WorkspacePickerModel(source: FakeSource([home, trading, pentest, locked]))
        await m.refresh(pairingID: "p1")

        let rows = Dictionary(uniqueKeysWithValues: m.state.rows.map { ($0.workspace.id, $0) })
        XCTAssertEqual(rows["home"]?.status, .open)
        XCTAssertEqual(rows["trading"]?.status, WorkspaceStatus.none)
        XCTAssertEqual(rows["pentest"]?.status, .waiting)
        XCTAssertEqual(WorkspacePicker.waitingFor(try XCTUnwrap(rows["pentest"]?.workspace)), "Nadia")
        XCTAssertEqual(rows["locked"]?.status, .unavailable)
        XCTAssertTrue(m.state.polling)
        // The row has no tap and no button: nothing here can start, hurry or cancel a switch.
        XCTAssertEqual(Set(Mirror(reflecting: try XCTUnwrap(rows["home"])).children.compactMap(\.label)), ["workspace", "status"])
    }

    func testAChangeOfTheOpenWorkspaceReloadsOnceForThatPairing() async {
        let source = FakeSource([home, pentest])
        let m = WorkspacePickerModel(source: source)
        await m.refresh(pairingID: "p1")
        XCTAssertTrue(m.state.polling)

        var next = pentest
        next.current = true
        next.state = "ready"
        next.waitingFor = nil
        source.list = [home.asReady(), next]
        await m.refresh(pairingID: "p1")

        XCTAssertEqual(m.state.current?.id, "pentest")
        XCTAssertEqual(source.changes, ["p1"])
        XCTAssertFalse(m.state.polling)
    }

    func testAListReadThatFailsKeepsTheLastList() async {
        let source = FakeSource([home, trading])
        let m = WorkspacePickerModel(source: source)
        await m.refresh(pairingID: "p1")
        source.listFails = true
        await m.refresh(pairingID: "p1")
        XCTAssertTrue(m.state.visible)
        XCTAssertEqual(m.state.current?.id, "home")
    }

    func testAListThatArrivesAfterThePairingChangedIsDropped() async {
        let source = FakeSource([home, trading])
        let m = WorkspacePickerModel(source: source)
        await m.refresh(pairingID: "p1")
        XCTAssertEqual(m.state.workspaces.count, 2)

        source.list = [home.asReady(), trading.asOpen()]
        source.duringRead = { source.activePairingID = "p2" }
        await m.refresh(pairingID: "p1")

        XCTAssertEqual(m.state.current?.id, "home", "the stale answer did not replace the list")
        XCTAssertTrue(source.changes.isEmpty, "a stale answer never reloads the session")
    }

    func testAPairingThatIsNotActiveIsNeverRead() async {
        let source = FakeSource([home, trading])
        source.activePairingID = "p2"
        let m = WorkspacePickerModel(source: source)
        await m.refresh(pairingID: "p1")
        XCTAssertTrue(source.reads.isEmpty)
        XCTAssertTrue(m.state.workspaces.isEmpty)
    }

    func testANewPairingStartsFromAnEmptyListEvenWhenItsReadFails() async {
        let source = FakeSource([home, trading])
        let m = WorkspacePickerModel(source: source)
        await m.refresh(pairingID: "p1")
        XCTAssertTrue(m.state.visible)

        source.activePairingID = "p2"
        source.listFails = true
        await m.refresh(pairingID: "p2")

        XCTAssertTrue(m.state.workspaces.isEmpty, "a failed refresh never falls back to another computer's list")
        XCTAssertFalse(m.state.visible)
    }

    func testNoPairingEmptiesThePicker() async {
        let source = FakeSource([home, trading])
        let m = WorkspacePickerModel(source: source)
        await m.refresh(pairingID: "p1")
        await m.refresh(pairingID: nil)
        XCTAssertTrue(m.state.workspaces.isEmpty)
    }

    func testSixHexDigitsAreAColorWithOrWithoutTheHash() {
        XCTAssertEqual(WorkspacePicker.colorValue("#3366ff"), 0x3366FF)
        XCTAssertEqual(WorkspacePicker.colorValue("3366FF"), 0x3366FF)
        XCTAssertEqual(WorkspacePicker.colorValue("  #3366ff "), 0x3366FF)
    }

    func testAnythingElseIsNoColor() {
        XCTAssertNil(WorkspacePicker.colorValue(nil))
        XCTAssertNil(WorkspacePicker.colorValue(""))
        XCTAssertNil(WorkspacePicker.colorValue("red"))
        XCTAssertNil(WorkspacePicker.colorValue("#36f"))
        XCTAssertNil(WorkspacePicker.colorValue("#3366ffaa"))
        XCTAssertNil(WorkspacePicker.colorValue("#33ggff"))
    }

    func testABlankNameIsNoName() {
        XCTAssertEqual(WorkspacePicker.name(PhoneWorkspace(id: "a", name: " Work ")), "Work")
        XCTAssertNil(WorkspacePicker.name(PhoneWorkspace(id: "a", name: "  ")))
        XCTAssertNil(WorkspacePicker.name(PhoneWorkspace(id: "a", name: nil)))
    }
}

private extension PhoneWorkspace {
    /// The same workspace, not open, with nothing in its way.
    func asReady() -> PhoneWorkspace {
        var next = self
        next.current = false
        next.state = "ready"
        next.waitingFor = nil
        return next
    }

    /// The same workspace, open, with nothing in its way.
    func asOpen() -> PhoneWorkspace {
        var next = asReady()
        next.current = true
        return next
    }
}
