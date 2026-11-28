package bot.seira.companion.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import bot.seira.companion.core.Bot
import bot.seira.companion.core.Chat
import bot.seira.companion.core.chatSummaries
import bot.seira.companion.ui.CompanionNavigator
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.NewChatSheet
import bot.seira.companion.ui.NewGroupSheet
import bot.seira.companion.ui.SectionRules
import bot.seira.companion.ui.TactileAction
import bot.seira.companion.ui.home.chats.NewBotRules
import bot.seira.companion.ui.home.chats.NewBotSheet
import bot.seira.companion.ui.play
import bot.seira.companion.ui.rememberHaptics
import kotlinx.coroutines.launch

private enum class NewChatForm { GROUP, BOT }

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
    var botFailed by remember { mutableStateOf(false) }
    var creatingThreadFor by remember { mutableStateOf(emptySet<String>()) }
    var form by remember { mutableStateOf<NewChatForm?>(null) }

    LaunchedEffect(show) {
        if (!show) {
            form = null
            botFailed = false
        }
    }
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

    if (form == NewChatForm.BOT) {
        NewBotSheet(
            creating = creatingBot,
            failed = botFailed,
            onCreate = { name, job, retryKey ->
                if (!creatingBot) {
                    creatingBot = true
                    botFailed = false
                    scope.launch {
                        try {
                            val bot = session.createBot(NewBotRules.spec(name, job, retryKey))
                            if (bot != null) {
                                haptics.play(TactileAction.CREATE_BOT_SUCCESS)
                                onDismiss()
                                navigator.open(Chat.BotChat(bot))
                            } else {
                                session.actionError = null
                                botFailed = true
                            }
                        } finally {
                            creatingBot = false
                        }
                    }
                }
            },
            onDismiss = onDismiss,
        )
        return
    }

    if (form == NewChatForm.GROUP) {
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
            form = NewChatForm.GROUP
        },
        onNewBot = {
            botFailed = false
            form = NewChatForm.BOT
        },
        onOrganize = {
            onDismiss()
            haptics.play(TactileAction.START_NEW_SECTION)
            onNewDepartment()
        },
        onDismiss = onDismiss,
    )
}
