package com.openmausbot.companion.ui.home.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Room
import com.openmausbot.companion.core.RosterBotRow
import com.openmausbot.companion.core.RosterRowStatus
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.RelativeStamp
import com.openmausbot.companion.ui.RoomFaces
import com.openmausbot.companion.ui.localizedMobileCopy
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween
import java.util.Locale

private val COMFORTABLE_HEIGHT = 66.dp
private val COMPACT_HEIGHT = 48.dp
private val COMFORTABLE_FACE = 46.dp
private val COMPACT_FACE = 32.dp

/** One bot: face, name with its role, one line of what was said, the time, and the thread control. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatsBotRow(
    bot: Bot,
    row: RosterBotRow,
    preview: String,
    lastActivity: Double,
    face: CharacterMood,
    compact: Boolean,
    expanded: Boolean,
    creating: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNewThread: () -> Unit,
    onManage: () -> Unit,
) {
    val colors = Seira.colors
    val faceSize = if (compact) COMPACT_FACE else COMFORTABLE_FACE
    val waiting = row.status == RosterRowStatus.WAITING_ON_YOU
    val working = row.status == RosterRowStatus.WORKING
    val now = remember(lastActivity) { System.currentTimeMillis() }
    val stamp = RelativeStamp.list(lastActivity, now, locale = Locale.getDefault())
    val waitingLabel = stringResource(R.string.mobile_waiting_on_you_edab5b72)
    val newThread = stringResource(R.string.mobile_new_thread_02057e28)
    val manageThreads = stringResource(R.string.home_manage_threads)
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat-row.${bot.id}")
                    .combinedClickable(
                        role = Role.Button,
                        onLongClickLabel = manageThreads,
                        onLongClick = { menuOpen = true },
                        onClick = onOpen,
                    )
                    .semantics(mergeDescendants = true) {
                        if (waiting) stateDescription = waitingLabel
                        customActions = listOf(
                            CustomAccessibilityAction(newThread) {
                                if (!creating) onNewThread()
                                !creating
                            },
                            CustomAccessibilityAction(manageThreads) {
                                onManage()
                                true
                            },
                        )
                    }
                    .heightIn(min = if (compact) COMPACT_HEIGHT else COMFORTABLE_HEIGHT)
                    .padding(start = 20.dp, end = if (row.showsThreadControl) 4.dp else 20.dp)
                    .padding(vertical = if (compact) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IdentityAvatar(bot = bot, size = faceSize, state = face, animated = false)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    NameLine(
                        name = bot.name,
                        role = bot.title,
                        bold = row.unread && !row.busy,
                        chief = row.showsChiefBadge,
                    )
                    if (!compact) {
                        Text(
                            text = preview.ifEmpty { " " },
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = if (waiting) colors.warningText else colors.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                StatusAndTime(
                    working = working || creating,
                    unread = row.showsUnreadDot,
                    waitingDot = compact && waiting,
                    stamp = stamp,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(newThread) },
                    leadingIcon = { Icon(SeiraIcons.Plus, contentDescription = null) },
                    enabled = !creating,
                    onClick = {
                        menuOpen = false
                        onNewThread()
                    },
                )
                DropdownMenuItem(
                    text = { Text(manageThreads) },
                    onClick = {
                        menuOpen = false
                        onManage()
                    },
                )
            }
        }
        if (row.showsThreadControl) {
            ThreadsButton(bot = bot, count = row.threadCount, expanded = expanded, onToggle = onToggle)
        }
    }
}

/** One group: its stacked faces, its name, and one line of what was said. */
@Composable
internal fun ChatsRoomRow(
    room: Room,
    members: List<Bot>,
    preview: String,
    lastActivity: Double,
    waiting: Boolean,
    compact: Boolean,
    onOpen: () -> Unit,
) {
    val colors = Seira.colors
    val faceSize = if (compact) COMPACT_FACE else COMFORTABLE_FACE
    val busy = room.busyBotId != null
    val now = remember(lastActivity) { System.currentTimeMillis() }
    val stamp = RelativeStamp.list(lastActivity, now, locale = Locale.getDefault())
    val waitingLabel = stringResource(R.string.mobile_waiting_on_you_edab5b72)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat-row.${room.id}")
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { if (waiting) stateDescription = waitingLabel }
            .heightIn(min = if (compact) COMPACT_HEIGHT else COMFORTABLE_HEIGHT)
            .padding(horizontal = 20.dp)
            .padding(vertical = if (compact) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoomFaces(members = members, size = faceSize)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            NameLine(name = room.name, role = "", bold = room.unread && !busy, chief = false)
            if (!compact) {
                Text(
                    text = preview.ifEmpty { " " },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = if (waiting) colors.warningText else colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StatusAndTime(working = busy, unread = room.unread && !busy, waitingDot = compact && waiting, stamp = stamp)
    }
}

@Composable
private fun NameLine(name: String, role: String, bold: Boolean, chief: Boolean) {
    val colors = Seira.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            fontSize = 15.5.sp,
            lineHeight = 20.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (chief) {
            Icon(
                painter = painterResource(R.drawable.ic_crown),
                contentDescription = localizedMobileCopy("Chief of Staff"),
                tint = colors.accent,
                modifier = Modifier
                    .size(14.dp)
                    .testTag("chief-badge"),
            )
        }
        if (role.isNotEmpty()) {
            Text(
                text = role,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun StatusAndTime(working: Boolean, unread: Boolean, waitingDot: Boolean, stamp: String) {
    val colors = Seira.colors
    val workingLabel = stringResource(R.string.mobile_thread_working)
    val unreadLabel = stringResource(R.string.mobile_unread_07b032b5)
    val waitingLabel = stringResource(R.string.mobile_waiting_on_you_edab5b72)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            working -> CircularProgressIndicator(
                modifier = Modifier
                    .size(12.dp)
                    .semantics { contentDescription = workingLabel },
                strokeWidth = 2.dp,
                color = colors.success,
            )
            waitingDot -> StatusDot(colors.warning, waitingLabel)
            unread -> StatusDot(colors.accent, unreadLabel)
        }
        if (stamp.isNotEmpty() && !working) {
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
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Box(
        Modifier
            .size(8.dp)
            .background(color, CircleShape)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun ThreadsButton(bot: Bot, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val angle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = seiraTween(SeiraMotion.BASE, reduced),
        label = "threads-chevron",
    )
    val label = stringResource(R.string.mobile_a11y_bot_threads, bot.name)
    val state = stringResource(
        if (expanded) R.string.mobile_a11y_thread_state_expanded else R.string.mobile_a11y_thread_state_collapsed,
        count,
    )
    Row(
        modifier = Modifier
            .testTag("threads-toggle.${bot.id}")
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = state
            }
            .padding(start = 8.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = count.toString(), fontSize = 12.sp, color = colors.ink3)
        Icon(
            imageVector = SeiraIcons.ChevronRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = angle },
        )
    }
}
