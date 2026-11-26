package com.openmausbot.companion.ui.home

import androidx.compose.runtime.Composable
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.RosterScreen

/** The Chats tab: the roster without its own header and bar, limited to [sectionFilter] when set. */
@Composable
internal fun ChatsTab(navigator: CompanionNavigator, sectionFilter: String?) {
    RosterScreen(navigator = navigator, embedded = true, sectionFilter = sectionFilter)
}
