import Foundation

/// One server chip: `title` is its label, `prompt` is the text a tap puts in the composer.
public struct SuggestedReply: Hashable, Sendable {
    public var title: String
    public var prompt: String

    public init(title: String, prompt: String) {
        self.title = title
        self.prompt = prompt
    }
}

/// What a tap on a chip does.
public enum ChipTap: Hashable, Sendable {
    /// The tap sends the text at once. Only the chips the person saved have it: a chip from a model never does.
    case send
    /// The tap puts the full text in the composer. The person reads it, edits it and presses Send.
    case fillComposer
}

/// A chip above the composer, from the server or from the saved list.
public struct ComposerChip: Hashable, Sendable {
    public var title: String
    public var prompt: String
    public var icon: String
    public var tap: ChipTap
}

/// The `suggestedReplies` field of a bot line, as it came: one raw entry per array element.
///
/// Decoding never throws. The field is text from a model, and a newer computer may send a shape this build does not know;
/// either would otherwise fail the decode of the whole line and the thread would not render.
public struct SuggestedReplyList: Codable, Hashable, Sendable {
    public struct Entry: Codable, Hashable, Sendable {
        public var title: String?
        public var prompt: String?

        enum CodingKeys: String, CodingKey { case title, prompt }

        public init(title: String?, prompt: String?) {
            self.title = title
            self.prompt = prompt
        }

        public init(from decoder: any Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            title = try? container.decode(String.self, forKey: .title)
            prompt = try? container.decode(String.self, forKey: .prompt)
        }
    }

    /// An array element that is no object reads as an entry with no parts, so its position still counts.
    private struct Lossy: Decodable {
        let entry: Entry

        init(from decoder: any Decoder) throws {
            entry = (try? Entry(from: decoder)) ?? Entry(title: nil, prompt: nil)
        }
    }

    public var entries: [Entry]

    public init(entries: [Entry]) {
        self.entries = entries
    }

    public init(from decoder: any Decoder) throws {
        let all = (try? decoder.singleValueContainer().decode([Lossy].self)) ?? []
        entries = all.prefix(SuggestedReplies.readLimit).map(\.entry)
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.unkeyedContainer()
        for entry in entries { try container.encode(entry) }
    }
}

/// The `suggestedReplies` field on the last bot line of a finished turn (seira#527, `suggested-replies.ts`).
///
/// The text comes from a model, so the phone runs the same cleaner as the computer before a chip exists. The filter is
/// best effort. The boundary is `tap`: a chip from the server never sends anything. A chip row can clip a title (a
/// narrow screen, large text, a row that scrolls), so no layout proves that the person saw the whole text. The tap puts
/// the full text in the composer, where the person reads it and presses Send.
/// Port of Android `SuggestedReplies`.
public enum SuggestedReplies {
    public static let maxChips = 4
    public static let titleMax = 40
    public static let promptMax = 500
    static let readLimit = 16

    /// Mark for a server chip on the composer row.
    public static let icon = "sparkles"

    private static func regex(_ pattern: String, caseInsensitive: Bool = false) -> NSRegularExpression {
        // The patterns are fixed in this file.
        // swiftlint:disable:next force_try
        try! NSRegularExpression(pattern: pattern, options: caseInsensitive ? [.caseInsensitive] : [])
    }

    private static let control = regex(#"[\p{Cc}\p{Cf}\p{Zl}\p{Zp}]"#)
    private static let spaces = regex(#"\s+"#)
    private static let link = regex(
        #"(?:^|[^\p{L}\p{N}])(?:[a-z][a-z0-9+.-]*://|www\.|(?:javascript|data|vbscript|mailto|tel|sms|file|blob):)"#,
        caseInsensitive: true
    )
    private static let tlds = [
        "com", "net", "org", "io", "dev", "app", "ai", "co", "xyz", "info", "biz", "gov", "edu", "uk", "us", "de", "fr", "jp",
        "cn", "ru", "tv", "id", "me", "link", "site", "online", "top", "store", "tech", "cloud", "page",
    ]
    private static let bareAddress = regex(
        #"(?:^|[^\p{L}\p{N}])(?:(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)+(?:"# + tlds.joined(separator: "|")
            + #")(?![\p{L}\p{N}-])|localhost(?![\p{L}\p{N}-])|[0-9]{1,3}(?:\.[0-9]{1,3}){3}(?![\p{L}\p{N}]))"#,
        caseInsensitive: true
    )
    private static let slashCommand = regex(#"^\s*/\S"#)
    private static let shellSyntax = regex(#"`|\$\(|\$\{|&&|\|\||\||^\s*\$\s|>>|(?:^|\s)[0-9]?>\s*/|(?:^|\s)<\s*/"#)
    private static let shellWords: Set<String> = [
        "sudo", "rm", "curl", "wget", "chmod", "chown", "bash", "sh", "zsh", "npm", "npx", "pnpm", "yarn", "pip", "git", "ssh",
        "scp", "docker", "kubectl", "cd", "ls", "cat", "echo", "export", "eval", "exec", "kill", "dd", "mkfs",
    ]
    private static let actionPayload = regex(#"^\s*[\{\[]|"(?:action|tool|command|cmd|url)"\s*:"#, caseInsensitive: true)

    private static func matches(_ expression: NSRegularExpression, _ text: String) -> Bool {
        expression.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }

    private static func replacing(_ expression: NSRegularExpression, in text: String, with template: String) -> String {
        expression.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text), withTemplate: template)
    }

    private static func tidy(_ value: String?) -> String? {
        guard let value else { return nil }
        let text = replacing(spaces, in: replacing(control, in: value, with: " "), with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? nil : text
    }

    private static func plain(_ text: String) -> Bool {
        if matches(link, text) || matches(bareAddress, text) || matches(slashCommand, text)
            || matches(shellSyntax, text) || matches(actionPayload, text) { return false }
        let first = text.split(separator: " ", maxSplits: 1, omittingEmptySubsequences: false).first.map(String.init) ?? ""
        return !shellWords.contains(first)
    }

    /// The list a phone may show: at most `maxChips` entries, each plain text, a title of at most `titleMax` and a prompt of at
    /// most `promptMax` characters. An entry with a part that is missing, not text, empty or over the limit, or that holds a
    /// link, a slash command, shell syntax or an action payload, is dropped whole and never cut. A repeated prompt counts
    /// once. A field that is absent or holds nothing usable gives an empty list: the phone keeps its own chips.
    public static func clean(_ field: SuggestedReplyList?) -> [SuggestedReply] {
        guard let field else { return [] }
        var kept: [SuggestedReply] = []
        var seen = Set<String>()
        for entry in field.entries.prefix(readLimit) {
            if kept.count == maxChips { break }
            guard let title = tidy(entry.title), let prompt = tidy(entry.prompt) else { continue }
            if title.unicodeScalars.count > titleMax || prompt.unicodeScalars.count > promptMax { continue }
            if !plain(title) || !plain(prompt) { continue }
            if !seen.insert(prompt.lowercased()).inserted { continue }
            kept.append(SuggestedReply(title: title, prompt: prompt))
        }
        return kept
    }

    /// The chips of the open conversation: those of its last line, and only when that line is a bot line. A line of the
    /// person after it, or a conversation with no field, gives none, so one conversation never shows the chips of another.
    public static func forTranscript(_ messages: [Message]) -> [SuggestedReply] {
        guard let last = messages.last, last.role == .bot else { return [] }
        return clean(last.suggestedReplies)
    }

    /// A tap on a chip from the server always fills the composer, for every title and prompt, whatever the screen shows.
    /// The person reads the full text there and presses Send.
    public static func tap(_ reply: SuggestedReply) -> ChipTap {
        .fillComposer
    }

    /// The composer row: the chips of the server first, then the saved chips. A saved chip with the prompt of a server chip
    /// is left out, so the row never shows the same ask twice. Without server chips the row is the saved list as before.
    public static func composerChips(server: [SuggestedReply], saved: [QuickReply]) -> [ComposerChip] {
        let fromServer = server.map { ComposerChip(title: $0.title, prompt: $0.prompt, icon: icon, tap: tap($0)) }
        let taken = Set(server.map { $0.prompt.lowercased() })
        let own = saved
            .filter { !taken.contains($0.prompt.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()) }
            .map { ComposerChip(title: $0.title, prompt: $0.prompt, icon: $0.icon, tap: .send) }
        return fromServer + own
    }
}
