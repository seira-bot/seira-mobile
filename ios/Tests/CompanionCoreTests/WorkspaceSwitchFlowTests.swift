import XCTest
@testable import CompanionCore

/// A switch the phone starts, through `WorkspaceSwitchFlow`. The Kotlin twin is `WorkspaceSwitchSessionTest.kt`.
/// The fake computer changes the identity it shows when a switch is done, as the real one does, and it counts the
/// calls that carry the bearer.
private enum Ids {
    static let old = "env-old"
    static let new = "env-new"
    static let operation = "0123456789abcdef0123456789abcdef"
    static let pairing = "pairing-1"
}

@MainActor
final class WorkspaceSwitchFlowTests: XCTestCase {
    private final class Computer: WorkspaceSwitchTransport, @unchecked Sendable {
        var identity = Ids.old
        var listedTarget: String? = Ids.new
        var accepted: PhoneWorkspaceSwitch? = PhoneWorkspaceSwitch(
            id: Ids.operation, workspaceId: "trading", environmentId: Ids.new
        )
        var status: PhoneWorkspaceSwitch?? = .some(nil)
        var bearerCalls: [String] = []
        var identityProbes = 0

        func environmentId() async throws -> String { identityProbes += 1; return identity }

        func workspaces() async throws -> [PhoneWorkspace]? {
            bearerCalls.append("list@\(identity)")
            return [PhoneWorkspace(id: "trading", environmentId: listedTarget)]
        }

        func switchWorkspace(_ id: String) async throws -> [PhoneWorkspace] {
            bearerCalls.append("switch@\(identity)")
            return []
        }

        func startWorkspaceSwitch(_ id: String) async throws -> PhoneWorkspaceSwitchResponse {
            bearerCalls.append("start@\(identity)")
            return PhoneWorkspaceSwitchResponse(workspaces: [], switchInFlight: accepted)
        }

        func workspaceSwitch(_ operationId: String) async throws -> PhoneWorkspaceSwitch? {
            bearerCalls.append("status@\(identity)")
            switch status {
            case .none: throw APIError.status(code: 500, message: nil)
            case let .some(value): return value
            }
        }
    }

    private var computer: Computer!

    override func setUp() {
        super.setUp()
        computer = Computer()
    }

    private func flow(proof: (@Sendable (PhoneWorkspaceSwitch) -> WorkspaceSwitchCompletion?)? = nil) -> WorkspaceSwitchFlow {
        WorkspaceSwitchFlow(seams: WorkspaceSwitchSeams(
            now: { 1_000 },
            proof: { _, switchInFlight in proof?(switchInFlight) }
        ))
    }

    nonisolated private static func signed(_ sw: PhoneWorkspaceSwitch, authenticated: Bool = true) -> WorkspaceSwitchCompletion {
        WorkspaceSwitchCompletion(
            operationId: sw.id,
            workspaceId: sw.workspaceId,
            environmentId: sw.environmentId ?? "",
            authenticatedByPairing: authenticated
        )
    }

    private func start(_ flow: WorkspaceSwitchFlow, pin: String? = Ids.old) async throws -> WorkspaceSwitchResult {
        try await flow.start(workspaceId: "trading", pairingId: Ids.pairing, pin: pin, transport: computer)
    }

    private func assertNoBearerToTheNewIdentity(file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertFalse(computer.bearerCalls.contains { $0.hasSuffix("@\(Ids.new)") }, "\(computer.bearerCalls)", file: file, line: line)
    }

    func testASwitchThePhoneStartedAndProvedGivesTheNewPin() async throws {
        computer.accepted?.state = "done"
        let result = try await start(flow(proof: { Self.signed($0) }))
        XCTAssertEqual(result, .switched(environmentId: Ids.new))
        assertNoBearerToTheNewIdentity()
    }

    func testWithoutAProofTheOldPinStaysAndTheNewIdentityGetsNoBearer() async throws {
        computer.accepted?.state = "done"
        let result = try await start(flow())
        XCTAssertEqual(result, .rejected(.verificationFailed))
        assertNoBearerToTheNewIdentity()
    }

    func testAProofThatThePairedComputerDidNotSignIsRejected() async throws {
        computer.accepted?.state = "done"
        let result = try await start(flow(proof: { Self.signed($0, authenticated: false) }))
        XCTAssertEqual(result, .rejected(.notAuthenticated))
    }

    func testAServerThatClaimsAnotherIdentityThanTheTargetIsRejected() async throws {
        computer.accepted?.state = "done"
        let result = try await start(flow(proof: { sw in
            WorkspaceSwitchCompletion(operationId: sw.id, workspaceId: sw.workspaceId, environmentId: "env-liar", authenticatedByPairing: true)
        }))
        XCTAssertEqual(result, .rejected(.wrongEnvironment))
    }

    func testAProofForAnotherOperationOrWorkspaceIsRejected() async throws {
        computer.accepted?.state = "done"
        var result = try await start(flow(proof: { sw in
            WorkspaceSwitchCompletion(operationId: "replayed", workspaceId: sw.workspaceId, environmentId: Ids.new, authenticatedByPairing: true)
        }))
        XCTAssertEqual(result, .rejected(.wrongOperation))
        result = try await start(flow(proof: { sw in
            WorkspaceSwitchCompletion(operationId: sw.id, workspaceId: "other", environmentId: Ids.new, authenticatedByPairing: true)
        }))
        XCTAssertEqual(result, .rejected(.wrongWorkspace))
    }

    func testAListWithoutTheTargetIdentityStartsNothingAndAsksForPairing() async throws {
        computer.listedTarget = nil
        let result = try await start(flow(proof: { Self.signed($0) }))
        XCTAssertEqual(result, .needsPairing)
        XCTAssertFalse(computer.bearerCalls.contains { $0.hasPrefix("start@") })
    }

    func testAnAnswerThatNamesAnotherTargetThanTheListEndsTheSwitch() async throws {
        computer.accepted = PhoneWorkspaceSwitch(id: Ids.operation, workspaceId: "trading", environmentId: "env-other", state: "done")
        let result = try await start(flow(proof: { Self.signed($0) }))
        XCTAssertEqual(result, .rejected(.wrongOperation))
    }

    func testAnAnswerWithoutASwitchKeepsThePin() async throws {
        computer.accepted = nil
        let result = try await start(flow(proof: { Self.signed($0) }))
        XCTAssertEqual(result, .rejected(.wrongOperation))
    }

    func testAnAddressThatAlreadyShowsAnotherIdentitySendsNoBearer() async throws {
        computer.identity = "env-impostor"
        let result = try await start(flow(proof: { Self.signed($0) }))
        XCTAssertEqual(result, .rejected(.verificationFailed))
        XCTAssertTrue(computer.bearerCalls.isEmpty, "\(computer.bearerCalls)")
    }

    func testASwitchThatWaitsMovesThePinOnlyWhenItIsDoneAndOnlyOnce() async throws {
        let waiting = flow(proof: { Self.signed($0) })
        guard case .waiting = try await start(waiting) else { return XCTFail("expected waiting") }
        computer.status = .some(PhoneWorkspaceSwitch(id: Ids.operation, workspaceId: "trading", environmentId: Ids.new, state: "pending"))
        guard case .waiting = await waiting.finish(pairingId: Ids.pairing, transport: computer) else { return XCTFail("expected waiting") }
        // Done: the address shows the new identity from now on, and the pin gate refuses the bearer.
        computer.status = .some(PhoneWorkspaceSwitch(id: Ids.operation, workspaceId: "trading", environmentId: Ids.new, state: "done"))
        computer.identity = Ids.new
        let result = await waiting.finish(pairingId: Ids.pairing, transport: computer)
        XCTAssertEqual(result, .rejected(.verificationFailed))
        assertNoBearerToTheNewIdentity()
        let again = await waiting.finish(pairingId: Ids.pairing, transport: computer)
        XCTAssertEqual(again, .rejected(.noPendingSwitch))
    }

    func testAWaitingSwitchThatIsDoneWhileTheOldIdentityAnswersMovesThePinOnce() async throws {
        let waiting = flow(proof: { Self.signed($0) })
        guard case .waiting = try await start(waiting) else { return XCTFail("expected waiting") }
        computer.status = .some(PhoneWorkspaceSwitch(id: Ids.operation, workspaceId: "trading", environmentId: Ids.new, state: "done"))
        let result = await waiting.finish(pairingId: Ids.pairing, transport: computer)
        XCTAssertEqual(result, .switched(environmentId: Ids.new))
        let again = await waiting.finish(pairingId: Ids.pairing, transport: computer)
        XCTAssertEqual(again, .rejected(.noPendingSwitch))
    }

    func testASwitchThatEndedOrExpiredKeepsThePin() async throws {
        for state in ["failed", "cancelled", "superseded"] {
            computer.accepted?.state = state
            let result = try await start(flow(proof: { Self.signed($0) }))
            XCTAssertEqual(result, .ended(state))
        }
        computer.accepted?.state = "pending"
        let waiting = flow(proof: { Self.signed($0) })
        _ = try await start(waiting)
        computer.status = .some(nil)
        let expired = await waiting.finish(pairingId: Ids.pairing, transport: computer)
        XCTAssertEqual(expired, .ended("expired"))
    }

    func testAStatusReadThatFailsIsNotExpiryAndKeepsThePin() async throws {
        let waiting = flow(proof: { Self.signed($0) })
        _ = try await start(waiting)
        computer.status = .none
        let result = await waiting.finish(pairingId: Ids.pairing, transport: computer)
        XCTAssertEqual(result, .rejected(.verificationFailed))
    }

    func testAChangedPairingEndsTheWaitingSwitch() async throws {
        let waiting = flow(proof: { Self.signed($0) })
        _ = try await start(waiting)
        let result = await waiting.finish(pairingId: "pairing-2", transport: computer)
        XCTAssertEqual(result, .rejected(.pairingChanged))
        let again = await waiting.finish(pairingId: Ids.pairing, transport: computer)
        XCTAssertEqual(again, .rejected(.noPendingSwitch))
    }

    func testASwitchStartedOnAnotherDeviceMovesNothing() async throws {
        let idle = flow(proof: { Self.signed($0) })
        let result = await idle.finish(pairingId: Ids.pairing, transport: computer)
        XCTAssertEqual(result, .rejected(.noPendingSwitch))
    }

    func testAPairingWithNoPinSwitchesAsItAlwaysDid() async throws {
        let result = try await start(flow(), pin: nil)
        XCTAssertEqual(result, .opened([]))
        XCTAssertEqual(computer.bearerCalls, ["switch@\(Ids.old)"])
        XCTAssertEqual(computer.identityProbes, 0)
    }
}

