import Foundation

/// What the Glance Live Activity shows, as one value the app writes and the widget reads.
/// ActivityKit limits a content state to 4 KB; the text fields below are all capped by `Glance`.
public struct GlanceContent: Codable, Hashable, Sendable {
    /// The title and detail wrap to at most this many rows in the narrowest Live Activity view that
    /// offers Allow once: the lock screen on a 375 pt phone, 12 pt monospaced, with the head beside it. The widget measures the real layout again and
    /// hides Allow once when the text does not fit the 160 pt activity height.
    public static let budget = GlanceBudget(lineChars: 28, lines: 4)

    public var botId: String
    public var threadId: String
    public var name: String
    public var color: String?
    public var state: GlanceState
    /// The bot's own words; only while it works.
    public var line: String
    public var askKind: GlanceAskKind?
    public var requestId: String?
    public var title: String
    /// The whole approval text. Empty unless Allow once can show it whole.
    public var detail: String
    public var showsDeny: Bool
    public var showsAllow: Bool
    /// The last answer from the activity did not reach the computer.
    public var couldNotSend: Bool
    public var mascotBody: String?
    public var mascotFaceColor: String?
    public var mascotAccessories: [String]?

    public init(entry: GlanceEntry, budget: GlanceBudget = GlanceContent.budget, couldNotSend: Bool = false) {
        let bot = entry.bot
        let ask = bot.ask
        let canAllow = ask?.canAllow(budget) == true
        botId = bot.botId
        threadId = bot.threadId
        name = bot.name
        color = bot.color
        state = bot.state
        line = bot.state == .working ? bot.line : ""
        askKind = ask?.kind
        requestId = ask?.requestId
        title = ask?.title ?? ""
        detail = canAllow ? ask?.detail ?? "" : ""
        showsDeny = ask?.canDeny == true && !couldNotSend
        showsAllow = canAllow && !couldNotSend
        self.couldNotSend = couldNotSend
        mascotBody = bot.mascotBody
        mascotFaceColor = bot.mascotFaceColor
        mascotAccessories = bot.mascotAccessories
    }

    /// Security: an answer from the activity goes out only for the approval that Glance shows now.
    /// Allow once also needs the whole text on the surface; a stale tap sends nothing.
    public static func canAnswer(
        state: CompanionState, botId: String, requestId: String, allow: Bool,
        budget: GlanceBudget = GlanceContent.budget
    ) -> Bool {
        guard let ask = Glance.currentAsk(state, botId: botId, requestId: requestId), ask.kind == .approval else {
            return false
        }
        return allow ? ask.canAllow(budget) : ask.canDeny
    }
}
