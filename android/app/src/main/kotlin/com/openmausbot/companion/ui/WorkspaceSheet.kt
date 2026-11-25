package com.openmausbot.companion.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.PhoneWorkspace
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspaceSheet(
    state: WorkspacePickerState,
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
            items(state.rows, key = { "workspace-${it.workspace.id}" }) { row ->
                WorkspaceRowView(row = row)
            }
        }
    }
}

/** A row only shows a workspace. It has no tap and no button: the picker is read-only. */
@Composable
private fun WorkspaceRowView(row: WorkspaceRow) {
    val colors = Seira.colors
    val workspace = row.workspace
    val title = WorkspaceSheetRules.name(workspace) ?: stringResource(R.string.workspace_unnamed)
    val subtitle = workspaceStatusText(row)
    val dot = WorkspaceSheetRules.parseColor(workspace.color)?.let { Color(it) } ?: colors.control
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workspace-row.${workspace.id}")
            .semantics { selected = workspace.current }
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
        if (workspace.current) {
            Icon(SeiraIcons.Check, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun workspaceStatusText(row: WorkspaceRow): String? = when (row.status) {
    WorkspaceStatus.NONE -> null
    WorkspaceStatus.OPEN -> stringResource(R.string.workspace_status_open)
    WorkspaceStatus.UNAVAILABLE -> stringResource(R.string.workspace_status_unavailable)
    WorkspaceStatus.WAITING -> WorkspacePicker.waitingFor(row.workspace)
        ?.let { stringResource(R.string.workspace_status_waiting, it) }
        ?: stringResource(R.string.workspace_status_waiting_generic)
}
