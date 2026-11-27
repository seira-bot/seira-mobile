package com.openmausbot.companion.ui.home.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.RelativeStamp
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.SeiraRoomAvatar
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween
import java.util.Locale

private val CHIEF_BADGE = Color(0xFFF0952B)

@Composable
private fun dotColor(dot: HomeDot): Color {
    val colors = Seira.colors
    return when (dot) {
        HomeDot.WAITING -> colors.warning
        HomeDot.PROBLEM -> colors.danger
        HomeDot.WORKING -> colors.accent
        HomeDot.NONE -> Color.Transparent
    }
}

@Composable
private fun dotLabel(dot: HomeDot): String = when (dot) {
    HomeDot.WAITING -> stringResource(R.string.home2_waiting)
    HomeDot.PROBLEM -> stringResource(R.string.home2_problem)
    HomeDot.WORKING -> stringResource(R.string.home2_working)
    HomeDot.NONE -> ""
}

/** One row of up to four bot tiles; a short row keeps the tile width of a full one. */
@Composable
internal fun TileRow(bots: List<Bot>, modifier: Modifier = Modifier, tile: @Composable (Bot, Modifier) -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bots.forEach { bot -> tile(bot, Modifier.weight(1f)) }
        repeat(HomeLayoutRules.TILE_COLUMNS - bots.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A bot as a tile: face, name, and a crown or a dot only when it applies. */
@Composable
internal fun BotTile(
    bot: Bot,
    chief: Boolean,
    dot: HomeDot,
    face: CharacterMood,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(16.dp)
    val chiefLabel = stringResource(R.string.home2_chief)
    val showDot = HomeDotRules.tileShows(dot)
    val state = dotLabel(dot)
    Column(
        modifier = modifier
            .testTag("chat-row.${bot.id}")
            .clip(shape)
            .background(if (chief) colors.s2 else colors.s1, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {
                if (showDot) stateDescription = state
                if (chief) contentDescription = "${bot.name}, $chiefLabel"
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            IdentityAvatar(bot = bot, size = 56.dp, state = face, animated = false)
            if (chief) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp)
                        .testTag("chief-badge")
                        .size(22.dp)
                        .background(CHIEF_BADGE, CircleShape)
                        .padding(5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_crown),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
            if (showDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .background(dotColor(dot), CircleShape),
                )
            }
        }
        Text(
            text = bot.name,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A bot in the Advanced list: the threads chevron, face with its dot, name, time and one line of what was said. */
@Composable
internal fun AdvancedBotRow(
    bot: Bot,
    chief: Boolean,
    dot: HomeDot,
    face: CharacterMood,
    preview: String,
    lastActivity: Double,
    threadCount: Int,
    expanded: Boolean,
    onOpen: () -> Unit,
    onToggleThreads: () -> Unit,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val shape = RoundedCornerShape(12.dp)
    val waiting = dot == HomeDot.WAITING
    val now = remember(lastActivity) { System.currentTimeMillis() }
    val stamp = RelativeStamp.list(lastActivity, now, locale = Locale.getDefault())
    val chiefLabel = stringResource(R.string.home2_chief)
    val state = dotLabel(dot)
    val angle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = seiraTween(SeiraMotion.BASE, reduced),
        label = "advanced-threads-chevron",
    )
    val threadsLabel = stringResource(R.string.mobile_a11y_bot_threads, bot.name)
    val threadsState = stringResource(
        if (expanded) R.string.mobile_a11y_thread_state_expanded else R.string.mobile_a11y_thread_state_collapsed,
        threadCount,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(shape)
            .background(if (chief) colors.s2 else Color.Transparent, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .testTag("threads-toggle.${bot.id}")
                .size(width = 48.dp, height = 60.dp)
                .clickable(role = Role.Button, onClick = onToggleThreads)
                .semantics {
                    contentDescription = threadsLabel
                    stateDescription = threadsState
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SeiraIcons.ChevronRight,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer { rotationZ = angle },
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .testTag("chat-row.${bot.id}")
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    if (dot != HomeDot.NONE) stateDescription = state
                    if (chief) contentDescription = "${bot.name}, $chiefLabel"
                }
                .padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(40.dp)) {
                IdentityAvatar(bot = bot, size = 40.dp, state = face, animated = false)
                if (dot != HomeDot.NONE) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 1.dp, y = 1.dp)
                            .size(12.dp)
                            .background(colors.app, CircleShape)
                            .padding(2.dp)
                            .background(dotColor(dot), CircleShape),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = bot.name,
                        fontSize = 15.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chief) {
                        Icon(
                            painter = painterResource(R.drawable.ic_crown),
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier
                                .size(14.dp)
                                .testTag("chief-badge"),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (stamp.isNotEmpty()) {
                        Text(
                            text = stamp,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = colors.ink3,
                            maxLines = 1,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                        )
                    }
                }
                Text(
                    text = preview.ifEmpty { " " },
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    color = if (waiting) colors.warningText else colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One group chat: one or two faces, the name with its time, and one line of what was said. */
@Composable
internal fun GroupChatRow(
    room: Room,
    members: List<Bot>,
    preview: String,
    lastActivity: Double,
    waiting: Boolean,
    advanced: Boolean,
    onOpen: () -> Unit,
) {
    val colors = Seira.colors
    val now = remember(lastActivity) { System.currentTimeMillis() }
    val stamp = RelativeStamp.list(lastActivity, now, locale = Locale.getDefault())
    val waitingLabel = stringResource(R.string.home2_waiting)
    val unread = room.unread
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat-row.${room.id}")
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { if (waiting) stateDescription = waitingLabel }
            .heightIn(min = if (advanced) 56.dp else 66.dp)
            .padding(start = if (advanced) 18.dp else 16.dp, end = if (advanced) 22.dp else 16.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (advanced) 14.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupFaces(members = members, advanced = advanced)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = room.name,
                    fontSize = if (advanced) 15.sp else 15.5.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (unread) {
                    Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                if (stamp.isNotEmpty()) {
                    Text(
                        text = stamp,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp,
                        color = colors.ink3,
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    )
                }
            }
            Text(
                text = preview.ifEmpty { " " },
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                color = if (waiting) colors.warningText else colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Two faces side by side, or one when the group has one member. */
@Composable
private fun GroupFaces(members: List<Bot>, advanced: Boolean) {
    val colors = Seira.colors
    val box = if (advanced) 40.dp else 52.dp
    val boxHeight = 40.dp
    Box(modifier = Modifier.size(width = box, height = boxHeight)) {
        val first = members.getOrNull(0)
        val second = members.getOrNull(1)
        when {
            first == null -> SeiraRoomAvatar(size = 36.dp, modifier = Modifier.align(Alignment.Center))
            second == null -> IdentityAvatar(
                bot = first,
                size = if (advanced) 30.dp else 36.dp,
                state = CharacterMood.IDLE,
                animated = false,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> {
                val face = if (advanced) 27.dp else 34.dp
                IdentityAvatar(
                    bot = first,
                    size = face,
                    state = CharacterMood.IDLE,
                    animated = false,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = if (advanced) 0.dp else 3.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (advanced) 0.dp else 3.dp)
                        .background(colors.app, CircleShape)
                        .padding(2.dp),
                ) {
                    IdentityAvatar(bot = second, size = face - 4.dp, state = CharacterMood.IDLE, animated = false)
                }
            }
        }
    }
}
