import CompanionCore
import SwiftUI

/// Offered under the latest failed turn of a bot chat. It opens the bot's
/// settings at the model; once the model has changed it says to send the
/// message again, because the change does not resend the one that failed.
struct SwitchModelCard: View {
    let selection: ModelSelection
    let tint: Color
    let actions: TranscriptActions
    @State private var failedOn: ModelSelection
    @State private var appliedLabel: String?

    init(selection: ModelSelection, tint: Color, actions: TranscriptActions) {
        self.selection = selection
        self.tint = tint
        self.actions = actions
        _failedOn = State(initialValue: selection)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let appliedLabel {
                Label("Now on \(appliedLabel). Send your message again to continue.", systemImage: "checkmark.circle")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.green)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Text("Try this message on another AI model.")
                    .font(.system(size: 15))
                    .foregroundStyle(Color.primary)
                    .fixedSize(horizontal: false, vertical: true)
                Button {
                    Haptics.selection()
                    actions.openBotSettings()
                } label: {
                    Text("Switch model")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .background(Capsule().fill(tint))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color.secondary.opacity(0.13))
        )
        .accessibilityElement(children: .contain)
        .task(id: selection) {
            guard selection != failedOn else { return }
            appliedLabel = await actions.modelLabel(for: selection)
        }
    }
}
