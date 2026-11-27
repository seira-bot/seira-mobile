// The roster.
//
// Home: the Chats tab as Bots, one foldable group per department, and group
// chats. Simple mode shows bots as tiles. Advanced mode (Settings) shows a
// search row, Seira on top and a row per bot with its threads beneath.
import SwiftUI
import CompanionCore

struct ChatListView: View {
    @EnvironmentObject private var session: Session
    @EnvironmentObject private var liveCall: LiveCallController
    @AppStorage(PrefKey.activityDetail) private var activityDetail = ActivityDetail.phoneDefault.rawValue
    @AppStorage(PrefKey.rosterDensity) private var rosterDensity = RosterDensity.default.rawValue
    /// Driven so that making a bot can open it. Value-based navigation alone
    /// cannot push without a tap, and a new bot appearing silently at the
    /// bottom of the roster is a poor answer to pressing +.
    @State private var path = NavigationPath()
    @State private var tab = HomeTab.chats
    @AppStorage(PrefKey.advancedMode) private var advancedMode = false
    /// Folded group keys; a group that is not listed is open.
    @SceneStorage("home.foldedGroups") private var foldedGroups = ""
    @State private var departmentMenu: HomeDepartmentTarget?
    @State private var afterMenu: DepartmentAction?
    @State private var addingBotsTo: HomeDepartmentTarget?
    @State private var newBotIn: HomeDepartmentTarget?
    @State private var drawerOpen = false
    @State private var showingNewChat = false
    @State private var showingUpdates = false
    @State private var showingWalkie = false
    @State private var showingNewGroup = false
    @State private var showingNewSection = false
    @State private var expandedBots = Set<String>()
    @State private var collapsedFolders = Set<String>()
    @State private var creatingThreads = Set<String>()
    @State private var managingThreads: Chat?
    @State private var disclosingLiveCall: Bot?
    @State private var unplacedBot: UnplacedBot?
    @State private var changingLook: Bot?
    /// The preview lines comfortable rows and search show, folded again only
    /// for a thread that changed. Filled while rendering: it is a cache,
    /// not something any view observes.
    @State private var previewCache = RosterPreviewCache()

    /// Space between the header and whatever the list starts with.
    private static let listTopInset: CGFloat = 8
    private static let listBottomMargin: CGFloat = 16

    var body: some View {
        // Read once per render. The island, the pill (twice: the bar lays
        // out both of its widths) and every row's waiting hand all start
        // from the same approvals, and each walk of them visits every thread.
        let approvals = session.state.pendingApprovals
        let updates = session.state.updates(detail: activity, pendingApprovals: approvals)
        let waiting = waitingChats(approvals)
        NavigationStack(path: $path) {
            HomeDrawerLayer(
                open: $drawerOpen,
                name: session.connection?.name ?? "You",
                select: { path.append($0) }
            ) {
                homeColumn(updates: updates, waiting: waiting)
            }
            .background(Color.seiraApp.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: Chat.self) { ChatView(chat: $0) }
            .navigationDestination(for: HomeRoute.self) { route in
                routePage(route).environment(\.returnToChats, { path = NavigationPath() })
            }
            .onValueChange(of: session.notificationChat) { chat in
                guard let chat else { return }
                path.append(chat)
                session.consumeNotificationChat()
            }
            // The computer moved to another workspace: a chat of the old one has no meaning here.
            .onValueChange(of: session.workspaceEpoch) { _ in
                path = NavigationPath()
                managingThreads = nil
                changingLook = nil
            }
            .onValueChange(of: session.pendingChat) { chat in
                guard let chat else { return }
                path.append(chat)
                session.consumePendingChat()
            }
            .task {
                if let chat = session.notificationChat {
                    path.append(chat)
                    session.consumeNotificationChat()
                }
                if let chat = session.pendingChat {
                    path.append(chat)
                    session.consumePendingChat()
                }
            }
#if DEBUG
            // Preview-only routes let the screenshot harness reach screens
            // that normally require a paired computer and a tap.
            .task {
                if ProcessInfo.processInfo.arguments.contains("-open-new-section") {
                    showingNewSection = true
                }
                if ProcessInfo.processInfo.arguments.contains("-open-walkie") {
                    showingWalkie = true
                }
                if ProcessInfo.processInfo.arguments.contains("-open-first"),
                   path.isEmpty,
                   let first = rosterSummaries(previews: false).first(where: { if case .bot = $0.chat { return true } else { return false } }) {
                    path.append(first.chat)
                }
            }
#endif
            .sheet(item: $changingLook) { bot in
                ChangeLookSheet(bot: bot)
                    .environmentObject(session)
            }
            .sheet(isPresented: $showingUpdates) {
                UpdatesSheet { chat in
                    showingUpdates = false
                    path.append(chat)
                }
            }
            .sheet(isPresented: $showingNewChat) {
                NewChatSheet(
                    chief: homeChief,
                    openBot: { openBot($0) },
                    newChatWithChief: { newChat() },
                    openGroup: { path.append(Chat.room($0)) },
                    newSection: { openNewSection() },
                    walkie: { Haptics.selection(); showingWalkie = true },
                    walkieDisabled: liveCall.machine.isActive
                )
                .environmentObject(session)
            }
            .fullScreenCover(isPresented: $showingWalkie) {
                WalkieView { chat in
                    showingWalkie = false
                    path.append(chat)
                }
                .environmentObject(session)
            }
            .sheet(isPresented: $showingNewGroup) {
                NewGroupSheet { room in
                    showingNewGroup = false
                    path.append(Chat.room(room))
                }
            }
            .sheet(isPresented: $showingNewSection) {
                NewSectionSheet()
            }
            .sheet(item: $departmentMenu, onDismiss: runAfterMenu) { target in
                DepartmentMenuSheet(
                    department: target.name,
                    addBots: { afterMenu = .addBots(target); departmentMenu = nil },
                    newBot: { afterMenu = .newBot(target); departmentMenu = nil }
                )
            }
            .sheet(item: $addingBotsTo) { target in
                AddBotsSheet(department: target.name)
                    .environmentObject(session)
            }
            .sheet(item: $newBotIn) { target in
                NewBotForm { assignNewBot($0, to: target.name) }
                    .environmentObject(session)
            }
            .sheet(item: $managingThreads) { chat in
                TaskManagerView(chat: chat) { threadId in
                    guard let bot = session.state.bot(forThread: threadId) else { return }
                    managingThreads = nil
                    path.append(Chat.bot(bot))
                }
            }
            .alert(
                Text("\(unplacedBot?.bot.name ?? "") was made, but is not in \(unplacedBot?.department ?? "")."),
                isPresented: Binding(
                    get: { unplacedBot != nil },
                    set: { if !$0 { unplacedBot = nil } }
                ),
                presenting: unplacedBot
            ) { target in
                Button("Try again") {
                    Task { await placeNewBot(target.bot, in: target.department) }
                }
                Button("Not now", role: .cancel) { openBot(target.bot) }
            }
        }
    }

    // MARK: - Scroll

    private func homeColumn(updates: [ChatUpdate], waiting: Set<String>) -> some View {
        VStack(spacing: 0) {
            HomeTopBar(
                profileName: session.connection?.name ?? "You",
                needsYou: !waiting.isEmpty,
                openMenu: { Haptics.selection(); drawerOpen = true },
                openSeira: { Haptics.selection(); showingUpdates = true },
                openProfile: { path.append(HomeRoute.settings) }
            )
            WorkspaceStrip(
                model: session.workspacePicker,
                computerID: session.connection?.id,
                live: session.status == .live
            )
            StatusBanner()
            LiveCallBanner { target in
                if let bot = session.state.bot(target.botId)?.projected(forThread: target.threadId) {
                    path.append(Chat.bot(bot))
                }
            }
            tabContent(updates: updates, waiting: waiting)
            HomeTabBar(tab: $tab, inboxCount: InboxLogic.badgeCount(updates))
        }
        .frame(maxWidth: CompanionLayout.rosterWidth, maxHeight: .infinity, alignment: .top)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .modifier(HomeLiveCallDisclosure(bot: $disclosingLiveCall, start: startLiveCall))
    }

    @ViewBuilder
    private func tabContent(updates: [ChatUpdate], waiting: Set<String>) -> some View {
        switch tab {
        case .chats:
            VStack(spacing: 0) {
                HomeChatsHeader(
                    newChat: { Haptics.selection(); showingNewChat = true }
                )
                rosterScroll(waiting: waiting)
            }
        case .inbox:
            InboxTab { path.append($0) }
        case .automations:
            AutomationsTab()
        }
    }

    @ViewBuilder
    private func routePage(_ route: HomeRoute) -> some View {
        switch route {
        case .search: SearchPage { path.append($0) }
        case .settings: SettingsView()
        case .teamMap: TeamMapPage()
        case .departmentMemory: DepartmentMemoryPage()
        case .computers: ComputersPage()
        case .botProfiles: BotProfilesPage()
        }
    }

    /// What one pass over the list needs. Worked out once, then each line
    /// reads from it: no line walks the state again.
    private struct HomeContext {
        let state: CompanionState
        let items: [HomeItem]
        let waiting: Set<String>
        let byBot: [String: ChatSummary]
        let today: Date
        let queued: Set<String>
        let held: Set<String>
        let compact: Bool
    }

    private func homeContext(waiting: Set<String>) -> HomeContext {
        let state = session.state
        let layout = HomeLayoutRules.layout(state, advanced: advancedMode, department: nil)
        let folded = FoldRules.decode(foldedGroups)
        let items = HomeItems.build(
            layout: layout, folded: folded, advanced: advancedMode, expandedBots: expandedBots, waiting: waiting
        )
        var byBot: [String: ChatSummary] = [:]
        if advancedMode {
            for summary in rosterSummaries(previews: density == .comfortable) {
                if case let .bot(bot) = summary.chat, byBot[bot.id] == nil { byBot[bot.id] = summary }
            }
        }
        return HomeContext(
            state: state,
            items: items,
            waiting: waiting,
            byBot: byBot,
            today: RosterDay.today,
            queued: state.queuedThreadIds,
            held: Set(state.pendingQueued.compactMap { $0.value.isEmpty ? nil : $0.key }),
            compact: density == .compact
        )
    }

    private func rosterScroll(waiting: Set<String>) -> some View {
        let home = homeContext(waiting: waiting)
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(home.items) { item in
                    itemView(item, home: home)
                }
            }
            .padding(.top, Self.listTopInset)
            .padding(.bottom, Self.listBottomMargin)
        }
        .refreshable { await session.refresh() }
        .accessibilityIdentifier("roster-list")
        .overlay { emptyState() }
    }

    @ViewBuilder
    private func emptyState() -> some View {
        if rosterIsEmpty() {
            if case .offline = session.status {
                SeiraEmptyState(
                    icon: "Icons3D/offline",
                    title: "You are offline",
                    message: "Check your internet connection. Seira picks up where you left off."
                )
            } else {
                SeiraEmptyState(
                    icon: "Icons3D/empty-chats",
                    title: "No bots",
                    message: "Bots you create on your computer show up here."
                )
            }
        }
    }

    // MARK: - Home top

    /// The Chief of Staff, or the first Chief when none sits outside a section.
    private var homeChief: Bot? {
        let state = session.state
        return state.unsectionedChief ?? state.bots.first { $0.hidden != true && $0.chiefOfStaff == true }
    }

    private func openBot(_ bot: Bot) {
        Haptics.selection()
        path.append(session.threadSelection.restoringThread(.bot(bot), connectionID: session.connection?.id))
    }

    private func talkTo(_ bot: Bot) {
        Haptics.selection()
        if LiveCallDisclosure().isDue {
            disclosingLiveCall = bot
        } else {
            startLiveCall(bot)
        }
    }

    private func startLiveCall(_ bot: Bot) {
        liveCall.start(bot: bot)
        liveCall.presentScreen()
    }

    private func newChat() {
        guard let chief = homeChief, creatingThreads.insert(chief.id).inserted else { return }
        Haptics.selection()
        Task {
            defer { creatingThreads.remove(chief.id) }
            if let created = await session.createRosterThread(for: chief) {
                Haptics.success()
                path.append(Chat.bot(created))
            }
        }
    }

    // MARK: - Groups

    private enum DepartmentAction: Equatable {
        case addBots(HomeDepartmentTarget)
        case newBot(HomeDepartmentTarget)
    }

    @ViewBuilder
    private func itemView(_ item: HomeItem, home: HomeContext) -> some View {
        switch item {
        case .search:
            HomeSearchRow { Haptics.selection(); path.append(HomeRoute.search) }
        case let .header(group, open, foldable, waiting):
            groupHeader(group, open: open, foldable: foldable, waiting: waiting)
        case let .tiles(_, bots):
            tileRow(bots, home: home)
        case let .advancedBot(bot, _, highlighted):
            advancedRow(bot, highlighted: highlighted, home: home)
        case let .threads(bot):
            threadTree(bot, queued: home.queued, held: home.held)
        case let .room(room, _):
            roomRow(room, home: home)
        case let .emptyDepartment(name, advanced):
            emptyDepartment(name, advanced: advanced)
        }
    }

    private func groupHeader(_ group: HomeGroup, open: Bool, foldable: Bool, waiting: Int) -> some View {
        let folded: Bool? = foldable ? !open : nil
        let toggle = { toggleGroup(group.key, keys: HomeLayoutRules.layout(session.state, advanced: advancedMode, department: nil).keys) }
        return groupHeaderContent(group, folded: folded, toggle: toggle, waiting: waiting)
            .padding(.top, 6)
    }

    @ViewBuilder
    private func groupHeaderContent(
        _ group: HomeGroup, folded: Bool?, toggle: @escaping () -> Void, waiting: Int
    ) -> some View {
        switch group.kind {
        case .bots:
            HomeGroupHeader(title: String(localized: "Bots"), folded: folded, toggle: toggle) {
                if !advancedMode {
                    HomeGroupIconButton(symbol: "magnifyingglass", label: Text("Search chats and bots")) {
                        Haptics.selection()
                        path.append(HomeRoute.search)
                    }
                    .accessibilityIdentifier("home-search")
                }
            }
        case .department:
            let name = group.name ?? ""
            let target = HomeDepartmentTarget(name: name)
            HomeGroupHeader(title: name, folded: folded, toggle: toggle, waiting: waiting) {
                HomeGroupIconButton(symbol: "ellipsis", label: Text("Actions for \(name) department")) {
                    Haptics.selection()
                    departmentMenu = target
                }
                if advancedMode {
                    HomeGroupIconButton(symbol: "plus", label: Text("Add or remove bots in \(name)")) {
                        Haptics.selection()
                        addingBotsTo = target
                    }
                }
            }
        case .groups:
            HomeGroupHeader(title: String(localized: "Group chats"), folded: folded, toggle: toggle) {
                HomeGroupIconButton(symbol: "plus", label: Text("New group")) {
                    Haptics.selection()
                    showingNewGroup = true
                }
                .accessibilityIdentifier("new-group")
            }
        }
    }

    @ViewBuilder
    private func emptyDepartment(_ name: String, advanced: Bool) -> some View {
        if advanced {
            Text("No bots in this department.")
                .font(.seira(13, relativeTo: .footnote))
                .foregroundStyle(Color.seiraInk3)
                .padding(.horizontal, HomeRowMetrics.side)
                .padding(.bottom, 8)
        } else {
            Button {
                Haptics.selection()
                addingBotsTo = HomeDepartmentTarget(name: name)
            } label: {
                Text("Add bots")
                    .font(.seira(14, .medium, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraAccent)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(.horizontal, HomeRowMetrics.side)
            .accessibilityIdentifier("empty-add-bots.\(name)")
        }
    }

    /// One row of up to four tiles; a short last row keeps the tiles' width.
    private func tileRow(_ bots: [Bot], home: HomeContext) -> some View {
        HStack(alignment: .top, spacing: 4) {
            ForEach(bots) { bot in
                let dot = HomeDotRules.of(bot, bot.rosterStatus(hasPendingCard: home.waiting.contains(bot.id)))
                Button { openBot(bot) } label: {
                    BotTile(bot: bot, dot: dot).equatable()
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("chat-row.\(bot.id)")
                .contextMenu { botMenu(bot) }
            }
            ForEach(0 ..< max(0, HomeLayoutRules.tileColumns - bots.count), id: \.self) { _ in
                Color.clear.frame(maxWidth: .infinity, minHeight: 1)
            }
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }

    @ViewBuilder
    private func botMenu(_ bot: Bot) -> some View {
        Button { changingLook = bot } label: { Label("Change look", systemImage: "face.smiling") }
        if !liveCall.machine.isActive, liveCall.machine.allowsStart(onThread: bot.threadId),
           session.state.liveCall?.isRunning != true {
            Button { talkTo(bot) } label: { Label("Talk", systemImage: "waveform") }
        }
    }

    private func advancedRow(_ bot: Bot, highlighted: Bool, home: HomeContext) -> some View {
        let summary = home.byBot[bot.id]
        return AdvancedBotRow(
            bot: bot,
            preview: summary?.preview ?? "",
            at: summary?.lastActivity ?? 0,
            today: home.today,
            mood: CharacterMood.forChat(.bot(bot), in: home.state),
            waiting: home.waiting.contains(bot.id),
            expanded: expandedBots.contains(bot.id),
            highlighted: highlighted,
            compact: home.compact,
            toggle: {
                Haptics.selection()
                withSeiraAnimation(SeiraMotion.base) {
                    if expandedBots.contains(bot.id) { expandedBots.remove(bot.id) } else { expandedBots.insert(bot.id) }
                }
            },
            open: { openBot(bot) }
        )
        .equatable()
        .contextMenu { botMenu(bot) }
    }

    private func runAfterMenu() {
        guard let action = afterMenu else { return }
        afterMenu = nil
        switch action {
        case let .addBots(target): addingBotsTo = target
        case let .newBot(target): newBotIn = target
        }
    }

    private func toggleGroup(_ key: String, keys: Set<String>) {
        Haptics.selection()
        let folded = FoldRules.decode(foldedGroups)
        let next = FoldRules.prune(FoldRules.toggle(folded, key), keys)
        withSeiraAnimation(SeiraMotion.base) { foldedGroups = FoldRules.encode(next) }
    }

    private func assignNewBot(_ bot: Bot, to department: String) {
        newBotIn = nil
        Task { await placeNewBot(bot, in: department) }
    }

    /// The bot exists; only its place can fail. Retrying sends the place again, never a second bot.
    private func placeNewBot(_ bot: Bot, in department: String) async {
        if await session.assignSection(name: department, botIds: [bot.id], reportsError: false) == nil {
            Haptics.notification(.error)
            unplacedBot = UnplacedBot(bot: bot, department: department)
        } else {
            openBot(bot)
        }
    }

    // MARK: - Rooms

    private func roomRow(_ room: Room, home: HomeContext) -> some View {
        NavigationLink(value: Chat.room(room)) {
            HomeRoomRow(
                room: room,
                members: members(of: room),
                lastActivity: home.state.lastVisibleMessage(forThread: room.threadId)?.at ?? 0,
                today: home.today,
                waiting: home.waiting.contains(room.id),
                compact: advancedMode && home.compact
            )
            .equatable()
        }
        .buttonStyle(HomeRowButtonStyle())
        .accessibilityIdentifier("chat-row.\(room.id)")
    }

    private func threadTree(_ bot: Bot, queued: Set<String>, held: Set<String>) -> some View {
        BotThreadTree(
            bot: bot,
            expandedBots: $expandedBots,
            collapsedFolders: $collapsedFolders,
            creatingThreads: $creatingThreads,
            queuedThreadIds: queued,
            heldThreadIds: held,
            session: session,
            open: { chat in path.append(chat) },
            manage: { chat in managingThreads = chat }
        )
        .equatable()
    }

    private func openNewSection() {
        Haptics.selection()
        showingNewSection = true
    }

    // MARK: - Data

    /// The reader's activity level, which the roster preview folds by.
    private var activity: ActivityDetail { ActivityDetail(rawValue: activityDetail) ?? .phoneDefault }

    /// How much each Advanced row says, from Settings.
    private var density: RosterDensity { RosterDensity(stored: rosterDensity) }

    /// Every chat in roster order, as `chatSummaries` lists them. With
    /// previews, each line comes from `previewCache`, which folds a thread
    /// again only once it has changed; without, the cache lets go of what
    /// it holds.
    private func rosterSummaries(previews: Bool) -> [ChatSummary] {
        let state = session.state
        let all = state.chatSummaries(activity: activity, previews: false)
        guard previews else {
            previewCache.removeAll()
            return all
        }
        previewCache.keepOnly(Set(all.map(\.chat.threadId)))
        return all.map { summary in
            ChatSummary(
                chat: summary.chat,
                preview: previewCache.preview(forThread: summary.chat.threadId, in: state, detail: activity),
                lastActivity: summary.lastActivity,
                pinned: summary.pinned
            )
        }
    }

    private func waitingChats(_ approvals: [(threadId: String, message: Message)]) -> Set<String> {
        Set(approvals.compactMap { session.state.chat(forThread: $0.threadId)?.id })
    }

    private func members(of room: Room) -> [Bot] {
        room.memberIds.compactMap { session.state.bot($0) }
    }

    private func rosterIsEmpty() -> Bool {
        session.state.bots.allSatisfy { $0.hidden == true } && session.state.rooms.isEmpty
    }
}

/// A bot that was made for a department but could not be placed there.
private struct UnplacedBot: Identifiable {
    let bot: Bot
    let department: String
    var id: String { bot.id }
}

/// The floating pill: who is doing what right now, at a glance.
struct UpdatesPill: View {
    let updates: [ChatUpdate]
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if !updates.isEmpty {
                    HeadStack(colors: Array(updates.prefix(3).map(\.chat.color)))
                }
                VStack(alignment: .leading, spacing: 1) {
                    headline
                        .font(.seira(14, .semibold, relativeTo: .subheadline))
                        .foregroundStyle(updates.isEmpty ? Color.seiraInk3 : Color.seiraInk)
                        .lineLimit(1)

                    subline
                        .font(.seira(12, relativeTo: .caption))
                        .foregroundStyle(Color.seiraInk3)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.up")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color.seiraInk3)
            }
            .padding(.leading, updates.isEmpty ? 18 : 7)
            .padding(.trailing, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .glassCapsule()
        .accessibilityLabel("Updates")
        .accessibilityIdentifier("updates-button")
    }

    @ViewBuilder private var headline: some View {
        HStack(spacing: 4) {
            if let first = updates.first {
                switch first.kind {
                case .needsYou:
                    Image(systemName: "hand.raised.fill")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.seiraWarning)
                    Text("\(first.chat.name) needs you")
                case .working:
                    Text("\(first.chat.name) is working")
                case .toReview:
                    Text("\(first.chat.name) has an update")
                }
            } else {
                Text("All quiet")
            }
        }
    }

    private var subline: Text {
        guard let first = updates.first else { return Text("Nothing needs you") }
        let rest = updates.count - 1
        if rest == 0 { return Text(verbatim: first.line.isEmpty ? " " : first.line) }
        return rest == 1 ? Text("1 more update") : Text("\(rest) more updates")
    }
}

/// Up to three heads overlapping, the way a group of faces reads at a glance.
struct HeadStack: View {
    let colors: [String]
    var size: CGFloat = 28
    var overlap: CGFloat = 12

    var body: some View {
        HStack(spacing: -overlap) {
            ForEach(Array(colors.enumerated()), id: \.offset) { _, color in
                SeiraAvatarHead(look: SeiraLook(hair: "seira", color: color), size: size)
                    .background(Circle().fill(SeiraIdentity.plate(hex: color)))
                    .padding(2)
                    .background(Circle().fill(Color(uiColor: .systemBackground)))
            }
        }
    }
}

/// Connection state, shown only when it is not "fine".
struct StatusBanner: View {
    @EnvironmentObject private var session: Session

    var body: some View {
        Group {
            switch session.status {
            case .live, .unpaired:
                EmptyView()
            case .connecting:
                banner(Text("Connecting…"), systemImage: "arrow.triangle.2.circlepath", tint: .seiraInk2)
            case let .offline(reason):
                banner(Text(verbatim: reason), systemImage: "wifi.slash", tint: .seiraWarningText)
            case .unauthorized:
                banner(Text("This device was unpaired on the computer."), systemImage: "lock.slash", tint: .seiraDangerText)
            }
        }
        .seiraAnimation(SeiraMotion.base, value: session.status)
    }

    private func banner(_ text: Text, systemImage: String, tint: Color) -> some View {
        Label { text } icon: { Image(systemName: systemImage) }
            .font(.seira(13, relativeTo: .footnote))
            .foregroundStyle(tint)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .glassCapsule(interactive: false)
            .padding(.bottom, 8)
    }
}

struct SearchHitRow: View {
    let hit: SearchHit

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: hit.role == .user ? "person.fill" : "bubble.left.fill")
                .foregroundStyle(Color.seiraInk3)
                .frame(width: 26, height: 26)
                .background(Circle().fill(Color.seiraTint))

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(hit.name)
                        .font(.seira(15, .semibold, relativeTo: .body))
                        .foregroundStyle(Color.seiraInk)
                    if let task = hit.task, !task.isEmpty {
                        Text(task)
                            .font(.seira(12, relativeTo: .caption))
                            .foregroundStyle(Color.seiraInk3)
                    }
                    Spacer()
                    Text(RelativeStamp.list(hit.at))
                        .font(.seira(12, relativeTo: .caption))
                        .monospacedDigit()
                        .foregroundStyle(Color.seiraInk3)
                }
                Text(hit.snippet)
                    .font(.seira(13, relativeTo: .subheadline))
                    .foregroundStyle(Color.seiraInk2)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }
}

/// Timestamps the way a messaging app writes them.
enum RelativeStamp {
    /// Roster: time today, weekday this week, date beyond that.
    static func list(_ at: Double) -> String {
        guard at > 0 else { return "" }
        let date = Date(timeIntervalSince1970: at / 1000)
        let calendar = Calendar.current
        if calendar.isDateInToday(date) {
            return date.formatted(date: .omitted, time: .shortened)
        }
        if calendar.isDateInYesterday(date) { return "Yesterday" }
        if let week = calendar.date(byAdding: .day, value: -6, to: Date()), date > week {
            return date.formatted(.dateTime.weekday(.wide))
        }
        return date.formatted(.dateTime.day().month(.abbreviated))
    }

    /// In a transcript: enough to place a gap in the conversation.
    static func separator(_ date: Date) -> String {
        let calendar = Calendar.current
        let time = date.formatted(date: .omitted, time: .shortened)
        if calendar.isDateInToday(date) { return "Today \(time)" }
        if calendar.isDateInYesterday(date) { return "Yesterday \(time)" }
        return "\(date.formatted(.dateTime.day().month(.abbreviated))) \(time)"
    }
}
