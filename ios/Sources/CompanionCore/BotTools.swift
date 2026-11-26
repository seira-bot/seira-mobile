import Foundation

/// A JSON value kept as the computer sent it.
public enum JSONValue: Codable, Hashable, Sendable {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    public init(from decoder: Decoder) throws {
        let single = try decoder.singleValueContainer()
        if single.decodeNil() {
            self = .null
        } else if let value = try? single.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? single.decode(Double.self) {
            self = .number(value)
        } else if let value = try? single.decode(String.self) {
            self = .string(value)
        } else if let value = try? single.decode([JSONValue].self) {
            self = .array(value)
        } else {
            self = .object(try single.decode([String: JSONValue].self))
        }
    }

    public func encode(to encoder: Encoder) throws {
        var single = encoder.singleValueContainer()
        switch self {
        case .null: try single.encodeNil()
        case let .bool(value): try single.encode(value)
        case let .number(value): try single.encode(value)
        case let .string(value): try single.encode(value)
        case let .array(value): try single.encode(value)
        case let .object(value): try single.encode(value)
        }
    }

    /// The cell as a person would read it.
    public var display: String {
        switch self {
        case .null: return ""
        case let .bool(value): return value ? "true" : "false"
        case let .number(value):
            return value == value.rounded() && abs(value) < 1e15 ? String(Int64(value)) : String(value)
        case let .string(value): return value
        case .array, .object:
            guard let data = try? JSONEncoder().encode(self), let text = String(data: data, encoding: .utf8) else { return "" }
            return text
        }
    }
}

/// How a bot's replies are shaped. Absent means `.default`.
public enum ReplyStyle: String, Codable, CaseIterable, Hashable, Sendable {
    case `default`
    case conversational
}

/// What a phone may say when it makes a bot. A field left nil is left to the
/// computer, which picks a name, colour and look when none is given.
/// `retryKey` makes a repeated send return the same bot.
public struct NewBot: Equatable, Sendable {
    public var name: String?
    public var title: String?
    public var description: String?
    public var soul: String?
    public var replyStyle: ReplyStyle?
    public var color: String?
    public var mascotBody: String?
    public var mascotAccessories: [String]?
    public var mascotFaceColor: String?
    public var mascotExpression: String?
    public var retryKey: String?

    public init(
        name: String? = nil,
        title: String? = nil,
        description: String? = nil,
        soul: String? = nil,
        replyStyle: ReplyStyle? = nil,
        color: String? = nil,
        mascotBody: String? = nil,
        mascotAccessories: [String]? = nil,
        mascotFaceColor: String? = nil,
        mascotExpression: String? = nil,
        retryKey: String? = nil
    ) {
        self.name = name
        self.title = title
        self.description = description
        self.soul = soul
        self.replyStyle = replyStyle
        self.color = color
        self.mascotBody = mascotBody
        self.mascotAccessories = mascotAccessories
        self.mascotFaceColor = mascotFaceColor
        self.mascotExpression = mascotExpression
        self.retryKey = retryKey
    }

    public var isEmpty: Bool { self == NewBot() }

    var wireBody: [String: Any]? {
        guard !isEmpty else { return nil }
        var body: [String: Any] = [:]
        if let name { body["name"] = name }
        if let title { body["title"] = title }
        if let description { body["description"] = description }
        if let soul { body["soul"] = soul }
        if let replyStyle { body["replyStyle"] = replyStyle.rawValue }
        if let color { body["color"] = color }
        if let mascotBody { body["mascotBody"] = mascotBody }
        if let mascotAccessories { body["mascotAccessories"] = mascotAccessories }
        if let mascotFaceColor { body["mascotFaceColor"] = mascotFaceColor }
        if let mascotExpression { body["mascotExpression"] = mascotExpression }
        if let retryKey { body["retryKey"] = retryKey }
        return body
    }
}

/// What the computer did with a queued message the person asked to send into the running turn.
public enum SteerResult: Equatable, Sendable {
    /// The held messages went into the running turn. `queueIds` joined it.
    case steered(queueIds: [String])
    /// The turn cannot take them now; they stay queued. `blockedBy` names why, when the computer says.
    case stillQueued(blockedBy: String?)
    /// The entry left the queue before the request arrived.
    case gone
}

struct SteerBody: Decodable {
    var steered: Bool?
    var queued: Bool?
    var queueIds: [String]?
    var blockedBy: String?

    var result: SteerResult {
        steered == true ? .steered(queueIds: queueIds ?? []) : .stillQueued(blockedBy: blockedBy)
    }
}

/// How much a bot may do in one conversation before it asks. A phone may set
/// the first four; `custom` is made and left only in the desktop app.
public enum ApprovalMode: String, CaseIterable, Hashable, Sendable {
    case ask, edits, auto, full, custom

    public var phoneCanSet: Bool { self != .custom }

    public static let phoneChoices: [ApprovalMode] = [.ask, .edits, .auto, .full]

    public static func parse(_ wire: String?) -> ApprovalMode? {
        wire.flatMap { ApprovalMode(rawValue: $0) }
    }
}

extension Bot {
    /// The level a conversation runs at, from its task record and then its bot's.
    /// Nil when the computer sent none.
    public func approvalLevel(in threadId: String) -> ApprovalMode? {
        guard let shown = projected(forThread: threadId) else { return nil }
        if let mode = ApprovalMode.parse(shown.approvalMode) { return mode }
        return shown.autoApprove.map { $0 ? .auto : .ask }
    }
}

/// Why the computer refused a change of approval level. `message` is the computer's own sentence.
public enum ApprovalModeRefusal: Equatable, Sendable {
    case fullNotAllowedHere(String?)
    case leaveCustomOnDesktop(String?)
    case customOnDesktop(String?)
    case localAutoNeedsAcknowledgement(String?)
    case ownerOnly(String?)
    case providerUnsupported(String?)
    case stillConfirming(String?)
    case other(String?)

    public var message: String? {
        switch self {
        case let .fullNotAllowedHere(text), let .leaveCustomOnDesktop(text), let .customOnDesktop(text),
             let .localAutoNeedsAcknowledgement(text), let .ownerOnly(text), let .providerUnsupported(text),
             let .stillConfirming(text), let .other(text):
            return text
        }
    }

    /// Matched on the computer's own wording: the refusals share a few status
    /// codes and carry no code field.
    public static func from(_ error: Error) -> ApprovalModeRefusal {
        guard case let APIError.status(code, text) = error else {
            return .other((error as? APIError)?.errorDescription ?? error.localizedDescription)
        }
        func has(_ part: String) -> Bool { text?.localizedCaseInsensitiveContains(part) == true }
        switch code {
        case 403 where has("shared company workspace"): return .fullNotAllowedHere(text)
        case 403 where has("Leaving Custom"): return .leaveCustomOnDesktop(text)
        case 403 where has("Custom"): return .customOnDesktop(text)
        case 403 where has("only its owner"): return .ownerOnly(text)
        case 400 where has("acknowledgeLocalAuto"): return .localAutoNeedsAcknowledgement(text)
        case 400 where has("does not support the selected approval level"): return .providerUnsupported(text)
        case 409 where has("still being confirmed"): return .stillConfirming(text)
        default: return .other(text)
        }
    }
}

public enum ApprovalModeResult: Equatable, Sendable {
    case changed(Bot, ApprovalMode?)
    case refused(ApprovalModeRefusal)
}

struct TaskPatchResponse: Decodable {
    var bot: Bot
}

/// A skill of a bot, by name. A phone only reads the list.
public struct BotSkill: Codable, Hashable, Identifiable, Sendable {
    public var name: String
    public var description: String
    public var enabled: Bool
    public var editable: Bool?
    public var source: String?
    public var license: String?
    public var compatibility: String?
    public var warnings: [String]
    /// private for the bot's own skill, library for one shared by the workspace.
    public var origin: String?

    public var id: String { name }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        name = try values.decode(String.self, forKey: .name)
        description = try values.decodeIfPresent(String.self, forKey: .description) ?? ""
        enabled = try values.decodeIfPresent(Bool.self, forKey: .enabled) ?? false
        editable = try values.decodeIfPresent(Bool.self, forKey: .editable)
        source = try values.decodeIfPresent(String.self, forKey: .source)
        license = try values.decodeIfPresent(String.self, forKey: .license)
        compatibility = try values.decodeIfPresent(String.self, forKey: .compatibility)
        warnings = try values.decodeIfPresent([String].self, forKey: .warnings) ?? []
        origin = try values.decodeIfPresent(String.self, forKey: .origin)
    }
}

struct BotSkillsResponse: Decodable {
    var skills: [BotSkill]?
}

public enum ComputerControlAction: String, Sendable {
    case take, release, check
}

public struct DataError: Codable, Hashable, Sendable {
    public var code: String
    public var message: String
    public var sql: String?
    public var line: Int?
    public var hint: String?
    public var retryable: Bool?
    public var candidates: [String]?
}

public struct DataColumn: Codable, Hashable, Sendable {
    public var name: String
    public var type: String
    public var nullPct: Double?
    public var approxUnique: Double?
    public var min: String?
    public var max: String?
    public var sample: [String]?
}

public struct DataTable: Codable, Hashable, Sendable {
    public var name: String
    public var sqlName: String?
    public var rowCount: Int64?
    public var columns: [DataColumn]?
}

public struct DataSource: Codable, Hashable, Sendable {
    public var name: String
    public var sqlName: String?
    public var kind: String
    public var source: String?
    public var rowCount: Int64?
    public var columns: [DataColumn]?
    public var loadedAt: String?
}

/// One card on a bot's data sheet. `chart`, `vegaLite` and `reduction` are kept as sent.
public struct DataCard: Codable, Hashable, Identifiable, Sendable {
    public var id: String
    /// table, chart or text
    public var kind: String
    public var title: String
    public var sql: String?
    public var result: String?
    public var columns: [DataColumn]?
    public var rowCount: Int64?
    public var truncated: Bool?
    public var chart: [String: JSONValue]?
    public var vegaLite: [String: JSONValue]?
    public var reduction: [String: JSONValue]?
    public var text: String?
    /// running, ready or failed
    public var status: String
    public var error: DataError?
    /// bot or person
    public var by: String?
    public var pinned: Bool?
    public var createdAt: String?
    public var updatedAt: String?
    public var elapsedMs: Double?
}

public struct DataSheet: Codable, Hashable, Sendable {
    public var version: Int?
    public var botId: String?
    public var cards: [DataCard]?
    public var sources: [DataSource]?
    public var updatedAt: String?
}

/// A bot's data sheet and the tables in its database.
public struct BotData: Codable, Hashable, Sendable {
    public var sheet: DataSheet
    public var tables: [DataTable]?
}

/// One page of rows. Cells are JSON: big numbers arrive as strings.
public struct DataPage: Codable, Hashable, Sendable {
    public var columns: [DataColumn]?
    public var rows: [[JSONValue]]
    public var rowCount: Int64?
    public var offset: Int?
}

/// Ask for rows of exactly one source: a table by `table` or a card's result by `cardId`.
public struct DataPageRequest: Equatable, Sendable {
    public static let defaultLimit = 100
    public static let maxLimit = 1_024

    public var table: String?
    public var cardId: String?
    public var offset: Int
    public var limit: Int
    public var sortColumn: String?
    public var sortDescending: Bool
    public var filter: String?
    public var filterColumn: String?

    public init(
        table: String? = nil,
        cardId: String? = nil,
        offset: Int = 0,
        limit: Int = DataPageRequest.defaultLimit,
        sortColumn: String? = nil,
        sortDescending: Bool = false,
        filter: String? = nil,
        filterColumn: String? = nil
    ) {
        self.table = table
        self.cardId = cardId
        self.offset = offset
        self.limit = limit
        self.sortColumn = sortColumn
        self.sortDescending = sortDescending
        self.filter = filter
        self.filterColumn = filterColumn
    }
}

/// Two bots that talk in a private room of their own.
public struct TeamCollaboration: Codable, Hashable, Sendable {
    public var groupId: String
    public var botIds: [String]
    public var lastAt: Double
}

/// Work one bot handed to another that has not finished.
public struct TeamHandoff: Codable, Hashable, Sendable {
    public var sourceBotId: String
    public var targetBotId: String
    public var threadId: String
    public var groupId: String?
}

/// Who works with whom. It carries ids, links and times, never message text.
public struct TeamMap: Codable, Hashable, Sendable {
    public var collaborations: [TeamCollaboration]
    public var queued: [TeamHandoff]
    public var running: [TeamHandoff]

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        collaborations = try values.decodeIfPresent([TeamCollaboration].self, forKey: .collaborations) ?? []
        queued = try values.decodeIfPresent([TeamHandoff].self, forKey: .queued) ?? []
        running = try values.decodeIfPresent([TeamHandoff].self, forKey: .running) ?? []
    }
}

public enum TeamMemoryKind: String, CaseIterable, Hashable, Sendable {
    case person, place, decision, term
}
