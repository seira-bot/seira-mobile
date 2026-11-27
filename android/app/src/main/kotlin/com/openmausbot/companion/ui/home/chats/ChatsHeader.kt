package com.openmausbot.companion.ui.home.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.TouchTarget
import com.openmausbot.companion.ui.theme.AvatarRing
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType

@Composable
internal fun ChatsGreeting(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SeiraType.title1.copy(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        color = Seira.colors.ink,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 4.dp)
            .testTag("roster-header")
            .semantics { heading() },
    )
}

/** All, Bots and Groups as pills, and the button that opens search. */
@Composable
internal fun ChatsFilterBar(
    selected: ChatsFilter,
    onSelect: (ChatsFilter) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterPill(stringResource(R.string.chats_filter_all), selected == ChatsFilter.ALL, "chats-filter-all") {
            onSelect(ChatsFilter.ALL)
        }
        FilterPill(stringResource(R.string.mobile_bots_4ca88ea4), selected == ChatsFilter.BOTS, "chats-filter-bots") {
            onSelect(ChatsFilter.BOTS)
        }
        FilterPill(stringResource(R.string.mobile_groups_ae9629f4), selected == ChatsFilter.GROUPS, "chats-filter-groups") {
            onSelect(ChatsFilter.GROUPS)
        }
        Box(modifier = Modifier.weight(1f))
        TouchTarget(
            onClick = onSearch,
            contentDescription = stringResource(R.string.mobile_search_bce06414),
            modifier = Modifier.testTag("chats-search"),
        ) {
            Icon(SeiraIcons.Search, contentDescription = null, tint = Seira.colors.ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun FilterPill(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = Seira.colors
    val shape = CircleShape
    Box(
        modifier = Modifier
            .testTag(tag)
            .heightIn(min = 48.dp)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(shape)
                .background(if (selected) colors.primary else androidx.compose.ui.graphics.Color.Transparent, shape)
                .border(1.dp, if (selected) colors.primary else colors.hairline, shape)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) colors.onPrimary else colors.ink,
                maxLines = 1,
            )
        }
    }
}

/** The chief of staff, large, with the line that needs the person or the quiet line. */
@Composable
internal fun ChatsHero(
    bot: Bot,
    role: String,
    line: String,
    busy: Boolean,
    face: CharacterMood,
    canTalk: Boolean,
    onOpen: () -> Unit,
    onTalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(22.dp)
    val talkLabel = stringResource(R.string.home_talk_to, bot.name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("home-hero")
            .clip(shape)
            .background(colors.s1, shape)
            .border(1.dp, colors.hairline, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .testTag("home-hero-open")
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(14.dp)
                .clearAndSetSemantics { contentDescription = "${bot.name}, $role, $line" },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(5.dp)) {
                IdentityAvatar(
                    bot = bot,
                    size = 60.dp,
                    ring = if (busy) AvatarRing.Working else AvatarRing.None,
                    state = face,
                    animated = false,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = bot.name,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = role,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = line,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = colors.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (canTalk) {
            Box(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .testTag("home-talk")
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primary, CircleShape)
                    .clickable(role = Role.Button, onClick = onTalk)
                    .semantics { contentDescription = talkLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(SeiraIcons.AudioLines, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A heading over a stretch of the list, with an optional action at its end. */
@Composable
internal fun ChatsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Seira.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = colors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            Row(
                modifier = Modifier
                    .testTag("chats-new-group")
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .semantics(mergeDescendants = true) { contentDescription = actionLabel }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SeiraIcons.Plus, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(16.dp))
                Text(text = actionLabel, fontSize = 13.sp, color = colors.ink2)
            }
        }
    }
}

/** The open workspace of the computer; it shows only when the computer lists more than one. */
@Composable
internal fun ChatsWorkspaceChip(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    val label = stringResource(R.string.workspace_chip_description, name)
    Row(
        modifier = modifier
            .testTag("home-workspace")
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, fontSize = 13.sp, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(SeiraIcons.ChevronDown, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(14.dp))
    }
}
