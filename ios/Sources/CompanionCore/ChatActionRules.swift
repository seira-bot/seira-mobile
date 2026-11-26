import Foundation

public enum MessageActionRules {
    /// The Copy, Reply and More row sits under a bot's finished text. Narration
    /// inside a running turn has no row until the turn ends.
    public static func showsRow(_ message: Message) -> Bool {
        guard message.role != .user, message.kind == .text else { return false }
        guard ChatFindRules.searchableText(message) != nil else { return false }
        return (message.turnId ?? "").isEmpty || message.turnTerminal == true
    }
}

public enum ApprovalButtons {
    private static func isAlways(_ option: String) -> Bool {
        option.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare("Always allow") == .orderedSame
    }

    /// True when the card's own "Always allow" moves to the separate button:
    /// that button is offered, the card sits in a row, and another way to say yes remains.
    public static func movesAlways(_ card: OptionCard, showsAlwaysButton: Bool) -> Bool {
        showsAlwaysButton && !card.stacksOptions
            && card.options.contains { OptionCard.isApproval($0) && !isAlways($0) }
    }

    /// The card's answers as buttons. "Always allow" shows once.
    public static func options(_ card: OptionCard, showsAlwaysButton: Bool) -> [String] {
        let moves = movesAlways(card, showsAlwaysButton: showsAlwaysButton)
        return card.options.filter { !(moves && isAlways($0)) }
    }
}

/// Folding the steps of a turn under one line.
public enum WorkFold {
    /// Full detail lists every step once opened, so it starts open. Reduced starts as one line.
    public static func startsOpen(_ detail: ActivityDetail) -> Bool { detail == .full }

    public static func rows(_ messages: [Message], detail: ActivityDetail) -> [TranscriptRow] {
        let rows = transcriptRows(messages, detail: detail)
        return detail == .full ? foldSteps(rows) : rows
    }

    /// Two or more finished steps in a row sit behind one fold. A failure and a status notice stay alone.
    public static func foldSteps(_ rows: [TranscriptRow]) -> [TranscriptRow] {
        var out: [TranscriptRow] = []
        var run: [Message] = []
        func flush() {
            if run.count > 1 {
                out.append(.activityRun(run))
            } else {
                out.append(contentsOf: run.map(TranscriptRow.message))
            }
            run.removeAll()
        }
        for row in rows {
            if case let .message(message) = row, foldable(message) {
                run.append(message)
            } else {
                flush()
                out.append(row)
            }
        }
        flush()
        return out
    }

    private static func foldable(_ message: Message) -> Bool {
        message.kind == .activity && message.tool?.ok != false && !isStatusNotice(message)
            && !isFailedTurn(message) && !isTurnNotice(message)
    }
}

public enum ComputerScreenState: Equatable, Sendable {
    case working, waiting, idle
}

public struct BotScreenEntry: Equatable, Identifiable, Sendable {
    public var bot: Bot
    public var state: ComputerScreenState
    public var id: String { bot.id }
}

public enum ComputersRules {
    public static let cloud = "cloud"

    /// Bots that work on a cloud desktop, with what each is doing now.
    public static func botScreens(_ bots: [Bot]) -> [BotScreenEntry] {
        bots.filter { $0.hidden != true && $0.computer == cloud }.map { bot in
            let state: ComputerScreenState
            if bot.busy == true {
                state = .working
            } else if bot.waitingOnTeammate == true {
                state = .waiting
            } else {
                state = .idle
            }
            return BotScreenEntry(bot: bot, state: state)
        }
    }
}
