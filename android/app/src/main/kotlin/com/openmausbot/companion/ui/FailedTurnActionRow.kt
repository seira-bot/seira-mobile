package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.CODEX_USAGE_URL
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.FailedTurnActions
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.forTask
import kotlinx.coroutines.launch

/**
 * What the person can do under a failed turn: switch the model of this
 * conversation, send the last message again, check the Codex usage page, or
 * continue when the limit resets. Only the newest failure of a bot chat shows
 * it, and never while a turn runs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FailedTurnActionRow(chat: Chat, message: Message) {
    val tool = message.tool ?: return
    if (chat !is Chat.BotChat) return
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val modelForbidden by session.modelListForbidden.collectAsState()
    val continueForbidden by session.limitResetForbidden.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val task = (state.bot(chat.bot.id) ?: chat.bot).forTask(chat.threadId)
    val running = task?.busy == true || state.pendingEdits.containsKey(chat.threadId)
    val latest = FailedTurnActions.isLatestFailure(state.transcript(chat.threadId), message)
    var switchedTo by rememberSaveable(message.id) { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var failedModel by remember { mutableStateOf<ModelSelection?>(null) }
    val offer = if (latest) {
        FailedTurnActions.offer(tool, running, modelForbidden, continueForbidden)
    } else {
        null
    }

    LaunchedEffect(offer?.tryAgain) {
        if (offer?.tryAgain == true && !modelForbidden) session.switchableModels()
    }
    if (offer == null) return

    Column(
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        switchedTo?.let {
            Text(stringResource(R.string.chat_failed_turn_now_using, it), fontSize = 13.sp, color = secondaryTint)
        }
        if (offer.askOwner) {
            Text(stringResource(R.string.chat_failed_turn_ask_owner), fontSize = 13.sp, color = secondaryTint)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (offer.tryAgain) {
                val primary = switchedTo != null
                val send = { scope.launch { session.retryLast(chat) }; Unit }
                if (primary) {
                    Button(onClick = send, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.chat_failed_turn_try_again))
                    }
                } else {
                    OutlinedButton(onClick = send, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                        Text(stringResource(R.string.chat_failed_turn_try_again))
                    }
                }
            }
            if (offer.switchModel) {
                OutlinedButton(
                    onClick = {
                        failedModel = task?.modelSelection ?: chat.bot.modelSelection
                        picking = true
                    },
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                ) { Text(stringResource(R.string.chat_failed_turn_switch_model)) }
            }
            if (offer.checkUsage) {
                OutlinedButton(
                    onClick = { runCatching { uriHandler.openUri(CODEX_USAGE_URL) } },
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                ) { Text(stringResource(R.string.chat_failed_turn_check_usage)) }
            }
            offer.continueAt?.let { resetsAt ->
                val time = android.text.format.DateFormat.getTimeFormat(LocalContext.current)
                    .format(java.util.Date(resetsAt.toLong()))
                OutlinedButton(
                    onClick = { scope.launch { session.continueAtReset(chat) } },
                    modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                ) { Text(stringResource(R.string.chat_failed_turn_continue_at, time)) }
            }
        }
    }

    val unavailable = failedModel
    if (picking && unavailable != null) {
        ModelPickerSheet(
            bot = chat.bot,
            unavailable = unavailable,
            onSwitched = { label ->
                switchedTo = label
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
