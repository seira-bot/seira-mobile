package com.openmausbot.companion.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType

internal const val CHAT_APP_OPEN_TAG = "chat-app-open"

/** The row's own words: the server's text without its "[App] title" marker line, which the card shows as its title. */
internal fun chatAppPreviewText(text: String?): String {
    val lines = text.orEmpty().lines()
    val rest = if (lines.firstOrNull()?.startsWith("[App] ") == true) lines.drop(1) else lines
    return rest.joinToString("\n").trim()
}

@Composable
internal fun ChatAppCard(chat: Chat, message: Message, startsRun: Boolean, replyBot: Bot?) {
    val app = message.app ?: return
    val colors = Seira.colors
    var open by remember(message.id) { mutableStateOf(false) }
    val preview = remember(message.text) { chatAppPreviewText(message.text) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (message.role == Message.Role.BOT && startsRun) {
            ReplyHeader(
                bot = replyBot,
                name = message.from?.name ?: replyBot?.name ?: chat.name,
                colorName = message.from?.color ?: replyBot?.color,
                at = message.at,
            )
        }
        Surface(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.tint,
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(app.title, style = SeiraType.title3, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (preview.isNotEmpty()) {
                    Text(preview, style = SeiraType.body, color = colors.ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Row {
                    Button(
                        onClick = { open = true },
                        modifier = Modifier.testTag(CHAT_APP_OPEN_TAG),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    ) { Text(stringResource(R.string.mobile_open_cf9b7706)) }
                }
            }
        }
    }
    if (open) {
        ChatAppScreen(threadId = chat.threadId, messageId = message.id, title = app.title, onDismiss = { open = false })
    }
}
