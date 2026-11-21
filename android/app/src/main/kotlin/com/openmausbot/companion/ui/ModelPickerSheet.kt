package com.openmausbot.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Instance
import com.openmausbot.companion.core.ModelListResult
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.forTask
import kotlinx.coroutines.launch

/**
 * The provider, model and reasoning pickers with their apply button. The bot
 * settings sheet and the failed-turn "Switch model" sheet both draw them, so
 * the same [ModelRules] decide what each offers.
 *
 * [unavailable] is the model that just failed; picking it shows a note.
 */
@Composable
internal fun ModelPickerFields(
    instances: List<Instance>,
    loaded: Boolean,
    saved: ModelSelection,
    draft: ModelSelection,
    onDraft: (ModelSelection) -> Unit,
    taskBusy: Boolean?,
    applying: Boolean,
    applyLabel: String,
    canChange: Boolean = true,
    unavailable: ModelSelection? = null,
    onApply: () -> Unit,
) {
    val instanceChoices = ModelRules.instanceChoices(instances, saved)
    val selectedInstance = instances.firstOrNull { it.instanceId == draft.instanceId }
    if (!loaded) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(localizedMobileCopy(ModelRules.LOADING), fontSize = 15.sp, modifier = Modifier.weight(1f))
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    } else if (instanceChoices.isEmpty()) {
        IconNote(text = ModelRules.NONE_AVAILABLE, icon = Icons.Filled.Warning)
    } else {
        val providerRows = buildList {
            if (ModelRules.providerMissing(instances, draft.instanceId)) {
                add(
                    VoiceChoice(
                        id = draft.instanceId,
                        label = ModelRules.CURRENT_PROVIDER_UNAVAILABLE,
                        detail = null,
                        enabled = false,
                    ),
                )
            }
            instanceChoices.forEach {
                add(
                    VoiceChoice(
                        id = it.instanceId,
                        label = ModelRules.instanceLabel(it),
                        detail = null,
                        enabled = it.snapshot.isAvailable,
                    ),
                )
            }
        }
        ChoicePicker(
            label = stringResource(R.string.mobile_provider_7ceee3f3),
            choices = providerRows,
            selected = draft.instanceId,
            onSelect = { id ->
                val instance = instances.firstOrNull { it.instanceId == id }
                if (instance != null) onDraft(ModelRules.defaultsFor(instance, saved))
            },
        )
        ChoicePicker(
            label = stringResource(R.string.mobile_model_68c2cc7f),
            choices = ModelRules.modelChoices(selectedInstance, draft.model).map {
                VoiceChoice(id = it.id, label = it.label, detail = null, enabled = true)
            },
            selected = draft.model,
            enabled = selectedInstance?.snapshot?.isAvailable == true,
            onSelect = { onDraft(draft.copy(model = it)) },
        )
        if (unavailable != null && unavailable.instanceId == draft.instanceId && unavailable.model == draft.model) {
            IconNote(text = stringResource(R.string.chat_failed_turn_model_down), icon = Icons.Filled.Warning)
        }
        val effortLevels = ModelRules.effortLevels(selectedInstance)
        if (effortLevels.isNotEmpty()) {
            ChoicePicker(
                label = stringResource(R.string.mobile_reasoning_effort_cd32c0f5),
                choices = buildList {
                    add(VoiceChoice(id = "", label = ModelRules.DEFAULT_EFFORT_LABEL, detail = null, enabled = true))
                    effortLevels.forEach {
                        add(VoiceChoice(id = it, label = ModelRules.effortLabel(it), detail = null, enabled = true))
                    }
                },
                selected = draft.effort.orEmpty(),
                onSelect = { onDraft(draft.copy(effort = it.ifEmpty { null })) },
            )
        }
        ModelRules.note(taskBusy, selectedInstance)?.let { note ->
            IconNote(text = note, icon = Icons.Filled.Info)
        }
        ActionRow(
            text = applyLabel,
            icon = Icons.Filled.Check,
            enabled = !applying && canChange && ModelRules.canApply(
                loaded = loaded,
                botBusy = taskBusy,
                instance = selectedInstance,
                draft = draft,
                saved = saved,
            ),
            onClick = onApply,
        )
    }
}

/**
 * "Switch model" under a failed turn: changes the model of this conversation
 * only, never the bot's default. [onSwitched] gets the label of the model now
 * in use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    bot: Bot,
    unavailable: ModelSelection,
    onSwitched: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val session = LocalCompanion.current.session
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    val current = (state.bot(bot.id) ?: bot).forTask(bot.threadId)
    val saved = current?.modelSelection ?: bot.modelSelection
    var instances by remember { mutableStateOf<List<Instance>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var failedToLoad by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var switchFailed by remember { mutableStateOf(false) }
    var instanceId by rememberSaveable { mutableStateOf(saved.instanceId) }
    var modelId by rememberSaveable { mutableStateOf(saved.model) }
    var effort by rememberSaveable { mutableStateOf(saved.effort.orEmpty()) }
    val draft = ModelSelection(instanceId, modelId, effort.ifEmpty { null })

    LaunchedEffect(Unit) {
        when (val result = session.switchableModels()) {
            is ModelListResult.Loaded -> instances = result.instances
            ModelListResult.Forbidden, ModelListResult.Failed -> failedToLoad = true
        }
        loaded = true
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(stringResource(R.string.mobile_done_e9b450d1))
                    }
                    Text(
                        text = stringResource(R.string.chat_failed_turn_switch_model),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (failedToLoad) {
                        IconNote(text = stringResource(R.string.chat_failed_turn_models_unavailable), icon = Icons.Filled.Warning)
                    } else {
                        ModelPickerFields(
                            instances = instances,
                            loaded = loaded,
                            saved = saved,
                            draft = draft,
                            onDraft = {
                                instanceId = it.instanceId
                                modelId = it.model
                                effort = it.effort.orEmpty()
                                switchFailed = false
                            },
                            taskBusy = current?.busy,
                            applying = applying,
                            applyLabel = stringResource(R.string.chat_failed_turn_use_model),
                            canChange = current != null,
                            unavailable = unavailable,
                            onApply = {
                                val target = current ?: return@ModelPickerFields
                                scope.launch {
                                    applying = true
                                    try {
                                        val updated = session.updateModel(draft, target, quietly = true)
                                        if (updated == null) {
                                            switchFailed = true
                                        } else {
                                            val label = ModelRules
                                                .modelChoices(
                                                    instances.firstOrNull { it.instanceId == updated.modelSelection.instanceId },
                                                    updated.modelSelection.model,
                                                )
                                                .firstOrNull { it.id == updated.modelSelection.model }
                                                ?.label ?: updated.modelSelection.model
                                            onSwitched(label)
                                        }
                                    } finally {
                                        applying = false
                                    }
                                }
                            },
                        )
                        if (switchFailed) {
                            IconNote(text = stringResource(R.string.chat_failed_turn_switch_failed), icon = Icons.Filled.Warning)
                        }
                    }
                }
            }
        }
    }
}
