// The roster.
//
// Home: a greeting, the Chief of Staff card, a rail of the team, and the chats
// with built-in and user-named sidebar sections. A floating bar keeps Updates,
// search and "New chat" within one thumb's reach while everything scrolls
// beneath its fade. Two densities, chosen in Settings: compact (the default)
// shows each bot and group without its last message; comfortable keeps
// channels as tiles and bots with their last message and a "Threads"
// disclosure beneath each.
import SwiftUI
import CompanionCore

struct ChatListView: View {
    @EnvironmentObject private var session: Session
    @EnvironmentObject private var liveCall: LiveCallController
    @State private var query = ""
    @AppStorage(PrefKey.activityDetail) private var activityDetail = ActivityDetail.phoneDefault.rawValue
    @AppStorage(PrefKey.rosterDensity) private var rosterDensity = RosterDensity.default.rawValue
    /// Driven so that making a bot can open it. Value-based navigation alone
    /// cannot push without a tap, and a new bot appearing silently at the
    /// bottom of the roster is a poor answer to pressing +.
    @State private var path = NavigationPath()
    @State private var searchHits: [SearchHit] = []
    @State private var searching = false
    @State private var tab = HomeTab.chats
    @State private var chatFilter = HomeChatFilter.all
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
        // Search's matches: the list and its empty state both read them.
        let matches: [ChatSummary] = query.isEmpty ? [] : chats
        NavigationStack(path: $path) {
            HomeDrawerLayer(
                open: $drawerOpen,
                name: session.connection?.name ?? "You",
                select: { path.append($0) }
            ) {
                homeColumn(updates: updates, waiting: waiting, matches: matches)
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
                   path.isEmpty, let first = chats.first {
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
            .sheet(item: $managingThreads) { chat in
                TaskManagerView(chat: chat) { threadId in
                    guard let bot = session.state.bot(forThread: threadId) else { return }
                    managingThreads = nil
                    path.append(Chat.bot(bot))
                }
            }
            .task(id: query) {
                let expected = query
                guard expected.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 else {
                    searchHits = []
                    searching = false
                    return
                }
                searching = true
                defer {
                    if query == expected { searching = false }
                }
                try? await Task.sleep(for: .milliseconds(250))
                guard !Task.isCancelled, query == expected else { return }
                let hits = await session.search(expected)
                guard !Task.isCancelled, query == expected else { return }
                searchHits = hits
            }
        }
    }

    // MARK: - Scroll

    private func homeColumn(
        updates: [ChatUpdate], waiting: Set<String>, matches: [ChatSummary]
    ) -> some View {
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
            tabContent(updates: updates, waiting: waiting, matches: matches)
            HomeTabBar(tab: $tab, inboxCount: InboxLogic.badgeCount(updates))
        }
        .frame(maxWidth: CompanionLayout.rosterWidth, maxHeight: .infinity, alignment: .top)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    @ViewBuilder
    private func tabContent(
        updates: [ChatUpdate], waiting: Set<String>, matches: [ChatSummary]
    ) -> some View {
        switch tab {
        case .chats:
            VStack(spacing: 0) {
                HomeChatsHeader(
                    openSearch: { Haptics.selection(); path.append(HomeRoute.search) },
                    newChat: { Haptics.selection(); showingNewChat = true }
                )
                HomeFilterChips(filter: $chatFilter)
                    .padding(.top, 8)
                rosterScroll(updates: updates, waiting: waiting, matches: matches)
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

    private func rosterScroll(
        updates: [ChatUpdate], waiting: Set<String>, matches: [ChatSummary]
    ) -> some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    if query.isEmpty {
                        homeTop(updates: updates, waiting: waiting) { id in
                            withSeiraAnimation(SeiraMotion.slow) { proxy.scrollTo(id, anchor: .top) }
                        }
                        rosterSections(waiting: waiting)
                    } else {
                        searchResults(matches, waiting: waiting)
                    }
                }
                .padding(.top, Self.listTopInset)
                .padding(.bottom, Self.listBottomMargin)
            }
            .refreshable { await session.refresh() }
            .accessibilityIdentifier("roster-list")
            .overlay { emptyState(matches: matches) }
        }
    }

    @ViewBuilder
    private func emptyState(matches: [ChatSummary]) -> some View {
        if rosterIsEmpty(matches: matches) {
            if !query.isEmpty {
                EmptyStateView(
                    "Nothing matches",
                    systemImage: "magnifyingglass",
                    description: Text("No thread matches \u{201C}\(query)\u{201D}.")
                )
            } else if case .offline = session.status {
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

    @ViewBuilder
    private func searchResults(_ matches: [ChatSummary], waiting: Set<String>) -> some View {
        if !searchHits.isEmpty {
            HStack {
                sectionLabel(Text("Messages"))
                Spacer()
                if searching { ProgressView().controlSize(.small) }
            }
            .padding(.top, 10)
            .padding(.bottom, 4)

            ForEach(searchHits) { hit in
                Button {
                    Task {
                        if let chat = await session.open(hit) {
                            Haptics.selection()
                            path.append(chat)
                        }
                    }
                } label: {
                    SearchHitRow(hit: hit)
                }
                .buttonStyle(HomeRowButtonStyle())
                .padding(.horizontal, HomeRowMetrics.side)
            }
            sectionLabel(Text("Threads"))
                .padding(.top, 14)
                .padding(.bottom, 4)
        } else if searching {
            ProgressView()
                .controlSize(.small)
                .frame(maxWidth: .infinity)
                .padding(.top, 24)
        }

        botRows(matches, waiting: waiting)
    }

    // MARK: - Home top

    /// The Chief of Staff, or the first Chief when none sits outside a section.
    private var homeChief: Bot? {
        let state = session.state
        return state.unsectionedChief ?? state.bots.first { $0.hidden != true && $0.chiefOfStaff == true }
    }

    @ViewBuilder
    private func homeTop(
        updates: [ChatUpdate], waiting: Set<String>, scrollTo: @escaping (String) -> Void
    ) -> some View {
        let chief = chatFilter.showsBots ? homeChief : nil
        let team = chatFilter.showsBots ? teamMembers(excluding: chief, waiting: waiting) : []
        if let chief {
            chiefCard(chief, updates: updates, waiting: waiting)
                .padding(.top, 12)
        }
        if !team.isEmpty {
            HomeTeamRail(
                members: team,
                open: { openBot($0) },
                changeLook: { changingLook = $0 },
                seeAll: session.state.unsectionedBots.isEmpty ? nil : { scrollTo(Self.botsSectionID) }
            )
            .padding(.top, chief == nil ? 0 : 20)
        }
        if !rosterIsEmpty(matches: []) {
            sectionLabel(Text("Chats"))
                .padding(.top, chief == nil && team.isEmpty ? 8 : 24)
                .padding(.bottom, 4)
        }
    }

    private static let botsSectionID = "home-bots-section"

    private func chiefCard(_ chief: Bot, updates: [ChatUpdate], waiting: Set<String>) -> some View {
        let line = updates.first { $0.kind == .working && $0.chat.id == chief.id }?.line ?? ""
        let canTalk = !liveCall.machine.isActive
            && liveCall.machine.allowsStart(onThread: chief.threadId)
            && session.state.liveCall?.isRunning != true
        return HomeChiefCard(
            bot: chief,
            status: chief.rosterStatus(hasPendingCard: waiting.contains(chief.id)),
            workingLine: line,
            canTalk: canTalk,
            open: { openBot(chief) },
            talk: { talkTo(chief) },
            allQuiet: updates.allSatisfy { $0.kind != .needsYou }
        )
        .modifier(HomeLiveCallDisclosure(bot: $disclosingLiveCall, start: startLiveCall))
    }

    /// Waiting first, then working, then the rest, each in the roster's order.
    private func teamMembers(excluding chief: Bot?, waiting: Set<String>) -> [HomeTeamMember] {
        let members = session.state.bots
            .filter { $0.hidden != true && $0.id != chief?.id }
            .map { HomeTeamMember(bot: $0, status: $0.rosterStatus(hasPendingCard: waiting.contains($0.id))) }
        func rank(_ member: HomeTeamMember) -> Int {
            switch member.status {
            case .waitingOnYou: return 0
            case .working: return 1
            case .idle: return 2
            }
        }
        return members.enumerated()
            .sorted { (rank($0.element), $0.offset) < (rank($1.element), $1.offset) }
            .map(\.element)
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

    // MARK: - Sidebar sections

    /// Every thread across every bot that needs the person right now — the
    /// same rule and order as the thread tree, so the inbox can never
    /// disagree with it.
    private var attention: [AttentionThread] {
        crossBotAttentionThreads(session.state.bots)
    }

    @ViewBuilder
    private func rosterSections(waiting: Set<String>) -> some View {
        // Only comfortable rows show a preview line (search, which matches
        // on it, reads `chats` instead).
        let allSummaries = rosterSummaries(previews: density == .comfortable)
        let attention = chatFilter.showsBots ? self.attention : []
        let showsBots = chatFilter.showsBots
        let showsGroups = chatFilter.showsGroups
        if !attention.isEmpty {
            sectionLabel(Text("Needs attention"))
                .padding(.top, 2)
                .padding(.bottom, 4)
            ForEach(attention) { entry in
                Button {
                    Haptics.selection()
                    openAttention(entry)
                } label: {
                    AttentionRow(entry: entry)
                }
                .buttonStyle(HomeRowButtonStyle())
            }
        }

        if showsBots, let chief = session.state.unsectionedChief {
            VStack(alignment: .leading, spacing: 0) {
                botRows(summaries(for: [chief], from: allSummaries), waiting: waiting)
            }
            // In compact, a one-line row right under the attention rows
            // would read as one more of them: set it apart like a section.
            .padding(.top, density == .compact && !attention.isEmpty ? sectionSpacing : 0)
        }

        let pinned = showsBots ? summaries(for: session.state.pinnedBots, from: allSummaries) : []
        if !pinned.isEmpty {
            sectionLabel(Text("Pinned"))
                // a compact row above it leaves little air of its own
                .padding(.top, density == .compact ? sectionSpacing : 2)
                .padding(.bottom, 4)
            botRows(pinned, waiting: waiting)
        }

        if showsGroups {
            groupSections(waiting: waiting)
        }

        let unsectioned = showsBots ? summaries(for: session.state.unsectionedBots, from: allSummaries) : []
        if !unsectioned.isEmpty {
            sectionLabel(Text("Bots"))
                .padding(.top, sectionSpacing)
                .padding(.bottom, 4)
                .id(Self.botsSectionID)
            botRows(unsectioned, waiting: waiting)
        }

        ForEach(visibleSections) { section in
            departmentSection(section, allSummaries: allSummaries, waiting: waiting)
        }
    }

    private var visibleSections: [SidebarSection] {
        session.state.sidebarSections.compactMap { section -> SidebarSection? in
            var shown = section
            if !chatFilter.showsBots {
                shown.chiefs = []
                shown.bots = []
            }
            if !chatFilter.showsGroups { shown.channels = [] }
            let empty = shown.chiefs.isEmpty && shown.bots.isEmpty && shown.channels.isEmpty
            return empty && chatFilter != .all ? nil : shown
        }
    }

    @ViewBuilder
    private func groupSections(waiting: Set<String>) -> some View {
        switch density {
        case .comfortable:
            channelsStrip(
                title: "Groups",
                rooms: session.state.unsectionedChannels,
                showsCreate: true
            )

            if !session.state.botChats.isEmpty {
                channelsStrip(title: "Bot threads", rooms: session.state.botChats, showsCreate: false)
            }
        case .compact:
            compactRoomsSection(
                title: "Groups",
                rooms: session.state.unsectionedChannels,
                showsCreate: true,
                waiting: waiting
            )

            if !session.state.botChats.isEmpty {
                compactRoomsSection(title: "Bot threads", rooms: session.state.botChats, showsCreate: false, waiting: waiting)
            }
        }
    }

    private func departmentHeader(_ name: String) -> some View {
        HStack(spacing: 0) {
            sectionLabel(Text(verbatim: name))
            Spacer(minLength: 0)
            if chatFilter.showsGroups {
                Button {
                    Haptics.selection()
                    showingNewGroup = true
                } label: {
                    Label("New group", systemImage: "plus")
                        .labelStyle(.titleAndIcon)
                        .font(.seira(13, .medium, relativeTo: .caption))
                        .foregroundStyle(Color.seiraInk3)
                        .padding(.horizontal, HomeRowMetrics.side)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("new-group-\(name)")
            }
        }
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
    }

    @ViewBuilder
    private func departmentSection(
        _ section: SidebarSection, allSummaries: [ChatSummary], waiting: Set<String>
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            switch density {
            case .comfortable:
                departmentHeader(section.name)
                    .padding(.top, 18)
                    .padding(.bottom, section.chiefs.isEmpty && !section.channels.isEmpty ? 6 : 0)
                if !section.chiefs.isEmpty {
                    botRows(summaries(for: section.chiefs, from: allSummaries), waiting: waiting)
                }
                if !section.channels.isEmpty {
                    channelTiles(section.channels, showsCreate: false)
                        .padding(.top, section.chiefs.isEmpty ? 0 : 8)
                        .padding(.bottom, section.bots.isEmpty ? 4 : 8)
                }
            case .compact:
                departmentHeader(section.name)
                    .padding(.top, sectionSpacing - 12)
                if !section.chiefs.isEmpty {
                    botRows(summaries(for: section.chiefs, from: allSummaries), waiting: waiting)
                }
                compactRoomRows(section.channels, waiting: waiting)
            }
            botRows(summaries(for: section.bots, from: allSummaries), waiting: waiting)
        }
    }

    /// Between one section and the next title.
    private var sectionSpacing: CGFloat { density == .compact ? 20 : 24 }

    /// Groups as one-line rows under a title that carries the "+" the
    /// comfortable strip shows as a tile.
    @ViewBuilder
    private func compactRoomsSection(
        title: LocalizedStringKey, rooms: [Room], showsCreate: Bool, waiting: Set<String>
    ) -> some View {
        HStack(spacing: 0) {
            sectionLabel(Text(title))
            Spacer(minLength: 0)
            if showsCreate {
                Button {
                    Haptics.selection()
                    showingNewGroup = true
                } label: {
                    Image(systemName: "plus")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(Color.seiraInk3)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.trailing, 10)
                .accessibilityLabel("New group")
                .accessibilityIdentifier("new-group")
            }
        }
        // the "+" grows with its title, and stops where the title does
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
        // the "+" is a 44pt target; the title keeps the other titles' rhythm
        .frame(minHeight: showsCreate ? 44 : nil)
        .padding(.top, showsCreate ? 0 : sectionSpacing)
        .padding(.bottom, showsCreate ? 0 : 4)
        compactRoomRows(rooms, waiting: waiting)
    }

    /// In the order the tiles showed them, each stamped with its thread's
    /// last message the way `chatSummaries` stamps a row.
    private func compactRoomRows(_ rooms: [Room], waiting: Set<String>) -> some View {
        let today = RosterDay.today
        return ForEach(rooms) { room in
            NavigationLink(value: Chat.room(room)) {
                CompactRoomRow(
                    room: room,
                    members: members(of: room),
                    lastActivity: session.state.lastVisibleMessage(forThread: room.threadId)?.at ?? 0,
                    today: today,
                    waiting: waiting.contains(room.id)
                )
                .equatable()
            }
            .buttonStyle(HomeRowButtonStyle())
            .accessibilityIdentifier("chat-row.\(room.id)")
        }
    }

    private func channelsStrip(title: LocalizedStringKey, rooms: [Room], showsCreate: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionLabel(Text(title))
            channelTiles(rooms, showsCreate: showsCreate)
        }
        .padding(.top, 2)
    }

    private func channelTiles(_ rooms: [Room], showsCreate: Bool) -> some View {
        // Members travel in the data, as the comfortable rows' values do.
        let tiles = rooms.map { RoomTile(room: $0, members: members(of: $0)) }
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(tiles) { tile in
                    NavigationLink(value: Chat.room(tile.room)) {
                        GroupTile(room: tile.room, members: tile.members)
                            .equatable()
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("chat-row.\(tile.id)")
                }
                if showsCreate {
                    Button {
                        Haptics.selection()
                        showingNewGroup = true
                    } label: {
                        GroupTile(room: nil, members: [])
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("New group")
                }
            }
            .padding(.horizontal, HomeRowMetrics.side)
        }
    }

    @ViewBuilder
    private func botRows(_ rows: [ChatSummary], waiting: Set<String>) -> some View {
        switch density {
        case .comfortable: comfortableRows(rows, waiting: waiting)
        case .compact: compactRows(rows, waiting: waiting)
        }
    }

    /// One line per bot, its threads beneath it once opened. Search results
    /// can include groups, which get their own one-line row.
    private func compactRows(_ rows: [ChatSummary], waiting: Set<String>) -> some View {
        let queued = session.state.queuedThreadIds
        let today = RosterDay.today
        return ForEach(rows) { summary in
            switch summary.chat {
            case let .bot(bot):
                CompactBotEntry(
                    bot: bot,
                    face: CharacterMood.forChat(summary.chat, in: session.state),
                    lastActivity: summary.lastActivity,
                    today: today,
                    hasPendingCard: waiting.contains(bot.id),
                    queuedThreadIds: queued,
                    query: query,
                    expanded: expandedBots.contains(bot.id),
                    collapsedFolders: collapsedFolders,
                    creating: creatingThreads.contains(bot.id),
                    session: session,
                    toggleExpanded: {
                        if expandedBots.contains(bot.id) { expandedBots.remove(bot.id) } else { expandedBots.insert(bot.id) }
                    },
                    collapse: { expandedBots.remove(bot.id) },
                    toggleFolder: { key in
                        if collapsedFolders.contains(key) { collapsedFolders.remove(key) } else { collapsedFolders.insert(key) }
                    },
                    beginCreating: { creatingThreads.insert(bot.id).inserted },
                    endCreating: { creatingThreads.remove(bot.id) },
                    openRow: {
                        path.append(session.threadSelection.restoringThread(summary.chat, connectionID: session.connection?.id))
                    },
                    open: { chat in path.append(chat) },
                    manage: { chat in managingThreads = chat }
                )
                .equatable()
            case let .room(room):
                Button {
                    path.append(summary.chat)
                } label: {
                    CompactRoomRow(
                        room: room, members: members(of: room),
                        lastActivity: summary.lastActivity, today: today, waiting: waiting.contains(room.id)
                    )
                    .equatable()
                }
                .buttonStyle(HomeRowButtonStyle())
                .accessibilityIdentifier("chat-row.\(room.id)")
            }
        }
    }

    /// Two lines per bot with its "Threads" beneath. As in `compactRows`,
    /// each row and tree is Equatable on its bot's values and shown
    /// `.equatable()`, so a publish redraws only the bots that moved.
    private func comfortableRows(_ rows: [ChatSummary], waiting: Set<String>) -> some View {
        ForEach(comfortableEntries(rows, waiting: waiting)) { entry in
            VStack(spacing: 0) {
                Button {
                    path.append(session.threadSelection.restoringThread(entry.row.chat, connectionID: session.connection?.id))
                } label: {
                    entry.row.equatable()
                }
                .buttonStyle(HomeRowButtonStyle())
                .accessibilityIdentifier("chat-row.\(entry.id)")
                if case let .bot(bot) = entry.row.chat {
                    threadTree(bot, queued: entry.queued, held: entry.held)
                }
            }
        }
    }

    /// Each row worked out before the `ForEach`, so everything it shows is
    /// in the `ForEach`'s data: the lazy list may skip rebuilding a row whose
    /// data did not change, and a value read inside the closure instead
    /// would go stale there.
    private func comfortableEntries(_ rows: [ChatSummary], waiting: Set<String>) -> [ComfortableEntry] {
        let state = session.state
        let today = RosterDay.today
        let queued = state.queuedThreadIds
        // The trees' "Queued" marks read the held sends themselves.
        let held = Set(state.pendingQueued.compactMap { $0.value.isEmpty ? nil : $0.key })
        return rows.map { summary -> ComfortableEntry in
            var roomMembers: [Bot] = []
            if case let .room(room) = summary.chat { roomMembers = members(of: room) }
            let row = ChatRow(
                chat: summary.chat,
                members: roomMembers,
                preview: summary.preview,
                at: summary.lastActivity,
                today: today,
                state: CharacterMood.forChat(summary.chat, in: state),
                waiting: waiting.contains(summary.chat.id)
            )
            return ComfortableEntry(row: row, queued: queued, held: held)
        }
    }

    private func threadTree(_ bot: Bot, queued: Set<String>, held: Set<String>) -> some View {
        BotThreadTree(
            bot: bot,
            query: $query,
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

    private var hasVisibleBots: Bool {
        session.state.bots.contains { $0.hidden != true }
    }

    private func openNewSection() {
        Haptics.selection()
        showingNewSection = true
    }

    // MARK: - Data

    /// The reader's activity level, which the roster preview folds by.
    private var activity: ActivityDetail { ActivityDetail(rawValue: activityDetail) ?? .phoneDefault }

    /// How much each row says, from Settings.
    private var density: RosterDensity { RosterDensity(stored: rosterDensity) }

    private var chats: [ChatSummary] {
        let all = rosterSummaries(previews: true)
        guard !query.isEmpty else {
            // rooms live in the strip; the list is bots
            return all.filter { if case .bot = $0.chat { return true } else { return false } }
        }
        let queued = session.state.queuedThreadIds
        return all.filter {
            $0.chat.name.localizedCaseInsensitiveContains(query)
                || $0.chat.subtitle.localizedCaseInsensitiveContains(query)
                || $0.preview.localizedCaseInsensitiveContains(query)
                || matchesThread($0.chat, queued: queued)
        }
    }

    private func matchesThread(_ chat: Chat, queued: Set<String>) -> Bool {
        guard case let .bot(bot) = chat else { return false }
        return !bot.threadGroups(matching: query, queuedThreadIds: queued).isEmpty
    }

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

    private func openAttention(_ entry: AttentionThread) {
        guard let bot = entry.destinationBot(in: session.state) else { return }
        path.append(Chat.bot(bot))
    }

    private func summaries(for bots: [Bot], from all: [ChatSummary]) -> [ChatSummary] {
        let ids = Set(bots.map(\.id))
        return all.filter { summary in
            if case let .bot(bot) = summary.chat { return ids.contains(bot.id) }
            return false
        }
    }

    private func waitingChats(_ approvals: [(threadId: String, message: Message)]) -> Set<String> {
        Set(approvals.compactMap { session.state.chat(forThread: $0.threadId)?.id })
    }

    private func members(of room: Room) -> [Bot] {
        room.memberIds.compactMap { session.state.bot($0) }
    }

    private func rosterIsEmpty(matches: [ChatSummary]) -> Bool {
        if query.isEmpty {
            return session.state.bots.allSatisfy { $0.hidden == true } && session.state.rooms.isEmpty
        }
        return matches.isEmpty && searchHits.isEmpty && !searching
    }

    private func sectionLabel(_ text: Text) -> some View {
        HomeSectionTitle(text: text)
    }
}

// MARK: - Rows and tiles

/// One comfortable row as the list worked it out: the row, and what its
/// thread tree needs beyond the list's own state.
private struct ComfortableEntry: Identifiable, Equatable {
    /// Equatable on everything it draws (the whole bot or room).
    let row: ChatRow
    let queued: Set<String>
    let held: Set<String>

    var id: String { row.chat.id }
}

/// A room and its members as the state holds them, for its tile.
private struct RoomTile: Identifiable, Equatable {
    let room: Room
    let members: [Bot]

    var id: String { room.id }
}

/// A room as a round tile: the first two members' faces stacked, its name
/// beneath. `nil` is the "make one" tile.
///
/// Its members come from the list, which looks them up once, and it is shown
/// `.equatable()`: a tile no longer redraws its two faces on every publish.
struct GroupTile: View, Equatable {
    let room: Room?
    /// The room's members as the state holds them.
    let members: [Bot]

    var body: some View {
        VStack(spacing: 7) {
            ZStack {
                if let room {
                    Circle().fill(Color.seiraTint)
                    faces
                    if room.unread {
                        Circle()
                            .fill(Color.seiraAccent)
                            .frame(width: 10, height: 10)
                            .overlay(Circle().stroke(Color.seiraApp, lineWidth: 2))
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                            .padding(3)
                            .accessibilityLabel("Unread")
                    }
                } else {
                    Circle()
                        .strokeBorder(style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                        .foregroundStyle(Color.seiraInk3)
                    Image(systemName: "plus")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(Color.seiraInk3)
                }
            }
            .frame(width: 64, height: 64)

            Text(room?.name ?? "New group")
                .font(.seira(12, .medium, relativeTo: .caption))
                .foregroundStyle(room == nil ? Color.seiraInk3 : Color.seiraInk)
                .lineLimit(1)
        }
        .frame(width: 76)
        .contentShape(Rectangle())
    }

    @ViewBuilder private var faces: some View {
        if let first = members.first {
            SeiraAvatar(bot: first, size: 34, ringGap: .seiraTint)
                .offset(x: -9, y: -6)
                .accessibilityHidden(true)
        }
        if members.count > 1 {
            SeiraAvatar(bot: members[1], size: 30, ringGap: .seiraTint)
                .padding(2)
                .background(Color.seiraTint, in: Circle())
                .offset(x: 11, y: 9)
                .accessibilityHidden(true)
        }
    }
}

/// One thread that needs the person, from any bot: title, status, and the
/// bot it belongs to, ready to jump straight there. Waiting outranks
/// working, which outranks queued and unread — the same order as the tree.
struct AttentionRow: View {
    let entry: AttentionThread

    @Environment(\.dynamicTypeSize) private var typeSize
    @ScaledMetric(relativeTo: .body) private var markWidth: CGFloat = 24

    /// A scaled title wraps at the accessibility sizes instead of being cut
    /// short.
    private var titleWraps: Bool { typeSize.isAccessibilitySize }

    private var waiting: Bool { entry.task.activity == "waiting-on-you" }
    private var working: Bool { !waiting && (entry.task.busy == true || entry.task.activity == "working") }
    private var queued: Bool { !waiting && !working && entry.task.activity == "queued" }

    private var statusText: String {
        if waiting { return "Waiting on you" }
        if working { return "Working" }
        if queued { return "Queued" }
        return "Unread"
    }

    private var markColor: Color {
        if waiting { return .seiraWarning }
        if queued { return .seiraInk3 }
        return .seiraAccent
    }

    private var markSymbol: String {
        if waiting { return "exclamationmark.circle.fill" }
        return queued ? "clock" : "bell.badge.fill"
    }

    var body: some View {
        HStack(spacing: 12) {
            mark
                .foregroundStyle(markColor)
                .frame(width: markWidth)

            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: entry.task.displayTitle)
                    .font(.seira(15, .medium, relativeTo: .body))
                    .foregroundStyle(Color.seiraInk)
                    .lineLimit(titleWraps ? 3 : 1)
                    .fixedSize(horizontal: false, vertical: titleWraps)
                Text("\(entry.botName) · \(statusText)")
                    .font(.seira(13, relativeTo: .subheadline))
                    .foregroundStyle(waiting ? Color.seiraWarningText : Color.seiraInk2)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, HomeRowMetrics.side)
        .padding(.vertical, 8)
        .frame(minHeight: 56)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(entry.task.displayTitle), \(entry.botName), \(statusText)")
    }

    @ViewBuilder private var mark: some View {
        if working {
            ProgressView().controlSize(.small)
        } else {
            Image(systemName: markSymbol)
                .font(.system(size: 17, weight: .medium))
        }
    }
}

/// A bot or group in comfortable Home: face, name, time, the last line, and
/// a warning-coloured line while it waits on the person.
///
/// Plain values only, shown `.equatable()` like `CompactBotEntry`, so a row
/// whose chat has not moved is skipped when the session publishes.
struct ChatRow: View, Equatable {
    let chat: Chat
    /// A group's members, for its two stacked faces; empty for a bot.
    var members: [Bot] = []
    let preview: String
    let at: Double
    /// The day the list drew on (`RosterDay.today`); see
    /// `CompactBotEntry.today`. "9:15 AM" must still become "Yesterday".
    let today: Date
    var state: CharacterMood = .idle
    var waiting = false

    /// `Chat`'s own `==` compares identity (id and thread) only; a row
    /// draws the whole bot or room, so it compares those.
    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.preview == rhs.preview && lhs.at == rhs.at && lhs.today == rhs.today && lhs.state == rhs.state
            && lhs.waiting == rhs.waiting && lhs.members == rhs.members
            && sameContent(lhs.chat, rhs.chat)
    }

    private static func sameContent(_ lhs: Chat, _ rhs: Chat) -> Bool {
        switch (lhs, rhs) {
        case let (.bot(left), .bot(right)): return left == right
        case let (.room(left), .room(right)): return left == right
        default: return false
        }
    }

    private var detail: String {
        if !preview.isEmpty { return preview }
        return waiting ? String(localized: "Waiting on you") : ""
    }

    var body: some View {
        HStack(spacing: HomeRowMetrics.gap) {
            HomeRowAvatar(chat: chat, members: members, working: chat.busy && !waiting, waiting: waiting)
            HomeRowLabels(
                title: chat.name,
                unread: chat.unread && !chat.busy,
                stamp: RelativeStamp.list(at),
                detail: detail,
                detailWaiting: waiting,
                working: chat.busy && !waiting
            )
        }
        .padding(.horizontal, HomeRowMetrics.side)
        .frame(minHeight: HomeRowMetrics.minHeight)
        .contentShape(Rectangle())
    }
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
