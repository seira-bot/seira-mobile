import Foundation
import Intents
import UIKit
import UserNotifications
import CompanionCore

/// The on-device notification surface. Delivery comes from live or replayed
/// companion frames; a future APNs relay can feed the same categories and
/// userInfo without changing the rest of the app.
final class NotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationCoordinator()
    static let reviewActionIdentifier = "REVIEW"
    static let approveActionIdentifier = "APPROVE"
    static let denyActionIdentifier = "DENY"
    private static let approvalCategory = "OPENMAUS_APPROVAL"
    private static let answerableApprovalCategory = "SEIRA_APPROVAL_ANSWER"
    private static let updateCategory = "OPENMAUS_UPDATE"

    private let center = UNUserNotificationCenter.current()
    private var lastBadgeCount: Int?
    /// Set by `Session`; kept as an id-only value so the notification layer
    /// does not know about SwiftUI navigation or mutable fleet state.
    var responseHandler: ((NotificationTarget) -> Void)?
    /// Set by `Session`. Returns the avatar image of the bot with this id.
    @MainActor var avatarProvider: (@MainActor (String) async -> Data?)?
    /// Set by `Session`. Answers the request an alert names, or says that the
    /// person has to review it in the chat.
    @MainActor var answerHandler: (@MainActor (AlertAnswerTarget, AlertChoice) async -> AlertAnswerOutcome)?

    private override init() {
        super.init()
        center.delegate = self
        center.setNotificationCategories(Self.categories)
    }

    /// Security invariant: an alert answers a card only when the alert names
    /// that exact request. Approve and Deny exist only on the answerable
    /// category, which `post` picks for an `approval` that carries a request
    /// id. Every other alert offers Review, which opens the chat. Both answers
    /// need an unlocked device.
    private static var categories: Set<UNNotificationCategory> {
        let review = UNNotificationAction(
            identifier: reviewActionIdentifier,
            title: String(localized: "Review"),
            options: [.foreground]
        )
        let approve = UNNotificationAction(
            identifier: approveActionIdentifier,
            title: String(localized: "Approve"),
            options: [.authenticationRequired]
        )
        let deny = UNNotificationAction(
            identifier: denyActionIdentifier,
            title: String(localized: "Deny"),
            options: [.authenticationRequired, .destructive]
        )
        let blocking = UNNotificationCategory(
            identifier: approvalCategory,
            actions: [review],
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: String(localized: "Needs your OK"),
            options: []
        )
        let answerable = UNNotificationCategory(
            identifier: answerableApprovalCategory,
            actions: [approve, deny, review],
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: String(localized: "Needs your OK"),
            options: []
        )
        let update = UNNotificationCategory(
            identifier: updateCategory,
            actions: [],
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: "",
            options: []
        )
        return [blocking, answerable, update]
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) == true
    }

    /// `connectionId` is the computer the frame arrived from. An answer from
    /// the alert goes back to that computer and no other.
    func deliver(_ notification: NotificationFrame, sequence: Int?, connectionId: String?) {
        guard isEnabled(for: notification) else { return }
        Task { [weak self] in
            guard let self else { return }
            let provider = await self.avatarProvider
            let avatar = await provider?(notification.botId)
            self.post(notification, sequence: sequence, avatar: avatar, connectionId: connectionId)
        }
    }

    private func isEnabled(for notification: NotificationFrame) -> Bool {
        let key = notification.isBlocking ? PrefKey.notifyApprovals : PrefKey.notifyUpdates
        return UserDefaults.standard.object(forKey: key) as? Bool ?? true
    }

    private func post(_ notification: NotificationFrame, sequence: Int?, avatar: Data?, connectionId: String?) {
        let content = UNMutableNotificationContent()
        content.title = notification.title
        content.body = notification.body
        content.sound = .default
        let answerTarget = AlertAnswerTarget(notification: notification, connectionId: connectionId)
        content.categoryIdentifier = answerTarget != nil
            ? Self.answerableApprovalCategory
            : notification.isBlocking ? Self.approvalCategory : Self.updateCategory
        content.threadIdentifier = "bot.\(notification.botId)"
        let userInfo: [String: String] = answerTarget?.payload ?? [
            "threadId": notification.threadId,
            "botId": notification.botId,
            "kind": notification.kind,
        ]
        content.userInfo = userInfo
        if notification.isBlocking { content.interruptionLevel = .timeSensitive }

        // A replay after a short disconnect must reconcile a missed alert,
        // but a repeated frame must not draw it twice.
        let identifier = "openmaus.\(notification.threadId).\(sequence.map(String.init) ?? notification.title)"
        center.add(UNNotificationRequest(
            identifier: identifier,
            content: communicationContent(from: content, notification: notification, avatar: avatar),
            trigger: nil
        ))
    }

    /// The bot shows as the sender, with its avatar. Any failure leaves the
    /// plain notification.
    private func communicationContent(
        from content: UNMutableNotificationContent,
        notification: NotificationFrame,
        avatar: Data?
    ) -> UNNotificationContent {
        let sender = INPerson(
            personHandle: INPersonHandle(value: notification.botId, type: .unknown),
            nameComponents: nil,
            displayName: notification.botName,
            image: avatar.map { INImage(imageData: $0) },
            contactIdentifier: nil,
            customIdentifier: notification.botId
        )
        let text = notification.body.isEmpty ? notification.title : "\(notification.title)\n\(notification.body)"
        let intent = INSendMessageIntent(
            recipients: nil,
            outgoingMessageType: .outgoingMessageText,
            content: text,
            speakableGroupName: nil,
            conversationIdentifier: notification.threadId,
            serviceName: nil,
            sender: sender,
            attachments: nil
        )
        let interaction = INInteraction(intent: intent, response: nil)
        interaction.direction = .incoming
        interaction.donate(completion: nil)
        do {
            return try content.updating(from: intent)
        } catch {
            return content
        }
    }

    func setBadge(_ count: Int) {
        let count = max(0, count)
        guard count != lastBadgeCount else { return }
        lastBadgeCount = count
        center.setBadgeCount(count) { [weak self] error in
            if error != nil {
                DispatchQueue.main.async { self?.lastBadgeCount = nil }
            }
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let content = response.notification.request.content
        let strings = content.userInfo.reduce(into: [String: String]()) { result, pair in
            guard let key = pair.key as? String, let value = pair.value as? String else { return }
            result[key] = value
        }
        let choice: AlertChoice
        switch response.actionIdentifier {
        case Self.approveActionIdentifier: choice = .approve
        case Self.denyActionIdentifier: choice = .deny
        default:
            if let target = NotificationTarget(payload: strings) { responseHandler?(target) }
            completionHandler()
            return
        }
        // The system holds the app awake until this handler returns, so the
        // answer must finish first.
        Task { @MainActor [weak self] in
            await self?.answer(choice, payload: strings, title: content.title)
            completionHandler()
        }
    }

    /// Answer from the alert, or hand the request to the person in the chat.
    @MainActor
    private func answer(_ choice: AlertChoice, payload: [String: String], title: String) async {
        guard let target = AlertAnswerTarget(payload: payload) else {
            if let chat = NotificationTarget(payload: payload) {
                sendToReview(chat, title: title, requestId: nil)
            }
            return
        }
        let outcome = await answerHandler?(target, choice) ?? .needsReview
        guard outcome == .needsReview, let chat = target.notificationTarget else { return }
        sendToReview(chat, title: title, requestId: target.requestId)
    }

    /// With the app in front, open the chat. Otherwise a new alert opens it
    /// on a tap, because an action without `.foreground` cannot open it.
    @MainActor
    private func sendToReview(_ target: NotificationTarget, title: String, requestId: String?) {
        if UIApplication.shared.applicationState == .active {
            responseHandler?(target)
            return
        }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = String(localized: "Open Seira to review this.")
        content.sound = .default
        content.categoryIdentifier = Self.updateCategory
        content.threadIdentifier = "bot.\(target.botId)"
        content.userInfo = ["threadId": target.threadId, "botId": target.botId]
        content.interruptionLevel = .timeSensitive
        center.add(UNNotificationRequest(
            identifier: "openmaus.review.\(target.threadId).\(requestId ?? title)",
            content: content,
            trigger: nil
        ))
    }
}
