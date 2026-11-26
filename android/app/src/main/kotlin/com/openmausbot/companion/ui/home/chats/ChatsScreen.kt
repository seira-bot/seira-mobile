package com.openmausbot.companion.ui.home.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.BuildConfig
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ChatSummary
import com.openmausbot.companion.core.RosterBotRow
import com.openmausbot.companion.core.RosterDensity
import com.openmausbot.companion.core.RosterRowStatus
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.WorkspacePickerModel
import com.openmausbot.companion.core.chat
import com.openmausbot.companion.core.chatSummaries
import com.openmausbot.companion.core.rosterStatus
import com.openmausbot.companion.ui.BotThreadTree
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.ChangeLookSheet
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.Destination
import com.openmausbot.companion.ui.HapticCue
import com.openmausbot.companion.ui.LiveCallRules
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.LocalHomeHour
import com.openmausbot.companion.ui.NewGroupSheet
import com.openmausbot.companion.ui.RosterEmptyState
import com.openmausbot.companion.ui.RosterLayout
import com.openmausbot.companion.ui.StatusBanner
import com.openmausbot.companion.ui.TactileAction
import com.openmausbot.companion.ui.TaskSheet
import com.openmausbot.companion.ui.TeamMember
import com.openmausbot.companion.ui.TeamRail
import com.openmausbot.companion.ui.WorkspaceSheet
import com.openmausbot.companion.ui.WorkspaceSheetRules
import com.openmausbot.companion.ui.forChat
import com.openmausbot.companion.ui.homeGreetingText
import com.openmausbot.companion.ui.localizedMobileCopy
import com.openmausbot.companion.ui.play
import com.openmausbot.companion.ui.rememberHaptics
import com.openmausbot.companion.ui.rememberPhoneOnline
import com.openmausbot.companion.ui.rememberSnoozeNow
import com.openmausbot.companion.ui.rosterEmpty
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween
import com.openmausbot.companion.ui.updates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WORKSPACE_POLL_MILLIS = 3_000L

private val StringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

/**
 * The Chats tab: greeting, filter chips, the chief of staff, the team rail and
 * the sections of chats. [sectionFilter] limits the list to one department.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatsScreen(
    navigator: CompanionNavigator,
    sectionFilter: String?,
    onNewChat: () -> Unit,
    callsEnabled: Boolean = BuildConfig.LIVE_CALLS,
) {
    val environment = LocalCompanion.current
    val session = environment.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val state by session.state.collectAsState()
    val connection by session.connection.collectAsState()
    val status by session.status.collectAsState()
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    val rosterDensity by environment.chatPreferences.rosterDensity.collectAsState()
    val liveCall by environment.liveCalls.state.collectAsState()
    val compact = rosterDensity == RosterDensity.COMPACT
    val reduced = rememberReducedMotion()

    var filter by rememberSaveable { mutableStateOf(ChatsFilter.ALL) }
    var refreshing by remember { mutableStateOf(false) }
    var expandedBots by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var collapsedFolders by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var creatingThreads by remember { mutableStateOf(emptySet<String>()) }
    var managingThreads by remember { mutableStateOf<Chat?>(null) }
    var changingLookId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingNewGroup by remember { mutableStateOf(false) }
    var showingWorkspaces by remember { mutableStateOf(false) }
    val workspaceUnnamed = stringResource(R.string.workspace_unnamed)
    val workspacePicker = remember(session, connection?.id) {
        WorkspacePickerModel(connection?.id.orEmpty(), session.workspaceSource) { session.connection.value?.id }
    }
    val workspaces by workspacePicker.state.collectAsState()

    LaunchedEffect(connection?.id, status, showingWorkspaces, workspaces.polling) {
        if (status != Session.Status.Live || connection == null) return@LaunchedEffect
        workspacePicker.refresh()
        while (showingWorkspaces || workspaces.polling) {
            delay(WORKSPACE_POLL_MILLIS)
            workspacePicker.refresh()
        }
    }
    LaunchedEffect(workspaces.visible) {
        if (!workspaces.visible) showingWorkspaces = false
    }

    val fleet = remember(
        state.bots, state.rooms, state.messages, state.activeLeafIds, state.pendingEdits, state.pendingQueued,
    ) { state }
    val summaries = remember(fleet, activityDetail) { fleet.chatSummaries(activityDetail) }
    val summariesById = remember(summaries) { summaries.associateBy { it.id } }
    val approvals = remember(fleet) { fleet.pendingApprovals }
    val waiting = remember(fleet, approvals) { RosterLayout.waitingChats(fleet, approvals) }
    val updates = remember(fleet, approvals, activityDetail) { fleet.updates(approvals, activityDetail) }
    val faces = remember(fleet, summaries) {
        summaries.associate { it.id to CharacterMood.forChat(it.chat, fleet) }
    }
    val chief = remember(fleet) {
        fleet.unsectionedChief ?: fleet.bots.firstOrNull { it.hidden != true && it.chiefOfStaff == true }
    }
    val team = remember(fleet, summaries, waiting, faces, chief?.id) {
        val visible = summaries.mapNotNull { (it.chat as? Chat.BotChat)?.bot }.filter { it.id != chief?.id }
        visible.map { bot ->
            val rowStatus = bot.rosterStatus(hasPendingCard = bot.id in waiting)
            TeamMember(
                bot = bot,
                waiting = rowStatus == RosterRowStatus.WAITING_ON_YOU,
                working = rowStatus == RosterRowStatus.WORKING,
                face = faces[bot.id] ?: CharacterMood.IDLE,
            )
        }.sortedBy { if (it.waiting) 0 else if (it.working) 1 else 2 }
    }
    val sections = remember(fleet, filter, sectionFilter) { ChatsLayout.sections(fleet, filter, sectionFilter) }
    val needsYou = remember(updates) { ChatsHeroRules.needsYou(updates) }
    val canTalk = LiveCallRules.offersCall(liveCall, state.liveCall, callsEnabled)
    val queuedThreadIds = fleet.queuedThreadIds

    val toggleBot: (String) -> Unit = { botId ->
        haptics.play(HapticCue.SELECT)
        expandedBots = if (botId in expandedBots) expandedBots - botId else expandedBots + botId
    }
    val toggleFolder: (String) -> Unit = { key ->
        haptics.play(HapticCue.SELECT)
        collapsedFolders = if (key in collapsedFolders) collapsedFolders - key else collapsedFolders + key
    }
    val createThread: (Bot) -> Unit = { bot ->
        if (bot.id !in creatingThreads) {
            creatingThreads = creatingThreads + bot.id
            scope.launch {
                try {
                    val created = session.createTask(bot, title = null)
                    if (created != null) {
                        navigator.open(Chat.BotChat(created))
                    } else if (session.actionError == null) {
                        session.actionError = "Couldn't create a thread. Check the connection and try again."
                    }
                } finally {
                    creatingThreads = creatingThreads - bot.id
                }
            }
        }
    }
    val openSummary: (ChatSummary) -> Unit = { summary ->
        navigator.open(environment.chatPreferences.restoringThread(summary.chat, connection?.id))
    }
    val openBot: (Bot) -> Unit = { bot ->
        summariesById[bot.id]?.let(openSummary) ?: navigator.open(Chat.BotChat(bot))
    }
    val refresh: () -> Unit = {
        if (!refreshing) {
            scope.launch {
                refreshing = true
                session.refresh()
                refreshing = false
            }
        }
    }
    val phoneOnline = rememberPhoneOnline()
    val startNewGroup = {
        haptics.play(TactileAction.START_NEW_GROUP)
        showingNewGroup = true
    }
    val greetingHour = LocalHomeHour.current
    val greeting = homeGreetingText(remember { greetingHour() }, null)
    val showHero = chief != null && sectionFilter == null && filter == ChatsFilter.ALL
    val showRail = showHero && team.isNotEmpty()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("roster-list"),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "greeting") { ChatsGreeting(greeting) }
            item(key = "filters") {
                ChatsFilterBar(
                    selected = filter,
                    onSelect = {
                        haptics.play(HapticCue.SELECT)
                        filter = it
                    },
                    onSearch = {
                        haptics.play(TactileAction.OPEN_SEARCH)
                        navigator.push(Destination.Search)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "banner") { StatusBanner() }
            if (showHero && chief != null) {
                item(key = "hero") {
                    val chiefStatus = chief.rosterStatus(hasPendingCard = chief.id in waiting)
                    val line = needsYou?.let { update ->
                        if (update.line.isBlank()) {
                            stringResource(R.string.chats_hero_waiting_bare, update.chat.name)
                        } else {
                            stringResource(R.string.chats_hero_waiting, update.chat.name, update.line)
                        }
                    } ?: stringResource(R.string.chats_hero_quiet)
                    ChatsHero(
                        bot = chief,
                        role = localizedMobileCopy("Chief of Staff"),
                        line = line,
                        busy = chiefStatus == RosterRowStatus.WORKING,
                        face = faces[chief.id] ?: CharacterMood.IDLE,
                        canTalk = canTalk,
                        onOpen = { openBot(chief) },
                        onTalk = {
                            haptics.play(HapticCue.SELECT)
                            environment.callUi.request(chief.id)
                            openBot(chief)
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (showRail) {
                item(key = "team") {
                    TeamRail(
                        members = team,
                        expanded = false,
                        onToggle = {},
                        onOpen = openBot,
                        onCreateThread = createThread,
                        onManageThreads = { bot -> managingThreads = Chat.BotChat(bot) },
                        onChangeLook = { bot -> changingLookId = bot.id },
                        statusDots = true,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (workspaces.visible) {
                item(key = "workspace") {
                    ChatsWorkspaceChip(
                        name = workspaces.current?.let { WorkspaceSheetRules.name(it) ?: workspaceUnnamed }.orEmpty(),
                        onClick = {
                            haptics.play(HapticCue.SELECT)
                            showingWorkspaces = true
                        },
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                    )
                }
            }

            sections.forEach { section ->
                item(key = "header-${section.key}") {
                    val isGroups = section.kind == ChatsSection.Kind.GROUPS
                    ChatsSectionHeader(
                        title = when (section.kind) {
                            ChatsSection.Kind.CHATS -> stringResource(R.string.home_chats)
                            ChatsSection.Kind.DEPARTMENT -> section.name.orEmpty()
                            ChatsSection.Kind.GROUPS -> stringResource(R.string.mobile_groups_ae9629f4)
                        },
                        actionLabel = if (isGroups) stringResource(R.string.mobile_new_group_f9850c0b) else null,
                        onAction = if (isGroups) startNewGroup else null,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(section.bots, key = { "${section.key}-bot-${it.id}" }) { bot ->
                    val summary = summariesById[bot.id]
                    if (summary != null) {
                        BotEntry(
                            bot = bot,
                            summary = summary,
                            face = faces[bot.id] ?: CharacterMood.IDLE,
                            hasPendingCard = bot.id in waiting,
                            compact = compact,
                            expanded = bot.id in expandedBots,
                            collapsedFolders = collapsedFolders,
                            creating = bot.id in creatingThreads,
                            queuedThreadIds = queuedThreadIds,
                            reduced = reduced,
                            onOpenRow = { openSummary(summary) },
                            onToggle = { toggleBot(bot.id) },
                            onToggleFolder = toggleFolder,
                            onCreate = { createThread(bot) },
                            onManage = { managingThreads = Chat.BotChat(bot) },
                            onOpen = navigator::open,
                            onCollapse = { expandedBots = expandedBots - bot.id },
                        )
                    }
                }
                items(section.rooms, key = { "${section.key}-room-${it.id}" }) { room ->
                    val summary = summariesById[room.id]
                    ChatsRoomRow(
                        room = room,
                        members = remember(fleet, room) { RosterLayout.memberBots(fleet, room) },
                        preview = summary?.preview.orEmpty(),
                        lastActivity = summary?.lastActivity ?: 0.0,
                        waiting = room.id in waiting,
                        compact = compact,
                        onOpen = { navigator.open(Chat.RoomChat(room)) },
                    )
                }
            }

            if (ChatsLayout.isEmpty(sections)) {
                if (summaries.isEmpty()) {
                    item(key = "empty") {
                        RosterEmptyState(
                            empty = rosterEmpty(status, phoneOnline),
                            refreshing = refreshing,
                            onNewChat = onNewChat,
                            onPairComputer = { session.beginPairing() },
                            onRefresh = refresh,
                            modifier = Modifier.fillParentMaxHeight(0.6f),
                        )
                    }
                } else {
                    item(key = "filter-empty") {
                        Text(
                            text = stringResource(R.string.chats_filter_empty),
                            fontSize = 14.sp,
                            color = Seira.colors.ink3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }

    if (showingNewGroup) {
        NewGroupSheet(
            onCreated = { room ->
                showingNewGroup = false
                navigator.open(Chat.RoomChat(room))
            },
            onDismiss = { showingNewGroup = false },
        )
    }

    if (showingWorkspaces && workspaces.visible) {
        WorkspaceSheet(state = workspaces, onDismiss = { showingWorkspaces = false })
    }

    changingLookId?.let { id ->
        state.bot(id)?.let { bot -> ChangeLookSheet(bot = bot, onDismiss = { changingLookId = null }) }
    }

    managingThreads?.let { chat ->
        TaskSheet(
            chat = chat,
            onDismiss = { managingThreads = null },
            onSelectTask = { target ->
                managingThreads = null
                session.state.value.chat(target)?.let(navigator::open)
            },
        )
    }
}

@Composable
private fun BotEntry(
    bot: Bot,
    summary: ChatSummary,
    face: CharacterMood,
    hasPendingCard: Boolean,
    compact: Boolean,
    expanded: Boolean,
    collapsedFolders: Set<String>,
    creating: Boolean,
    queuedThreadIds: Set<String>,
    reduced: Boolean,
    onOpenRow: () -> Unit,
    onToggle: () -> Unit,
    onToggleFolder: (String) -> Unit,
    onCreate: () -> Unit,
    onManage: () -> Unit,
    onOpen: (Chat) -> Unit,
    onCollapse: () -> Unit,
) {
    val now = rememberSnoozeNow(bot.tasks.orEmpty())
    val row = RosterBotRow(bot, hasPendingCard, queuedThreadIds, now)
    val drops = row.dropsExpansion(expanded)
    LaunchedEffect(drops) { if (drops) onCollapse() }
    Column(modifier = Modifier.fillMaxWidth()) {
        ChatsBotRow(
            bot = bot,
            row = row,
            preview = summary.preview,
            lastActivity = summary.lastActivity,
            face = face,
            compact = compact,
            expanded = expanded,
            creating = creating,
            onOpen = onOpenRow,
            onToggle = onToggle,
            onNewThread = onCreate,
            onManage = onManage,
        )
        AnimatedVisibility(
            visible = expanded && row.showsThreadControl,
            enter = expandVertically(seiraTween(SeiraMotion.BASE, reduced)) + fadeIn(seiraTween(SeiraMotion.BASE, reduced)),
            exit = shrinkVertically(seiraTween(SeiraMotion.BASE, reduced)) + fadeOut(seiraTween(SeiraMotion.FAST, reduced)),
        ) {
            BotThreadTree(
                bot = bot,
                query = "",
                expanded = true,
                collapsedFolders = collapsedFolders,
                creating = creating,
                onToggle = onToggle,
                onToggleFolder = onToggleFolder,
                onCreate = onCreate,
                onManage = onManage,
                onOpen = onOpen,
                queuedThreadIds = queuedThreadIds,
            )
        }
    }
}
