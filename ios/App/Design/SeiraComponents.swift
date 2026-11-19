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

    init(
        bot: Bot,
        size: CGFloat,
        ring: SeiraAvatarRing = .none,
        waiting: Bool = false,
        ringGap: Color = .seiraApp
    ) {
        self.bot = bot
        self.size = size
        self.ring = ring
        self.waiting = waiting
        self.ringGap = ringGap
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
            BotAvatarView(bot: bot, size: size)
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
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
            HStack {
                configuration.label
                Spacer(minLength: 12)
                SeiraSwitch(isOn: configuration.isOn)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .feedback(.selection, trigger: configuration.isOn)
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(configuration.isOn ? "On" : "Off")
    }
}

private struct SeiraSwitch: View {
    let isOn: Bool

    var body: some View {
        Capsule()
            .fill(isOn ? Color.seiraPrimary : Color.seiraTrackOff)
            .frame(width: 51, height: 31)
            .overlay(alignment: isOn ? .trailing : .leading) { knob }
            .seiraAnimation(0.24, value: isOn)
            .accessibilityHidden(true)
    }

    private var knob: some View {
        Circle()
            .fill(isOn ? Color.seiraKnobOn : Color.seiraKnobOff)
            .frame(width: 27, height: 27)
            .shadow(color: .black.opacity(0.18), radius: 2, x: 0, y: 1)
            .padding(2)
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
