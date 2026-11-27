import SwiftUI

struct AppearanceSegmentedControl: View {
    @AppStorage(PrefKey.appearance) private var stored = AppearanceChoice.auto.rawValue
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Namespace private var haloSpace
    @State private var bodyFrames: [AppearanceChoice: CGRect] = [:]

    private var selected: AppearanceChoice { AppearanceChoice(rawValue: stored) ?? .auto }

    private var haloAnimation: Animation {
        reduceMotion
            ? Animation.easeOut(duration: SeiraMotion.fast)
            : Animation.spring(response: 0.32, dampingFraction: 0.67)
    }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppearanceChoice.allCases) { choice in
                tile(choice)
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .animation(haloAnimation, value: stored)
        .onPreferenceChange(AppearanceBodyFrames.self) { bodyFrames = $0 }
        .background(Color.seiraSegment, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .feedback(.selection, trigger: stored)
        .accessibilityElement(children: .contain)
    }

    private func tile(_ choice: AppearanceChoice) -> some View {
        let isSelected = choice == selected
        let ink: Color = isSelected ? .seiraInk : .seiraInk2
        return Button {
            select(choice)
        } label: {
            VStack(spacing: 4) {
                glyphStage(choice, isSelected: isSelected)
                Text(choice.title)
                    .font(.seira(13, .medium, relativeTo: .footnote))
                    .foregroundStyle(ink)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(choice.title)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : [.isButton])
    }

    private func glyphStage(_ choice: AppearanceChoice, isSelected: Bool) -> some View {
        ZStack {
            if isSelected {
                Circle()
                    .fill(Color.seiraThumb)
                    .frame(width: 56, height: 56)
                    .matchedGeometryEffect(id: "halo", in: haloSpace)
            }
            AppearanceBody(choice: choice, playing: isSelected && !reduceMotion)
        }
        .frame(width: 56, height: 56)
        .background(frameReader(choice))
        .accessibilityHidden(true)
    }

    private func frameReader(_ choice: AppearanceChoice) -> some View {
        GeometryReader { proxy in
            Color.clear.preference(
                key: AppearanceBodyFrames.self,
                value: [choice: proxy.frame(in: .global)]
            )
        }
    }

    @MainActor private func select(_ choice: AppearanceChoice) {
        guard choice != selected else { return }
        if let frame = bodyFrames[choice] {
            AppearanceReveal.origin = CGPoint(x: frame.midX, y: frame.midY)
        }
        stored = choice.rawValue
    }
}

private struct AppearanceBodyFrames: PreferenceKey {
    static var defaultValue: [AppearanceChoice: CGRect] = [:]

    static func reduce(value: inout [AppearanceChoice: CGRect], nextValue: () -> [AppearanceChoice: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, next in next })
    }
}

private struct AppearanceBody: View {
    let choice: AppearanceChoice
    let playing: Bool

    var body: some View {
        if playing {
            TimelineView(.animation) { context in
                glyph(time: context.date.timeIntervalSinceReferenceDate)
            }
        } else {
            glyph(time: 0)
        }
    }

    @ViewBuilder private func glyph(time: Double) -> some View {
        switch choice {
        case .light: SunGlyph(time: time)
        case .dark: MoonGlyph(time: time, blinking: playing)
        case .auto: EclipseGlyph(time: time)
        }
    }
}

private func cycle(_ time: Double, _ seconds: Double) -> Double {
    time.truncatingRemainder(dividingBy: seconds) / seconds
}

private struct SunGlyph: View {
    let time: Double

    var body: some View {
        ZStack {
            rays.rotationEffect(.degrees(cycle(time, 22) * 360))
            Circle().fill(Color.seiraWarning).frame(width: 18, height: 18)
        }
        .frame(width: 40, height: 40)
    }

    private var rays: some View {
        ZStack {
            ForEach(0..<8, id: \.self) { index in
                Capsule()
                    .fill(Color.seiraWarning)
                    .frame(width: 2.5, height: 6)
                    .offset(y: -15)
                    .rotationEffect(.degrees(Double(index) * 45))
            }
        }
    }
}

private struct MoonGlyph: View {
    let time: Double
    let blinking: Bool

    private var stars: [(x: CGFloat, y: CGFloat, size: CGFloat, phase: Double)] {
        [(13, -13, 3.5, 0), (17, 2, 2.5, 0.33), (6, 14, 2.5, 0.66)]
    }

    var body: some View {
        ZStack {
            crescent
            ForEach(0..<stars.count, id: \.self) { index in
                star(stars[index])
            }
        }
        .frame(width: 40, height: 40)
    }

    private var crescent: some View {
        Circle()
            .fill(Color.seiraInk)
            .overlay {
                Circle()
                    .fill(Color.black)
                    .frame(width: 18, height: 18)
                    .offset(x: 7, y: -5)
                    .blendMode(.destinationOut)
            }
            .compositingGroup()
            .frame(width: 24, height: 24)
            .offset(x: -3, y: 1)
    }

    private func star(_ spec: (x: CGFloat, y: CGFloat, size: CGFloat, phase: Double)) -> some View {
        let wave = 0.5 + 0.5 * sin(2 * Double.pi * (cycle(time, 2.4) + spec.phase))
        let opacity = blinking ? 0.25 + 0.75 * wave : 1
        return Circle()
            .fill(Color.seiraInk)
            .frame(width: spec.size, height: spec.size)
            .offset(x: spec.x, y: spec.y)
            .opacity(opacity)
    }
}

private struct EclipseGlyph: View {
    let time: Double

    var body: some View {
        let drift = CGFloat(sin(2 * Double.pi * cycle(time, 8)))
        return ZStack {
            Circle().fill(Color.seiraWarning).frame(width: 24, height: 24)
            Circle()
                .fill(Color.seiraInk)
                .frame(width: 22, height: 22)
                .offset(x: 5 + 3 * drift, y: -2)
        }
        .frame(width: 40, height: 40)
        .clipShape(Circle().inset(by: 4))
    }
}
