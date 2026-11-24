import Combine
import Foundation

/// What the person asks the computer to do from one picker row.
public enum WorkspaceAction: Hashable, Sendable {
    /// Open a workspace that is ready.
    case open
    /// Open a workspace that waits, without waiting for the bot to finish.
    case openNow
    /// Stop a switch that waits and stay on the open workspace.
    case cancelWait
}

/// The line under the name of a picker row.
public enum WorkspaceStatus: Hashable, Sendable {
    case none
    case open
    case waiting
    case unavailable
    case pairAgain
}

/// One row of the picker. `tap` is what the whole row does and `button` is what its own button
/// does; both are nil when the row offers nothing.
public struct WorkspaceRow: Hashable, Sendable {
    public var workspace: PhoneWorkspace
    public var status: WorkspaceStatus
    public var tap: WorkspaceAction?
    public var button: WorkspaceAction?

    public init(workspace: PhoneWorkspace, status: WorkspaceStatus, tap: WorkspaceAction?, button: WorkspaceAction?) {
        self.workspace = workspace
        self.status = status
        self.tap = tap
        self.button = button
    }

    public func offers(_ action: WorkspaceAction) -> Bool {
        tap == action || button == action
    }
}

/// Why the last request did not do what the person asked.
public enum WorkspaceNotice: Hashable, Sendable {
    /// 403: the owner did not let this phone open other workspaces.
    case turnedOff
    /// 409: the workspace cannot be opened now.
    case unavailable
    /// 404: the workspace is not on the computer any more.
    case gone
    /// The computer did not answer, or answered with an error this build has no words for.
    case failed
    /// The phone cannot prove that the computer it reaches after the switch is the one it paired with.
    case needsPairing
}

public enum WorkspacePicker {
    /// Whether this phone may ask the computer to open another workspace. A pairing that pins the
    /// identity of the computer would meet a different identity after the switch, and the phone has
    /// no proof yet for that (seira#540). Such a pairing never starts a switch and never moves its
    /// pin: the picker shows the pairing again instead.
    public static func canOpen(_ connection: Connection?) -> Bool {
        guard let connection else { return false }
        return !connection.pairedWithServer
    }

    public static func currentID(_ workspaces: [PhoneWorkspace]) -> String? {
        workspaces.first { $0.current }?.id
    }

    public static func isWaiting(_ workspaces: [PhoneWorkspace]) -> Bool {
        workspaces.contains { $0.availability == .waiting }
    }

    /// The rows of the picker. With `canOpen` false, no row starts a switch.
    public static func rows(_ workspaces: [PhoneWorkspace], canOpen: Bool) -> [WorkspaceRow] {
        let list = WorkspacePolicy.normalized(workspaces)
        let waiting = isWaiting(list)
        return list.map { workspace in
            if workspace.current {
                return WorkspaceRow(
                    workspace: workspace, status: .open, tap: nil, button: waiting ? .cancelWait : nil
                )
            }
            switch workspace.availability {
            case .unavailable:
                return WorkspaceRow(workspace: workspace, status: .unavailable, tap: nil, button: nil)
            case .waiting:
                return WorkspaceRow(
                    workspace: workspace, status: .waiting, tap: nil, button: canOpen ? .openNow : nil
                )
            case .ready:
                return canOpen
                    ? WorkspaceRow(workspace: workspace, status: .none, tap: .open, button: nil)
                    : WorkspaceRow(workspace: workspace, status: .pairAgain, tap: nil, button: nil)
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

    public static func notice(forStatus code: Int) -> WorkspaceNotice {
        switch code {
        case 403: return .turnedOff
        case 409: return .unavailable
        case 404: return .gone
        default: return .failed
        }
    }
}

/// What the picker model reads from and asks of the session.
@MainActor
public protocol WorkspaceSource: AnyObject {
    /// The list, or nil when the computer offers no picker (404, or 403 for a pairing that may not read it).
    func workspaces() async throws -> [PhoneWorkspace]?

    func requestOpen(id: String, now: Bool) async throws -> [PhoneWorkspace]

    /// The computer now serves another workspace: drop what the old one showed and read the new one.
    func workspaceChanged() async
}

public struct WorkspacePickerState: Equatable, Sendable {
    public var workspaces: [PhoneWorkspace] = []
    public var canOpen = false
    /// The workspace a request is out for.
    public var busyID: String?
    public var notice: WorkspaceNotice?

    public init(
        workspaces: [PhoneWorkspace] = [],
        canOpen: Bool = false,
        busyID: String? = nil,
        notice: WorkspaceNotice? = nil
    ) {
        self.workspaces = workspaces
        self.canOpen = canOpen
        self.busyID = busyID
        self.notice = notice
    }

    /// One entry, or none, gets no picker.
    public var visible: Bool { WorkspacePolicy.showsPicker(workspaces) }
    public var rows: [WorkspaceRow] { WorkspacePicker.rows(workspaces, canOpen: canOpen) }
    public var current: PhoneWorkspace? { WorkspacePolicy.normalized(workspaces).first { $0.current } }

    /// True while a switch waits, so the sheet reads the list again until it ends.
    public var polling: Bool { visible && WorkspacePicker.isWaiting(workspaces) }
}

/// The state of the workspace picker. It never touches the pin of a pairing: `WorkspacePicker.canOpen`
/// decides if a row may start a switch, and a row that may not shows the pairing again.
@MainActor
public final class WorkspacePickerModel: ObservableObject {
    @Published public private(set) var state = WorkspacePickerState()

    private let source: WorkspaceSource
    private let canOpen: () -> Bool

    public init(source: WorkspaceSource, canOpen: @escaping () -> Bool) {
        self.source = source
        self.canOpen = canOpen
    }

    public func dismissNotice() {
        state.notice = nil
    }

    /// Reads the list. A list read that fails keeps the last list: the person is still on the same workspace.
    public func refresh() async {
        let list: [PhoneWorkspace]?
        do {
            list = try await source.workspaces()
        } catch {
            return
        }
        if Task.isCancelled { return }
        await adopt(list ?? [])
    }

    public func perform(_ action: WorkspaceAction, id: String) async {
        guard state.busyID == nil else { return }
        guard let row = state.rows.first(where: { $0.workspace.id == id }), row.offers(action) else {
            if state.rows.first(where: { $0.workspace.id == id })?.status == .pairAgain {
                state.notice = .needsPairing
            }
            return
        }
        state.busyID = id
        state.notice = nil
        defer { state.busyID = nil }
        do {
            let list = try await source.requestOpen(id: id, now: action == .openNow)
            if Task.isCancelled { return }
            await adopt(list)
        } catch let APIError.status(code, _) {
            state.notice = WorkspacePicker.notice(forStatus: code)
            await refresh()
        } catch is CancellationError {
            return
        } catch {
            state.notice = .failed
        }
    }

    private func adopt(_ list: [PhoneWorkspace]) async {
        let normalized = WorkspacePolicy.normalized(list)
        let before = WorkspacePicker.currentID(state.workspaces)
        let after = WorkspacePicker.currentID(normalized)
        state.workspaces = normalized
        state.canOpen = canOpen()
        if let before, let after, before != after { await source.workspaceChanged() }
    }
}
