package com.openmausbot.companion.ui.search

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
internal fun SearchScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Seira.colors.app)
            .testTag("search-screen"),
    ) {
        PageTopBar(title = stringResource(R.string.mobile_search_bce06414), onBack = onBack)
    }
}
