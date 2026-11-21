package com.openmausbot.companion.ui

import android.text.format.DateUtils
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.CallRecord
import com.openmausbot.companion.core.CallRecords
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Message

/** How many spoken lines the row shows under its title; the sheet shows all of them. */
internal const val CALL_ROW_LINES = 3

/**
 * A finished Live call as one centered row: its title, how long it ran and what
 * the person said. A tap opens the whole call. What was said also stays in the
 * chat as ordinary bubbles. The caller shows `text` instead when [call] is missing.
 */
@Composable
internal fun CallRecordRow(chat: Chat, call: CallRecord) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val haptics = rememberHaptics()
    var open by remember(call.callId) { mutableStateOf(false) }
    val transcript = remember(state, chat.threadId) { state.visibleTranscript(chat.threadId) }
    val lines = remember(transcript, call.callId) { CallRecords.callRecordLines(transcript, call.callId) }
    val partial = remember(transcript, call, state) {
        CallRecords.isPartial(transcript, call, state.hasMore[chat.threadId] == true)
    }
    val botName = state.bot(call.botId)?.name ?: chat.name
    val title = call.title?.trim()?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.call_record_title, botName)
    val duration = CallRecords.formatCallDuration(call.seconds)
    val description = stringResource(R.string.call_record_open, title, duration)
    val shape = RoundedCornerShape(18.dp)

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .border(1.dp, secondaryTint.copy(alpha = 0.25f), shape)
                .clickable(role = Role.Button) {
                    haptics.play(TactileAction.TOGGLE_ACTIVITY_RUN)
                    open = true
                }
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.heightIn(min = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Call, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Text(
                    title,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("· $duration", fontSize = 13.sp, color = secondaryTint, maxLines = 1)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = secondaryTint, modifier = Modifier.size(14.dp))
            }
            lines.take(CALL_ROW_LINES).forEach { line ->
                Text(line.text.orEmpty().trim(), fontSize = 13.sp, color = secondaryTint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (partial) {
                Text(stringResource(R.string.call_record_partial), fontSize = 12.sp, color = secondaryTint)
            }
        }
    }

    if (open) {
        CallSheet(title, duration, call, lines, partial) { open = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallSheet(
    title: String,
    duration: String,
    call: CallRecord,
    lines: List<Message>,
    partial: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    val started = DateUtils.formatDateTime(context, call.startedAt.toLong(), flags)
    val ended = DateUtils.formatDateTime(context, call.endedAt.toLong(), flags)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("$title · $duration", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (call.startedAt > 0) Text(stringResource(R.string.call_record_started, started), fontSize = 13.sp, color = secondaryTint)
            if (call.endedAt > 0) Text(stringResource(R.string.call_record_ended, ended), fontSize = 13.sp, color = secondaryTint)
            if (partial) Text(stringResource(R.string.call_record_partial), fontSize = 12.sp, color = secondaryTint)
            Text(
                stringResource(R.string.call_record_said),
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (lines.isEmpty()) {
                Text(stringResource(R.string.call_record_nothing), fontSize = 14.sp, color = secondaryTint)
            } else {
                SelectionContainer {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(lines, key = { _, line -> line.id }) { _, line ->
                            Text(line.text.orEmpty().trim(), fontSize = 15.sp)
                        }
                    }
                }
            }
            Box(Modifier.size(12.dp))
        }
    }
}
