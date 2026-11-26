package com.openmausbot.companion.ui.home.automations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.core.RoutineRun
import com.openmausbot.companion.ui.theme.Seira

/** What a routine can do, opened from its row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutineActionSheet(
    routine: Routine,
    runs: List<RoutineRun>,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onOpenResults: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Seira.colors
    val actions = remember(routine, runs) { AutomationsRules.menu(routine, runs, System.currentTimeMillis().toDouble()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("routine-sheet"),
        ) {
            Text(
                text = routine.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            actions.forEach { action ->
                val label = when (action) {
                    RoutineMenuAction.RUN_NOW -> stringResource(R.string.mobile_run_now_2af00e23)
                    RoutineMenuAction.EDIT -> stringResource(R.string.mobile_edit_5301648d)
                    RoutineMenuAction.TOGGLE ->
                        if (routine.enabled) stringResource(R.string.mobile_pause_781961bc)
                        else stringResource(R.string.mobile_resume_b3bd0b5a)
                    RoutineMenuAction.OPEN_RESULTS -> stringResource(R.string.automations_open_results)
                    RoutineMenuAction.DELETE -> stringResource(R.string.mobile_delete_f6fdbe48)
                }
                val run = {
                    onDismiss()
                    when (action) {
                        RoutineMenuAction.RUN_NOW -> onRunNow()
                        RoutineMenuAction.EDIT -> onEdit()
                        RoutineMenuAction.TOGGLE -> onToggle()
                        RoutineMenuAction.OPEN_RESULTS -> onOpenResults()
                        RoutineMenuAction.DELETE -> onDelete()
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button, onClick = run)
                        .padding(horizontal = 24.dp)
                        .testTag("routine-action-${action.name}"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        color = if (action == RoutineMenuAction.DELETE) colors.danger else colors.ink,
                    )
                }
            }
        }
    }
}
