package com.openmausbot.companion.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.ui.CompanionNavigator
import com.openmausbot.companion.ui.HomeTab
import com.openmausbot.companion.ui.UpdatesList

/** The Inbox tab: the chats that need you, are working, or have news. */
@Composable
internal fun InboxTab(navigator: CompanionNavigator) {
    UpdatesList(
        onOpen = navigator::open,
        onEmptyBack = { navigator.selectTab(HomeTab.Chats) },
        modifier = Modifier
            .fillMaxSize()
            .testTag("inbox-tab"),
        showHeader = false,
        contentPadding = PaddingValues(bottom = 24.dp),
    )
}
