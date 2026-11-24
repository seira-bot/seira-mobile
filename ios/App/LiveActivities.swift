import Foundation

/// The scene does not want an `#available` around a stored property, so the bridge owns the
/// coordinator where it is available and does nothing where it is not.
@MainActor
final class LiveActivityBridge {
    private var coordinator: AnyObject?

    func attach(to session: Session) {
        guard #available(iOS 16.2, *) else { return }
        // The scene's onAppear runs again after a language change; reuse the coordinator.
        let coordinator = (self.coordinator as? GlanceCoordinator) ?? GlanceCoordinator()
        coordinator.attach(to: session)
        self.coordinator = coordinator
    }
}
