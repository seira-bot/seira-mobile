// The compact home list: one row per bot and per group, without the last message.
//
// The phone's version of the desktop sidebar's compact density: name, role
// and status on one line, a crown after a Chief of Staff's name, and a
// thread list only under a bot that has more than one thread — opened from
// its "› N" control and lined up with the bot's name. What each row shows
// is decided in CompanionCore's `CompactBotRow`; this file is layout.
import SwiftUI
import CompanionCore

/// Rhythm shared by bot rows, group rows and the thread lines beneath a
/// bot, so a thread starts exactly where its bot's name does.
enum CompactRosterMetrics {
    static let nameInset = HomeRowMetrics.nameInset
    static let trailing = HomeRowMetrics.side
}

/// One bot on one line, with its threads beneath it when opened.
///
/// Drawn only from values the list hands it, and shown `.equatable()`, so a
/// row whose bot has not moved is skipped. Under a busy fleet the session
/// publishes many times a second; a row that observed it, or that read the
/// list's state through `Binding(get:set:)` (a new location every render),
/// ran its body for every visible bot on every publish. What the row
/// changes goes back to the list through the callbacks instead.
struct CompactBotEntry: View, Equatable {
    /// The bot as the state holds it now.
    let bot: Bot
    /// Its face (`MausState.forChat`), worked out by the list.
    let face: MausState
    /// When the bot's current thread last moved, from the roster summary.
    let lastActivity: Double
    /// The day the list drew on (`RosterDay.today`). The stamps say "9:15 AM",
    /// "Yesterday" or a weekday against the clock, so a new day must redraw
    /// a row whose bot has not moved.
    let today: Date
    /// An unanswered approval or question sits in one of its threads.
    let hasPendingCard: Bool
    /// Threads holding a held send: `CompanionState.queuedThreadIds`.
    let queuedThreadIds: Set<String>
    /// The roster search: a match lists the bot's threads beneath it.
    let query: String
    /// Its thread list is open ("› N").
    let expanded: Bool
    /// Closed folders, keyed `botID:folderID`, for every bot.
    let collapsedFolders: Set<String>
    /// A thread asked for from this row is being made.
    let creating: Bool
    /// For the row's actions only. A plain reference is not observed, so
    /// nothing the session publishes redraws the row by itself.
    let session: Session
    /// Opens or closes the thread list, from the list's own state.
    let toggleExpanded: () -> Void
    let collapse: () -> Void
    let toggleFolder: (String) -> Void
    /// Marks a thread as being made from this row; false when one already is.
    let beginCreating: () -> Bool
    let endCreating: () -> Void
    /// The row itself: the bot's last-opened thread, as before.
    let openRow: () -> Void
    /// An exact thread, or one just created.
    let open: (Chat) -> Void
    let manage: (Chat) -> Void

    /// Everything the row draws. The callbacks are left out: they write
    /// the list's own state, which a skipped row still reaches.
    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.bot == rhs.bot && lhs.face == rhs.face && lhs.lastActivity == rhs.lastActivity
            && lhs.today == rhs.today && lhs.hasPendingCard == rhs.hasPendingCard && lhs.queuedThreadIds == rhs.queuedThreadIds
            && lhs.query == rhs.query && lhs.expanded == rhs.expanded
            && lhs.collapsedFolders == rhs.collapsedFolders && lhs.creating == rhs.creating
            && lhs.session === rhs.session
    }

    /// Wakes the list when a timed snooze ends, so the count and the list
    /// fold that thread back in without waiting for a snapshot.
    @State private var snoozeTick = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var searching: Bool {
        !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        let _ = snoozeTick
        let row = CompactBotRow(
            bot: bot, hasPendingCard: hasPendingCard, queuedThreadIds: queuedThreadIds, creatingThread: creating
        )
        VStack(alignment: .leading, spacing: 0) {
            rowLine(bot, row)
            if row.listsThreads(expanded: expanded, searching: searching) {
                threadList(bot, row: row, queued: queuedThreadIds)
            }
        }
        .snoozeExpiryTick(bot.visibleTasks.nextSnoozeExpiry(), tick: $snoozeTick)
        // A list opened with "› N" closes when the control goes away (the
        // bot is down to one thread), so it cannot reopen by itself when the
        // bot gains a thread again. Also clears a stale open state carried
        // over from comfortable, where every bot has a Threads row.
        .onValueChange(of: row.showsThreadControl, initial: true) { shows in
            if !shows && expanded { collapse() }
        }
    }

    // MARK: - The bot's line

    private func rowLine(_ bot: Bot, _ row: CompactBotRow) -> some View {
        HStack(spacing: 0) {
            Button(action: openRow) { rowContent(bot, row) }
                .buttonStyle(HomeRowButtonStyle())
                .contextMenu {
                    // A single-thread bot shows no thread list to end in "New
                    // thread", so the home list offers it here — for every bot.
                    Button { createThread(for: bot) } label: {
                        Label("New thread", systemImage: "square.and.pencil")
                    }
                    .disabled(creating)
                    Button { manage(.bot(bot)) } label: {
                        Label("Manage threads", systemImage: "list.bullet")
                    }
                }
                .accessibilityIdentifier("chat-row.\(bot.id)")

            if row.showsThreadControl {
                threadControl(bot, count: row.threadCount)
                    // the control's own padding lands its glyph on the
                    // 20pt edge that times in other rows end on
                    .padding(.trailing, CompactRosterMetrics.trailing - 10)
            }
        }
    }

    private func rowContent(_ bot: Bot, _ row: CompactBotRow) -> some View {
        // the spinner also stands for a thread being made
        let spinnerLabel: LocalizedStringKey = row.status == .working ? "Working" : "Creating…"
        let detail = row.showsWaiting ? String(localized: "Waiting on you") : bot.title
        return HStack(spacing: HomeRowMetrics.gap) {
            SeiraAvatar(
                bot: bot,
                size: HomeRowMetrics.avatar,
                ring: row.status == .working ? .working : .none,
                waiting: row.showsWaiting
            )
            .accessibilityHidden(true)
            HomeRowLabels(
                title: bot.name,
                showsCrown: row.showsChiefBadge,
                unread: row.showsUnreadDot,
                stamp: row.showsTime ? RelativeStamp.list(lastActivity) : "",
                detail: detail,
                detailWaiting: row.showsWaiting,
                working: row.showsSpinner,
                spinnerLabel: spinnerLabel
            )
        }
        .padding(.leading, HomeRowMetrics.side)
        .padding(.trailing, row.showsThreadControl ? 0 : HomeRowMetrics.side)
        .frame(minHeight: HomeRowMetrics.minHeight)
        .contentShape(Rectangle())
    }

    /// "› N": the bot's threads, opened in place.
    private func threadControl(_ bot: Bot, count: Int) -> some View {
        let listed = searching || expanded
        return Button {
            Haptics.selection()
            withSeiraAnimation(SeiraMotion.base) { toggleExpanded() }
        } label: {
            HStack(spacing: 3) {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .rotationEffect(.degrees(listed && !reduceMotion ? 90 : 0))
                Text("\(count)")
                    .font(.seira(13, .medium, relativeTo: .subheadline))
                    .monospacedDigit()
            }
            .foregroundStyle(Color.seiraInk3)
            .padding(.horizontal, 10)
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(searching)
        .accessibilityLabel("\(bot.name)'s threads")
        .accessibilityValue(listed ? "Expanded, \(count) threads" : "Collapsed, \(count) threads")
        .accessibilityIdentifier("threads-toggle.\(bot.id)")
    }

    // MARK: - The threads beneath it

    private func threadList(_ bot: Bot, row: CompactBotRow, queued: Set<String>) -> some View {
        // A name match lists everything; otherwise only what matched.
        let groups = bot.threadGroups(
            matching: bot.name.localizedCaseInsensitiveContains(query) ? "" : query,
            queuedThreadIds: queued
        )
        let labelsUnfiled = groups.labelsUnfiledThreads
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(groups) { group in
                if let folder = group.project {
                    folderHeader(folder)
                    if searching || !collapsedFolders.contains(folderKey(folder)) {
                        threadLines(group.tasks, bot: bot)
                    }
                } else {
                    if labelsUnfiled {
                        unfiledLabel(bot)
                    }
                    threadLines(group.tasks, bot: bot)
                }
            }
            if row.endsWithNewThread(expanded: expanded, searching: searching) {
                newThreadLine(bot)
            }
        }
        .padding(.leading, CompactRosterMetrics.nameInset)
        .padding(.trailing, CompactRosterMetrics.trailing)
        .padding(.bottom, 4)
    }

    private func folderKey(_ folder: BotProject) -> String { "\(bot.id):\(folder.id)" }

    private func folderHeader(_ folder: BotProject) -> some View {
        let open = searching || !collapsedFolders.contains(folderKey(folder))
        return Button {
            toggleFolder(folderKey(folder))
        } label: {
            HStack(spacing: 6) {
                if let emoji = folder.emoji, !emoji.isEmpty {
                    Text(verbatim: emoji)
                } else {
                    Image(systemName: "folder")
                }
                Text(verbatim: folder.name)
                    .lineLimit(1)
                Image(systemName: "chevron.right")
                    .font(.system(size: 11, weight: .semibold))
                    .rotationEffect(.degrees(open && !reduceMotion ? 90 : 0))
                Spacer(minLength: 0)
            }
            .font(.seira(13, .medium, relativeTo: .subheadline))
            .foregroundStyle(Color.seiraInk3)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(searching)
        .accessibilityLabel(Text(verbatim: folder.name))
        .accessibilityValue(open ? "Expanded" : "Collapsed")
        .accessibilityIdentifier("folder.\(bot.id).\(folder.id)")
    }

    /// Unfiled threads start on the same edge as a folder's, so after a
    /// folder they need a label of their own — the desktop's "Threads".
    private func unfiledLabel(_ bot: Bot) -> some View {
        Text("Threads")
            .font(.seira(13, .medium, relativeTo: .subheadline))
            .foregroundStyle(Color.seiraInk3)
            .padding(.top, 10)
            .padding(.bottom, 2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
            .accessibilityIdentifier("unfiled-threads.\(bot.id)")
    }

    private func threadLines(_ tasks: [BotTask], bot: Bot) -> some View {
        ForEach(tasks, id: \.threadId) { task in
            if let projected = bot.projected(forThread: task.threadId) {
                NavigationLink(value: Chat.bot(projected)) {
                    CompactThreadLine(task: task, queued: queuedThreadIds.contains(task.threadId), today: today)
                }
                .buttonStyle(.plain)
                .contextMenu {
                    Button {
                        let pinned = task.pinned != true
                        Task { await session.setTaskPinned(task, pinned: pinned, in: .bot(bot)) }
                    } label: {
                        Label(task.pinned == true ? "Unpin" : "Pin", systemImage: task.pinned == true ? "pin.slash" : "pin")
                    }
                }
                .accessibilityIdentifier("thread.\(task.threadId)")
            }
        }
    }

    private func newThreadLine(_ bot: Bot) -> some View {
        Button { createThread(for: bot) } label: {
            HStack(spacing: 6) {
                Image(systemName: "plus")
                    .font(.system(size: 12, weight: .semibold))
                Text("New thread")
                if creating {
                    ProgressView().controlSize(.small)
                }
                Spacer(minLength: 0)
            }
            .font(.seira(15, .medium, relativeTo: .subheadline))
            .foregroundStyle(Color.seiraAccentText)
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(creating)
        .accessibilityLabel("New thread with \(bot.name)")
        .accessibilityIdentifier("new-thread.\(bot.id)")
    }

    private func createThread(for bot: Bot) {
        guard beginCreating() else { return }
        Task {
            defer { endCreating() }
            if let created = await session.createRosterThread(for: bot) {
                open(.bot(created))
            }
        }
    }
}

/// One thread under its bot: title, then its status and when it last moved.
struct CompactThreadLine: View {
    let task: BotTask
    /// A held send, from the client's queue state (never in `activity`).
    var queued = false
    /// The day its stamp was worded on; see `CompactBotEntry.today`.
    let today: Date

    @Environment(\.dynamicTypeSize) private var typeSize

    private enum Mark {
        case waitingOnYou, waitingOnTeammate, working, queued

        var words: LocalizedStringKey {
            switch self {
            case .waitingOnYou: "Waiting on you"
            case .waitingOnTeammate: "Waiting on teammate"
            case .working: "Working"
            case .queued: "Queued"
            }
        }
    }

    /// Ordered as `BotThreadRow` orders it: the person, a teammate wait
    /// (a quiet clock, never a spinner), work, then a held send.
    private var mark: Mark? {
        if task.activity == "waiting-on-you" { return .waitingOnYou }
        if task.isWaitingOnTeammate { return .waitingOnTeammate }
        if task.isWorking { return .working }
        if task.activity == "queued" || queued { return .queued }
        return nil
    }

    private var dimmed: Bool {
        (task.isClosed || task.isArchived) && mark == nil && task.unread != true
    }

    private var stamp: String { RelativeStamp.list(task.listStamp) }

    var body: some View {
        Group {
            if typeSize.isAccessibilitySize {
                // the title gets the whole width; its marks and time follow
                VStack(alignment: .leading, spacing: 2) {
                    title.lineLimit(3).fixedSize(horizontal: false, vertical: true)
                    marks
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                HStack(spacing: 6) {
                    title.lineLimit(1).layoutPriority(1)
                    Spacer(minLength: 8)
                    marks
                }
            }
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilitySummary)
        .accessibilityValue(task.isArchived ? "Archived" : dimmed ? "Closed" : "")
    }

    private var title: some View {
        HStack(spacing: 6) {
            Text(verbatim: task.displayTitle)
                .font(.seira(15, task.unread == true ? .semibold : .regular, relativeTo: .subheadline))
                .foregroundStyle(dimmed ? Color.seiraInk3 : Color.seiraInk)
            if task.pinned == true {
                Image(systemName: "pin.fill")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.seiraInk3)
            }
        }
    }

    /// Unread, then the live mark, then the time — or the spinner in its place.
    private var marks: some View {
        HStack(spacing: 6) {
            if task.unread == true {
                Circle()
                    .fill(Color.seiraAccent)
                    .frame(width: 8, height: 8)
            }
            switch mark {
            case .waitingOnYou:
                Image(systemName: "hand.raised.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.seiraWarning)
            case .waitingOnTeammate, .queued:
                Image(systemName: "clock")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.seiraInk3)
            case .working, nil:
                EmptyView()
            }
            if mark == .working {
                // the spinner stands where the time was, as on the bot's line
                ProgressView().controlSize(.small)
            } else if !stamp.isEmpty {
                Text(verbatim: stamp)
                    .font(.seira(12, relativeTo: .caption))
                    .monospacedDigit()
                    .foregroundStyle(Color.seiraInk3)
            }
        }
        .fixedSize()
    }

    private var accessibilitySummary: Text {
        var parts = [Text(verbatim: task.displayTitle)]
        if let mark { parts.append(Text(mark.words)) }
        if task.unread == true { parts.append(Text("Unread")) }
        if task.pinned == true { parts.append(Text("Pinned")) }
        if !stamp.isEmpty { parts.append(Text(verbatim: stamp)) }
        return parts.dropFirst().reduce(parts[0]) { $0 + Text(verbatim: ", ") + $1 }
    }
}

/// A group in the list: two of its members' faces, stacked, then its name.
///
/// Shown `.equatable()`, so a group whose room and members have not moved
/// does not redraw its two faces on every publish.
struct CompactRoomRow: View, Equatable {
    let room: Room
    /// Its members as the state holds them, looked up by the list rather
    /// than by observing the session from every row.
    let members: [Bot]
    let lastActivity: Double
    /// The day its stamp was worded on; see `CompactBotEntry.today`.
    let today: Date
    /// An unanswered approval or question sits in the group's thread.
    var waiting = false

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.room == rhs.room && lhs.members == rhs.members && lhs.lastActivity == rhs.lastActivity
            && lhs.today == rhs.today && lhs.waiting == rhs.waiting
    }

    private var busy: Bool { room.busyBotId != nil }

    var body: some View {
        HStack(spacing: HomeRowMetrics.gap) {
            HomeRoomFaces(members: members)
            HomeRowLabels(
                title: room.name,
                unread: room.unread && !busy,
                stamp: busy ? "" : RelativeStamp.list(lastActivity),
                detail: waiting ? String(localized: "Waiting on you") : Chat.room(room).subtitle,
                detailWaiting: waiting,
                working: busy && !waiting
            )
        }
        .padding(.horizontal, HomeRowMetrics.side)
        .frame(minHeight: HomeRowMetrics.minHeight)
        .contentShape(Rectangle())
    }
}

/// The Chief of Staff mark after a bot's name: the desktop's crown. It
/// scales with the name, so it keeps its default proportion to it at every
/// text size instead of outgrowing it at the largest ones.
struct ChiefBadge: View {
    /// Caption's size at the default text size, scaled as the name's body is.
    @ScaledMetric(relativeTo: .body) private var size: CGFloat = 12

    var body: some View {
        Image(systemName: "crown.fill")
            .font(.system(size: size))
            .foregroundStyle(Color.seiraInk2)
            .accessibilityLabel("Chief of Staff")
            .accessibilityIdentifier("chief-badge")
    }
}

/// The day a roster render words its stamps on. The compact rows no longer
/// redraw on every publish, so the list hands them this instead: a row whose
/// bot has not moved still redraws once the day turns over.
enum RosterDay {
    static var today: Date { Calendar.current.startOfDay(for: Date()) }
}
