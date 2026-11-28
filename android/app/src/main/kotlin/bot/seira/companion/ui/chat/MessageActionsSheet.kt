package bot.seira.companion.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.R
import bot.seira.companion.core.Message
import bot.seira.companion.ui.MessageActions
import bot.seira.companion.ui.Reactions
import bot.seira.companion.ui.TouchTarget
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons

internal object MessageActionRules {
    /**
     * The Copy, Reply and more row sits under a bot's finished text. Narration
     * inside a running turn has no row until the turn ends.
     */
    fun showsRow(message: Message): Boolean {
        if (message.role == Message.Role.USER || message.kind != Message.Kind.TEXT) return false
        if (MessageActions.copyableText(message) == null) return false
        return message.turnId.isNullOrEmpty() || message.turnTerminal == true
    }
}

internal const val MESSAGE_ACTIONS_SHEET_TAG = "message-actions-sheet"
internal const val MESSAGE_ACTION_ROW_TAG = "message-action-row"
internal const val MESSAGE_ACTION_COPY_TAG = "message-action-copy"
internal const val MESSAGE_ACTION_REPLY_TAG = "message-action-reply"
internal const val MESSAGE_ACTION_MORE_TAG = "message-action-more"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionsSheet(
    onReact: ((String) -> Unit)?,
    onReply: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onSelectText: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    editEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag(MESSAGE_ACTIONS_SHEET_TAG),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            if (onReact != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .background(colors.s1, RoundedCornerShape(26.dp))
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (emoji in Reactions.CHOICES) {
                        TouchTarget(
                            onClick = { onReact(emoji) },
                            contentDescription = stringResource(R.string.chat2_msg_react, emoji),
                            modifier = Modifier.testTag("message-react.$emoji"),
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
            if (onReply != null) {
                ChatSheetRow(ChatIcons.Reply, stringResource(R.string.chat2_msg_reply), null, "message-sheet.reply", onReply)
            }
            if (onCopy != null) {
                ChatSheetRow(ChatIcons.Copy, stringResource(R.string.mobile_copy_af74f7c5), null, "message-sheet.copy", onCopy)
            }
            if (onSelectText != null) {
                ChatSheetRow(
                    ChatIcons.TextCursor, stringResource(R.string.mobile_select_text_9d49219e), null,
                    "message-sheet.select", onSelectText,
                )
            }
            if (onEdit != null) {
                ChatSheetRow(
                    SeiraIcons.PenSquare, stringResource(R.string.chat2_msg_edit), null,
                    "message-sheet.edit", onEdit, enabled = editEnabled,
                )
            }
        }
    }
}

/** Copy, Reply and the full menu under a finished bot reply. */
@Composable
internal fun MessageActionRow(
    onCopy: () -> Unit,
    onReply: (() -> Unit)?,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag(MESSAGE_ACTION_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionGlyph(ChatIcons.Copy, stringResource(R.string.mobile_copy_af74f7c5), MESSAGE_ACTION_COPY_TAG, onCopy)
        if (onReply != null) {
            ActionGlyph(ChatIcons.Reply, stringResource(R.string.chat2_msg_reply), MESSAGE_ACTION_REPLY_TAG, onReply)
        }
        ActionGlyph(SeiraIcons.MoreHorizontal, stringResource(R.string.chat2_msg_more), MESSAGE_ACTION_MORE_TAG, onMore)
    }
}

@Composable
private fun ActionGlyph(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) {
    val colors = Seira.colors
    TouchTarget(onClick = onClick, contentDescription = description, modifier = Modifier.testTag(tag)) {
        Box(
            modifier = Modifier.size(36.dp).background(colors.s1, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(18.dp))
        }
    }
}
