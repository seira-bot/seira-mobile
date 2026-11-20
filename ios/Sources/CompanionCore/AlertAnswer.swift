import Foundation

/// What a person chose on an alert.
public enum AlertChoice: String, Sendable {
    case approve
    case deny

    /// The `behavior` the respond route takes.
    public var behavior: String {
        switch self {
        case .approve: return "allow"
        case .deny: return "deny"
        }
    }
}

/// The request an alert was posted for, as the alert carries it.
///
/// Security invariant: an alert answers a card only when this value matches
/// that card exactly. `requestId` comes from the `notify` frame, and the
/// connection is the one the frame arrived on. Anything else opens the chat.
public struct AlertAnswerTarget: Equatable, Sendable {
    public let connectionId: String
    public let botId: String
    public let threadId: String
    public let requestId: String

    /// The only kind an alert may answer. A question can carry several
    /// questions with their own options, and one tap cannot name which.
    public static let answerableKind = "approval"

    /// The alert of an `approval` that names its request. Nil for every other
    /// frame, for a frame from a computer that predates `requestId`, and when
    /// the connection is unknown.
    public init?(notification: NotificationFrame, connectionId: String?) {
        guard notification.kind == Self.answerableKind,
              let connectionId = Self.nonEmpty(connectionId),
              let requestId = Self.nonEmpty(notification.requestId),
              let botId = Self.nonEmpty(notification.botId),
              let threadId = Self.nonEmpty(notification.threadId)
        else { return nil }
        self.connectionId = connectionId
        self.botId = botId
        self.threadId = threadId
        self.requestId = requestId
    }

    /// The same value, read back from the alert's `userInfo`.
    public init?(payload: [String: String]) {
        guard payload[Self.kindKey] == Self.answerableKind,
              let connectionId = Self.nonEmpty(payload[Self.connectionKey]),
              let requestId = Self.nonEmpty(payload[Self.requestKey]),
              let botId = Self.nonEmpty(payload["botId"]),
              let threadId = Self.nonEmpty(payload["threadId"])
        else { return nil }
        self.connectionId = connectionId
        self.botId = botId
        self.threadId = threadId
        self.requestId = requestId
    }

    /// The `userInfo` of the alert. It holds only ids, never a secret.
    public var payload: [String: String] {
        [
            Self.kindKey: Self.answerableKind,
            Self.connectionKey: connectionId,
            Self.requestKey: requestId,
            "botId": botId,
            "threadId": threadId,
        ]
    }

    /// Where Review goes: the chat, never an answer.
    public var notificationTarget: NotificationTarget? {
        NotificationTarget(botId: botId, threadId: threadId)
    }

    private static let kindKey = "kind"
    private static let connectionKey = "connectionId"
    private static let requestKey = "requestId"

    private static func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return value
    }
}

/// What became of an answer given on an alert.
public enum AlertAnswerOutcome: Equatable, Sendable {
    /// The computer took the answer.
    case answered
    /// Nothing was sent, or the computer could not take it. The person
    /// reviews the request in the chat.
    case needsReview
}

public enum AlertAnswerDecision: Equatable, Sendable {
    /// The alert names the pending card. Send the answer.
    case answer
    /// The alert cannot prove which card it is about. Open the chat.
    case openChat
}

extension CompanionState {
    /// Whether an alert may answer a card. It may only when the alert's
    /// `requestId` equals the `requestId` of a card that is still pending in
    /// the alert's `threadId`, on the connection the alert arrived on, and
    /// that card is a plain permission ask with an allow choice. A skill,
    /// outbound or team memory card must be read in full in the chat, so it
    /// never answers from an alert.
    public func alertAnswerDecision(
        for target: AlertAnswerTarget,
        activeConnectionId: String?
    ) -> AlertAnswerDecision {
        guard let activeConnectionId, activeConnectionId == target.connectionId else { return .openChat }
        let answerable = pendingApprovals.contains { pending in
            guard pending.threadId == target.threadId,
                  let card = pending.message.card,
                  card.requestId == target.requestId
            else { return false }
            return card.isPending
                && card.isPermission
                && card.presentation == .approval
                && card.skillRequest == nil
                && card.teamMemoryRequest == nil
                && card.outboundRequest == nil
                && card.offersPlainAllow
        }
        return answerable ? .answer : .openChat
    }
}

extension OptionCard {
    /// Approve sends `allow` for this one request. The card must offer an
    /// allow choice that is not the standing grant.
    fileprivate var offersPlainAllow: Bool {
        options.contains { option in
            Self.responseBehavior(for: option, isPermission: true) == "allow"
                && option.trimmingCharacters(in: .whitespacesAndNewlines)
                    .caseInsensitiveCompare("Always allow") != .orderedSame
        }
    }
}
