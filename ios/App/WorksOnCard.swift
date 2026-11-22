import CompanionCore
import SwiftUI

enum WorksOnOutcome {
    case changed
    case refused(String?)
}

/// The newest cloud-computer notice of a chat and where its bot works now.
struct WorksOnState: Equatable {
    let noticeId: String
    let botId: String
    let computer: String?
    let cloudBackend: String?
    let vpsAlias: String?
}

/// Offered under the newest cloud-computer notice to a phone that may
/// administer: move the bot to no computer or to its own server.
struct WorksOnCard: View {
    let state: WorksOnState
    let busy: Bool
    let actions: TranscriptActions
    @State private var ownServerConfigured = false
    @State private var changing = false
    @State private var changedToOwnServer: Bool?
    @State private var refusal: String?
    @State private var failed = false

    private var offer: WorksOnOffer? {
        WorksOnChoice.offer(
            canAdminister: true, running: busy, computer: state.computer, cloudBackend: state.cloudBackend,
            ownServerReady: WorksOnChoice.ownServerReady(vpsAlias: state.vpsAlias, configured: ownServerConfigured)
        )
    }

    var body: some View {
        Group {
            if offer != nil || changedToOwnServer != nil {
                VStack(alignment: .leading, spacing: 8) {
                    if let changedToOwnServer {
                        if changedToOwnServer {
                            note("Now works on your own server.")
                        } else {
                            note("Now works without a computer.")
                        }
                    }
                    if let refusal {
                        Text(verbatim: refusal)
                            .font(.system(size: 14))
                            .foregroundStyle(Color.secondary)
                    } else if failed {
                        note("Couldn't change where this bot works. Try again.")
                    }
                    if let offer {
                        HStack(spacing: 8) {
                            if offer.withoutComputer { button("Work without a computer", ownServer: false) }
                            if offer.ownServer { button("Use your own server", ownServer: true) }
                        }
                    }
                }
                .padding(.top, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .contain)
            } else {
                Color.clear.frame(height: 0)
            }
        }
        .task(id: state.noticeId) {
            guard (state.vpsAlias ?? "").isEmpty else { return }
            ownServerConfigured = await actions.ownServerConfigured()
        }
    }

    private func note(_ text: LocalizedStringKey) -> some View {
        Text(text)
            .font(.system(size: 14))
            .foregroundStyle(Color.secondary)
    }

    private func button(_ title: LocalizedStringKey, ownServer: Bool) -> some View {
        Button {
            Haptics.selection()
            change(ownServer: ownServer)
        } label: {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .padding(.horizontal, 14)
                .frame(minHeight: 40)
                .background(Capsule().fill(Color.secondary.opacity(0.13)))
        }
        .buttonStyle(.plain)
        .disabled(changing)
    }

    private func change(ownServer: Bool) {
        changing = true
        refusal = nil
        failed = false
        Task {
            let outcome = await actions.setWorksOn(botId: state.botId, ownServer: ownServer)
            guard !Task.isCancelled else { return }
            switch outcome {
            case .changed:
                changedToOwnServer = ownServer
            case let .refused(sentence):
                refusal = sentence
                failed = sentence == nil
            }
            changing = false
        }
    }
}
