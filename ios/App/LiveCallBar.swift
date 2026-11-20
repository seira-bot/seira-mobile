// The call bar: a Live call's controls, compact, above the composer, so the
// chat stays in view while you talk. Three shapes:
//
// - LiveCallBar: this phone's call on this chat — bot, "Live with Ada ·
//   m:ss", one caption line, settings, expand, mute, hang up; or why it
//   stopped, with Try again.
// - RemoteLiveCallBar: a call on this chat that another device holds. The
//   Mac's word only (state.liveCall): "Live with Ada · m:ss", where the call
//   is on a second line, and a hang up that asks the Mac.
// - LiveCallBanner: a thin line on other screens while this phone is on a
//   call, tapping back to it.
import CompanionCore
import SwiftUI

struct LiveCallBar: View {
    let botName: String
    @EnvironmentObject private var liveCall: LiveCallController
    @State private var showingSettings = false
    @State private var hangUpTaps = 0

    var body: some View {
        Group {
            switch liveCall.machine.phase {
            case .idle:
                EmptyView()
            case .starting:
                // Hang up already: a start can take seconds (the microphone
                // prompt, the offer, the Mac asking OpenAI), and a call the
                // person no longer wants must not be one they have to wait
                // out. The controller ends whatever a late 201 created.
                row(
                    title: Text("Calling \(botName)…").accessibilityIdentifier("live-call-title"),
                    captions: false,
                    controls: .hangUpOnly
                )
            case .live:
                row(
                    title: LiveCallTitle(
                        botName: botName,
                        connecting: liveCall.machine.isConnecting,
                        feed: liveCall.feed
                    ),
                    captions: true,
                    controls: .full
                )
            case .ending:
                row(title: Text("Hanging up…").accessibilityIdentifier("live-call-title"), captions: false, controls: .none)
            case let .stopped(_, notice):
                stoppedRow(notice)
            }
        }
        // The gear and its sheet belong to the live row. When the call leaves
        // it the sheet goes with the row, and the flag must go too, or a
        // Try again that reaches live would open the sheet uninvited.
        .onValueChange(of: isLive) { live in
            if !live { showingSettings = false }
        }
    }

    private var isLive: Bool {
        if case .live = liveCall.machine.phase { return true }
        return false
    }

    /// Which buttons a row carries: none while hanging up, expand and the
    /// red hang-up while calling, settings, expand, mute and hang-up once live.
    private enum Controls { case none, hangUpOnly, full }

    /// `title` carries its own accessibility id: "live-call-title" (and
    /// "live-call-clock" while live).
    private func row(title: some View, captions: Bool, controls: Controls) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                titleArea(title)
                if controls != .none { controlButtons(controls) }
            }
            if captions {
                LiveCallCaptionLine(feed: liveCall.feed)
                    .padding(.bottom, 8)
            }
        }
        .sheet(isPresented: $showingSettings) { LiveCallSettingsSheet() }
        .modifier(BarChrome())
    }

    private func titleArea(_ title: some View) -> some View {
        HStack(spacing: 8) {
            StatusDot(color: .seiraLiveDot)
            title
                .font(.seira(14, .semibold, relativeTo: .subheadline))
                .foregroundStyle(Color.seiraInk)
                .lineLimit(1)
            Spacer(minLength: 4)
        }
        .contentShape(Rectangle())
        .onTapGesture { liveCall.presentScreen() }
    }

    private func controlButtons(_ controls: Controls) -> some View {
        HStack(spacing: 0) {
            if controls == .full { settingsButton }
            expandButton
            if controls == .full { muteButton }
            hangUpButton
        }
    }

    private var settingsButton: some View {
        Button { showingSettings = true } label: {
            BarIcon(symbol: "gearshape.fill", ink: .seiraInk)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 36, fill: .seiraControl))
        .accessibilityLabel("Live call settings")
        .accessibilityIdentifier("live-call-settings")
    }

    private var expandButton: some View {
        Button { liveCall.presentScreen() } label: {
            BarIcon(symbol: "arrow.up.left.and.arrow.down.right", ink: .seiraInk)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 36, fill: .seiraControl))
        .accessibilityLabel("Open call screen")
        .accessibilityIdentifier("live-call-expand")
    }

    private var muteButton: some View {
        let muted = liveCall.isMuted
        let fill: Color = muted ? .seiraInverse : .seiraControl
        let ink: Color = muted ? .seiraOnInverse : .seiraInk
        return Button { liveCall.toggleMute() } label: {
            BarIcon(symbol: muted ? "mic.slash.fill" : "mic.fill", ink: ink)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 36, fill: fill))
        .feedback(.selection, trigger: muted)
        .accessibilityLabel(muted ? "Unmute" : "Mute")
        .accessibilityIdentifier("live-call-mute")
    }

    private var hangUpButton: some View {
        Button {
            hangUpTaps += 1
            liveCall.hangUp()
        } label: {
            BarIcon(symbol: "phone.down.fill", ink: .white)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 36, fill: .seiraDangerFill))
        .feedback(.warning, trigger: hangUpTaps)
        .accessibilityLabel("Hang up")
        .accessibilityIdentifier("live-call-hangup")
    }

    private func stoppedRow(_ notice: LiveCallNotice) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "phone.down")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(Color.seiraInk3)
            notice.text
                .font(.seira(13, .regular, relativeTo: .footnote))
                .foregroundStyle(Color.seiraInk2)
                .lineLimit(2)
                .accessibilityIdentifier("live-call-title")
            Spacer(minLength: 4)
            if notice.canRetry {
                Button { liveCall.retry() } label: {
                    Text("Try again").font(.seira(13, .semibold, relativeTo: .footnote))
                }
                .buttonStyle(SeiraControlButtonStyle())
                .accessibilityIdentifier("live-call-retry")
            }
            Button { liveCall.dismiss() } label: {
                BarIcon(symbol: "xmark", ink: .seiraInk)
            }
            .buttonStyle(SeiraCircleButtonStyle(size: 36, fill: .seiraControl))
            .accessibilityLabel("Dismiss")
            .accessibilityIdentifier("live-call-dismiss")
        }
        .modifier(BarChrome())
    }
}

/// "Live with Ada · m:ss", or "Connecting…" until the computer says live
/// and the channel is open; the clock counts from that moment. It follows
/// the feed, so the clock redraws this line only.
private struct LiveCallTitle: View {
    let botName: String
    let connecting: Bool
    @ObservedObject var feed: LiveCallFeed

    var body: some View {
        if connecting {
            Text("Connecting…")
                .accessibilityIdentifier("live-call-title")
        } else {
            LiveCallNameAndClock(botName: botName, clock: LiveCallClock.text(feed.elapsedSeconds), id: "live-call")
        }
    }
}

/// "Live with Ada · m:ss" on one line, on this phone's bar and on the remote
/// bar. Only the name gives way: the clock takes its whole width first and
/// the name gets what is left, cut short with "…" when it needs more. The
/// clock's digits are all one width, so a long name is not cut a letter
/// shorter or longer every second. `id` names the two parts, "<id>-title"
/// and "<id>-clock"; VoiceOver reads the whole name and then the time.
private struct LiveCallNameAndClock: View {
    let botName: String
    let clock: String
    let id: String

    var body: some View {
        HStack(spacing: 0) {
            Text("Live with \(botName)")
                .accessibilityIdentifier("\(id)-title")
            Text(verbatim: " · \(clock)")
                .monospacedDigit()
                .fixedSize()
                .layoutPriority(1)
                .accessibilityLabel(Text(verbatim: clock))
                .accessibilityIdentifier("\(id)-clock")
        }
    }
}

/// The last words said, the person's in a quieter ink. It follows the feed,
/// so each word redraws this line only.
private struct LiveCallCaptionLine: View {
    @ObservedObject var feed: LiveCallFeed

    var body: some View {
        if !feed.captions.line.isEmpty {
            Text(verbatim: feed.captions.line)
                .font(.seira(13, .regular, relativeTo: .footnote))
                .foregroundStyle(feed.captions.speaker == .user ? Color.seiraInk3 : Color.seiraInk2)
                .lineLimit(1)
                .accessibilityIdentifier("live-call-caption")
        }
    }
}

/// A call on this chat that another device holds. The clock counts from the
/// Mac's `startedAt`; hanging up asks the Mac, which tells everyone. The bar
/// goes on the Mac's answer; if the Mac (or the sidecar) refuses, its words
/// show in the app's alert and the bar stays, so the tap can be tried again.
///
/// Where the call is ("From your computer") has a line of its own, where this
/// phone's own call shows its captions: on the first line it pushed the clock
/// off a phone's width.
struct RemoteLiveCallBar: View {
    let call: LiveCallState
    let botName: String
    @EnvironmentObject private var session: Session
    /// One hang-up at a time: a second tap while the first is on its way
    /// would only earn a 404 for a call the first one ended.
    @State private var hangingUp = false
    @State private var hangUpTaps = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                StatusDot(color: .seiraLiveDot)
                // The clock runs on the timeline, not on a timer held by this
                // view: the chat re-renders the bar as frames land, and a timer
                // re-created with each render may never get to fire.
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    LiveCallNameAndClock(
                        botName: botName,
                        clock: LiveCallClock.text(LiveCallClock.elapsed(since: call.startedAt, now: context.date)),
                        id: "live-call-remote"
                    )
                    .font(.seira(14, .semibold, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraInk)
                    .lineLimit(1)
                }
                Spacer(minLength: 4)
                hangUpButton
            }
            LiveCallNotice.fromDevice(call.client)
                .font(.seira(13, .regular, relativeTo: .footnote))
                .foregroundStyle(Color.seiraInk3)
                .lineLimit(1)
                .padding(.bottom, 8)
                .accessibilityIdentifier("live-call-remote-device")
        }
        .modifier(BarChrome())
        .accessibilityIdentifier("live-call-remote-bar")
    }

    private var hangUpButton: some View {
        Button {
            hangingUp = true
            hangUpTaps += 1
            Task {
                await session.hangUpRemoteLiveCall(callId: call.callId)
                hangingUp = false
            }
        } label: {
            BarIcon(symbol: "phone.down.fill", ink: .white)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 36, fill: .seiraDangerFill))
        .disabled(hangingUp)
        .feedback(.warning, trigger: hangUpTaps)
        .accessibilityLabel("Hang up")
        .accessibilityIdentifier("live-call-remote-hangup")
    }
}

/// On screens other than the call's chat: one line, tap to go back.
struct LiveCallBanner: View {
    let open: (LiveCallMachine.Target) -> Void
    @EnvironmentObject private var liveCall: LiveCallController

    var body: some View {
        if liveCall.machine.isActive, let target = liveCall.machine.target {
            Button { open(target) } label: {
                LiveCallBannerLabel(botName: target.botName, feed: liveCall.feed)
            }
            .buttonStyle(.plain)
            .padding(.bottom, 8)
            .accessibilityIdentifier("live-call-banner")
        }
    }
}

/// The banner's words and clock. It follows the feed, so the clock redraws
/// this label only, not the roster or the chat around it.
private struct LiveCallBannerLabel: View {
    let botName: String
    @ObservedObject var feed: LiveCallFeed

    var body: some View {
        HStack(spacing: 8) {
            StatusDot(color: .seiraLiveDot)
            Text("On a call with \(botName) · \(LiveCallClock.text(feed.elapsedSeconds))")
                .font(.seira(13, .semibold, relativeTo: .footnote))
                .foregroundStyle(Color.seiraInk)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Color.seiraTint, in: Capsule())
    }
}

/// The card the bars sit on, the same width as the composer.
private struct BarChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(.leading, 14)
            .padding(.trailing, 6)
            .padding(.vertical, 6)
            .seiraCard(radius: 22, fill: .seiraTint)
            .padding(.horizontal, 12)
            .padding(.top, 6)
            .frame(maxWidth: CompanionLayout.chatWidth)
            .frame(maxWidth: .infinity)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("live-call-bar")
    }
}

/// The glyph inside a round bar button.
private struct BarIcon: View {
    let symbol: String
    let ink: Color

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(ink)
    }
}

extension LiveCallNotice {
    /// The words for each notice. Here, not in CompanionCore, so the string
    /// catalog can translate them; the classification is tested there.
    var text: Text {
        switch self {
        case .micDenied:
            return Text("Live calls need Microphone access. Enable it in Settings → Seira.")
        case .needsKey:
            return Text("Set up Live calls on your computer first.")
        case let .busy(client, botName):
            return Text("\(Self.deviceName(client)) is on a call with \(botName.map { Text(verbatim: $0) } ?? Text("a bot")).")
        case let .unreachable(detail):
            return Text("Can't reach your computer. \(detail)")
        case let .refused(message):
            return Text(verbatim: message)
        case let .couldNotStart(detail):
            return Text("The call could not start: \(detail)")
        case .dropped:
            return Text("Call dropped.")
        case .audioNeverConnected:
            return Text("Call dropped: the audio could not connect.")
        // The end reasons in the desktop's words (call.live.* in
        // src/locales/en.json), so a call reads the same on every client.
        case .ended:
            return Text("Call ended.")
        case .endedIdle:
            return Text("Call ended after a long silence.")
        case .endedExpired:
            return Text("Call ended: it reached OpenAI's time limit.")
        case .endedContent:
            return Text("OpenAI ended the call under its content rules.")
        case .endedDeleted:
            return Text("Call ended: the chat was deleted.")
        case .endedShutdown:
            return Text("Call ended: Seira restarted.")
        // The computer's own words when it ended the call; this phone's
        // when the computer stopped taking its requests: the words the
        // harness uses for a phone it unpaired (LIVE_COPY.unpaired).
        case let .signedOut(message?):
            return Text(verbatim: message)
        case .signedOut(nil):
            return Text("Call ended: you were signed out.")
        case let .endedWithError(message, _):
            return Text(verbatim: message)
        }
    }

    /// "Your computer", for the start of a sentence. Not "your Mac": the
    /// harness runs on Linux too. A `Text`, not a `String(localized:)`, so it
    /// follows the in-app language like the sentence it sits in; a String
    /// would follow the phone's and mix two languages in one line.
    static func deviceName(_ client: String) -> Text {
        switch client {
        case "desktop": return Text("Your computer")
        case "ios": return Text("An iPhone")
        case "android": return Text("An Android phone")
        default: return Text("Another device")
        }
    }

    /// Where a call another device holds is: the remote bar's second line.
    /// The desktop's remote bar says it the same way ("Pepper is on a Live
    /// call from an iPhone"), and so does Android's.
    static func fromDevice(_ client: String) -> Text {
        switch client {
        case "desktop": return Text("From your computer")
        case "ios": return Text("From an iPhone")
        case "android": return Text("From an Android phone")
        default: return Text("From another device")
        }
    }
}
