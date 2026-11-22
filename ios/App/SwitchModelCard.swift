import CompanionCore
import SwiftUI

/// Offered under the newest failed or stopped turn of a bot chat. "Try again"
/// sends the person's last message again. "Switch model" shows only where a
/// model change can fix the failure and opens the bot's settings at the model.
/// Once the model has changed, the card says which one is used now and makes
/// "Try again" the main action.
struct SwitchModelCard: View {
    let selection: ModelSelection?
    let offersSwitch: Bool
    let retry: (() -> Void)?
    let tint: Color
    let actions: TranscriptActions
    @State private var failedOn: ModelSelection?
    @State private var appliedLabel: String?

    init(
        selection: ModelSelection?, offersSwitch: Bool, retry: (() -> Void)?,
        tint: Color, actions: TranscriptActions
    ) {
        self.selection = selection
        self.offersSwitch = offersSwitch
        self.retry = retry
        self.tint = tint
        self.actions = actions
        _failedOn = State(initialValue: selection)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let appliedLabel {
                Label("Now using \(appliedLabel).", systemImage: "checkmark.circle")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.green)
                    .fixedSize(horizontal: false, vertical: true)
                tryAgainButton(primary: true)
            } else {
                if offersSwitch {
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
                tryAgainButton(primary: false)
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
            guard let selection, selection != failedOn else { return }
            appliedLabel = await actions.modelLabel(for: selection)
        }
    }

    @ViewBuilder
    private func tryAgainButton(primary: Bool) -> some View {
        if let retry {
            Button {
                Haptics.selection()
                retry()
            } label: {
                Text("Try again")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(primary ? Color.white : tint)
                    .frame(maxWidth: .infinity)
                    .frame(height: 40)
                    .background(
                        Capsule().fill(primary ? tint : Color.clear)
                    )
                    .overlay(Capsule().stroke(tint, lineWidth: primary ? 0 : 1))
            }
            .buttonStyle(.plain)
        }
    }
}
