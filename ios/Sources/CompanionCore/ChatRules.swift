import Foundation

public enum ChatFindRules {
    /// Ids of the messages whose words hold `query`, oldest first. Tool chips,
    /// screens and digests carry no words to find.
    public static func hits(_ messages: [Message], query: String) -> [String] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else { return [] }
        return messages.filter { message in
            guard let text = searchableText(message) else { return false }
            return text.range(of: needle, options: [.caseInsensitive, .diacriticInsensitive]) != nil
        }.map(\.id)
    }

    public static func searchableText(_ message: Message) -> String? {
        switch message.kind {
        case .activity, .screen, .digest, .secret: return nil
        default:
            guard message.text != nil else { return nil }
            let visible = MessagePresentation(message).visibleText
            return visible.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : visible
        }
    }

    /// The next position, wrapping past either end. -1 when there is nothing to step to.
    public static func step(current: Int, count: Int, forward: Bool) -> Int {
        guard count > 0 else { return -1 }
        guard (0..<count).contains(current) else { return forward ? 0 : count - 1 }
        return forward ? (current + 1) % count : (current - 1 + count) % count
    }
}

public enum DayKind: Sendable {
    case today, yesterday, date
}

public struct DayLabel: Equatable, Sendable {
    public var kind: DayKind
    public var date: Date
}

public enum ChatDaySeparators {
    /// A label on the first row and on each row that starts a new calendar day.
    public static func label(
        rows: [TranscriptRow], index: Int, now: Date, calendar: Calendar = .current
    ) -> DayLabel? {
        guard rows.indices.contains(index) else { return nil }
        let day = Date(timeIntervalSince1970: rows[index].at / 1_000)
        if index > 0 {
            let before = Date(timeIntervalSince1970: rows[index - 1].endAt / 1_000)
            if calendar.isDate(before, inSameDayAs: day) { return nil }
        }
        let kind: DayKind
        if calendar.isDate(day, inSameDayAs: now) {
            kind = .today
        } else if let yesterday = calendar.date(byAdding: .day, value: -1, to: now),
                  calendar.isDate(day, inSameDayAs: yesterday) {
            kind = .yesterday
        } else {
            kind = .date
        }
        return DayLabel(kind: kind, date: day)
    }
}

/// A reply travels as ordinary text: a quoted block, a blank line, then the
/// person's own words. The computer needs no extra field for it.
public enum ReplyQuote {
    public static let limit = 280

    public static func quote(_ replied: String) -> String {
        let trimmed = replied
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let head = trimmed.count > limit ? String(trimmed.prefix(limit)) : trimmed
        return head.components(separatedBy: "\n").map { "> \($0)" }.joined(separator: "\n")
    }

    public static func wrap(_ replied: String?, text: String) -> String {
        guard let replied, !replied.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return text }
        return quote(replied) + "\n\n" + text
    }

    /// The first non-blank line, for the strip above the composer.
    public static func firstLine(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: .newlines)
            .first { !$0.trimmingCharacters(in: .whitespaces).isEmpty }?
            .trimmingCharacters(in: .whitespaces) ?? ""
    }
}
