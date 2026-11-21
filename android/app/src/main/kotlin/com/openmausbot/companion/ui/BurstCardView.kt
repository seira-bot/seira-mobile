package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.BurstAction
import com.openmausbot.companion.core.BurstActionRunner
import com.openmausbot.companion.core.BurstCard
import com.openmausbot.companion.core.BurstChildRow
import com.openmausbot.companion.core.BurstEdit
import com.openmausbot.companion.core.BurstView
import com.openmausbot.companion.core.Bursts
import com.openmausbot.companion.core.GoalRuns
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.ThreadRef
import kotlinx.coroutines.launch

internal fun burstChildStatusRes(status: String): Int? = when (status) {
    "queued" -> R.string.chat_burst_status_queued
    "running" -> R.string.chat_burst_status_running
    "done" -> R.string.chat_burst_status_done
    "failed" -> R.string.chat_burst_status_failed
    "cancelled" -> R.string.chat_burst_status_cancelled
    else -> null
}

/**
 * One burst in its chat: a preview with Run, Edit and Cancel, then a progress
 * card with Show tasks, Retry failed and Cancel all. Show tasks and Edit open a
 * bottom sheet. The caller shows `text` instead when [card] is missing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BurstCardView(message: Message, card: BurstCard, openThread: ((ThreadRef) -> Unit)?) {
    val session = LocalCompanion.current.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val fallback = stringResource(R.string.chat_burst_error_action)
    val runner = remember(message.id, fallback) {
        BurstActionRunner({ action, edit -> session.burstAction(card.burstId, action, edit) }, fallback)
    }
    val request by runner.state.collectAsState()
    var showTasks by remember(message.id) { mutableStateOf(false) }
    var editing by remember(message.id) { mutableStateOf(false) }

    val headline = burstHeadline(card)
    val done = card.count("done")
    val failedNames = card.failed.orEmpty().joinToString(", ") { it.title }
    val shape = RoundedCornerShape(18.dp)
    val description = stringResource(R.string.chat_burst_status_label, headline)

    Column(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .background(secondaryTint.copy(alpha = 0.10f), shape)
            .border(1.dp, secondaryTint.copy(alpha = 0.18f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (card.isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(R.string.chat_burst_title, GoalRuns.compact(card.task, Bursts.TASK_LIMIT)),
                modifier = Modifier.semantics { contentDescription = description },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.isPreview) {
            Text(
                stringResource(R.string.chat_burst_plan, card.total, card.modelName, card.parallel, card.estimateMinutes),
                fontSize = 12.sp,
                color = secondaryTint,
            )
            val more = card.total - card.sample.size
            val names = card.sample.joinToString(" · ") +
                if (more > 0) " · " + stringResource(R.string.chat_burst_more_items, more) else ""
            if (names.isNotBlank()) Text(names, fontSize = 12.sp, color = secondaryTint, maxLines = 2, overflow = TextOverflow.Ellipsis)
            card.taskLimit?.let { limit ->
                Text(
                    stringResource(R.string.chat_burst_limit_cut, card.total, card.requested, limit),
                    fontSize = 12.sp,
                    color = secondaryTint,
                )
            }
        } else {
            Text(headline, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            burstCountsLine(card)?.let { Text(it, fontSize = 12.sp, color = secondaryTint) }
            if (failedNames.isNotEmpty()) {
                val more = card.failedMore ?: 0
                Text(
                    if (more > 0) {
                        stringResource(R.string.chat_burst_not_finished_more, failedNames, more)
                    } else {
                        stringResource(R.string.chat_burst_not_finished, failedNames)
                    },
                    fontSize = 12.sp,
                    color = secondaryTint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        request.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val idle = !request.busy
            if (card.isPreview) {
                Button(
                    enabled = idle,
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                    onClick = {
                        haptics.play(TactileAction.CHOOSE_APPROVAL)
                        scope.launch { runner.press(BurstAction.RUN) }
                    },
                ) { Text(stringResource(R.string.chat_burst_run), fontSize = 13.sp) }
                OutlinedButton(
                    enabled = idle,
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                    onClick = {
                        haptics.play(TactileAction.TOGGLE_ACTIVITY_RUN)
                        editing = true
                    },
                ) { Text(stringResource(R.string.chat_burst_edit), fontSize = 13.sp) }
                OutlinedButton(
                    enabled = idle,
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                    onClick = {
                        haptics.play(TactileAction.CHOOSE_APPROVAL)
                        scope.launch { runner.press(BurstAction.CANCEL) }
                    },
                ) { Text(stringResource(R.string.chat_burst_cancel), fontSize = 13.sp) }
            } else {
                OutlinedButton(
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                    onClick = {
                        haptics.play(TactileAction.TOGGLE_ACTIVITY_RUN)
                        showTasks = true
                    },
                ) { Text(stringResource(R.string.chat_burst_show_tasks), fontSize = 13.sp) }
                if (card.canRetry) {
                    OutlinedButton(
                        enabled = idle,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                        onClick = {
                            haptics.play(TactileAction.CHOOSE_APPROVAL)
                            scope.launch { runner.press(BurstAction.RETRY) }
                        },
                    ) { Text(stringResource(R.string.chat_burst_retry_failed), fontSize = 13.sp) }
                }
                if (card.isActive) {
                    OutlinedButton(
                        enabled = idle,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                        onClick = {
                            haptics.play(TactileAction.CHOOSE_APPROVAL)
                            scope.launch { runner.press(BurstAction.CANCEL) }
                        },
                    ) { Text(stringResource(R.string.chat_burst_cancel_all), fontSize = 13.sp) }
                }
            }
        }
    }

    if (showTasks) {
        BurstTasksSheet(
            card = card,
            doneCount = done,
            openThread = openThread,
            onDismiss = { showTasks = false },
        )
    }
    if (editing) {
        BurstEditSheet(
            card = card,
            busy = request.busy,
            error = request.error,
            onSave = { edit ->
                scope.launch { if (runner.press(BurstAction.EDIT, edit)) editing = false }
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun burstHeadline(card: BurstCard): String {
    val progress = stringResource(R.string.chat_burst_progress, card.count("done"), card.total)
    return when (card.phase) {
        "merging" -> stringResource(R.string.chat_burst_phase_merging)
        "done" -> stringResource(R.string.chat_burst_phase_done) + " · " + progress
        "cancelled" -> stringResource(R.string.chat_burst_phase_cancelled)
        else -> progress
    }
}

@Composable
private fun burstCountsLine(card: BurstCard): String? {
    val parts = Bursts.countParts(card).map { (status, count) ->
        when (status) {
            "running" -> stringResource(R.string.chat_burst_running, count)
            "queued" -> stringResource(R.string.chat_burst_waiting, count)
            else -> stringResource(R.string.chat_burst_failed, count)
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BurstTasksSheet(
    card: BurstCard,
    doneCount: Int,
    openThread: ((ThreadRef) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val session = LocalCompanion.current.session
    val haptics = rememberHaptics()
    var view by remember { mutableStateOf<BurstView?>(null) }
    var failed by remember { mutableStateOf(false) }
    // The list is read again each time the card's counts move.
    val moved = "${card.phase}:${card.counts.toSortedMap()}:$doneCount"
    LaunchedEffect(card.burstId, moved) {
        val next = session.burstView(card.burstId)
        if (next != null) view = next
        failed = next == null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.chat_burst_tasks_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            val rows = view?.children
            if (rows == null) {
                if (failed) {
                    Text(stringResource(R.string.chat_burst_error_action), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(rows, key = { it.seq }) { row ->
                        BurstTaskRow(row, openable = openThread != null) {
                            haptics.play(TactileAction.OPEN_THREAD_CHIP)
                            val threadId = row.threadId ?: return@BurstTaskRow
                            onDismiss()
                            openThread?.invoke(ThreadRef(card.botId, threadId, row.title))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BurstTaskRow(row: BurstChildRow, openable: Boolean, onOpen: () -> Unit) {
    val statusText = burstChildStatusRes(row.status)?.let { stringResource(it) } ?: row.status
    val opens = openable && Bursts.opensThread(row)
    val tint = when (row.status) {
        "done" -> Color(BotPalette.argb("green"))
        "failed" -> MaterialTheme.colorScheme.error
        "running" -> MaterialTheme.colorScheme.primary
        else -> secondaryTint
    }
    val openLabel = stringResource(R.string.chat_burst_row_open, row.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .then(
                if (opens) {
                    Modifier
                        .semantics { contentDescription = openLabel }
                        .clickable(role = Role.Button, onClick = onOpen)
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when (row.status) {
                "running" -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = tint)
                "done" -> Icon(Icons.Filled.CheckCircle, null, tint = tint, modifier = Modifier.size(16.dp))
                "failed", "cancelled" -> Icon(Icons.Filled.Close, null, tint = tint, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.Filled.Info, null, tint = tint, modifier = Modifier.size(16.dp))
            }
        }
        Text(row.title, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(statusText, fontSize = 12.sp, color = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BurstEditSheet(
    card: BurstCard,
    busy: Boolean,
    error: String?,
    onSave: (BurstEdit) -> Unit,
    onDismiss: () -> Unit,
) {
    val session = LocalCompanion.current.session
    var view by remember { mutableStateOf<BurstView?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(card.burstId) {
        view = session.burstView(card.burstId)
        failed = view == null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.chat_burst_edit_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            val loaded = view
            if (loaded == null) {
                if (failed) {
                    Text(stringResource(R.string.chat_burst_error_action), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.chat_burst_back))
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                var instructions by remember(loaded) { mutableStateOf(loaded.instructions) }
                var items by remember(loaded) { mutableStateOf(loaded.items.joinToString("\n")) }
                val lines = Bursts.parseItems(items)
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(stringResource(R.string.chat_burst_edit_instructions)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it },
                    label = { Text(stringResource(R.string.chat_burst_edit_items)) },
                    minLines = 3,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = Bursts.canSave(lines, busy),
                        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                        onClick = { onSave(BurstEdit(instructions, lines)) },
                    ) { Text(stringResource(R.string.chat_burst_save)) }
                    TextButton(
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                        onClick = onDismiss,
                    ) { Text(stringResource(R.string.chat_burst_back)) }
                }
            }
            Box(Modifier.size(8.dp))
        }
    }
}
