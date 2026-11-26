import Foundation

public enum HomeChatFilter: String, CaseIterable, Sendable {
    case all, bots, groups

    public var showsBots: Bool { self != .groups }
    public var showsGroups: Bool { self != .bots }
}

public enum HomeSearchFilter: String, CaseIterable, Sendable {
    case all, messages, bots, groups, routines

    public var showsMessages: Bool { self == .all || self == .messages }
    public var showsBots: Bool { self == .all || self == .bots }
    public var showsGroups: Bool { self == .all || self == .groups }
    public var showsRoutines: Bool { self == .all || self == .routines }
}

public enum HomeSearch {
    public static func normalized(_ query: String) -> String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func bots(_ bots: [Bot], query: String) -> [Bot] {
        let needle = normalized(query)
        guard !needle.isEmpty else { return [] }
        return bots.filter {
            $0.hidden != true
                && ($0.name.localizedCaseInsensitiveContains(needle)
                    || $0.title.localizedCaseInsensitiveContains(needle))
        }
    }

    public static func rooms(_ rooms: [Room], query: String) -> [Room] {
        let needle = normalized(query)
        guard !needle.isEmpty else { return [] }
        return rooms.filter { $0.dm != true && $0.name.localizedCaseInsensitiveContains(needle) }
    }

    public static func routines(_ routines: [Routine], query: String) -> [Routine] {
        let needle = normalized(query)
        guard !needle.isEmpty else { return [] }
        return routines.filter { $0.name.localizedCaseInsensitiveContains(needle) }
    }
}

public enum InboxAction: Equatable, Sendable {
    case decide(allow: String, deny: String?)
    case answerInChat
}

public enum InboxLogic {
    public static func badgeCount(_ updates: [ChatUpdate]) -> Int {
        updates.filter { $0.kind == .needsYou }.count
    }

    public static func action(for card: OptionCard?) -> InboxAction {
        guard let card, card.isPending, card.isPermission, card.skillRequest == nil else { return .answerInChat }
        let plain = card.options.first { $0.trimmingCharacters(in: .whitespaces).caseInsensitiveCompare("Allow") == .orderedSame }
        guard let allow = plain ?? card.options.first(where: { OptionCard.isApproval($0) }) else {
            return .answerInChat
        }
        let deny = card.options.first { OptionCard.isRefusal($0) }
        return .decide(allow: allow, deny: deny)
    }

    /// Runs that ended, newest first. Runs still going or waiting stay out.
    public static func finishedRuns(_ runs: [RoutineRun], limit: Int = 20) -> [RoutineRun] {
        let ended = runs.filter { $0.status != "running" && $0.status != "waiting" && $0.status != "scheduled" }
        return Array(ended.sorted { $0.scheduledFor > $1.scheduledFor }.prefix(max(0, limit)))
    }
}

public struct NewBotDraft: Equatable, Sendable {
    public var name: String
    public var job: String

    public init(name: String = "", job: String = "") {
        self.name = name
        self.job = job
    }

    public var canCreate: Bool { !HomeSearch.normalized(name).isEmpty }

    public var spec: NewBot {
        let trimmedJob = HomeSearch.normalized(job)
        return NewBot(name: HomeSearch.normalized(name), title: trimmedJob.isEmpty ? nil : trimmedJob)
    }
}
