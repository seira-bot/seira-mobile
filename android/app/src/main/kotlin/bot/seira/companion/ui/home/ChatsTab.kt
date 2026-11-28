package bot.seira.companion.ui.home

import androidx.compose.runtime.Composable
import bot.seira.companion.BuildConfig
import bot.seira.companion.ui.CompanionNavigator
import bot.seira.companion.ui.home.chats.ChatsScreen

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
