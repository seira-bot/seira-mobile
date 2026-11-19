package com.openmausbot.companion.ui

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.ui.theme.InkPillButton

/** What the home list says when it has no chat to list. */
internal enum class RosterEmpty { CHATS, NOT_CONNECTED, OFFLINE }

/** A lost route to the computer is the phone's fault only when the phone has no internet. */
internal fun rosterEmpty(status: Session.Status, phoneOnline: Boolean): RosterEmpty = when {
    status !is Session.Status.Offline -> RosterEmpty.CHATS
    phoneOnline -> RosterEmpty.NOT_CONNECTED
    else -> RosterEmpty.OFFLINE
}

/** Tests set this to skip the system network service. */
internal val LocalPhoneOnline = compositionLocalOf<Boolean?> { null }

internal const val EMPTY_PRIMARY_TAG = "empty-primary"
internal const val EMPTY_SECONDARY_TAG = "empty-secondary"

@Composable
internal fun rememberPhoneOnline(): Boolean {
    val override = LocalPhoneOnline.current
    val inspecting = LocalInspectionMode.current
    val context = LocalContext.current
    val manager: ConnectivityManager? = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    val tracked = override == null && !inspecting && manager != null
    var online by remember(manager) { mutableStateOf(manager?.hasInternet(manager.activeNetwork) ?: true) }
    DisposableEffect(manager, tracked) {
        if (!tracked || manager == null) return@DisposableEffect onDispose {}
        var current: Network? = manager.activeNetwork
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                current = network
                online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                if (network == current) online = false
            }
        }
        try {
            manager.registerDefaultNetworkCallback(callback)
        } catch (_: SecurityException) {
            online = true
        }
        onDispose {
            try {
                manager.unregisterNetworkCallback(callback)
            } catch (_: IllegalArgumentException) {
            }
        }
    }
    return override ?: online
}

private fun ConnectivityManager.hasInternet(network: Network?): Boolean =
    network != null && getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

@Composable
private fun EmptyPill(text: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    InkPillButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(tag),
    )
}

/**
 * The empty home list. [onRefresh] is the pull-to-refresh action; both
 * "Try again" buttons wait while [refreshing] so two refreshes never overlap.
 */
@Composable
internal fun RosterEmptyState(
    empty: RosterEmpty,
    refreshing: Boolean,
    onNewChat: () -> Unit,
    onPairComputer: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tryAgain = stringResource(R.string.mobile_try_again_042c862e)
    when (empty) {
        RosterEmpty.CHATS -> EmptyState(
            title = stringResource(R.string.home_empty_chats_title),
            description = stringResource(R.string.home_empty_chats_body),
            art = Art3d(R.drawable.icon3d_empty_chats, "empty_chats"),
            modifier = modifier,
        ) {
            EmptyPill(stringResource(R.string.home_new_chat), EMPTY_PRIMARY_TAG, onClick = onNewChat)
        }

        RosterEmpty.NOT_CONNECTED -> EmptyState(
            title = stringResource(R.string.home_empty_not_connected_title),
            description = stringResource(R.string.home_empty_not_connected_body),
            art = Art3d(R.drawable.icon3d_not_connected, "not_connected"),
            modifier = modifier,
        ) {
            EmptyPill(stringResource(R.string.home_empty_pair_computer), EMPTY_PRIMARY_TAG, onClick = onPairComputer)
            TextButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.testTag(EMPTY_SECONDARY_TAG),
            ) { Text(tryAgain) }
        }

        RosterEmpty.OFFLINE -> EmptyState(
            title = stringResource(R.string.home_empty_offline_title),
            description = stringResource(R.string.home_empty_offline_body),
            art = Art3d(R.drawable.icon3d_offline, "offline"),
            modifier = modifier,
        ) {
            EmptyPill(tryAgain, EMPTY_PRIMARY_TAG, enabled = !refreshing, onClick = onRefresh)
        }
    }
}

/** The empty Updates sheet. */
@Composable
internal fun UpdatesEmptyState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.home_empty_all_done_title),
        description = stringResource(R.string.home_empty_all_done_body),
        art = Art3d(R.drawable.icon3d_all_done, "all_done"),
        modifier = modifier,
    ) {
        EmptyPill(stringResource(R.string.home_empty_back_to_chats), EMPTY_PRIMARY_TAG, onClick = onBack)
    }
}
