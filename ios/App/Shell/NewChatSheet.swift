import CompanionCore
import SwiftUI

struct NewChatSheet: View {
    let chief: Bot?
    let openBot: (Bot) -> Void
    let newChatWithChief: () -> Void
    let openGroup: (Room) -> Void
    let newSection: () -> Void
    let walkie: () -> Void
    let walkieDisabled: Bool

    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var showingNewGroup = false
    @State private var showingNewBot = false

    private var bots: [Bot] { session.state.bots.filter { $0.hidden != true } }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if chief != nil {
                        row("New chat with Seira", symbol: "square.and.pencil") {
                            dismiss()
                            newChatWithChief()
                        }
                    }
                    row("New group", symbol: "person.2") { showingNewGroup = true }
                    if session.canAdminister {
                        row("New bot", symbol: "plus.circle") { showingNewBot = true }
                        if !bots.isEmpty {
                            row("New section", symbol: "folder.badge.plus") {
                                dismiss()
                                newSection()
                            }
                        }
                    }
                    if !walkieDisabled {
                        row("Walkie", symbol: "waveform") {
                            dismiss()
                            walkie()
                        }
                    }
                }
                if !bots.isEmpty {
                    Section("Chat with a bot") {
                        ForEach(bots) { bot in
                            Button {
                                dismiss()
                                openBot(bot)
                            } label: {
                                HStack(spacing: 12) {
                                    SeiraAvatar(bot: bot, size: 36)
                                    Text(verbatim: bot.name).foregroundStyle(Color.seiraInk)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("New chat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .sheet(isPresented: $showingNewGroup) {
                NewGroupSheet { room in
                    showingNewGroup = false
                    dismiss()
                    openGroup(room)
                }
            }
            .sheet(isPresented: $showingNewBot) {
                NewBotForm { bot in
                    showingNewBot = false
                    dismiss()
                    openBot(bot)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func row(_ title: LocalizedStringKey, symbol: String, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.selection()
            action()
        } label: {
            Label(title, systemImage: symbol).foregroundStyle(Color.seiraInk)
        }
    }
}

/// Asks who the bot is. Nothing is created until the person taps Create bot.
struct NewBotForm: View {
    let created: (Bot) -> Void

    @EnvironmentObject private var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var draft = NewBotDraft()
    @State private var creating = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $draft.name)
                        .autocorrectionDisabled()
                    TextField("What does it do?", text: $draft.job)
                } footer: {
                    Text("You can change the name and job later in the bot's profile.")
                }
            }
            .navigationTitle("New bot")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create bot") { create() }
                        .disabled(!draft.canCreate || creating)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func create() {
        guard draft.canCreate, !creating else { return }
        creating = true
        let spec = draft.spec
        Task {
            let bot = await session.createBot(spec)
            creating = false
            if let bot {
                Haptics.success()
                created(bot)
            }
        }
    }
}
