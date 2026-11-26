package com.openmausbot.companion.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType

internal enum class ChatMenuItem {
    FIND, NEW_THREAD, ALL_THREADS, MODEL, MEMBERS, BOT_SCREEN, SHARE_MARKDOWN, SHARE_JSON,
}

internal object ChatMenuRules {
    fun items(chat: Chat): List<ChatMenuItem> = buildList {
        add(ChatMenuItem.FIND)
        if (chat.supportsTasks) {
            add(ChatMenuItem.NEW_THREAD)
            add(ChatMenuItem.ALL_THREADS)
        }
        when (chat) {
            is Chat.BotChat -> {
                add(ChatMenuItem.MODEL)
                add(ChatMenuItem.BOT_SCREEN)
            }
            is Chat.RoomChat -> add(ChatMenuItem.MEMBERS)
        }
        add(ChatMenuItem.SHARE_MARKDOWN)
        add(ChatMenuItem.SHARE_JSON)
    }

    /** The Monitor button in the header: a bot's own screen, never a group's. */
    fun showsScreenButton(chat: Chat): Boolean = chat is Chat.BotChat
}

internal fun chatMenuTag(item: ChatMenuItem): String = "chat-menu.${item.name.lowercase()}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatMenuSheet(
    chat: Chat,
    newThreadEnabled: Boolean,
    onPick: (ChatMenuItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("chat-menu-sheet"),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            for (item in ChatMenuRules.items(chat)) {
                val (icon, title, subtitle) = copyFor(item)
                ChatSheetRow(
                    icon = icon,
                    title = title,
                    subtitle = subtitle,
                    enabled = item != ChatMenuItem.NEW_THREAD || newThreadEnabled,
                    tag = chatMenuTag(item),
                    onClick = { onPick(item) },
                )
            }
        }
    }
}

private data class MenuCopy(val icon: ImageVector, val title: String, val subtitle: String?)

@Composable
private fun copyFor(item: ChatMenuItem): MenuCopy = when (item) {
    ChatMenuItem.FIND -> MenuCopy(SeiraIcons.Search, stringResource(R.string.chat2_menu_find), null)
    ChatMenuItem.NEW_THREAD -> MenuCopy(SeiraIcons.PenSquare, stringResource(R.string.chat2_menu_new_thread), null)
    ChatMenuItem.ALL_THREADS -> MenuCopy(ChatIcons.Layers, stringResource(R.string.chat2_menu_all_threads), null)
    ChatMenuItem.MODEL -> MenuCopy(SeiraIcons.Sliders, stringResource(R.string.chat2_menu_model), null)
    ChatMenuItem.MEMBERS -> MenuCopy(SeiraIcons.Users, stringResource(R.string.chat2_menu_members), null)
    ChatMenuItem.BOT_SCREEN -> MenuCopy(SeiraIcons.Monitor, stringResource(R.string.chat2_menu_bot_screen), null)
    ChatMenuItem.SHARE_MARKDOWN -> MenuCopy(
        ChatIcons.Share,
        stringResource(R.string.chat2_menu_share),
        stringResource(R.string.chat2_menu_share_markdown),
    )
    ChatMenuItem.SHARE_JSON -> MenuCopy(ChatIcons.Code, stringResource(R.string.chat2_menu_share_json), null)
}

@Composable
internal fun ChatSheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Seira.colors
    val ink = if (enabled) colors.ink else colors.ink3
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { if (!enabled) disabled() }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(colors.control, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SeiraType.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = SeiraType.caption, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
