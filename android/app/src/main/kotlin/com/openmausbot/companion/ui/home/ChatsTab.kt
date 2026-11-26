package com.openmausbot.companion.ui.home

import androidx.compose.runtime.Composable
import com.openmausbot.companion.BuildConfig
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.home.chats.ChatsScreen

/** The Chats tab, limited to [sectionFilter] when set. */
@Composable
internal fun ChatsTab(
    navigator: CompanionNavigator,
    sectionFilter: String?,
    onNewChat: () -> Unit,
    callsEnabled: Boolean = BuildConfig.LIVE_CALLS,
) {
    ChatsScreen(navigator = navigator, sectionFilter = sectionFilter, onNewChat = onNewChat, callsEnabled = callsEnabled)
}
