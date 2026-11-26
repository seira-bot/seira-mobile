package com.openmausbot.companion.ui.team

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
internal fun TeamMapScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Seira.colors.app)
            .testTag("team-map-screen"),
    ) {
        PageTopBar(title = stringResource(R.string.shell_drawer_team_map), onBack = onBack)
    }
}
