package com.openmausbot.companion.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/** A decorative 3D icon. Its meaning always comes from the text next to it. */
@Composable
internal fun Icon3d(@DrawableRes res: Int, size: Dp, name: String, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size).testTag("icon3d.$name"),
    )
}

/** A 3D icon and the name its test tag carries. */
class Art3d(@param:DrawableRes val res: Int, val name: String)
