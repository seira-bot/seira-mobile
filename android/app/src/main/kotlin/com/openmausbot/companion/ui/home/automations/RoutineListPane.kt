package com.openmausbot.companion.ui.home.automations

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.NotificationTarget
import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.core.RoutineRun
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.RoutineRules
import com.openmausbot.companion.ui.localizedMobileCopy
import com.openmausbot.companion.ui.localizedRoutineRunSubtitle
import com.openmausbot.companion.ui.localizedRoutineSubtitle
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween
import kotlinx.coroutines.launch

/** The List page of Automations: routines with a switch, then the run receipts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineListPane(
    onOpenChat: (Chat) -> Unit,
    onEdit: (Routine) -> Unit,
    reloadSignal: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()

    var routines by remember { mutableStateOf<List<Routine>>(emptyList()) }
    var runs by remember { mutableStateOf<List<RoutineRun>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Routine?>(null) }
    var deleting by remember { mutableStateOf<Routine?>(null) }
    val pending = remember { mutableStateMapOf<String, Boolean>() }

    suspend fun reload() {
        val loaded = session.loadRoutines()
        routines = RoutineRules.sorted(loaded.routines)
        runs = loaded.runs
        loading = false
    }
    LaunchedEffect(reloadSignal) { reload() }

    val receipts = remember(runs) { AutomationsRules.receipts(runs) }

    fun openThread(run: RoutineRun) {
        val target = NotificationTarget.from(run.botId, run.threadId) ?: return
        scope.launch { session.openNotification(target)?.let(onOpenChat) }
    }

    fun toggle(routine: Routine) {
        val target = !AutomationsRules.shownEnabled(routine, pending)
        pending[routine.id] = target
        scope.launch {
            session.setRoutineEnabled(routine, target)
            reload()
            pending.remove(routine.id)
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                reload()
                refreshing = false
            }
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("automations-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.automations_intro),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.ink2,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            if (routines.isEmpty() && !loading) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = localizedMobileCopy(RoutineRules.NO_ROUTINES_TITLE),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                        )
                        Text(
                            text = localizedMobileCopy(RoutineRules.NO_ROUTINES_DESCRIPTION),
                            fontSize = 13.sp,
                            color = colors.ink2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(routines, key = { "routine-${it.id}" }) { routine ->
                HorizontalDivider(color = colors.hairline)
                RoutineRow(
                    routine = routine,
                    subtitle = localizedRoutineSubtitle(routine, state.bot(routine.botId)?.name),
                    enabled = AutomationsRules.shownEnabled(routine, pending),
                    onOpen = { menuFor = routine },
                    onToggle = { toggle(routine) },
                    onMenu = { menuFor = routine },
                )
            }

            if (routines.isNotEmpty()) {
                item(key = "list-end") { HorizontalDivider(color = colors.hairline) }
            }

            item(key = "receipts-header") {
                Text(
                    text = stringResource(R.string.mobile_run_receipts_9115568f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink3,
                    modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
                )
            }

            if (receipts.isEmpty() && !loading) {
                item(key = "receipts-empty") {
                    Text(
                        text = localizedMobileCopy(RoutineRules.NO_RECEIPTS),
                        fontSize = 13.sp,
                        color = colors.ink2,
                    )
                }
            }

            items(receipts, key = { "receipt-${it.id}" }) { run ->
                ReceiptRow(
                    run = run,
                    subtitle = localizedRoutineRunSubtitle(run, state.bot(run.botId)?.name),
                    onClick = { openThread(run) },
                )
            }
        }
    }

    menuFor?.let { routine ->
        RoutineActionSheet(
            routine = routine,
            runs = runs,
            pending = pending,
            onRunNow = {
                scope.launch {
                    session.runRoutine(routine)
                    reload()
                }
            },
            onEdit = { onEdit(routine) },
            onToggle = { toggle(routine) },
            onOpenResults = { AutomationsRules.resultsRun(routine.id, runs)?.let(::openThread) },
            onDelete = { deleting = routine },
            onDismiss = { menuFor = null },
        )
    }

    deleting?.let { routine ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.mobile_delete_routine_name_41a4c8bf, routine.name)) },
            text = { Text(localizedMobileCopy(RoutineRules.DELETE_MESSAGE)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch {
                            if (session.deleteRoutine(routine)) reload()
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.mobile_delete_routine_0cb076a0),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.mobile_cancel_77dfd213))
                }
            },
        )
    }
}

@Composable
private fun RoutineRow(
    routine: Routine,
    subtitle: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = Seira.colors
    val finished = !routine.canToggle()
    val switchLabel = when {
        finished -> stringResource(R.string.automations_switch_finished, routine.name)
        enabled -> stringResource(R.string.automations_pause_named, routine.name)
        else -> stringResource(R.string.automations_resume_named, routine.name)
    }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("routine-${routine.id}"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 66.dp)
                .clickable(role = Role.Button, onClick = onOpen),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = routine.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = colors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        RoutineSwitch(
            checked = enabled,
            enabled = !finished,
            contentDescription = switchLabel,
            onCheckedChange = { onToggle() },
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.mobile_actions_for_routine_name_767f1549, routine.name),
                tint = colors.ink2,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** On is `success`. The whole 48 dp cell takes the tap. */
@Composable
private fun RoutineSwitch(
    checked: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val track by animateColorAsState(
        if (checked) colors.success else colors.switchTrackOff,
        seiraTween(SeiraMotion.BASE, reduced),
        label = "routine-switch-track",
    )
    val travel by animateDpAsState(
        if (checked) 20.dp else 0.dp,
        seiraTween(SeiraMotion.BASE, reduced),
        label = "routine-switch-travel",
    )
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(width = 51.dp, height = 31.dp).background(track, CircleShape))
        Box(
            modifier = Modifier
                .offset(x = (-10).dp + travel)
                .size(27.dp)
                .background(colors.switchKnobOn, CircleShape),
        )
    }
}

@Composable
private fun ReceiptRow(run: RoutineRun, subtitle: String, onClick: () -> Unit) {
    val colors = Seira.colors
    val tone = AutomationsRules.tone(run.status)
    val tint = when (tone) {
        ReceiptTone.SUCCESS -> colors.success
        ReceiptTone.DANGER -> colors.danger
        ReceiptTone.WARNING -> colors.warning
        ReceiptTone.QUIET -> colors.ink3
    }
    val label = when (tone) {
        ReceiptTone.SUCCESS -> stringResource(R.string.mobile_completed_1798b3ba)
        ReceiptTone.DANGER -> stringResource(R.string.automations_status_failed)
        else -> localizedMobileCopy(RoutineRules.runStatusLabel(run.status))
    }
    val hasThread = NotificationTarget.from(run.botId, run.threadId) != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = hasThread, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = run.routineName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = subtitle, fontSize = 12.5.sp, color = colors.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(text = label, fontSize = 13.sp, color = tint)
    }
}
