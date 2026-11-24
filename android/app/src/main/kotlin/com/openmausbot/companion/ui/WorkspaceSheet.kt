package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.PhoneWorkspace
import com.openmausbot.companion.core.WorkspaceAction
import com.openmausbot.companion.core.WorkspaceNotice
import com.openmausbot.companion.core.WorkspacePicker
import com.openmausbot.companion.core.WorkspacePickerState
import com.openmausbot.companion.core.WorkspaceRow
import com.openmausbot.companion.core.WorkspaceStatus
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType

internal object WorkspaceSheetRules {
    private val HEX = Regex("^#?([0-9a-fA-F]{6})$")

    /** The color of a workspace as the desktop rail shows it; null for anything that is not six hex digits. */
    fun parseColor(value: String?): Long? =
        value?.trim()?.let { HEX.matchEntire(it) }?.groupValues?.get(1)?.toLong(16)?.let { 0xFF000000L or it }

    /** The name of a workspace, or null when it has none and the sheet words it itself. */
    fun name(workspace: PhoneWorkspace): String? = workspace.name?.trim()?.takeIf { it.isNotEmpty() }
}

@Composable
internal fun workspaceNoticeText(notice: WorkspaceNotice): String = stringResource(
    when (notice) {
        WorkspaceNotice.TURNED_OFF -> R.string.workspace_notice_turned_off
        WorkspaceNotice.UNAVAILABLE -> R.string.workspace_notice_unavailable
        WorkspaceNotice.GONE -> R.string.workspace_notice_gone
        WorkspaceNotice.FAILED -> R.string.workspace_notice_failed
        WorkspaceNotice.NEEDS_PAIRING -> R.string.workspace_notice_needs_pairing
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceSheet(
    state: WorkspacePickerState,
    onAction: (WorkspaceAction, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.sheet,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.testTag("workspace-sheet"),
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.workspace_sheet_title),
                    style = SeiraType.title2,
                    color = colors.ink,
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            state.notice?.let { notice ->
                item(key = "notice") {
                    Text(
                        text = workspaceNoticeText(notice),
                        style = SeiraType.callout,
                        color = colors.warningText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .testTag("workspace-notice"),
                    )
                }
            }
            items(state.rows, key = { "workspace-${it.workspace.id}" }) { row ->
                WorkspaceRowView(row = row, busy = state.busyId == row.workspace.id, locked = state.busyId != null, onAction = onAction)
            }
        }
    }
}

@Composable
private fun WorkspaceRowView(
    row: WorkspaceRow,
    busy: Boolean,
    locked: Boolean,
    onAction: (WorkspaceAction, String) -> Unit,
) {
    val colors = Seira.colors
    val workspace = row.workspace
    val title = WorkspaceSheetRules.name(workspace) ?: stringResource(R.string.workspace_unnamed)
    val subtitle = when {
        busy -> stringResource(R.string.workspace_opening)
        else -> workspaceStatusText(row)
    }
    val dot = WorkspaceSheetRules.parseColor(workspace.color)?.let { Color(it) } ?: colors.control
    // A row that offers nothing still answers a tap when it only needs the pairing again, so the notice explains why.
    val rowAction = row.tap ?: WorkspaceAction.OPEN.takeIf { row.status == WorkspaceStatus.PAIR_AGAIN }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workspace-row.${workspace.id}")
            .semantics { selected = workspace.current }
            .then(
                if (rowAction != null && !locked) {
                    Modifier.clickable(role = Role.Button) { onAction(rowAction, workspace.id) }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 60.dp)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).background(colors.control, CircleShape), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(16.dp).background(dot, CircleShape))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SeiraType.bodyMedium, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = SeiraType.caption, color = colors.ink3, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val button = row.button
        if (button != null) {
            TextButton(
                onClick = { onAction(button, workspace.id) },
                enabled = !locked,
                modifier = Modifier.testTag("workspace-button.${workspace.id}"),
            ) {
                Text(
                    stringResource(
                        if (button == WorkspaceAction.CANCEL_WAIT) R.string.workspace_action_cancel else R.string.workspace_action_open_now,
                    ),
                )
            }
        } else if (workspace.current) {
            Icon(SeiraIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun workspaceStatusText(row: WorkspaceRow): String? = when (row.status) {
    WorkspaceStatus.NONE -> null
    WorkspaceStatus.OPEN -> stringResource(R.string.workspace_status_open)
    WorkspaceStatus.UNAVAILABLE -> stringResource(R.string.workspace_status_unavailable)
    WorkspaceStatus.PAIR_AGAIN -> stringResource(R.string.workspace_status_pair_again)
    WorkspaceStatus.WAITING -> WorkspacePicker.waitingFor(row.workspace)
        ?.let { stringResource(R.string.workspace_status_waiting, it) }
        ?: stringResource(R.string.workspace_status_waiting_generic)
}
