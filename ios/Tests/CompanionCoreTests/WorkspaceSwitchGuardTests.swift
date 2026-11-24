import XCTest
@testable import CompanionCore

/// The pin of a pairing across a workspace switch. The Kotlin twin is `WorkspaceSwitchGuardTest.kt`.
final class WorkspaceSwitchGuardTests: XCTestCase {
    private static let old = "env-old"
    private static let new = "env-new"
    private static let pairing = "pairing-1"

    private var clock: Int64 = 1_000
    private var counter = 0
    private var pinGuard: WorkspaceSwitchGuard!

    override func setUp() {
        super.setUp()
        clock = 1_000
        counter = 0
        pinGuard = WorkspaceSwitchGuard(
            initialPin: Self.old,
            now: { [unowned self] in self.clock },
            ttlMillis: 60_000,
            newOperationId: { [unowned self] in
                self.counter += 1
                return "op-\(self.counter)"
            }
        )
    }

    private func start(target: String? = WorkspaceSwitchGuardTests.new) -> String {
        let started = pinGuard.begin(pairingId: Self.pairing, workspaceId: "trading", targetEnvironmentId: target)
        guard case let .started(operationId) = started else {
            XCTFail("expected a started switch, got \(started)")
            return ""
        }
        return operationId
    }

    private func completion(
        _ operationId: String,
        workspaceId: String = "trading",
        environmentId: String = WorkspaceSwitchGuardTests.new,
        authenticated: Bool = true
    ) -> WorkspaceSwitchCompletion {
        WorkspaceSwitchCompletion(
            operationId: operationId,
            workspaceId: workspaceId,
            environmentId: environmentId,
            authenticatedByPairing: authenticated
        )
    }

    private func assertRejected(_ reason: WorkspaceSwitchRejection, _ outcome: WorkspaceSwitchOutcome) {
        XCTAssertEqual(outcome, .rejected(reason))
        assertKeepsOldPin()
    }

    private func assertKeepsOldPin() {
        XCTAssertEqual(pinGuard.pin, Self.old)
        XCTAssertTrue(pinGuard.bearerAllowed(observedEnvironmentId: Self.old))
        XCTAssertFalse(pinGuard.bearerAllowed(observedEnvironmentId: Self.new))
    }

    func testASwitchThePhoneStartedAndVerifiedMovesThePin() {
        let operation = start()
        XCTAssertEqual(
            pinGuard.complete(pairingId: Self.pairing, completion: completion(operation)),
            .repinned(environmentId: Self.new)
        )
        XCTAssertEqual(pinGuard.pin, Self.new)
        XCTAssertTrue(pinGuard.bearerAllowed(observedEnvironmentId: Self.new))
        XCTAssertFalse(pinGuard.bearerAllowed(observedEnvironmentId: Self.old))
    }

    func testAnIdChangeWithoutASwitchThePhoneStartedStopsAsToday() {
        XCTAssertFalse(pinGuard.bearerAllowed(observedEnvironmentId: Self.new))
        assertRejected(
            .noPendingSwitch,
            pinGuard.complete(pairingId: Self.pairing, completion: completion("op-from-another-device"))
        )
    }

    func testAFailedVerificationKeepsTheOldPinAndSendsNoTokenToTheTarget() {
        _ = start()
        assertRejected(.verificationFailed, pinGuard.complete(pairingId: Self.pairing, completion: nil))
    }

    func testAServerAtTheSameAddressThatClaimsAnotherIdIsRejected() {
        let operation = start()
        assertRejected(
            .wrongEnvironment,
            pinGuard.complete(pairingId: Self.pairing, completion: completion(operation, environmentId: "env-other"))
        )
    }

    func testACompletionForAnotherWorkspaceIsRejected() {
        let operation = start()
        assertRejected(
            .wrongWorkspace,
            pinGuard.complete(pairingId: Self.pairing, completion: completion(operation, workspaceId: "default"))
        )
    }

    func testACompletionThatTheTargetDidNotSendOverThePairingIsRejected() {
        let operation = start()
        assertRejected(
            .notAuthenticated,
            pinGuard.complete(pairingId: Self.pairing, completion: completion(operation, authenticated: false))
        )
    }

    func testACompletionForAnotherOperationIsRejected() {
        _ = start()
        assertRejected(.wrongOperation, pinGuard.complete(pairingId: Self.pairing, completion: completion("op-99")))
    }

    func testAnExpiredSwitchIsRejected() {
        let operation = start()
        clock += 60_000
        assertRejected(.expired, pinGuard.complete(pairingId: Self.pairing, completion: completion(operation)))
    }

    func testACompletionCannotBeReplayed() {
        let operation = start()
        XCTAssertEqual(
            pinGuard.complete(pairingId: Self.pairing, completion: completion(operation)),
            .repinned(environmentId: Self.new)
        )
        XCTAssertEqual(
            pinGuard.complete(pairingId: Self.pairing, completion: completion(operation)),
            .rejected(.noPendingSwitch)
        )
        XCTAssertEqual(pinGuard.pin, Self.new)
    }

    func testAFailedSwitchCannotBeRetriedWithTheSameOperation() {
        let operation = start()
        _ = pinGuard.complete(pairingId: Self.pairing, completion: nil)
        assertRejected(.noPendingSwitch, pinGuard.complete(pairingId: Self.pairing, completion: completion(operation)))
    }

    func testAChangedPairingOrACancelledSwitchIsRejected() {
        var operation = start()
        assertRejected(.pairingChanged, pinGuard.complete(pairingId: "pairing-2", completion: completion(operation)))
        operation = start()
        pinGuard.cancel()
        assertRejected(.noPendingSwitch, pinGuard.complete(pairingId: Self.pairing, completion: completion(operation)))
    }

    func testATargetIdentityThePhoneDoesNotKnowKeepsThePinAndAsksForPairing() {
        XCTAssertEqual(
            pinGuard.begin(pairingId: Self.pairing, workspaceId: "trading", targetEnvironmentId: nil),
            .needsPairing
        )
        XCTAssertEqual(
            pinGuard.begin(pairingId: Self.pairing, workspaceId: "trading", targetEnvironmentId: " "),
            .needsPairing
        )
        assertRejected(.noPendingSwitch, pinGuard.complete(pairingId: Self.pairing, completion: completion("op-1")))
    }
}
