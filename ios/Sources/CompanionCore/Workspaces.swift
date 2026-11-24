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
    public var name: String?
    public var color: String?
    public var current: Bool
    public var state: String
    public var waitingFor: String?

    public init(
        id: String,
        name: String? = nil,
        color: String? = nil,
        current: Bool = false,
        state: String = "ready",
        waitingFor: String? = nil
    ) {
        self.id = id
        self.name = name
        self.color = color
        self.current = current
        self.state = state
        self.waitingFor = waitingFor
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, color, current, state, waitingFor
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
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
