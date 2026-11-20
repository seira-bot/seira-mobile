import SwiftUI
import UIKit

enum AppearanceChoice: String, CaseIterable, Identifiable {
    case light, dark, auto

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .light: return "Light"
        case .dark: return "Dark"
        case .auto: return "Auto"
        }
    }

    var symbol: String {
        switch self {
        case .light: return "sun.max"
        case .dark: return "moon"
        case .auto: return "iphone"
        }
    }

    var uiStyle: UIUserInterfaceStyle {
        switch self {
        case .light: return .light
        case .dark: return .dark
        case .auto: return .unspecified
        }
    }
}

struct AppearanceApplier: ViewModifier {
    @AppStorage(PrefKey.appearance) private var stored = AppearanceChoice.auto.rawValue

    private var choice: AppearanceChoice { AppearanceChoice(rawValue: stored) ?? .auto }

    func body(content: Content) -> some View {
        content
            .onAppear { apply(choice.uiStyle, animated: false) }
            .onValueChange(of: stored) { _ in apply(choice.uiStyle, animated: true) }
    }

    private func apply(_ style: UIUserInterfaceStyle, animated: Bool) {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
        for window in windows where window.overrideUserInterfaceStyle != style {
            guard animated else {
                window.overrideUserInterfaceStyle = style
                continue
            }
            UIView.transition(
                with: window,
                duration: SeiraMotion.slow,
                options: [.transitionCrossDissolve, .allowUserInteraction]
            ) {
                window.overrideUserInterfaceStyle = style
            }
        }
    }
}

extension View {
    func appliesAppearance() -> some View {
        modifier(AppearanceApplier())
    }
}
