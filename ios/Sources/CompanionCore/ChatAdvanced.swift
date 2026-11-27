import Foundation

/// How full a thread's context window is, as the computer last reported it.
public struct ContextUsage: Codable, Hashable, Sendable {
    public var tokens: Int64
    public var window: Int64?

    public init(tokens: Int64 = 0, window: Int64? = nil) {
        self.tokens = tokens
        self.window = window
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        tokens = try container.decodeIfPresent(Int64.self, forKey: .tokens) ?? 0
        window = try container.decodeIfPresent(Int64.self, forKey: .window)
    }

    private enum CodingKeys: String, CodingKey { case tokens, window }
}

/// What a thread has spent. Only the numbers the phone shows are decoded.
public struct TaskUsage: Codable, Hashable, Sendable {
    public var input: Int64
    public var output: Int64
    public var turns: Int
    public var context: ContextUsage?

    public init(input: Int64 = 0, output: Int64 = 0, turns: Int = 0, context: ContextUsage? = nil) {
        self.input = input
        self.output = output
        self.turns = turns
        self.context = context
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        input = try container.decodeIfPresent(Int64.self, forKey: .input) ?? 0
        output = try container.decodeIfPresent(Int64.self, forKey: .output) ?? 0
        turns = try container.decodeIfPresent(Int.self, forKey: .turns) ?? 0
        context = try? container.decodeIfPresent(ContextUsage.self, forKey: .context)
    }

    private enum CodingKeys: String, CodingKey { case input, output, turns, context }
}

/// Built only when the computer reported both the token count and the window.
public struct ContextMeter: Hashable, Sendable {
    public var percent: Int
    public var tokens: Int64
    public var window: Int64

    public init(percent: Int, tokens: Int64, window: Int64) {
        self.percent = percent
        self.tokens = tokens
        self.window = window
    }
}

public enum ComposerTool: String, CaseIterable, Sendable {
    case teach, goal, context
}

public enum ChatAdvanced {
    public static let renameLimit = 80
    private static let defaultFile = "conversation"

    public static func currentTask(_ bot: Bot) -> BotTask? {
        bot.tasks?.first { $0.threadId == bot.threadId }
    }

    public static func contextMeter(_ task: BotTask?) -> ContextMeter? {
        guard let usage = task?.usage, usage.input + usage.output > 0 || usage.turns > 0,
              let context = usage.context, let window = context.window, window > 0 else { return nil }
        let percent = Int((Double(context.tokens) * 100 / Double(window)).rounded())
        return ContextMeter(percent: min(max(percent, 0), 100), tokens: context.tokens, window: window)
    }

    /// The chips above the field. Off, the row is empty, so the composer reads as it did before
    /// Advanced mode. `learnAllowed` is the learn gate: Teach stays hidden while it is false.
    public static func tools(
        _ chat: Chat,
        advanced: Bool,
        learnAllowed: Bool,
        goalRunning: Bool,
        context: ContextMeter?
    ) -> [ComposerTool] {
        guard advanced else { return [] }
        var tools: [ComposerTool] = []
        if learnAllowed { tools.append(.teach) }
        if !goalRunning && !isDirectMessage(chat) { tools.append(.goal) }
        if chat.isBot && context != nil { tools.append(.context) }
        return tools
    }

    private static func isDirectMessage(_ chat: Chat) -> Bool {
        if case .room(let room) = chat { return room.dm == true }
        return false
    }

    /// A chip writes its command at the start of the field and keeps what was typed.
    public static func insertion(draft: String, command: String) -> String {
        draft.hasPrefix(command) ? draft : command + draft
    }

    /// A short count for the context line: 850, 1.5k, 12k, 1.2M.
    public static func compactCount(_ count: Int64) -> String {
        if count < 1_000 { return String(count) }
        if count < 1_000_000 { return scaled(Double(count) / 1_000, "k") }
        return scaled(Double(count) / 1_000_000, "M")
    }

    private static func scaled(_ value: Double, _ suffix: String) -> String {
        if value >= 10 { return "\(Int(value.rounded()))\(suffix)" }
        var text = String(format: "%.1f", value)
        if text.hasSuffix(".0") { text.removeLast(2) }
        return text + suffix
    }

    /// The file a downloaded transcript gets: the thread title as one safe word run, then `.md`.
    public static func markdownFileName(_ title: String?) -> String {
        let trimmed = (title ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        var words = ""
        var pendingDash = false
        for scalar in trimmed.unicodeScalars {
            if CharacterSet.alphanumerics.contains(scalar) {
                if pendingDash && !words.isEmpty { words += "-" }
                pendingDash = false
                words.unicodeScalars.append(scalar)
            } else {
                pendingDash = true
            }
        }
        let cut = String(words.prefix(renameLimit))
        return (cut.isEmpty ? defaultFile : cut) + ".md"
    }

    /// The title a rename sends: trimmed and cut to what the computer keeps.
    public static func renameTitle(_ raw: String) -> String {
        String(raw.trimmingCharacters(in: .whitespacesAndNewlines).prefix(renameLimit))
    }

    public static func canSaveRename(_ raw: String) -> Bool {
        !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
