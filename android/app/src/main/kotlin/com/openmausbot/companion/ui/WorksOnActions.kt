package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ConfigStatus
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.WorksOnChoice
import com.openmausbot.companion.core.WorksOnResult
import com.openmausbot.companion.core.forTask
import kotlinx.coroutines.launch

/**
 * Under the newest cloud-computer notice of a bot chat, a phone that may
 * administer can move the bot to no computer or to its own server.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorksOnActions(chat: Chat, message: Message) {
    val tool = message.tool ?: return
    if (chat !is Chat.BotChat || !WorksOnChoice.isNotice(tool)) return
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    val admin = session.canAdminister
    val newest = WorksOnChoice.noticeMessageId(state.transcript(chat.threadId)) == message.id
    val bot = state.bot(chat.bot.id) ?: chat.bot
    val running = bot.forTask(chat.threadId)?.busy == true || state.pendingEdits.containsKey(chat.threadId)
    var config by remember { mutableStateOf<ConfigStatus?>(null) }
    var changing by remember { mutableStateOf(false) }
    var changedToOwnServer by rememberSaveable(message.id) { mutableStateOf<Boolean?>(null) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    val shows = newest && admin
    LaunchedEffect(shows) {
        if (shows && bot.vpsAlias.isNullOrBlank()) config = session.configStatus()
    }
    if (!shows) return

    val offer = WorksOnChoice.offer(
        admin, running, bot.computer, bot.cloudBackend, WorksOnChoice.ownServerReady(bot, config),
    )
    if (offer == null && changedToOwnServer == null) return

    val change = { ownServer: Boolean ->
        changing = true
        refusal = null
        failed = false
        scope.launch {
            when (val result = session.setWorksOn(bot, ownServer)) {
                is WorksOnResult.Changed -> changedToOwnServer = ownServer
                is WorksOnResult.Refused -> {
                    refusal = result.sentence
                    failed = result.sentence == null
                }
            }
            changing = false
        }
        Unit
    }

    Column(
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        changedToOwnServer?.let {
            Text(
                stringResource(if (it) R.string.chat_works_on_now_own_server else R.string.chat_works_on_now_without_computer),
                fontSize = 13.sp,
                color = secondaryTint,
            )
        }
        refusal?.let { Text(it, fontSize = 13.sp, color = secondaryTint) }
        if (failed) Text(stringResource(R.string.chat_works_on_failed), fontSize = 13.sp, color = secondaryTint)
        if (offer != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (offer.withoutComputer) {
                    OutlinedButton(
                        onClick = { change(false) },
                        enabled = !changing,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                    ) { Text(stringResource(R.string.chat_works_on_without_computer)) }
                }
                if (offer.ownServer) {
                    OutlinedButton(
                        onClick = { change(true) },
                        enabled = !changing,
                        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET),
                    ) { Text(stringResource(R.string.chat_works_on_own_server)) }
                }
            }
        }
    }
}
