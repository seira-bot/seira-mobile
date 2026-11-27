package bot.seira.companion.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.R
import bot.seira.companion.core.Message
import bot.seira.companion.ui.TouchTarget
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons

/** The message a person is answering, held until the answer is sent. */
internal data class ReplyTarget(val messageId: String, val author: String, val text: String) {
    val firstLine: String get() = text.trim().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
}

/**
 * A reply travels as ordinary text: a quoted block, a blank line, then the
 * person's own words. The computer needs no extra field for it.
 */
internal object ReplyQuote {
    const val LIMIT = 280

    fun quote(replied: String): String {
        val trimmed = replied.replace("\r\n", "\n").replace('\r', '\n').trim()
        val head = if (trimmed.length > LIMIT) {
            val cut = trimmed.take(LIMIT)
            if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
        } else {
            trimmed
        }
        return head.split('\n').joinToString("\n") { "> $it" }
    }

    fun wrap(replied: String?, text: String): String {
        if (replied.isNullOrBlank()) return text
        return quote(replied) + "\n\n" + text
    }

    /**
     * What goes out for a typed line: a command stays a bare command, and a
     * reply with no words of its own (attachments only) is the quote alone.
     */
    fun outgoing(replied: String?, text: String): String = when {
        replied.isNullOrBlank() -> text
        text.startsWith("/") -> text
        text.isBlank() -> quote(replied)
        else -> wrap(replied, text)
    }
}

/** Set by the chat screen; a row starts a reply through it. Null where replies are off. */
internal val LocalReplyHandler = compositionLocalOf<((Message) -> Unit)?> { null }

internal const val REPLY_STRIP_TAG = "reply-strip"
internal const val REPLY_STRIP_CLOSE_TAG = "reply-strip-close"

@Composable
internal fun ReplyStrip(target: ReplyTarget, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.s1, shape)
            .border(1.dp, colors.hairline, shape)
            .padding(start = 12.dp)
            .testTag(REPLY_STRIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.chat2_reply_to, target.author),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accentText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = target.firstLine,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TouchTarget(
            onClick = onClose,
            contentDescription = stringResource(R.string.chat2_reply_cancel),
            modifier = Modifier.testTag(REPLY_STRIP_CLOSE_TAG),
        ) {
            Icon(SeiraIcons.X, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
        }
    }
}
