package bot.seira.companion.ui.home.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.R
import bot.seira.companion.ui.TouchTarget
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraMotion
import bot.seira.companion.ui.theme.rememberReducedMotion
import bot.seira.companion.ui.theme.seiraTween

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

/** An icon-only button at the end of a group heading. */
@Composable
internal fun HeaderIconButton(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    TouchTarget(
        onClick = onClick,
        contentDescription = label,
        modifier = Modifier.testTag(tag),
    ) {
        Icon(icon, contentDescription = null, tint = Seira.colors.ink, modifier = Modifier.size(20.dp))
    }
}

/**
 * The heading of a group. A foldable one is a button with a chevron that turns
 * to the side when the group is folded; [trailing] holds the buttons at the end.
 * A folded group shows how many of its bots wait for the person in [waitingCount].
 */
@Composable
internal fun GroupHeader(
    title: String,
    foldable: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    tag: String,
    startPadding: Dp,
    modifier: Modifier = Modifier,
    waitingCount: Int = 0,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val angle by animateFloatAsState(
        targetValue = if (open) 0f else -90f,
        animationSpec = seiraTween(SeiraMotion.BASE, reduced),
        label = "group-chevron",
    )
    val openLabel = stringResource(R.string.home2_fold_open)
    val foldedLabel = stringResource(R.string.home2_fold_folded)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = startPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (foldable) {
                Row(
                    modifier = Modifier
                        .testTag(tag)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onToggle)
                        .semantics(mergeDescendants = true) {
                            heading()
                            stateDescription = if (open) openLabel else foldedLabel
                        }
                        .padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
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
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = SeiraIcons.ChevronDown,
                        contentDescription = null,
                        tint = colors.ink3,
                        modifier = Modifier
                            .size(13.dp)
                            .graphicsLayer { rotationZ = angle },
                    )
                    if (!open && waitingCount > 0) {
                        WaitingBadge(waitingCount, Modifier.testTag("$tag.waiting"))
                    }
                }
            } else {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .testTag(tag)
                        .semantics { heading() },
                )
            }
        }
        trailing()
    }
}

/** A small accent dot with the number of bots that wait for the person. */
@Composable
private fun WaitingBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    val label = stringResource(R.string.home2_waiting_count, count)
    Box(
        modifier = modifier
            .heightIn(min = 18.dp)
            .background(colors.accent, CircleShape)
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimary,
            maxLines = 1,
        )
    }
}

/** The "Show all chats" action that ends a department filter. */
@Composable
internal fun ShowAllChatsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag("chats-show-all")
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.home2_show_all),
            fontSize = 13.sp,
            color = Seira.colors.accentText,
        )
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

@Composable
internal fun GroupsTopRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 1.dp)
            .background(Seira.colors.hairline),
    )
}

/** The search entry of Advanced mode: one row that opens the search screen. */
@Composable
internal fun SearchRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    val label = stringResource(R.string.home2_search)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("chats-search")
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(colors.s1, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SeiraIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
        Text(text = label, fontSize = 15.sp, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
