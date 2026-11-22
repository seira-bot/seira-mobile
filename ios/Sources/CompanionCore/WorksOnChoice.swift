import Foundation

public struct WorksOnOffer: Equatable, Sendable {
    public var withoutComputer: Bool
    public var ownServer: Bool
}

/// Where a phone that may administer can move a bot from the cloud-computer notice.
public enum WorksOnChoice {
    private static let notSetUp = "cc-not-set-up"

    public static func isNotice(_ tool: ToolActivity) -> Bool {
        (tool.notice == true && tool.place?.state == notSetUp) || CloudComputerNotice.matches(tool)
    }

    /// The newest notice row the transcript draws.
    public static func noticeMessageId(in messages: [Message]) -> String? {
        let repeated = CloudComputerNotice.repeatedIds(messages)
        return messages.last {
            $0.kind == .activity && !repeated.contains($0.id) && $0.tool.map(isNotice) == true
        }?.id
    }

    public static func ownServerReady(vpsAlias: String?, configured: Bool?) -> Bool {
        let alias = vpsAlias?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return !alias.isEmpty || configured == true
    }

    public static func offer(
        canAdminister: Bool, running: Bool, computer: String?, cloudBackend: String?, ownServerReady: Bool
    ) -> WorksOnOffer? {
        guard canAdminister, !running else { return nil }
        let onOwnServer = computer == "cloud" && cloudBackend == "vps"
        let offer = WorksOnOffer(
            withoutComputer: computer != "off",
            ownServer: ownServerReady && !onOwnServer
        )
        return offer.withoutComputer || offer.ownServer ? offer : nil
    }

    /// The server's own sentence for a refusal; nil when it sent none.
    public static func refusal(_ error: Error) -> String? {
        guard let api = error as? APIError, case let .status(_, message) = api,
              let message, !message.trimmingCharacters(in: .whitespaces).isEmpty
        else { return nil }
        return message
    }
}
