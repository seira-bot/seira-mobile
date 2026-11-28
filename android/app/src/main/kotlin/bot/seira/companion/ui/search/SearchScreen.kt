package bot.seira.companion.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.R
import bot.seira.companion.core.Chat
import bot.seira.companion.core.Routine
import bot.seira.companion.core.SearchHit
import bot.seira.companion.ui.CompanionNavigator
import bot.seira.companion.ui.Destination
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.RoomFaces
import bot.seira.companion.ui.SearchHitRow
import bot.seira.companion.ui.SearchPolicy
import bot.seira.companion.ui.TactileAction
import bot.seira.companion.ui.TouchTarget
import bot.seira.companion.ui.home.chats.FilterPill
import bot.seira.companion.ui.play
import bot.seira.companion.ui.rememberHaptics
import bot.seira.companion.ui.theme.IdentityAvatar
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The search page: a field, five chips and results from this phone's bots,
 * groups and routines plus message hits from the computer.
 *
 * [navigator] opens a chat or the routines page from a result. Without one the
 * results still list, and a tap does nothing.
 */
@Composable
internal fun SearchScreen(onBack: () -> Unit, navigator: CompanionNavigator? = null) {
    val environment = LocalCompanion.current
    val session = environment.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val state by session.state.collectAsState()
    val actionError by session.actionErrorFlow.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(SearchFilter.ALL.name) }
    val filter = SearchFilter.valueOf(filterName)
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var routines by remember { mutableStateOf<List<Routine>>(emptyList()) }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        routines = session.loadRoutines().routines
    }

    LaunchedEffect(query) {
        failed = false
        when (val decision = SearchPolicy.decide(query)) {
            SearchPolicy.Decision.Clear -> {
                hits = emptyList()
                searching = false
            }
            is SearchPolicy.Decision.Remote -> {
                searching = true
                delay(SearchPolicy.DEBOUNCE_MILLIS)
                val before = session.actionErrorFlow.value
                val found = session.search(decision.query)
                failed = SearchRules.failed(before, session.actionErrorFlow.value, found)
                hits = found
                searching = false
            }
        }
    }

    val local = remember(state.bots, state.rooms, routines, query) {
        SearchResults(
            bots = SearchRules.bots(state.bots, query),
            groups = SearchRules.groups(state.rooms, query),
            routines = SearchRules.routines(routines, query),
        )
    }
    val results = remember(local, hits) { local.copy(messages = hits) }
    val visible = remember(results, filter) { SearchRules.visible(results, filter) }
    val empty = SearchRules.empty(query, visible, searching, failed)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Seira.colors.app)
            .imePadding()
            .testTag("search-screen"),
    ) {
        SearchBar(
            query = query,
            onQuery = { query = it },
            onBack = onBack,
            focus = focus,
            onDone = { focusManager.clearFocus() },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(stringResource(R.string.chats_filter_all), filter == SearchFilter.ALL, "search-filter-all") {
                filterName = SearchFilter.ALL.name
            }
            FilterPill(stringResource(R.string.mobile_roster_messages), filter == SearchFilter.MESSAGES, "search-filter-messages") {
                filterName = SearchFilter.MESSAGES.name
            }
            FilterPill(stringResource(R.string.mobile_bots_4ca88ea4), filter == SearchFilter.BOTS, "search-filter-bots") {
                filterName = SearchFilter.BOTS.name
            }
            FilterPill(stringResource(R.string.mobile_groups_ae9629f4), filter == SearchFilter.GROUPS, "search-filter-groups") {
                filterName = SearchFilter.GROUPS.name
            }
            FilterPill(stringResource(R.string.search_filter_routines), filter == SearchFilter.ROUTINES, "search-filter-routines") {
                filterName = SearchFilter.ROUTINES.name
            }
        }

        if (empty != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .testTag("search-empty"),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    text = stringResource(
                        when (empty) {
                            SearchEmpty.PROMPT -> R.string.search_prompt
                            SearchEmpty.NONE -> R.string.search_none
                            SearchEmpty.FAILED -> R.string.search_failed
                        },
                    ),
                    fontSize = 15.sp,
                    color = Seira.colors.ink3,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (visible.bots.isNotEmpty()) {
                item(key = "bots-header") {
                    SearchSectionLabel(stringResource(R.string.mobile_bots_4ca88ea4), searching = false)
                }
                items(visible.bots, key = { "bot-${it.id}" }) { bot ->
                    SearchRow(
                        title = bot.name,
                        subtitle = bot.title.ifBlank { bot.description },
                        tag = "search-bot.${bot.id}",
                        onClick = {
                            navigator?.let { nav ->
                                haptics.play(TactileAction.OPEN_SEARCH_RESULT)
                                nav.open(Chat.BotChat(bot))
                            }
                        },
                        leading = { IdentityAvatar(bot = bot, size = 40.dp, animated = false) },
                    )
                }
            }
            if (visible.groups.isNotEmpty()) {
                item(key = "groups-header") {
                    SearchSectionLabel(stringResource(R.string.mobile_groups_ae9629f4), searching = false)
                }
                items(visible.groups, key = { "group-${it.id}" }) { room ->
                    val members = room.memberIds.mapNotNull { id -> state.bots.firstOrNull { it.id == id } }
                    SearchRow(
                        title = room.name,
                        subtitle = room.bulletin,
                        tag = "search-group.${room.id}",
                        onClick = {
                            navigator?.let { nav ->
                                haptics.play(TactileAction.OPEN_SEARCH_RESULT)
                                nav.open(Chat.RoomChat(room))
                            }
                        },
                        leading = { RoomFaces(members = members, size = 40.dp) },
                    )
                }
            }
            if (visible.routines.isNotEmpty()) {
                item(key = "routines-header") {
                    SearchSectionLabel(stringResource(R.string.search_filter_routines), searching = false)
                }
                items(visible.routines, key = { "routine-${it.id}" }) { routine ->
                    SearchRow(
                        title = routine.name,
                        subtitle = state.bots.firstOrNull { it.id == routine.botId }?.name.orEmpty(),
                        tag = "search-routine.${routine.id}",
                        onClick = { navigator?.push(Destination.Routines) },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Seira.colors.s2, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(SeiraIcons.Clock, contentDescription = null, tint = Seira.colors.ink2, modifier = Modifier.size(20.dp))
                            }
                        },
                    )
                }
            }
            if (visible.messages.isNotEmpty() || (searching && filter != SearchFilter.BOTS && filter != SearchFilter.GROUPS && filter != SearchFilter.ROUTINES)) {
                item(key = "messages-header") {
                    SearchSectionLabel(stringResource(R.string.mobile_roster_messages), searching = searching)
                }
                items(visible.messages, key = { "hit-${it.id}" }) { hit ->
                    SearchHitRow(
                        hit = hit,
                        onClick = {
                            val nav = navigator ?: return@SearchHitRow
                            scope.launch {
                                session.open(hit)?.let {
                                    haptics.play(TactileAction.OPEN_SEARCH_RESULT)
                                    nav.open(it)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    focus: FocusRequester,
    onDone: () -> Unit,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(24.dp)
    val placeholder = stringResource(R.string.search_placeholder)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
            .testTag("page-top-bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TouchTarget(
            onClick = onBack,
            contentDescription = stringResource(R.string.mobile_back_b52b36b7),
            modifier = Modifier.testTag("page-back"),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(colors.s1, CircleShape)
                    .border(1.dp, colors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SeiraIcons.ChevronLeft, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(colors.s1, shape)
                .border(1.dp, colors.hairline, shape)
                .padding(start = 14.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SeiraIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(text = placeholder, fontSize = 16.sp, color = colors.ink3, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onDone() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .testTag("search-field")
                        .semantics { contentDescription = placeholder },
                )
            }
            if (query.isNotEmpty()) {
                TouchTarget(
                    onClick = { onQuery("") },
                    contentDescription = stringResource(R.string.search_clear),
                    modifier = Modifier.testTag("search-clear"),
                ) {
                    Icon(SeiraIcons.X, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchSectionLabel(text: String, searching: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Seira.colors.ink3,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (searching) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun SearchRow(
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
