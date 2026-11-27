import Foundation

/// The choices behind "Move to department".
public enum BotDepartments {
    public static let maxNameLength = 60

    /// The department names the phone already knows, sorted without regard to case.
    public static func names(in bots: [Bot]) -> [String] {
        var seen = Set<String>()
        return bots
            .compactMap { $0.section?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0.lowercased()).inserted }
            .sorted { $0.lowercased() < $1.lowercased() }
    }

    /// A new name with 1 to 60 characters, or nil when it cannot be used.
    public static func cleanName(_ raw: String) -> String? {
        let name = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return (1...maxNameLength).contains(name.count) ? name : nil
    }

    /// The department a bot is in now; empty means General.
    public static func current(of bot: Bot) -> String {
        bot.section?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    /// Same department, with no regard to case; empty is General.
    public static func same(_ current: String, _ other: String) -> Bool {
        current.trimmingCharacters(in: .whitespacesAndNewlines)
            .caseInsensitiveCompare(other.trimmingCharacters(in: .whitespacesAndNewlines)) == .orderedSame
    }
}
