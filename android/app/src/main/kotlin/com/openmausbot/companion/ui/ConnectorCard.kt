package com.openmausbot.companion.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.openmausbot.companion.R
import com.openmausbot.companion.core.APIError
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ConnectorCardApi
import com.openmausbot.companion.core.ConnectorCardData
import com.openmausbot.companion.core.ConnectorCards
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.SignInChoice
import com.openmausbot.companion.core.SignInOpenTarget
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType
import java.net.URI
import kotlinx.coroutines.launch

internal const val CONNECTOR_CONNECT_TAG = "connector-connect"
internal const val CONNECTOR_DISMISS_TAG = "connector-dismiss"
internal const val CONNECTOR_CONTINUE_TAG = "connector-continue"
internal const val CONNECTOR_PAGE_LINK_TAG = "connector-page-link"
internal const val CONNECTOR_ERROR_TAG = "connector-error"

/** Where a sign-in card's Open goes: the bot's browser or computer screen. Null leaves Open off. */
fun interface SignInOpen {
    fun open(botId: String, target: SignInOpenTarget)
}

internal val LocalSignInOpen = compositionLocalOf<SignInOpen?> { null }

/** A test replaces the computer the cards talk to. */
internal val LocalConnectorCardApi = compositionLocalOf<ConnectorCardApi?> { null }

/** A test replaces the browser tab that shows a connection page. */
internal val LocalConnectionPageOpener = compositionLocalOf<((URI) -> Boolean)?> { null }

internal class SessionConnectorCardApi(private val session: Session) : ConnectorCardApi {
    override suspend fun authorize(botId: String, messageId: String, threadId: String) =
        session.authorizeConnectorCard(botId, messageId, threadId)

    override suspend fun status(botId: String, messageId: String, threadId: String) =
        session.connectorCardStatus(botId, messageId, threadId)

    override suspend fun resume(botId: String, messageId: String, threadId: String) =
        session.resumeConnectorCard(botId, messageId, threadId)

    override suspend fun dismiss(botId: String, messageId: String, threadId: String) =
        session.dismissConnectorCard(botId, messageId, threadId)

    override suspend fun answerSignIn(botId: String, messageId: String, threadId: String, choice: SignInChoice) =
        session.answerSignIn(botId, messageId, threadId, choice)
}

/** The bot a card belongs to: this chat's bot, or in a room the member who sent it. */
internal fun cardBotId(chat: Chat, message: Message): String? = when (chat) {
    is Chat.BotChat -> chat.bot.id
    is Chat.RoomChat -> message.from?.botId
}

/** Opens an https link in a Custom Tab. Returns false when nothing on the phone can show it. */
internal fun openConnectionPage(context: Context, url: URI): Boolean {
    if (ConnectorCards.secureLink(url.toString()) == null) return false
    return try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(url.toASCIIString()))
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
internal fun rememberConnectorCardApi(): ConnectorCardApi {
    val session = LocalCompanion.current.session
    val injected = LocalConnectorCardApi.current
    return injected ?: remember(session) { SessionConnectorCardApi(session) }
}

@Composable
internal fun ConnectorCardRow(chat: Chat, message: Message, startsRun: Boolean, replyBot: Bot?) {
    val card = message.connector ?: return
    val botId = cardBotId(chat, message) ?: return
    val context = LocalContext.current
    val opener = LocalConnectionPageOpener.current ?: { url: URI -> openConnectionPage(context, url) }
    ConnectorCard(
        botId = botId,
        threadId = chat.threadId,
        message = message,
        card = card,
        api = rememberConnectorCardApi(),
        openPage = opener,
        header = { CardHeader(chat, message, startsRun, replyBot) },
    )
}

@Composable
internal fun CardHeader(chat: Chat, message: Message, startsRun: Boolean, replyBot: Bot?) {
    if (message.role == Message.Role.BOT && startsRun) {
        ReplyHeader(
            bot = replyBot,
            name = message.from?.name ?: replyBot?.name ?: chat.name,
            colorName = message.from?.color ?: replyBot?.color,
            at = message.at,
        )
    }
}

@Composable
internal fun CardClosedLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = SeiraType.callout,
        color = Seira.colors.ink2,
    )
}

@Composable
internal fun CardSurface(content: @Composable () -> Unit) {
    val colors = Seira.colors
    Surface(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.tint,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConnectorCard(
    botId: String,
    threadId: String,
    message: Message,
    card: ConnectorCardData,
    api: ConnectorCardApi,
    openPage: (URI) -> Boolean,
    now: () -> Long = System::currentTimeMillis,
    header: @Composable () -> Unit = {},
) {
    val colors = Seira.colors
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val status = card.connectorStatus
    val gone = card.isGone
    var busy by remember(message.id) { mutableStateOf(false) }
    var error by remember(message.id) { mutableStateOf<String?>(null) }
    var refusal by remember(message.id) { mutableStateOf<String?>(null) }
    var link by remember(message.id) { mutableStateOf<URI?>(null) }
    var pageMissed by remember(message.id) { mutableStateOf(false) }
    var dismissed by remember(message.id) { mutableStateOf(false) }
    val watch = remember(message.id) { longArrayOf(0L) }
    val badLink = stringResource(R.string.connector_bad_link)

    // Ask only while the screen shows: leaving it cancels the loop, and coming back asks at once.
    LaunchedEffect(message.id, status, gone) {
        if (status != ConnectorCardData.Status.AUTHORIZING || gone) return@LaunchedEffect
        if (watch[0] == 0L) watch[0] = now()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            ConnectorCards.watchStatus(watch[0], now) {
                api.status(botId, message.id, threadId).getOrThrow().connected
            }
        }
    }

    if (gone || dismissed) return

    val stopped = card.stopped == true
    if (stopped && status != ConnectorCardData.Status.CONNECTED) {
        CardClosedLine(stringResource(R.string.connector_line_stopped, card.label))
        return
    }

    if (status == ConnectorCardData.Status.CONNECTED) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val resumed = card.resumed == true
            CardClosedLine(
                stringResource(
                    if (resumed) R.string.connector_line_connected_continuing else R.string.connector_line_connected,
                    card.label,
                ),
            )
            if (!resumed && !stopped) {
                Row {
                    TextButton(
                        onClick = {
                            if (busy) return@TextButton
                            busy = true
                            error = null
                            scope.launch {
                                api.resume(botId, message.id, threadId).onFailure { error = it.message }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.testTag(CONNECTOR_CONTINUE_TAG),
                    ) { Text(stringResource(R.string.connector_continue)) }
                }
            }
            (error ?: card.error)?.let {
                Text(it, style = SeiraType.caption, color = colors.danger, modifier = Modifier.testTag(CONNECTOR_ERROR_TAG))
            }
        }
        return
    }

    val authorizing = status == ConnectorCardData.Status.AUTHORIZING
    val shownError = error ?: card.error
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        header()
        CardSurface {
            Text(
                if (card.isMcp) stringResource(R.string.connector_title_mcp, card.label) else card.label,
                style = SeiraType.title3,
                color = colors.ink,
            )
            if (card.description.isNotEmpty()) {
                Text(card.description, style = SeiraType.body, color = colors.ink2)
            }
            Text(
                stringResource(if (card.isMcp) R.string.connector_hint_mcp else R.string.connector_hint_app),
                style = SeiraType.caption,
                color = colors.ink3,
            )
            refusal?.let { Text(it, style = SeiraType.caption, color = colors.ink2) }
            shownError?.let {
                Text(it, style = SeiraType.caption, color = colors.danger, modifier = Modifier.testTag(CONNECTOR_ERROR_TAG))
            }
            if (pageMissed) {
                Text(stringResource(R.string.connector_link_not_opened), style = SeiraType.caption, color = colors.ink2)
                link?.let { url ->
                    TextButton(
                        onClick = { pageMissed = !openPage(url) },
                        modifier = Modifier.testTag(CONNECTOR_PAGE_LINK_TAG),
                    ) { Text(stringResource(R.string.connector_open_page)) }
                }
            }
            Text(
                stringResource(if (authorizing) R.string.connector_waiting else R.string.connector_requested),
                style = SeiraType.caption,
                color = colors.ink3,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        error = null
                        refusal = null
                        pageMissed = false
                        scope.launch {
                            api.authorize(botId, message.id, threadId)
                                .onSuccess { url ->
                                    val safe = ConnectorCards.secureLink(url.toString())
                                    if (safe == null) {
                                        error = badLink
                                    } else {
                                        link = safe
                                        pageMissed = !openPage(safe)
                                    }
                                }
                                .onFailure { failure ->
                                    when {
                                        failure is APIError.BadUrl -> error = badLink
                                        card.isMcp && failure is APIError.Status && failure.code == 409 -> refusal = failure.message
                                        else -> error = failure.message
                                    }
                                }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag(CONNECTOR_CONNECT_TAG),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    Text(
                        stringResource(
                            when {
                                authorizing -> R.string.connector_open_again
                                status == ConnectorCardData.Status.FAILED -> R.string.connector_try_again
                                card.isMcp -> R.string.connector_sign_in
                                else -> R.string.connector_connect
                            },
                        ),
                    )
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            api.dismiss(botId, message.id, threadId)
                                .onSuccess { dismissed = true }
                                .onFailure { error = it.message }
                        }
                    },
                    modifier = Modifier.testTag(CONNECTOR_DISMISS_TAG),
                ) { Text(stringResource(R.string.connector_not_now)) }
            }
        }
    }
}
