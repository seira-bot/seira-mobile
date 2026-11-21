// What a bot is doing, as one of the 40 moods that the app stores in Live Activities and notifications.
// The raw values are the same as the desktop mascot states, so a stored value keeps its meaning.
import CompanionCore
import Foundation
import SwiftUI

enum CharacterMood: String, CaseIterable {
    /// Moods whose motion carries information: a turn in progress. Faces in lists move
    /// only for these. A resting bot earns a resting face.
    var showsActivity: Bool {
        switch self {
        case .listening, .thinking, .searching, .working: return true
        default: return false
        }
    }

    case sleeping = "sleeping"
    case waking = "waking"
    case idle = "idle"
    case listening = "listening"
    case thinking = "thinking"
    case searching = "searching"
    case working = "working"
    case excited = "excited"
    case surprised = "surprised"
    case suspicious = "suspicious"
    case angry = "angry"
    case drowsy = "drowsy"
    case happy = "happy"
    case curious = "curious"
    case confused = "confused"
    case bored = "bored"
    case proud = "proud"
    case shy = "shy"
    case sad = "sad"
    case laughing = "laughing"
    case scared = "scared"
    case playful = "playful"
    case celebrate = "celebrate"
    case orbit = "orbit"
    case radar = "radar"
    case progress = "progress"
    case thinkingDots = "thinking-dots"
    case spawning = "spawning"
    case humming = "humming"
    case loading = "loading"
    case dictating = "dictating"
    case sending = "sending"
    case receiving = "receiving"
    case uploading = "uploading"
    case writing = "writing"
    case notifying = "notifying"
    case alerting = "alerting"
    case bouncing = "bouncing"
    case dragging = "dragging"
    case poweringDown = "powering-down"

    /// The id of the screen face that this mood shows. See `SeiraFaces`.
    var faceId: String {
        switch self {
        case .sleeping, .poweringDown: return "asleep"
        case .waking, .surprised, .spawning, .notifying, .dragging: return "surprised"
        case .idle, .orbit: return "calm"
        case .listening, .dictating: return "listening"
        case .thinking, .thinkingDots: return "think-state"
        case .searching, .curious, .radar: return "thinking"
        case .working, .progress, .loading, .sending, .receiving, .uploading, .writing: return "working"
        case .excited, .celebrate: return "cheer"
        case .suspicious: return "smug"
        case .angry: return "angry"
        case .drowsy: return "sleepy"
        case .happy, .humming, .bouncing: return "happy"
        case .confused: return "confused"
        case .bored: return "bored"
        case .proud: return "proud"
        case .shy: return "shy"
        case .sad: return "sad"
        case .laughing: return "laugh"
        case .scared: return "scared"
        case .playful: return "wink"
        case .alerting: return "error"
        }
    }
}

extension CharacterMood {
    /// The desktop's legacy names, kept so an older bot record still resolves.
    private static let legacy: [String: CharacterMood] = [
        "deadpan": .idle, "friendly": .happy, "focused": .working, "thinking": .thinking,
        "excited": .excited, "sleepy": .drowsy, "surprised": .surprised, "skeptical": .suspicious,
        "worried": .scared, "mischievous": .playful,
    ]

    /// Resolves any stored value, current, legacy or junk, to a real mood.
    static func normalize(_ value: String?) -> CharacterMood? {
        guard let value, !value.isEmpty else { return nil }
        return CharacterMood(rawValue: value) ?? legacy[value]
    }

    static func forBot(_ bot: Bot, last: Message?) -> CharacterMood {
        if let pinned = normalize(bot.mascotExpression) { return pinned }

        if last?.kind == .activity, last?.tool?.statusOk == false { return .alerting }
        if bot.busy == true { return .working }
        if bot.unread { return .notifying }
        if last?.kind == .options { return .curious }

        let profile = "\(bot.name) \(bot.title) \(bot.description)".lowercased()
        let range = NSRange(profile.startIndex..., in: profile)
        for (pattern, mood) in roleGuesses where pattern.firstMatch(in: profile, range: range) != nil {
            return mood
        }
        return .idle
    }

    /// A guess from the bot's role, first match wins. Each list is one compiled pattern.
    private static let roleGuesses: [(NSRegularExpression, CharacterMood)] = {
        let lists: [([String], CharacterMood)] = [
            (["code", "coding", "developer", "development", "engineer", "engineering", "build", "debug", "program", "software"], .working),
            (["research", "researcher", "search", "investigate", "strategy", "strategist", "study", "learn", "knowledge"], .searching),
            (["marketing", "growth", "launch", "campaign", "social", "sales", "outreach", "brand"], .excited),
            (["overnight", "night", "background", "async", "queue", "batch", "long-running"], .drowsy),
            (["monitor", "monitoring", "incident", "alert", "watch", "status", "uptime"], .radar),
            (["review", "reviewer", "audit", "critic", "critique", "quality", "qa", "test", "legal"], .suspicious),
            (["security", "secure", "compliance", "risk", "privacy", "finance", "financial"], .scared),
            (["design", "designer", "creative", "brainstorm", "art", "illustration", "music", "story"], .playful),
            (["support", "help", "success", "onboarding", "coach", "teacher", "guide", "welcome"], .happy),
        ]
        return lists.map { words, mood in
            let alternatives = words.map(NSRegularExpression.escapedPattern(for:)).joined(separator: "|")
            // fixed, escaped words: this pattern always compiles
            return (try! NSRegularExpression(pattern: "\\b(?:\(alternatives))\\b"), mood)
        }
    }()

    /// The mood for a chat as a whole: a bot's own. A room's is "happy".
    static func forChat(_ chat: Chat, in state: CompanionState) -> CharacterMood {
        switch chat {
        case let .bot(bot): return forBot(bot, last: state.lastVisibleMessage(forThread: bot.threadId))
        case .room: return .happy
        }
    }
}

// MARK: Motion budget

/// How many opted-in faces may move at once on one screen.
///
/// Each moving face is its own 30 fps drawing. Under a budget the first `limit` faces to come on
/// screen move. The others hold a resting frame and take a turn when a moving face leaves the
/// screen or settles. Faces outside a budget move as before. Reduce Motion and the scene phase
/// still stop every face.
@MainActor
final class CharacterMotionBudget {
    private var turns: MotionTurns<ObjectIdentifier>
    private var tickets: [ObjectIdentifier: CharacterMotionTicket] = [:]

    init(limit: Int) {
        turns = MotionTurns(limit: limit)
    }

    fileprivate func ask(_ ticket: CharacterMotionTicket) {
        let id = ObjectIdentifier(ticket)
        tickets[id] = ticket
        turns.ask(id)
        handOut()
    }

    fileprivate func leave(_ ticket: CharacterMotionTicket) {
        let id = ObjectIdentifier(ticket)
        tickets[id] = nil
        turns.leave(id)
        ticket.moving = false
        handOut()
    }

    private func handOut() {
        for (id, ticket) in tickets {
            let moves = turns.moves(id)
            if ticket.moving != moves { ticket.moving = moves }
        }
    }
}

/// One face's place under a `CharacterMotionBudget`. Its own object, so a turn changing hands
/// redraws only the faces it moves between.
@MainActor
fileprivate final class CharacterMotionTicket: ObservableObject {
    @Published var moving = false
}

/// Asks for a turn while the face is on screen and wants to move, and gives it back when the
/// face leaves or settles.
struct CharacterMotionTurn<Face: View>: View {
    let budget: CharacterMotionBudget
    let face: (Bool) -> Face
    @StateObject private var ticket = CharacterMotionTicket()

    var body: some View {
        face(ticket.moving)
            .onAppear { budget.ask(ticket) }
            .onDisappear { budget.leave(ticket) }
    }
}

private struct CharacterMotionBudgetKey: EnvironmentKey {
    static let defaultValue: CharacterMotionBudget? = nil
}

extension EnvironmentValues {
    var characterMotionBudget: CharacterMotionBudget? {
        get { self[CharacterMotionBudgetKey.self] }
        set { self[CharacterMotionBudgetKey.self] = newValue }
    }
}
