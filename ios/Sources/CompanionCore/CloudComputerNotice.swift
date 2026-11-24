import Foundation

/// The failed-turn row a thread gets when the bot's cloud computer is not set
/// up. A server that predates the fix fails every message with the same row, so
/// the phone words it itself and keeps one notice per run of such failures.
public enum CloudComputerNotice {
    public static let text = "This bot's cloud computer isn't set up."

    private static let legacyState = "cc-needs-key"
    private static let legacyText = try! NSRegularExpression(
        pattern: #"connect your \S+ account|cloud desktops from \S+, a paid service"#,
        options: [.caseInsensitive]
    )

    private static func hasLegacyText(_ value: String) -> Bool {
        legacyText.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)) != nil
    }

    public static func matches(_ tool: ToolActivity) -> Bool {
        guard let cause = failedTurnCause(tool.name) else { return false }
        return tool.place?.state == legacyState || hasLegacyText(cause)
    }

    /// The same words for a notification body that carries the old error.
    public static func words(_ value: String) -> String {
        hasLegacyText(value) ? text : value
    }

    /// Every notice after the first of a run. A run ends when the bot answers,
    /// so a failure that comes back later is announced again.
    public static func repeatedIds(_ messages: [Message]) -> Set<String> {
        var repeated = Set<String>()
        var announced = false
        for message in messages {
            if message.kind == .activity, let tool = message.tool, matches(tool) {
                if announced { repeated.insert(message.id) } else { announced = true }
            } else if message.role == .bot, message.kind == .text {
                announced = false
            }
        }
        return repeated
    }
}
