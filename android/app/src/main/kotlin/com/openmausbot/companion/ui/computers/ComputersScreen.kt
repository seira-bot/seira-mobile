package com.openmausbot.companion.ui.computers

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.ui.CopyIcon
import com.openmausbot.companion.ui.DesignRow
import com.openmausbot.companion.ui.LocalCompanion
import com.openmausbot.companion.ui.SettingsPolicy
import com.openmausbot.companion.ui.TactileAction
import com.openmausbot.companion.ui.TouchTarget
import com.openmausbot.companion.ui.computerStatusPhrase
import com.openmausbot.companion.ui.play
import com.openmausbot.companion.ui.rememberHaptics
import com.openmausbot.companion.ui.settings.Chevron
import com.openmausbot.companion.ui.settings.ConfirmSheet
import com.openmausbot.companion.ui.settings.GroupCard
import com.openmausbot.companion.ui.settings.GroupNote
import com.openmausbot.companion.ui.settings.GroupTitle
import com.openmausbot.companion.ui.settings.PageActionPill
import com.openmausbot.companion.ui.settings.RowDivider
import com.openmausbot.companion.ui.shortenedAddress
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ADDRESS_CLIP_LABEL = "Seira computer address"
private const val COPIED_LABEL_MILLIS = 2_000L

/**
 * Every computer this phone is paired with, the connection to the current
 * one, and the screens of the bots that work on a cloud desktop. The links
 * to other pages are optional: a host that cannot open them leaves them out.
 */
@Composable
internal fun ComputersScreen(
    onBack: () -> Unit,
    onOpenAiUsage: (() -> Unit)? = null,
    onOpenConnectedApps: (() -> Unit)? = null,
    onOpenBotScreen: ((String) -> Unit)? = null,
) {
    val environment = LocalCompanion.current
    val session = environment.session
    val connection by session.connection.collectAsState()
    val connections by session.connections.collectAsState()
    val status by session.status.collectAsState()
    val state by session.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val haptics = rememberHaptics()
    val colors = Seira.colors

    var refreshing by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var addressCopied by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<Connection?>(null) }
    val invalidAddress = stringResource(R.string.mobile_settings_invalid_address)

    val entries = remember(connections, connection?.id) { ComputersRules.entries(connections, connection?.id) }
    val botScreens = remember(state.bots) { ComputersRules.botScreens(state.bots) }
    val bound = connection

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.app)
            .testTag("computers-screen"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
                .testTag("page-top-bar"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TouchTarget(
                onClick = onBack,
                contentDescription = stringResource(R.string.mobile_a11y_close),
                modifier = Modifier.testTag("page-close"),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.s1, CircleShape)
                        .border(1.dp, colors.hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SeiraIcons.X, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text = stringResource(R.string.home_computers_title),
                style = SeiraType.title3,
                color = colors.ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            PageActionPill(
                text = stringResource(if (refreshing) R.string.mobile_refreshing_96141178 else R.string.mobile_refresh_56e3badc),
                enabled = !refreshing,
                onClick = {
                    scope.launch {
                        refreshing = true
                        session.refresh()
                        refreshing = false
                    }
                },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            GroupTitle(stringResource(R.string.computers_paired_title))
            GroupCard {
                entries.forEach { entry ->
                    val computer = entry.connection
                    val subtitle = listOfNotNull(
                        if (entry.current) computerStatusPhrase(status) else stringResource(R.string.computers_tap_to_switch),
                        entry.distinguishingAddress,
                    ).joinToString(" · ")
                    DesignRow(
                        title = computer.name,
                        icon = SeiraIcons.Laptop,
                        minHeight = 64.dp,
                        subtitle = subtitle,
                        subtitleDot = if (entry.current) {
                            when (status) {
                                Session.Status.Live -> colors.success
                                is Session.Status.Offline -> colors.danger
                                else -> null
                            }
                        } else {
                            null
                        },
                        onClick = if (entry.current) {
                            null
                        } else {
                            {
                                haptics.play(TactileAction.SWITCH_COMPUTER)
                                session.switchComputer(computer.id)
                            }
                        },
                        trailing = {
                            if (entry.current) {
                                Icon(SeiraIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            } else {
                                TouchTarget(
                                    onClick = { pendingRemoval = computer },
                                    contentDescription = stringResource(R.string.mobile_settings_remove_computer, computer.name),
                                ) {
                                    Icon(SeiraIcons.X, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                    )
                    RowDivider(50.dp)
                }
                DesignRow(
                    title = stringResource(R.string.settings_pair_another),
                    icon = SeiraIcons.Plus,
                    onClick = {
                        haptics.play(TactileAction.CONNECT_ANOTHER_COMPUTER)
                        session.beginPairing()
                    },
                    trailing = { Chevron() },
                )
            }

            if (bound != null) {
                GroupTitle(stringResource(R.string.settings_connection_details))
                GroupCard {
                    val address = SettingsPolicy.addressText(bound)
                    DesignRow(
                        title = stringResource(R.string.mobile_address_d70f93df),
                        subtitle = shortenedAddress(address),
                        icon = SeiraIcons.Shield,
                        trailing = {
                            TouchTarget(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(ADDRESS_CLIP_LABEL, address)))
                                        addressCopied = true
                                        delay(COPIED_LABEL_MILLIS)
                                        addressCopied = false
                                    }
                                },
                                contentDescription = stringResource(
                                    if (addressCopied) R.string.mobile_copied_8e3df45a else R.string.mobile_copy_af74f7c5,
                                ),
                            ) {
                                Icon(
                                    if (addressCopied) SeiraIcons.Check else CopyIcon,
                                    contentDescription = null,
                                    tint = colors.ink2,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                    RowDivider(50.dp)
                    if (editingAddress) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.mobile_settings_edit_address_message),
                                style = SeiraType.callout,
                                color = colors.ink2,
                            )
                            OutlinedTextField(
                                value = addressText,
                                onValueChange = {
                                    addressText = it
                                    addressError = null
                                },
                                placeholder = { Text(stringResource(R.string.mobile_https_mac_example_or_192_168_1_42__e277eb2d)) },
                                singleLine = true,
                                isError = addressError != null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth().testTag("computers-address-field"),
                            )
                            addressError?.let { Text(it, style = SeiraType.callout, color = colors.danger) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PageActionPill(
                                    text = stringResource(R.string.computers_save_address),
                                    onClick = {
                                        if (session.updateAddress(addressText)) {
                                            editingAddress = false
                                        } else {
                                            addressError = invalidAddress
                                        }
                                    },
                                )
                                PageActionPill(
                                    text = stringResource(R.string.mobile_cancel_77dfd213),
                                    onClick = { editingAddress = false },
                                )
                            }
                        }
                    } else {
                        DesignRow(
                            title = stringResource(R.string.mobile_edit_address_31fe67f4),
                            icon = SeiraIcons.PenSquare,
                            onClick = {
                                addressText = address
                                addressError = null
                                editingAddress = true
                            },
                            trailing = { Chevron() },
                        )
                    }
                    RowDivider(50.dp)
                    DesignRow(
                        title = stringResource(R.string.mobile_settings_try_reconnecting),
                        icon = SeiraIcons.Monitor,
                        enabled = !reconnecting,
                        onClick = {
                            scope.launch {
                                reconnecting = true
                                session.refresh()
                                reconnecting = false
                            }
                        },
                        trailing = {
                            if (reconnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.ink2)
                            }
                        },
                    )
                }
            }

            if (botScreens.isNotEmpty()) {
                GroupTitle(stringResource(R.string.computers_bot_screens_title))
                GroupCard {
                    botScreens.forEachIndexed { index, entry ->
                        if (index > 0) RowDivider(50.dp)
                        val stateText = stringResource(
                            when (entry.state) {
                                BotScreenState.WORKING -> R.string.computers_bot_working
                                BotScreenState.WAITING -> R.string.computers_bot_waiting
                                BotScreenState.IDLE -> R.string.computers_bot_idle
                            },
                        )
                        DesignRow(
                            title = entry.bot.name,
                            icon = SeiraIcons.Monitor,
                            minHeight = 64.dp,
                            subtitle = stringResource(R.string.computers_bot_subtitle, stringResource(R.string.computers_bot_cloud_desktop), stateText),
                            onClick = onOpenBotScreen?.let { open -> { open(entry.bot.id) } },
                            trailing = if (onOpenBotScreen != null) {
                                { Chevron() }
                            } else {
                                null
                            },
                        )
                    }
                }
                GroupNote(stringResource(R.string.computers_bot_screens_note))
            }

            if (onOpenAiUsage != null || onOpenConnectedApps != null) {
                Box(modifier = Modifier.padding(top = 24.dp))
                GroupCard {
                    if (onOpenAiUsage != null) {
                        DesignRow(
                            title = stringResource(R.string.ai_usage_title),
                            icon = SeiraIcons.Gauge,
                            onClick = onOpenAiUsage,
                            trailing = { Chevron() },
                        )
                    }
                    if (onOpenAiUsage != null && onOpenConnectedApps != null) RowDivider(50.dp)
                    if (onOpenConnectedApps != null) {
                        DesignRow(
                            title = stringResource(R.string.settings_connected_apps),
                            icon = SeiraIcons.Plug,
                            onClick = onOpenConnectedApps,
                            trailing = { Chevron() },
                        )
                    }
                }
            }
            Box(modifier = Modifier.padding(bottom = 32.dp))
        }
    }

    pendingRemoval?.let { computer ->
        ConfirmSheet(
            title = stringResource(R.string.mobile_remove_computer_name_272e4835, computer.name),
            note = stringResource(R.string.mobile_this_removes_the_saved_connection__54db6818),
            confirmLabel = stringResource(R.string.mobile_remove_e963907d),
            onConfirm = {
                pendingRemoval = null
                session.forgetConnection(computer.id)
            },
            onDismiss = { pendingRemoval = null },
            tag = "computers-remove-sheet",
        )
    }
}
