// The Glance Live Activity: the one bot that needs you most, on the lock
// screen and in the Dynamic Island. The head is the bot's own mascot; the
// colour of the state is the only count of attention, never a number.
#if !targetEnvironment(macCatalyst)
import ActivityKit
import AppIntents
import CompanionCore
import SwiftUI
import WidgetKit

struct GlanceActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: GlanceActivityAttributes.self) { context in
            GlanceLockScreen(content: context.state)
                .activityBackgroundTint(.black)
                .activitySystemActionForegroundColor(.white)
                .widgetURL(GlanceLink.chat(threadId: context.state.threadId))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    GlanceHead(content: context.state, size: 40)
                        .padding(.leading, 2)
                }
                DynamicIslandExpandedRegion(.center) {
                    GlanceHeading(content: context.state)
                        .padding(.top, 2)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    GlanceFittedBody(content: context.state)
                        .padding(.top, 2)
                }
            } compactLeading: {
                GlanceHead(content: context.state, size: 24)
            } compactTrailing: {
                Circle().fill(context.state.accent).frame(width: 8, height: 8)
            } minimal: {
                GlanceMinimal(content: context.state)
            }
            .keylineTint(context.state.accent)
            .widgetURL(GlanceLink.chat(threadId: context.state.threadId))
        }
    }
}

enum GlanceLink {
    static func chat(threadId: String) -> URL? { WidgetChatLink.url(threadId: threadId) }
}

private extension GlanceContent {
    var accent: Color {
        switch state {
        case .working: return Color(hex: "#4f8dff")
        case .needs, .question: return Color(hex: "#f5a524")
        case .failed: return Color(hex: "#ff5c6c")
        case .done: return Color(hex: "#3ccf8e")
        }
    }

    var stateWord: LocalizedStringKey {
        switch state {
        case .working: return "Working"
        case .needs: return "Needs you"
        case .question: return "Has a question"
        case .failed: return "Failed"
        case .done: return "Finished"
        }
    }

    var showsLine: Bool { state == .working && !line.isEmpty }
    var showsDetail: Bool { showsAllow && !detail.isEmpty }
    var showsApproval: Bool { askKind == .approval }
}

private struct GlanceHead: View {
    let content: GlanceContent
    let size: CGFloat

    var body: some View {
        SeiraPlateHead(look: content.look, mood: content.mood, size: size)
    }
}

private struct GlanceMinimal: View {
    let content: GlanceContent

    var body: some View {
        ZStack {
            Circle().strokeBorder(content.accent, lineWidth: 2)
            SeiraPlateHead(look: content.look, mood: content.mood, size: 18)
        }
    }
}

/// The name, then what the bot is doing: its own last line while it works, otherwise the state
/// on the same row, so an approval keeps its height for the text it covers.
private struct GlanceHeading: View {
    let content: GlanceContent

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(content.name)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                if !content.showsLine {
                    Text(content.stateWord)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(content.accent)
                        .lineLimit(1)
                        .layoutPriority(1)
                }
            }
            if content.showsLine {
                Text(content.line)
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.7))
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The approval's title and its whole text, then the buttons. With `full` false the text is
/// left out and so is Allow once: a person never allows text that the screen may cut.
private struct GlanceBody: View {
    let content: GlanceContent
    var full = true

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            if content.showsApproval && !content.title.isEmpty {
                Text(content.title)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if full && content.showsDetail {
                Text(content.detail)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundStyle(.white)
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 7).fill(Color.white.opacity(0.12)))
            }
            if content.couldNotSend {
                Text("Could not send. Open the chat to answer.")
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            GlanceButtons(content: content, allowOnce: full)
        }
    }
}

/// Security: Allow once shows only when the whole text fits the height the system gives. If it
/// does not, the second layout shows the title and Deny / Open chat.
private struct GlanceFittedBody: View {
    let content: GlanceContent
    var heading = false

    var body: some View {
        if content.showsAllow {
            ViewThatFits(in: .vertical) {
                column(full: true)
                column(full: false)
            }
        } else {
            column(full: true)
        }
    }

    private func column(full: Bool) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            if heading { GlanceHeading(content: content) }
            GlanceBody(content: content, full: full)
        }
    }
}

private struct GlanceLockScreen: View {
    let content: GlanceContent

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            GlanceHead(content: content, size: 44)
            GlanceFittedBody(content: content, heading: true)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }
}

/// Deny and Allow once answer in the app's process, and need the interactive widgets of iOS 17.
/// Below that, and for every ask the chat has to answer, only Open chat shows.
private struct GlanceButtons: View {
    let content: GlanceContent
    let allowOnce: Bool

    var body: some View {
        if #available(iOS 17.0, *) {
            GlanceInteractiveButtons(content: content, allowOnce: allowOnce)
        } else {
            GlanceOpenChat(content: content)
        }
    }
}

@available(iOS 17.0, *)
private struct GlanceInteractiveButtons: View {
    let content: GlanceContent
    let allowOnce: Bool

    var body: some View {
        HStack(spacing: 8) {
            if content.showsDeny, let requestId = content.requestId {
                Button(intent: GlanceDenyIntent(botId: content.botId, threadId: content.threadId, requestId: requestId)) {
                    GlancePill(title: "Deny", fill: Color.white.opacity(0.16))
                }
                .buttonStyle(.plain)
            }
            if allowOnce && content.showsAllow, let requestId = content.requestId {
                Button(intent: GlanceAllowOnceIntent(botId: content.botId, threadId: content.threadId, requestId: requestId)) {
                    GlancePill(title: "Allow once", fill: content.accent)
                }
                .buttonStyle(.plain)
            }
            GlanceOpenChat(content: content)
        }
    }
}

private struct GlanceOpenChat: View {
    let content: GlanceContent

    var body: some View {
        if let url = GlanceLink.chat(threadId: content.threadId) {
            Link(destination: url) {
                GlancePill(title: "Open chat", fill: Color.white.opacity(0.16))
            }
        }
    }
}

private struct GlancePill: View {
    let title: LocalizedStringKey
    let fill: Color

    var body: some View {
        Text(title)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .frame(maxWidth: .infinity)
            .frame(height: 30)
            .background(Capsule().fill(fill))
    }
}
#endif
