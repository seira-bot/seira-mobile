package com.openmausbot.companion.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.BuildConfig
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.Destination
import com.openmausbot.companion.ui.HomeTab
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.NewSectionSheet
import com.openmausbot.companion.ui.TactileAction
import com.openmausbot.companion.ui.UpdateKind
import com.openmausbot.companion.ui.UpdatesSheet
import com.openmausbot.companion.ui.computerStatusPhrase
import com.openmausbot.companion.ui.play
import com.openmausbot.companion.ui.rememberHaptics
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween
import com.openmausbot.companion.ui.updates

/** Home after pairing: top bar, the three tabs, the floating tab bar and the left drawer. */
@Composable
internal fun HomeShell(navigator: CompanionNavigator, callsEnabled: Boolean = BuildConfig.LIVE_CALLS) {
    val colors = Seira.colors
    val environment = LocalCompanion.current
    val session = environment.session
    val haptics = rememberHaptics()
    val state by session.state.collectAsState()
    val status by session.status.collectAsState()
    val connection by session.connection.collectAsState()
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var showUpdates by remember { mutableStateOf(false) }
    var showNewChat by remember { mutableStateOf(false) }
    var showNewDepartment by remember { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = if (drawerOpen) 1f else 0f,
        animationSpec = seiraTween(SeiraMotion.SHEET, reduced),
        label = "home-drawer",
    )

    val waiting = remember(state, activityDetail) {
        state.updates(activityDetail).count { it.kind == UpdateKind.NEEDS_YOU }
    }
    val departments = remember(state.sidebarSections) {
        state.sidebarSections.map { DrawerDepartment(it.name, it.chiefs.size + it.bots.size) }
    }
    val sectionFilter = navigator.sectionFilter
    val fleetLoaded = state.bots.isNotEmpty() || state.rooms.isNotEmpty()
    LaunchedEffect(fleetLoaded, departments, sectionFilter) {
        if (HomeShellRules.staleFilter(fleetLoaded, departments.map { it.name }, sectionFilter)) navigator.showChats(null)
    }

    val closeDrawer = { drawerOpen = false }
    val go: (Destination) -> Unit = { destination ->
        drawerOpen = false
        navigator.push(destination)
    }
    val actions = DrawerActions(
        onComputer = { go(Destination.Computers) },
        onSearch = { go(Destination.Search) },
        onAllChats = {
            drawerOpen = false
            navigator.showChats(null)
        },
        onDepartment = { name ->
            drawerOpen = false
            navigator.showChats(name)
        },
        onAddDepartment = {
            drawerOpen = false
            showNewDepartment = true
        },
        onInbox = {
            drawerOpen = false
            navigator.selectTab(HomeTab.Inbox)
        },
        onTeamMap = { go(Destination.TeamMap) },
        onTeamMemory = { go(Destination.TeamMemory) },
        onCalendar = { go(Destination.Calendar) },
        onConnectedApps = { go(Destination.ConnectedApps) },
        onAiUsage = { go(Destination.AiUsage) },
        onSettings = { go(Destination.Settings) },
        onNew = {
            drawerOpen = false
            showNewChat = true
        },
    )
    val drawerState = DrawerState(
        computerName = connection?.name,
        connectionText = computerStatusPhrase(status),
        connected = status == Session.Status.Live,
        departments = departments,
        selectedDepartment = sectionFilter,
        allChatsSelected = navigator.homeTab == HomeTab.Chats && sectionFilter == null,
        inboxSelected = navigator.homeTab == HomeTab.Inbox,
        inboxCount = waiting,
    )

    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    val shiftPx = with(density) { DRAWER_SHIFT.toPx() }
    val closeLabel = stringResource(R.string.shell_close_menu)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.app)
            .testTag("home-shell"),
    ) {
        if (progress > 0f || drawerOpen) {
            HomeDrawer(
                state = drawerState,
                actions = actions,
                modifier = Modifier
                    .statusBarsPadding()
                    .testTag("home-drawer"),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = progress * shiftPx }
                .clip(RoundedCornerShape((32 * progress).dp))
                .background(colors.app)
                .then(
                    if (progress > 0f) {
                        Modifier.border(1.dp, colors.hairline, RoundedCornerShape((32 * progress).dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                HomeTopBar(
                    waiting = waiting,
                    onMenu = { drawerOpen = true },
                    onUpdates = {
                        haptics.play(TactileAction.OPEN_UPDATES)
                        showUpdates = true
                    },
                    onProfile = { navigator.push(Destination.Settings) },
                )
                Box(modifier = Modifier.weight(1f)) {
                    when (navigator.homeTab) {
                        HomeTab.Chats -> ChatsTab(navigator, sectionFilter, onNewChat = { showNewChat = true }, callsEnabled = callsEnabled)
                        HomeTab.Inbox -> InboxTab(navigator)
                        HomeTab.Automations -> AutomationsTab(navigator)
                    }
                }
                HomeBottomBar(
                    selected = navigator.homeTab,
                    inboxCount = waiting,
                    onSelect = { navigator.selectTab(it) },
                )
            }
            if (progress > 0f || drawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim.copy(alpha = colors.scrim.alpha * progress))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = closeDrawer,
                        )
                        .semantics {
                            contentDescription = closeLabel
                            onClick(label = closeLabel) {
                                closeDrawer()
                                true
                            }
                        }
                        .testTag("home-scrim"),
                )
            }
        }
    }

    if (showUpdates) {
        UpdatesSheet(
            onOpen = {
                showUpdates = false
                navigator.open(it)
            },
            onDismiss = { showUpdates = false },
        )
    }

    NewChatHost(
        show = showNewChat,
        navigator = navigator,
        onDismiss = { showNewChat = false },
        onNewDepartment = { showNewDepartment = true },
    )

    if (showNewDepartment) {
        NewSectionSheet(onDismiss = { showNewDepartment = false })
    }
}

internal object HomeShellRules {
    /** A department filter is dropped only after the fleet has arrived, so a restored filter survives the wait. */
    fun staleFilter(fleetLoaded: Boolean, departments: List<String>, filter: String?): Boolean =
        fleetLoaded && filter != null && filter !in departments
}
