import SwiftUI

/// Times in seconds from the first appearance of the overlay.
enum LaunchVisorTimeline {
    struct Frame: Equatable {
        var lid: Double
        var wordmarkAlpha: Double
        var faceScale: Double
        var faceOpacity: Double
        var holeFraction: Double
        var overlayOpacity: Double
    }

    static let revealStart = 0.45
    static let revealDuration = 0.45
    static let total = revealStart + revealDuration
    static let reducedMotionTotal = 0.50

    static func totalDuration(reduceMotion: Bool) -> Double {
        reduceMotion ? reducedMotionTotal : total
    }

    static func frame(at t: Double, reduceMotion: Bool) -> Frame {
        reduceMotion ? reducedFrame(at: t) : fullFrame(at: t)
    }

    private static func fullFrame(at t: Double) -> Frame {
        let reveal = clamp((t - revealStart) / revealDuration)
        let face = clamp((t - revealStart) / 0.25)
        let faceEase = cubicBezier(0.4, 0, 0.2, 1, face)
        let wordmarkFade = 1 - clamp((t - revealStart) / 0.20)
        return Frame(
            lid: lid(at: t),
            wordmarkAlpha: decelerate(clamp(t / 0.30)) * wordmarkFade,
            faceScale: 1 + 0.25 * faceEase,
            faceOpacity: 1 - faceEase,
            holeFraction: cubicBezier(0.55, 0, 0.2, 1, reveal),
            overlayOpacity: 1
        )
    }

    private static func reducedFrame(at t: Double) -> Frame {
        Frame(
            lid: 1,
            wordmarkAlpha: 1,
            faceScale: 1,
            faceOpacity: 1,
            holeFraction: 0,
            overlayOpacity: 1 - clamp((t - 0.30) / 0.20)
        )
    }

    static func lid(at t: Double) -> Double {
        switch t {
        case ..<0.12:
            return 1
        case ..<0.20:
            let x = (t - 0.12) / 0.08
            return 1 - 0.9 * x * x
        case ..<0.28:
            let x = (t - 0.20) / 0.08
            return 0.1 + 0.9 * decelerate(x)
        default:
            return 1
        }
    }

    private static func decelerate(_ x: Double) -> Double {
        1 - (1 - x) * (1 - x)
    }

    private static func clamp(_ x: Double) -> Double {
        min(max(x, 0), 1)
    }

    /// CSS-style cubic-bezier easing. iOS 16 has no `UnitCurve`.
    static func cubicBezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        func coordinate(_ a: Double, _ b: Double, _ s: Double) -> Double {
            let u = 1 - s
            return 3 * u * u * s * a + 3 * u * s * s * b + s * s * s
        }
        var low = 0.0
        var high = 1.0
        var s = x
        for _ in 0..<40 {
            let value = coordinate(x1, x2, s)
            if abs(value - x) < 1e-6 { break }
            if value < x { low = s } else { high = s }
            s = (low + high) / 2
        }
        return coordinate(y1, y2, s)
    }
}

/// The ink panel with a circular hole at the centre. The even-odd fill cuts the hole.
private struct VisorInk: Shape {
    var holeRadius: Double

    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addRect(rect)
        path.addEllipse(in: CGRect(
            x: rect.midX - holeRadius,
            y: rect.midY - holeRadius,
            width: holeRadius * 2,
            height: holeRadius * 2
        ))
        return path
    }
}

/// Continues the system launch screen (ink colour and eyes) with a blink, the wordmark and a circle reveal.
struct LaunchVisorView: View {
    let onFinished: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start: Date?

    private static let eyesPivot = UnitPoint(x: 0.5, y: 0.5425)
    private static let wordmarkSize = CGSize(width: 112, height: 20.8)
    private static let wordmarkBottomInset = 60.0

    var body: some View {
        GeometryReader { proxy in
            TimelineView(.animation) { context in
                layers(in: proxy.size, at: elapsed(context.date))
            }
        }
        .ignoresSafeArea()
        .contentShape(Rectangle())
        .accessibilityHidden(true)
        .task {
            start = Date()
            let seconds = LaunchVisorTimeline.totalDuration(reduceMotion: reduceMotion)
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            if !Task.isCancelled { onFinished() }
        }
    }

    private func elapsed(_ date: Date) -> Double {
        start.map { date.timeIntervalSince($0) } ?? 0
    }

    private func layers(in size: CGSize, at time: Double) -> some View {
        let frame = LaunchVisorTimeline.frame(at: time, reduceMotion: reduceMotion)
        let reach = hypot(size.width / 2, size.height / 2)
        return ZStack {
            VisorInk(holeRadius: frame.holeFraction * reach)
                .fill(Color("LaunchBackground"), style: FillStyle(eoFill: true))
            eyes(frame)
                .position(x: size.width / 2, y: size.height / 2)
            wordmark(frame)
                .position(
                    x: size.width / 2,
                    y: size.height - Self.wordmarkBottomInset - Self.wordmarkSize.height / 2
                )
        }
        .opacity(frame.overlayOpacity)
    }

    private func eyes(_ frame: LaunchVisorTimeline.Frame) -> some View {
        Image("LaunchEyes")
            .scaleEffect(x: 1, y: frame.lid, anchor: Self.eyesPivot)
            .scaleEffect(frame.faceScale)
            .opacity(frame.faceOpacity)
    }

    private func wordmark(_ frame: LaunchVisorTimeline.Frame) -> some View {
        Image("LaunchWordmark")
            .resizable()
            .frame(width: Self.wordmarkSize.width, height: Self.wordmarkSize.height)
            .opacity(frame.wordmarkAlpha)
    }
}
