package com.openmausbot.companion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.RoutineCalendarScreen
import com.openmausbot.companion.ui.RoutineEditorSheet
import com.openmausbot.companion.ui.RoutineEditorTarget
import com.openmausbot.companion.ui.TouchTarget
import com.openmausbot.companion.ui.home.automations.AutomationsSegment
import com.openmausbot.companion.ui.home.automations.RoutineListPane
import com.openmausbot.companion.ui.home.inbox.TitleSegment
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons

/** The Automations tab: routines as a list or on a calendar. */
@Composable
internal fun AutomationsTab(navigator: CompanionNavigator) {
    val colors = Seira.colors
    var segmentIndex by rememberSaveable { mutableIntStateOf(AutomationsSegment.LIST.ordinal) }
    val segment = AutomationsSegment.entries[segmentIndex]
    var editor by rememberSaveable(stateSaver = RoutineEditorTarget.Saver) {
        mutableStateOf<RoutineEditorTarget?>(null)
    }
    var reloadSignal by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("automations-tab"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TitleSegment(
                label = stringResource(R.string.automations_list),
                selected = segment == AutomationsSegment.LIST,
                onClick = { segmentIndex = AutomationsSegment.LIST.ordinal },
                tag = "automations-segment-list",
            )
            TitleSegment(
                label = stringResource(R.string.automations_calendar),
                selected = segment == AutomationsSegment.CALENDAR,
                onClick = { segmentIndex = AutomationsSegment.CALENDAR.ordinal },
                tag = "automations-segment-calendar",
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TouchTarget(
                    onClick = { editor = RoutineEditorTarget.new() },
                    contentDescription = stringResource(R.string.mobile_new_routine_32809dc6),
                    modifier = Modifier.testTag("automations-new"),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.s1, CircleShape)
                            .border(1.dp, colors.hairline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SeiraIcons.Plus, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        when (segment) {
            AutomationsSegment.LIST -> RoutineListPane(
                onOpenChat = navigator::open,
                onEdit = { editor = RoutineEditorTarget.edit(it) },
                reloadSignal = reloadSignal,
                modifier = Modifier.weight(1f),
            )
            AutomationsSegment.CALENDAR -> Box(modifier = Modifier.weight(1f)) {
                RoutineCalendarScreen(
                    onBack = {},
                    onOpenChat = navigator::open,
                    embedded = true,
                    reloadSignal = reloadSignal,
                )
            }
        }
    }

    editor?.let { target ->
        key(target.stateKey) {
            RoutineEditorSheet(
                target = target,
                onSaved = { reloadSignal++ },
                onDismiss = { editor = null },
            )
        }
    }
}
