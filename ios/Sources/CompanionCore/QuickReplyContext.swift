import Foundation

/// Which stock chips fit the open conversation.
///
/// The wire carries no bot kind, so the phone reads the conversation: "Show diff" and "Run tests"
/// make sense only after the bot ran a command or changed a file there. A chip the user wrote or
/// edited always shows. Port of Android `QuickReplyContext`.
public enum QuickReplyContext {
    private static let codeChipIds: Set<String> = ["default.diff", "default.tests"]

    private static let codeTools: Set<String> = [
        "bash", "edit", "multiedit", "write", "notebookedit", "apply_patch", "shell", "exec_command",
        "ran a command", "edited a file", "edited files", "wrote a file",
    ]

    /// A shell command shown by its own text, as some engines name a call.
    private static let shellPrefixes = ["/bin/bash ", "/bin/sh ", "/bin/zsh ", "bash -", "sh -c"]

    public static func isCodeTool(_ tool: ToolActivity) -> Bool {
        if tool.notice == true { return false }
        let name = tool.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return codeTools.contains(name) || shellPrefixes.contains { name.hasPrefix($0) }
    }

    /// True once the bot ran a command or changed a file in this conversation.
    public static func hasCodeActivity(_ messages: [Message]) -> Bool {
        messages.contains { message in
            guard message.kind == .activity, let tool = message.tool else { return false }
            return isCodeTool(tool)
        }
    }

    /// A stock chip that the user has not touched. An edited title or prompt makes it the user's own.
    private static func isUntouchedCodeChip(_ reply: QuickReply) -> Bool {
        guard codeChipIds.contains(reply.id),
              let stock = QuickReply.defaults.first(where: { $0.id == reply.id }) else { return false }
        return reply.title == stock.title && reply.prompt == stock.prompt
    }

    /// The saved chips for a conversation: the untouched code chips drop out until there is code activity.
    public static func chips(_ saved: [QuickReply], codeActivity: Bool) -> [QuickReply] {
        codeActivity ? saved : saved.filter { !isUntouchedCodeChip($0) }
    }
}
