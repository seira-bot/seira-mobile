package com.openmausbot.companion.ui.computers

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
internal fun ComputersScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Seira.colors.app)
            .testTag("computers-screen"),
    ) {
        PageTopBar(title = stringResource(R.string.home_computers_title), onBack = onBack)
    }
}
