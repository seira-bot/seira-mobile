import Foundation

/// The receipt of a group goal run (`kind == "goal.run"`), patched in place as
/// the run moves. Every field is read leniently and `status` stays a string:
/// a newer computer may add a status, and a card this build cannot read must
/// not fail the message that carries it.
public struct GoalRunCard: Codable, Hashable, Sendable {
    public var runId: String
    public var goal: String
    public var status: String

    public init(runId: String = "", goal: String = "", status: String = "") {
        self.runId = runId
        self.goal = goal
        self.status = status
    }

    private enum CodingKeys: String, CodingKey { case runId, goal, status }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        runId = (try? container.decodeIfPresent(String.self, forKey: .runId)) ?? ""
        goal = (try? container.decodeIfPresent(String.self, forKey: .goal)) ?? ""
        status = (try? container.decodeIfPresent(String.self, forKey: .status)) ?? ""
    }

    /// Only a run in the working status can still be stopped.
    public var isWorking: Bool { status == "working" }
}

public enum GoalChip {
    static let limit = 36

    /// The words of the newest goal that is still running, or nil when none is.
    public static func text(_ messages: [Message]) -> String? {
        guard let run = messages.reversed().lazy.compactMap({ $0.goalRun }).first(where: \.isWorking) else { return nil }
        let words = compact(run.goal, limit: limit)
        return words.isEmpty ? nil : words
    }

    static func compact(_ text: String, limit: Int) -> String {
        let clean = text.split(whereSeparator: \.isWhitespace).joined(separator: " ")
        guard clean.count > limit else { return clean }
        let cut = String(clean.prefix(limit - 1)).trimmingCharacters(in: .whitespaces)
        return cut + "…"
    }
}
