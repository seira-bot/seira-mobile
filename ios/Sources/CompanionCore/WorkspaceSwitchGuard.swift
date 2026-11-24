import Foundation

/// What the paired computer sends back to finish a switch the phone started. `authenticatedByPairing`
/// is true only when the answer came over the authenticated channel of the same pairing. An address,
/// a redirect or an id from an unauthenticated probe never sets it.
public struct WorkspaceSwitchCompletion: Equatable, Sendable {
    public var operationId: String
    public var workspaceId: String
    public var environmentId: String
    public var authenticatedByPairing: Bool

    public init(operationId: String, workspaceId: String, environmentId: String, authenticatedByPairing: Bool) {
        self.operationId = operationId
        self.workspaceId = workspaceId
        self.environmentId = environmentId
        self.authenticatedByPairing = authenticatedByPairing
    }
}

public enum WorkspaceSwitchStart: Equatable, Sendable {
    case started(operationId: String)
    /// The phone does not know the target identity in advance, so it keeps the pin and asks for pairing again.
    case needsPairing
}

public enum WorkspaceSwitchRejection: Equatable, Sendable {
    case noPendingSwitch
    case pairingChanged
    case expired
    case verificationFailed
    case wrongOperation
    case wrongWorkspace
    case wrongEnvironment
    case notAuthenticated
}

public enum WorkspaceSwitchOutcome: Equatable, Sendable {
    case repinned(environmentId: String)
    /// The pin stays as it was and no bearer goes to the target.
    case rejected(WorkspaceSwitchRejection)
}

/// The pin of one pairing: the server identity the phone sends its bearer to. A different identity at
/// the same address stops the connection, as it does without this class. The pin moves only after a
/// switch that this phone started, bound to this pairing and this target, finishes with an
/// authenticated completion for the same operation. Every pending switch is used once, whatever the
/// result. The caller stores the new pin in one write.
///
/// The target identity comes from the authenticated workspace list of the old connection. The workspace
/// id alone does not prove it.
public final class WorkspaceSwitchGuard: @unchecked Sendable {
    public static let defaultTTLMillis: Int64 = 60_000

    private struct Pending {
        let operationId: String
        let pairingId: String
        let workspaceId: String
        let targetEnvironmentId: String
        let expiresAt: Int64
    }

    private let lock = NSLock()
    private let now: () -> Int64
    private let ttlMillis: Int64
    private let newOperationId: () -> String
    private var currentPin: String
    private var pending: Pending?

    public init(
        initialPin: String,
        now: @escaping () -> Int64,
        ttlMillis: Int64 = WorkspaceSwitchGuard.defaultTTLMillis,
        newOperationId: @escaping () -> String = { UUID().uuidString }
    ) {
        self.currentPin = initialPin
        self.now = now
        self.ttlMillis = ttlMillis
        self.newOperationId = newOperationId
    }

    public var pin: String {
        lock.lock()
        defer { lock.unlock() }
        return currentPin
    }

    /// Call this only after the user picked `workspaceId` on this phone.
    public func begin(pairingId: String, workspaceId: String, targetEnvironmentId: String?) -> WorkspaceSwitchStart {
        lock.lock()
        defer { lock.unlock() }
        pending = nil
        let target = (targetEnvironmentId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let workspace = workspaceId.trimmingCharacters(in: .whitespacesAndNewlines)
        if workspace.isEmpty || target.isEmpty { return .needsPairing }
        let operationId = newOperationId()
        pending = Pending(
            operationId: operationId,
            pairingId: pairingId,
            workspaceId: workspaceId,
            targetEnvironmentId: target,
            expiresAt: now() + ttlMillis
        )
        return .started(operationId: operationId)
    }

    /// A nil `completion` means the check did not finish: no answer, an error, or a failed signature.
    public func complete(pairingId: String, completion: WorkspaceSwitchCompletion?) -> WorkspaceSwitchOutcome {
        lock.lock()
        defer { lock.unlock() }
        guard let switchInFlight = pending else { return .rejected(.noPendingSwitch) }
        pending = nil
        if switchInFlight.pairingId != pairingId { return .rejected(.pairingChanged) }
        if now() >= switchInFlight.expiresAt { return .rejected(.expired) }
        guard let completion = completion else { return .rejected(.verificationFailed) }
        if completion.operationId != switchInFlight.operationId { return .rejected(.wrongOperation) }
        if !completion.authenticatedByPairing { return .rejected(.notAuthenticated) }
        if completion.workspaceId != switchInFlight.workspaceId { return .rejected(.wrongWorkspace) }
        if completion.environmentId != switchInFlight.targetEnvironmentId { return .rejected(.wrongEnvironment) }
        currentPin = switchInFlight.targetEnvironmentId
        return .repinned(environmentId: currentPin)
    }

    public func cancel() {
        lock.lock()
        defer { lock.unlock() }
        pending = nil
    }

    /// The one gate before a bearer leaves the phone: the identity read at the address must be the pin.
    public func bearerAllowed(observedEnvironmentId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return observedEnvironmentId == currentPin
    }
}
