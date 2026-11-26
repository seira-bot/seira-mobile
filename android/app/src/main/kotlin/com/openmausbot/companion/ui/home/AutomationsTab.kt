package com.openmausbot.companion.ui.home

import androidx.compose.runtime.Composable
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.HomeTab
import com.openmausbot.companion.ui.TasksRoutinesScreen

/** The Automations tab: routines and their run receipts. */
@Composable
internal fun AutomationsTab(navigator: CompanionNavigator) {
    TasksRoutinesScreen(
        onBack = { navigator.selectTab(HomeTab.Chats) },
        onOpenChat = navigator::open,
        embedded = true,
    )
}
