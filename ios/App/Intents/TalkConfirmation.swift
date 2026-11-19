import SwiftUI
import CompanionCore

/// A call that a link asks for starts only when the person taps Start call.
/// The first call on this phone also shows the Live disclosure.
struct TalkConfirmation: ViewModifier {
    @Binding var bot: Bot?
    let start: (Bot) -> Void

    func body(content: Content) -> some View {
        content.alert(
            Text("Start a Live call with \(bot?.name ?? "")?"),
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
        } message: { _ in
            message
        }
    }

    @ViewBuilder
    private var message: some View {
        if LiveCallDisclosure().isDue {
            Text("A Live call sends your voice to OpenAI, along with the chat's recent messages, the bot's answers and the details of any approval it asks for. The OpenAI key stays on your computer.")
        }
    }
}
