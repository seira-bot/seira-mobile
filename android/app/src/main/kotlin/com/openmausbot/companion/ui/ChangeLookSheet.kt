package com.openmausbot.companion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import com.openmausbot.companion.core.Bot
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
import kotlinx.coroutines.launch

private val SHEET_RADIUS = 34.dp
private val SHEET_INSET = 8.dp
private val PLATE_SIZE = 216.dp
private val PREVIEW_SIZE = 184.dp
private const val CELEBRATE_MILLIS = 1_200L
private const val FADE_MILLIS = 180
private const val SLIDE_FROM = 1.12f

/**
 * Lets the person change a bot's look. It is a floating sheet in its own window, so it opens over the
 * bot's settings sheet as well as over Home.
 */
@Composable
internal fun ChangeLookSheet(bot: Bot, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = LocalCompanion.current.session
    val status by session.status.collectAsState()
    val state = remember(bot.id) {
        val cast = runCatching { SeiraCast.decode(SeiraAssets.forContext(context).castJson()) }.getOrDefault(emptyList())
        ChangeLookState(SeiraLook.from(bot), cast)
    }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val close = { if (!state.saving) onDismiss() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

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
            AnimatedVisibility(visibleState = visible, enter = fadeIn(seiraTween(SCRIM_MILLIS)), modifier = Modifier.fillMaxSize()) {
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
                enter = slideInVertically(seiraTween(SeiraMotion.SHEET)) { (it * SLIDE_FROM).toInt() },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ChangeLookSheetBody(
                    botName = bot.name,
                    state = state,
                    connected = status is Session.Status.Live,
                    onClose = close,
                    onSave = {
                        scope.launch {
                            if (state.save { patch -> session.updateLook(bot.id, patch) }) {
                                haptics.play(TactileAction.SAVE_LOOK_SUCCESS)
                                onDismiss()
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

/** The sheet itself: header, preview, categories, the open panel and the footer. */
@Composable
internal fun ChangeLookSheetBody(
    botName: String,
    state: ChangeLookState,
    connected: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val shape = RoundedCornerShape(SHEET_RADIUS)
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
                Preview(state)
                Categories(state)
                Box(modifier = Modifier.padding(horizontal = 16.dp)) { ChangeLookPanel(state) }
            }
            Footer(state, connected, onSave)
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
                text = stringResource(R.string.change_look_title),
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
private fun ColumnScope.Preview(state: ChangeLookState) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(state.celebrations) {
        if (state.celebrations == 0) return@LaunchedEffect
        celebrating = true
        delay(CELEBRATE_MILLIS)
        celebrating = false
    }
    val description = stringResource(R.string.change_look_preview)
    Box(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(PLATE_SIZE)
            .clip(CircleShape)
            .background(identityPlate(state.look.color, colors.isDark), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = state::celebrate,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = state.look,
            animationSpec = if (reduced) snap() else tween(FADE_MILLIS, easing = SeiraMotion.Ease),
            label = "look",
        ) { look ->
            CarvedAvatar(
                look = look,
                size = PREVIEW_SIZE,
                mood = if (celebrating) CharacterMood.HAPPY else CharacterMood.IDLE,
                animated = true,
                sway = true,
            )
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
private fun Footer(state: ChangeLookState, connected: Boolean, onSave: () -> Unit) {
    val colors = Seira.colors
    val canSave = state.canSave(connected)
    val note = when {
        state.saveFailed -> stringResource(R.string.change_look_failed)
        !connected -> stringResource(R.string.change_look_offline)
        else -> null
    }
    val reason = when {
        !connected -> stringResource(R.string.change_look_offline)
        !state.changed -> stringResource(R.string.change_look_no_changes)
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
                enabled = !state.saving,
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
