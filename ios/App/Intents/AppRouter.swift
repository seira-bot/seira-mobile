import Foundation

enum AppRoute: Equatable {
    case talk
    case newChat
}

@MainActor
final class AppRouter: ObservableObject {
    static let shared = AppRouter()

    @Published var pending: AppRoute?

    nonisolated static func route(for url: URL) -> AppRoute? {
        guard url.scheme?.lowercased() == "seira" else { return nil }
        switch url.host?.lowercased() {
        case "talk": return .talk
        case "new-chat": return .newChat
        default: return nil
        }
    }
}
