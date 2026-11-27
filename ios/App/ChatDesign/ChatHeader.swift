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
    let onComputer: () -> Void
    let menuItems: [ChatMenuItem]
    let onMenu: (ChatMenuItem) -> Void

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
            .frame(minWidth: 46, minHeight: 46)
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
        if case let .room(room) = chat { return String(localized: "\(room.name), \(room.memberIds.count) bots") }
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
        .frame(maxWidth: .infinity, minHeight: 46, maxHeight: 46, alignment: .leading)
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
            if ChatMenuRules.showsScreenButton(chat) { computerButton }
            menuButton
        }
    }

    private var menuButton: some View {
        ChatMenuButton(
            conversation: chat.conversationID, threadTitle: chat.threadTitle,
            items: menuItems, onMenu: onMenu
        )
        .equatable()
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
                .frame(width: 46, height: 46)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassCapsule()
    }
}

/// On iOS 26.5 every rebuild of an open `Menu` animates into the open menu,
/// and a streaming fleet redraws the chat many times a second. The menu
/// redraws only when what it lists changes.
private struct ChatMenuButton: View, Equatable {
    let conversation: String
    let threadTitle: String
    let items: [ChatMenuItem]
    let onMenu: (ChatMenuItem) -> Void

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.conversation == rhs.conversation && lhs.threadTitle == rhs.threadTitle && lhs.items == rhs.items
    }

    var body: some View {
        Menu {
            ForEach(items, id: \.self) { item in
                Button { onMenu(item) } label: { Label(title(of: item), systemImage: symbol(of: item)) }
                    .accessibilityIdentifier("menu-\(item.rawValue)")
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 17, weight: .medium))
                .foregroundStyle(Color.seiraInk)
                .frame(width: 46, height: 46)
                .contentShape(Circle())
        }
        .glassCapsule()
        .accessibilityLabel("More")
        .accessibilityValue(Text(verbatim: threadTitle))
        .accessibilityIdentifier("chat-menu")
    }

    private func title(of item: ChatMenuItem) -> LocalizedStringKey {
        switch item {
        case .find: return "Find in conversation"
        case .newThread: return "New thread"
        case .allThreads: return "All threads"
        case .model: return "Model"
        case .members: return "Members"
        case .botScreen: return "Bot screen"
        case .share: return "Share"
        case .rename: return "Rename"
        case .copyMarkdown: return "Copy as Markdown"
        case .downloadMarkdown: return "Download .md"
        case .activity: return "Activity"
        }
    }

    private func symbol(of item: ChatMenuItem) -> String {
        switch item {
        case .find: return "magnifyingglass"
        case .newThread: return "plus.square.on.square"
        case .allThreads: return "square.stack"
        case .model: return "cpu"
        case .members: return "person.2"
        case .botScreen: return "display"
        case .share: return "square.and.arrow.up"
        case .rename: return "pencil"
        case .copyMarkdown: return "doc.on.doc"
        case .downloadMarkdown: return "arrow.down.doc"
        case .activity: return "clock"
        }
    }
}
