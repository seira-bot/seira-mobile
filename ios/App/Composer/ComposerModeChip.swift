import CompanionCore
import SwiftUI

extension ApprovalMode {
    var title: LocalizedStringKey {
        switch self {
        case .ask: return "Ask"
        case .edits: return "Edits"
        case .auto: return "Auto"
        case .full: return "Full"
        case .custom: return "Custom"
        }
    }

    var subtitle: LocalizedStringKey {
        switch self {
        case .ask: return "Asks before it edits or runs anything."
        case .edits: return "Edits files on its own. Asks before it runs commands."
        case .auto: return "Works on its own and asks only about risky steps."
        case .full: return "Never asks. Use it only for work you trust."
        case .custom: return "Set on your computer."
        }
    }

    var symbol: String {
        switch self {
        case .ask: return "hand.raised"
        case .edits: return "pencil"
        case .auto: return "wand.and.stars"
        case .full: return "bolt.fill"
        case .custom: return "slider.horizontal.3"
        }
    }
}

extension ModeRefusalReason {
    var message: LocalizedStringKey {
        switch self {
        case .fullNotAllowedHere: return "Full isn't available for this bot."
        case .leaveCustomOnDesktop: return "Change this mode on your computer."
        case .customOnDesktop: return "Custom can only be set on your computer."
        case .ownerOnly: return "Only the owner of this bot can change this."
        case .providerUnsupported: return "This bot's model doesn't support this mode."
        case .stillConfirming: return "The last change is still being confirmed. Try again in a moment."
        case .other: return "Couldn't change the mode. Try again."
        }
    }
}

/// The chip above the field that shows how much the bot may do before it asks.
struct ComposerModeChip: View {
    let bot: Bot
    let threadId: String
    @EnvironmentObject private var session: Session
    @State private var showingSheet = false
    /// The runtime this chip was tapped on. The sheet is shown for it and for no other.
    @State private var shownRuntime: RuntimeBinding?

    private var mode: ApprovalMode { session.approvalMode(bot: bot, threadId: threadId) ?? .ask }

    var body: some View {
        Button {
            Haptics.selection()
            shownRuntime = session.currentRuntimeBinding()
            showingSheet = true
        } label: {
            HStack(spacing: 6) {
                Image(systemName: mode.symbol)
                    .font(.system(size: 12, weight: .semibold))
                Text(mode.title)
                    .font(.seira(13, .semibold))
                Image(systemName: "chevron.up.chevron.down")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Color.seiraInk3)
            }
            .foregroundStyle(Color.seiraInk)
            .padding(.horizontal, 12)
            .frame(minHeight: 36)
            .background(Capsule().fill(Color.seiraControl))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Approvals")
        .accessibilityValue(Text(mode.title))
        .accessibilityIdentifier("composer-mode-chip")
        // A new runtime (another computer, a re-pair, a workspace switch) retires the sheet that was open for the old one.
        .onValueChange(of: session.runtimeGeneration) { _ in
            showingSheet = false
            shownRuntime = nil
        }
        .sheet(isPresented: $showingSheet) {
            if let runtime = shownRuntime {
                ApprovalModeSheet(bot: bot, threadId: threadId, runtime: runtime)
                    .environmentObject(session)
                    .presentationDetents([.medium])
            }
        }
    }
}

struct ApprovalModeSheet: View {
    let bot: Bot
    let threadId: String
    /// The runtime the person saw when this sheet opened. Every read and every change is bound to it.
    let runtime: RuntimeBinding
    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var working = false
    @State private var notice: ModeRefusalReason?
    @State private var confirming: ApprovalMode?

    private var mode: ApprovalMode? { session.approvalMode(bot: bot, threadId: threadId, for: runtime) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Approvals")
                .font(.seira(17, .semibold))
                .foregroundStyle(Color.seiraInk)
                .padding(.horizontal, 24)
                .padding(.top, 24)
                .padding(.bottom, 8)
            if ModeLadder.showsCustom(current: mode) {
                optionRow(.custom)
            }
            ApprovalLadder(shown: mode, working: working) { target in
                choose(target, acknowledged: false)
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)
            if let notice {
                Text(notice.message)
                    .font(.seira(13, .regular))
                    .foregroundStyle(Color.seiraInk2)
                    .padding(.horizontal, 24)
                    .padding(.top, 8)
                    .accessibilityIdentifier("approval-mode-notice")
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.seiraApp)
        .accessibilityIdentifier("approval-mode-sheet")
        .onValueChange(of: session.runtimeGeneration) { _ in retireIfReplaced() }
        .alert("Turn on Auto?", isPresented: Binding(get: { confirming != nil }, set: { if !$0 { confirming = nil } })) {
            Button("Cancel", role: .cancel) { confirming = nil }
            Button("Turn on Auto") {
                if let target = confirming { choose(target, acknowledged: true) }
                confirming = nil
            }
        } message: {
            Text("Auto lets this bot run steps on your computer without asking each time.")
        }
    }

    private func optionRow(_ option: ApprovalMode) -> some View {
        Button {
            choose(option, acknowledged: false)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: option.symbol)
                    .font(.system(size: 17))
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(option.title).font(.seira(16, .medium))
                    Text(option.subtitle)
                        .font(.seira(13, .regular))
                        .foregroundStyle(Color.seiraInk2)
                        .multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                if mode == option {
                    Image(systemName: "checkmark").font(.system(size: 15, weight: .semibold))
                }
            }
            .foregroundStyle(option.phoneCanSet ? Color.seiraInk : Color.seiraInk3)
            .padding(.horizontal, 24)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(working || !option.phoneCanSet)
        .accessibilityAddTraits(mode == option ? .isSelected : [])
        .accessibilityIdentifier("approval-mode.\(option.rawValue)")
    }

    /// Close the sheet, and drop the pending confirmation and notice, once the runtime it was shown for is gone.
    private func retireIfReplaced() {
        guard !session.isCurrent(runtime) else { return }
        confirming = nil
        notice = nil
        dismiss()
    }

    private func choose(_ target: ApprovalMode, acknowledged: Bool) {
        guard session.isCurrent(runtime) else {
            retireIfReplaced()
            return
        }
        guard target != mode, !working else { return }
        working = true
        notice = nil
        Task {
            let outcome = await ModeChange.request(current: mode, target: target, acknowledged: acknowledged) { mode, ack in
                await session.setApprovalMode(
                    bot: bot, threadId: threadId, mode: mode, acknowledgeLocalAuto: ack, expected: runtime
                )
            }
            working = false
            // The answer belongs to the runtime the sheet was shown for; once it is gone, nothing is shown or confirmed.
            guard session.isCurrent(runtime) else {
                retireIfReplaced()
                return
            }
            switch outcome {
            case .changed:
                dismiss()
            case let .needsConfirmation(mode):
                confirming = mode
            case let .refused(_, reason):
                notice = reason
            }
        }
    }
}
