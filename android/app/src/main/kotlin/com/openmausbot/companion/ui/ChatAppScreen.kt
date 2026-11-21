package com.openmausbot.companion.ui

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openmausbot.companion.BuildConfig
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ChatAppDocument
import kotlinx.coroutines.CancellationException

internal const val CHAT_APP_ERROR_TAG = "chat-app-error"
internal const val CHAT_APP_WEB_TAG = "chat-app-web"

internal sealed interface ChatAppLoad {
    data object Loading : ChatAppLoad
    data class Ready(val document: ChatAppDocument) : ChatAppLoad
    data object Unavailable : ChatAppLoad
    data object Failed : ChatAppLoad
}

/** The document comes from the paired computer; whatever it holds, the page may only open links. */
internal fun chatAppLoadOf(document: ChatAppDocument): ChatAppLoad =
    if (document.canOpenOnPhone) ChatAppLoad.Ready(document.viewOnly()) else ChatAppLoad.Unavailable

/** Full window, view only: a bot's page, shown the way it was sent. */
@Composable
fun ChatAppScreen(threadId: String, messageId: String, title: String, onDismiss: () -> Unit) {
    val session = LocalCompanion.current.session
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf<ChatAppLoad>(ChatAppLoad.Loading) }
    var webViewTooOld by remember { mutableStateOf(false) }
    var pageStopped by remember { mutableStateOf(false) }

    LaunchedEffect(threadId, messageId, attempt) {
        load = ChatAppLoad.Loading
        load = try {
            val connectionId = session.connection.value?.id ?: throw IllegalStateException("not connected")
            chatAppLoadOf(session.withShareClient(connectionId) { it.appDocument(threadId, messageId) })
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            ChatAppLoad.Failed
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.mobile_a11y_close))
                    }
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                }
                HorizontalDivider()
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    when (val current = load) {
                        ChatAppLoad.Loading -> ChatAppStatus(spinner = true, text = stringResource(R.string.chat_app_loading))
                        ChatAppLoad.Unavailable -> ChatAppStatus(text = stringResource(R.string.chat_app_unavailable))
                        ChatAppLoad.Failed -> ChatAppStatus(text = stringResource(R.string.chat_app_failed), retry = { attempt += 1 })
                        is ChatAppLoad.Ready -> if (webViewTooOld) {
                            ChatAppStatus(text = stringResource(R.string.chat_app_update_webview))
                        } else if (pageStopped) {
                            ChatAppStatus(text = stringResource(R.string.chat_app_failed), retry = {
                                pageStopped = false
                                attempt += 1
                            })
                        } else {
                            ChatAppWebView(
                                document = current.document,
                                heightDp = maxHeight.value.toInt().coerceAtLeast(1),
                                dark = dark,
                                hostVersion = "android-${BuildConfig.VERSION_NAME}",
                                onOpenLink = { url -> openInCustomTab(context, url) },
                                onUnsupported = { webViewTooOld = true },
                                onGone = { pageStopped = true },
                                modifier = Modifier.fillMaxSize().testTag(CHAT_APP_WEB_TAG),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatAppStatus(text: String, spinner: Boolean = false, retry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (spinner) CircularProgressIndicator()
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.testTag(CHAT_APP_ERROR_TAG))
        if (retry != null) TextButton(onClick = retry) { Text(stringResource(R.string.mobile_try_again_042c862e)) }
    }
}

/** The web address was already checked to be http or https. */
internal fun openInCustomTab(context: Context, url: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        // A phone with no browser has nothing to open the link in.
    }
}
