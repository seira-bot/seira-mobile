package bot.seira.companion.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import bot.seira.companion.BuildConfig
import bot.seira.companion.R
import bot.seira.companion.character.SeiraCharacter
import bot.seira.companion.core.ActivityDetail
import bot.seira.companion.core.AiUsageRules
import bot.seira.companion.core.RosterDensity
import bot.seira.companion.core.SeiraLook
import bot.seira.companion.core.Session
import bot.seira.companion.ui.home.PageTopBar
import bot.seira.companion.ui.settings.ChatRowKind
import bot.seira.companion.ui.settings.Chevron
import bot.seira.companion.ui.settings.ChipChoiceRow
import bot.seira.companion.ui.settings.ChoiceSheet
import bot.seira.companion.ui.settings.ConfirmSheet
import bot.seira.companion.ui.settings.GroupCard
import bot.seira.companion.ui.settings.GroupNote
import bot.seira.companion.ui.settings.GroupTitle
import bot.seira.companion.ui.settings.RowDivider
import bot.seira.companion.ui.settings.SettingsGroup
import bot.seira.companion.ui.settings.SettingsLayout
import bot.seira.companion.ui.settings.ValueWithChevron
import bot.seira.companion.ui.theme.Seira
import bot.seira.companion.ui.theme.SeiraIcons
import bot.seira.companion.ui.theme.SeiraType
import bot.seira.companion.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Phone settings. Almost everything else lives on the computer, because losing
 * the phone must not mean losing the ability to lock it out. The screen is also
 * the one the unpaired home reaches, so the actions that need a pairing are
 * simply absent without one.
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRoutines: (() -> Unit)? = null,
    onOpenConnectedApps: (() -> Unit)? = null,
    onOpenAiUsage: (() -> Unit)? = null,
    onConnect: (() -> Unit)? = null,
    accounts: AccountInfoSource? = null,
    callsEnabled: Boolean = BuildConfig.LIVE_CALLS,
    alwaysOnAvailable: Boolean = BuildConfig.ALWAYS_ON,
    onOpenComputers: (() -> Unit)? = null,
) {
    val environment = LocalCompanion.current
    val session = environment.session
    val connection by session.connection.collectAsState()
    val connections by session.connections.collectAsState()
    val status by session.status.collectAsState()
    val notifications by environment.notifications.access.collectAsState()
    val activityDetail by environment.chatPreferences.activityDetail.collectAsState()
    val themeMode by environment.chatPreferences.themeMode.collectAsState()
    val rosterDensity by environment.chatPreferences.rosterDensity.collectAsState()
    val liveCaptions by environment.chatPreferences.liveCaptions.collectAsState()
    val advancedMode by environment.chatPreferences.advancedMode.collectAsState()
    val alwaysOnEnabled by environment.alwaysOnEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = Seira.colors
    val bound = connection

    var confirmingSignOut by remember { mutableStateOf(false) }
    var choosingActivity by remember { mutableStateOf(false) }
    var editingQuickReplies by remember { mutableStateOf(false) }
    var showingLiveSettings by rememberSaveable { mutableStateOf(false) }
    var liveVoice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(connection?.id, status, showingLiveSettings) {
        if (connection == null || status != Session.Status.Live || showingLiveSettings) return@LaunchedEffect
        liveVoice = session.liveSettings()?.let { LiveCallRules.voiceLabel(it.voice) }
    }

    var channelStates by remember { mutableStateOf(readChannelStates(context)) }
    LifecycleResumeEffect(Unit) {
        environment.notifications.refresh()
        channelStates = readChannelStates(context)
        onPauseOrDispose {}
    }

    val showsApps = onOpenConnectedApps != null
    val source = accounts ?: remember(session, showsApps) {
        AccountInfoSource {
            AccountInfo(
                connectedApps = if (showsApps) {
                    session.loadAllConnectorStatuses()
                        ?.takeIf { it.isAuthoritative }
                        ?.services?.values?.count { it.connected }
                } else {
                    null
                },
            )
        }
    }
    var account by remember { mutableStateOf(AccountInfo()) }
    LaunchedEffect(connection?.id, source) {
        account = if (connection != null) source.load() else AccountInfo()
    }

    val aiUsageHiddenFor by session.aiUsageHiddenFor.collectAsState()
    val showsAiUsage = onOpenAiUsage != null && connection != null &&
        !AiUsageRules.hidden(aiUsageHiddenFor, connection?.id)
    LaunchedEffect(connection?.id, status, onOpenAiUsage != null) {
        if (onOpenAiUsage != null && connection != null && status == Session.Status.Live) session.aiUsage()
    }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }
    val versionText = if (version.isBlank()) {
        stringResource(R.string.mobile_openmausbot_befe710d)
    } else {
        stringResource(R.string.settings_version_footer, version)
    }
    val appUpdates = environment.appUpdates
    val updatesAvailable = appUpdates?.available == true
    val removesOne = SettingsLayout.removesOneOfMany(connections.size)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.app),
    ) {
        PageTopBar(title = stringResource(R.string.mobile_settings_c7f73bb5), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val groups = SettingsLayout.groups(
                showsComputer = bound != null || onConnect != null,
                paired = bound != null,
                showsWorkspace = onOpenRoutines != null || showsAiUsage,
                showsPrivacy = showsApps,
                updatesAvailable = updatesAvailable,
            )
            groups.forEach { group ->
                when (group) {
                    SettingsGroup.COMPUTER -> {
                        GroupTitle(stringResource(R.string.settings_your_computer))
                        GroupCard {
                            if (bound != null) {
                                val live = status == Session.Status.Live
                                val offline = status is Session.Status.Offline
                                DesignRow(
                                    title = bound.name,
                                    icon = SeiraIcons.Laptop,
                                    minHeight = 64.dp,
                                    subtitle = stringResource(R.string.settings2_computer_subtitle, computerStatusPhrase(status)),
                                    subtitleDot = if (live) colors.success else if (offline) colors.danger else null,
                                    onClick = onOpenComputers,
                                    trailing = if (onOpenComputers != null) {
                                        { Chevron() }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                DesignRow(
                                    title = stringResource(R.string.mobile_settings_connection_label),
                                    subtitle = localizedConnectionStatus(status),
                                    icon = SeiraIcons.Laptop,
                                    minHeight = 64.dp,
                                )
                                if (onConnect != null) {
                                    RowDivider(50.dp)
                                    DesignRow(
                                        title = stringResource(R.string.mobile_settings_connect_computer),
                                        icon = SeiraIcons.Plus,
                                        onClick = onConnect,
                                        trailing = { Chevron() },
                                    )
                                }
                            }
                        }
                    }

                    SettingsGroup.APPEARANCE -> {
                        GroupTitle(stringResource(R.string.mobile_settings_appearance))
                        GroupCard {
                            val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO)
                            ChipChoiceRow(
                                title = stringResource(R.string.settings2_theme_label),
                                options = listOf(
                                    stringResource(R.string.theme_mode_light),
                                    stringResource(R.string.theme_mode_dark),
                                    stringResource(R.string.theme_mode_auto),
                                ),
                                selectedIndex = modes.indexOf(themeMode).coerceAtLeast(0),
                                onSelect = { environment.chatPreferences.setThemeMode(modes[it]) },
                            )
                        }
                        GroupNote(stringResource(R.string.settings_appearance_caption))
                    }

                    SettingsGroup.NOTIFICATIONS -> {
                        GroupTitle(stringResource(R.string.mobile_settings_notifications_section))
                        GroupCard {
                            val appEnabled = notifications == NotificationAccess.GRANTED
                            if (!appEnabled) {
                                DesignRow(
                                    title = stringResource(R.string.settings_notif_turn_on),
                                    subtitle = stringResource(R.string.settings_notif_turn_on_body),
                                    minHeight = 64.dp,
                                    enabled = NotificationPermissionController.buttonEnabled(notifications),
                                    onClick = environment.notifications::act,
                                    trailing = { Chevron() },
                                )
                            } else {
                                NotificationChannelRow.entries.forEachIndexed { index, row ->
                                    if (index > 0) RowDivider()
                                    SwitchRow(
                                        title = stringResource(row.title),
                                        subtitle = stringResource(row.body),
                                        icon = null,
                                        checked = channelStates[row.channelId] ?: true,
                                        onToggle = { openChannelSettings(context, row.channelId) },
                                    )
                                }
                            }
                            if (alwaysOnAvailable) {
                                RowDivider()
                                SwitchRow(
                                    title = stringResource(R.string.mobile_settings_always_on),
                                    subtitle = stringResource(
                                        if (alwaysOnEnabled) {
                                            R.string.mobile_settings_background_on_description
                                        } else {
                                            R.string.mobile_settings_background_off_description
                                        },
                                    ),
                                    icon = null,
                                    checked = alwaysOnEnabled,
                                    onToggle = environment.onToggleAlwaysOn,
                                )
                            }
                        }
                    }

                    SettingsGroup.CHAT -> {
                        GroupTitle(stringResource(R.string.mobile_settings_chat_section))
                        GroupCard {
                            SettingsLayout.chatRows(paired = bound != null, callsEnabled = callsEnabled)
                                .forEachIndexed { index, row ->
                                    if (index > 0) RowDivider()
                                    when (row) {
                                        ChatRowKind.ACTIVITY -> DesignRow(
                                            title = stringResource(R.string.mobile_settings_change_activity_detail),
                                            onClick = { choosingActivity = true },
                                            trailing = { ValueWithChevron(localizedActivityLabel(activityDetail)) },
                                        )

                                        ChatRowKind.QUICK_REPLIES -> DesignRow(
                                            title = stringResource(R.string.mobile_quick_replies_c14223c4),
                                            onClick = { editingQuickReplies = true },
                                            trailing = { Chevron() },
                                        )

                                        ChatRowKind.DENSITY -> ChipChoiceRow(
                                            title = localizedMobileCopy("List density"),
                                            options = RosterDensity.entries.map { localizedMobileCopy(it.label) },
                                            selectedIndex = RosterDensity.entries.indexOf(rosterDensity),
                                            onSelect = { environment.chatPreferences.setRosterDensity(RosterDensity.entries[it]) },
                                        )

                                        ChatRowKind.VOICE -> DesignRow(
                                            title = stringResource(R.string.mobile_voice_3091c844),
                                            onClick = { showingLiveSettings = true },
                                            trailing = { liveVoice?.let { ValueWithChevron(it) } ?: Chevron() },
                                        )

                                        ChatRowKind.CAPTIONS -> SwitchRow(
                                            title = stringResource(R.string.mobile_settings_live_captions),
                                            subtitle = stringResource(R.string.mobile_settings_live_captions_body),
                                            icon = null,
                                            checked = liveCaptions,
                                            onToggle = { environment.chatPreferences.setLiveCaptions(!liveCaptions) },
                                        )
                                    }
                                }
                        }
                    }

                    SettingsGroup.WORKSPACE -> {
                        GroupTitle(stringResource(R.string.mobile_settings_workspace_section))
                        GroupCard {
                            if (onOpenRoutines != null) {
                                DesignRow(
                                    title = stringResource(R.string.mobile_threads_routines_65d7efcd),
                                    icon = SeiraIcons.CalendarClock,
                                    onClick = onOpenRoutines,
                                    trailing = { Chevron() },
                                )
                            }
                            if (showsAiUsage) {
                                if (onOpenRoutines != null) RowDivider(50.dp)
                                DesignRow(
                                    title = stringResource(R.string.ai_usage_title),
                                    icon = SeiraIcons.Gauge,
                                    onClick = onOpenAiUsage,
                                    trailing = { Chevron() },
                                )
                            }
                        }
                    }

                    SettingsGroup.PRIVACY -> {
                        GroupTitle(stringResource(R.string.settings_privacy))
                        GroupCard {
                            DesignRow(
                                title = stringResource(R.string.settings_connected_apps),
                                icon = SeiraIcons.Plug,
                                onClick = onOpenConnectedApps,
                                trailing = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        account.connectedApps?.let {
                                            Text(it.toString(), style = SeiraType.body, color = colors.ink2)
                                        }
                                        Chevron()
                                    }
                                },
                            )
                        }
                    }

                    SettingsGroup.ADVANCED -> {
                        GroupCard {
                            SwitchRow(
                                title = stringResource(R.string.home2_settings_advanced_title),
                                subtitle = stringResource(R.string.home2_settings_advanced_body),
                                icon = null,
                                checked = advancedMode,
                                onToggle = { environment.chatPreferences.setAdvancedMode(!advancedMode) },
                            )
                        }
                    }

                    SettingsGroup.HELP -> {
                        GroupTitle(stringResource(R.string.mobile_settings_help_section))
                        GroupCard {
                            DesignRow(
                                title = stringResource(R.string.mobile_settings_help_faq),
                                icon = SeiraIcons.Search,
                                onClick = { openLink(context, SettingsPolicy.HELP_FAQ_URL) },
                                trailing = { Chevron() },
                            )
                            RowDivider(50.dp)
                            DesignRow(
                                title = stringResource(R.string.mobile_settings_help_report),
                                icon = SeiraIcons.File,
                                onClick = { openLink(context, SettingsPolicy.HELP_REPORT_URL) },
                                trailing = { Chevron() },
                            )
                            RowDivider(50.dp)
                            DesignRow(
                                title = stringResource(R.string.mobile_settings_privacy_policy),
                                icon = SeiraIcons.Shield,
                                onClick = { openLink(context, SettingsPolicy.PRIVACY_POLICY_URL) },
                                trailing = { Chevron() },
                            )
                        }
                    }

                    SettingsGroup.SIGN_OUT -> {
                        Box(modifier = Modifier.padding(top = 24.dp))
                        GroupCard {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(role = Role.Button) { confirmingSignOut = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(if (removesOne) R.string.mobile_settings_remove_this_computer else R.string.settings_sign_out),
                                    style = SeiraType.bodyMedium.copy(fontSize = 16.sp),
                                    color = colors.danger,
                                )
                            }
                        }
                        if (!removesOne) GroupNote(stringResource(R.string.settings2_sign_out_footer))
                    }

                    SettingsGroup.UPDATES -> {
                        var updateCheck by remember { mutableStateOf(UpdateCheck.IDLE) }
                        Box(modifier = Modifier.padding(top = 24.dp))
                        GroupCard {
                            DesignRow(
                                title = stringResource(R.string.settings_check_updates),
                                subtitle = versionText,
                                minHeight = 64.dp,
                                enabled = updateCheck != UpdateCheck.CHECKING,
                                onClick = {
                                    updateCheck = UpdateCheck.CHECKING
                                    scope.launch {
                                        updateCheck = if (appUpdates?.checkNow() == true) UpdateCheck.FOUND else UpdateCheck.CURRENT
                                    }
                                },
                                trailing = {
                                    val text = when (updateCheck) {
                                        UpdateCheck.IDLE -> null
                                        UpdateCheck.CHECKING -> stringResource(R.string.settings_update_checking)
                                        UpdateCheck.FOUND -> stringResource(R.string.settings_update_found)
                                        UpdateCheck.CURRENT -> stringResource(R.string.settings_update_current)
                                    }
                                    if (text != null) Text(text, style = SeiraType.callout, color = colors.ink3)
                                },
                            )
                        }
                    }
                }
            }

            if (!updatesAvailable) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SeiraCharacter(look = SeiraLook.of(hair = "seira", color = "chrome"), size = 44.dp, animated = false)
                    Text(versionText, style = SeiraType.callout, color = colors.ink3)
                }
            }
            Box(modifier = Modifier.padding(bottom = 32.dp))
        }
    }

    if (showingLiveSettings) {
        LiveCallSettingsSheet(onDismiss = { showingLiveSettings = false })
    }

    if (confirmingSignOut) {
        ConfirmSheet(
            title = if (removesOne) {
                stringResource(R.string.mobile_remove_connection_name_e686927b, connection?.name.orEmpty())
            } else {
                stringResource(R.string.settings2_sign_out_title)
            },
            note = if (removesOne) {
                stringResource(R.string.mobile_this_removes_the_saved_connection__45c7d71e)
            } else {
                stringResource(R.string.settings2_sign_out_note)
            },
            confirmLabel = stringResource(if (removesOne) R.string.mobile_remove_e963907d else R.string.mobile_unpair_9c293ad4),
            onConfirm = {
                confirmingSignOut = false
                // Local token and connection only. Revoking the device itself is
                // done on the computer.
                session.signOut()
            },
            onDismiss = { confirmingSignOut = false },
            tag = "sign-out-sheet",
        )
    }

    if (choosingActivity) {
        ChoiceSheet(
            title = localizedMobileCopy("Activity detail"),
            options = ActivityDetail.entries,
            selected = activityDetail,
            label = { localizedActivityLabel(it) },
            caption = { localizedActivityCaption(it) },
            onChoose = environment.chatPreferences::setActivityDetail,
            onDismiss = { choosingActivity = false },
            tag = "activity-detail-sheet",
        )
    }

    if (editingQuickReplies) {
        QuickRepliesEditor(
            preferences = environment.chatPreferences,
            onDismiss = { editingQuickReplies = false },
        )
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

private fun readChannelStates(context: Context): Map<String, Boolean> {
    val manager = NotificationManagerCompat.from(context)
    val enabled = manager.areNotificationsEnabled()
    return NotificationChannelRow.entries.associate { row ->
        row.channelId to SettingsDesign.channelIsOn(enabled, manager.getNotificationChannel(row.channelId)?.importance)
    }
}

private fun openChannelSettings(context: Context, channelId: String) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun localizedConnectionStatus(status: Session.Status): String = when (status) {
    Session.Status.Live -> stringResource(R.string.mobile_settings_connected_status)
    Session.Status.Connecting -> stringResource(R.string.mobile_settings_connecting_status)
    Session.Status.Unpaired -> stringResource(R.string.mobile_settings_not_paired_status)
    Session.Status.Unauthorized -> stringResource(R.string.mobile_settings_unpaired_on_computer_status)
    is Session.Status.Offline -> localizedMobileCopy(status.message)
}

@Composable
internal fun computerStatusPhrase(status: Session.Status): String = when (status) {
    is Session.Status.Offline -> stringResource(R.string.settings_state_offline)
    else -> localizedConnectionStatus(status)
}

@Composable
private fun localizedActivityLabel(detail: ActivityDetail): String = when (detail) {
    ActivityDetail.FULL -> stringResource(R.string.mobile_activity_full)
    ActivityDetail.REDUCED -> stringResource(R.string.mobile_activity_reduced)
    ActivityDetail.HIDDEN -> stringResource(R.string.mobile_activity_hidden)
}

@Composable
private fun localizedActivityCaption(detail: ActivityDetail): String = when (detail) {
    ActivityDetail.FULL -> stringResource(R.string.mobile_activity_full_caption)
    ActivityDetail.REDUCED -> stringResource(R.string.mobile_activity_reduced_caption)
    ActivityDetail.HIDDEN -> stringResource(R.string.mobile_activity_hidden_caption)
}

/**
 * What the Troubleshooting section says before offering to reconnect — the port
 * of `ConnectionSecurityView.troubleshootingText` (`ios/App/SettingsView.swift:445-458`).
 */
internal fun troubleshootingText(status: Session.Status): String = when (status) {
    Session.Status.Live -> "This computer is connected and responding normally."
    Session.Status.Connecting -> "Seira is trying the saved connection automatically."
    Session.Status.Unauthorized -> "This phone was removed from the computer. Pair it again to reconnect."
    Session.Status.Unpaired -> "This phone is not paired with a computer."
    is Session.Status.Offline -> status.message
}

/**
 * The address with its middle taken out, so a long tailnet name still shows the
 * host and the port it ends in — `shortened` in `ios/App/SettingsView.swift:460-464`.
 */
internal fun shortenedAddress(address: String): String {
    if (address.length <= 14) return address
    val leading = minOf(20, maxOf(8, address.length - 8))
    return address.take(leading) + "…" + address.takeLast(6)
}

private enum class UpdateCheck { IDLE, CHECKING, FOUND, CURRENT }
