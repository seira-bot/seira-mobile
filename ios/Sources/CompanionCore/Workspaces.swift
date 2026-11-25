import Foundation

/// Whether a workspace can be opened from the phone right now.
public enum WorkspaceAvailability: Hashable, Sendable {
    case ready
    case waiting
    case unavailable
}

/// One workspace of the computer a phone is paired with: the rail of the desktop app, as the
/// phone's `GET /api/phones/self/workspaces` lists it. The shape is `PhoneWorkspace` of the
/// server's phone protocol.
///
/// `state` stays the wire string: a state this build has never heard of reads as unavailable
/// through `availability` instead of failing the whole list.
public struct PhoneWorkspace: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    /// The identity the environment of this workspace shows as its own. Absent for a workspace that never ran.
    public var environmentId: String?
    public var name: String?
    public var color: String?
    public var current: Bool
    public var state: String
    public var waitingFor: String?

    public init(
        id: String,
        environmentId: String? = nil,
        name: String? = nil,
        color: String? = nil,
        current: Bool = false,
        state: String = "ready",
        waitingFor: String? = nil
    ) {
        self.id = id
        self.environmentId = environmentId
        self.name = name
        self.color = color
        self.current = current
        self.state = state
        self.waitingFor = waitingFor
    }

    private enum CodingKeys: String, CodingKey {
        case id, environmentId, name, color, current, state, waitingFor
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        environmentId = try container.decodeIfPresent(String.self, forKey: .environmentId)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        color = try container.decodeIfPresent(String.self, forKey: .color)
        current = try container.decodeIfPresent(Bool.self, forKey: .current) ?? false
        state = try container.decodeIfPresent(String.self, forKey: .state) ?? "ready"
        waitingFor = try container.decodeIfPresent(String.self, forKey: .waitingFor)
    }

    public var availability: WorkspaceAvailability {
        switch state {
        case "ready": return .ready
        case "waiting": return .waiting
        default: return .unavailable
        }
    }
}

public struct PhoneWorkspaceList: Decodable, Sendable {
    public var workspaces: [PhoneWorkspace]

    private enum CodingKeys: String, CodingKey { case workspaces }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        workspaces = try container.decodeIfPresent([PhoneWorkspace].self, forKey: .workspaces) ?? []
    }
}

/// One switch the computer accepted (`PhoneWorkspaceSwitch` of the phone protocol). `state` stays the wire
/// string: a state this build does not know reads as pending, so the phone keeps waiting and never reports an
/// unknown result.
public struct PhoneWorkspaceSwitch: Decodable, Equatable, Sendable {
    public var id: String
    public var workspaceId: String
    public var environmentId: String?
    public var state: String

    public init(id: String, workspaceId: String, environmentId: String? = nil, state: String = "pending") {
        self.id = id
        self.workspaceId = workspaceId
        self.environmentId = environmentId
        self.state = state
    }

    private enum CodingKeys: String, CodingKey { case id, workspaceId, environmentId, state }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        workspaceId = try container.decode(String.self, forKey: .workspaceId)
        environmentId = try container.decodeIfPresent(String.self, forKey: .environmentId)
        state = try container.decodeIfPresent(String.self, forKey: .state) ?? "pending"
    }

    public var isDone: Bool { state == "done" }
    public var isEnded: Bool { state == "failed" || state == "cancelled" || state == "superseded" }
}

/// The answer to a switch request: the list a moment later, and the switch when the computer started or hurried one.
public struct PhoneWorkspaceSwitchResponse: Decodable, Sendable {
    public var workspaces: [PhoneWorkspace]
    public var switchInFlight: PhoneWorkspaceSwitch?

    private enum CodingKeys: String, CodingKey { case workspaces, switchInFlight = "switch" }

    public init(workspaces: [PhoneWorkspace] = [], switchInFlight: PhoneWorkspaceSwitch? = nil) {
        self.workspaces = workspaces
        self.switchInFlight = switchInFlight
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        workspaces = try container.decodeIfPresent([PhoneWorkspace].self, forKey: .workspaces) ?? []
        switchInFlight = try container.decodeIfPresent(PhoneWorkspaceSwitch.self, forKey: .switchInFlight)
    }
}

struct PhoneWorkspaceSwitchStatus: Decodable, Sendable {
    var switchInFlight: PhoneWorkspaceSwitch
    private enum CodingKeys: String, CodingKey { case switchInFlight = "switch" }
}

/// What a switch the person asked for on this phone came to. Only `switched` changes what the phone trusts.
public enum WorkspaceSwitchResult: Equatable, Sendable {
    /// The computer started it, but it waits (for a bot to finish its turn). Call `finish` later.
    case waiting([PhoneWorkspace])
    /// The computer ended the switch without opening the workspace. The pin stayed.
    case ended(String)
    /// The switch finished and the phone now pins `environmentId`. The caller stores it in one write.
    case switched(environmentId: String)
    /// The phone has no pinned server identity for this pairing, or did not know the target identity in advance,
    /// so it did not start and sent no bearer. The person pairs again.
    case needsPairing
    /// The check failed. The old pin stays and no bearer went to the target.
    case rejected(WorkspaceSwitchRejection)
}

/// What the flow needs from the client of one pairing. `CompanionClient` is the real one.
public protocol WorkspaceSwitchTransport: Sendable {
    /// The public identity read: it sends no credential.
    func environmentId() async throws -> String
    func workspaces() async throws -> [PhoneWorkspace]?
    func switchWorkspace(_ id: String) async throws -> [PhoneWorkspace]
    func startWorkspaceSwitch(_ id: String) async throws -> PhoneWorkspaceSwitchResponse
    func workspaceSwitch(_ operationId: String) async throws -> PhoneWorkspaceSwitch?
}

/// What the app gives the flow: the clock, and the proof of a switch the phone starts. `proof` reads how the
/// computer proves, after the switch, that it is the one the phone paired with. It returns a completion whose
/// `authenticatedByPairing` is true only for an answer that a server at the same address cannot forge (a proof
/// from the paired computer's key). The contract of seira#525 has no such proof yet, so the default returns nil
/// and a switch keeps the old pin. An address, a redirect or an identity probe is not a proof.
public struct WorkspaceSwitchSeams: Sendable {
    public var now: @Sendable () -> Int64
    public var proof: @Sendable (any WorkspaceSwitchTransport, PhoneWorkspaceSwitch) async -> WorkspaceSwitchCompletion?

    public init(
        now: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        proof: @escaping @Sendable (any WorkspaceSwitchTransport, PhoneWorkspaceSwitch) async -> WorkspaceSwitchCompletion? = { _, _ in nil }
    ) {
        self.now = now
        self.proof = proof
    }
}

/// A workspace switch the phone starts, for a pairing with a pinned server identity. The flow never sends a
/// bearer to an address whose public identity is not the pin: the phone reads the identity first, and a switch
/// changes the identity at the address, so the status of a finished switch is read only with a proof from the
/// paired computer. The caller stores a `switched` result in one write and starts a new stream.
@MainActor
public final class WorkspaceSwitchFlow {
    private struct Attempt {
        let guardState: WorkspaceSwitchGuard
        let pairingId: String
        let switchInFlight: PhoneWorkspaceSwitch
    }

    private let seams: WorkspaceSwitchSeams
    private var attempt: Attempt?

    public init(seams: WorkspaceSwitchSeams = WorkspaceSwitchSeams()) {
        self.seams = seams
    }

    /// Opens `workspaceId`. A pairing with no pinned identity has no trust anchor, so no switch starts and no
    /// bearer is sent: the person pairs again.
    public func start(
        workspaceId: String,
        pairingId: String,
        pin: String?,
        transport: any WorkspaceSwitchTransport
    ) async throws -> WorkspaceSwitchResult {
        attempt = nil
        guard let pin else { return .needsPairing }
        // The pin is checked before the bearer of the list and of the switch leaves the phone.
        guard (try? await transport.environmentId()) == pin else { return .rejected(.verificationFailed) }
        guard let listed = try await transport.workspaces() else { return .needsPairing }
        let pinGuard = WorkspaceSwitchGuard(initialPin: pin, now: seams.now)
        let target = listed.first { $0.id == workspaceId }?.environmentId
        if case .needsPairing = pinGuard.begin(pairingId: pairingId, workspaceId: workspaceId, targetEnvironmentId: target) {
            return .needsPairing
        }
        do {
            let response = try await transport.startWorkspaceSwitch(workspaceId)
            guard let accepted = response.switchInFlight,
                  pinGuard.bind(
                    pairingId: pairingId,
                    operationId: accepted.id,
                    workspaceId: accepted.workspaceId,
                    environmentId: accepted.environmentId
                  )
            else {
                pinGuard.cancel()
                return .rejected(.wrongOperation)
            }
            let next = Attempt(guardState: pinGuard, pairingId: pairingId, switchInFlight: accepted)
            if accepted.isEnded {
                pinGuard.cancel()
                return .ended(accepted.state)
            }
            if accepted.isDone { return await complete(next, transport: transport, pairingId: pairingId) }
            attempt = next
            return .waiting(response.workspaces)
        } catch {
            pinGuard.cancel()
            throw error
        }
    }

    /// Reads the switch that waits, once. Call it again later while it answers `waiting`.
    public func finish(
        pairingId: String,
        transport: any WorkspaceSwitchTransport
    ) async -> WorkspaceSwitchResult {
        guard let waiting = attempt else { return .rejected(.noPendingSwitch) }
        guard waiting.pairingId == pairingId else {
            attempt = nil
            waiting.guardState.cancel()
            return .rejected(.pairingChanged)
        }
        // The bearer goes only to the identity the phone pinned. After a finished switch the address shows
        // the new identity, so a read that fails the pin gate ends as "not verified", with the pin kept.
        guard (try? await transport.environmentId()) == waiting.guardState.pin else {
            return await end(waiting, with: .verificationFailed)
        }
        let status: PhoneWorkspaceSwitch?
        do {
            status = try await transport.workspaceSwitch(waiting.switchInFlight.id)
        } catch {
            return await end(waiting, with: .verificationFailed)
        }
        guard let status else {
            attempt = nil
            waiting.guardState.cancel()
            return .ended("expired")
        }
        if status.isEnded {
            attempt = nil
            waiting.guardState.cancel()
            return .ended(status.state)
        }
        if status.isDone {
            return await complete(
                Attempt(guardState: waiting.guardState, pairingId: waiting.pairingId, switchInFlight: status),
                transport: transport,
                pairingId: pairingId
            )
        }
        return .waiting([])
    }

    private func end(_ waiting: Attempt, with reason: WorkspaceSwitchRejection) async -> WorkspaceSwitchResult {
        attempt = nil
        waiting.guardState.cancel()
        return .rejected(reason)
    }

    private func complete(
        _ finished: Attempt,
        transport: any WorkspaceSwitchTransport,
        pairingId: String
    ) async -> WorkspaceSwitchResult {
        attempt = nil
        let completion = await seams.proof(transport, finished.switchInFlight)
        let pairing = finished.pairingId == pairingId ? finished.pairingId : "changed"
        switch finished.guardState.complete(pairingId: pairing, completion: completion) {
        case let .rejected(reason): return .rejected(reason)
        case let .repinned(environmentId): return .switched(environmentId: environmentId)
        }
    }
}

/// What the phone shows and offers for the workspaces a computer lists.
public enum WorkspacePolicy {
    public static let unnamed = "This computer"

    /// The list as the picker may trust it: a blank or repeated id is dropped, and only the first
    /// workspace marked current stays current, so two open workspaces can never both look open.
    public static func normalized(_ workspaces: [PhoneWorkspace]) -> [PhoneWorkspace] {
        var seen = Set<String>()
        var currentSeen = false
        var kept: [PhoneWorkspace] = []
        for workspace in workspaces {
            if workspace.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                || !seen.insert(workspace.id).inserted { continue }
            var next = workspace
            next.current = workspace.current && !currentSeen
            if next.current { currentSeen = true }
            kept.append(next)
        }
        return kept
    }

    /// A computer with one workspace, or one that does not list any, gets no picker.
    public static func showsPicker(_ workspaces: [PhoneWorkspace]) -> Bool {
        normalized(workspaces).count > 1
    }

    public static func displayName(_ workspace: PhoneWorkspace) -> String {
        let trimmed = workspace.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? unnamed : trimmed
    }

    /// The second line of a picker row; nil when the row has nothing to add.
    public static func statusLine(_ workspace: PhoneWorkspace) -> String? {
        switch workspace.availability {
        case .waiting:
            let who = workspace.waitingFor?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return who.isEmpty ? "Waiting for work to finish" : "Waiting for \(who) to finish"
        case .unavailable:
            return "Not available"
        case .ready:
            return workspace.current ? "Open" : nil
        }
    }

    /// The open workspace has nothing to switch to, and one that is not available cannot be opened.
    public static func canSwitch(_ workspace: PhoneWorkspace) -> Bool {
        !workspace.current && workspace.availability != .unavailable
    }
}
