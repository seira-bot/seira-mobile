package com.openmausbot.companion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.core.RoutineRun
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.RelativeStamp
import com.openmausbot.companion.ui.home.inbox.DecisionCard
import com.openmausbot.companion.ui.home.inbox.InboxDecisions
import com.openmausbot.companion.ui.home.inbox.InboxNews
import com.openmausbot.companion.ui.home.inbox.NewsItem
import com.openmausbot.companion.ui.home.inbox.TitleSegment
import com.openmausbot.companion.ui.localizedMobileCopy
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.updates
import kotlinx.coroutines.launch

/** The Inbox tab: what waits for your answer, then the news. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InboxTab(navigator: CompanionNavigator) {
    val colors = Seira.colors
    val environment = LocalCompanion.current
    val session = environment.session
    val state by session.state.collectAsState()
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    val scope = rememberCoroutineScope()

    var routines by remember { mutableStateOf<List<Routine>>(emptyList()) }
    var runs by remember { mutableStateOf<List<RoutineRun>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    val retrying = remember { mutableStateListOf<String>() }

    suspend fun reload() {
        val loaded = session.loadRoutines()
        routines = loaded.routines
        runs = loaded.runs
    }
    LaunchedEffect(Unit) { reload() }

    val updates = remember(state, activityDetail) { state.updates(activityDetail) }
    val decisions = remember(updates) { InboxDecisions.decisions(updates) }
    val news = remember(updates, routines, runs) {
        InboxNews.build(updates, routines, runs, System.currentTimeMillis())
    }
    val now = remember(news) { System.currentTimeMillis() }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                reload()
                refreshing = false
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("inbox-tab"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "title") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    TitleSegment(
                        label = stringResource(R.string.shell_tab_inbox),
                        selected = true,
                        onClick = null,
                        tag = "inbox-title",
                    )
                }
            }

            item(key = "decisions-header") {
                SectionLabel(stringResource(R.string.inbox_needs_decision))
            }

            if (decisions.isEmpty()) {
                item(key = "empty") { InboxEmpty() }
            }

            items(decisions, key = { it.id }) { update ->
                DecisionCard(
                    update = update,
                    onOpenChat = { navigator.open(update.chat) },
                )
            }

            if (news.isNotEmpty()) {
                item(key = "news-header") {
                    SectionLabel(stringResource(R.string.inbox_news), modifier = Modifier.padding(top = 6.dp))
                }
            }

            items(news, key = { it.id }) { item ->
                when (item) {
                    is NewsItem.Finished -> NewsRow(
                        dot = colors.success,
                        title = stringResource(R.string.inbox_news_finished, item.update.chat.name),
                        detail = item.update.line.trim().takeIf { it.isNotEmpty() },
                        time = item.at?.let { localizedMobileCopy(RelativeStamp.list(it, now)) },
                        onClick = { navigator.open(item.update.chat) },
                    )
                    is NewsItem.RoutineProblem -> NewsRow(
                        dot = colors.danger,
                        title = stringResource(R.string.inbox_news_routine_failed, item.routine.name),
                        detail = null,
                        time = item.at?.let { localizedMobileCopy(RelativeStamp.list(it, now)) },
                        enabled = item.run.id !in retrying,
                        onClick = {
                            retrying += item.run.id
                            scope.launch {
                                session.runRoutine(item.routine)
                                reload()
                                retrying -= item.run.id
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Seira.colors.ink3,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun InboxEmpty() {
    val colors = Seira.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.hairline, shape)
            .padding(horizontal = 10.dp, vertical = 26.dp)
            .testTag("inbox-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.inbox_empty_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.inbox_empty_body),
            fontSize = 13.5.sp,
            color = colors.ink2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NewsRow(
    dot: androidx.compose.ui.graphics.Color,
    title: String,
    detail: String?,
    time: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(9.dp)
                .background(dot, CircleShape),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.ink else colors.ink3,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    fontSize = 13.sp,
                    color = colors.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!time.isNullOrEmpty()) {
            Text(text = time, fontSize = 12.sp, color = colors.ink3, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
