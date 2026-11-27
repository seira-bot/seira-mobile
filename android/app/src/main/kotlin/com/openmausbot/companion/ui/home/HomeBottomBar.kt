package com.openmausbot.companion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.HomeTab
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons

private const val BADGE_CAP = 99

/** The floating pill with the three home tabs. [inboxCount] is the number of decisions that wait. */
@Composable
internal fun HomeBottomBar(
    selected: HomeTab,
    inboxCount: Int,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
            .background(colors.s1, CircleShape)
            .border(1.dp, colors.hairline, CircleShape)
            .padding(6.dp)
            .selectableGroup()
            .testTag("home-bottom-bar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarItem(
            tab = HomeTab.Chats,
            selected = selected,
            icon = SeiraIcons.MessageCircle,
            label = stringResource(R.string.home_chats),
            description = null,
            badge = 0,
            onSelect = onSelect,
        )
        BarItem(
            tab = HomeTab.Inbox,
            selected = selected,
            icon = SeiraIcons.Inbox,
            label = stringResource(R.string.shell_tab_inbox),
            description = if (inboxCount > 0) {
                pluralStringResource(R.plurals.shell_inbox_waiting, inboxCount, inboxCount)
            } else {
                null
            },
            badge = inboxCount,
            onSelect = onSelect,
        )
        BarItem(
            tab = HomeTab.Automations,
            selected = selected,
            icon = SeiraIcons.Clock,
            label = stringResource(R.string.shell_tab_automations),
            description = null,
            badge = 0,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun RowScope.BarItem(
    tab: HomeTab,
    selected: HomeTab,
    icon: ImageVector,
    label: String,
    description: String?,
    badge: Int,
    onSelect: (HomeTab) -> Unit,
) {
    val colors = Seira.colors
    val on = tab == selected
    Column(
        modifier = Modifier
            .weight(1f)
            .height(54.dp)
            .clip(CircleShape)
            .background(if (on) colors.s2 else Color.Transparent, CircleShape)
            .border(1.dp, if (on) colors.line2 else Color.Transparent, CircleShape)
            .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .testTag("home-tab-${tab.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (on) colors.ink else colors.ink3,
                modifier = Modifier.size(22.dp),
            )
            if (badge > 0) {
                CountBadge(
                    count = badge,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp),
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (on) colors.ink else colors.ink3,
            maxLines = 1,
        )
    }
}

/** The pink count on the Inbox tab. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .background(colors.accent, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > BADGE_CAP) "$BADGE_CAP+" else count.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onAccent,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
