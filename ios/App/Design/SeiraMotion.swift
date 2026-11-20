import SwiftUI
import UIKit

extension Animation {
    static func seira(_ duration: Double) -> Animation {
        .timingCurve(0.22, 1, 0.36, 1, duration: duration)
    }
}

enum SeiraMotion {
    static let fast = 0.12
    static let base = 0.2
    static let slow = 0.24
    static let sheet = 0.28

    static func animation(_ duration: Double, reduceMotion: Bool) -> Animation {
        reduceMotion ? .easeOut(duration: fast) : .seira(duration)
    }
}

struct SeiraAnimationModifier<V: Equatable>: ViewModifier {
    let duration: Double
    let value: V
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content.animation(SeiraMotion.animation(duration, reduceMotion: reduceMotion), value: value)
    }
}

extension View {
    func seiraAnimation<V: Equatable>(_ duration: Double, value: V) -> some View {
        modifier(SeiraAnimationModifier(duration: duration, value: value))
    }
}

func withSeiraAnimation<R>(_ duration: Double, _ body: () throws -> R) rethrows -> R {
    let animation = SeiraMotion.animation(
        duration, reduceMotion: UIAccessibility.isReduceMotionEnabled)
    return try withAnimation(animation, body)
}
