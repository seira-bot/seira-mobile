package com.openmausbot.companion.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.openmausbot.companion.BuildConfig
import com.openmausbot.companion.R
import com.openmausbot.companion.character.SeiraCharacter
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Profile
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.core.RosterDensity
import com.openmausbot.companion.core.AiUsageRules
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraCard
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraTween
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.SegmentedControl
import com.openmausbot.companion.ui.theme.GlassCircleButton
import com.openmausbot.companion.ui.theme.ThemeMode
import kotlinx.coroutines.delay
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
    val alwaysOnEnabled by environment.alwaysOnEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val colors = Seira.colors

    var editingAddress by remember { mutableStateOf(false) }
    var addressText by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    val invalidAddressMessage = stringResource(R.string.mobile_settings_invalid_address)
    var showingFullAddress by remember { mutableStateOf(false) }
    var addressCopied by remember { mutableStateOf(false) }
    var reconnecting by remember { mutableStateOf(false) }
    var confirmingUnpair by remember { mutableStateOf(false) }
    var pendingComputerRemoval by remember { mutableStateOf<Connection?>(null) }
    var choosingActivity by remember { mutableStateOf(false) }
    var choosingDensity by remember { mutableStateOf(false) }
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
                profile = session.configStatus()?.profile,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.app)
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            Row(modifier = Modifier.padding(start = 0.dp)) {
                GlassCircleButton(
                    icon = SeiraIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.mobile_back_b52b36b7),
                    onClick = onBack,
                )
            }
            Text(
                stringResource(R.string.mobile_settings_c7f73bb5),
                style = SeiraType.title1,
                color = colors.ink,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        val bound = connection
        if (bound != null) {
            Spacer(Modifier.height(18.dp))
            ProfileCard(account.profile, bound)
        }

        SettingsGroupTitle(stringResource(R.string.mobile_settings_appearance))
        SeiraCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(6.dp)) {
                val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO)
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.theme_mode_light) to SeiraIcons.Sun,
                        stringResource(R.string.theme_mode_dark) to SeiraIcons.Moon,
                        stringResource(R.string.theme_mode_auto) to SeiraIcons.Smartphone,
                    ),
                    selectedIndex = modes.indexOf(themeMode).coerceAtLeast(0),
                    onSelect = { environment.chatPreferences.setThemeMode(modes[it]) },
                )
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon3d(R.drawable.icon3d_appearance, 30.dp, "appearance")
                    Text(
                        stringResource(R.string.settings_appearance_caption),
                        style = SeiraType.callout,
                        color = colors.ink3,
                    )
                }
            }
        }

        SettingsGroupTitle(stringResource(R.string.settings_your_computer))
        SeiraCard(modifier = Modifier.fillMaxWidth()) {
            val reduced = rememberReducedMotion()
            var detailsOpen by remember { mutableStateOf(false) }
            val chevronTurn by animateFloatAsState(
                if (detailsOpen) 180f else 0f,
                seiraTween(SeiraMotion.BASE, reduced),
            )
            val reconnect: () -> Unit = {
                scope.launch {
                    reconnecting = true
                    session.refresh()
                    reconnecting = false
                }
            }
            if (bound != null) {
                val live = status == Session.Status.Live
                val offline = status is Session.Status.Offline
                DesignRow(
                    title = bound.name,
                    icon = SeiraIcons.Laptop,
                    art = Art3d(R.drawable.icon3d_computer, "computer"),
                    minHeight = 64.dp,
                    subtitle = computerStatusPhrase(status),
                    subtitleDot = if (live) colors.success else if (offline) colors.danger else null,
                )
                if (offline) {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        FullWidthPill(
                            text = stringResource(R.string.settings_reconnect),
                            onClick = reconnect,
                            enabled = !reconnecting,
                            height = 48.dp,
                        )
                    }
                }
                SettingsHairline(50.dp)
                Column(modifier = Modifier.animateContentSize(seiraTween(SeiraMotion.BASE, reduced))) {
                    DesignRow(
                        title = stringResource(R.string.settings_connection_details),
                        icon = SeiraIcons.Monitor,
                        onClick = { detailsOpen = !detailsOpen },
                        trailing = {
                            Icon(
                                SeiraIcons.ChevronDown,
                                contentDescription = null,
                                tint = colors.ink3,
                                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevronTurn },
                            )
                        },
                    )
                    if (detailsOpen) {
                        val address = SettingsPolicy.addressText(bound)
                        SettingsHairline(50.dp)
                        DesignRow(
                            title = stringResource(R.string.mobile_address_d70f93df),
                            subtitle = if (showingFullAddress) null else shortenedAddress(address),
                            icon = SeiraIcons.Shield,
                            onClick = { showingFullAddress = !showingFullAddress },
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
                        if (showingFullAddress) {
                            SelectionContainer {
                                Text(
                                    text = address,
                                    style = SeiraType.callout,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.ink2,
                                    modifier = Modifier.padding(start = 50.dp, end = 16.dp, bottom = 12.dp),
                                )
                            }
                        }
                        SettingsHairline(50.dp)
                        DesignRow(
                            title = stringResource(R.string.mobile_edit_address_31fe67f4),
                            icon = SeiraIcons.PenSquare,
                            onClick = {
                                addressText = address
                                addressError = null
                                editingAddress = true
                            },
                            trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
                        )
                        SettingsHairline(50.dp)
                        DesignRow(
                            title = stringResource(R.string.settings_pair_another),
                            icon = SeiraIcons.Plus,
                            onClick = {
                                haptics.play(TactileAction.CONNECT_ANOTHER_COMPUTER)
                                session.beginPairing()
                            },
                            trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
                        )
                        SettingsHairline(50.dp)
                        DesignRow(
                            title = stringResource(R.string.mobile_settings_try_reconnecting),
                            icon = SeiraIcons.Monitor,
                            enabled = !reconnecting,
                            onClick = reconnect,
                            trailing = {
                                if (reconnecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.ink2)
                                }
                            },
                        )
                    }
                }
            } else {
                DesignRow(
                    title = stringResource(R.string.mobile_settings_connection_label),
                    subtitle = localizedConnectionStatus(status),
                    icon = SeiraIcons.Laptop,
                    art = Art3d(R.drawable.icon3d_computer, "computer"),
                    minHeight = 64.dp,
                )
            }

            connections.filter { it.id != bound?.id }.forEach { computer ->
                SettingsHairline(60.dp)
                DesignRow(
                    title = computer.name,
                    subtitle = stringResource(R.string.settings_state_saved),
                    icon = SeiraIcons.Laptop,
                    art = Art3d(R.drawable.icon3d_computer, "computer"),
                    minHeight = 64.dp,
                    onClick = {
                        haptics.play(TactileAction.SWITCH_COMPUTER)
                        session.switchComputer(computer.id)
                    },
                    trailing = {
                        ControlRemoveButton(stringResource(R.string.mobile_settings_remove_computer, computer.name)) {
                            pendingComputerRemoval = computer
                        }
                    },
                )
            }

            if (bound == null && onConnect != null) {
                SettingsHairline(60.dp)
                DesignRow(
                    title = stringResource(R.string.mobile_settings_connect_computer),
                    icon = SeiraIcons.Plus,
                    art = Art3d(R.drawable.icon3d_pair, "pair"),
                    onClick = onConnect,
                    trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
                )
            }
        }

        SettingsGroupTitle(stringResource(R.string.mobile_settings_notifications_section))
        SeiraCard(modifier = Modifier.fillMaxWidth()) {
            val appEnabled = notifications == NotificationAccess.GRANTED
            if (!appEnabled) {
                DesignRow(
                    title = stringResource(R.string.settings_notif_turn_on),
                    subtitle = stringResource(R.string.settings_notif_turn_on_body),
                    icon = SeiraIcons.Bell,
                    art = Art3d(R.drawable.icon3d_notifications, "notifications"),
                    minHeight = 64.dp,
                    enabled = NotificationPermissionController.buttonEnabled(notifications),
                    onClick = environment.notifications::act,
                    trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
                )
            } else {
                NotificationChannelRow.entries.forEachIndexed { index, row ->
                    if (index > 0) SettingsHairline(60.dp)
                    SwitchRow(
                        title = stringResource(row.title),
                        subtitle = stringResource(row.body),
                        icon = SeiraIcons.Bell,
                        art = Art3d(R.drawable.icon3d_notifications, "notifications"),
                        checked = channelStates[row.channelId] ?: true,
                        onToggle = { openChannelSettings(context, row.channelId) },
                    )
                }
            }
        }

        if (bound != null && callsEnabled) {
            SettingsGroupTitle(stringResource(R.string.mobile_settings_voice_calls_section))
            SeiraCard(modifier = Modifier.fillMaxWidth()) {
                DesignRow(
                    title = stringResource(R.string.mobile_voice_3091c844),
                    icon = SeiraIcons.Volume2,
                    art = Art3d(R.drawable.icon3d_voice, "voice"),
                    onClick = { showingLiveSettings = true },
                    trailing = {
                        liveVoice?.let { ValueWithChevron(it) }
                            ?: Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                    },
                )
                SettingsHairline(60.dp)
                SwitchRow(
                    title = stringResource(R.string.mobile_settings_live_captions),
                    subtitle = stringResource(R.string.mobile_settings_live_captions_body),
                    icon = SeiraIcons.Captions,
                    checked = liveCaptions,
                    onToggle = { environment.chatPreferences.setLiveCaptions(!liveCaptions) },
                )
            }
        }

        if (alwaysOnAvailable) {
            SettingsGroupTitle(stringResource(R.string.mobile_settings_background_connection))
            SeiraCard(modifier = Modifier.fillMaxWidth()) {
                SwitchRow(
                    title = stringResource(R.string.mobile_settings_always_on),
                    subtitle = stringResource(if (alwaysOnEnabled) R.string.mobile_settings_background_on_description else R.string.mobile_settings_background_off_description),
                    icon = SeiraIcons.Phone,
                    checked = alwaysOnEnabled,
                    onToggle = environment.onToggleAlwaysOn,
                )
            }
        }

        SettingsGroupTitle(stringResource(R.string.mobile_settings_chat_section))
        SeiraCard(modifier = Modifier.fillMaxWidth()) {
            DesignRow(
                title = stringResource(R.string.mobile_settings_change_activity_detail),
                onClick = { choosingActivity = true },
                trailing = { ValueWithChevron(localizedActivityLabel(activityDetail)) },
            )
            SettingsHairline()
            DesignRow(
                title = stringResource(R.string.mobile_quick_replies_c14223c4),
                onClick = { editingQuickReplies = true },
                trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
            )
        }

        SettingsGroupTitle(localizedMobileCopy("List density"))
        SeiraCard(modifier = Modifier.fillMaxWidth()) {
            DesignRow(
                title = localizedMobileCopy("Change list density"),
                onClick = { choosingDensity = true },
                trailing = { ValueWithChevron(localizedMobileCopy(rosterDensity.label)) },
            )
        }

        if (onOpenRoutines != null) {
            SettingsGroupTitle(stringResource(R.string.mobile_settings_workspace_section))
            SeiraCard(modifier = Modifier.fillMaxWidth()) {
                DesignRow(
                    title = stringResource(R.string.mobile_threads_routines_65d7efcd),
                    icon = SeiraIcons.CalendarClock,
                    onClick = onOpenRoutines,
                    trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
                )
                if (showsAiUsage) {
                    DesignRow(
                        title = stringResource(R.string.ai_usage_title),
                        icon = SeiraIcons.BarChart,
                        onClick = onOpenAiUsage,
                        trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }

        if (onOpenConnectedApps != null) {
            SettingsGroupTitle(stringResource(R.string.settings_privacy))
            SeiraCard(modifier = Modifier.fillMaxWidth()) {
                DesignRow(
                    title = stringResource(R.string.settings_connected_apps),
                    icon = SeiraIcons.Shield,
                    art = Art3d(R.drawable.icon3d_privacy, "privacy"),
                    onClick = onOpenConnectedApps,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            account.connectedApps?.let { Text(it.toString(), style = SeiraType.body, color = colors.ink2) }
                            Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                        }
                    },
                )
            }
        }

        SettingsGroupTitle(stringResource(R.string.mobile_settings_help_section))
        SeiraCard(modifier = Modifier.fillMaxWidth()) {
            DesignRow(
                title = stringResource(R.string.mobile_settings_help_faq),
                icon = SeiraIcons.Search,
                art = Art3d(R.drawable.icon3d_help, "help"),
                onClick = { openLink(context, SettingsPolicy.HELP_FAQ_URL) },
                trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
            )
            SettingsHairline(60.dp)
            DesignRow(
                title = stringResource(R.string.mobile_settings_help_report),
                icon = SeiraIcons.File,
                onClick = { openLink(context, SettingsPolicy.HELP_REPORT_URL) },
                trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
            )
            SettingsHairline(60.dp)
            DesignRow(
                title = stringResource(R.string.mobile_settings_privacy_policy),
                icon = SeiraIcons.Shield,
                onClick = { openLink(context, SettingsPolicy.PRIVACY_POLICY_URL) },
                trailing = { Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp)) },
            )
        }

        if (bound != null) {
            Spacer(Modifier.height(12.dp))
            SeiraCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { confirmingUnpair = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (connections.size > 1) R.string.mobile_settings_remove_this_computer else R.string.settings_sign_out),
                        style = SeiraType.bodyMedium.copy(fontSize = 16.sp),
                        color = colors.danger,
                    )
                }
            }
            Text(
                stringResource(R.string.mobile_settings_unpair_footer),
                style = SeiraType.callout,
                color = colors.ink3,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }

        val appUpdates = environment.appUpdates
        if (appUpdates?.available == true) {
            var updateCheck by remember { mutableStateOf(UpdateCheck.IDLE) }
            Spacer(Modifier.height(18.dp))
            SeiraCard(modifier = Modifier.fillMaxWidth()) {
                DesignRow(
                    title = stringResource(R.string.settings_check_updates),
                    enabled = updateCheck != UpdateCheck.CHECKING,
                    onClick = {
                        updateCheck = UpdateCheck.CHECKING
                        scope.launch {
                            updateCheck = if (appUpdates.checkNow()) UpdateCheck.FOUND else UpdateCheck.CURRENT
                        }
                    },
                    trailing = {
                        when (updateCheck) {
                            UpdateCheck.IDLE -> Unit
                            UpdateCheck.CHECKING -> Text(stringResource(R.string.settings_update_checking), style = SeiraType.callout, color = colors.ink3)
                            UpdateCheck.FOUND -> Text(stringResource(R.string.settings_update_found), style = SeiraType.callout, color = colors.ink3)
                            UpdateCheck.CURRENT -> Text(stringResource(R.string.settings_update_current), style = SeiraType.callout, color = colors.ink3)
                        }
                    },
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SeiraCharacter(look = SeiraLook.of(hair = "seira", color = "chrome"), size = 44.dp, animated = false)
            Text(
                if (version.isBlank()) stringResource(R.string.mobile_openmausbot_befe710d) else stringResource(R.string.settings_version_footer, version),
                style = SeiraType.callout,
                color = colors.ink3,
            )
        }
        }
    }

    if (editingAddress) {
        AlertDialog(
            onDismissRequest = { editingAddress = false },
            title = { Text(stringResource(R.string.mobile_edit_address_31fe67f4)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mobile_settings_edit_address_message), fontSize = 14.sp)
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    addressError?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Session re-parses, re-dials and persists; the walk and
                        // promote semantics stay its job. The form only refuses
                        // what it can already tell is not an address.
                        if (session.updateAddress(addressText)) {
                            editingAddress = false
                        } else {
                            addressError = invalidAddressMessage
                        }
                    },
                ) { Text(stringResource(R.string.mobile_save_efc007a3)) }
            },
            dismissButton = {
                TextButton(onClick = { editingAddress = false }) { Text(stringResource(R.string.mobile_cancel_77dfd213)) }
            },
        )
    }

    if (showingLiveSettings) {
        LiveCallSettingsSheet(onDismiss = { showingLiveSettings = false })
    }

    if (confirmingUnpair) {
        AlertDialog(
            onDismissRequest = { confirmingUnpair = false },
            title = { Text(if (connections.size > 1) stringResource(R.string.mobile_remove_connection_name_e686927b, connection?.name.orEmpty()) else stringResource(R.string.mobile_settings_unpair_confirm_title)) },
            text = {
                Text(
                    if (connections.size > 1) {
                        stringResource(R.string.mobile_this_removes_the_saved_connection__45c7d71e)
                    } else {
                        stringResource(R.string.mobile_settings_unpair_confirm_message)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingUnpair = false
                        // Local token and connection only. Revoking the device
                        // itself is Settings → Phone on the computer (§6).
                        session.signOut()
                    },
                ) {
                    // With another computer saved this removes one of them; the
                    // phone stays paired, so "Unpair" would be the wrong promise.
                    Text(
                        text = if (connections.size > 1) stringResource(R.string.mobile_remove_e963907d) else stringResource(R.string.mobile_unpair_9c293ad4),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingUnpair = false }) { Text(stringResource(R.string.mobile_cancel_77dfd213)) }
            },
        )
    }

    pendingComputerRemoval?.let { computer ->
        AlertDialog(
            onDismissRequest = { pendingComputerRemoval = null },
            title = { Text(stringResource(R.string.mobile_remove_computer_name_272e4835, computer.name)) },
            text = { Text(stringResource(R.string.mobile_this_removes_the_saved_connection__54db6818)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingComputerRemoval = null
                        session.forgetConnection(computer.id)
                    },
                ) { Text(stringResource(R.string.mobile_remove_e963907d), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingComputerRemoval = null }) { Text(stringResource(R.string.mobile_cancel_77dfd213)) }
            },
        )
    }

    if (choosingActivity) {
        ChoiceDialog(
            title = "Activity detail",
            options = ActivityDetail.entries,
            selected = activityDetail,
            label = { it.label },
            caption = { it.caption },
            onChoose = environment.chatPreferences::setActivityDetail,
            onDismiss = { choosingActivity = false },
        )
    }

    if (choosingDensity) {
        ChoiceDialog(
            title = "List density",
            options = RosterDensity.entries,
            selected = rosterDensity,
            label = { it.label },
            caption = { it.caption },
            onChoose = environment.chatPreferences::setRosterDensity,
            onDismiss = { choosingDensity = false },
        )
    }

    if (editingQuickReplies) {
        QuickRepliesEditor(
            preferences = environment.chatPreferences,
            onDismiss = { editingQuickReplies = false },
        )
    }
}


@Composable
private fun ProfileCard(profile: Profile?, connection: Connection) {
    val colors = Seira.colors
    val name = profile?.name?.takeIf { it.isNotBlank() }
    val email = profile?.email?.takeIf { it.isNotBlank() }
    SeiraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(name?.let(SettingsDesign::initials))
            Column(modifier = Modifier.weight(1f)) {
                Text(name ?: connection.name, style = SeiraType.title3, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (email != null) {
                    Text(email, style = SeiraType.body.copy(fontSize = 14.sp), color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun ValueWithChevron(value: String) {
    val colors = Seira.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = SeiraType.body, color = colors.ink2)
        Icon(SeiraIcons.ChevronRight, null, tint = colors.ink3, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ControlRemoveButton(label: String, onClick: () -> Unit) {
    TouchTarget(onClick = onClick, contentDescription = label) {
        Icon(SeiraIcons.X, contentDescription = null, tint = Seira.colors.ink3, modifier = Modifier.size(18.dp))
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

/**
 * One choice from a short list, each with the line that explains it. iOS draws
 * a Picker (SettingsView.swift:67-78), which marks the choice already in
 * force; three plain buttons do not, so these are radio rows.
 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    caption: (T) -> String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizedMobileCopy(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MIN_TOUCH_TARGET)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onChoose(option)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(localizedMobileCopy(label(option)), textAlign = TextAlign.Start)
                            Text(localizedMobileCopy(caption(option)), fontSize = 12.sp, color = secondaryTint)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mobile_cancel_77dfd213)) } },
    )
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
private fun computerStatusPhrase(status: Session.Status): String = when (status) {
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

private const val ADDRESS_CLIP_LABEL = "Seira computer address"

/** Long enough for "Copied" to be read, short enough not to linger (iOS `:363-367`). */
private const val COPIED_LABEL_MILLIS = 2_000L

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
