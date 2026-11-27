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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.DepartmentEdit
import com.openmausbot.companion.core.DepartmentPlacement
import com.openmausbot.companion.core.DepartmentRules
import com.openmausbot.companion.core.HomeLayoutRules
import com.openmausbot.companion.core.PlacementOutcome
import com.openmausbot.companion.ui.CharacterMood
import com.openmausbot.companion.ui.HapticCue
import com.openmausbot.companion.ui.play
import com.openmausbot.companion.ui.rememberHaptics
import com.openmausbot.companion.ui.theme.IdentityAvatar
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import kotlinx.coroutines.launch

internal val StringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() },
)

private val SheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)

/** What a department's ⋯ button offers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DepartmentMenuSheet(
    department: String,
    onAddBots: () -> Unit,
    onNewBot: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = SheetShape,
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("department-menu"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = department,
                style = SeiraType.title2,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            MenuRow(stringResource(R.string.home2_menu_add_bots), "department-menu-add-bots", onAddBots)
            MenuRow(stringResource(R.string.home2_menu_new_bot), "department-menu-new-bot", onNewBot)
        }
    }
}

@Composable
private fun MenuRow(label: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, fontSize = 16.sp, color = Seira.colors.ink)
    }
}

/**
 * The bots of the fleet with a check mark on the ones in [department]. Save sends
 * only the person's own changes through [onSave], which answers what the
 * computer took. A change made on the computer meanwhile stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddBotsSheet(
    department: String,
    bots: List<Bot>,
    onSave: suspend (DepartmentPlacement) -> PlacementOutcome,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var addedIds by rememberSaveable(department, stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var removedIds by rememberSaveable(department, stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
    var saving by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<PlacementOutcome?>(null) }
    val edit = DepartmentEdit(addedIds, removedIds)
    val members = remember(bots, department) { DepartmentRules.members(bots, department) }
    val placement = remember(bots, department, addedIds, removedIds) { DepartmentRules.placement(bots, department, edit) }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = SheetShape,
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("add-bots-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.home2_add_bots_title, department),
                style = SeiraType.title2,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .semantics { heading() },
            )
            if (bots.isEmpty()) {
                Text(
                    text = stringResource(R.string.home2_add_bots_nothing),
                    fontSize = 14.sp,
                    color = colors.ink3,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(bots, key = { it.id }) { bot ->
                        BotCheckRow(
                            bot = bot,
                            department = department,
                            checked = DepartmentRules.isChecked(members, edit, bot.id),
                            enabled = !saving,
                            onToggle = {
                                haptics.play(HapticCue.SELECT)
                                outcome = null
                                val next = DepartmentRules.toggle(members, edit, bot.id)
                                addedIds = next.added
                                removedIds = next.removed
                            },
                        )
                    }
                }
            }
            if (outcome != null) {
                Text(
                    text = stringResource(
                        if (outcome == PlacementOutcome.PARTIAL) R.string.home2_add_bots_partial else R.string.home2_add_bots_failed,
                    ),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.warningText,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .testTag("add-bots-error"),
                )
            }
            SheetPrimaryButton(
                label = stringResource(R.string.home2_add_bots_save),
                enabled = DepartmentRules.canSave(placement, saving),
                busy = saving,
                busyLabel = stringResource(R.string.home2_add_bots_saving),
                tag = "add-bots-save",
                onClick = {
                    if (DepartmentRules.canSave(placement, saving)) {
                        saving = true
                        outcome = null
                        scope.launch {
                            val result = try {
                                onSave(placement)
                            } finally {
                                saving = false
                            }
                            if (result == PlacementOutcome.SAVED) {
                                haptics.play(HapticCue.SUCCESS)
                                onDismiss()
                            } else {
                                outcome = result
                            }
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun BotCheckRow(
    bot: Bot,
    department: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val colors = Seira.colors
    val elsewhere = HomeLayoutRules.departmentOf(bot)?.takeIf { it != department }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add-bots-row.${bot.id}")
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdentityAvatar(bot = bot, size = 40.dp, state = CharacterMood.IDLE, animated = false)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bot.name,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (elsewhere != null) {
                Text(
                    text = stringResource(R.string.home2_bot_in_department, elsewhere),
                    fontSize = 12.5.sp,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(if (checked) colors.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                .border(1.5.dp, if (checked) colors.primary else colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(SeiraIcons.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Asks for the name of a new department; [onAdd] answers whether the computer took it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddDepartmentSheet(
    existing: List<String>,
    onAdd: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<DepartmentRules.NameProblem?>(null) }
    var failed by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!adding) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = SheetShape,
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("add-department-sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.home2_department_title),
                style = SeiraType.title2,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            SheetTextField(
                label = stringResource(R.string.home2_department_name),
                placeholder = stringResource(R.string.home2_department_placeholder),
                value = name,
                onValueChange = {
                    name = DepartmentRules.clampName(it)
                    problem = null
                    failed = false
                },
                tag = "add-department-name",
                imeAction = ImeAction.Done,
            )
            val message = when {
                problem == DepartmentRules.NameProblem.EXISTS -> stringResource(R.string.home2_department_exists)
                problem == DepartmentRules.NameProblem.EMPTY -> stringResource(R.string.home2_department_needed)
                failed -> stringResource(R.string.home2_department_failed)
                else -> null
            }
            if (message != null) {
                Text(
                    text = message,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.warningText,
                    modifier = Modifier.testTag("add-department-error"),
                )
            }
            SheetPrimaryButton(
                label = stringResource(R.string.home2_department_add),
                enabled = !adding,
                busy = adding,
                busyLabel = stringResource(R.string.home2_department_adding),
                tag = "add-department-save",
                onClick = {
                    val found = DepartmentRules.nameProblem(name, existing)
                    problem = found
                    if (found == null && !adding) {
                        adding = true
                        failed = false
                        scope.launch {
                            val added = try {
                                onAdd(name.trim())
                            } finally {
                                adding = false
                            }
                            if (added) {
                                haptics.play(HapticCue.SUCCESS)
                                onDismiss()
                            } else {
                                failed = true
                            }
                        }
                    }
                },
            )
        }
    }
}
