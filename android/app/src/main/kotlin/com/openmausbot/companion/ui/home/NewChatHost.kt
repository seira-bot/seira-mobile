package com.openmausbot.companion.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.chatSummaries
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.NewChatSheet
import com.openmausbot.companion.ui.NewGroupSheet
import com.openmausbot.companion.ui.SectionRules
import com.openmausbot.companion.ui.TactileAction
import com.openmausbot.companion.ui.rememberHaptics
import kotlinx.coroutines.launch

/**
 * The New chat sheet and what it starts: a thread with a bot, a group, a bot or a
 * department. It stays in composition while hidden so that a creation that is
 * still running when the sheet closes can finish and open its chat.
 */
@Composable
internal fun NewChatHost(
    show: Boolean,
    navigator: CompanionNavigator,
    onDismiss: () -> Unit,
    onNewDepartment: () -> Unit,
) {
    val environment = LocalCompanion.current
    val session = environment.session
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val state by session.state.collectAsState()
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    var creatingBot by remember { mutableStateOf(false) }
    var creatingThreadFor by remember { mutableStateOf(emptySet<String>()) }
    var group by remember { mutableStateOf(false) }

    LaunchedEffect(show) { if (!show) group = false }
    if (!show) return

    val bots = remember(state, activityDetail) {
        state.chatSummaries(activityDetail).mapNotNull { (it.chat as? Chat.BotChat)?.bot }
    }
    val createThread: (Bot) -> Unit = { bot ->
        if (bot.id !in creatingThreadFor) {
            creatingThreadFor = creatingThreadFor + bot.id
            scope.launch {
                try {
                    val created = session.createTask(bot, title = null)
                    if (created != null) {
                        navigator.open(Chat.BotChat(created))
                    } else if (session.actionError == null) {
                        session.actionError = "Couldn't create a thread. Check the connection and try again."
                    }
                } finally {
                    creatingThreadFor = creatingThreadFor - bot.id
                }
            }
        }
    }

    if (group) {
        NewGroupSheet(
            onCreated = { room ->
                onDismiss()
                navigator.open(Chat.RoomChat(room))
            },
            onDismiss = onDismiss,
        )
        return
    }

    NewChatSheet(
        bots = bots,
        canCreateBot = !creatingBot,
        canOrganize = remember(state) { SectionRules.selectable(state).isNotEmpty() },
        onBot = { bot ->
            onDismiss()
            createThread(bot)
        },
        onNewGroup = {
            haptics.play(TactileAction.START_NEW_GROUP)
            group = true
        },
        onNewBot = {
            onDismiss()
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
            onDismiss()
            haptics.play(TactileAction.START_NEW_SECTION)
            onNewDepartment()
        },
        onDismiss = onDismiss,
    )
}
