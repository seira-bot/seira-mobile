package com.openmausbot.companion.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.ui.theme.AvatarRing
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.seiraTween

/** One bot on the rail, and what its face should say about it. */
internal data class TeamMember(
    val bot: Bot,
    val waiting: Boolean,
    val working: Boolean,
    val face: CharacterMood,
)

/** About what one screen width holds; with more bots than this, "See all" earns its place. */
internal const val TEAM_RAIL_FIT = 4

private const val MEMBER_WIDTH = 64
private const val MEMBER_FACE = 60
private const val STATUS_FACE = 58

/**
 * The team: every visible bot but the chief, in a row that scrolls sideways, or
 * wrapped so all of them show at once after "See all".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamRail(
    members: List<TeamMember>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (Bot) -> Unit,
    onCreateThread: (Bot) -> Unit,
    onManageThreads: (Bot) -> Unit,
    onChangeLook: (Bot) -> Unit,
    modifier: Modifier = Modifier,
    /** The Chats tab's rail: no heading, one scrolling row, a status dot on each face. */
    statusDots: Boolean = false,
) {
    val colors = Seira.colors
    val canExpand = members.size > TEAM_RAIL_FIT && !statusDots
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 28 dp to the text; the 48 dp toggle row already adds 14 dp above it.
            .padding(top = if (statusDots) 4.dp else if (canExpand) 14.dp else 28.dp)
            .testTag("home-team")
            .animateContentSize(seiraTween(SeiraMotion.BASE)),
    ) {
        if (!statusDots) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_team),
                style = SeiraType.headline,
                color = colors.ink,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            if (canExpand) {
                Box(
                    modifier = Modifier
                        .testTag("home-team-toggle")
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onToggle)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.home_team_show_less)
                        } else {
                            pluralStringResource(R.plurals.home_team_see_all, members.size, members.size)
                        },
                        style = SeiraType.callout,
                        color = colors.accentText,
                    )
                }
            }
        }
        if (expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                members.forEach {
                    TeamMemberItem(it, statusDots, onOpen = { onOpen(it.bot) }, onCreateThread = { onCreateThread(it.bot) }, onManageThreads = { onManageThreads(it.bot) }, onChangeLook = { onChangeLook(it.bot) })
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
            ) {
                items(members, key = { it.bot.id }) {
                    TeamMemberItem(it, statusDots, onOpen = { onOpen(it.bot) }, onCreateThread = { onCreateThread(it.bot) }, onManageThreads = { onManageThreads(it.bot) }, onChangeLook = { onChangeLook(it.bot) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TeamMemberItem(
    member: TeamMember,
    statusDots: Boolean,
    onOpen: () -> Unit,
    onCreateThread: () -> Unit,
    onManageThreads: () -> Unit,
    onChangeLook: () -> Unit,
) {
    val colors = Seira.colors
    val bot = member.bot
    val label = when {
        member.waiting -> stringResource(R.string.home_team_member_waiting, bot.name)
        member.working -> stringResource(R.string.home_team_member_working, bot.name)
        else -> bot.name
    }
    val newThread = stringResource(R.string.mobile_new_thread_02057e28)
    val manageThreads = stringResource(R.string.home_manage_threads)
    val changeLook = stringResource(R.string.change_look_title)
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .width(MEMBER_WIDTH.dp)
                .testTag("team-member.${bot.id}")
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = manageThreads,
                    onLongClick = { menuOpen = true },
                    onClick = onOpen,
                )
                .heightIn(min = 48.dp)
                .clearAndSetSemantics {
                    contentDescription = label
                    customActions = listOf(
                        CustomAccessibilityAction(newThread) {
                            onCreateThread()
                            true
                        },
                        CustomAccessibilityAction(manageThreads) {
                            onManageThreads()
                            true
                        },
                        CustomAccessibilityAction(changeLook) {
                            onChangeLook()
                            true
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (statusDots) {
                Box {
                    IdentityAvatar(bot = bot, size = STATUS_FACE.dp, state = member.face, animated = false)
                    val dot = when {
                        member.waiting -> colors.warning
                        member.working -> colors.accent
                        else -> null
                    }
                    if (dot != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .background(colors.app, CircleShape)
                                .padding(3.dp)
                                .background(dot, CircleShape),
                        )
                    }
                }
            } else {
                IdentityAvatar(
                    bot = bot,
                    size = MEMBER_FACE.dp,
                    ring = if (member.working) AvatarRing.Working else AvatarRing.None,
                    waitingDot = member.waiting,
                    state = member.face,
                    animated = false,
                )
            }
            Text(
                text = bot.name,
                style = SeiraType.caption,
                color = colors.ink2,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(newThread) },
                leadingIcon = { Icon(SeiraIcons.Plus, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onCreateThread()
                },
                modifier = Modifier.testTag("team-new-thread.${bot.id}"),
            )
            DropdownMenuItem(
                text = { Text(manageThreads) },
                onClick = {
                    menuOpen = false
                    onManageThreads()
                },
                modifier = Modifier.testTag("team-manage-threads.${bot.id}"),
            )
            DropdownMenuItem(
                text = { Text(changeLook) },
                onClick = {
                    menuOpen = false
                    onChangeLook()
                },
                modifier = Modifier.testTag("team-change-look.${bot.id}"),
            )
        }
    }
}
