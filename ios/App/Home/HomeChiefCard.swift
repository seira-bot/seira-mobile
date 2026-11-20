import CompanionCore
import SwiftUI

struct HomeChiefCard: View {
    let bot: Bot
    let status: RosterRowStatus
    let workingLine: String
    let canTalk: Bool
    let open: () -> Void
    let talk: () -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        HStack(spacing: 12) {
            Button(action: open) { summary }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: spokenSummary))
                .accessibilityAddTraits(.isButton)
                .accessibilityIdentifier("home-chief-card")
            if canTalk { talkButton }
        }
        .padding(.leading, 16)
        .padding(.trailing, 14)
        .padding(.vertical, 14)
        .seiraCard(radius: 24)
        .seiraAnimation(SeiraMotion.base, value: status)
        .padding(.horizontal, 16)
    }

    private var summary: some View {
        HStack(spacing: 14) {
            SeiraAvatar(
                bot: bot,
                size: 56,
                ring: status == .working ? .working : .none,
                ringGap: .seiraTint,
                mood: status == .working ? .working : .idle,
                motion: .sway
            )
            VStack(alignment: .leading, spacing: 4) {
                nameLine
                statusLine
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
    }

    @ViewBuilder private var nameLine: some View {
        if typeSize.isAccessibilitySize {
            VStack(alignment: .leading, spacing: 2) { nameText; roleText }
        } else {
            HStack(alignment: .firstTextBaseline, spacing: 8) { nameText; roleText }
        }
    }

    private var nameText: some View {
        Text(verbatim: bot.name)
            .font(.seira(17, .semibold, relativeTo: .headline))
            .foregroundStyle(Color.seiraInk)
            .lineLimit(1)
            .layoutPriority(1)
    }

    private var roleText: some View {
        Text(verbatim: role)
            .font(.seira(12, relativeTo: .caption))
            .foregroundStyle(Color.seiraInk3)
            .lineLimit(1)
    }

    private var statusLine: some View {
        HStack(spacing: 6) {
            StatusDot(color: dotColor)
            Text(verbatim: statusText)
                .font(.seira(13, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk2)
                .lineLimit(1)
        }
    }

    private var talkButton: some View {
        Button(action: talk) {
            Image(systemName: "waveform")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.seiraOnPrimary)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 48, fill: .seiraPrimary))
        .accessibilityLabel("Talk to \(bot.name)")
        .accessibilityIdentifier("home-talk")
    }

    private var role: String {
        let title = bot.title.trimmingCharacters(in: .whitespacesAndNewlines)
        return title.isEmpty ? String(localized: "Chief of Staff") : title
    }

    private var statusText: String {
        switch status {
        case .working:
            let line = workingLine.trimmingCharacters(in: .whitespacesAndNewlines)
            return line.isEmpty ? String(localized: "Working") : line
        case .waitingOnYou:
            return String(localized: "Needs your OK")
        case .idle:
            return String(localized: "Ready")
        }
    }

    private var dotColor: Color {
        switch status {
        case .working: return .seiraSuccess
        case .waitingOnYou: return .seiraWarning
        case .idle: return .seiraInk3
        }
    }

    private var spokenSummary: String { "\(bot.name), \(role), \(statusText)" }
}

struct HomeLiveCallDisclosure: ViewModifier {
    @Binding var bot: Bot?
    let start: (Bot) -> Void

    func body(content: Content) -> some View {
        content.alert(
            Text("A Live call sends your voice to OpenAI, along with the chat's recent messages, the bot's answers and the details of any approval it asks for. The OpenAI key stays on your computer."),
            isPresented: Binding(
                get: { bot != nil },
                set: { if !$0 { bot = nil } }
            ),
            presenting: bot
        ) { target in
            Button("Cancel", role: .cancel) {}
            Button("Start call") {
                LiveCallDisclosure().accept()
                start(target)
            }
        }
    }
}
