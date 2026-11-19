import AppIntents

struct TalkToSeiraIntent: AppIntent {
    static var title: LocalizedStringResource = "Talk to Seira"
    static var description = IntentDescription("Start a Live call with your Chief of Staff.")
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        AppRouter.shared.pending = .talk(confirmed: true)
        return .result()
    }
}

struct NewChatIntent: AppIntent {
    static var title: LocalizedStringResource = "New chat"
    static var description = IntentDescription("Open a new chat with your Chief of Staff.")
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        AppRouter.shared.pending = .newChat
        return .result()
    }
}

struct SeiraShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: TalkToSeiraIntent(),
            phrases: [
                "Talk to \(.applicationName)",
                "Call \(.applicationName)",
            ]
        )
        AppShortcut(
            intent: NewChatIntent(),
            phrases: [
                "Start a chat in \(.applicationName)",
                "New \(.applicationName) chat",
            ]
        )
    }
}
