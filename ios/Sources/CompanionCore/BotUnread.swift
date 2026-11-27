import Foundation

extension Bot {
    /// The threads of this bot that still show as unread, in list order. A bot
    /// without a task list counts as one thread. A task without its own flag
    /// follows the bot's flag, but only for the selected thread.
    public var unreadThreadIds: [String] {
        guard let tasks, !tasks.isEmpty else { return unread ? [threadId] : [] }
        return tasks
            .filter { $0.unread ?? ($0.threadId == threadId && unread) }
            .map(\.threadId)
    }
}
