import CompanionCore
import SwiftUI
import UIKit

/// "Bot actions": small things to do with one bot, shown in Advanced mode only.
/// Each row changes the screen after the computer answers, never before.
struct BotActionsSection: View {
    let bot: Bot

    @AppStorage(PrefKey.advancedMode) private var advancedMode = false
    @EnvironmentObject private var session: Session
    @State private var busy = false
    @State private var answer: String?
    @State private var answerFailed = false
    @State private var moving = false
    @State private var clearing: Task<Void, Never>?

    var body: some View {
        Group {
            if advancedMode {
                Section {
                    ForEach(BotActionRules.visible(for: bot), id: \.self) { action in
                        Button { run(action) } label: {
                            Text(title(for: action))
                                .foregroundStyle(Color.seiraInk)
                                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                                .contentShape(Rectangle())
                        }
                        .disabled(busy)
                        .accessibilityIdentifier("bot-action-\(String(describing: action))")
                    }
                } header: {
                    Text("Bot actions")
                } footer: {
                    Text(verbatim: answer ?? "")
                        .foregroundStyle(answerFailed ? Color.seiraBad : Color.seiraInk2)
                        .frame(minHeight: 20, alignment: .leading)
                        .accessibilityIdentifier("bot-action-answer")
                }
            }
        }
        .sheet(isPresented: $moving) {
            DepartmentSheet(bot: bot, departments: BotDepartments.names(in: session.state.bots, declared: session.state.declaredSections)) { name in
                moving = false
                change("Moved") { await session.moveToDepartment(botId: bot.id, name: name) }
            }
        }
    }

    private func title(for action: BotAction) -> LocalizedStringKey {
        switch action {
        case .pin: return BotActionRules.isPinned(bot) ? "Unpin" : "Pin"
        case .markUnread: return "Mark as unread"
        case .markAllRead: return "Mark all conversations as read"
        case .move: return "Move to department"
        case .copyID: return "Copy conversation ID"
        }
    }

    private func run(_ action: BotAction) {
        switch action {
        case .pin:
            let pin = !BotActionRules.isPinned(bot)
            change(pin ? "Pinned" : "Unpinned") { await session.setBotFlags(botId: bot.id, pinned: pin) }
        case .markUnread:
            change("Marked as unread") { await session.setBotFlags(botId: bot.id, unread: true) }
        case .markAllRead:
            change("All read") { await session.markAllRead(botId: bot.id) }
        case .move:
            moving = true
        case .copyID:
            UIPasteboard.general.string = bot.threadId
            Haptics.selection()
            say("Copied", failed: false)
        }
    }

    private func change(_ done: String.LocalizationValue, _ call: @escaping () async -> Bool) {
        guard !busy else { return }
        busy = true
        Task {
            let ok = await call()
            busy = false
            if ok {
                Haptics.success()
                say(done, failed: false)
            } else {
                say("Could not save. Try again.", failed: true)
            }
        }
    }

    private func say(_ key: String.LocalizationValue, failed: Bool) {
        let text = String(localized: key)
        answer = text
        answerFailed = failed
        UIAccessibility.post(notification: .announcement, argument: text)
        clearing?.cancel()
        clearing = Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard !Task.isCancelled else { return }
            answer = nil
        }
    }
}

/// General, the departments the phone knows, and a way to start a new one.
private struct DepartmentSheet: View {
    let bot: Bot
    let departments: [String]
    let onPick: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var naming = false
    @State private var draft = ""

    private var current: String { BotDepartments.current(of: bot) }

    var body: some View {
        NavigationStack {
            List {
                row("", label: "General")
                ForEach(departments, id: \.self) { name in
                    row(name, label: LocalizedStringKey(name))
                }
                if naming {
                    VStack(alignment: .leading, spacing: 12) {
                        TextField("Department name", text: $draft)
                            .textInputAutocapitalization(.words)
                            .onValueChange(of: draft) { value in
                                if value.count > BotDepartments.maxNameLength {
                                    draft = String(value.prefix(BotDepartments.maxNameLength))
                                }
                            }
                            .accessibilityIdentifier("department-name")
                        Button("Move") {
                            if let name = BotDepartments.cleanName(draft) { onPick(name) }
                        }
                        .buttonStyle(SeiraPrimaryButtonStyle())
                        .disabled(BotDepartments.cleanName(draft) == nil)
                        .opacity(BotDepartments.cleanName(draft) == nil ? 0.4 : 1)
                        .accessibilityIdentifier("department-move")
                    }
                    .padding(.vertical, 4)
                } else {
                    Button { naming = true } label: {
                        Label("New department…", systemImage: "plus")
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle("Move to department")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func row(_ name: String, label: LocalizedStringKey) -> some View {
        let isCurrent = BotDepartments.same(current, name)
        return Button {
            Haptics.selection()
            onPick(name)
        } label: {
            HStack {
                Text(label).foregroundStyle(Color.seiraInk)
                Spacer(minLength: 0)
                if isCurrent { Image(systemName: "checkmark").foregroundStyle(Color.seiraInk) }
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isCurrent ? .isSelected : [])
    }
}
