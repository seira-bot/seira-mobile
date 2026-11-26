import Foundation
import Intents
import UserNotifications
import CompanionCore

/// The on-device notification surface. Delivery comes from live or replayed
/// companion frames; a future APNs relay can feed the same categories and
/// userInfo without changing the rest of the app.
final class NotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationCoordinator()
    static let reviewActionIdentifier = "REVIEW"

    private let center = UNUserNotificationCenter.current()
    private var lastBadgeCount: Int?
    /// Set by `Session`; kept as an id-only value so the notification layer
    /// does not know about SwiftUI navigation or mutable fleet state.
    var responseHandler: ((NotificationTarget) -> Void)?
    /// Set by `Session`. Returns the avatar image of the bot with this id:
    /// its photo, or the Seira character on its plate.
    @MainActor var avatarProvider: (@MainActor (String) async -> Data?)?

    private override init() {
        super.init()
        center.delegate = self
        center.setNotificationCategories(Self.categories)
    }

    /// Security invariant: an alert offers no Approve action. The notification
    /// frame does not name the request, so the app cannot prove which card an
    /// alert is about. A person answers in the chat.
    private static var categories: Set<UNNotificationCategory> {
        let review = UNNotificationAction(
            identifier: reviewActionIdentifier,
            title: String(localized: "Review"),
            options: [.foreground]
        )
        let blocking = UNNotificationCategory(
            identifier: "OPENMAUS_APPROVAL",
            actions: [review],
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: String(localized: "Needs your OK"),
            options: []
        )
        let update = UNNotificationCategory(
            identifier: "OPENMAUS_UPDATE",
            actions: [],
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: "",
            options: []
        )
        return [blocking, update]
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) == true
    }

    func deliver(_ notification: NotificationFrame, sequence: Int?) {
        guard isEnabled(for: notification) else { return }
        Task { [weak self] in
            guard let self else { return }
            let provider = await self.avatarProvider
            let avatar = await provider?(notification.botId)
            self.post(notification, sequence: sequence, avatar: avatar)
        }
    }

    private func isEnabled(for notification: NotificationFrame) -> Bool {
        let key: String
        if notification.kind == "routine-failed" {
            key = PrefKey.notifyRoutines
        } else {
            key = notification.isBlocking ? PrefKey.notifyApprovals : PrefKey.notifyUpdates
        }
        return UserDefaults.standard.object(forKey: key) as? Bool ?? true
    }

    private func post(_ notification: NotificationFrame, sequence: Int?, avatar: Data?) {
        let content = UNMutableNotificationContent()
        content.title = notification.title
        content.body = CloudComputerNotice.words(notification.body)
        content.sound = .default
        content.categoryIdentifier = notification.isBlocking ? "OPENMAUS_APPROVAL" : "OPENMAUS_UPDATE"
        content.threadIdentifier = "bot.\(notification.botId)"
        content.userInfo = [
            "threadId": notification.threadId,
            "botId": notification.botId,
            "kind": notification.kind,
        ]
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
        let strings = response.notification.request.content.userInfo.reduce(into: [String: String]()) { result, pair in
            guard let key = pair.key as? String, let value = pair.value as? String else { return }
            result[key] = value
        }
        if let target = NotificationTarget(payload: strings) { responseHandler?(target) }
        completionHandler()
    }
}
