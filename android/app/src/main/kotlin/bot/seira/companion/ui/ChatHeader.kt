package bot.seira.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.BuildConfig
import bot.seira.companion.R
import bot.seira.companion.audio.LiveCallSnapshot
import bot.seira.companion.core.Chat
import bot.seira.companion.core.LiveCallState
import bot.seira.companion.ui.theme.AvatarRing
import bot.seira.companion.ui.theme.IdentityAvatar
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.TopFade

/**
 * The window insets the floating chat chrome keeps clear of. Null in the app,
 * where the root already consumed the system bars and the keyboard; a test that
 * mounts the chat without a root supplies fixed ones.
 */
internal class ChatInsets(val top: WindowInsets, val bottom: WindowInsets)

internal val LocalChatInsets = compositionLocalOf<ChatInsets?> { null }

@Composable
internal fun chatTopInsets(): WindowInsets = LocalChatInsets.current?.top ?: WindowInsets.statusBars

@Composable
internal fun chatBottomInsets(): WindowInsets =
    LocalChatInsets.current?.bottom ?: WindowInsets.ime.union(WindowInsets.navigationBars)

/** What the status line under the name says. */
internal enum class ChatStatus { WORKING, WAITING, IDLE }

internal object ChatStatusRules {
    /** A waiting question outranks a running turn: it is the one thing only a person can move. */
    fun of(busy: Boolean, pendingApproval: Boolean): ChatStatus = when {
        pendingApproval -> ChatStatus.WAITING
        busy -> ChatStatus.WORKING
        else -> ChatStatus.IDLE
    }
}

/** What the composer's talk button does. */
internal enum class ChatCallAction { START, OPEN, NONE }

internal object ChatCallRules {
    /**
     * A call running on this phone opens its screen. A call another device holds
     * has to hang up there first, so the button steps aside.
     */
    fun of(
        local: LiveCallSnapshot,
        server: LiveCallState?,
        isBotChat: Boolean,
        callsEnabled: Boolean = BuildConfig.LIVE_CALLS,
    ): ChatCallAction = when {
        !callsEnabled || !isBotChat -> ChatCallAction.NONE
        local.active -> ChatCallAction.OPEN
        LiveCallRules.offersCall(local, server, callsEnabled) -> ChatCallAction.START
        else -> ChatCallAction.NONE
    }
}

internal val CHAT_HEADER_FADE = 120.dp
internal val CHAT_HEADER_SIDE = 12.dp
internal val CHAT_HEADER_TOP_GAP = 8.dp
private val HEADER_BUTTON = 46.dp

internal const val CHAT_HEADER_SCREEN_TAG = "chat-header-screen"
internal const val CHAT_HEADER_MENU_TAG = "chat-header-menu"

/** The face for a chat: a bot's own, or its member bots stacked for a room. */
@Composable
internal fun ChatFace(
    chat: Chat,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: AvatarRing = AvatarRing.None,
    waitingDot: Boolean = false,
    state: CharacterMood = CharacterMood.IDLE,
    animated: Boolean = true,
) {
    when (chat) {
        is Chat.BotChat -> IdentityAvatar(
            bot = chat.bot, size = size, modifier = modifier, ring = ring,
            waitingDot = waitingDot, state = state, animated = animated,
        )
        is Chat.RoomChat -> RoomAvatar(room = chat.room, size = size, modifier = modifier)
    }
}

/**
 * Floating header: back, the name pill, the bot's screen and the chat menu. The
 * log scrolls under it and fades out beneath the controls. [onHeight] reports
 * the header's own height, status bar included, so the log can start below it.
 */
@Composable
internal fun ChatHeader(
    chat: Chat,
    face: CharacterMood,
    status: ChatStatus,
    unreadElsewhere: Int,
    showScreen: Boolean,
    onBack: () -> Unit,
    onOpenScreen: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenPill: (() -> Unit)?,
    onHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    Box(modifier = modifier.fillMaxWidth()) {
        TopFade(CHAT_HEADER_FADE, modifier = Modifier.align(Alignment.TopCenter))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onHeight(it.height) }
                .windowInsetsPadding(chatTopInsets())
                .padding(start = CHAT_HEADER_SIDE, end = CHAT_HEADER_SIDE, top = CHAT_HEADER_TOP_GAP),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                HeaderRoundButton(
                    icon = SeiraIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.mobile_back_b52b36b7),
                    onClick = onBack,
                )
                if (unreadElsewhere > 0) {
                    Text(
                        text = "$unreadElsewhere",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .background(colors.primary, CircleShape)
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                            .clearAndSetSemantics { },
                    )
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ChatNamePill(chat = chat, face = face, status = status, onOpen = onOpenPill)
            }
            if (showScreen) {
                HeaderRoundButton(
                    icon = SeiraIcons.Monitor,
                    contentDescription = stringResource(R.string.chat2_header_screen),
                    onClick = onOpenScreen,
                    modifier = Modifier.testTag(CHAT_HEADER_SCREEN_TAG),
                )
            }
            HeaderRoundButton(
                icon = SeiraIcons.MoreHorizontal,
                contentDescription = stringResource(R.string.chat2_header_menu),
                onClick = onOpenMenu,
                modifier = Modifier.testTag(CHAT_HEADER_MENU_TAG),
            )
        }
    }
}

@Composable
private fun HeaderRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    TouchTarget(onClick = onClick, contentDescription = contentDescription, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(HEADER_BUTTON)
                .background(colors.s1, CircleShape)
                .border(1.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ChatNamePill(
    chat: Chat,
    face: CharacterMood,
    status: ChatStatus,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val description = when (chat) {
        is Chat.BotChat -> stringResource(R.string.chat2_open_bot, chat.name)
        is Chat.RoomChat -> pluralStringResource(R.plurals.mobile_a11y_room_header, chat.room.memberIds.size, chat.name, chat.room.memberIds.size)
    }
    val statusColor = when (status) {
        ChatStatus.WORKING -> colors.accent
        ChatStatus.WAITING -> colors.warning
        ChatStatus.IDLE -> colors.ink3
    }
    Box(
        modifier = modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(CircleShape)
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (onOpen != null) role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(HEADER_BUTTON)
                .background(colors.s1, CircleShape)
                .border(1.dp, colors.hairline, CircleShape)
                .padding(start = 6.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatFace(chat = chat, size = 34.dp, state = face)
            Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.Center) {
                Text(
                    text = chat.name,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        when (status) {
                            ChatStatus.WORKING -> R.string.chat_status_working
                            ChatStatus.WAITING -> R.string.chat_status_waiting
                            ChatStatus.IDLE -> R.string.chat_status_idle
                        },
                    ),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
