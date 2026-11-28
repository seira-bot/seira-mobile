package bot.seira.companion.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.Bot
import bot.seira.companion.core.TeamMap
import bot.seira.companion.ui.HapticCue
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.home.PageTopBar
import bot.seira.companion.ui.home.chats.AddDepartmentSheet
import bot.seira.companion.ui.play
import bot.seira.companion.ui.rememberHaptics
import bot.seira.companion.ui.profile.CardDivider
import bot.seira.companion.ui.profile.ProfileGroup
import bot.seira.companion.ui.profile.ProfileLoading
import bot.seira.companion.ui.profile.ProfileNotice
import bot.seira.companion.ui.theme.IdentityAvatar
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType

/** Who is on the team: the Chief of Staff, then each department with its bots. Tapping a bot opens its profile. */
@Composable
internal fun TeamMapScreen(onBack: () -> Unit, onOpenBot: (String) -> Unit = {}) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    var map by remember { mutableStateOf<TeamMap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var addingDepartment by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val layout = TeamMapRules.layout(state)

    LaunchedEffect(attempt) {
        failed = false
        val loaded = session.teamMap()
        map = loaded
        failed = loaded == null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Seira.colors.app)
            .testTag("team-map-screen"),
    ) {
        PageTopBar(title = stringResource(R.string.shell_drawer_team_map), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                failed -> ProfileNotice(stringResource(R.string.team_failed), retry = { attempt++ })
                map == null -> ProfileLoading()
                layout.isEmpty -> ProfileNotice(stringResource(R.string.team_empty))
                else -> {
                    layout.chief?.let { chief ->
                        ProfileGroup(title = stringResource(R.string.team_chief_group)) {
                            TeamRow(
                                bot = chief,
                                role = stringResource(R.string.team_chief_role),
                                status = TeamMapRules.status(map, chief.id),
                                onClick = { onOpenBot(chief.id) },
                            )
                        }
                    }
                    layout.groups.forEach { group ->
                        ProfileGroup(title = group.name ?: stringResource(R.string.team_general)) {
                            if (group.bots.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.home2_team_department_empty),
                                    style = SeiraType.callout,
                                    color = Seira.colors.ink3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                            group.bots.forEachIndexed { index, bot ->
                                if (index > 0) CardDivider()
                                TeamRow(
                                    bot = bot,
                                    role = bot.title,
                                    status = TeamMapRules.status(map, bot.id),
                                    onClick = { onOpenBot(bot.id) },
                                )
                            }
                        }
                    }
                    ProfileGroup(title = null) {
                        AddDepartmentRow(
                            onClick = {
                                haptics.play(HapticCue.SELECT)
                                addingDepartment = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (addingDepartment) {
        AddDepartmentSheet(
            existing = state.sidebarSections.map { it.name },
            onAdd = { name -> session.assignSection(name, emptyList(), reportsError = false) != null },
            onDismiss = { addingDepartment = false },
        )
    }
}

@Composable
private fun AddDepartmentRow(onClick: () -> Unit) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("team-add-department")
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SeiraIcons.Plus, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        Text(text = stringResource(R.string.home2_team_add_department), style = SeiraType.bodyMedium, color = colors.ink)
    }
}

@Composable
private fun TeamRow(bot: Bot, role: String, status: TeamStatus?, onClick: () -> Unit) {
    val colors = Seira.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdentityAvatar(bot = bot, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = bot.name, style = SeiraType.bodyMedium, color = colors.ink)
            if (role.isNotBlank()) Text(text = role, style = SeiraType.callout, color = colors.ink3)
        }
        when (status) {
            TeamStatus.WORKING -> Text(
                text = stringResource(R.string.chat_status_working),
                style = SeiraType.callout,
                color = colors.success,
            )
            TeamStatus.WAITING -> Text(
                text = stringResource(R.string.team_status_waiting),
                style = SeiraType.callout,
                color = colors.warning,
            )
            null -> Unit
        }
        Icon(SeiraIcons.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(20.dp))
    }
}
