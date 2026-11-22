import Foundation

private let stoppedPrefix = "stopped:"

private let harnessPrefix = try! NSRegularExpression(
    pattern: #"^provider returned a(?: streaming)? completion error:\s*"#,
    options: [.caseInsensitive]
)

private let clientCancel: [NSRegularExpression] = [
    #"^context\s+canceled\.?$"#,
    #"^(?:context\s+canceled)?\s*the\s+request\s+was\s+cancell?ed\s+by\s+the\s+client\.?$"#,
    #"^(?:(?:the|this)\s+)?operation\s+was\s+aborted\.?$"#,
    #"^(?:the\s+)?request\s+was\s+aborted\.?$"#,
    #"^the\s+user\s+aborted\s+a\s+request\.?$"#,
].map { try! NSRegularExpression(pattern: $0, options: [.caseInsensitive]) }

/// The text is only a client abort sentence, alone or inside the harness
/// wrapper. Port of `isClientCancellation` in `shared/client-cancel.ts` on the
/// computer: a real error that merely mentions a cancellation stays an error.
public func isClientCancellation(_ text: String) -> Bool {
    var body = text.trimmingCharacters(in: .whitespacesAndNewlines)
    body = harnessPrefix.stringByReplacingMatches(
        in: body, range: NSRange(body.startIndex..., in: body), withTemplate: ""
    ).trimmingCharacters(in: .whitespacesAndNewlines)
    guard !body.isEmpty else { return false }
    let range = NSRange(body.startIndex..., in: body)
    return clientCancel.contains { $0.firstMatch(in: body, range: range) != nil }
}

/// A row the chat reads as a stop: the stopped activity, a bot bubble that is
/// only the abort sentence, or a failed-turn row whose cause is that sentence.
/// Port of `isCancelledTranscriptRow` in `shared/client-cancel.ts`.
public func isStoppedTurn(_ message: Message) -> Bool {
    if message.role == .user { return false }
    switch message.kind {
    case .text:
        return (message.attachments ?? []).isEmpty && isClientCancellation(message.text ?? "")
    case .activity:
        guard let tool = message.tool else { return false }
        return tool.name.hasPrefix(stoppedPrefix) || isClientCancellation(failedTurnCause(tool.name) ?? "")
    default:
        return false
    }
}
