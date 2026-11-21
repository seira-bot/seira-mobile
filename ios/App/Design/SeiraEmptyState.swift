import SwiftUI

struct SeiraEmptyState<Actions: View>: View {
    let icon: String
    let title: LocalizedStringKey
    var message: LocalizedStringKey?
    var hasActions = true
    @ViewBuilder var actions: () -> Actions

    var body: some View {
        VStack(spacing: 0) {
            Image(icon)
                .resizable()
                .scaledToFit()
                .frame(width: 120, height: 120)
                .accessibilityHidden(true)
            Text(title)
                .font(.seira(22, .semibold, relativeTo: .title2))
                .foregroundStyle(Color.seiraInk)
                .multilineTextAlignment(.center)
                .padding(.top, 20)
            messageText
            if hasActions {
                VStack(spacing: 12) { actions() }
                    .padding(.top, 24)
            }
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder private var messageText: some View {
        if let message {
            Text(message)
                .font(.seira(15, .regular, relativeTo: .body))
                .foregroundStyle(Color.seiraInk2)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 290)
                .padding(.top, 8)
        }
    }
}

extension SeiraEmptyState where Actions == EmptyView {
    init(icon: String, title: LocalizedStringKey, message: LocalizedStringKey? = nil) {
        self.init(icon: icon, title: title, message: message, hasActions: false) { EmptyView() }
    }
}
