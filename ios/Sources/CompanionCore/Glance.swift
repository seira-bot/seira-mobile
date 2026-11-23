import Foundation

/// A bot's state in Glance. A lower `rank` comes first.
public enum GlanceState: String, Codable, Hashable, Sendable {
    case needs, question, failed, working, done

    public var rank: Int {
        switch self {
        case .needs, .question: return 0
        case .failed: return 1
        case .working: return 2
        case .done: return 3
        }
    }
}

public enum GlanceAskKind: String, Codable, Hashable, Sendable {
    /// A plain tool permission. Glance can deny it, and allow it once next to its whole text.
    case approval
    /// The bot asks the person something. Glance only opens the chat.
    case question
    /// A skill, a send on the person's behalf, a team memory or a proposal. Only the chat can answer it.
    case review
}

public struct GlanceAsk: Hashable, Sendable {
    public var kind: GlanceAskKind
    public var requestId: String
    public var threadId: String
    public var title: String
    /// Security: the whole text that the approval covers, or empty. Never a cut text: a cut
    /// command can hide its tail. Only a `GlanceAskKind.approval` has one.
    public var detail: String
    public var at: Double

    public init(kind: GlanceAskKind, requestId: String, threadId: String, title: String, detail: String, at: Double) {
        self.kind = kind
        self.requestId = requestId
        self.threadId = threadId
        self.title = title
        self.detail = detail
        self.at = at
    }

    public var canDeny: Bool { kind == .approval }

    /// Security: Allow once shows only where the surface can show `title` and `detail` whole.
    public func canAllow(_ budget: GlanceBudget) -> Bool {
        kind == .approval && !detail.isEmpty && budget.fitsWhole(title.isEmpty ? detail : title + "\n" + detail)
    }
}

/// How much text one Glance surface shows without a cut: `lines` rows of `lineChars` narrow cells.
public struct GlanceBudget: Hashable, Sendable {
    public var lineChars: Int
    public var lines: Int

    public init(lineChars: Int, lines: Int) {
        self.lineChars = lineChars
        self.lines = lines
    }

    /// Security: an overestimate, so a text that fits is never cut on screen. Only printable ASCII,
    /// new lines and tabs qualify: other characters can draw much wider than one cell. Rows wrap
    /// greedily at spaces, a longer word breaks at the row end, and a tab takes 8 cells.
    /// A surface must still measure its real layout before it offers Allow once.
    public func fitsWhole(_ text: String) -> Bool {
        if text.isEmpty || lineChars <= 0 { return false }
        guard text.unicodeScalars.allSatisfy({ $0.value == 0x0a || $0.value == 0x09 || (0x20...0x7e).contains($0.value) }) else {
            return false
        }
        var used = 0
        for line in text.split(separator: "\n", omittingEmptySubsequences: false) {
            used += rows(line)
            if used > lines { return false }
        }
        return true
    }

    private func rows(_ line: Substring) -> Int {
        var rows = 1
        var column = 0
        for word in line.split(separator: " ", omittingEmptySubsequences: false) {
            let width = word.unicodeScalars.reduce(0) { $0 + ($1.value == 0x09 ? 8 : 1) }
            let gap = column == 0 ? 0 : 1
            if column + gap + width <= lineChars {
                column += gap + width
                continue
            }
            if column > 0 { rows += 1 }
            let extra = max(0, width - 1) / lineChars
            rows += extra
            column = width - extra * lineChars
        }
        return rows
    }
}

public struct GlanceBot: Hashable, Sendable {
    public var botId: String
    public var threadId: String
    public var name: String
    public var color: String?
    public var state: GlanceState
    /// The bot's own words while it works; empty in every other state.
    public var line: String
    public var ask: GlanceAsk?
    public var mascotBody: String?
    public var mascotFaceColor: String?
    public var mascotAccessories: [String]?

    public init(
        botId: String, threadId: String, name: String, color: String?, state: GlanceState,
        line: String, ask: GlanceAsk?,
        mascotBody: String? = nil, mascotFaceColor: String? = nil, mascotAccessories: [String]? = nil
    ) {
        self.botId = botId
        self.threadId = threadId
        self.name = name
        self.color = color
        self.state = state
        self.line = line
        self.ask = ask
        self.mascotBody = mascotBody
        self.mascotFaceColor = mascotFaceColor
        self.mascotAccessories = mascotAccessories
    }

    public var key: String { "\(botId)\n\(threadId)" }
}

public struct GlanceEntry: Hashable, Sendable {
    public var bot: GlanceBot
    /// When the model first saw this bot in this thread, in ms. Working bots rank by it.
    public var startedAt: Int64
    /// When a finished entry leaves, in ms; nil for every other state.
    public var until: Int64?

    public init(bot: GlanceBot, startedAt: Int64, until: Int64? = nil) {
        self.bot = bot
        self.startedAt = startedAt
        self.until = until
    }
}

public struct GlanceMemory: Hashable, Sendable {
    public struct Keyed: Hashable, Sendable {
        public var key: String
        public var entry: GlanceEntry
    }

    /// Kept in insertion order: ties in the view sort by it.
    public var seen: [Keyed]
    public var failedShown: Set<String>
    public var done: [Keyed]

    public init(seen: [Keyed] = [], failedShown: Set<String> = [], done: [Keyed] = []) {
        self.seen = seen
        self.failedShown = failedShown
        self.done = done
    }

    func seenEntry(_ key: String) -> GlanceEntry? { seen.first { $0.key == key }?.entry }
}

public struct GlanceView: Hashable, Sendable {
    /// In rank order: the focus first.
    public var entries: [GlanceEntry]
    /// When a finished entry leaves and the view must be worked out again, in ms.
    public var nextAt: Int64?

    public init(entries: [GlanceEntry], nextAt: Int64?) {
        self.entries = entries
        self.nextAt = nextAt
    }

    public var focus: GlanceEntry? { entries.first }
}

/// What Glance shows, worked out from the phone's own state. The rules are the desktop's
/// (`electron/glance-model.mjs` in the Seira repository): a bot that needs the person or asks
/// comes first, then one that failed, then the one that works longest, then one that finished.
public enum Glance {
    public static let doneMs: Int64 = 4_000
    public static let nameLimit = 80
    public static let lineLimit = 160
    public static let titleLimit = 240
    public static let detailLimit = 400

    public static func step(_ memory: GlanceMemory, bots: [GlanceBot], now: Int64) -> (GlanceMemory, GlanceView) {
        let present = Set(bots.map(\.key))
        var seen: [GlanceMemory.Keyed] = []
        var failedShown = Set<String>()
        for bot in bots {
            let before = memory.seenEntry(bot.key)
            put(&seen, GlanceEntry(bot: bot, startedAt: before?.startedAt ?? now), key: bot.key)
            let failureIsNew = memory.failedShown.contains(bot.key) || (before != nil && before?.bot.state != .failed)
            if bot.state == .failed && failureIsNew { failedShown.insert(bot.key) }
        }
        var done: [GlanceMemory.Keyed] = []
        for item in memory.done where !present.contains(item.key) && (item.entry.until ?? 0) > now {
            put(&done, item.entry, key: item.key)
        }
        for item in memory.seen where !present.contains(item.key) && item.entry.bot.state == .working {
            var finished = item.entry.bot
            finished.state = .done
            finished.line = ""
            finished.ask = nil
            put(&done, GlanceEntry(bot: finished, startedAt: item.entry.startedAt, until: now + doneMs), key: item.key)
        }
        let shown = seen.map(\.entry).filter { $0.bot.state != .failed || failedShown.contains($0.bot.key) }
        let entries = (shown + done.map(\.entry))
            .enumerated()
            .sorted { a, b in
                let (x, y) = (a.element, b.element)
                if x.bot.state.rank != y.bot.state.rank { return x.bot.state.rank < y.bot.state.rank }
                let xs = x.bot.state == .working ? x.startedAt : 0
                let ys = y.bot.state == .working ? y.startedAt : 0
                if xs != ys { return xs < ys }
                let xu = x.bot.state == .done ? x.until ?? 0 : 0
                let yu = y.bot.state == .done ? y.until ?? 0 : 0
                if xu != yu { return xu > yu }
                return a.offset < b.offset
            }
            .map(\.element)
        let next = done.compactMap(\.entry.until).min()
        return (GlanceMemory(seen: seen, failedShown: failedShown, done: done), GlanceView(entries: entries, nextAt: next))
    }

    /// One entry per bot that works, waits on the person, asks, or failed, in fleet order.
    public static func bots(_ state: CompanionState) -> [GlanceBot] {
        let pending = state.pendingApprovals
        return state.bots.filter { $0.hidden != true }.compactMap { botOf(state, $0, pending) }
    }

    /// The ask Glance shows now for `requestId` of `botId`, or nil when the request changed or settled.
    public static func currentAsk(_ state: CompanionState, botId: String, requestId: String) -> GlanceAsk? {
        guard let bot = state.bot(botId), bot.hidden != true,
              let ask = botOf(state, bot, state.pendingApprovals)?.ask, ask.requestId == requestId else { return nil }
        return ask
    }

    /// Security: the card's whole text, for a plain permission card whose text has 1 to
    /// `detailLimit` characters. Empty for every other card and for a longer text.
    public static func approvalDetail(_ card: OptionCard) -> String {
        guard isPlainPermission(card) else { return "" }
        let lines = card.subtitle
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
        let text = dropFormat(strip(lines, keepLines: true))
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let count = text.unicodeScalars.count
        return (1...detailLimit).contains(count) ? text : ""
    }

    /// The last line the bot wrote in its running turn, without markup or code.
    public static func line(_ streaming: String?) -> String {
        guard let streaming, !streaming.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return "" }
        var prose: [String] = []
        var inFence = false
        for raw in streaming.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline) {
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.hasPrefix("```") || trimmed.hasPrefix("~~~") {
                inFence.toggle()
                continue
            }
            if !inFence { prose.append(trimmed) }
        }
        guard let last = prose.last(where: { !$0.isEmpty }) else { return "" }
        var plain = replace(listMark, in: last, with: "")
        plain = replace(markup, in: plain, with: "")
        plain = collapse(strip(dropFormat(plain), keepLines: false))
        return clip(plain, limit: lineLimit, fromEnd: true)
    }

    /// Security: the chat's own classifier must also draw the card as an approval; a proposal never qualifies.
    public static func isPlainPermission(_ card: OptionCard) -> Bool {
        card.isPermission
            && card.presentation == .approval
            && card.requestType != "question"
            && card.skillRequest == nil
            && card.questionRequest == nil
            && card.outboundRequest == nil
            && card.teamMemoryRequest == nil
            && card.quotaSwitch == nil
    }

    private static func botOf(
        _ state: CompanionState, _ bot: Bot, _ pending: [(threadId: String, message: Message)]
    ) -> GlanceBot? {
        let threads = threadsOf(bot)
        let threadIds = Set(threads.map(\.threadId)).union([bot.threadId])
        let ask = pending
            .filter { threadIds.contains($0.threadId) }
            .compactMap { approval in approval.message.card.flatMap { askOf($0, approval) } }
            .enumerated()
            .sorted { a, b in
                let ak = a.element.kind == .question ? 1 : 0
                let bk = b.element.kind == .question ? 1 : 0
                if ak != bk { return ak < bk }
                if a.element.at != b.element.at { return a.element.at < b.element.at }
                return a.offset < b.offset
            }
            .first?.element
        let name = clip(collapse(strip(dropFormat(bot.name), keepLines: false, replacement: " ")), limit: nameLimit)
        func entry(_ threadId: String, _ state: GlanceState, line: String = "", ask: GlanceAsk? = nil) -> GlanceBot {
            GlanceBot(
                botId: bot.id, threadId: threadId, name: name, color: bot.color, state: state, line: line, ask: ask,
                mascotBody: bot.mascotBody, mascotFaceColor: bot.mascotFaceColor, mascotAccessories: bot.mascotAccessories
            )
        }
        if let ask {
            return entry(ask.threadId, ask.kind == .question ? .question : .needs, ask: ask)
        }
        if let waiting = threads.first(where: { $0.activity == "waiting-on-you" }) {
            return entry(waiting.threadId, .needs)
        }
        let working = threads.first { $0.isWorking && !$0.isWaitingOnTeammate }?.threadId
            ?? (bot.busy == true && bot.waitingOnTeammate != true ? bot.threadId : nil)
        if let working {
            return entry(working, .working, line: line(state.streaming[working]))
        }
        let failed = threads.first { task in
            task.unread == true && lastTurnFailed(in: state.visibleTranscript(forThread: task.threadId))
        }
        return failed.map { entry($0.threadId, .failed) }
    }

    private static func threadsOf(_ bot: Bot) -> [BotTask] {
        guard bot.tasks != nil else {
            return [BotTask(
                threadId: bot.threadId, title: "", createdAt: bot.createdAt,
                modelSelection: bot.modelSelection, busy: bot.busy, activity: bot.activity,
                waitingOnTeammate: bot.waitingOnTeammate, unread: bot.unread,
                approvalMode: bot.approvalMode, autoApprove: bot.autoApprove, alwaysAllow: bot.alwaysAllow
            )]
        }
        return bot.visibleTasks.map { task in
            guard task.threadId == bot.threadId else { return task }
            var merged = task
            merged.activity = task.activity ?? bot.activity
            merged.busy = task.busy ?? bot.busy
            merged.unread = task.unread ?? bot.unread
            return merged
        }
    }

    private static func askOf(_ card: OptionCard, _ approval: (threadId: String, message: Message)) -> GlanceAsk? {
        guard let requestId = card.requestId else { return nil }
        let kind: GlanceAskKind
        if card.requestType == "question" || card.questionRequest != nil {
            kind = .question
        } else if isPlainPermission(card) {
            kind = .approval
        } else {
            kind = .review
        }
        let detail = kind == .approval ? approvalDetail(card) : ""
        return GlanceAsk(
            kind: kind, requestId: requestId, threadId: approval.threadId,
            title: clip(collapse(strip(dropFormat(card.title), keepLines: false, replacement: " ")), limit: titleLimit),
            detail: detail, at: approval.message.at
        )
    }

    private static func put(_ list: inout [GlanceMemory.Keyed], _ entry: GlanceEntry, key: String) {
        if let index = list.firstIndex(where: { $0.key == key }) {
            list[index].entry = entry
        } else {
            list.append(GlanceMemory.Keyed(key: key, entry: entry))
        }
    }

    private static func clip(_ text: String, limit: Int, fromEnd: Bool = false) -> String {
        let scalars = Array(text.unicodeScalars)
        guard scalars.count > limit else { return text }
        func string(_ slice: ArraySlice<Unicode.Scalar>) -> String {
            var view = String.UnicodeScalarView()
            view.append(contentsOf: slice)
            return String(view)
        }
        if fromEnd {
            let tail = string(scalars.suffix(limit - 1))
            return "…" + String(tail.drop { $0.isWhitespace })
        }
        let head = string(scalars.prefix(limit - 1))
        return head.trimmingCharacters(in: .whitespacesAndNewlines) + "…"
    }

    /// Replaces control, bidi and zero-width scalars with `replacement`. `keepLines` keeps tab, line feed and carriage return.
    private static func strip(_ text: String, keepLines: Bool, replacement: String = "") -> String {
        var out = String.UnicodeScalarView()
        for scalar in text.unicodeScalars {
            let v = scalar.value
            let kept = keepLines && (v == 0x09 || v == 0x0a || v == 0x0d)
            let control = v <= 0x1f || (0x7f...0x9f).contains(v) || (0x200b...0x200f).contains(v)
                || (0x2028...0x202e).contains(v) || (0x2060...0x2069).contains(v) || v == 0xfeff
            if control && !kept {
                out.append(contentsOf: replacement.unicodeScalars)
            } else {
                out.append(scalar)
            }
        }
        return String(out)
    }

    private static func dropFormat(_ text: String) -> String {
        var out = String.UnicodeScalarView()
        out.append(contentsOf: text.unicodeScalars.filter { $0.properties.generalCategory != .format })
        return String(out)
    }

    private static func collapse(_ text: String) -> String {
        text.split(whereSeparator: { $0 == " " || $0 == "\t" || $0 == "\n" || $0 == "\r" || $0 == "\u{0B}" || $0 == "\u{0C}" })
            .joined(separator: " ")
    }

    private static func replace(_ expression: NSRegularExpression, in text: String, with template: String) -> String {
        expression.stringByReplacingMatches(
            in: text, range: NSRange(text.startIndex..., in: text), withTemplate: template
        )
    }

    private static let listMark = try! NSRegularExpression(pattern: "^(?:#{1,6}\\s+|>\\s*|[-*+]\\s+|\\d+[.)]\\s+)")
    private static let markup = try! NSRegularExpression(pattern: "\\*+|`+|~~")
}
