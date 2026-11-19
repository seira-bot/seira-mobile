import CompanionCore
import SwiftUI

/// The floating chat header: back, the bot pill, and the round actions.
struct ChatHeaderBar: View {
    let chat: Chat
    let faceBot: Bot?
    let unread: Int
    let hidesFace: Bool
    let showsCall: Bool
    let onBack: () -> Void
    let onProfile: () -> Void
    let onCall: () -> Void
    let onThreads: () -> Void
    let onComputer: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            backButton
            pill
            actions
            if showsCall { callButton }
        }
        .padding(.horizontal, 12)
        .padding(.top, 4)
        .padding(.bottom, 8)
        .frame(maxWidth: CompanionLayout.headerWidth)
        .frame(maxWidth: .infinity)
    }

    private var backButton: some View {
        Button(action: onBack) {
            HStack(spacing: 4) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                if unread > 0 { unreadBadge }
            }
            .foregroundStyle(Color.seiraInk)
            .padding(.leading, 12)
            .padding(.trailing, unread > 0 ? 8 : 12)
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .glassCapsule()
        .accessibilityLabel("Back")
    }

    private var unreadBadge: some View {
        Text("\(unread)")
            .font(.seira(13, .semibold, relativeTo: .subheadline))
            .padding(.horizontal, 7)
            .frame(minWidth: 22, minHeight: 22)
            .background(Capsule().fill(Color.seiraControl))
    }

    @ViewBuilder private var pill: some View {
        if case .bot = chat {
            Button(action: onProfile) { pillBody }
                .buttonStyle(.plain)
                .accessibilityLabel("Open \(chat.name) settings")
                .accessibilityHint("Changes this bot's model, profile, notifications, and voice")
        } else {
            pillBody
                .accessibilityElement()
                .accessibilityLabel(roomLabel)
                .accessibilityIdentifier("header-room")
        }
    }

    private var roomLabel: String {
        if case let .room(room) = chat { return "\(room.name), \(room.memberIds.count) bots" }
        return chat.name
    }

    private var pillBody: some View {
        HStack(spacing: 8) {
            face
            VStack(alignment: .leading, spacing: 1) {
                Text(chat.name)
                    .font(.seira(15, .semibold))
                    .foregroundStyle(Color.seiraInk)
                    .lineLimit(1)
                if chat.busy { workingLine }
            }
            Spacer(minLength: 0)
        }
        .padding(.leading, 6)
        .padding(.trailing, 14)
        .frame(maxWidth: .infinity, minHeight: 44, maxHeight: 44, alignment: .leading)
        .contentShape(Capsule())
        .glassCapsule()
    }

    private var workingLine: some View {
        HStack(spacing: 5) {
            StatusDot(color: .seiraSuccess, size: 6)
            Text("Working")
                .font(.seira(11, .regular, relativeTo: .caption2))
                .foregroundStyle(Color.seiraInk2)
        }
    }

    @ViewBuilder private var face: some View {
        Group {
            if let faceBot {
                SeiraAvatar(bot: faceBot, size: 32)
            } else {
                SeiraInitials(text: String(chat.name.prefix(1)).uppercased(), size: 32, style: .tint)
            }
        }
        .opacity(hidesFace ? 0 : 1)
        .accessibilityHidden(true)
    }

    private var actions: some View {
        HStack(spacing: 8) {
            if chat.supportsTasks { threadsButton }
            if case .bot = chat { computerButton }
        }
    }

    private var threadsButton: some View {
        roundButton("square.stack", action: onThreads)
            .accessibilityLabel("Threads")
            .accessibilityValue(Text(verbatim: chat.threadTitle))
            .accessibilityIdentifier("header-threads")
    }

    private var computerButton: some View {
        roundButton("display", action: onComputer)
            .accessibilityLabel("Watch \(chat.name)'s computer")
    }

    private var callButton: some View {
        roundButton("phone", action: onCall)
            .accessibilityLabel("Start a Live call with \(chat.name)")
            .accessibilityIdentifier("live-call-start")
    }

    private func roundButton(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Color.seiraInk)
                .frame(width: 44, height: 44)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassCapsule()
    }
}
