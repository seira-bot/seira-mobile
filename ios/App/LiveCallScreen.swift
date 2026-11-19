// The full-screen Live call view. The root shows it as a cover while a call
// exists and the person asked for it (`LiveCallController.isScreenPresented`).
import CompanionCore
import SwiftUI

struct LiveCallScreenPresenter: ViewModifier {
    @EnvironmentObject private var liveCall: LiveCallController

    func body(content: Content) -> some View {
        content.fullScreenCover(isPresented: presented) { LiveCallScreen() }
    }

    private var presented: Binding<Bool> {
        Binding(
            get: { liveCall.isScreenPresented && !liveCall.machine.isIdle },
            set: { shown in
                if !shown { liveCall.dismissScreen() }
            }
        )
    }
}

extension View {
    func liveCallScreen() -> some View {
        modifier(LiveCallScreenPresenter())
    }
}

private enum LiveCallStatus: Equatable {
    case calling, connecting, muted, hangingUp, talking

    var showsClock: Bool { self == .talking || self == .muted }
}

struct LiveCallScreen: View {
    @EnvironmentObject private var liveCall: LiveCallController

    var body: some View {
        ZStack {
            Color.seiraCallApp.ignoresSafeArea()
            content
        }
        .seiraAnimation(SeiraMotion.fast, value: liveCall.isMuted)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("live-call-screen")
    }

    @ViewBuilder private var content: some View {
        if let target = liveCall.machine.target {
            if case let .stopped(_, notice) = liveCall.machine.phase {
                LiveCallStoppedContent(target: target, notice: notice)
            } else {
                LiveCallActiveContent(target: target, status: status)
            }
        }
    }

    private var status: LiveCallStatus {
        switch liveCall.machine.phase {
        case .starting: return .calling
        case .ending: return .hangingUp
        case .live:
            if liveCall.machine.isConnecting { return .connecting }
            return liveCall.isMuted ? .muted : .talking
        case .idle, .stopped: return .talking
        }
    }
}

private struct LiveCallActiveContent: View {
    let target: LiveCallMachine.Target
    let status: LiveCallStatus
    @EnvironmentObject private var liveCall: LiveCallController
    @AppStorage(PrefKey.liveCaptions) private var captionsOn = true

    var body: some View {
        VStack(spacing: 0) {
            LiveCallHeader(showsClock: status.showsClock, feed: liveCall.feed)
            LiveCallStage(target: target, status: status, feed: liveCall.feed)
            LiveCallCaptionCard(botId: target.botId, botName: target.botName, visible: captionsOn, feed: liveCall.feed)
                .padding(.bottom, 28)
            LiveCallControls(isEnding: status == .hangingUp)
        }
        .padding(.bottom, 12)
    }
}

private struct LiveCallHeader: View {
    let showsClock: Bool
    @ObservedObject var feed: LiveCallFeed
    @EnvironmentObject private var liveCall: LiveCallController

    var body: some View {
        HStack(spacing: 0) {
            backButton
            Spacer(minLength: 8)
            title
            Spacer(minLength: 8)
            Color.clear.frame(width: 44, height: 44)
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }

    private var backButton: some View {
        Button { liveCall.dismissScreen() } label: {
            Image(systemName: "chevron.down")
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(Color.seiraInk)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 44, fill: .seiraCallControl))
        .accessibilityLabel("Hide call screen")
        .accessibilityIdentifier("live-call-screen-back")
    }

    private var title: some View {
        HStack(spacing: 8) {
            StatusDot(color: .seiraLiveDot)
            Text("Live call")
                .font(.seira(13, .regular, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk2)
            if showsClock {
                Text(verbatim: LiveCallClock.text(feed.elapsedSeconds))
                    .font(.seira(13, .regular, relativeTo: .subheadline))
                    .monospacedDigit()
                    .foregroundStyle(Color.seiraInk3)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct LiveCallStage: View {
    let target: LiveCallMachine.Target
    let status: LiveCallStatus
    @ObservedObject var feed: LiveCallFeed

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                LiveCallRings(
                    side: ringSide(for: geo.size.height),
                    muted: status == .muted,
                    speaking: feed.botIsSpeaking(in: status),
                    botId: target.botId,
                    name: target.botName
                )
                name
                LiveCallStatusRow(status: status, botName: target.botName, feed: feed)
                    .padding(.top, 6)
                Spacer(minLength: 0)
            }
            .frame(width: geo.size.width)
        }
    }

    private var name: some View {
        Text(verbatim: target.botName)
            .font(.seira(22, .semibold, relativeTo: .title2))
            .tracking(-0.22)
            .foregroundStyle(Color.seiraInk)
            .lineLimit(1)
            .padding(.horizontal, 24)
            .padding(.top, 8)
            .accessibilityAddTraits(.isHeader)
    }

    private func ringSide(for height: CGFloat) -> CGFloat {
        min(300, max(160, height - 90))
    }
}

private struct LiveCallRings: View {
    let side: CGFloat
    let muted: Bool
    let speaking: Bool
    let botId: String
    let name: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let scale: CGFloat = muted && !reduceMotion ? 0.94 : 1
        ZStack {
            Circle()
                .stroke(Color.seiraRing3, lineWidth: 1)
                .scaleEffect(scale)
                .animation(Animation.seira(0.3), value: muted)
            Circle()
                .stroke(Color.seiraRing2, lineWidth: 1)
                .padding(side * 0.1)
                .scaleEffect(scale)
                .animation(Animation.seira(0.26), value: muted)
            Circle()
                .fill(Color.seiraRing1)
                .padding(side * 0.2)
            LiveCallAvatar(botId: botId, name: name, size: side * 156 / 300, speaking: speaking)
        }
        .frame(width: side, height: side)
        .accessibilityHidden(true)
    }
}

private struct LiveCallAvatar: View {
    let botId: String
    let name: String
    let size: CGFloat
    /// Nil on the ended screen, where the character holds still.
    var speaking: Bool?
    @EnvironmentObject private var session: Session

    var body: some View {
        if let bot = session.state.bot(botId) {
            if let speaking {
                LiveCallCharacter(bot: bot, size: size, speaking: speaking)
            } else {
                SeiraAvatar(bot: bot, size: size, ringGap: .seiraCallApp)
            }
        } else {
            SeiraInitials(text: initials, size: size, style: .tint)
        }
    }

    private var initials: String {
        let letters = name.split(separator: " ").prefix(2).compactMap(\.first)
        return String(letters).uppercased()
    }
}

/// The bot on the call: it sways, blinks, and its mouth follows the same
/// pulse as the voice bars while the bot speaks.
private struct LiveCallCharacter: View {
    let bot: Bot
    let size: CGFloat
    let speaking: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(paused: !speaking || reduceMotion)) { context in
            BotCharacterView(
                bot: bot, size: size, mood: .idle, motion: .sway,
                talkLevel: speaking ? level(at: context.date) : 0)
        }
    }

    private func level(at date: Date) -> Double {
        0.45 + 0.55 * abs(sin(date.timeIntervalSinceReferenceDate * 5))
    }
}

extension LiveCallFeed {
    fileprivate func botIsSpeaking(in status: LiveCallStatus) -> Bool {
        status == .talking && !captions.line.isEmpty && captions.speaker == .voice
    }
}

private struct LiveCallStatusRow: View {
    let status: LiveCallStatus
    let botName: String
    @ObservedObject var feed: LiveCallFeed

    var body: some View {
        HStack(spacing: 8) {
            LiveCallVoiceBars(muted: status == .muted, speaking: speaking)
            text
                .font(.seira(15, .regular, relativeTo: .body))
                .foregroundStyle(Color.seiraInk2)
                .lineLimit(1)
        }
        .frame(minHeight: 22)
        .padding(.horizontal, 24)
        .accessibilityElement(children: .combine)
    }

    private var speaking: Bool {
        feed.botIsSpeaking(in: status)
    }

    @ViewBuilder private var text: some View {
        switch status {
        case .calling: Text("Calling \(botName)…")
        case .connecting: Text("Connecting…")
        case .muted: Text("You are muted")
        case .hangingUp: Text("Hanging up…")
        case .talking:
            if speaking {
                Text("\(botName) is speaking")
            } else {
                Text("Listening")
            }
        }
    }
}

private struct LiveCallVoiceBars: View {
    let muted: Bool
    let speaking: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let heights: [CGFloat] = [6, 12, 16, 10, 6]

    var body: some View {
        if speaking && !reduceMotion {
            TimelineView(.animation) { context in
                bars { index in Self.heights[index] * level(at: context.date, index: index) }
            }
        } else {
            bars { index in muted ? 4 : Self.heights[index] }
                .seiraAnimation(SeiraMotion.base, value: muted)
        }
    }

    private func bars(height: (Int) -> CGFloat) -> some View {
        // Read the heights now: the ForEach closure escapes, and `height` does not.
        let values = Self.heights.indices.map(height)
        return HStack(spacing: 3) {
            ForEach(values.indices, id: \.self) { index in
                Capsule()
                    .fill(Color.seiraInk2)
                    .frame(width: 3, height: values[index])
            }
        }
        .frame(height: 16)
        .accessibilityHidden(true)
    }

    private func level(at date: Date, index: Int) -> CGFloat {
        let time = date.timeIntervalSinceReferenceDate
        return CGFloat(0.45 + 0.55 * abs(sin(time * 5 + Double(index) * 0.8)))
    }
}

private struct LiveCallCaptionCard: View {
    let botId: String
    let botName: String
    let visible: Bool
    @ObservedObject var feed: LiveCallFeed
    @EnvironmentObject private var session: Session
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var personLine = ""
    @State private var botLine = ""

    var body: some View {
        ZStack {
            if showsCard { card.transition(rise) }
        }
        .seiraAnimation(SeiraMotion.slow, value: showsCard)
        .onValueChange(of: feed.captions, initial: true) { captions in
            record(captions)
        }
    }

    private var showsCard: Bool {
        visible && !(personLine.isEmpty && botLine.isEmpty)
    }

    private var rise: AnyTransition {
        reduceMotion ? .opacity : .opacity.combined(with: .offset(y: 12))
    }

    private var nameColor: Color {
        session.state.bot(botId)?.chiefOfStaff == true ? .seiraName : .seiraInk
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !personLine.isEmpty {
                line(label: Text("You"), labelColor: .seiraInk3, text: personLine, textColor: .seiraInk2)
                    .transition(rise)
            }
            if !botLine.isEmpty {
                line(label: Text(verbatim: botName), labelColor: nameColor, text: botLine, textColor: .seiraInk)
                    .transition(rise)
            }
        }
        .seiraAnimation(SeiraMotion.slow, value: personLine.isEmpty)
        .seiraAnimation(SeiraMotion.slow, value: botLine.isEmpty)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 16)
        .padding(.horizontal, 18)
        .seiraCard(radius: 24)
        .padding(.horizontal, 16)
    }

    private func line(label: Text, labelColor: Color, text: String, textColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            label
                .font(.seira(12, .medium, relativeTo: .caption))
                .foregroundStyle(labelColor)
            Text(verbatim: text)
                .font(.seira(15, .regular, relativeTo: .body))
                .foregroundStyle(textColor)
                .lineSpacing(3)
                .lineLimit(3)
        }
    }

    private func record(_ captions: LiveCaptions) {
        guard !captions.line.isEmpty else { return }
        switch captions.speaker {
        case .user: personLine = captions.line
        case .voice: botLine = captions.line
        }
    }
}

private struct LiveCallControls: View {
    let isEnding: Bool
    @EnvironmentObject private var liveCall: LiveCallController
    @AppStorage(PrefKey.liveCaptions) private var captionsOn = true
    @StateObject private var routes = LiveCallRouteMonitor()
    @State private var endTaps = 0

    var body: some View {
        HStack(alignment: .top, spacing: 20) {
            muteButton
            speakerSlot
            captionsButton
            endButton
        }
        .feedback(.selection, trigger: liveCall.isMuted)
        .feedback(.selection, trigger: liveCall.speakerOn)
        .feedback(.selection, trigger: captionsOn)
        .feedback(.warning, trigger: endTaps)
    }

    private var muteButton: some View {
        let muted = liveCall.isMuted
        let fill: Color = muted ? .seiraInverse : .seiraCallControl
        let ink: Color = muted ? .seiraOnInverse : .seiraInk
        return LiveCallRoundButton(
            symbol: muted ? "mic.slash" : "mic",
            title: muted ? "Unmute" : "Mute",
            accessibilityName: muted ? "Unmute" : "Mute",
            fill: fill,
            ink: ink,
            active: muted,
            identifier: "live-call-screen-mute"
        ) { liveCall.toggleMute() }
    }

    @ViewBuilder private var speakerSlot: some View {
        if routes.hasExternalOutput {
            routeColumn
        } else {
            speakerButton
        }
    }

    private var speakerButton: some View {
        let on = liveCall.speakerOn
        let fill: Color = on ? .seiraInverse : .seiraCallControl
        let ink: Color = on ? .seiraOnInverse : .seiraInk
        return LiveCallRoundButton(
            symbol: "speaker.wave.2",
            title: "Speaker",
            accessibilityName: "Speaker",
            fill: fill,
            ink: ink,
            active: on,
            identifier: "live-call-screen-speaker"
        ) { liveCall.speakerOn.toggle() }
    }

    private var routeColumn: some View {
        VStack(spacing: 8) {
            LiveCallRoutePicker()
                .frame(width: 64, height: 64)
                .background(Color.seiraCallControl, in: Circle())
            LiveCallControlLabel(title: "Audio")
        }
    }

    private var captionsButton: some View {
        let fill: Color = captionsOn ? .seiraInverse : .seiraCallControl
        let ink: Color = captionsOn ? .seiraOnInverse : .seiraInk
        return LiveCallRoundButton(
            symbol: "captions.bubble",
            title: "Captions",
            accessibilityName: "Captions",
            fill: fill,
            ink: ink,
            active: captionsOn,
            identifier: "live-call-screen-captions"
        ) { captionsOn.toggle() }
    }

    private var endButton: some View {
        LiveCallRoundButton(
            symbol: "phone.down.fill",
            title: "End",
            accessibilityName: "Hang up",
            fill: .seiraDangerFill,
            ink: .white,
            active: false,
            identifier: "live-call-screen-hangup"
        ) {
            endTaps += 1
            liveCall.hangUp()
        }
        .disabled(isEnding)
    }
}

private struct LiveCallRoundButton: View {
    let symbol: String
    let title: LocalizedStringKey
    let accessibilityName: LocalizedStringKey
    let fill: Color
    let ink: Color
    let active: Bool
    let identifier: String
    let action: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Button(action: action) {
                Image(systemName: symbol)
                    .font(.system(size: 24, weight: .regular))
                    .foregroundStyle(ink)
            }
            .buttonStyle(SeiraCircleButtonStyle(size: 64, fill: fill))
            .seiraAnimation(SeiraMotion.fast, value: active)
            .accessibilityLabel(accessibilityName)
            .accessibilityAddTraits(active ? .isSelected : [])
            .accessibilityIdentifier(identifier)
            LiveCallControlLabel(title: title)
        }
    }
}

private struct LiveCallControlLabel: View {
    let title: LocalizedStringKey

    var body: some View {
        Text(title)
            .font(.seira(12, .regular, relativeTo: .caption))
            .foregroundStyle(Color.seiraInk3)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(width: 64)
            .accessibilityHidden(true)
    }
}

private struct LiveCallStoppedContent: View {
    let target: LiveCallMachine.Target
    let notice: LiveCallNotice
    @EnvironmentObject private var liveCall: LiveCallController

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                closeButton
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.top, 8)
            Spacer(minLength: 0)
            LiveCallAvatar(botId: target.botId, name: target.botName, size: 96)
                .accessibilityHidden(true)
            Text(verbatim: target.botName)
                .font(.seira(22, .semibold, relativeTo: .title2))
                .tracking(-0.22)
                .foregroundStyle(Color.seiraInk)
                .padding(.top, 12)
            noticeText
            retryButton
            Spacer(minLength: 0)
        }
    }

    private var closeButton: some View {
        Button { liveCall.dismiss() } label: {
            Image(systemName: "xmark")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.seiraInk)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 44, fill: .seiraCallControl))
        .accessibilityLabel("Dismiss")
        .accessibilityIdentifier("live-call-screen-close")
    }

    private var noticeText: some View {
        notice.text
            .font(.seira(15, .regular, relativeTo: .body))
            .foregroundStyle(Color.seiraInk2)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 32)
            .padding(.top, 6)
            .accessibilityIdentifier("live-call-screen-notice")
    }

    @ViewBuilder private var retryButton: some View {
        if notice.canRetry {
            Button { liveCall.retry() } label: {
                Text("Try again").font(.seira(16, .semibold, relativeTo: .body))
            }
            .buttonStyle(SeiraControlButtonStyle())
            .padding(.top, 20)
            .accessibilityIdentifier("live-call-screen-retry")
        }
    }
}
