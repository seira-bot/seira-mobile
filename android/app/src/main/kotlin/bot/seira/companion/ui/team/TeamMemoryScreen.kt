package bot.seira.companion.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.TeamMemoryEdit
import bot.seira.companion.core.TeamMemoryEntry
import bot.seira.companion.core.TeamMemoryKind
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.MIN_TOUCH_TARGET
import bot.seira.companion.ui.home.PageTopBar
import bot.seira.companion.ui.profile.CardDivider
import bot.seira.companion.ui.profile.ProfileGroup
import bot.seira.companion.ui.profile.ProfileLoading
import bot.seira.companion.ui.profile.ProfileNotice
import bot.seira.companion.ui.theme.InkPillButton
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType
import kotlinx.coroutines.launch

/** What the team remembers, by department: the notes every bot reads, and a way to add one. */
@Composable
internal fun TeamMemoryScreen(onBack: () -> Unit) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = Seira.colors
    val sections = TeamMemoryRules.sections(state)
    val generalName = stringResource(R.string.team_general)
    val changedText = stringResource(R.string.team_memory_changed)

    var section by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<TeamMemoryEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<TeamMemoryEntry?>(null) }

    LaunchedEffect(section, attempt) {
        entries = null
        failed = false
        val page = session.teamMemory(section)
        entries = page?.entries
        failed = page == null
    }

    suspend fun mutate(entry: TeamMemoryEntry?, block: suspend () -> TeamMemoryEdit?): Boolean {
        busy = true
        try {
            val edit = block()
            if (edit != null) {
                entries = edit.entries
                return true
            }
            val latest = session.teamMemory(section)
            if (latest != null) {
                if (entry != null && TeamMemoryRules.changedSince(entry, latest.entries)) {
                    session.actionError = changedText
                }
                entries = latest.entries
            }
            return false
        } finally {
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.app)
            .imePadding()
            .testTag("team-memory-screen"),
    ) {
        PageTopBar(title = stringResource(R.string.shell_drawer_department_memory), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sections.forEach { name ->
                    val label = name.ifEmpty { generalName }
                    val isSelected = name == section
                    Box(
                        modifier = Modifier
                            .heightIn(min = MIN_TOUCH_TARGET)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.primary else colors.s1, CircleShape)
                            .border(1.dp, if (isSelected) colors.primary else colors.line2, CircleShape)
                            .selectable(selected = isSelected, role = Role.RadioButton, onClick = { section = name })
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = SeiraType.bodyMedium,
                            color = if (isSelected) colors.onPrimary else colors.ink,
                            maxLines = 1,
                        )
                    }
                }
            }

            val list = entries
            when {
                failed -> ProfileNotice(stringResource(R.string.team_memory_failed), retry = { attempt++ })
                list == null -> ProfileLoading()
                else -> {
                    val proposed = TeamMemoryRules.proposed(list)
                    if (proposed.isNotEmpty()) {
                        ProfileGroup(title = stringResource(R.string.team_memory_pending)) {
                            proposed.forEachIndexed { index, entry ->
                                if (index > 0) CardDivider()
                                NoteRow(
                                    entry = entry,
                                    busy = busy,
                                    onAccept = {
                                        scope.launch {
                                            mutate(entry) { session.updateTeamMemory(section, entry, accept = true) }
                                        }
                                    },
                                    onDelete = { deleting = entry },
                                )
                            }
                        }
                    }
                    val accepted = TeamMemoryRules.accepted(list)
                    ProfileGroup(title = stringResource(R.string.team_memory_notes)) {
                        if (accepted.isEmpty()) {
                            ProfileNotice(stringResource(R.string.team_memory_empty))
                        } else {
                            accepted.forEachIndexed { index, entry ->
                                if (index > 0) CardDivider()
                                NoteRow(entry = entry, busy = busy, onAccept = null, onDelete = { deleting = entry })
                            }
                        }
                    }
                }
            }

            ProfileGroup(title = stringResource(R.string.team_memory_add_label)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(stringResource(R.string.team_memory_add_placeholder)) },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().testTag("team-memory-draft"),
                    )
                    InkPillButton(
                        text = stringResource(R.string.team_memory_add_button),
                        enabled = !busy && list != null && TeamMemoryRules.split(draft) != null,
                        modifier = Modifier.fillMaxWidth().testTag("team-memory-add"),
                        onClick = {
                            val (name, detail) = TeamMemoryRules.split(draft) ?: return@InkPillButton
                            scope.launch {
                                val added = mutate(null) {
                                    session.addTeamMemory(section, TeamMemoryKind.TERM, name, detail)
                                }
                                if (added) draft = ""
                            }
                        },
                    )
                }
            }
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.team_memory_delete_title)) },
            text = { Text(stringResource(R.string.team_memory_delete_body, entry.name)) },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.mobile_cancel_77dfd213)) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch { mutate(entry) { session.deleteTeamMemory(section, entry) } }
                    },
                ) { Text(stringResource(R.string.mobile_delete_f6fdbe48), color = colors.danger) }
            },
        )
    }
}

@Composable
private fun NoteRow(entry: TeamMemoryEntry, busy: Boolean, onAccept: (() -> Unit)?, onDelete: () -> Unit) {
    val colors = Seira.colors
    val deleteLabel = stringResource(R.string.team_memory_delete)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = entry.name, style = SeiraType.bodyMedium, color = colors.ink)
            if (entry.detail.isNotBlank()) {
                Text(text = entry.detail, style = SeiraType.callout, color = colors.ink3)
            }
        }
        if (onAccept != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onAccept)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.team_memory_accept),
                    style = SeiraType.bodyMedium,
                    color = if (busy) colors.ink3 else colors.accentText,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(MIN_TOUCH_TARGET)
                .clip(CircleShape)
                .clickable(enabled = !busy, role = Role.Button, onClick = onDelete)
                .semantics { contentDescription = deleteLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(SeiraIcons.X, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
        }
    }
}
