package com.openmausbot.companion.ui.home

import androidx.compose.runtime.Composable
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.home.chats.ChatsScreen

/** The Chats tab, limited to [sectionFilter] when set. */
@Composable
internal fun ChatsTab(navigator: CompanionNavigator, sectionFilter: String?) {
    ChatsScreen(navigator = navigator, sectionFilter = sectionFilter)
}
