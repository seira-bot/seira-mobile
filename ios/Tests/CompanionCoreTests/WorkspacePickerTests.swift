import Foundation
import XCTest
@testable import CompanionCore

/// The state of the workspace picker. The Kotlin twin is `WorkspacePickerTest`.
@MainActor
final class WorkspacePickerTests: XCTestCase {
    private final class FakeSource: WorkspaceSource {
        var list: [PhoneWorkspace]?
        var listFails = false
        var opens: [(id: String, now: Bool)] = []
        var changes = 0
        var onOpen: (String, Bool) async throws -> [PhoneWorkspace]

        init(_ list: [PhoneWorkspace]?) {
            self.list = list
            onOpen = { _, _ in [] }
        }

        func workspaces() async throws -> [PhoneWorkspace]? {
            if listFails { throw URLError(.notConnectedToInternet) }
            return list
        }

        func requestOpen(id: String, now: Bool) async throws -> [PhoneWorkspace] {
            opens.append((id, now))
            return try await onOpen(id, now)
        }

        func workspaceChanged() async {
            changes += 1
        }
    }

    private let home = PhoneWorkspace(id: "home", name: "Home", current: true)
    private let trading = PhoneWorkspace(id: "trading", name: "Trading")
    private let pentest = PhoneWorkspace(id: "pentest", name: "Pentest", state: "waiting", waitingFor: "Nadia")
    private let locked = PhoneWorkspace(id: "locked", name: "Locked", state: "unavailable")

    private func model(_ source: FakeSource, canOpen: Bool = true) -> WorkspacePickerModel {
        WorkspacePickerModel(source: source) { canOpen }
    }

    private func opened(_ workspace: PhoneWorkspace) -> PhoneWorkspace {
        var next = workspace
        next.current = true
        next.state = "ready"
        next.waitingFor = nil
        return next
    }

    private func notCurrent(_ workspace: PhoneWorkspace) -> PhoneWorkspace {
        var next = workspace
        next.current = false
        return next
    }

    func testOneEntryGetsNoPicker() async {
        let m = model(FakeSource([home]))
        await m.refresh()
        XCTAssertFalse(m.state.visible)
    }

    func testNoListGetsNoPicker() async {
        let m = model(FakeSource(nil))
        await m.refresh()
        XCTAssertFalse(m.state.visible)
        XCTAssertTrue(m.state.workspaces.isEmpty)
    }

    func testAReadyWorkspaceOpensOnATap() async throws {
        let source = FakeSource([home, trading])
        source.onOpen = { [self] _, _ in [notCurrent(home), opened(trading)] }
        let m = model(source)
        await m.refresh()

        let row = try XCTUnwrap(m.state.rows.first { $0.workspace.id == "trading" })
        XCTAssertEqual(row.tap, .open)
        XCTAssertEqual(row.status, .none)

        await m.perform(.open, id: "trading")

        XCTAssertEqual(source.opens.map { $0.id }, ["trading"])
        XCTAssertEqual(source.opens.map { $0.now }, [false])
        XCTAssertEqual(m.state.current?.id, "trading")
        XCTAssertEqual(source.changes, 1)
        XCTAssertNil(m.state.notice)
        XCTAssertNil(m.state.busyID)
    }

    func testTheOpenWorkspaceHasNothingToOpen() async throws {
        let source = FakeSource([home, trading])
        let m = model(source)
        await m.refresh()

        let row = try XCTUnwrap(m.state.rows.first { $0.workspace.id == "home" })
        XCTAssertEqual(row.status, .open)
        XCTAssertNil(row.tap)
        XCTAssertNil(row.button)
        await m.perform(.open, id: "home")
        XCTAssertTrue(source.opens.isEmpty)
    }

    func testAWaitingWorkspaceOffersOpenNowAndTheOpenOneOffersCancel() async throws {
        let m = model(FakeSource([home, pentest]))
        await m.refresh()

        let rows = Dictionary(uniqueKeysWithValues: m.state.rows.map { ($0.workspace.id, $0) })
        let waiting = try XCTUnwrap(rows["pentest"])
        XCTAssertEqual(waiting.status, .waiting)
        XCTAssertEqual(WorkspacePicker.waitingFor(waiting.workspace), "Nadia")
        XCTAssertNil(waiting.tap)
        XCTAssertEqual(waiting.button, .openNow)
        XCTAssertEqual(rows["home"]?.button, .cancelWait)
        XCTAssertTrue(m.state.polling)
    }

    func testOpenNowSendsTheHurryFlagAndKeepsWaitingUntilTheComputerOpensIt() async {
        let source = FakeSource([home, pentest])
        source.onOpen = { [self] _, _ in [home, pentest] }
        let m = model(source)
        await m.refresh()

        await m.perform(.openNow, id: "pentest")

        XCTAssertEqual(source.opens.map { $0.id }, ["pentest"])
        XCTAssertEqual(source.opens.map { $0.now }, [true])
        XCTAssertEqual(source.changes, 0)
        XCTAssertTrue(m.state.polling)

        source.list = [notCurrent(home), opened(pentest)]
        await m.refresh()

        XCTAssertEqual(m.state.current?.id, "pentest")
        XCTAssertEqual(source.changes, 1)
        XCTAssertFalse(m.state.polling)
    }

    func testCancelAsksForTheOpenWorkspaceAndKeepsIt() async {
        let source = FakeSource([home, pentest])
        source.onOpen = { [self] _, _ in [home, pentest.asReady()] }
        let m = model(source)
        await m.refresh()

        await m.perform(.cancelWait, id: "home")

        XCTAssertEqual(source.opens.map { $0.id }, ["home"])
        XCTAssertEqual(source.opens.map { $0.now }, [false])
        XCTAssertEqual(source.changes, 0)
        XCTAssertEqual(m.state.current?.id, "home")
        XCTAssertFalse(m.state.polling)
    }

    func testARowCannotRunAnActionItDoesNotOffer() async {
        let source = FakeSource([home, pentest, locked])
        let m = model(source)
        await m.refresh()

        await m.perform(.open, id: "pentest")
        await m.perform(.open, id: "locked")
        await m.perform(.openNow, id: "locked")
        await m.perform(.cancelWait, id: "pentest")
        await m.perform(.open, id: "missing")

        XCTAssertTrue(source.opens.isEmpty)
        XCTAssertEqual(m.state.rows.first { $0.workspace.id == "locked" }?.status, .unavailable)
    }

    func testARefusedSwitchTheOwnerTurnedOffSaysSoAndHidesThePickerOnTheNextList() async {
        let source = FakeSource([home, trading])
        source.onOpen = { [unowned source, home] _, _ in
            source.list = [home]
            throw APIError.status(code: 403, message: nil)
        }
        let m = model(source)
        await m.refresh()

        await m.perform(.open, id: "trading")

        XCTAssertEqual(m.state.notice, .turnedOff)
        XCTAssertFalse(m.state.visible)
        XCTAssertEqual(m.state.current?.id, "home")
        XCTAssertEqual(source.changes, 0)
    }

    func testAWorkspaceThatCannotOpenNowGives409AndReadsTheListAgain() async {
        let source = FakeSource([home, trading])
        source.onOpen = { [unowned source, home, trading] _, _ in
            var gone = trading
            gone.state = "unavailable"
            source.list = [home, gone]
            throw APIError.status(code: 409, message: "workspace_unavailable")
        }
        let m = model(source)
        await m.refresh()

        await m.perform(.open, id: "trading")

        XCTAssertEqual(m.state.notice, .unavailable)
        XCTAssertEqual(m.state.rows.first { $0.workspace.id == "trading" }?.status, .unavailable)
        XCTAssertEqual(source.changes, 0)
        XCTAssertNil(m.state.busyID)
    }

    func testAWorkspaceThatIsGoneGives404() async {
        let source = FakeSource([home, trading])
        source.onOpen = { [unowned source, home] _, _ in
            source.list = [home]
            throw APIError.status(code: 404, message: "workspace_not_found")
        }
        let m = model(source)
        await m.refresh()
        await m.perform(.open, id: "trading")
        XCTAssertEqual(m.state.notice, .gone)
        XCTAssertFalse(m.state.visible)
    }

    func testANetworkFailureKeepsTheListAndSaysFailed() async {
        let source = FakeSource([home, trading])
        source.onOpen = { _, _ in throw URLError(.notConnectedToInternet) }
        let m = model(source)
        await m.refresh()
        await m.perform(.open, id: "trading")
        XCTAssertEqual(m.state.notice, .failed)
        XCTAssertTrue(m.state.visible)
        XCTAssertEqual(m.state.current?.id, "home")
    }

    func testAListReadThatFailsKeepsTheLastList() async {
        let source = FakeSource([home, trading])
        let m = model(source)
        await m.refresh()
        source.listFails = true
        await m.refresh()
        XCTAssertTrue(m.state.visible)
    }

    func testAPairingWithAPinnedIdentityNeverStartsASwitch() async throws {
        let source = FakeSource([home, trading, pentest])
        let m = model(source, canOpen: false)
        await m.refresh()

        let rows = Dictionary(uniqueKeysWithValues: m.state.rows.map { ($0.workspace.id, $0) })
        XCTAssertEqual(rows["trading"]?.status, .pairAgain)
        XCTAssertNil(rows["trading"]?.tap)
        XCTAssertEqual(rows["pentest"]?.status, .waiting)
        XCTAssertNil(rows["pentest"]?.button)
        XCTAssertEqual(rows["home"]?.button, .cancelWait)

        await m.perform(.open, id: "trading")
        await m.perform(.openNow, id: "pentest")

        XCTAssertTrue(source.opens.isEmpty)
        XCTAssertEqual(source.changes, 0)
        XCTAssertEqual(m.state.notice, .needsPairing)
    }

    func testOnlyAnUnpinnedPairingMayOpen() {
        XCTAssertFalse(WorkspacePicker.canOpen(nil))
        var plain = Connection(name: "Mac", host: "127.0.0.1", port: 8810)
        XCTAssertTrue(WorkspacePicker.canOpen(plain))
        plain.serverEnvironmentId = "9f2c1a54-1111-4222-8333-444455556666"
        XCTAssertFalse(WorkspacePicker.canOpen(plain))
    }

    func testASecondRequestWhileOneIsOutIsIgnored() async {
        let source = FakeSource([home, trading])
        let m = model(source)
        await m.refresh()
        source.onOpen = { [home, trading] _, _ in
            await m.perform(.open, id: "trading")
            return [home, trading]
        }
        await m.perform(.open, id: "trading")
        XCTAssertEqual(source.opens.count, 1)
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

    func testStatusCodesMapToNotices() {
        XCTAssertEqual(WorkspacePicker.notice(forStatus: 403), .turnedOff)
        XCTAssertEqual(WorkspacePicker.notice(forStatus: 409), .unavailable)
        XCTAssertEqual(WorkspacePicker.notice(forStatus: 404), .gone)
        XCTAssertEqual(WorkspacePicker.notice(forStatus: 500), .failed)
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
}
