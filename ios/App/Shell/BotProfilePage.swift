import CompanionCore
import SwiftUI

/// A bot's page: who it is, then Activity, Settings, Library and Computer.
struct BotProfilePage: View {
    let botId: String
    var initialTab: ProfileTab = .activity
    var onDeleted: () -> Void = {}

    @EnvironmentObject private var session: Session
    @State private var tab: ProfileTab = .activity
    @State private var showingChangeLook = false

    private var bot: Bot? { session.state.bot(botId) }

    var body: some View {
        Group {
            if let bot {
                VStack(spacing: 0) {
                    header(bot)
                    tabBar
                    content(bot)
                }
            } else {
                EmptyStateView(String(localized: "This bot is gone"), systemImage: "person.crop.circle.badge.xmark")
            }
        }
        .background(Color.seiraBg)
        .navigationTitle(bot?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { tab = initialTab }
        .sheet(isPresented: $showingChangeLook) {
            if let bot {
                ChangeLookSheet(bot: bot).environmentObject(session)
            }
        }
    }

    private func header(_ bot: Bot) -> some View {
        HStack(spacing: 14) {
            Button { showingChangeLook = true } label: {
                BotAvatarView(bot: bot, size: 56, state: .idle)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Change look")
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: bot.name)
                    .font(.seira(20, .semibold, relativeTo: .title3))
                    .foregroundStyle(Color.seiraFg)
                if !bot.title.isEmpty {
                    Text(verbatim: bot.title)
                        .font(.seira(14, relativeTo: .subheadline))
                        .foregroundStyle(Color.seiraMut)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var tabBar: some View {
        HStack(spacing: 8) {
            ForEach(ProfileTab.allCases, id: \.self) { item in
                let selected = item == tab
                let fill: Color = selected ? .seiraAcc : .seiraS2
                let ink: Color = selected ? .seiraOnAcc : .seiraFg
                Button { tab = item } label: {
                    Text(title(of: item))
                        .font(.seira(14, .semibold, relativeTo: .subheadline))
                        .foregroundStyle(ink)
                        .padding(.horizontal, 14)
                        .frame(minHeight: 44)
                        .background(fill, in: Capsule())
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
                .accessibilityIdentifier("profile-tab-\(item.rawValue)")
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private func title(of tab: ProfileTab) -> LocalizedStringKey {
        switch tab {
        case .activity: return "Activity"
        case .settings: return "Settings"
        case .library: return "Library"
        case .computer: return "Computer"
        }
    }

    @ViewBuilder
    private func content(_ bot: Bot) -> some View {
        switch tab {
        case .activity: BotActivityView(bot: bot, embedded: true)
        case .settings: BotSettingsTab(bot: bot, onDeleted: onDeleted)
        case .library: BotLibraryTab(bot: bot)
        case .computer: ComputerView(bot: bot)
        }
    }
}

private struct BotSettingsTab: View {
    let bot: Bot
    let onDeleted: () -> Void

    @EnvironmentObject private var session: Session
    @State private var draft: ProfileDraft
    @State private var baseline: ProfileDraft
    @State private var notifications: Bool
    @State private var busy = false
    @State private var confirmingDelete = false

    init(bot: Bot, onDeleted: @escaping () -> Void) {
        self.bot = bot
        self.onDeleted = onDeleted
        let seed = ProfileDraft.of(bot)
        _draft = State(initialValue: seed)
        _baseline = State(initialValue: seed)
        _notifications = State(initialValue: bot.notifications)
    }

    var body: some View {
        Form {
            Section("Identity") {
                TextField("Name", text: $draft.name)
                    .textInputAutocapitalization(.words)
                TextField("Title", text: $draft.title)
                TextField("What this agent does", text: $draft.description, axis: .vertical)
                    .lineLimit(3...8)
            }
            Section("Instructions") {
                TextField("How this bot should work", text: $draft.soul, axis: .vertical)
                    .lineLimit(4...12)
            }
            Section("Reply style") {
                Picker("Reply style", selection: $draft.replyStyle) {
                    Text("Not set").tag(ReplyStyle?.none)
                    Text("Default").tag(Optional(ReplyStyle.default))
                    Text("Conversational").tag(Optional(ReplyStyle.conversational))
                }
            }
            Section {
                Toggle("Agent notifications", isOn: $notifications)
            }
            Section {
                Button("Save profile changes") { Task { await save() } }
                    .disabled(!ProfileDraftRules.canSave(draft, baseline: baseline, busy: busy))
            }
            Section {
                Button("Delete \(bot.name)", role: .destructive) { confirmingDelete = true }
                    .disabled(busy)
            }
        }
        .scrollContentBackground(.hidden)
        .onValueChange(of: notifications) { value in
            guard value != bot.notifications else { return }
            Task { _ = await session.updateProfile(BotProfilePatch(notifications: value), for: bot) }
        }
        .confirmationDialog("Delete \(bot.name)?", isPresented: $confirmingDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { Task { await delete() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This cannot be undone.")
        }
    }

    private func save() async {
        busy = true
        defer { busy = false }
        let patch = ProfileDraftRules.patch(draft, baseline: baseline)
        guard await session.updateProfile(patch, for: bot) != nil else { return }
        baseline = draft
    }

    private func delete() async {
        busy = true
        defer { busy = false }
        if await session.deleteBot(bot) { onDeleted() }
    }
}

private struct BotLibraryTab: View {
    let bot: Bot

    @EnvironmentObject private var session: Session
    @State private var items: [CompanionCore.LibraryItem]?
    @State private var failed = false

    var body: some View {
        List {
            if let items {
                if items.isEmpty {
                    Text("Nothing here yet. Tables and notes that \(bot.name) makes show up here.")
                        .foregroundStyle(Color.seiraMut)
                }
                ForEach(items) { item in
                    NavigationLink { LibraryItemPage(bot: bot, item: item) } label: {
                        HStack {
                            Text(verbatim: item.title)
                            Spacer()
                            if let count = item.count {
                                Text("\(count)").foregroundStyle(Color.seiraMut)
                            }
                        }
                    }
                }
            }
            if failed {
                EmptyStateView(String(localized: "Couldn't load"), systemImage: "wifi.exclamationmark")
            }
        }
        .scrollContentBackground(.hidden)
        .overlay { if items == nil && !failed { ProgressView() } }
        .task(id: session.connection?.id) { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        if let data = await session.botData(for: bot) {
            items = ProfileLibraryRules.items(data)
            failed = false
        } else {
            failed = true
        }
    }
}

private struct LibraryItemPage: View {
    let bot: Bot
    let item: CompanionCore.LibraryItem

    @EnvironmentObject private var session: Session
    @State private var columns: [String] = []
    @State private var rows: [[String]] = []
    @State private var total: Int64 = 0
    @State private var loading = false

    var body: some View {
        List {
            if let text = item.text {
                Text(verbatim: text).textSelection(.enabled)
            } else {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(Array(row.enumerated()), id: \.offset) { index, cell in
                            HStack(alignment: .top) {
                                Text(verbatim: index < columns.count ? columns[index] : "")
                                    .font(.seira(12, relativeTo: .caption))
                                    .foregroundStyle(Color.seiraMut)
                                    .frame(width: 90, alignment: .leading)
                                Text(verbatim: cell).font(.seira(14, relativeTo: .subheadline))
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
                if ProfileLibraryRules.hasMore(loaded: rows.count, total: total) {
                    Button("Show more") { Task { await loadMore() } }.disabled(loading)
                }
            }
        }
        .navigationTitle(item.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { if item.text == nil { await loadMore() } }
    }

    private func loadMore() async {
        loading = true
        defer { loading = false }
        let request = ProfileLibraryRules.request(item, offset: rows.count)
        guard let page = await session.botDataPage(for: bot, request: request) else { return }
        if columns.isEmpty { columns = (page.columns ?? []).map(\.name) }
        let pageRows: [[String]] = page.rows.map { (row: [JSONValue]) -> [String] in
            row.map { ProfileLibraryRules.cell($0) }
        }
        rows += pageRows
        total = page.rowCount ?? Int64(rows.count)
    }
}
