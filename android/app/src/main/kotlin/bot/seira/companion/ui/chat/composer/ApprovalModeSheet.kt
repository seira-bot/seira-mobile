package bot.seira.companion.ui.chat.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import bot.seira.companion.R
import bot.seira.companion.core.ApprovalMode
import bot.seira.companion.core.ApprovalModeResult
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType
import kotlinx.coroutines.launch

internal fun modeRowTag(mode: ApprovalMode): String = "mode-row.${mode.wire}"

/**
 * The sheet behind the mode chip. A refusal leaves the old mode selected and
 * shows its reason under the list. Auto on the computer itself asks for a yes
 * first, then sends the change again with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApprovalModeSheet(
    current: ApprovalMode?,
    change: suspend (ApprovalMode, Boolean) -> ApprovalModeResult,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(current) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf<ApprovalMode?>(null) }
    var working by remember { mutableStateOf(false) }

    fun request(target: ApprovalMode, acknowledged: Boolean) {
        if (working) return
        working = true
        scope.launch {
            val outcome = try {
                ModeChange.request(shown, target, acknowledged, change)
            } finally {
                working = false
            }
            when (outcome) {
                is ModeChangeOutcome.Changed -> {
                    shown = outcome.mode
                    notice = null
                    onDismiss()
                }
                is ModeChangeOutcome.NeedsConfirmation -> confirm = outcome.mode
                is ModeChangeOutcome.Refused -> {
                    shown = outcome.kept
                    notice = outcome.reason
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("mode-sheet"),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.composer_mode_title),
                style = SeiraType.title3,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            for (mode in ModeChip.options(current)) {
                ModeRow(
                    mode = mode,
                    selected = mode == shown,
                    enabled = mode.phoneCanSet && !working,
                    onClick = { request(mode, false) },
                )
            }
            notice?.let { reason ->
                Text(
                    text = stringResource(reason),
                    style = SeiraType.caption,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("mode-notice"),
                )
            }
        }
    }

    confirm?.let { target ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.composer_mode_confirm_title)) },
            text = { Text(stringResource(R.string.composer_mode_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    request(target, true)
                }) { Text(stringResource(R.string.composer_mode_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.composer_mode_confirm_no)) }
            },
        )
    }
}

@Composable
private fun ModeRow(mode: ApprovalMode, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = Seira.colors
    val ink = if (enabled || selected) colors.ink else colors.ink3
    val selectedLabel = stringResource(R.string.composer_mode_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(modeRowTag(mode))
            .semantics {
                if (!enabled && !selected) disabled()
                if (selected) stateDescription = selectedLabel
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(ModeChip.nameRes(mode)), style = SeiraType.bodyMedium, color = ink)
            Text(stringResource(ModeChip.descriptionRes(mode)), style = SeiraType.caption, color = colors.ink3)
        }
        if (selected) {
            Icon(SeiraIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        }
    }
}
