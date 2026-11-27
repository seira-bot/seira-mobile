import ActivityKit
import Foundation

/// The scene does not want an `#available` around a stored property, so the bridge owns the
/// coordinator where it is available and does nothing where it is not.
@MainActor
final class LiveActivityBridge {
    private var coordinator: AnyObject?

    func attach(to session: Session) {
        guard #available(iOS 16.2, *) else { return }
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-store-no-activities") {
            endAllActivities()
            return
        }
#endif
        // The scene's onAppear runs again after a language change; reuse the coordinator.
        let coordinator = (self.coordinator as? GlanceCoordinator) ?? GlanceCoordinator()
        coordinator.attach(to: session)
        self.coordinator = coordinator
    }

#if DEBUG
    @available(iOS 16.2, *)
    private func endAllActivities() {
        for activity in Activity<GlanceActivityAttributes>.activities {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
        for activity in Activity<BotActivityAttributes>.activities {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
    }
#endif
}
