package com.openmausbot.companion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.testTag
import com.openmausbot.companion.R
import com.openmausbot.companion.character.CarvedAvatar
import com.openmausbot.companion.character.SeiraAssets
import com.openmausbot.companion.avatar.AvatarImagePicker
import com.openmausbot.companion.avatar.PreparedAvatar
import com.openmausbot.companion.core.AvatarCrop
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotProfilePatch
import com.openmausbot.companion.core.ConfigStatus
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.SeiraCast
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.ControlCircleButton
import com.openmausbot.companion.ui.theme.identityPlate
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import com.openmausbot.companion.ui.theme.seiraShadow
import com.openmausbot.companion.ui.theme.seiraTween
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val SHEET_RADIUS = 34.dp
private val SHEET_INSET = 8.dp
private val PLATE_SIZE = 216.dp
private val PREVIEW_SIZE = 184.dp
private const val CELEBRATE_MILLIS = 1_200L
private const val FADE_MILLIS = 180
private const val SLIDE_FROM = 1.12f

/**
 * The avatar sheet: the Seira mascot, a generated picture or an uploaded picture. It is a floating
 * sheet in its own window, so it opens over the bot's settings sheet as well as over Home.
 */
@Composable
internal fun ChangeLookSheet(bot: Bot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = LocalCompanion.current.session
    val status by session.status.collectAsState()
    val fleet by session.state.collectAsState()
    val live = fleet.bot(bot.id) ?: bot
    val cast = remember {
        runCatching { SeiraCast.decode(SeiraAssets.forContext(context).castJson()) }.getOrDefault(emptyList())
    }
    val state = rememberSaveable(bot.id, saver = ChangeLookState.saver(cast)) {
        ChangeLookState(SeiraLook.from(bot), cast)
    }
    var tab by rememberSaveable(bot.id, stateSaver = AvatarTabSaver) { mutableStateOf(AvatarSheetRules.defaultTab(bot)) }
    var prompt by rememberSaveable(bot.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var noteRes by rememberSaveable { mutableStateOf<Int?>(null) }
    var noteText by rememberSaveable { mutableStateOf<String?>(null) }
    var config by remember { mutableStateOf<ConfigStatus?>(null) }
    var configKnown by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    var opened by rememberSaveable { mutableStateOf(false) }
    val visible = remember { MutableTransitionState(opened).apply { targetState = true } }
    var closing by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val close = {
        if (!state.saving && !busy && !closing) {
            closing = true
            visible.targetState = false
        }
    }
    LaunchedEffect(Unit) { opened = true }
    val connected = status is Session.Status.Live
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        config = session.configStatus()
        configKnown = true
    }
    LaunchedEffect(visible) {
        snapshotFlow { closing && visible.isIdle && !visible.currentState }.first { it }
        currentOnDismiss()
    }

    fun liveBot(): Bot = session.state.value.bot(bot.id) ?: bot

    fun fail(res: Int) {
        noteText = null
        noteRes = res
        session.actionError = null
    }

    fun launchWork(block: suspend () -> Unit) {
        if (busy) return
        noteRes = null
        noteText = null
        scope.launch {
            busy = true
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(remember { AvatarImagePicker.contract() }) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        launchWork {
            when (val prepared = AvatarImagePicker.read(context.contentResolver, uri)) {
                is PreparedAvatar.Rejected -> {
                    noteText = prepared.message
                    session.actionError = null
                }
                is PreparedAvatar.Ready -> {
                    val updated = session.uploadAvatar(prepared.data, prepared.mime, liveBot(), AvatarCrop.CIRCLE)
                    if (updated == null) fail(R.string.avatar_picture_failed)
                }
            }
        }
    }

    val panels = AvatarPanels(
        showsPicture = AvatarSheetRules.showsPicture(live),
        hasPicture = AvatarSheetRules.hasPicture(live),
        busy = busy,
        note = noteRes?.let { stringResource(it) } ?: noteText?.let { localizedMobileCopy(it) },
        generationKnown = configKnown,
        generationReady = ProfileRules.imageGenerationReady(config),
        canGenerate = connected && ProfileRules.canGenerate(busy, config, prompt),
        prompt = prompt,
        onPrompt = { prompt = it },
        onGenerate = {
            launchWork {
                val generated = session.generateAvatar(ProfileRules.generatePrompt(prompt), liveBot())
                if (generated == null) {
                    fail(R.string.avatar_generate_failed)
                } else if (AvatarSheetRules.needsCircle(generated)) {
                    val shown = session.updateProfile(BotProfilePatch(avatarCrop = AvatarCrop.CIRCLE), generated)
                    if (shown == null) fail(R.string.avatar_picture_failed)
                }
            }
        },
        onChoose = { pickImage.launch(AvatarImagePicker.request()) },
        onUsePicture = {
            launchWork {
                val shown = session.updateProfile(BotProfilePatch(avatarCrop = AvatarCrop.CIRCLE), liveBot())
                if (shown == null) fail(R.string.avatar_picture_failed)
            }
        },
        onRemovePicture = {
            launchWork {
                val cleared = session.updateProfile(
                    BotProfilePatch(avatarUrl = BotProfilePatch.AvatarURL.Clear, avatarCrop = AvatarCrop.MASCOT),
                    liveBot(),
                )
                if (cleared == null) fail(R.string.avatar_picture_failed)
            }
        },
    )

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(seiraTween(SCRIM_MILLIS, reduced)),
                exit = fadeOut(seiraTween(SCRIM_MILLIS, reduced)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Seira.colors.scrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = close,
                        )
                        .localizedSemantics(contentDescription = { stringResource(R.string.change_look_close) }),
                )
            }
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(seiraTween(SeiraMotion.SHEET, reduced)) { (it * SLIDE_FROM).toInt() },
                exit = slideOutVertically(seiraTween(SeiraMotion.SHEET, reduced)) { (it * SLIDE_FROM).toInt() },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChangeLookSheetBody(
                    botName = bot.name,
                    state = state,
                    connected = connected,
                    tab = tab,
                    onTab = {
                        noteRes = null
                        noteText = null
                        tab = it
                    },
                    panels = panels,
                    currentAvatar = { size ->
                        BotAvatar(
                            bot = live,
                            size = size,
                            state = CharacterMood.IDLE,
                            animated = true,
                            sway = true,
                        )
                    },
                    onClose = close,
                    onSave = {
                        if (!closing && !busy) {
                            scope.launch {
                                val saved = state.save(
                                    showsPicture = AvatarSheetRules.showsPicture(liveBot()),
                                    showMascot = {
                                        session.updateProfile(BotProfilePatch(avatarCrop = AvatarCrop.MASCOT), liveBot())
                                            .also { if (it == null) session.actionError = null }
                                    },
                                    update = { patch -> session.updateLook(bot.id, patch) },
                                )
                                if (saved) {
                                    haptics.play(TactileAction.SAVE_LOOK_SUCCESS)
                                    close()
                                }
                            }
                        }
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}

private const val SCRIM_MILLIS = 240

private val AvatarTabSaver = androidx.compose.runtime.saveable.Saver<AvatarTab, String>(
    save = { it.name },
    restore = { AvatarTab.restore(it) ?: AvatarTab.SEIRA },
)

/** The sheet itself: header, preview, the three tabs, the open panel and, on the Seira tab, the footer. */
@Composable
internal fun ChangeLookSheetBody(
    botName: String,
    state: ChangeLookState,
    connected: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    tab: AvatarTab = AvatarTab.SEIRA,
    onTab: (AvatarTab) -> Unit = {},
    panels: AvatarPanels = AvatarPanels(),
    currentAvatar: @Composable (Dp) -> Unit = { size -> CarvedAvatar(look = state.original, size = size) },
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(SHEET_RADIUS)
    val offline = stringResource(R.string.change_look_offline)
    val note = panels.note ?: if (!connected && tab != AvatarTab.SEIRA) offline else null
    BoxWithConstraints(modifier = modifier.fillMaxWidth().widthIn(max = 560.dp)) {
        Column(
            modifier = Modifier
                .padding(SHEET_INSET)
                .fillMaxWidth()
                .seiraShadow(shape)
                .background(colors.sheet, shape)
                .clip(shape)
                .heightIn(max = maxHeight * 0.92f),
        ) {
            Header(botName, onClose)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Preview(state, tab, panels.busy, currentAvatar)
                AvatarTabs(tab, onTab)
                if (note != null) {
                    Text(
                        text = note,
                        style = SeiraType.callout,
                        color = if (panels.note != null) colors.danger else colors.ink2,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().testTag("avatar-note"),
                    )
                }
                when (tab) {
                    AvatarTab.SEIRA -> {
                        Categories(state)
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) { ChangeLookPanel(state) }
                    }
                    AvatarTab.GENERATE -> Box(modifier = Modifier.padding(horizontal = 16.dp)) { GeneratePanel(panels) }
                    AvatarTab.UPLOAD -> Box(modifier = Modifier.padding(horizontal = 16.dp)) { UploadPanel(panels, connected) }
                }
            }
            if (tab == AvatarTab.SEIRA) Footer(state, connected, panels.showsPicture, panels.busy, onSave)
        }
    }
}

@Composable
private fun Header(botName: String, onClose: () -> Unit) {
    val colors = Seira.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            Text(
                text = stringResource(R.string.mobile_avatar_7631b26e),
                style = SeiraType.title3,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = botName, style = SeiraType.body.copy(fontSize = 14.sp), color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ControlCircleButton(
            icon = SeiraIcons.X,
            contentDescription = stringResource(R.string.change_look_close),
            onClick = onClose,
            size = 32.dp,
        )
    }
}

@Composable
private fun ColumnScope.Preview(
    state: ChangeLookState,
    tab: AvatarTab,
    busy: Boolean,
    currentAvatar: @Composable (Dp) -> Unit,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(state.celebrations) {
        if (state.celebrations == 0) return@LaunchedEffect
        celebrating = true
        delay(CELEBRATE_MILLIS)
        celebrating = false
    }
    val seira = tab == AvatarTab.SEIRA
    val description = stringResource(R.string.change_look_preview)
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(PLATE_SIZE)
            .clip(CircleShape)
            .background(identityPlate((if (seira) state.look else state.original).color, colors.isDark), CircleShape)
            .then(
                if (seira) {
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = state::celebrate,
                        )
                        .semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (seira) {
            Crossfade(
                targetState = state.look,
                animationSpec = if (reduced) snap() else tween(FADE_MILLIS, easing = SeiraMotion.Ease),
            ) { look ->
                CarvedAvatar(
                    look = look,
                    size = PREVIEW_SIZE,
                    mood = if (celebrating) CharacterMood.HAPPY else CharacterMood.IDLE,
                    animated = true,
                    sway = true,
                )
            }
        } else {
            currentAvatar(PREVIEW_SIZE)
        }
        if (busy) {
            Box(
                modifier = Modifier.fillMaxSize().background(colors.scrim, CircleShape).testTag("avatar-busy"),
                contentAlignment = Alignment.Center,
            ) { ChangeLookSpinner(colors.onPrimary) }
        }
    }
}

@Composable
private fun Categories(state: ChangeLookState) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChangeLookCategory.entries.forEach { category ->
            ChangeLookChip(
                title = changeLookCategoryTitle(category),
                selected = state.category == category,
                onClick = { state.category = category },
            )
        }
    }
}

@Composable
private fun Footer(state: ChangeLookState, connected: Boolean, showsPicture: Boolean, busy: Boolean, onSave: () -> Unit) {
    val colors = Seira.colors
    val canSave = state.canSave(connected, showsPicture) && !busy
    val note = when {
        state.saveFailed -> stringResource(R.string.change_look_failed)
        !connected -> stringResource(R.string.change_look_offline)
        else -> null
    }
    val reason = when {
        !connected -> stringResource(R.string.change_look_offline)
        !state.changed && !showsPicture -> stringResource(R.string.change_look_no_changes)
        else -> null
    }
    val saving = stringResource(R.string.change_look_saving)
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (note != null) {
            Text(
                text = note,
                style = SeiraType.callout,
                color = if (state.saveFailed) colors.danger else colors.ink2,
                modifier = Modifier.fillMaxWidth().testTag("change-look-note"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(
                modifier = Modifier.weight(1f).testTag("change-look-surprise"),
                fill = colors.control,
                enabled = !state.saving && !busy,
                onClick = state::surprise,
            ) {
                Text(stringResource(R.string.change_look_surprise), style = SeiraType.headline.copy(fontSize = 16.sp), color = colors.ink, maxLines = 1)
            }
            Pill(
                modifier = Modifier
                    .weight(1f)
                    .testTag("change-look-save")
                    .semantics {
                        if (state.saving) contentDescription = saving
                        if (!canSave && !state.saving && reason != null) stateDescription = reason
                    },
                fill = colors.primary.copy(alpha = if (canSave || state.saving) 1f else 0.4f),
                enabled = canSave,
                onClick = onSave,
            ) {
                if (state.saving) {
                    ChangeLookSpinner(colors.onPrimary)
                } else {
                    Text(stringResource(R.string.change_look_save), style = SeiraType.headline.copy(fontSize = 16.sp), color = colors.onPrimary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Pill(
    fill: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
