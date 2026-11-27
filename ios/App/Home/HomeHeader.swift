import SwiftUI

struct HomeChatsHeader: View {
    let newChat: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text("Chats")
                .font(.seira(28, .semibold, relativeTo: .largeTitle))
                .tracking(-0.56)
                .foregroundStyle(Color.seiraInk)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)

            button("square.and.pencil", label: "New chat", fill: .seiraAcc, ink: .seiraOnAcc, action: newChat)
                .accessibilityIdentifier("home-new-chat")
        }
        .padding(.horizontal, HomeRowMetrics.side)
        .padding(.top, 4)
        .frame(minHeight: 48)
    }

    private func button(
        _ symbol: String, label: LocalizedStringKey, fill: Color, ink: Color, action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(ink)
        }
        .buttonStyle(SeiraCircleButtonStyle(size: 48, fill: fill))
        .accessibilityLabel(label)
    }
}
