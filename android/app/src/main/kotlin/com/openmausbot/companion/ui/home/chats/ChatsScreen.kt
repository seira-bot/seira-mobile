package com.openmausbot.companion.ui.home.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.WorkspacePickerModel
import com.openmausbot.companion.core.chat
import com.openmausbot.companion.core.chatSummaries
import com.openmausbot.companion.ui.BotThreadTree
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.ChangeLookSheet
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.Destination
import com.openmausbot.companion.ui.HapticCue
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.NewGroupSheet
import com.openmausbot.companion.ui.RosterEmptyState
import com.openmausbot.companion.ui.RosterLayout
import com.openmausbot.companion.ui.StatusBanner
import com.openmausbot.companion.ui.TactileAction
import com.openmausbot.companion.ui.TaskSheet
import com.openmausbot.companion.ui.WorkspaceSheet
import com.openmausbot.companion.ui.WorkspaceSheetRules
import com.openmausbot.companion.ui.forChat
import com.openmausbot.companion.ui.play
import com.openmausbot.companion.ui.rememberHaptics
import com.openmausbot.companion.ui.rememberPhoneOnline
import com.openmausbot.companion.ui.rememberSnoozeNow
import com.openmausbot.companion.ui.rosterEmpty
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val WORKSPACE_POLL_MILLIS = 3_000L

/**
 * The Chats tab. Simple mode shows the bots as tiles; Advanced mode shows one
 * row per bot with its threads. Departments fold, and [sectionFilter] limits the
 * list to one of them.
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
    val advanced by environment.chatPreferences.advancedMode.collectAsState()
    val reduced = rememberReducedMotion()

    var refreshing by remember { mutableStateOf(false) }
    var folded by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var expandedBots by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var collapsedFolders by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var creatingThreads by remember { mutableStateOf(emptySet<String>()) }
    var managingThreads by remember { mutableStateOf<Chat?>(null) }
    var changingLookId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingNewGroup by remember { mutableStateOf(false) }
    var showingWorkspaces by remember { mutableStateOf(false) }
    var menuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var addBotsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var newBotFor by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingBot by remember { mutableStateOf(false) }
    var botFailed by remember { mutableStateOf(false) }
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
        state.declaredSections,
    ) { state }
    val summaries = remember(fleet, activityDetail) { fleet.chatSummaries(activityDetail) }
    val summariesById = remember(summaries) { summaries.associateBy { it.id } }
    val approvals = remember(fleet) { fleet.pendingApprovals }
    val waiting = remember(fleet, approvals) { RosterLayout.waitingChats(fleet, approvals) }
    val faces = remember(fleet, summaries) {
        summaries.associate { it.id to CharacterMood.forChat(it.chat, fleet) }
    }
    val layout = remember(fleet, advanced, sectionFilter) { HomeLayoutRules.layout(fleet, advanced, sectionFilter) }
    val queuedThreadIds = fleet.queuedThreadIds

    LaunchedEffect(layout.groups.map(HomeGroup::key), sectionFilter) {
        if (sectionFilter == null && fleet.bots.isNotEmpty()) {
            val next = FoldRules.prune(folded, layout.groups.mapTo(mutableSetOf(), HomeGroup::key))
            if (next != folded) folded = next
        }
    }

    val toggleGroup: (String) -> Unit = { key ->
        haptics.play(HapticCue.SELECT)
        folded = FoldRules.toggle(folded, key)
    }
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
    val openSearch = {
        haptics.play(TactileAction.OPEN_SEARCH)
        navigator.push(Destination.Search)
    }
    val openDepartmentMenu: (String) -> Unit = { name ->
        haptics.play(HapticCue.SELECT)
        menuFor = name
    }
    val openAddBots: (String) -> Unit = { name ->
        haptics.play(HapticCue.SELECT)
        addBotsFor = name
    }
    val statusOf: (Bot) -> HomeDot = { bot ->
        HomeDotRules.of(bot, RosterBotRow(bot, hasPendingCard = bot.id in waiting).status)
    }
    val groupStart = if (advanced) 16.dp else 20.dp

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
            item(key = "banner") { StatusBanner() }
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
            if (layout.department != null) {
                item(key = "show-all") {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        ShowAllChatsButton(onClick = {
                            haptics.play(HapticCue.SELECT)
                            navigator.showChats(null)
                        })
                    }
                }
            }
            if (advanced && layout.department == null) {
                item(key = "search") { SearchRow(onClick = openSearch) }
            }
            layout.chief?.let { chief ->
                item(key = "chief-${chief.id}") {
                    val summary = summariesById[chief.id]
                    AdvancedBotEntry(
                        bot = chief,
                        chief = true,
                        summary = summary,
                        face = faces[chief.id] ?: CharacterMood.IDLE,
                        dot = statusOf(chief),
                        hasPendingCard = chief.id in waiting,
                        expanded = chief.id in expandedBots,
                        collapsedFolders = collapsedFolders,
                        creating = chief.id in creatingThreads,
                        queuedThreadIds = queuedThreadIds,
                        reduced = reduced,
                        onOpenRow = { openBot(chief) },
                        onToggle = { toggleBot(chief.id) },
                        onToggleFolder = toggleFolder,
                        onCreate = { createThread(chief) },
                        onManage = { managingThreads = Chat.BotChat(chief) },
                        onOpen = navigator::open,
                    )
                }
            }

            layout.groups.forEach { group ->
                val open = FoldRules.isOpen(folded, group.key)
                val foldable = group.kind != HomeGroupKind.BOTS && (advanced || group.kind == HomeGroupKind.DEPARTMENT)
                item(key = "header-${group.key}") {
                    val name = group.name
                    GroupHeader(
                        title = when (group.kind) {
                            HomeGroupKind.BOTS -> stringResource(R.string.home2_bots)
                            HomeGroupKind.DEPARTMENT -> name.orEmpty()
                            HomeGroupKind.GROUPS -> stringResource(R.string.home2_group_chats)
                        },
                        foldable = foldable,
                        open = open,
                        onToggle = { toggleGroup(group.key) },
                        tag = "group-header.${group.key}",
                        startPadding = groupStart,
                        modifier = Modifier.padding(top = 4.dp),
                        trailing = {
                            when (group.kind) {
                                HomeGroupKind.BOTS -> if (!advanced) {
                                    HeaderIconButton(
                                        icon = SeiraIcons.Search,
                                        label = stringResource(R.string.home2_search),
                                        tag = "chats-search",
                                        onClick = openSearch,
                                    )
                                }
                                HomeGroupKind.DEPARTMENT -> if (name != null) {
                                    HeaderIconButton(
                                        icon = SeiraIcons.MoreHorizontal,
                                        label = stringResource(R.string.home2_department_actions, name),
                                        tag = "department-actions.$name",
                                        onClick = { openDepartmentMenu(name) },
                                    )
                                    if (advanced) {
                                        HeaderIconButton(
                                            icon = SeiraIcons.Plus,
                                            label = stringResource(R.string.home2_department_add_bots_label, name),
                                            tag = "department-add.$name",
                                            onClick = { openAddBots(name) },
                                        )
                                    }
                                }
                                HomeGroupKind.GROUPS -> HeaderIconButton(
                                    icon = SeiraIcons.Plus,
                                    label = stringResource(R.string.home2_new_group),
                                    tag = "groups-new",
                                    onClick = startNewGroup,
                                )
                            }
                        },
                    )
                }
                if (!open) return@forEach

                if (group.kind == HomeGroupKind.GROUPS) {
                    items(group.rooms.size, key = { "${group.key}-room-${group.rooms[it].id}" }) { index ->
                        val room = group.rooms[index]
                        val summary = summariesById[room.id]
                        GroupChatRow(
                            room = room,
                            members = remember(fleet, room) { RosterLayout.memberBots(fleet, room) },
                            preview = summary?.preview.orEmpty(),
                            lastActivity = summary?.lastActivity ?: 0.0,
                            waiting = room.id in waiting,
                            advanced = advanced,
                            onOpen = { navigator.open(Chat.RoomChat(room)) },
                        )
                    }
                    return@forEach
                }

                if (group.kind == HomeGroupKind.DEPARTMENT && group.bots.isEmpty()) {
                    item(key = "empty-${group.key}") {
                        Text(
                            text = stringResource(R.string.home2_department_empty),
                            fontSize = 14.sp,
                            color = Seira.colors.ink3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(start = groupStart + 4.dp, end = 16.dp, top = 12.dp)
                                .testTag("department-empty.${group.name}"),
                        )
                    }
                    return@forEach
                }

                if (advanced) {
                    items(group.bots.size, key = { "${group.key}-bot-${group.bots[it].id}" }) { index ->
                        val bot = group.bots[index]
                        AdvancedBotEntry(
                            bot = bot,
                            chief = bot.chiefOfStaff == true,
                            summary = summariesById[bot.id],
                            face = faces[bot.id] ?: CharacterMood.IDLE,
                            dot = statusOf(bot),
                            hasPendingCard = bot.id in waiting,
                            expanded = bot.id in expandedBots,
                            collapsedFolders = collapsedFolders,
                            creating = bot.id in creatingThreads,
                            queuedThreadIds = queuedThreadIds,
                            reduced = reduced,
                            onOpenRow = { openBot(bot) },
                            onToggle = { toggleBot(bot.id) },
                            onToggleFolder = toggleFolder,
                            onCreate = { createThread(bot) },
                            onManage = { managingThreads = Chat.BotChat(bot) },
                            onOpen = navigator::open,
                        )
                    }
                } else {
                    val rows = HomeLayoutRules.tileRows(group.bots)
                    items(rows.size, key = { "${group.key}-tiles-${rows[it].first().id}" }) { index ->
                        TileRow(rows[index]) { bot, modifier ->
                            BotTile(
                                bot = bot,
                                chief = bot.chiefOfStaff == true,
                                dot = statusOf(bot),
                                face = faces[bot.id] ?: CharacterMood.IDLE,
                                onOpen = { openBot(bot) },
                                modifier = modifier,
                            )
                        }
                    }
                }
            }

            if (layout.isEmpty && summaries.isEmpty()) {
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

    menuFor?.let { department ->
        DepartmentMenuSheet(
            department = department,
            onAddBots = {
                menuFor = null
                addBotsFor = department
            },
            onNewBot = {
                menuFor = null
                botFailed = false
                newBotFor = department
            },
            onDismiss = { menuFor = null },
        )
    }

    addBotsFor?.let { department ->
        AddBotsSheet(
            department = department,
            bots = DepartmentRules.selectable(state),
            onSave = { placement ->
                var saved = true
                for (call in placement.calls(department)) {
                    if (session.assignSection(call.name, call.botIds, reportsError = false) == null) {
                        saved = false
                        break
                    }
                }
                saved
            },
            onDismiss = { addBotsFor = null },
        )
    }

    newBotFor?.let { department ->
        NewBotSheet(
            creating = creatingBot,
            failed = botFailed,
            onCreate = { name, job, retryKey ->
                if (!creatingBot) {
                    creatingBot = true
                    botFailed = false
                    scope.launch {
                        try {
                            val bot = session.createBot(NewBotRules.spec(name, job, retryKey))
                            if (bot != null) {
                                haptics.play(TactileAction.CREATE_BOT_SUCCESS)
                                session.assignSection(department, listOf(bot.id), reportsError = false)
                                newBotFor = null
                                navigator.open(Chat.BotChat(session.state.value.bot(bot.id) ?: bot))
                            } else {
                                session.actionError = null
                                botFailed = true
                            }
                        } finally {
                            creatingBot = false
                        }
                    }
                }
            },
            onDismiss = { newBotFor = null },
        )
    }
}

@Composable
private fun AdvancedBotEntry(
    bot: Bot,
    chief: Boolean,
    summary: ChatSummary?,
    face: CharacterMood,
    dot: HomeDot,
    hasPendingCard: Boolean,
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
) {
    val now = rememberSnoozeNow(bot.tasks.orEmpty())
    val row = RosterBotRow(bot, hasPendingCard, queuedThreadIds, now)
    Column(modifier = Modifier.fillMaxWidth()) {
        AdvancedBotRow(
            bot = bot,
            chief = chief,
            dot = dot,
            face = face,
            preview = summary?.preview.orEmpty(),
            lastActivity = summary?.lastActivity ?: 0.0,
            threadCount = row.threadCount,
            expanded = expanded,
            onOpen = onOpenRow,
            onToggleThreads = onToggle,
        )
        AnimatedVisibility(
            visible = expanded,
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
