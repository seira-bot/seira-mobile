import Foundation

public enum BotAction: Hashable, Sendable {
    case pin, markUnread, markAllRead, move, copyID
}

/// What the "Bot actions" group offers for one bot.
public enum BotActionRules {
    /// Every action, in order. Copy needs a conversation to name.
    public static func visible(for bot: Bot) -> [BotAction] {
        var actions: [BotAction] = [.pin, .markUnread, .markAllRead, .move]
        if !bot.threadId.isEmpty { actions.append(.copyID) }
        return actions
    }

    /// The first row reads Unpin once the bot is pinned.
    public static func isPinned(_ bot: Bot) -> Bool { bot.pinned == true }
}
