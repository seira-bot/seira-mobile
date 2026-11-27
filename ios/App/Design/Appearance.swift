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

    @MainActor private func apply(_ style: UIUserInterfaceStyle, animated: Bool) {
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
        let origin = animated ? AppearanceReveal.take() : nil
        let revealWindow: UIWindow? = UIAccessibility.isReduceMotionEnabled ? nil : origin.flatMap { point in
            AppearanceReveal.window(containing: point, in: windows, changingTo: style)
        }
        for window in windows where window.overrideUserInterfaceStyle != style {
            guard animated else {
                window.overrideUserInterfaceStyle = style
                continue
            }
            if let origin, let revealWindow {
                if window === revealWindow {
                    AppearanceReveal.run(in: window, style: style, from: origin)
                } else {
                    window.overrideUserInterfaceStyle = style
                }
            } else {
                crossfade(window, to: style)
            }
        }
    }

    private func crossfade(_ window: UIWindow, to style: UIUserInterfaceStyle) {
        UIView.transition(
            with: window,
            duration: SeiraMotion.slow,
            options: [.transitionCrossDissolve, .allowUserInteraction]
        ) {
            window.overrideUserInterfaceStyle = style
        }
    }
}

@MainActor
enum AppearanceReveal {
    static var origin: CGPoint?

    static let duration: CFTimeInterval = 0.56

    static func take() -> CGPoint? {
        defer { origin = nil }
        return origin
    }

    static func window(
        containing point: CGPoint,
        in windows: [UIWindow],
        changingTo style: UIUserInterfaceStyle
    ) -> UIWindow? {
        let candidates = windows.filter {
            $0.overrideUserInterfaceStyle != style && $0.bounds.contains(point)
        }
        return candidates.first(where: { $0.isKeyWindow }) ?? candidates.first
    }

    static func run(in window: UIWindow, style: UIUserInterfaceStyle, from origin: CGPoint) {
        guard let snapshot = window.snapshotView(afterScreenUpdates: false) else {
            window.overrideUserInterfaceStyle = style
            return
        }
        snapshot.frame = window.bounds
        snapshot.isUserInteractionEnabled = false
        window.addSubview(snapshot)
        window.overrideUserInterfaceStyle = style

        let bounds = window.bounds
        let radius = farthestCornerDistance(from: origin, in: bounds)
        let mask = CAShapeLayer()
        mask.frame = bounds
        mask.fillRule = .evenOdd
        mask.path = path(in: bounds, around: origin, radius: radius)
        snapshot.layer.mask = mask

        let grow = CABasicAnimation(keyPath: "path")
        grow.fromValue = path(in: bounds, around: origin, radius: 0)
        grow.toValue = path(in: bounds, around: origin, radius: radius)
        grow.duration = duration
        grow.timingFunction = CAMediaTimingFunction(controlPoints: 0.22, 1, 0.36, 1)

        CATransaction.begin()
        CATransaction.setCompletionBlock { snapshot.removeFromSuperview() }
        mask.add(grow, forKey: "reveal")
        CATransaction.commit()
    }

    private static func farthestCornerDistance(from point: CGPoint, in rect: CGRect) -> CGFloat {
        let corners = [
            CGPoint(x: rect.minX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY),
            CGPoint(x: rect.minX, y: rect.maxY), CGPoint(x: rect.maxX, y: rect.maxY),
        ]
        return corners.map { hypot($0.x - point.x, $0.y - point.y) }.max() ?? 0
    }

    private static func path(in rect: CGRect, around center: CGPoint, radius: CGFloat) -> CGPath {
        let path = CGMutablePath()
        path.addRect(rect)
        path.addEllipse(in: CGRect(
            x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
        return path
    }
}

extension View {
    func appliesAppearance() -> some View {
        modifier(AppearanceApplier())
    }
}
