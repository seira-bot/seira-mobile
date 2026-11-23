// The Glance Live Activity: the one bot that needs you most, on the lock
// screen and in the Dynamic Island. The head is the bot's own mascot; the
// colour of the state is the only count of attention, never a number.
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
                    GlanceHead(content: context.state, size: 48)
                        .padding(.leading, 2)
                }
                DynamicIslandExpandedRegion(.center) {
                    GlanceHeading(content: context.state)
                        .padding(.top, 2)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    GlanceBody(content: context.state)
                        .padding(.top, 4)
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

/// The name, then what the bot is doing: its own last line while it works, otherwise the state.
private struct GlanceHeading: View {
    let content: GlanceContent

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(content.name)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
            if content.showsLine {
                Text(content.line)
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.7))
                    .lineLimit(2)
            } else {
                Text(content.stateWord)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(content.accent)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The approval's title and its whole text, then the buttons.
private struct GlanceBody: View {
    let content: GlanceContent

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if content.showsApproval && !content.title.isEmpty {
                Text(content.title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.white.opacity(0.85))
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if content.showsDetail {
                Text(content.detail)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(.white)
                    .lineLimit(nil)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(8)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.12)))
            }
            if content.couldNotSend {
                Text("Could not send. Open the chat to answer.")
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            GlanceButtons(content: content)
        }
    }
}

private struct GlanceLockScreen: View {
    let content: GlanceContent

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            GlanceHead(content: content, size: 52)
            VStack(alignment: .leading, spacing: 8) {
                GlanceHeading(content: content)
                GlanceBody(content: content)
            }
        }
        .padding(14)
    }
}

/// Deny and Allow once answer in the app's process, and need the interactive widgets of iOS 17.
/// Below that, and for every ask the chat has to answer, only Open chat shows.
private struct GlanceButtons: View {
    let content: GlanceContent

    var body: some View {
        if #available(iOS 17.0, *) {
            GlanceInteractiveButtons(content: content)
        } else {
            GlanceOpenChat(content: content)
        }
    }
}

@available(iOS 17.0, *)
private struct GlanceInteractiveButtons: View {
    let content: GlanceContent

    var body: some View {
        HStack(spacing: 8) {
            if content.showsDeny, let requestId = content.requestId {
                Button(intent: GlanceDenyIntent(botId: content.botId, threadId: content.threadId, requestId: requestId)) {
                    GlancePill(title: "Deny", fill: Color.white.opacity(0.16))
                }
                .buttonStyle(.plain)
            }
            if content.showsAllow, let requestId = content.requestId {
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
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .frame(maxWidth: .infinity)
            .frame(height: 34)
            .background(Capsule().fill(fill))
    }
}
