package com.openmausbot.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.update.UpdateGate
import com.openmausbot.companion.update.UpdateUiState

@Composable
fun UpdateBannerHost() {
    val environment = LocalCompanion.current
    val controller = environment.appUpdates ?: return
    val state by controller.state.collectAsState()
    val call by environment.liveCalls.state.collectAsState()
    val pairingRequested by environment.session.pairingRequested.collectAsState()
    val invite by environment.session.pairingInvite.collectAsState()
    val companion by environment.session.state.collectAsState()
    val waiting = UpdateGate.busy(call, pairingRequested, invite != null, companion)
    when (val current = state) {
        is UpdateUiState.Downloading -> UpdateBanner(
            text = stringResource(R.string.update_banner_downloading) +
                (current.progress?.let { " ${(it * 100).toInt()}%" } ?: ""),
            action = null,
            enabled = false,
            onAction = {},
        )
        UpdateUiState.ReadyToRestart -> UpdateBanner(
            text = stringResource(R.string.update_banner_ready),
            action = stringResource(R.string.update_banner_restart),
            enabled = !waiting,
            onAction = { controller.restart() },
        )
        UpdateUiState.None, UpdateUiState.Required -> Unit
    }
}

@Composable
fun UpdateBanner(text: String, action: String?, enabled: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Seira.colors.tint)
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Seira.colors.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction, enabled = enabled) {
                Text(action, color = if (enabled) Seira.colors.accentText else Seira.colors.ink3)
            }
        }
    }
}
