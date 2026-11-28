package bot.seira.companion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bot.seira.companion.R
import bot.seira.companion.character.CarvedAvatar
import bot.seira.companion.core.SeiraLook
import bot.seira.companion.ui.TouchTarget
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType

internal val DRAWER_WIDTH = 308.dp
internal val DRAWER_SHIFT = 318.dp

/** One department in the drawer: its name and how many bots it holds. */
internal data class DrawerDepartment(val name: String, val bots: Int)

/** Where each drawer row leads. The shell decides what a tap does. */
internal class DrawerActions(
    val onComputer: () -> Unit,
    val onSearch: () -> Unit,
    val onAllChats: () -> Unit,
    val onDepartment: (String) -> Unit,
    val onAddDepartment: () -> Unit,
    val onInbox: () -> Unit,
    val onTeamMap: () -> Unit,
    val onTeamMemory: () -> Unit,
    val onCalendar: () -> Unit,
    val onConnectedApps: () -> Unit,
    val onAiUsage: () -> Unit,
    val onSettings: () -> Unit,
    val onNew: () -> Unit,
)

/** What the drawer shows. */
internal data class DrawerState(
    val computerName: String?,
    val connectionText: String,
    val connected: Boolean,
    val departments: List<DrawerDepartment>,
    val selectedDepartment: String?,
    val allChatsSelected: Boolean,
    val inboxSelected: Boolean,
    val inboxCount: Int,
)

/** The left drawer: computer, search, departments and the workspace pages. */
@Composable
internal fun HomeDrawer(state: DrawerState, actions: DrawerActions, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    val menuTitle = stringResource(R.string.shell_menu)
    Column(
        modifier = modifier
            .width(DRAWER_WIDTH)
            .fillMaxHeight()
            .background(colors.app)
            .statusBarsPadding()
            .navigationBarsPadding()
            .semantics { paneTitle = menuTitle }
            .testTag("home-drawer"),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawerHeader()
            ComputerCard(state = state, onClick = actions.onComputer)
            SearchField(onClick = actions.onSearch)
            DrawerRow(
                icon = SeiraIcons.MessageCircle,
                label = stringResource(R.string.shell_drawer_all_chats),
                selected = state.allChatsSelected,
                onClick = actions.onAllChats,
                iconTint = colors.accentText,
            )

            DrawerLabel(stringResource(R.string.shell_drawer_departments))
            state.departments.forEach { department ->
                DrawerRow(
                    icon = SeiraIcons.Users,
                    label = department.name,
                    selected = state.selectedDepartment == department.name,
                    onClick = { actions.onDepartment(department.name) },
                    trailing = department.bots.toString(),
                    description = pluralStringResource(
                        R.plurals.shell_department_row,
                        department.bots,
                        department.name,
                        department.bots,
                    ),
                )
            }
            DrawerRow(
                icon = SeiraIcons.Plus,
                label = stringResource(R.string.shell_drawer_add_department),
                selected = false,
                onClick = actions.onAddDepartment,
            )

            DrawerLabel(stringResource(R.string.mobile_settings_workspace_section))
            DrawerRow(
                icon = SeiraIcons.Inbox,
                label = stringResource(R.string.shell_tab_inbox),
                selected = state.inboxSelected,
                onClick = actions.onInbox,
                trailing = state.inboxCount.takeIf { it > 0 }?.toString(),
                description = if (state.inboxCount > 0) {
                    pluralStringResource(R.plurals.shell_inbox_waiting, state.inboxCount, state.inboxCount)
                } else {
                    null
                },
            )
            DrawerRow(
                icon = SeiraIcons.Network,
                label = stringResource(R.string.shell_drawer_team_map),
                selected = false,
                onClick = actions.onTeamMap,
            )
            DrawerRow(
                icon = SeiraIcons.BookMarked,
                label = stringResource(R.string.shell_drawer_department_memory),
                selected = false,
                onClick = actions.onTeamMemory,
            )
            DrawerRow(
                icon = SeiraIcons.Calendar,
                label = stringResource(R.string.mobile_calendar_open),
                selected = false,
                onClick = actions.onCalendar,
            )
            DrawerRow(
                icon = SeiraIcons.Plug,
                label = stringResource(R.string.settings_connected_apps),
                selected = false,
                onClick = actions.onConnectedApps,
            )
            DrawerRow(
                icon = SeiraIcons.Gauge,
                label = stringResource(R.string.ai_usage_title),
                selected = false,
                onClick = actions.onAiUsage,
            )
            Spacer(Modifier.size(8.dp))
        }
        DrawerFooter(onSettings = actions.onSettings, onNew = actions.onNew)
    }
}

@Composable
private fun DrawerHeader() {
    val colors = Seira.colors
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarvedAvatar(look = SeiraLook.SEIRA, size = 40.dp)
        Text(
            text = stringResource(R.string.app_name),
            style = SeiraType.title2,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun ComputerCard(state: DrawerState, onClick: () -> Unit) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.dp, colors.hairline, shape)
            .background(colors.s1, shape)
            .clickable(onClickLabel = stringResource(R.string.shell_switch_computer), role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("home-drawer-computer"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SeiraIcons.Laptop, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.computerName ?: stringResource(R.string.home_computers_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.connectionText,
                fontSize = 12.sp,
                color = if (state.connected) colors.success else colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(SeiraIcons.ChevronDown, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SearchField(onClick: () -> Unit) {
    val colors = Seira.colors
    val label = stringResource(R.string.home_search_prompt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .border(1.dp, colors.hairline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag("home-drawer-search"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SeiraIcons.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(20.dp))
        Text(text = label, fontSize = 14.sp, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = Seira.colors.ink3,
        modifier = Modifier
            .padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    description: String? = null,
    iconTint: Color = Seira.colors.ink2,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) colors.s2 else Color.Transparent, shape)
            .border(1.dp, if (selected) colors.hairline else Color.Transparent, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            fontSize = 14.5.sp,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(text = trailing, fontSize = 13.sp, color = colors.ink3)
        }
    }
}

@Composable
private fun DrawerFooter(onSettings: () -> Unit, onNew: () -> Unit) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 50.dp)
                .clip(CircleShape)
                .background(colors.s1, CircleShape)
                .border(1.dp, colors.hairline, CircleShape)
                .clickable(role = Role.Button, onClick = onSettings)
                .padding(horizontal = 14.dp)
                .testTag("home-drawer-settings"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SeiraIcons.Sliders, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
            Text(
                text = stringResource(R.string.mobile_settings_c7f73bb5),
                fontSize = 14.sp,
                color = colors.ink,
            )
        }
        TouchTarget(
            onClick = onNew,
            size = 50.dp,
            contentDescription = stringResource(R.string.home_new_chat),
            modifier = Modifier.testTag("home-drawer-new"),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SeiraIcons.Plus, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}
