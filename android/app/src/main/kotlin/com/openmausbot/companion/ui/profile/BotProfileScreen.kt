package com.openmausbot.companion.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.home.PageTopBar
import com.openmausbot.companion.ui.theme.Seira

@Composable
internal fun BotProfileScreen(botId: String, tab: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Seira.colors.app)
            .testTag("bot-profile-screen"),
    ) {
        PageTopBar(title = stringResource(R.string.shell_bot_profile_title), onBack = onBack)
    }
}
