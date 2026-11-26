package com.openmausbot.companion.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.ui.CHAT_HEADER_FADE
import com.openmausbot.companion.ui.CHAT_HEADER_SIDE
import com.openmausbot.companion.ui.CHAT_HEADER_TOP_GAP
import com.openmausbot.companion.ui.MessageActions
import com.openmausbot.companion.ui.TouchTarget
import com.openmausbot.companion.ui.chatTopInsets
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.TopFade

internal object ChatFindRules {
    /** Ids of the messages whose text holds [query], oldest first. */
    fun hits(messages: List<Message>, query: String): List<String> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return messages.filter { message ->
            MessageActions.copyableText(message)?.contains(needle, ignoreCase = true) == true
        }.map { it.id }
    }

    /** The next position, wrapping past either end. */
    fun step(current: Int, count: Int, forward: Boolean): Int {
        if (count <= 0) return -1
        if (current !in 0 until count) return if (forward) 0 else count - 1
        return if (forward) (current + 1) % count else (current - 1 + count) % count
    }
}

internal enum class FindHighlight { MATCH, ACTIVE }

internal val LocalFindHighlights = compositionLocalOf<Map<String, FindHighlight>> { emptyMap() }

@Composable
internal fun Modifier.findHighlight(messageId: String): Modifier {
    val highlight = LocalFindHighlights.current[messageId] ?: return this
    val colors = Seira.colors
    val shape = RoundedCornerShape(16.dp)
    return when (highlight) {
        FindHighlight.ACTIVE -> this
            .background(colors.warningSoft, shape)
            .border(1.5.dp, colors.warning, shape)
        FindHighlight.MATCH -> this.background(colors.warningSoft, shape)
    }
}

internal const val CHAT_FIND_FIELD_TAG = "chat-find-field"
internal const val CHAT_FIND_COUNT_TAG = "chat-find-count"

/** The bar that takes the header's place while a person searches this thread. */
@Composable
internal fun ChatFindBar(
    query: String,
    count: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(modifier = modifier.fillMaxWidth()) {
        TopFade(CHAT_HEADER_FADE, modifier = Modifier.align(Alignment.TopCenter))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onHeight(it.height) }
                .windowInsetsPadding(chatTopInsets())
                .padding(start = CHAT_HEADER_SIDE, end = CHAT_HEADER_SIDE, top = CHAT_HEADER_TOP_GAP)
                .height(46.dp)
                .background(colors.s1, CircleShape)
                .border(1.dp, colors.hairline, CircleShape)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SeiraIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
            Box(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                val hint = stringResource(R.string.chat2_find_hint)
                if (query.isEmpty()) {
                    Text(hint, fontSize = 15.sp, color = colors.ink3, maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .testTag(CHAT_FIND_FIELD_TAG),
                )
            }
            if (query.isNotBlank()) {
                Text(
                    text = if (count == 0) {
                        stringResource(R.string.chat2_find_none)
                    } else {
                        pluralStringResource(R.plurals.chat2_find_count, count, count)
                    },
                    fontSize = 12.sp,
                    color = colors.ink3,
                    maxLines = 1,
                    modifier = Modifier.testTag(CHAT_FIND_COUNT_TAG),
                )
            }
            FindButton(ChatIcons.ChevronUp, stringResource(R.string.chat2_find_previous), count > 0, onPrevious)
            FindButton(SeiraIcons.ChevronDown, stringResource(R.string.chat2_find_next), count > 0, onNext)
            FindButton(SeiraIcons.X, stringResource(R.string.chat2_find_close), true, onClose)
        }
    }
}

@Composable
private fun FindButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Seira.colors
    TouchTarget(onClick = onClick, enabled = enabled, contentDescription = description) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) colors.ink else colors.ink3.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp).clip(CircleShape),
        )
    }
}
