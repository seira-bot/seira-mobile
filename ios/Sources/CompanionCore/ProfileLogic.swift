import Foundation

public enum ProfileTab: String, CaseIterable, Sendable {
    case activity, settings, library, computer

    public static func from(key: String?) -> ProfileTab {
        allCases.first { $0.rawValue == key } ?? .activity
    }
}

/// What the Settings tab edits. The computer does not send a bot's
/// instructions or reply style to the phone, so those two start empty and
/// leave with a save only after the person changes them. A blank field never
/// replaces a value the person cannot see.
public struct ProfileDraft: Equatable, Sendable {
    public var name: String
    public var title: String
    public var description: String
    public var soul: String
    public var replyStyle: ReplyStyle?

    public init(name: String, title: String, description: String, soul: String = "", replyStyle: ReplyStyle? = nil) {
        self.name = name
        self.title = title
        self.description = description
        self.soul = soul
        self.replyStyle = replyStyle
    }

    /// Instructions and reply style start from what the computer sent. A
    /// computer that sent none leaves them empty, and an empty value is never sent.
    public static func of(_ bot: Bot) -> ProfileDraft {
        ProfileDraft(
            name: bot.name, title: bot.title, description: bot.description,
            soul: bot.soul ?? "", replyStyle: bot.replyStyle.flatMap(ReplyStyle.init(rawValue:))
        )
    }
}

public enum ProfileDraftRules {
    /// Only the fields that differ from the baseline, trimmed. Instructions
    /// that were never typed stay out, so they cannot blank the stored value.
    public static func patch(_ draft: ProfileDraft, baseline: ProfileDraft) -> BotProfilePatch {
        var patch = BotProfilePatch()
        patch.name = changed(draft.name, baseline.name)
        patch.title = changed(draft.title, baseline.title)
        patch.description = changed(draft.description, baseline.description)
        if let soul = changed(draft.soul, baseline.soul), !soul.isEmpty {
            patch.soul = soul
        }
        if draft.replyStyle != baseline.replyStyle { patch.replyStyle = draft.replyStyle }
        return patch
    }

    public static func isDirty(_ draft: ProfileDraft, baseline: ProfileDraft) -> Bool {
        let patch = patch(draft, baseline: baseline)
        return patch.name != nil || patch.title != nil || patch.description != nil
            || patch.soul != nil || patch.replyStyle != nil
    }

    public static func canSave(_ draft: ProfileDraft, baseline: ProfileDraft, busy: Bool) -> Bool {
        !busy && !draft.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && isDirty(draft, baseline: baseline)
    }

    private static func changed(_ value: String, _ baseline: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed == baseline.trimmingCharacters(in: .whitespacesAndNewlines) ? nil : trimmed
    }
}

public enum ProfileActivityRules {
    /// The rows that started on `today` in `calendar`, newest first.
    public static func today(_ rows: [ActivityRow], now: Date, calendar: Calendar = .current) -> [ActivityRow] {
        rows
            .compactMap { row in instant(of: row).map { ($0, row) } }
            .filter { calendar.isDate($0.0, inSameDayAs: now) }
            .sorted { $0.0 > $1.0 }
            .map(\.1)
    }

    public static func instant(of row: ActivityRow) -> Date? {
        let plain = ISO8601DateFormatter()
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fractional.date(from: row.at) ?? plain.date(from: row.at)
    }
}

public enum ProfileComputerRules {
    /// A cloud computer has its own viewer. Any other may be driven, and the
    /// computer confirms or refuses.
    public static func mayTakeControl(_ bot: Bot) -> Bool { bot.computer != "cloud" }
}

/// One thing in a bot's library: a table, the table behind a card, or a card of text.
public struct LibraryItem: Equatable, Identifiable, Sendable {
    public var id: String
    public var title: String
    public var count: Int64?
    public var table: String?
    public var cardId: String?
    public var text: String?
}

public enum ProfileLibraryRules {
    public static let pageSize = 50

    /// Tables first, then the cards that finished and hold a table or text.
    /// Charts are left out.
    public static func items(_ data: BotData) -> [LibraryItem] {
        let tables = (data.tables ?? []).map {
            LibraryItem(id: "table:\($0.name)", title: $0.name, count: $0.rowCount, table: $0.name, cardId: nil, text: nil)
        }
        let cards = (data.sheet.cards ?? [])
            .filter { $0.status == "ready" && ($0.kind == "table" || $0.kind == "text") }
            .map { card -> LibraryItem in
                let isTable = card.kind == "table"
                return LibraryItem(
                    id: "card:\(card.id)", title: card.title,
                    count: isTable ? card.rowCount : nil,
                    table: nil, cardId: isTable ? card.id : nil,
                    text: isTable ? nil : (card.text ?? "")
                )
            }
        return tables + cards
    }

    public static func request(_ item: LibraryItem, offset: Int) -> DataPageRequest {
        DataPageRequest(table: item.table, cardId: item.cardId, offset: offset, limit: pageSize)
    }

    public static func cell(_ value: JSONValue) -> String {
        switch value {
        case .null: return ""
        case let .string(text): return text
        case let .bool(flag): return flag ? "true" : "false"
        case let .number(number):
            return number.rounded() == number && abs(number) < 1e15 ? String(Int64(number)) : String(number)
        case .array, .object:
            guard let data = try? JSONEncoder().encode(value), let text = String(data: data, encoding: .utf8) else { return "" }
            return text
        }
    }

    public static func hasMore(loaded: Int, total: Int64) -> Bool { Int64(loaded) < total }
}
