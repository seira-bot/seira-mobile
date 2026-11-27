import Foundation

public enum HomeGroupKind: String, Sendable {
    case bots, department, groups
}

public struct HomeGroup: Identifiable, Equatable, Sendable {
    public var kind: HomeGroupKind
    public var name: String?
    public var bots: [Bot]
    public var rooms: [Room]

    public init(kind: HomeGroupKind, name: String?, bots: [Bot], rooms: [Room]) {
        self.kind = kind
        self.name = name
        self.bots = bots
        self.rooms = rooms
    }

    /// Stable across edits: fold state and list identity both use it.
    public var key: String {
        switch kind {
        case .bots: return "bots"
        case .department: return "department:\(name ?? "")"
        case .groups: return "groups"
        }
    }

    public var id: String { key }
}

public struct HomeLayout: Equatable, Sendable {
    /// Advanced mode lists the chief on its own row above the groups.
    public var chief: Bot?
    public var groups: [HomeGroup]
    /// The department the list is limited to, when a filter is set.
    public var department: String?

    /// A department with no bot still shows, so only bots and group chats count.
    public var isEmpty: Bool {
        chief == nil && groups.allSatisfy { $0.kind != .department && $0.bots.isEmpty && $0.rooms.isEmpty }
    }

    /// The keys of every group, for pruning the fold state.
    public var keys: Set<String> { Set(groups.map(\.key)) }
}

public enum HomeLayoutRules {
    public static let tileColumns = 4

    public static func layout(_ state: CompanionState, advanced: Bool, department: String?) -> HomeLayout {
        let sections = departments(state)
        if let department {
            let kept = sections.filter { $0.name == department }
            return HomeLayout(chief: nil, groups: kept, department: kept.isEmpty ? nil : department)
        }
        let chief = state.unsectionedChief
        let others = state.pinnedBots + state.unsectionedBots
        let shown = Set(sections.compactMap(\.name))
        let rooms = state.rooms.filter {
            $0.dm != true && !shown.contains($0.section?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "")
        } + state.botChats
        let fleetEmpty = state.bots.allSatisfy { $0.hidden == true } && state.rooms.isEmpty && sections.isEmpty
        var groups: [HomeGroup] = []
        if advanced {
            if !others.isEmpty {
                groups.append(HomeGroup(kind: .bots, name: nil, bots: others, rooms: []))
            }
        } else if !fleetEmpty {
            groups.append(HomeGroup(kind: .bots, name: nil, bots: (chief.map { [$0] } ?? []) + others, rooms: []))
        }
        groups += sections
        if !fleetEmpty {
            groups.append(HomeGroup(kind: .groups, name: nil, bots: [], rooms: rooms))
        }
        return HomeLayout(chief: advanced ? chief : nil, groups: groups, department: nil)
    }

    /// A department exists when a bot sits in it or the computer declares it.
    /// A section that only channels name does not make one, so its channels
    /// go to the group chats. The channels of a department follow its bots.
    private static func departments(_ state: CompanionState) -> [HomeGroup] {
        state.sidebarSections
            .filter { !$0.chiefs.isEmpty || !$0.bots.isEmpty || state.declaredSections.contains($0.name) }
            .map { HomeGroup(kind: .department, name: $0.name, bots: $0.chiefs + $0.bots, rooms: $0.channels) }
    }

    public static func tileRows(_ bots: [Bot], columns: Int = tileColumns) -> [[Bot]] {
        guard columns > 0 else { return [] }
        return stride(from: 0, to: bots.count, by: columns).map {
            Array(bots[$0 ..< min($0 + columns, bots.count)])
        }
    }
}

public enum HomeDot: Sendable {
    case none, waiting, problem, working
}

public enum HomeDotRules {
    public static func of(_ bot: Bot, _ status: RosterRowStatus) -> HomeDot {
        if status == .waitingOnYou { return .waiting }
        if bot.activity == "failed" { return .problem }
        if status == .working { return .working }
        return .none
    }

    public static func tileShows(_ dot: HomeDot) -> Bool { dot == .waiting || dot == .problem }

    /// How many of the bots wait for the person; `waiting` holds the ids of bots with an open card.
    public static func waitingCount(_ bots: [Bot], waiting: Set<String>) -> Int {
        bots.filter { of($0, $0.rosterStatus(hasPendingCard: waiting.contains($0.id))) == .waiting }.count
    }
}

/// Which groups the person folded. Everything starts open, so the stored set holds only folded keys.
public enum FoldRules {
    public static func isOpen(_ folded: Set<String>, _ key: String) -> Bool { !folded.contains(key) }

    public static func toggle(_ folded: Set<String>, _ key: String) -> Set<String> {
        var next = folded
        if !next.insert(key).inserted { next.remove(key) }
        return next
    }

    public static func prune(_ folded: Set<String>, _ keys: Set<String>) -> Set<String> {
        folded.intersection(keys)
    }

    /// `@SceneStorage` keeps a string, so the set travels as sorted lines.
    public static func encode(_ folded: Set<String>) -> String {
        folded.sorted().joined(separator: "\n")
    }

    public static func decode(_ text: String) -> Set<String> {
        Set(text.split(separator: "\n").map(String.init))
    }
}

public struct SectionCall: Equatable, Sendable {
    public var name: String
    public var botIds: [String]

    public init(_ name: String, _ botIds: [String]) {
        self.name = name
        self.botIds = botIds
    }
}

public struct DepartmentPlacement: Equatable, Sendable {
    public var added: [String]
    public var removed: [String]

    public init(added: [String], removed: [String]) {
        self.added = added
        self.removed = removed
    }

    public var isEmpty: Bool { added.isEmpty && removed.isEmpty }

    /// One call to add, then one with an empty name to remove; an empty list sends nothing.
    public func calls(_ department: String) -> [SectionCall] {
        var calls: [SectionCall] = []
        if !added.isEmpty { calls.append(SectionCall(department, added)) }
        if !removed.isEmpty { calls.append(SectionCall("", removed)) }
        return calls
    }
}

/// What the person ticked and cleared in the "Add bots" sheet, and nothing else.
/// Save applies it to the department as it is at that moment, so a change made
/// on the computer while the sheet is open stays.
public struct DepartmentEdits: Equatable, Sendable {
    public private(set) var added: Set<String> = []
    public private(set) var removed: Set<String> = []

    public init() {}

    public func isChecked(_ id: String, members: Set<String>) -> Bool {
        added.contains(id) || (members.contains(id) && !removed.contains(id))
    }

    public mutating func toggle(_ id: String, members: Set<String>) {
        if isChecked(id, members: members) {
            added.remove(id)
            if members.contains(id) { removed.insert(id) }
        } else {
            removed.remove(id)
            if !members.contains(id) { added.insert(id) }
        }
    }

    /// The calls to send now: the bots the person added that are not in yet,
    /// and the bots the person cleared that are still in.
    public func placement(_ bots: [Bot], members: Set<String>) -> DepartmentPlacement {
        DepartmentPlacement(
            added: bots.map(\.id).filter { added.contains($0) && !members.contains($0) },
            removed: bots.map(\.id).filter { removed.contains($0) && members.contains($0) }
        )
    }
}

public enum DepartmentRules {
    public enum NameProblem: Sendable { case empty, exists }

    public static let maxNameLength = 60

    /// The bots the person can place: not hidden, not the chief.
    public static func selectable(_ state: CompanionState) -> [Bot] {
        state.bots.filter { $0.hidden != true && $0.chiefOfStaff != true }
    }

    public static func members(_ bots: [Bot], _ department: String) -> Set<String> {
        Set(bots.filter { $0.section?.trimmingCharacters(in: .whitespacesAndNewlines) == department }.map(\.id))
    }

    public static func canSave(_ placement: DepartmentPlacement, saving: Bool) -> Bool {
        !placement.isEmpty && !saving
    }

    public static func clampName(_ raw: String) -> String { String(raw.prefix(maxNameLength)) }

    public static func nameProblem(_ raw: String, _ existing: [String]) -> NameProblem? {
        let name = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty { return .empty }
        return existing.contains { $0.caseInsensitiveCompare(name) == .orderedSame } ? .exists : nil
    }
}
