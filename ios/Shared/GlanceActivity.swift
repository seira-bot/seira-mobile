// The Glance Live Activity's contract, compiled into the app and the widget
// extension. One activity shows the focus bot; the bot lives in the content
// state, so the activity never has to be replaced when the focus changes.
import ActivityKit
import AppIntents
import CompanionCore
import Foundation

struct GlanceActivityAttributes: ActivityAttributes {
    public typealias ContentState = GlanceContent
}

/// Where the intents below reach the app. A `LiveActivityIntent` runs in the
/// app's own process, so the app sets `handler` at launch and the widget copy
/// of this type never answers anything itself.
enum GlanceAnswer {
    typealias Handler = (_ botId: String, _ threadId: String, _ requestId: String, _ allow: Bool) async -> Void

    nonisolated(unsafe) static var handler: Handler?
}

struct GlanceDenyIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Deny"
    static var isDiscoverable = false

    @Parameter(title: "Bot") var botId: String
    @Parameter(title: "Thread") var threadId: String
    @Parameter(title: "Request") var requestId: String

    init() {}
    init(botId: String, threadId: String, requestId: String) {
        self.botId = botId
        self.threadId = threadId
        self.requestId = requestId
    }

    func perform() async throws -> some IntentResult {
        await GlanceAnswer.handler?(botId, threadId, requestId, false)
        return .result()
    }
}

struct GlanceAllowOnceIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Allow once"
    static var isDiscoverable = false
    /// Security: Allow once runs a command, so the phone must be unlocked.
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @Parameter(title: "Bot") var botId: String
    @Parameter(title: "Thread") var threadId: String
    @Parameter(title: "Request") var requestId: String

    init() {}
    init(botId: String, threadId: String, requestId: String) {
        self.botId = botId
        self.threadId = threadId
        self.requestId = requestId
    }

    func perform() async throws -> some IntentResult {
        await GlanceAnswer.handler?(botId, threadId, requestId, true)
        return .result()
    }
}

extension GlanceContent {
    var look: SeiraLook {
        SeiraLook(
            hair: mascotBody ?? SeiraLookCatalog.defaultHair,
            color: SeiraLookCatalog.color(color),
            faceInk: mascotFaceColor,
            accessories: mascotAccessories ?? []
        )
    }

    var mood: CharacterMood {
        switch state {
        case .working: return .working
        case .needs: return .alerting
        case .question: return .curious
        case .failed: return .sad
        case .done: return .happy
        }
    }
}
