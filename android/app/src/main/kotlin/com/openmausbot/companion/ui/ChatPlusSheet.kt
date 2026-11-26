package com.openmausbot.companion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.ui.theme.ControlCircleButton
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.seiraShadow
import com.openmausbot.companion.ui.theme.seiraTween

/** The three attachment tiles across the top of the sheet. */
private val TILE_IDS = listOf(ChatActionId.CAMERA, ChatActionId.PHOTOS, ChatActionId.FILES)

/** The first card: the things a bot does with you, live or later. */
private val LIVE_IDS = listOf(ChatActionId.LIVE_CALL, ChatActionId.SCHEDULE, ChatActionId.WATCH_COMPUTER)

/** How the sheet splits [ChatActions.sheet]: tiles, the live card, the rest. */
internal object ChatSheetLayout {
    fun tiles(actions: List<ChatAction>) = actions.filter { it.id in TILE_IDS }.sortedBy { TILE_IDS.indexOf(it.id) }
    fun live(actions: List<ChatAction>) = actions.filter { it.id in LIVE_IDS }.sortedBy { LIVE_IDS.indexOf(it.id) }
    fun rest(actions: List<ChatAction>) = actions.filter { it.id !in TILE_IDS && it.id !in LIVE_IDS }
}

private val SHEET_RADIUS = 34.dp
private val SHEET_INSET = 8.dp
private val TILE_HEIGHT = 108.dp
private val ICON_TILE = 64.dp
private val ICON_SIZE = 44.dp
private val TILE_RADIUS = 20.dp
private val ROW_HEIGHT = 56.dp
private val CARD_RADIUS = 22.dp

/** The tiles and cards sit on the sheet. In dark the tint is as dark as the sheet, so they take the control colour. */
@Composable
private fun cardColor() = Seira.colors.let { if (it.isDark) it.control else it.tint }
private const val SCRIM_MILLIS = 240
private const val SLIDE_FROM = 1.12f

/**
 * What the composer's + opens: a floating sheet over a scrim. Tapping the scrim,
 * the close button or the back gesture puts it away.
 */
@Composable
internal fun BoxScope.ChatPlusSheet(
    open: Boolean,
    chat: Chat,
    actions: List<ChatAction>,
    onDismiss: () -> Unit,
    onAction: (ChatActionId) -> Unit,
) {
    val colors = Seira.colors
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(seiraTween(SCRIM_MILLIS)),
        exit = fadeOut(seiraTween(SCRIM_MILLIS)),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                // The scrim is a dismissal, not a control: no ripple, and the
                // back gesture does the same thing for anyone not using touch.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onDismiss,
                )
                .localizedSemantics(contentDescription = { stringResource(R.string.mobile_a11y_close) }),
        )
    }

    AnimatedVisibility(
        visible = open,
        enter = slideInVertically(seiraTween(SeiraMotion.SHEET)) { (it * SLIDE_FROM).toInt() },
        exit = slideOutVertically(seiraTween(SeiraMotion.SHEET)) { (it * SLIDE_FROM).toInt() },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val shape = RoundedCornerShape(SHEET_RADIUS)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(chatBottomInsets())
                    .padding(SHEET_INSET)
                    .fillMaxWidth()
                    .seiraShadow(shape)
                    .background(colors.sheet, shape)
                    .heightIn(max = maxHeight * 0.92f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 5.dp)
                        .background(colors.grabber, CircleShape),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 0.dp)) {
                    Text(
                        text = stringResource(R.string.chat_sheet_title),
                        style = SeiraType.title3,
                        color = colors.ink,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    ControlCircleButton(
                        icon = SeiraIcons.X,
                        contentDescription = stringResource(R.string.mobile_a11y_close),
                        onClick = onDismiss,
                        size = 36.dp,
                    )
                }

                val tiles = ChatSheetLayout.tiles(actions)
                if (tiles.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.forEach { action ->
                            ActionTile(action, Modifier.weight(1f)) { onAction(action.id) }
                        }
                    }
                }
                val live = ChatSheetLayout.live(actions)
                if (live.isNotEmpty()) ActionCard(live, chat, onAction)
                val rest = ChatSheetLayout.rest(actions)
                if (rest.isNotEmpty()) ActionCard(rest, chat, onAction)
            }
        }
    }
}

@Composable
private fun ActionTile(action: ChatAction, modifier: Modifier, onClick: () -> Unit) {
    val colors = Seira.colors
    val alpha = if (action.enabled) 1f else 0.45f
    val shape = RoundedCornerShape(TILE_RADIUS)
    Column(
        modifier = modifier
            .height(TILE_HEIGHT)
            .clip(shape)
            .background(cardColor(), shape)
            .clickable(enabled = action.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        ActionGlyph(action.id, colors.ink.copy(alpha = alpha), alpha)
        Text(
            text = chatActionTitle(action.id),
            style = SeiraType.callout.copy(fontWeight = FontWeight.Medium),
            color = colors.ink.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionCard(actions: List<ChatAction>, chat: Chat, onAction: (ChatActionId) -> Unit) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(CARD_RADIUS)
    val art = actions.any { chatActionArt(it.id) != null }
    Column(modifier = Modifier.fillMaxWidth().clip(shape).background(cardColor(), shape).padding(vertical = 4.dp)) {
        actions.forEach { action ->
            val tint = if (action.destructive) colors.danger else colors.ink
            val alpha = if (action.enabled) 1f else 0.45f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_HEIGHT)
                    .clickable(enabled = action.enabled, role = Role.Button) { onAction(action.id) }
                    .padding(horizontal = if (art) 12.dp else 16.dp, vertical = if (art) 2.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(if (art) 8.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionGlyph(action.id, tint.copy(alpha = alpha), alpha)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chatActionTitle(action.id),
                        style = SeiraType.bodyMedium.copy(lineHeight = 20.sp),
                        color = tint.copy(alpha = alpha),
                        maxLines = 1,
                    )
                    Text(
                        text = chatActionSubtitle(action.id, chat),
                        style = SeiraType.caption,
                        color = colors.ink3.copy(alpha = alpha),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The six attach and live actions get a 3D icon in a 64 dp tile. The rest keep a flat glyph. */
@Composable
private fun ActionGlyph(id: ChatActionId, tint: androidx.compose.ui.graphics.Color, alpha: Float) {
    val art = chatActionArt(id)
    if (art == null) {
        Icon(chatActionIcon(id), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    } else {
        Box(modifier = Modifier.size(ICON_TILE), contentAlignment = Alignment.Center) {
            Icon3d(art.first, ICON_SIZE, art.second, Modifier.alpha(alpha))
        }
    }
}

private fun chatActionArt(id: ChatActionId): Pair<Int, String>? = when (id) {
    ChatActionId.CAMERA -> R.drawable.icon3d_camera to "camera"
    ChatActionId.PHOTOS -> R.drawable.icon3d_photos to "photos"
    ChatActionId.FILES -> R.drawable.icon3d_files to "files"
    ChatActionId.LIVE_CALL -> R.drawable.icon3d_live_call to "live_call"
    ChatActionId.SCHEDULE -> R.drawable.icon3d_schedule to "schedule"
    ChatActionId.WATCH_COMPUTER -> R.drawable.icon3d_cloud_desktop to "cloud_desktop"
    else -> null
}

@Composable
internal fun chatActionTitle(id: ChatActionId): String = when (id) {
    ChatActionId.CAMERA -> stringResource(R.string.chat_sheet_camera)
    ChatActionId.PHOTOS -> stringResource(R.string.chat_sheet_photos)
    ChatActionId.FILES -> stringResource(R.string.chat_sheet_files)
    ChatActionId.LIVE_CALL -> stringResource(R.string.chat_sheet_call)
    ChatActionId.SCHEDULE -> stringResource(R.string.chat_sheet_schedule)
    ChatActionId.WATCH_COMPUTER -> stringResource(R.string.chat_sheet_desktop)
    ChatActionId.NEW_TASK -> stringResource(R.string.mobile_new_thread_02057e28)
    ChatActionId.TASKS -> stringResource(R.string.mobile_threads_bb12e8aa)
    ChatActionId.SETTINGS -> stringResource(R.string.mobile_bot_settings_7092a294)
    ChatActionId.SHARE_MARKDOWN -> stringResource(R.string.mobile_share_transcript_004e223a)
    ChatActionId.SHARE_JSON -> stringResource(R.string.mobile_share_as_json_df80c8e6)
    ChatActionId.INTERRUPT -> stringResource(R.string.mobile_interrupt_d5db4549)
    ChatActionId.WORK_UNTIL_DONE -> stringResource(R.string.composer_plus_goal)
}

@Composable
internal fun chatActionSubtitle(id: ChatActionId, chat: Chat): String = when (id) {
    ChatActionId.CAMERA -> stringResource(R.string.chat_sheet_camera)
    ChatActionId.PHOTOS -> stringResource(R.string.mobile_action_add_photo)
    ChatActionId.FILES -> stringResource(R.string.mobile_action_add_document)
    ChatActionId.LIVE_CALL -> stringResource(R.string.chat_sheet_call_sub, chat.name)
    ChatActionId.SCHEDULE -> stringResource(R.string.chat_sheet_schedule_sub)
    ChatActionId.WATCH_COMPUTER -> stringResource(R.string.chat_sheet_desktop_sub, chat.name)
    ChatActionId.NEW_TASK -> when (chat) {
        is Chat.BotChat -> stringResource(R.string.mobile_action_new_thread_for_bot, chat.bot.name)
        is Chat.RoomChat -> stringResource(R.string.mobile_action_new_conversation_in_channel, chat.room.name)
    }
    ChatActionId.TASKS -> stringResource(R.string.mobile_action_manage_threads)
    ChatActionId.SETTINGS -> stringResource(R.string.mobile_action_bot_settings)
    ChatActionId.SHARE_MARKDOWN -> stringResource(R.string.mobile_action_markdown_transcript)
    ChatActionId.SHARE_JSON -> stringResource(R.string.mobile_action_structured_transcript)
    ChatActionId.INTERRUPT -> stringResource(R.string.mobile_action_stop_turn)
    ChatActionId.WORK_UNTIL_DONE -> stringResource(R.string.composer_plus_goal_sub)
}

private fun chatActionIcon(id: ChatActionId): ImageVector = when (id) {
    ChatActionId.CAMERA -> SeiraIcons.Camera
    ChatActionId.PHOTOS -> SeiraIcons.Image
    ChatActionId.FILES -> SeiraIcons.File
    ChatActionId.LIVE_CALL -> SeiraIcons.Phone
    ChatActionId.SCHEDULE -> SeiraIcons.CalendarClock
    ChatActionId.WATCH_COMPUTER -> SeiraIcons.Monitor
    ChatActionId.NEW_TASK -> SeiraIcons.PenSquare
    ChatActionId.TASKS -> Icons.AutoMirrored.Filled.List
    ChatActionId.SETTINGS -> Icons.Filled.Settings
    ChatActionId.SHARE_MARKDOWN, ChatActionId.SHARE_JSON -> Icons.Filled.Share
    ChatActionId.INTERRUPT -> SeiraIcons.X
    ChatActionId.WORK_UNTIL_DONE -> SeiraIcons.Clock
}
