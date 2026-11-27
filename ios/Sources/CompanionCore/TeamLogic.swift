import Foundation

/// One department of the team map. `name` is nil for the bots that belong to none.
public struct TeamGroup: Equatable, Identifiable, Sendable {
    public var name: String?
    public var bots: [Bot]

    public var id: String { name ?? "" }
}

public struct TeamLayout: Equatable, Sendable {
    public var chief: Bot?
    public var groups: [TeamGroup]

    public var isEmpty: Bool { chief == nil && groups.isEmpty }
}

public enum TeamStatus: Sendable {
    case working, waiting
}

public enum TeamMapRules {
    /// The Chief of Staff first, then each department with its own chiefs
    /// ahead of its bots, then the bots that belong to no department.
    public static func layout(_ state: CompanionState) -> TeamLayout {
        let chief = state.unsectionedChief
            ?? state.bots.first { $0.hidden != true && $0.chiefOfStaff == true }
        let sections = state.sidebarSections
            .map { TeamGroup(name: $0.name, bots: $0.chiefs + $0.bots) }
            .filter { !$0.bots.isEmpty || state.declaredSections.contains($0.name ?? "") }
        let general = state.unsectionedBots + state.pinnedBots
        let groups = sections + (general.isEmpty ? [] : [TeamGroup(name: nil, bots: general)])
        return TeamLayout(chief: chief, groups: groups)
    }

    public static func status(_ map: TeamMap?, botId: String) -> TeamStatus? {
        guard let map else { return nil }
        if map.running.contains(where: { $0.targetBotId == botId }) { return .working }
        if map.queued.contains(where: { $0.targetBotId == botId }) { return .waiting }
        return nil
    }
}

public enum TeamMemoryRules {
    public static let nameLimit = 120
    public static let detailLimit = 600

    /// The department names the notes are kept under, with the general one first.
    public static func sections(_ state: CompanionState) -> [String] {
        [""] + state.sidebarSections.map(\.name)
    }

    /// The first line is the note. Anything after it is its detail. A blank
    /// note is nothing.
    public static func split(_ text: String) -> (name: String, detail: String)? {
        let lines = text.trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
        guard let first = lines.first else { return nil }
        let name = String(first.prefix(nameLimit)).trimmingCharacters(in: .whitespaces)
        let overflow = String(first.dropFirst(nameLimit)).trimmingCharacters(in: .whitespaces)
        let rest = ([overflow] + lines.dropFirst()).filter { !$0.isEmpty }
        return (name, String(rest.joined(separator: "\n").prefix(detailLimit)))
    }

    public static func accepted(_ entries: [TeamMemoryEntry]) -> [TeamMemoryEntry] {
        entries.filter { $0.status == "accepted" }
    }

    public static func proposed(_ entries: [TeamMemoryEntry]) -> [TeamMemoryEntry] {
        entries.filter { $0.status == "proposed" }
    }

    /// Whether `entry` changed under the person: it is gone, or it carries another revision.
    public static func changedSince(_ entry: TeamMemoryEntry, latest: [TeamMemoryEntry]) -> Bool {
        guard let now = latest.first(where: { $0.id == entry.id }) else { return true }
        return now.revision != entry.revision
    }
}
