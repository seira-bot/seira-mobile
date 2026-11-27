import CompanionCore
import SwiftUI

enum SeiraAvatarRing {
    case none, working
}

struct SeiraAvatar: View {
    let bot: Bot
    let size: CGFloat
    var ring: SeiraAvatarRing = .none
    var waiting = false
    var ringGap: Color = .seiraApp
    var mood: CharacterMood = .idle
    var motion: SeiraCharacterMotion = .still

    init(
        bot: Bot,
        size: CGFloat,
        ring: SeiraAvatarRing = .none,
        waiting: Bool = false,
        ringGap: Color = .seiraApp,
        mood: CharacterMood = .idle,
        motion: SeiraCharacterMotion = .still
    ) {
        self.bot = bot
        self.size = size
        self.ring = ring
        self.waiting = waiting
        self.ringGap = ringGap
        self.mood = mood
        self.motion = motion
    }

    var body: some View {
        face
            .overlay { workingRing }
            .overlay(alignment: .bottomTrailing) { waitingDot }
            .frame(width: size, height: size)
    }

    private var face: some View {
        ZStack {
            Circle().fill(SeiraIdentity.plate(hex: bot.color))
            portrait
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }

    @ViewBuilder private var portrait: some View {
        if motion == .still {
            BotAvatarView(bot: bot, size: size, state: mood)
        } else {
            BotCharacterView(bot: bot, size: size, mood: mood, motion: motion)
        }
    }

    @ViewBuilder private var workingRing: some View {
        if ring == .working {
            ZStack {
                Circle().stroke(ringGap, lineWidth: 2.5).padding(-1.25)
                Circle().stroke(Color.seiraSuccess, lineWidth: 2).padding(-3.5)
            }
            .accessibilityHidden(true)
        }
    }

    @ViewBuilder private var waitingDot: some View {
        if waiting {
            Circle()
                .fill(Color.seiraWarning)
                .frame(width: 12, height: 12)
                .padding(2.5)
                .background(ringGap, in: Circle())
                .offset(x: 1, y: 1)
                .accessibilityHidden(true)
        }
    }
}

enum SeiraInitialsStyle {
    case tint, ink
}

struct SeiraInitials: View {
    let text: String
    let size: CGFloat
    let style: SeiraInitialsStyle

    var body: some View {
        Text(text)
            .font(.seira(size * 0.38, .semibold))
            .foregroundStyle(textColor)
            .frame(width: size, height: size)
            .background(fill, in: Circle())
    }

    private var fill: Color { style == .tint ? .seiraTint : .seiraPrimary }
    private var textColor: Color { style == .tint ? .seiraInk : .seiraOnPrimary }
}

struct StatusDot: View {
    let color: Color
    var size: CGFloat = 6

    init(color: Color, size: CGFloat = 6) {
        self.color = color
        self.size = size
    }

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

struct SeiraMark: View {
    let size: CGFloat

    var body: some View {
        Image("SeiraMark")
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: size, height: size)
    }
}

private struct SeiraPressEffect: ViewModifier {
    let isPressed: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .scaleEffect(isPressed && !reduceMotion ? 0.96 : 1)
            .opacity(isPressed ? 0.85 : 1)
            .animation(Animation.seira(SeiraMotion.fast), value: isPressed)
    }
}

struct SeiraPrimaryButtonStyle: ButtonStyle {
    var height: CGFloat = 52

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.seira(16, .semibold))
            .foregroundStyle(Color.seiraOnPrimary)
            .frame(maxWidth: .infinity, minHeight: max(height, 44))
            .background(Color.seiraPrimary, in: Capsule())
            .contentShape(Capsule())
            .modifier(SeiraPressEffect(isPressed: configuration.isPressed))
    }
}

struct SeiraControlButtonStyle: ButtonStyle {
    var height: CGFloat = 44

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(Color.seiraInk)
            .padding(.horizontal, 20)
            .frame(minWidth: 44, minHeight: max(height, 44))
            .background(Color.seiraControl, in: Capsule())
            .contentShape(Capsule())
            .modifier(SeiraPressEffect(isPressed: configuration.isPressed))
    }
}

struct SeiraCircleButtonStyle: ButtonStyle {
    var size: CGFloat = 44
    let fill: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(width: size, height: size)
            .background(fill, in: Circle())
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Circle())
            .modifier(SeiraPressEffect(isPressed: configuration.isPressed))
    }
}

struct SeiraToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            configuration.label
        }
        .buttonStyle(SeiraSwitchRowStyle(isOn: configuration.isOn))
        .feedback(.selection, trigger: configuration.isOn)
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(configuration.isOn ? "On" : "Off")
    }
}

private struct SeiraSwitchRowStyle: ButtonStyle {
    let isOn: Bool

    func makeBody(configuration: Configuration) -> some View {
        HStack {
            configuration.label
            Spacer(minLength: 12)
            SeiraSwitch(isOn: isOn, isPressed: configuration.isPressed)
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }
}

private struct SwitchCheckShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + rect.width * 0.12, y: rect.minY + rect.height * 0.55))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.4, y: rect.minY + rect.height * 0.82))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.88, y: rect.minY + rect.height * 0.22))
        return path
    }
}

private struct SeiraSwitch: View {
    let isOn: Bool
    let isPressed: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var stretching = false

    private var trackWidth: CGFloat { 51 }
    private var trackHeight: CGFloat { 31 }
    private var knobHeight: CGFloat { 27 }
    private var inkDiameter: CGFloat { 80 }

    private var widened: Bool { (isPressed || stretching) && !reduceMotion }
    private var knobWidth: CGFloat { widened ? 33 : 27 }
    private var knobX: CGFloat { isOn ? trackWidth - 2 - knobWidth : 2 }
    private var restingKnobCenter: CGFloat { isOn ? trackWidth - 2 - knobHeight / 2 : 2 + knobHeight / 2 }

    private var travelAnimation: Animation {
        reduceMotion
            ? Animation.easeOut(duration: SeiraMotion.fast)
            : Animation.spring(response: 0.28, dampingFraction: 0.61)
    }

    private var inkAnimation: Animation {
        reduceMotion
            ? Animation.easeOut(duration: SeiraMotion.fast)
            : Animation.timingCurve(0.22, 1, 0.36, 1, duration: 0.28)
    }

    private var stretchAnimation: Animation {
        Animation.spring(response: 0.2, dampingFraction: 0.7)
    }

    var body: some View {
        track
            .frame(width: trackWidth, height: trackHeight)
            .overlay(alignment: .leading) { knob }
            .onValueChange(of: isOn) { _ in stretch() }
            .accessibilityHidden(true)
    }

    private var track: some View {
        Capsule()
            .fill(Color.seiraTrackOff)
            .overlay(alignment: .leading) { ink }
            .clipShape(Capsule())
    }

    @ViewBuilder private var ink: some View {
        if reduceMotion {
            Rectangle()
                .fill(Color.seiraPrimary)
                .opacity(isOn ? 1 : 0)
                .animation(inkAnimation, value: isOn)
        } else {
            Circle()
                .fill(Color.seiraPrimary)
                .frame(width: inkDiameter, height: inkDiameter)
                .scaleEffect(isOn ? 1 : 0.01)
                .offset(x: restingKnobCenter - inkDiameter / 2)
                .animation(inkAnimation, value: isOn)
        }
    }

    private var knob: some View {
        Capsule()
            .fill(isOn ? Color.seiraKnobOn : Color.seiraKnobOff)
            .frame(width: knobWidth, height: knobHeight)
            .shadow(color: .black.opacity(0.18), radius: 2, x: 0, y: 1)
            .overlay { check }
            .offset(x: knobX)
            .animation(travelAnimation, value: isOn)
            .animation(stretchAnimation, value: widened)
    }

    private var check: some View {
        SwitchCheckShape()
            .trim(from: 0, to: reduceMotion || isOn ? 1 : 0)
            .stroke(Color.seiraPrimary, style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            .frame(width: 11, height: 11)
            .opacity(reduceMotion ? (isOn ? 1 : 0) : 1)
            .animation(inkAnimation, value: isOn)
    }

    private func stretch() {
        guard !reduceMotion else { return }
        stretching = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.14) { stretching = false }
    }
}

extension View {
    func seiraCard(radius: CGFloat = 22, fill: Color = .seiraTint) -> some View {
        background(fill, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}

struct SeiraFade: View {
    let edge: Edge
    let height: CGFloat
    let color: Color

    var body: some View {
        LinearGradient(colors: [color, color.opacity(0)], startPoint: start, endPoint: end)
            .frame(height: height)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    private var start: UnitPoint {
        switch edge {
        case .top: return .top
        case .bottom: return .bottom
        case .leading: return .leading
        case .trailing: return .trailing
        }
    }

    private var end: UnitPoint {
        switch edge {
        case .top: return .bottom
        case .bottom: return .top
        case .leading: return .trailing
        case .trailing: return .leading
        }
    }
}
