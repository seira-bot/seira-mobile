import Foundation

/// One usage window as an engine reported it. `window` stays a string: a
/// newer computer can name a window this build has never heard of.
public struct LimitWindowUsage: Codable, Hashable, Sendable {
    public var window: String
    /// 0 to 100.
    public var usedPercent: Double
    /// Epoch milliseconds when the window resets; nil when not known.
    public var resetsAt: Double?

    public init(window: String, usedPercent: Double, resetsAt: Double? = nil) {
        self.window = window
        self.usedPercent = usedPercent
        self.resetsAt = resetsAt
    }
}

/// One engine's usage limit, from `GET /api/limit-resets`.
public struct EngineLimit: Codable, Hashable, Sendable, Identifiable {
    /// The engine instance id.
    public var engine: String
    public var name: String
    public var driver: String
    /// The window that decides the reset: the fullest, or the one refusing work.
    public var window: String?
    public var usedPercent: Double?
    public var resetsAt: Double?
    /// The engine refuses work until `resetsAt`.
    public var blocked: Bool
    public var held: Bool?
    /// Every window the engine reported. A computer from before November
    /// 2026 sends only the deciding window above.
    public var windows: [LimitWindowUsage]?

    public var id: String { engine }

    public init(
        engine: String, name: String, driver: String, window: String? = nil,
        usedPercent: Double? = nil, resetsAt: Double? = nil, blocked: Bool = false,
        held: Bool? = nil, windows: [LimitWindowUsage]? = nil
    ) {
        self.engine = engine
        self.name = name
        self.driver = driver
        self.window = window
        self.usedPercent = usedPercent
        self.resetsAt = resetsAt
        self.blocked = blocked
        self.held = held
        self.windows = windows
    }
}

public struct EngineLimitsReport: Codable, Hashable, Sendable {
    public var checkedAt: Double
    public var engines: [EngineLimit]

    public init(checkedAt: Double, engines: [EngineLimit]) {
        self.checkedAt = checkedAt
        self.engines = engines
    }
}

public enum UsageTone: Hashable, Sendable {
    case fine, near, out
}

public enum EngineUsage {
    /// From this share of a window used, an engine counts as near its limit.
    public static let nearLimitPercent: Double = 90

    public static func nearLimit(usedPercent: Double?, blocked: Bool) -> Bool {
        blocked || (usedPercent ?? 0) >= nearLimitPercent
    }

    /// An engine that reports usage. API keys and local models report none.
    public static func reportsUsage(_ limit: EngineLimit?) -> Bool {
        guard let limit else { return false }
        return limit.blocked || limit.usedPercent != nil
    }

    /// The engines the usage screen lists. Empty means its empty state.
    public static func reporting(_ engines: [EngineLimit]) -> [EngineLimit] {
        engines.filter { reportsUsage($0) }
    }

    public static func tone(usedPercent: Double?, blocked: Bool) -> UsageTone {
        if blocked || (usedPercent ?? 0) >= 100 { return .out }
        return nearLimit(usedPercent: usedPercent, blocked: blocked) ? .near : .fine
    }

    public static func date(fromMilliseconds value: Double?) -> Date? {
        value.map { Date(timeIntervalSince1970: $0 / 1000) }
    }
}

/// One line of the usage screen: a window, the share left and its reset.
public struct UsageRow: Hashable, Sendable {
    public var window: String
    /// Whole percent left, 0 to 100.
    public var left: Int
    public var resetsAt: Date?
    public var tone: UsageTone
}

/// One row per window the engine reported, or the deciding window alone from
/// a computer that sends no list. The deciding window of a blocked engine is
/// at its limit until the engine's own reset time.
public func usageRows(_ limit: EngineLimit) -> [UsageRow] {
    var windows = limit.windows ?? []
    if windows.isEmpty, EngineUsage.reportsUsage(limit) {
        windows = [LimitWindowUsage(
            window: limit.window ?? "other",
            usedPercent: limit.usedPercent ?? 100,
            resetsAt: limit.resetsAt
        )]
    }
    let deciding = windows.contains { $0.window == limit.window } ? limit.window : windows.first?.window
    return windows.map { entry in
        let refused = limit.blocked && entry.window == deciding
        let tone: UsageTone = refused
            ? .out
            : EngineUsage.tone(usedPercent: entry.usedPercent, blocked: false)
        return UsageRow(
            window: entry.window,
            left: tone == .out ? 0 : max(0, 100 - Int(entry.usedPercent.rounded())),
            resetsAt: EngineUsage.date(fromMilliseconds: refused ? limit.resetsAt : entry.resetsAt),
            tone: tone
        )
    }
}

public func usageTone(_ limit: EngineLimit) -> UsageTone {
    EngineUsage.tone(usedPercent: limit.usedPercent, blocked: limit.blocked)
}

/// What a one-line summary of an engine says, without the words: the app
/// words it in the reader's language.
public struct LimitLine: Hashable, Sendable {
    public var name: String
    /// Nil when the engine is at its limit.
    public var usedPercent: Int?
    public var atLimit: Bool
    /// Only a reset still ahead of `now`.
    public var resetsAt: Date?
}

public func limitLine(_ limit: EngineLimit, now: Date = Date()) -> LimitLine {
    let reset = EngineUsage.date(fromMilliseconds: limit.resetsAt).flatMap { $0 > now ? $0 : nil }
    return LimitLine(
        name: limit.name,
        usedPercent: limit.blocked ? nil : Int((limit.usedPercent ?? 0).rounded()),
        atLimit: limit.blocked,
        resetsAt: reset
    )
}

/// "3:40 PM" for a reset today, "Fri 3:40 PM" further out.
public func limitResetTime(
    _ date: Date, now: Date = Date(), locale: Locale = .current,
    calendar: Calendar = .current, timeZone: TimeZone = .current
) -> String {
    var calendar = calendar
    calendar.timeZone = timeZone
    let formatter = DateFormatter()
    formatter.locale = locale
    formatter.calendar = calendar
    formatter.timeZone = timeZone
    formatter.setLocalizedDateFormatFromTemplate(calendar.isDate(date, inSameDayAs: now) ? "jmm" : "EEEjmm")
    return formatter.string(from: date)
}

/// The failed turn that should offer "Switch model": the latest one, while
/// nothing has been said since and no usage-limit offer is already open.
public func switchModelOfferMessageId(in messages: [Message]) -> String? {
    guard let index = messages.lastIndex(where: isFailedTurn) else { return nil }
    if messages[(index + 1)...].contains(where: { $0.kind == .text }) { return nil }
    if messages.contains(where: { $0.card?.quotaSwitch != nil && $0.card?.isPending == true }) { return nil }
    if messages[index].tool?.claudeUpdate == true { return nil }
    return messages[index].id
}
