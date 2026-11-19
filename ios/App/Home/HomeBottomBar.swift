import SwiftUI

struct HomeBottomBar: View {
    let hasChief: Bool
    let canAdminister: Bool
    let hasVisibleBots: Bool
    let walkieDisabled: Bool
    let openSearch: () -> Void
    let newChat: () -> Void
    let newGroup: () -> Void
    let newSection: () -> Void
    let newBot: () -> Void
    let walkie: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            searchPill
            newChatMenu
        }
    }

    private var searchPill: some View {
        Button(action: openSearch) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 17, weight: .medium))
                Text("Search chats and bots")
                    .font(.seira(15, relativeTo: .body))
                    .lineLimit(1)
            }
            .foregroundStyle(Color.seiraInk3)
            .padding(.horizontal, 18)
            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .glassCapsule()
        .accessibilityLabel("Search")
    }

    private var newChatIcon: some View {
        Image(systemName: "square.and.pencil")
            .font(.system(size: 20, weight: .semibold))
            .foregroundStyle(Color.seiraOnPrimary)
    }

    private var newChatMenu: some View {
        Menu { actions } label: {
            newChatIcon
                .frame(width: 52, height: 52)
                .background(Color.seiraPrimary, in: Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New chat")
        .accessibilityIdentifier("home-new-chat")
    }

    @ViewBuilder private var actions: some View {
        if hasChief {
            Button(action: newChat) { Label("New chat", systemImage: "square.and.pencil") }
        }
        Button(action: newGroup) { Label("New group", systemImage: "person.2") }
        if canAdminister {
            Button(action: newSection) { Label("New section", systemImage: "folder.badge.plus") }
                .disabled(!hasVisibleBots)
            Button(action: newBot) { Label("New bot", systemImage: "square.and.pencil") }
        }
        Button(action: walkie) { Label("Walkie", systemImage: "waveform") }
            .disabled(walkieDisabled)
    }
}
