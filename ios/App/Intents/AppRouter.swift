import Foundation

enum AppRoute: Equatable {
    /// Security invariant: any app can open a `seira://` link, so a link never
    /// starts a call. Only the app's own intent sets `confirmed`; every other
    /// call waits for the person to tap Start call.
    case talk(confirmed: Bool)
    case newChat
}

@MainActor
final class AppRouter: ObservableObject {
    static let shared = AppRouter()

    @Published var pending: AppRoute?

    nonisolated static func route(for url: URL) -> AppRoute? {
        guard url.scheme?.lowercased() == "seira" else { return nil }
        switch url.host?.lowercased() {
        case "talk": return .talk(confirmed: false)
        case "new-chat": return .newChat
        default: return nil
        }
    }
}
