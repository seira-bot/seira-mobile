package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType

/**
 * What the "New chat" button opens: a new group, a new bot, organizing bots
 * into a section, and a new chat with any bot on the list below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewChatSheet(
    bots: List<Bot>,
    canCreateBot: Boolean,
    canOrganize: Boolean,
    onBot: (Bot) -> Unit,
    onNewGroup: () -> Unit,
    onNewBot: () -> Unit,
    onOrganize: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("new-chat-sheet"),
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.home_new_chat),
                    style = SeiraType.title2,
                    color = colors.ink,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            item(key = "new-group") {
                ActionRow(SeiraIcons.Plus, stringResource(R.string.mobile_new_group_f9850c0b), "new-chat-group", enabled = true, onClick = onNewGroup)
            }
            item(key = "new-bot") {
                ActionRow(SeiraIcons.PenSquare, stringResource(R.string.mobile_new_bot_66d3c052), "new-chat-bot", enabled = canCreateBot, onClick = onNewBot, subtitle = stringResource(R.string.newbot_row_subtitle))
            }
            if (canOrganize) {
                item(key = "organize") {
                    ActionRow(
                        SeiraIcons.File,
                        stringResource(R.string.mobile_organize_bots_into_a_section_9293c5af),
                        "new-chat-section",
                        enabled = true,
                        onClick = onOrganize,
                    )
                }
            }
            if (bots.isNotEmpty()) {
                item(key = "bots-title") {
                    Text(
                        text = stringResource(R.string.mobile_bots_4ca88ea4),
                        style = SeiraType.headline,
                        color = colors.ink,
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                            .semantics { heading() },
                    )
                }
                items(bots, key = { "bot-${it.id}" }) { bot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new-chat-with.${bot.id}")
                            .clickable(role = Role.Button) { onBot(bot) }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IdentityAvatar(bot = bot, size = 40.dp, animated = false)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bot.name, style = SeiraType.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (bot.title.isNotEmpty()) {
                                Text(bot.title, style = SeiraType.caption, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tag: String, enabled: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(colors.control, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) colors.ink else colors.ink3,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = SeiraType.bodyMedium, color = if (enabled) colors.ink else colors.ink3)
            if (subtitle != null) {
                Text(subtitle, style = SeiraType.caption, color = colors.ink3)
            }
        }
    }
}
