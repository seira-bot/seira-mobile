import Foundation

public enum ChatMenuItem: String, CaseIterable, Sendable {
    case find, newThread, allThreads, model, members, botScreen, share
}

public enum ChatMenuRules {
    /// A bot has a model and a screen but no members. A group lists its
    /// members and has no model or screen. Without threads there are no thread items.
    public static func items(_ chat: Chat) -> [ChatMenuItem] {
        var items: [ChatMenuItem] = [.find]
        if chat.supportsTasks { items += [.newThread, .allThreads] }
        switch chat {
        case .bot: items += [.model, .botScreen]
        case .room: items.append(.members)
        }
        items.append(.share)
        return items
    }

    public static func showsScreenButton(_ chat: Chat) -> Bool { chat.isBot }

    /// The transcript as plain text for the share sheet: who spoke, then the words.
    public static func transcriptText(_ messages: [Message], botName: String) -> String {
        messages.compactMap { message -> String? in
            guard message.kind == .text, let text = ChatFindRules.searchableText(message) else { return nil }
            let who = message.role == .user ? "You" : botName
            return "\(who): \(text)"
        }.joined(separator: "\n\n")
    }
}
