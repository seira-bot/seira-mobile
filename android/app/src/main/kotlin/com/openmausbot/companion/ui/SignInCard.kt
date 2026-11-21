package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ConnectorCardApi
import com.openmausbot.companion.core.ConnectorCards
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.SignInChoice
import com.openmausbot.companion.core.SignInOpenTarget
import com.openmausbot.companion.core.SignInRequestCardData
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType
import kotlinx.coroutines.launch

internal const val SIGNIN_CANCEL_TAG = "signin-cancel"
internal const val SIGNIN_OPEN_TAG = "signin-open"
internal const val SIGNIN_DONE_TAG = "signin-done"
internal const val SIGNIN_RETRY_TAG = "signin-retry"
internal const val SIGNIN_ERROR_TAG = "signin-error"

@Composable
internal fun SignInCardRow(chat: Chat, message: Message, startsRun: Boolean, replyBot: Bot?) {
    val card = message.signIn ?: return
    val botId = cardBotId(chat, message) ?: return
    val open = LocalSignInOpen.current
    SignInCard(
        botId = botId,
        botName = message.from?.name ?: replyBot?.name ?: chat.name,
        threadId = chat.threadId,
        message = message,
        card = card,
        api = rememberConnectorCardApi(),
        onOpen = open?.let { handler -> { target: SignInOpenTarget -> handler.open(botId, target) } },
        header = { CardHeader(chat, message, startsRun, replyBot) },
    )
}

/**
 * The card never opens [SignInRequestCardData.url]: a sign-in made in a browser on the phone stays on the
 * phone and does not reach the bot. Open goes to the bot's own browser or computer screen instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignInCard(
    botId: String,
    botName: String,
    threadId: String,
    message: Message,
    card: SignInRequestCardData,
    api: ConnectorCardApi,
    onOpen: ((SignInOpenTarget) -> Unit)?,
    header: @Composable () -> Unit = {},
) {
    val colors = Seira.colors
    val scope = rememberCoroutineScope()
    var pending by remember(message.id) { mutableStateOf<SignInChoice?>(null) }
    var localError by remember(message.id) { mutableStateOf<String?>(null) }
    val error = localError ?: card.error
    val outcome = card.outcome()
    val stopped = card.stopped == true

    fun answer(choice: SignInChoice) {
        if (pending != null) return
        pending = choice
        localError = null
        scope.launch {
            api.answerSignIn(botId, message.id, threadId, choice).onFailure { localError = it.message }
            pending = null
        }
    }

    if (card.answeredInChat == true) {
        CardClosedLine(stringResource(R.string.signin_line_answered, card.site))
        return
    }
    if (card.superseded == true || (stopped && outcome == null)) {
        CardClosedLine(
            stringResource(if (card.superseded == true) R.string.signin_line_replaced else R.string.signin_line_stopped, card.site),
        )
        return
    }
    if (outcome != null) {
        val settled = when {
            card.savedLogin != null -> stringResource(R.string.signin_settled_saved, card.savedLogin)
            outcome == SignInRequestCardData.Outcome.DONE -> stringResource(R.string.signin_settled_done, card.site)
            else -> stringResource(R.string.signin_settled_declined, card.site)
        }
        val followUp = when {
            card.resumed == true -> stringResource(R.string.signin_follow_resumed, botName)
            stopped -> stringResource(R.string.signin_follow_stopped)
            error != null -> stringResource(R.string.signin_follow_failed)
            else -> stringResource(R.string.signin_follow_waiting)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardClosedLine("$settled · $followUp")
            if (card.resumed != true && !stopped && error != null) {
                Row {
                    TextButton(
                        onClick = {
                            answer(if (outcome == SignInRequestCardData.Outcome.DONE) SignInChoice.DONE else SignInChoice.CANCEL)
                        },
                        enabled = pending == null,
                        modifier = Modifier.testTag(SIGNIN_RETRY_TAG),
                    ) { Text(stringResource(R.string.mobile_try_again_042c862e)) }
                }
            }
            error?.let {
                Text(it, style = SeiraType.caption, color = colors.danger, modifier = Modifier.testTag(SIGNIN_ERROR_TAG))
            }
        }
        return
    }

    val where = when (card.signInPlace) {
        SignInRequestCardData.Place.BROWSER -> R.string.signin_where_browser
        SignInRequestCardData.Place.VM -> R.string.signin_where_vm
        SignInRequestCardData.Place.CLOUD -> R.string.signin_where_cloud
        SignInRequestCardData.Place.LOCAL -> R.string.signin_where_local
    }
    val target = ConnectorCards.openTarget(card)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        header()
        CardSurface {
            Text(stringResource(R.string.signin_title), style = SeiraType.caption, color = colors.ink3)
            Text(card.site, style = SeiraType.title3, color = colors.ink)
            card.reason?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.signin_reason, it, botName), style = SeiraType.callout, color = colors.ink2)
            }
            Text(stringResource(where, botName), style = SeiraType.callout, color = colors.ink2)
            Text(stringResource(R.string.signin_private), style = SeiraType.caption, color = colors.ink3)
            error?.let {
                Text(it, style = SeiraType.caption, color = colors.danger, modifier = Modifier.testTag(SIGNIN_ERROR_TAG))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { answer(SignInChoice.CANCEL) },
                    enabled = pending == null,
                    modifier = Modifier.testTag(SIGNIN_CANCEL_TAG),
                ) { Text(stringResource(R.string.mobile_cancel_77dfd213)) }
                if (target != null && onOpen != null) {
                    OutlinedButton(
                        onClick = { onOpen(target) },
                        enabled = pending == null,
                        modifier = Modifier.testTag(SIGNIN_OPEN_TAG),
                    ) { Text(stringResource(R.string.mobile_open_cf9b7706)) }
                }
                Button(
                    onClick = { answer(SignInChoice.DONE) },
                    enabled = pending == null,
                    modifier = Modifier.testTag(SIGNIN_DONE_TAG),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) { Text(stringResource(R.string.mobile_done_e9b450d1)) }
            }
        }
    }
}
