import Foundation

/// Which buttons stand at the right edge of the composer.
public struct ComposerTrailing: Equatable, Sendable {
    public var showsStop: Bool
    public var showsSend: Bool
    public var showsCall: Bool

    /// Stop stays beside Send while the bot works, with or without text, so a
    /// line typed during a turn never hides the way to end the turn. The call
    /// button shows only on an empty field with nothing to stop.
    public static func of(hasContent: Bool, canStop: Bool, callAvailable: Bool) -> ComposerTrailing {
        ComposerTrailing(
            showsStop: canStop,
            showsSend: hasContent,
            showsCall: !hasContent && !canStop && callAvailable
        )
    }
}

/// The approval chip above the field.
public enum ModeChip {
    /// A group has no approval mode, so only a bot chat shows the chip.
    public static func visible(_ chat: Chat) -> Bool { chat.isBot }

    /// Custom is listed only while it is the current mode, and cannot be picked.
    public static func options(current: ApprovalMode?) -> [ApprovalMode] {
        ApprovalMode.phoneChoices + (current == .custom ? [ApprovalMode.custom] : [])
    }

    public static func refusalReason(_ refusal: ApprovalModeRefusal) -> ModeRefusalReason {
        switch refusal {
        case .fullNotAllowedHere: return .fullNotAllowedHere
        case .leaveCustomOnDesktop: return .leaveCustomOnDesktop
        case .customOnDesktop: return .customOnDesktop
        case .ownerOnly: return .ownerOnly
        case .providerUnsupported: return .providerUnsupported
        case .stillConfirming: return .stillConfirming
        case .localAutoNeedsAcknowledgement, .other: return .other
        }
    }
}

public enum ModeRefusalReason: Equatable, Sendable {
    case fullNotAllowedHere, leaveCustomOnDesktop, customOnDesktop, ownerOnly
    case providerUnsupported, stillConfirming, other
}

/// The approval ladder in the mode sheet: four steps from Ask to Full.
public enum ModeLadder {
    public static let steps: [ApprovalMode] = ApprovalMode.phoneChoices

    /// How long a finger must rest on Full before it counts.
    public static let holdMilliseconds = 700

    /// The step of a mode, or nil when the mode has no step (Custom, unknown).
    public static func index(of mode: ApprovalMode?) -> Int? {
        guard let mode else { return nil }
        return steps.firstIndex(of: mode)
    }

    /// Where the mode sits on the track, 0 for Ask to 1 for Full.
    public static func fraction(of mode: ApprovalMode?) -> Double? {
        guard let index = index(of: mode) else { return nil }
        return Double(index) / Double(steps.count - 1)
    }

    /// The step nearest to a position on the track. A tie goes to the lower step.
    public static func step(atFraction fraction: Double) -> ApprovalMode {
        let last = Double(steps.count - 1)
        let clamped = min(max(fraction.isNaN ? 0 : fraction, 0), 1)
        let index = Int((clamped * last - 0.5).rounded(.up))
        return steps[min(max(index, 0), steps.count - 1)]
    }

    /// Only Full needs a hold, because it never asks again.
    public static func needsHold(_ mode: ApprovalMode) -> Bool { mode == .full }

    /// Custom has no step, so the sheet shows it as its own row.
    public static func showsCustom(current: ApprovalMode?) -> Bool { current == .custom }
}

public enum ModeChangeOutcome: Equatable, Sendable {
    case changed(ApprovalMode?)
    /// The computer needs a yes first. Ask, then call again with `acknowledged` true.
    case needsConfirmation(ApprovalMode)
    /// The mode did not change: `kept` is still the mode, and `reason` says why.
    case refused(kept: ApprovalMode?, reason: ModeRefusalReason)
}

public enum ModeChange {
    public static func request(
        current: ApprovalMode?,
        target: ApprovalMode,
        acknowledged: Bool,
        set: (ApprovalMode, Bool) async -> ApprovalModeResult
    ) async -> ModeChangeOutcome {
        guard target.phoneCanSet else { return .refused(kept: current, reason: .customOnDesktop) }
        switch await set(target, acknowledged) {
        case let .changed(_, mode):
            return .changed(mode ?? target)
        case let .refused(refusal):
            if case .localAutoNeedsAcknowledgement = refusal, !acknowledged {
                return .needsConfirmation(target)
            }
            return .refused(kept: current, reason: ModeChip.refusalReason(refusal))
        }
    }
}

public enum SteerOutcome: Equatable, Sendable {
    case done, waiting, failed
}

public enum QueuedSteering {
    /// The only way Steer reaches the computer: through the steer call, never an interrupt.
    public static func outcome(of result: SteerResult?) -> SteerOutcome {
        switch result {
        case .steered, .gone: return .done
        case .stillQueued: return .waiting
        case nil: return .failed
        }
    }
}

public enum SlashCommandID: String, CaseIterable, Sendable {
    case computer, threads, diff, retry, steer, learn, setup, burst
}

/// What choosing a command does. Only `send` carries a prompt, so a command
/// that opens a screen can never reach the wire as text.
public enum SlashEffect: Equatable, Sendable {
    case openComputer
    case openThreads
    case send(String)
    /// Written into the field for the person to finish.
    case insert(String)
}

public struct SlashCommand: Equatable, Identifiable, Sendable {
    public var id: SlashCommandID
    public var title: String
    public var description: String
    public var effect: SlashEffect
}

/// What the open chat allows. The gates follow the desktop composer.
public struct SlashGate: Equatable, Sendable {
    public var isBot: Bool
    public var supportsThreads: Bool
    public var supportsAgents: Bool
    public var canLearn: Bool

    public init(isBot: Bool, supportsThreads: Bool, supportsAgents: Bool = false, canLearn: Bool = false) {
        self.isBot = isBot
        self.supportsThreads = supportsThreads
        self.supportsAgents = supportsAgents
        self.canLearn = canLearn
    }

    public init(chat: Chat, supportsAgents: Bool = false, canLearn: Bool = false) {
        self.init(
            isBot: chat.isBot, supportsThreads: chat.supportsTasks,
            supportsAgents: supportsAgents, canLearn: canLearn
        )
    }
}

public enum SlashCommands {
    /// A goal needs the message to travel in goal mode, which the phone cannot
    /// send. So /goal is not offered at all.
    public static func available(_ gate: SlashGate) -> [SlashCommand] {
        var list: [SlashCommand] = []
        if gate.isBot {
            list.append(SlashCommand(
                id: .computer, title: "/computer",
                description: "Open live screen & desktop controls", effect: .openComputer
            ))
        }
        if gate.supportsThreads {
            list.append(SlashCommand(
                id: .threads, title: "/threads", description: "View and manage threads", effect: .openThreads
            ))
        }
        list.append(SlashCommand(
            id: .diff, title: "/diff", description: "Inspect latest git changes and patches",
            effect: .send("Show git diff and list modified files")
        ))
        list.append(SlashCommand(
            id: .retry, title: "/retry", description: "Retry the last turn with fresh context",
            effect: .send("Please retry the last turn")
        ))
        list.append(SlashCommand(
            id: .steer, title: "/steer", description: "Steer and redirect active execution",
            effect: .send("Pause and explain your current plan")
        ))
        if gate.canLearn {
            list.append(SlashCommand(
                id: .learn, title: "/learn", description: "Draft a reusable skill from this conversation",
                effect: .insert("/learn ")
            ))
        }
        if gate.isBot && gate.supportsAgents {
            list.append(SlashCommand(
                id: .setup, title: "/setup", description: "Have this bot interview you and set itself up",
                effect: .insert("/setup ")
            ))
            list.append(SlashCommand(
                id: .burst, title: "/burst",
                description: "Run many parts of a job at once, then merge the results",
                effect: .insert("/burst ")
            ))
        }
        return list
    }

    /// Typing a `/` opens the list, and typing anything after a space closes it.
    public static func opensOnDraft(_ draft: String) -> Bool {
        draft.hasPrefix("/") && !draft.contains(where: { $0.isWhitespace })
    }

    /// Filtered by what follows the `/`, over the title and the description.
    public static func matching(_ commands: [SlashCommand], draft: String) -> [SlashCommand] {
        guard draft.hasPrefix("/"), draft.count > 1 else { return commands }
        let query = String(draft.dropFirst()).lowercased()
        return commands.filter {
            $0.title.lowercased().contains(query) || $0.description.lowercased().contains(query)
        }
    }
}

/// How a reply's quote joins the message that goes out.
public struct ComposerOutgoing: Equatable, Sendable {
    public var text: String
    /// The reply strip closes only when the quote went out with the message.
    public var clearsReply: Bool

    public init(text: String, clearsReply: Bool) {
        self.text = text
        self.clearsReply = clearsReply
    }
}

public enum ComposerReply {
    /// A slash line stays bare, because the computer reads a command only at
    /// the start of a message. A message with a file and no words sends the
    /// quote as its words. A typed answer to an open question carries no
    /// quote, and still closes the strip.
    public static func outgoing(
        replied: String?, typed: String, hasAttachments: Bool, answersQuestion: Bool
    ) -> ComposerOutgoing {
        let hasReply = replied.map { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } ?? false
        guard hasReply else { return ComposerOutgoing(text: typed, clearsReply: false) }
        if answersQuestion { return ComposerOutgoing(text: typed, clearsReply: true) }
        if typed.isEmpty {
            guard hasAttachments else { return ComposerOutgoing(text: typed, clearsReply: false) }
            return ComposerOutgoing(text: ReplyQuote.quote(replied ?? ""), clearsReply: true)
        }
        if typed.hasPrefix("/") { return ComposerOutgoing(text: typed, clearsReply: false) }
        return ComposerOutgoing(text: ReplyQuote.wrap(replied, text: typed), clearsReply: true)
    }
}
