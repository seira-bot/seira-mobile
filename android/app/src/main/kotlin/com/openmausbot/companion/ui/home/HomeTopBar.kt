package com.openmausbot.companion.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.character.CarvedAvatar
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.ui.TouchTarget
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons

private val SIDE_BUTTON = 46.dp
private val FACE_BUTTON = 60.dp
private val FACE_INSET = 4.dp
private val WAITING_DOT = 14.dp

/**
 * The bar above every home tab: the menu on the left, Seira in the middle and the
 * profile on the right. A dot on Seira says that a decision waits.
 */
@Composable
internal fun HomeTopBar(
    waiting: Int,
    onMenu: () -> Unit,
    onUpdates: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
            .testTag("home-top-bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundBarButton(
            icon = SeiraIcons.Menu,
            contentDescription = stringResource(R.string.shell_open_menu),
            onClick = onMenu,
            modifier = Modifier.testTag("home-menu"),
        )
        SeiraButton(waiting = waiting, onClick = onUpdates)
        RoundBarButton(
            icon = SeiraIcons.User,
            contentDescription = stringResource(R.string.mobile_settings_c7f73bb5),
            onClick = onProfile,
            modifier = Modifier.testTag("home-profile"),
        )
    }
}

@Composable
private fun RoundBarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    TouchTarget(onClick = onClick, contentDescription = contentDescription, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(SIDE_BUTTON)
                .background(colors.s1, CircleShape)
                .border(1.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SeiraButton(waiting: Int, onClick: () -> Unit) {
    val colors = Seira.colors
    val state = if (waiting > 0) {
        pluralStringResource(R.plurals.shell_updates_waiting, waiting, waiting)
    } else {
        null
    }
    TouchTarget(
        onClick = onClick,
        size = FACE_BUTTON,
        contentDescription = stringResource(R.string.mobile_open_updates_2c80d633),
        modifier = Modifier
            .testTag("home-updates")
            .semantics { if (state != null) stateDescription = state },
    ) {
        Box(
            modifier = Modifier
                .size(FACE_BUTTON)
                .background(colors.s1, CircleShape)
                .border(1.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CarvedAvatar(look = SeiraLook.SEIRA, size = FACE_BUTTON - FACE_INSET * 2)
            if (waiting > 0) {
                WaitingDot(modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-1).dp, y = (-1).dp))
            }
        }
    }
}

@Composable
private fun WaitingDot(modifier: Modifier = Modifier, size: Dp = WAITING_DOT) {
    val colors = Seira.colors
    Box(
        modifier = modifier
            .size(size)
            .background(colors.warning, CircleShape)
            .border(2.dp, colors.s1, CircleShape)
            .testTag("home-waiting-dot"),
    )
}
