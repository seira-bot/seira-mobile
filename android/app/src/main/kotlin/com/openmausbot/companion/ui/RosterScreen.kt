package com.openmausbot.companion.ui

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Profile
import com.openmausbot.companion.core.ChatSummary
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.core.RosterRowStatus
import com.openmausbot.companion.core.RosterDensity
import com.openmausbot.companion.core.SearchHit
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.chat
import com.openmausbot.companion.core.chatSummaries
import com.openmausbot.companion.core.forTask
import com.openmausbot.companion.core.rosterStatus
import com.openmausbot.companion.ui.theme.BottomFade
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.glass
import com.openmausbot.companion.ui.theme.seiraShadow
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The roster — the port of `ios/App/ChatListView.swift`.
 *
 * Messages-shaped: a header with you on the left and settings on the right, your
 * groups, every bot below with the unread dot in the bot's own colour at the left
 * edge, and a bar floating at the bottom. The bar's pill is Updates — only the
 * bots that need you, are working, or have something you have not read — beside
 * round search and new-bot buttons. Everything scrolls under the bar, which is
 * why the list's end is inset by the bar's measured height plus
 * [LIST_END_MARGIN]: the bar grows with the text size, and a fixed guess at it
 * let the last row sit under the bar at the largest sizes.
 *
 * Two densities, chosen in Settings → List density. Compact, the default, puts
 * each bot and group on one line ([CompactBotEntry], [CompactRoomRow]);
 * comfortable keeps groups as tiles and bots as two-line rows with a "Threads"
 * disclosure beneath each.
 *
 * Ordering is not decided here. `chatSummaries` (`:core`) folds pinned → unread →
 * last activity and hides hidden bots; [RosterLayout] decides which of those are
 * rows and which are tiles; the screen renders what it is handed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterScreen(navigator: CompanionNavigator) {
    val environment = LocalCompanion.current
    val session = environment.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val state by session.state.collectAsState()
    val connection by session.connection.collectAsState()
    val status by session.status.collectAsState()
    // The same preference the transcript folds by, from the same store: a reader
    // who turned activity off must not still read tool names here.
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    // Settings → List density, per device. Compact unless the person chose otherwise.
    val rosterDensity by environment.chatPreferences.rosterDensity.collectAsState()
    val compact = rosterDensity == RosterDensity.COMPACT
    val sectionSpacing = if (compact) COMPACT_SECTION_SPACING else SECTION_SPACING

    var bar by rememberSaveable(stateSaver = RosterBarSaver) { mutableStateOf(RosterBar()) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var showingUpdates by remember { mutableStateOf(false) }
    var showingNewGroup by remember { mutableStateOf(false) }
    var showingNewSection by remember { mutableStateOf(false) }
    var expandedBots by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var collapsedFolders by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var creatingThreads by remember { mutableStateOf(emptySet<String>()) }
    // One createBot at a time: a second tap while the first is in flight
    // would race two bots into existence.
    var creatingBot by remember { mutableStateOf(false) }
    var managingThreads by remember { mutableStateOf<Chat?>(null) }
    var showingNewChat by remember { mutableStateOf(false) }
    var teamExpanded by rememberSaveable { mutableStateOf(false) }
    var profile by remember { mutableStateOf<Profile?>(null) }
    val liveCall by environment.liveCalls.state.collectAsState()
    // What the floating bar measures, so the list's end clears it exactly.
    // Zero until the first layout, when the list is at its top anyway.
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }

    val query = bar.query

    LaunchedEffect(connection?.id, status) {
        if (status == Session.Status.Live) {
            session.configStatus()?.profile?.let { profile = it }
        }
    }

    // Local filtering is always on; the computer is only asked past two
    // characters and after a quiet moment (§10).
    LaunchedEffect(query) {
        when (val decision = SearchPolicy.decide(query)) {
            SearchPolicy.Decision.Clear -> {
                hits = emptyList()
                searching = false
            }
            is SearchPolicy.Decision.Remote -> {
                searching = true
                delay(SearchPolicy.DEBOUNCE_MILLIS)
                hits = session.search(decision.query)
                searching = false
            }
        }
    }

    // Tokens and the stream cursor change the state on nearly every frame of a
    // busy fleet, and nothing below but the Updates pill reads them. So the
    // list's folds are keyed on this: the same state object until a field they
    // do read changes (bots, groups, transcripts, branches, edits, queues).
    val fleet = remember(
        state.bots, state.rooms, state.messages, state.activeLeafIds, state.pendingEdits, state.pendingQueued,
    ) { state }
    // Folding the fleet walks every thread's transcript, so it is keyed on the
    // fleet and the activity level alone: typing filters the fold instead of
    // repeating it.
    val summaries = remember(fleet, activityDetail) { fleet.chatSummaries(activityDetail) }
    // Only a search has rows to filter; the unsearched roster is assembled
    // section by section below.
    val rows = remember(summaries, query, fleet.queuedThreadIds) {
        rosterThreadRows(summaries, query, fleet.queuedThreadIds)
    }
    val approvals = remember(fleet) { fleet.pendingApprovals }
    val waiting = remember(fleet, approvals) { RosterLayout.waitingChats(fleet, approvals) }
    // One pass over the fleet rather than one per row: resolving a face walks the
    // chat's visible transcript.
    val faces = remember(fleet, summaries) {
        summaries.associate { it.id to MausState.forChat(it.chat, fleet) }
    }
    val summariesById = remember(summaries) { summaries.associateBy { it.id } }
    // Hoisted out of the list: read inside a lazy item, `state` would make that
    // item's recompose scope the whole fleet.
    val rooms = fleet.rooms
    val tiles = remember(fleet) {
        rooms.associate { it.id to RosterLayout.memberBots(fleet, it) }
    }
    // The cross-bot Needs attention section rides above every roster section.
    val attention = remember(fleet) { fleet.crossBotAttention() }
    // The hero shows the Chief of Staff the crown marks in the list.
    val chief = remember(fleet) {
        fleet.unsectionedChief ?: fleet.bots.firstOrNull { it.hidden != true && it.chiefOfStaff == true }
    }
    val team = remember(fleet, summaries, waiting, faces, chief?.id) {
        val visible = summaries.mapNotNull { (it.chat as? Chat.BotChat)?.bot }.filter { it.id != chief?.id }
        val members = visible.map { bot ->
            val status = bot.rosterStatus(hasPendingCard = bot.id in waiting)
            TeamMember(
                bot = bot,
                waiting = status == RosterRowStatus.WAITING_ON_YOU,
                working = status == RosterRowStatus.WORKING,
                face = faces[bot.id] ?: MausState.IDLE,
            )
        }
        // Who needs you leads, then who works; the list's own order breaks ties.
        members.sortedBy { if (it.waiting) 0 else if (it.working) 1 else 2 }
    }
    val canTalk = LiveCallRules.offersCall(liveCall, state.liveCall)

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
        // Hoisted across bot sections, search and both densities, so moving a
        // row cannot permit a second creation while the first is pending.
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
    val openRow: (ChatSummary) -> Unit = { summary ->
        navigator.open(environment.chatPreferences.restoringThread(summary.chat, connection?.id))
    }

    // Compact: a group is one line, like a bot.
    val compactRoom: @Composable (Room) -> Unit = { room ->
        CompactRoomRow(
            room = room,
            members = tiles[room.id].orEmpty(),
            lastActivity = summariesById[room.id]?.lastActivity ?: 0.0,
            waiting = room.id in waiting,
            onClick = { navigator.open(Chat.RoomChat(room)) },
        )
    }

    val entry: @Composable (ChatSummary, Boolean) -> Unit = { summary, _ ->
        val chat = summary.chat
        if (compact) {
            when (chat) {
                is Chat.BotChat -> CompactBotEntry(
                    bot = chat.bot,
                    lastActivity = summary.lastActivity,
                    face = faces[summary.id] ?: MausState.IDLE,
                    hasPendingCard = summary.id in waiting,
                    query = query,
                    expanded = chat.bot.id in expandedBots,
                    collapsedFolders = collapsedFolders,
                    creating = chat.bot.id in creatingThreads,
                    queuedThreadIds = queuedThreadIds,
                    onOpenRow = { openRow(summary) },
                    onToggle = { toggleBot(chat.bot.id) },
                    onToggleFolder = toggleFolder,
                    onCreate = { createThread(chat.bot) },
                    onManage = { managingThreads = Chat.BotChat(chat.bot) },
                    onOpen = navigator::open,
                    onCollapse = { expandedBots = expandedBots - chat.bot.id },
                )
                is Chat.RoomChat -> compactRoom(chat.room)
            }
        } else {
            // Comfortable: the two-line row, with its Threads disclosure beneath a bot.
            Column {
                ChatRow(
                    summary = summary,
                    face = faces[summary.id] ?: MausState.IDLE,
                    waiting = summary.id in waiting,
                    members = (chat as? Chat.RoomChat)?.let { tiles[it.room.id] }.orEmpty(),
                    onClick = { openRow(summary) },
                )
                (chat as? Chat.BotChat)?.bot?.let { bot ->
                    BotThreadTree(
                        bot = bot,
                        queuedThreadIds = queuedThreadIds,
                        query = query,
                        expanded = bot.id in expandedBots,
                        collapsedFolders = collapsedFolders,
                        creating = bot.id in creatingThreads,
                        onToggle = { toggleBot(bot.id) },
                        onToggleFolder = toggleFolder,
                        onCreate = { createThread(bot) },
                        onManage = { managingThreads = Chat.BotChat(bot) },
                        onOpen = navigator::open,
                    )
                }
            }
        }
    }

    val openChief: () -> Unit = {
        chief?.let { bot -> summariesById[bot.id]?.let(openRow) ?: navigator.open(Chat.BotChat(bot)) }
    }
    val greetingHour = LocalHomeHour.current
    val greeting = homeGreetingText(remember { greetingHour() }, profile?.name)
    val openSettings = { navigator.push(Destination.Settings) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        // Session.refresh waits until the stream leaves connecting
                        // (or 10s), so the spinner means what it appears to mean.
                        session.refresh()
                        refreshing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // Unsearched, "No bots yet" waits until there is no group either:
                // drawn over group rows, it would sit on top of them.
                val nothingToList =
                    if (query.isEmpty()) !RosterLayout.listsAnyChat(summaries) else rows.isEmpty()
                if (nothingToList && hits.isEmpty()) {
                    EmptyState(
                        title = stringResource(if (query.isEmpty()) R.string.mobile_roster_no_bots_yet else R.string.mobile_roster_nothing_matches),
                        description = if (query.isEmpty()) {
                            stringResource(R.string.mobile_roster_empty_description)
                        } else {
                            stringResource(R.string.mobile_roster_no_thread_matches, query)
                        },
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("roster-list"),
                    contentPadding = PaddingValues(bottom = barHeight + LIST_END_MARGIN),
                ) {
                    item(key = "home-header") {
                        HomeHeader(
                            greeting = greeting,
                            initials = HomeGreeting.initials(profile?.name),
                            onProfile = openSettings,
                        )
                    }
                    item(key = "home-banner") { StatusBanner() }
                    if (RosterLayout.showsGroups(query)) {
                        if (chief != null) {
                            item(key = "home-hero") {
                                val summary = summariesById[chief.id]
                                val chiefStatus = chief.rosterStatus(hasPendingCard = chief.id in waiting)
                                HomeHero(
                                    bot = chief,
                                    role = localizedMobileCopy("Chief of Staff"),
                                    status = summary?.preview?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.home_status_ready),
                                    busy = chiefStatus == RosterRowStatus.WORKING,
                                    waiting = chiefStatus == RosterRowStatus.WAITING_ON_YOU,
                                    face = faces[chief.id] ?: MausState.IDLE,
                                    canTalk = canTalk,
                                    onOpen = openChief,
                                    onTalk = {
                                        haptics.play(HapticCue.SELECT)
                                        environment.callUi.request(chief.id)
                                        openChief()
                                    },
                                    modifier = Modifier.padding(top = 20.dp),
                                )
                            }
                        }
                        if (team.isNotEmpty() && team.size + (if (chief != null) 1 else 0) >= 2) {
                            item(key = "home-team") {
                                TeamRail(
                                    members = team,
                                    expanded = teamExpanded,
                                    onToggle = {
                                        haptics.play(HapticCue.SELECT)
                                        teamExpanded = !teamExpanded
                                    },
                                    onOpen = { bot -> summariesById[bot.id]?.let(openRow) ?: navigator.open(Chat.BotChat(bot)) },
                                    onCreateThread = createThread,
                                    onManageThreads = { bot -> managingThreads = Chat.BotChat(bot) },
                                    modifier = Modifier.padding(top = 28.dp),
                                )
                            }
                        }
                        item(key = "home-chats-heading") {
                            ChatsHeading(
                                onUpdates = {
                                    haptics.play(TactileAction.OPEN_UPDATES)
                                    showingUpdates = true
                                },
                                onCalendar = { navigator.push(Destination.Calendar) },
                                onSettings = openSettings,
                                modifier = Modifier.padding(top = 20.dp),
                            )
                        }
                    }
                    if (RosterLayout.showsGroups(query)) {
                        if (attention.isNotEmpty()) {
                            item(key = "attention-label") {
                                SectionLabel(stringResource(R.string.mobile_roster_needs_attention), Modifier.padding(top = 2.dp, bottom = 4.dp))
                            }
                            items(attention, key = { "attention-${it.id}" }) { entry ->
                                AttentionRow(entry = entry, onOpen = {
                                    fleet.bots.firstOrNull { it.id == entry.botId }
                                        ?.let { bot -> Chat.BotChat(bot.forTask(entry.task.threadId) ?: bot) }
                                        ?.let(navigator::open)
                                })
                            }
                        }
                        fleet.unsectionedChief?.let { chief ->
                            summariesById[chief.id]?.let { summary ->
                                item(key = "chief-${chief.id}") {
                                    // A one-line row right under Needs attention
                                    // would read as one more of its rows.
                                    val apart = compact && attention.isNotEmpty()
                                    Column(modifier = Modifier.padding(top = if (apart) sectionSpacing else 0.dp)) {
                                        entry(summary, true)
                                    }
                                }
                            }
                        }
                        val pinned = fleet.pinnedBots.mapNotNull { summariesById[it.id] }
                        if (pinned.isNotEmpty()) {
                            item(key = "pinned-label") {
                                // a compact row above it leaves little air of its own
                                SectionLabel(
                                    stringResource(R.string.mobile_roster_pinned),
                                    Modifier.padding(top = if (compact) sectionSpacing else 2.dp, bottom = 4.dp),
                                )
                            }
                            itemsIndexed(pinned, key = { _, summary -> "pinned-${summary.id}" }) { index, summary ->
                                entry(summary, index == pinned.lastIndex)
                            }
                        }
                        val startNewGroup = {
                            haptics.play(TactileAction.START_NEW_GROUP)
                            showingNewGroup = true
                        }
                        if (compact) {
                            // Groups as rows under a title that carries the "+"
                            // the comfortable strip shows as a tile.
                            item(key = "groups-title") {
                                CompactGroupsTitle(stringResource(R.string.mobile_groups_ae9629f4), onCreate = startNewGroup, spacing = sectionSpacing)
                            }
                            items(fleet.unsectionedChannels, key = { "group-${it.id}" }) { compactRoom(it) }
                            if (fleet.botChats.isNotEmpty()) {
                                item(key = "bot-chats-title") {
                                    CompactGroupsTitle(stringResource(R.string.mobile_bot_threads_ec81acf2), onCreate = null, spacing = sectionSpacing)
                                }
                                items(fleet.botChats, key = { "bot-chat-${it.id}" }) { compactRoom(it) }
                            }
                        } else {
                            item(key = "channels") {
                                GroupsStrip(
                                    title = stringResource(R.string.mobile_groups_ae9629f4),
                                    rooms = fleet.unsectionedChannels,
                                    members = tiles,
                                    onOpen = { navigator.open(Chat.RoomChat(it)) },
                                    onCreate = startNewGroup,
                                )
                            }
                            if (fleet.botChats.isNotEmpty()) {
                                item(key = "bot-chats") {
                                    GroupsStrip(
                                        title = stringResource(R.string.mobile_bot_threads_ec81acf2),
                                        rooms = fleet.botChats,
                                        members = tiles,
                                        onOpen = { navigator.open(Chat.RoomChat(it)) },
                                        onCreate = null,
                                    )
                                }
                            }
                        }
                        val unsectioned = fleet.unsectionedBots.mapNotNull { summariesById[it.id] }
                        if (unsectioned.isNotEmpty()) {
                            item(key = "bots-label") {
                                SectionLabel(stringResource(R.string.mobile_bots_4ca88ea4), Modifier.padding(top = sectionSpacing, bottom = 4.dp))
                            }
                            itemsIndexed(unsectioned, key = { _, summary -> "bot-${summary.id}" }) { index, summary ->
                                entry(summary, index == unsectioned.lastIndex)
                            }
                        }
                        // Chiefs, then the section's channels, then its bots —
                        // the order of `rosterSections` in `ChatListView.swift`.
                        fleet.sidebarSections.forEach { section ->
                            item(key = "section-${section.id}") {
                                SectionLabel(section.name, Modifier.padding(top = sectionSpacing, bottom = 4.dp))
                            }
                            val sectionChiefs = section.chiefs.mapNotNull { summariesById[it.id] }
                            val sectionBots = section.bots.mapNotNull { summariesById[it.id] }
                            itemsIndexed(
                                sectionChiefs,
                                key = { _, summary -> "section-${section.id}-chief-${summary.id}" },
                            ) { index, summary ->
                                entry(
                                    summary,
                                    // A divider separates two rows. The strip is
                                    // not a row, and neither is the end of the
                                    // section.
                                    index == sectionChiefs.lastIndex &&
                                        (section.channels.isNotEmpty() || sectionBots.isEmpty()),
                                )
                            }
                            if (section.channels.isNotEmpty()) {
                                if (compact) {
                                    items(section.channels, key = { "section-${section.id}-group-${it.id}" }) {
                                        compactRoom(it)
                                    }
                                } else {
                                    item(key = "section-${section.id}-channels") {
                                        GroupsStrip(
                                            title = stringResource(R.string.mobile_groups_ae9629f4),
                                            rooms = section.channels,
                                            members = tiles,
                                            onOpen = { navigator.open(Chat.RoomChat(it)) },
                                            onCreate = null,
                                        )
                                    }
                                }
                            }
                            itemsIndexed(
                                sectionBots,
                                key = { _, summary -> "section-${section.id}-bot-${summary.id}" },
                            ) { index, summary ->
                                entry(summary, index == sectionBots.lastIndex)
                            }
                        }
                    }

                    if (query.isNotEmpty() && hits.isNotEmpty()) {
                        item(key = "search-header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionLabel(stringResource(R.string.mobile_roster_messages))
                                Spacer(Modifier.weight(1f))
                                if (searching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(end = 20.dp)
                                            .size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                        items(hits, key = { it.id }) { hit ->
                            SearchHitRow(
                                hit = hit,
                                onClick = {
                                    scope.launch {
                                        // Session.open switches task, sets the
                                        // active branch, loads the `around` page
                                        // and focuses the message — the chat
                                        // screen honours the focus when it opens.
                                        session.open(hit)?.let {
                                            haptics.play(TactileAction.OPEN_SEARCH_RESULT)
                                            navigator.open(it)
                                        }
                                    }
                                },
                            )
                        }
                        item(key = "chats-label") {
                            SectionLabel(stringResource(R.string.mobile_threads_bb12e8aa), Modifier.padding(top = 14.dp, bottom = 4.dp))
                        }
                    }

                    if (query.isNotEmpty()) {
                        itemsIndexed(rows, key = { _, summary -> summary.chat.threadId }) { index, summary ->
                            entry(summary, index == rows.lastIndex)
                        }
                    }
                }
            }
        }

        BottomFade(height = 128.dp, modifier = Modifier.align(Alignment.BottomCenter))
        RosterBottomBar(
            bar = bar,
            onBar = { bar = it },
            onOpenSearch = {
                haptics.play(TactileAction.OPEN_SEARCH)
                bar = bar.openSearch()
            },
            onNewChat = { showingNewChat = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .testTag("roster-bottom-bar")
                .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
        )
    }

    if (showingNewChat) {
        NewChatSheet(
            bots = remember(summaries) { summaries.mapNotNull { (it.chat as? Chat.BotChat)?.bot } },
            canCreateBot = !creatingBot,
            // The same rule the section sheet picks from: two copies of "which
            // bots can be sectioned" could disagree about a hidden one.
            canOrganize = remember(fleet) { SectionRules.selectable(fleet).isNotEmpty() },
            onBot = { bot ->
                showingNewChat = false
                createThread(bot)
            },
            onNewGroup = {
                showingNewChat = false
                haptics.play(TactileAction.START_NEW_GROUP)
                showingNewGroup = true
            },
            onNewBot = {
                showingNewChat = false
                if (!creatingBot) {
                    creatingBot = true
                    scope.launch {
                        try {
                            session.createBot()?.let {
                                haptics.play(TactileAction.CREATE_BOT_SUCCESS)
                                navigator.open(Chat.BotChat(it))
                            }
                        } finally {
                            creatingBot = false
                        }
                    }
                }
            },
            onOrganize = {
                showingNewChat = false
                haptics.play(TactileAction.START_NEW_SECTION)
                showingNewSection = true
            },
            onDismiss = { showingNewChat = false },
        )
    }

    if (showingUpdates) {
        UpdatesSheet(
            onOpen = { chat ->
                showingUpdates = false
                navigator.open(chat)
            },
            onDismiss = { showingUpdates = false },
        )
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

    if (showingNewSection) {
        NewSectionSheet(onDismiss = { showingNewSection = false })
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

/** Clear space between the last row and the floating bar, once scrolled to the end. */
private val LIST_END_MARGIN = 16.dp

/** Between one section and the next title. Compact rows leave less air of their own. */
private val SECTION_SPACING = 18.dp
private val COMPACT_SECTION_SPACING = 14.dp

/** Two flags and a string: enough to survive a rotation with the search still up. */
private val RosterBarSaver = listSaver<RosterBar, Any>(
    save = { listOf(it.searchOpen, it.query) },
    restore = { RosterBar(searchOpen = it[0] as Boolean, query = it[1] as String) },
)

private val StringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

/**
 * A compact groups heading. The unsectioned one carries the "+" that makes a
 * group, where the comfortable strip has its dashed tile.
 */
@Composable
private fun CompactGroupsTitle(title: String, onCreate: (() -> Unit)?, spacing: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (onCreate == null) spacing else 4.dp, bottom = if (onCreate == null) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, Modifier.weight(1f))
        if (onCreate != null) {
            TouchTarget(onClick = onCreate, contentDescription = stringResource(R.string.mobile_new_group_f9850c0b), modifier = Modifier.padding(end = 4.dp)) {
                Icon(
                    imageVector = SeiraIcons.Plus,
                    contentDescription = null,
                    tint = Seira.colors.ink2,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** A heading over a stretch of the list. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = RosterLayout.sectionLabel(text),
        style = SeiraType.callout.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        color = Seira.colors.ink2,
        modifier = modifier.padding(horizontal = 20.dp),
    )
}

/**
 * Every group, across the top, with the tile that makes one at the end — the
 * place iOS puts it, and the reason the header no longer carries a second plus.
 */
@Composable
private fun GroupsStrip(
    title: String,
    rooms: List<Room>,
    members: Map<String, List<Bot>>,
    onOpen: (Room) -> Unit,
    onCreate: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(title)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(rooms, key = { it.id }) { room ->
                GroupTile(
                    room = room,
                    members = members[room.id].orEmpty(),
                    onClick = { onOpen(room) },
                )
            }
            if (onCreate != null) {
                item(key = "new-group") { NewGroupTile(onClick = onCreate) }
            }
        }
    }
}

/**
 * A room as a round tile: the first two members' mascots stacked, its name
 * beneath.
 */
@Composable
private fun GroupTile(room: Room, members: List<Bot>, onClick: () -> Unit) {
    val colors = Seira.colors
    GroupTileFrame(
        label = room.name,
        labelColor = colors.ink,
        onClick = onClick,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(colors.tint, CircleShape),
        )
        RoomFaces(members = members, size = 44.dp)
        if (room.unread) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(14.dp)
                    .background(colors.app, CircleShape)
                    .padding(2.dp)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}

/** The empty tile at the end of the strip: this is where a group is made. */
@Composable
private fun NewGroupTile(onClick: () -> Unit) {
    val outline = Seira.colors.ink3
    GroupTileFrame(label = stringResource(R.string.mobile_new_group_f9850c0b), labelColor = Seira.colors.ink2, onClick = onClick) {
        Spacer(
            modifier = Modifier
                .size(64.dp)
                .drawWithCache {
                    val width = 1.5.dp.toPx()
                    val dash = 4.dp.toPx()
                    val style = Stroke(
                        width = width,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), 0f),
                    )
                    val radius = size.minDimension / 2f - width / 2f
                    onDrawBehind { drawCircle(color = outline, radius = radius, style = style) }
                },
        )
        Icon(
            imageVector = SeiraIcons.Plus,
            contentDescription = null,
            tint = Seira.colors.ink2,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun GroupTileFrame(
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center, content = content)
        Text(
            text = label,
            style = SeiraType.caption.copy(fontWeight = FontWeight.Medium),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One chat as the design's row: the face (two stacked faces for a group), the
 * name with its time, and one line of what was said. A bot that waits on you
 * has its line in the warning colour; the dot at the end is unread, or a
 * spinner while the bot works.
 */
@Composable
private fun ChatRow(
    summary: ChatSummary,
    face: MausState,
    waiting: Boolean,
    members: List<Bot>,
    onClick: () -> Unit,
) {
    val colors = Seira.colors
    val chat = summary.chat
    val now = remember(summary.lastActivity) { System.currentTimeMillis() }
    val waitingLabel = stringResource(R.string.mobile_waiting_on_you_edab5b72)
    val unreadLabel = stringResource(R.string.mobile_unread_07b032b5)
    val unread = chat.unread && !chat.busy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat-row.${summary.id}")
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (waiting) stateDescription = waitingLabel }
            .heightIn(min = 68.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (chat) {
            is Chat.BotChat -> IdentityAvatar(bot = chat.bot, size = 48.dp, state = face, animated = false)
            is Chat.RoomChat -> RoomFaces(members = members, size = 48.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = chat.name,
                    style = SeiraType.headline.copy(fontWeight = if (chat.unread) FontWeight.SemiBold else FontWeight.Medium),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if ((chat as? Chat.BotChat)?.bot?.chiefOfStaff == true) {
                    Icon(
                        painter = painterResource(R.drawable.ic_crown),
                        contentDescription = localizedMobileCopy("Chief of Staff"),
                        tint = colors.accent,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.CenterVertically)
                            .testTag("chief-badge"),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = RelativeStamp.list(summary.lastActivity, now, locale = Locale.getDefault()),
                    style = SeiraType.caption.copy(fontFeatureSettings = "tnum"),
                    color = colors.ink3,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.preview.ifEmpty { " " },
                    style = SeiraType.callout,
                    color = if (waiting) colors.warningText else colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (waiting) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pan_tool),
                        contentDescription = waitingLabel,
                        tint = colors.warning,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (chat.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = colors.success,
                    )
                } else if (unread) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(colors.accent, CircleShape)
                            .semantics { contentDescription = unreadLabel },
                    )
                }
            }
        }
    }
}

/**
 * The bar over the bottom of the list: the glass search pill beside the ink
 * New chat button, or, once search is open, the field beside Cancel.
 *
 * Cancel takes the query with it, which is what puts the Home sections back.
 */
@Composable
private fun RosterBottomBar(
    bar: RosterBar,
    onBar: (RosterBar) -> Unit,
    onOpenSearch: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(bar.searchOpen) {
        if (bar.searchOpen) focus.requestFocus() else focusManager.clearFocus()
    }
    val prompt = stringResource(R.string.home_search_prompt)
    val newChat = stringResource(R.string.home_new_chat)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bar.searchOpen) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BAR_HEIGHT)
                    .glass(CircleShape)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SeiraIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (bar.query.isEmpty()) {
                        Text(prompt, style = SeiraType.body, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicTextField(
                        value = bar.query,
                        onValueChange = { onBar(bar.typed(it)) },
                        singleLine = true,
                        textStyle = SeiraType.body.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (bar.query.isNotEmpty()) {
                    TouchTarget(onClick = { onBar(bar.clearQuery()) }, size = 48.dp) {
                        Icon(
                            imageVector = SeiraIcons.X,
                            contentDescription = stringResource(R.string.mobile_clear_search_67300d0f),
                            tint = colors.ink2,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .heightIn(min = BAR_HEIGHT)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = { onBar(bar.cancelSearch()) })
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.mobile_cancel_77dfd213), style = SeiraType.headline, color = colors.ink)
            }
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = BAR_HEIGHT)
                    .glass(CircleShape)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onOpenSearch)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SeiraIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                Text(prompt, style = SeiraType.body, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .testTag("home-new-chat")
                    .size(BAR_HEIGHT)
                    .seiraShadow(CircleShape)
                    .background(colors.primary, CircleShape)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onNewChat)
                    .semantics { contentDescription = newChat },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SeiraIcons.PenSquare, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private val BAR_HEIGHT = 52.dp

@Composable
private fun SearchHitRow(hit: SearchHit, onClick: () -> Unit) {
    val now = remember(hit.id) { System.currentTimeMillis() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Who said it. Two hits with the same words are otherwise identical.
        Icon(
            // A person for what you said, the Maus mark for what a bot said —
            // iOS makes the same split with person.fill and a speech bubble.
            painter = if (SearchHitRole.isFromUser(hit.role)) {
                rememberVectorPainter(Icons.Filled.Person)
            } else {
                painterResource(R.drawable.ic_maus_mark)
            },
            contentDescription = localizedMobileCopy(SearchHitRole.contentDescription(hit.role, hit.name)),
            tint = secondaryTint,
            modifier = Modifier
                .size(26.dp)
                .background(secondaryTint.copy(alpha = 0.13f), CircleShape)
                .padding(5.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(hit.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                hit.task?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 12.sp, color = secondaryTint)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = RelativeStamp.list(hit.at, now, locale = Locale.getDefault()),
                    fontSize = 12.sp,
                    color = secondaryTint,
                )
            }
            Text(
                text = hit.snippet,
                fontSize = 14.sp,
                color = secondaryTint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Connection state, shown only when it is not "fine". */
@Composable
fun StatusBanner() {
    val environment = LocalCompanion.current
    val session = environment.session
    val status by session.status.collectAsState()
    val connection by session.connection.collectAsState()
    // A saved LAN or Bonjour computer that will not connect on Android 17 may be
    // missing the local-network grant: a pairing made before the phone updated,
    // or a grant taken back in Settings. Ask here, where the failure is already
    // on screen, rather than at launch (the root asks for nothing) — and once
    // per computer, because every retry walks this banner back through
    // Connecting and would otherwise ask again.
    val offline = status is Session.Status.Offline
    var askedForLocalNetwork by rememberSaveable(connection?.id) { mutableStateOf(false) }
    LaunchedEffect(offline, connection?.id) {
        val routes = connection?.automaticEndpoints
        if (!offline || askedForLocalNetwork || routes == null) return@LaunchedEffect
        val missing = environment.permissions.localRoutePermissions(routes)
        if (missing.isEmpty()) return@LaunchedEffect
        askedForLocalNetwork = true
        environment.requestPermissions(missing)
    }
    val banner: Pair<String, Color>? = when (val current = status) {
        Session.Status.Live, Session.Status.Unpaired -> null
        Session.Status.Connecting -> stringResource(R.string.mobile_connecting_fd3e7969) to secondaryTint
        is Session.Status.Offline -> current.message to Color(MausPalette.argb("orange"))
        Session.Status.Unauthorized ->
            stringResource(R.string.mobile_phone_unpaired_banner) to MaterialTheme.colorScheme.error
    }
    val (text, tint) = banner ?: return
    Text(
        text = text,
        fontSize = 13.sp,
        color = tint,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(secondaryTint.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun localizedRosterHeaderSubtitle(name: String?, status: Session.Status): String {
    if (status == Session.Status.Unpaired) {
        return stringResource(R.string.mobile_settings_not_paired_status)
    }
    val computer = name ?: stringResource(R.string.mobile_settings_not_paired_status)
    return when (status) {
        Session.Status.Live -> stringResource(R.string.mobile_roster_header_connected, computer)
        Session.Status.Connecting -> stringResource(R.string.mobile_roster_header_connecting, computer)
        is Session.Status.Offline -> stringResource(R.string.mobile_roster_header_offline, computer)
        Session.Status.Unauthorized -> stringResource(R.string.mobile_roster_header_unpaired, computer)
        Session.Status.Unpaired -> stringResource(R.string.mobile_settings_not_paired_status)
    }
}
