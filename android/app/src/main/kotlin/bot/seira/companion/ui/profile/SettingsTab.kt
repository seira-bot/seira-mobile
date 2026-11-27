package bot.seira.companion.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.Bot
import bot.seira.companion.core.BotOverview
import bot.seira.companion.core.BotOverviewGrant
import bot.seira.companion.core.BotOverviewGrantLevel
import bot.seira.companion.core.BotSkill
import bot.seira.companion.core.Instance
import bot.seira.companion.core.ModelSelection
import bot.seira.companion.core.ReplyStyle
import bot.seira.companion.core.forTask
import bot.seira.companion.ui.ChangeLookSheet
import bot.seira.companion.ui.LocalCompanion
import bot.seira.companion.ui.ModelPickerFields
import bot.seira.companion.ui.OverviewBody
import bot.seira.companion.ui.ProfileRules
import bot.seira.companion.ui.theme.InkPillButton
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType
import bot.seira.companion.ui.theme.SegmentedControl
import kotlinx.coroutines.launch

@Composable
internal fun SettingsTab(
    bot: Bot,
    draft: ProfileDraft,
    baseline: ProfileDraft,
    onDraft: (ProfileDraft) -> Unit,
    onSaved: (ProfileDraft) -> Unit,
    onDeleting: (Boolean) -> Unit,
    onDeleted: () -> Unit,
) {
    val session = LocalCompanion.current.session
    val colors = Seira.colors
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var changingLook by remember { mutableStateOf(false) }
    var showingOverview by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var overview by remember(bot.id) { mutableStateOf<BotOverview?>(null) }
    var skills by remember(bot.id) { mutableStateOf<List<BotSkill>?>(null) }

    var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }
    var modelsLoaded by remember { mutableStateOf(false) }
    var savedModel by remember(bot.id) { mutableStateOf(bot.modelSelection) }
    var instanceId by rememberSaveable(bot.id) { mutableStateOf(bot.modelSelection.instanceId) }
    var modelId by rememberSaveable(bot.id) { mutableStateOf(bot.modelSelection.model) }
    var effort by rememberSaveable(bot.id) { mutableStateOf(bot.modelSelection.effort.orEmpty()) }
    val modelDraft = ModelSelection(instanceId, modelId, effort.ifEmpty { null })
    val currentTask = bot.forTask(bot.threadId)

    LaunchedEffect(bot.id) {
        overview = session.loadOverview(bot.id, quiet = true)
        instances = session.modelInstances()
        modelsLoaded = true
        val loaded = session.botSkills(bot)
        if (loaded == null) session.actionError = null
        skills = loaded
    }

    val styles = listOf(ReplyStyle.DEFAULT, ReplyStyle.CONVERSATIONAL)

    ProfileGroup(title = stringResource(R.string.profile_group_profile)) {
        if (overview != null) {
            ProfileRow(
                title = stringResource(R.string.profile_what_it_does),
                onClick = { showingOverview = true },
                trailing = { ChevronIcon() },
            )
            CardDivider()
        }
        ProfileRow(
            title = stringResource(R.string.mobile_avatar_7631b26e),
            onClick = { changingLook = true },
            trailing = { ChevronIcon() },
        )
    }

    overview?.grants?.let { grants ->
        ProfileGroup(
            title = stringResource(R.string.profile_group_apps),
            note = stringResource(R.string.profile_apps_note),
        ) {
            val rows = ProfileRules.connectorGrantRows(grants)
            if (rows.isEmpty()) {
                ProfileRow(title = stringResource(R.string.profile_apps_none))
            } else {
                rows.forEachIndexed { index, row ->
                    if (index > 0) CardDivider()
                    ProfileRow(
                        title = row.service,
                        trailing = {
                            Text(
                                text = grantSummary(grants[index]),
                                color = colors.ink3,
                                style = SeiraType.callout,
                            )
                        },
                    )
                }
            }
        }
    }

    ProfileGroup(
        title = stringResource(R.string.profile_group_model),
        note = stringResource(R.string.profile_model_note),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModelPickerFields(
                instances = instances,
                loaded = modelsLoaded,
                saved = savedModel,
                draft = modelDraft,
                onDraft = {
                    instanceId = it.instanceId
                    modelId = it.model
                    effort = it.effort.orEmpty()
                },
                taskBusy = currentTask?.busy,
                applying = busy,
                applyLabel = "Apply model",
                canChange = currentTask != null,
                onApply = {
                    scope.launch {
                        busy = true
                        try {
                            val target = session.state.value.bot(bot.id)?.forTask(bot.threadId) ?: return@launch
                            session.updateModel(modelDraft, target)?.let { updated ->
                                savedModel = updated.modelSelection
                                instanceId = updated.modelSelection.instanceId
                                modelId = updated.modelSelection.model
                                effort = updated.modelSelection.effort.orEmpty()
                            }
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }

    ProfileGroup(title = stringResource(R.string.profile_group_identity)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraft(draft.copy(name = it)) },
                label = { Text(stringResource(R.string.mobile_name_709a2322)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth().testTag("profile-name"),
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onDraft(draft.copy(title = it)) },
                label = { Text(stringResource(R.string.mobile_title_768e0c1c)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile-title"),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { onDraft(draft.copy(description = it)) },
                label = { Text(stringResource(R.string.mobile_what_this_agent_does_82aa1b56)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().testTag("profile-description"),
            )
        }
    }

    ProfileGroup(title = stringResource(R.string.profile_group_behaviour)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.profile_reply_style),
                style = SeiraType.bodyMedium,
                color = colors.ink,
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.profile_reply_default) to null,
                    stringResource(R.string.profile_reply_conversational) to null,
                ),
                selectedIndex = styles.indexOf(draft.replyStyle),
                onSelect = { onDraft(draft.copy(replyStyle = styles[it])) },
            )
            OutlinedTextField(
                value = draft.soul,
                onValueChange = { onDraft(draft.copy(soul = it)) },
                label = { Text(stringResource(R.string.profile_soul_label)) },
                placeholder = { Text(stringResource(R.string.profile_soul_placeholder)) },
                minLines = 4,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth().testTag("profile-soul"),
            )
        }
    }

    skills?.let { list ->
        ProfileGroup(
            title = stringResource(R.string.profile_group_skills),
            note = stringResource(R.string.profile_skills_note),
        ) {
            if (list.isEmpty()) {
                ProfileRow(title = stringResource(R.string.profile_skills_empty))
            } else {
                list.forEachIndexed { index, skill ->
                    if (index > 0) CardDivider()
                    ProfileRow(
                        title = skill.name,
                        subtitle = skill.description.takeIf { it.isNotBlank() },
                        trailing = {
                            Text(
                                text = stringResource(if (skill.enabled) R.string.profile_skill_on else R.string.profile_skill_off),
                                color = if (skill.enabled) colors.success else colors.ink3,
                                style = SeiraType.callout,
                            )
                        },
                    )
                }
            }
        }
    }

    ProfileGroup(title = stringResource(R.string.profile_group_more)) {
        ProfileRow(
            title = stringResource(R.string.profile_delete_bot),
            titleColor = colors.danger,
            enabled = !busy,
            onClick = { confirmingDelete = true },
            modifier = Modifier.testTag("profile-delete"),
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        InkPillButton(
            text = stringResource(R.string.profile_save),
            enabled = ProfileDraftRules.canSave(draft, baseline, busy),
            modifier = Modifier.fillMaxWidth().testTag("profile-save"),
            onClick = {
                scope.launch {
                    busy = true
                    try {
                        val patch = ProfileDraftRules.patch(draft, baseline)
                        val updated = session.updateProfile(patch, bot)
                        if (updated != null) {
                            onSaved(
                                ProfileDraft.of(updated).copy(
                                    soul = draft.soul.trim(),
                                    replyStyle = draft.replyStyle,
                                ),
                            )
                        }
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }

    BotActionsGroup(bot)

    if (changingLook) ChangeLookSheet(bot = bot, onDismiss = { changingLook = false })

    if (showingOverview) {
        OverviewSheet(overview = overview, onDismiss = { showingOverview = false })
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title, bot.name)) },
            text = { Text(stringResource(R.string.profile_delete_body, bot.name)) },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.mobile_cancel_77dfd213))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        scope.launch {
                            busy = true
                            onDeleting(true)
                            val removed = session.deleteBot(bot)
                            busy = false
                            if (removed) onDeleted() else onDeleting(false)
                        }
                    },
                ) {
                    Text(stringResource(R.string.mobile_delete_f6fdbe48), color = colors.danger)
                }
            },
        )
    }
}

@Composable
private fun ChevronIcon() {
    Icon(
        SeiraIcons.ChevronRight,
        contentDescription = null,
        tint = Seira.colors.ink3,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun grantSummary(grant: BotOverviewGrant): String = when (grant.level) {
    BotOverviewGrantLevel.All -> stringResource(R.string.profile_apps_all)
    BotOverviewGrantLevel.None -> stringResource(R.string.profile_apps_no_tools)
    BotOverviewGrantLevel.Partial -> pluralStringResource(R.plurals.profile_apps_tools, grant.toolCount, grant.toolCount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewSheet(overview: BotOverview?, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (overview != null) OverviewBody(overview)
        }
    }
}
