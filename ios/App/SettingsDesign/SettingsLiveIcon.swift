import SwiftUI

/// Rebuilds its content on every frame of an animation of `progress`.
/// SwiftUI animates only the end values of a plain view, so an effect that
/// rises and falls between 0 and 1 needs an `Animatable` view to be seen.
private struct AnimatedProgress<Content: View>: View, Animatable {
    var progress: Double
    let content: (Double) -> Content

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View { content(progress) }
}

private enum LiveIconEffect {
    case bell, burst, pulse, bounce

    init(symbol: String) {
        switch symbol {
        case "bell": self = .bell
        case "checkmark.circle": self = .burst
        case "exclamationmark.arrow.circlepath": self = .pulse
        default: self = .bounce
        }
    }

    func duration(turningOn: Bool) -> Double {
        switch self {
        case .bell: return turningOn ? 0.6 : 0.3
        case .burst: return 0.52
        case .pulse: return 0.6
        case .bounce: return 0.3
        }
    }
}

private struct SymbolReplaceTransition: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.contentTransition(.symbolEffect(.replace))
        } else {
            content
        }
    }
}

struct SettingsLiveIcon: View {
    let symbol: String
    let isOn: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var progress = 0.0
    @State private var turnedOn = true

    private var effect: LiveIconEffect { LiveIconEffect(symbol: symbol) }

    private var shownSymbol: String {
        effect == .bell && !isOn ? "bell.slash" : symbol
    }

    private var colourAnimation: Animation {
        Animation.easeOut(duration: reduceMotion ? SeiraMotion.fast : SeiraMotion.base)
    }

    var body: some View {
        AnimatedProgress(progress: progress) { value in
            layers(value)
        }
        .frame(width: 22, height: 22)
        .background(tile)
        .animation(colourAnimation, value: isOn)
        .onValueChange(of: isOn) { newValue in play(turningOn: newValue) }
        .accessibilityHidden(true)
    }

    private var tile: some View {
        RoundedRectangle(cornerRadius: 8, style: .continuous)
            .fill(isOn ? Color.seiraControl : Color.clear)
            .frame(width: 30, height: 30)
    }

    private func layers(_ value: Double) -> some View {
        ZStack {
            glyph(value)
            if effect == .bell { bellExtras(value) }
            if effect == .burst { dots(value) }
        }
    }

    private func glyph(_ value: Double) -> some View {
        let ink: Color = isOn ? .seiraInk : .seiraInk3
        return Image(systemName: shownSymbol)
            .font(.system(size: 17, weight: .regular))
            .foregroundStyle(ink)
            .modifier(SymbolReplaceTransition())
            .rotationEffect(.degrees(wiggleAngle(value)), anchor: .top)
            .scaleEffect(glyphScale(value))
    }

    private func wiggleAngle(_ value: Double) -> Double {
        guard effect == .bell, turnedOn else { return 0 }
        return 14 * sin(value * .pi * 5) * (1 - value)
    }

    private func glyphScale(_ value: Double) -> CGFloat {
        let swell = sin(value * .pi)
        switch effect {
        case .pulse: return CGFloat(1 + 0.18 * swell)
        case .bounce: return CGFloat(1 + 0.12 * swell)
        case .bell, .burst: return 1
        }
    }

    @ViewBuilder private func bellExtras(_ value: Double) -> some View {
        if turnedOn {
            arcs(value)
        } else {
            strike(value)
        }
    }

    private func arcs(_ value: Double) -> some View {
        let fade = min(1, value * 6) * (1 - value)
        return ZStack {
            arc(radius: 13)
            arc(radius: 17)
        }
        .opacity(fade)
        .scaleEffect(0.85 + 0.3 * value)
    }

    private func arc(radius: CGFloat) -> some View {
        let style = StrokeStyle(lineWidth: 1.5, lineCap: .round)
        return ZStack {
            Circle().trim(from: 0, to: 0.12).stroke(Color.seiraInk2, style: style)
                .rotationEffect(.degrees(-22))
            Circle().trim(from: 0, to: 0.12).stroke(Color.seiraInk2, style: style)
                .rotationEffect(.degrees(158))
        }
        .frame(width: radius * 2, height: radius * 2)
    }

    private func strike(_ value: Double) -> some View {
        let fade = value > 0.9 ? (1 - value) * 10 : 1
        return StrikeLine()
            .trim(from: 0, to: min(1, value * 1.4))
            .stroke(Color.seiraInk3, style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
            .frame(width: 18, height: 18)
            .opacity(fade)
    }

    private func dots(_ value: Double) -> some View {
        let fade = min(1, value * 8) * (1 - value)
        let distance = 17 + 5 * value
        return ZStack {
            ForEach(0..<8, id: \.self) { index in
                Circle()
                    .fill(Color.seiraInk2)
                    .frame(width: 2.5, height: 2.5)
                    .offset(y: -distance)
                    .rotationEffect(.degrees(Double(index) * 45))
            }
        }
        .opacity(fade)
    }

    private func play(turningOn: Bool) {
        guard !reduceMotion else { return }
        turnedOn = turningOn
        let duration = effect.duration(turningOn: turningOn)
        var reset = Transaction()
        reset.disablesAnimations = true
        withTransaction(reset) { progress = 0 }
        DispatchQueue.main.async {
            withAnimation(.linear(duration: duration)) { progress = 1 }
        }
    }
}

private struct StrikeLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 1, y: rect.minY + 1))
        path.addLine(to: CGPoint(x: rect.maxX - 1, y: rect.maxY - 1))
        return path
    }
}
