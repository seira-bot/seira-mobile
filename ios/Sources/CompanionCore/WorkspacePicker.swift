import Combine
import Foundation

/// The line under the name of a picker row.
public enum WorkspaceStatus: Hashable, Sendable {
    case none
    case open
    case waiting
    case unavailable
}

/// One row of the picker. The picker is read-only: a row shows the state of a workspace and offers no action.
public struct WorkspaceRow: Hashable, Sendable {
    public var workspace: PhoneWorkspace
    public var status: WorkspaceStatus

    public init(workspace: PhoneWorkspace, status: WorkspaceStatus) {
        self.workspace = workspace
        self.status = status
    }
}

public enum WorkspacePicker {
    public static func currentID(_ workspaces: [PhoneWorkspace]) -> String? {
        workspaces.first { $0.current }?.id
    }

    public static func isWaiting(_ workspaces: [PhoneWorkspace]) -> Bool {
        workspaces.contains { $0.availability == .waiting }
    }

    /// The rows of the picker. No row switches or cancels anything: a switch needs the guarded flow of the phone
    /// session and an authenticated transport (seira#540, seira#546), and a cancel needs an explicit cancel bound to
    /// the pending operation. Until then the picker only shows what the computer lists.
    public static func rows(_ workspaces: [PhoneWorkspace]) -> [WorkspaceRow] {
        WorkspacePolicy.normalized(workspaces).map { workspace in
            if workspace.current { return WorkspaceRow(workspace: workspace, status: .open) }
            switch workspace.availability {
            case .unavailable: return WorkspaceRow(workspace: workspace, status: .unavailable)
            case .waiting: return WorkspaceRow(workspace: workspace, status: .waiting)
            case .ready: return WorkspaceRow(workspace: workspace, status: .none)
            }
        }
    }

    /// The name a row shows next to "Waiting for"; nil when the computer named nobody.
    public static func waitingFor(_ workspace: PhoneWorkspace) -> String? {
        let trimmed = workspace.waitingFor?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }

    /// The color of a workspace as the desktop rail shows it, as 0xRRGGBB; nil for anything that is not
    /// six hex digits.
    public static func colorValue(_ value: String?) -> UInt32? {
        var text = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if text.hasPrefix("#") { text.removeFirst() }
        guard text.count == 6, text.allSatisfy(\.isHexDigit) else { return nil }
        return UInt32(text, radix: 16)
    }

    /// The name of a workspace, or nil when it has none and the sheet words it itself.
    public static func name(_ workspace: PhoneWorkspace) -> String? {
        let trimmed = workspace.name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return trimmed.isEmpty ? nil : trimmed
    }
}

/// What the picker model reads from the session. Every call names the pairing it is for.
@MainActor
public protocol WorkspaceSource: AnyObject {
    /// The pairing that is active now, or nil when there is none.
    var activePairingID: String? { get }

    /// The list, or nil when the computer offers no picker (404, or 403 for a pairing that may not read it). It reads
    /// nothing, and throws, when `pairingID` is not the active pairing.
    func workspaces(pairingID: String) async throws -> [PhoneWorkspace]?

    /// The computer now serves another workspace: drop what the old one showed and read the new one. A stale pairing
    /// does nothing.
    func workspaceChanged(pairingID: String) async
}

public struct WorkspacePickerState: Equatable, Sendable {
    public var workspaces: [PhoneWorkspace] = []

    public init(workspaces: [PhoneWorkspace] = []) {
        self.workspaces = workspaces
    }

    /// One entry, or none, gets no picker.
    public var visible: Bool { WorkspacePolicy.showsPicker(workspaces) }
    public var rows: [WorkspaceRow] { WorkspacePicker.rows(workspaces) }
    public var current: PhoneWorkspace? { WorkspacePolicy.normalized(workspaces).first { $0.current } }

    /// True while a switch waits, so the sheet reads the list again until it ends.
    public var polling: Bool { visible && WorkspacePicker.isWaiting(workspaces) }
}

/// The read-only state of the workspace picker. It is bound to one pairing at a time: a read for another pairing
/// starts from an empty list, and an answer that arrives after the pairing changed is dropped. It has no action: it
/// never asks the computer to switch, open or cancel.
@MainActor
public final class WorkspacePickerModel: ObservableObject {
    @Published public private(set) var state = WorkspacePickerState()

    private let source: WorkspaceSource
    private var boundPairingID: String?

    public init(source: WorkspaceSource) {
        self.source = source
    }

    /// Reads the list of `pairingID`. A read that fails keeps the last list of this pairing; nothing of another
    /// pairing is ever shown.
    public func refresh(pairingID: String?) async {
        if boundPairingID != pairingID {
            boundPairingID = pairingID
            state = WorkspacePickerState()
        }
        guard let pairingID, source.activePairingID == pairingID else { return }
        let list: [PhoneWorkspace]?
        do {
            list = try await source.workspaces(pairingID: pairingID)
        } catch {
            return
        }
        if Task.isCancelled { return }
        guard boundPairingID == pairingID, source.activePairingID == pairingID else { return }
        await adopt(list ?? [], pairingID: pairingID)
    }

    private func adopt(_ list: [PhoneWorkspace], pairingID: String) async {
        let normalized = WorkspacePolicy.normalized(list)
        let before = WorkspacePicker.currentID(state.workspaces)
        let after = WorkspacePicker.currentID(normalized)
        state.workspaces = normalized
        if let before, let after, before != after,
           boundPairingID == pairingID, source.activePairingID == pairingID {
            await source.workspaceChanged(pairingID: pairingID)
        }
    }
}
