package com.openmausbot.companion.ui.chat.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ApprovalMode
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal const val HOLD_HINT_TAG = "mode-hold-hint"

private val THUMB = 40.dp
private val TRACK_HEIGHT = 64.dp

private fun modeIcon(mode: ApprovalMode?): ImageVector = when (mode) {
    ApprovalMode.ASK -> SeiraIcons.Shield
    ApprovalMode.EDITS -> SeiraIcons.PenSquare
    ApprovalMode.AUTO -> SeiraIcons.Gauge
    ApprovalMode.FULL -> SeiraIcons.Warning
    else -> SeiraIcons.Sliders
}

/**
 * Four steps from calm to hot. A tap or a drag asks for a step. Full only
 * answers a hold of [ModeLadder.HOLD_MILLIS], so it cannot be picked by accident.
 */
@Composable
internal fun ApprovalLadder(
    shown: ApprovalMode?,
    enabled: Boolean,
    onRequest: (ApprovalMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val request by rememberUpdatedState(onRequest)
    val currentShown by rememberUpdatedState(shown)
    val currentEnabled by rememberUpdatedState(enabled)

    var width by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var releaseFrom by remember { mutableStateOf<Float?>(null) }
    var dragPreview by remember { mutableStateOf<ApprovalMode?>(null) }
    var fullPressed by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ApprovalMode?>(null) }
    var committed by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }

    val frac = remember { Animatable(ModeLadder.fraction(shown) ?: 0f) }
    val ring = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val spin = remember { Animatable(0f) }

    val preview = dragPreview ?: if (fullPressed) ApprovalMode.FULL else null
    val target = preview ?: pending ?: shown
    val holding = (fullPressed || dragPreview == ApprovalMode.FULL) && shown != ApprovalMode.FULL

    fun xFor(f: Float): Float {
        val span = width * 0.75f
        val x = width * 0.125f + f * span
        return if (rtl) width - x else x
    }

    fun fractionFor(x: Float): Float {
        val span = width * 0.75f
        val local = if (rtl) width - x else x
        return if (span <= 0f) 0f else ((local - width * 0.125f) / span).coerceIn(0f, 1f)
    }

    fun fail() {
        hint = true
        if (!reduced) {
            scope.launch {
                shake.snapTo(0f)
                shake.animateTo(
                    0f,
                    keyframes {
                        durationMillis = 340
                        0f at 0
                        7f at 60
                        -7f at 140
                        5f at 220
                        -3f at 280
                        0f at 340
                    },
                )
            }
        }
    }

    LaunchedEffect(dragFraction == null, preview, pending, shown, reduced) {
        if (dragFraction != null) return@LaunchedEffect
        releaseFrom?.let {
            frac.snapTo(it)
            releaseFrom = null
        }
        val goal = ModeLadder.fraction(target) ?: return@LaunchedEffect
        frac.animateTo(goal, if (reduced) tween(120) else spring(dampingRatio = 0.67f, stiffness = 380f))
    }

    LaunchedEffect(enabled) {
        if (enabled) pending = null
    }

    LaunchedEffect(holding) {
        ring.snapTo(0f)
        if (!holding) return@LaunchedEffect
        ring.animateTo(1f, tween(ModeLadder.HOLD_MILLIS.toInt(), easing = LinearEasing))
        committed = true
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        pending = ApprovalMode.FULL
        request(ApprovalMode.FULL)
    }

    val iconMode = preview ?: shown
    var lastIcon by remember { mutableStateOf(iconMode) }
    LaunchedEffect(iconMode) {
        if (iconMode == lastIcon) return@LaunchedEffect
        lastIcon = iconMode
        if (reduced) return@LaunchedEffect
        spin.snapTo(-55f)
        spin.animateTo(0f, spring(dampingRatio = 0.67f, stiffness = 380f))
    }

    val sixDp = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    val title = stringResource(R.string.composer_mode_title)
    val stateText = shown?.let {
        stringResource(ModeChip.nameRes(it)) + ", " + stringResource(ModeChip.descriptionRes(it))
    }
    val thumbFraction = dragFraction ?: frac.value
    val heat = heatAt(thumbFraction, colors.success, colors.warning, colors.danger)
    val thumbVisible = ModeLadder.index(shown) != null || preview != null || pending != null

    Column(modifier = modifier.fillMaxWidth().graphicsLayer { translationX = shake.value * density }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT)
                .onSizeChanged { width = it.width }
                .semantics {
                    contentDescription = title
                    stateText?.let { stateDescription = it }
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = (ModeLadder.index(shown) ?: 0).toFloat(),
                        range = 0f..(ModeLadder.steps.size - 1).toFloat(),
                        steps = ModeLadder.steps.size - 2,
                    )
                    if (!enabled) disabled()
                    setProgress { value ->
                        if (!currentEnabled) return@setProgress false
                        val step = ModeLadder.stepAt(value / (ModeLadder.steps.size - 1))
                        pending = step
                        request(step)
                        true
                    }
                }
                .pointerInput(width, rtl) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!currentEnabled || width == 0) return@awaitEachGesture
                        committed = false
                        hint = false
                        var last: ApprovalMode? = null
                        var position = down.position.x
                        do {
                            val f = fractionFor(position)
                            val step = ModeLadder.stepAt(f)
                            dragFraction = f
                            if (step != last) {
                                if (last != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                last = step
                                dragPreview = step
                            }
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            change.consume()
                            position = change.position.x
                            val pressed = change.pressed
                        } while (pressed)
                        val end = fractionFor(position)
                        val step = ModeLadder.stepAt(end)
                        releaseFrom = (dragFraction ?: end)
                        dragFraction = null
                        dragPreview = null
                        when {
                            ModeLadder.needsHold(step) && currentShown != step -> if (!committed) fail()
                            step != currentShown -> {
                                pending = step
                                request(step)
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.matchParentSize()) {
                val y = size.height / 2
                val x0 = xFor(0f)
                val x3 = xFor(1f)
                val stroke = 6.dp.toPx()
                val brush = Brush.horizontalGradient(
                    listOf(colors.success, colors.warning, colors.danger),
                    startX = minOf(x0, x3),
                    endX = maxOf(x0, x3),
                )
                drawLine(brush, Offset(x0, y), Offset(x3, y), strokeWidth = stroke, cap = StrokeCap.Round, alpha = if (enabled) 0.85f else 0.45f)
                for (i in ModeLadder.steps.indices) {
                    drawCircle(colors.sheet, radius = 4.dp.toPx(), center = Offset(xFor(i / (ModeLadder.steps.size - 1).toFloat()), y))
                }
            }
            if (thumbVisible) {
                Box(
                    modifier = Modifier
                        .size(THUMB + 16.dp)
                        .offset {
                            IntOffset(
                                (xFor(thumbFraction) - (THUMB + 16.dp).toPx() / 2).roundToInt(),
                                ((TRACK_HEIGHT - THUMB - 16.dp).toPx() / 2).roundToInt(),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        if (ring.value > 0f) {
                            val inset = 3.dp.toPx()
                            drawArc(
                                color = colors.danger,
                                startAngle = -90f,
                                sweepAngle = 360f * ring.value,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.size(THUMB).background(heat, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            modeIcon(iconMode),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = spin.value },
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            for (mode in ModeLadder.steps) {
                StepLabel(
                    mode = mode,
                    selected = mode == shown,
                    onPress = {
                        if (!currentEnabled) return@StepLabel
                        committed = false
                        hint = false
                        if (ModeLadder.needsHold(mode) && currentShown != mode) fullPressed = true
                    },
                    onRelease = { completed ->
                        val wasHolding = fullPressed
                        fullPressed = false
                        if (completed) {
                            if (wasHolding) {
                                if (!committed) fail()
                            } else {
                                pending = mode
                                request(mode)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            AnimatedContent(
                targetState = preview ?: shown,
                transitionSpec = {
                    if (reduced) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                    } else {
                        (fadeIn(tween(200)) + slideInVertically(tween(200)) { sixDp }) togetherWith fadeOut(tween(120))
                    }
                },
                label = "ladderDescription",
            ) { mode ->
                if (mode != null) {
                    Text(stringResource(ModeChip.descriptionRes(mode)), style = SeiraType.caption, color = colors.ink2)
                } else {
                    Box(Modifier.heightIn(min = 16.dp))
                }
            }
            if (hint) {
                Text(
                    stringResource(R.string.composer_mode_hold_full),
                    style = SeiraType.caption,
                    color = colors.danger,
                    modifier = Modifier.padding(top = 4.dp).testTag(HOLD_HINT_TAG),
                )
            }
        }
    }
}

@Composable
private fun StepLabel(
    mode: ApprovalMode,
    selected: Boolean,
    onPress: () -> Unit,
    onRelease: (completed: Boolean) -> Unit,
    modifier: Modifier,
) {
    val colors = Seira.colors
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(modeRowTag(mode))
            .semantics { hideFromAccessibility() }
            .pointerInput(mode) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    press()
                    val up = waitForUpOrCancellation()
                    release(up != null)
                }
            }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(ModeChip.nameRes(mode)),
            style = if (selected) SeiraType.bodyMedium else SeiraType.callout,
            color = if (selected) colors.ink else colors.ink2,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun heatAt(fraction: Float, calm: Color, warm: Color, hot: Color): Color =
    if (fraction < 0.5f) lerp(calm, warm, fraction * 2f) else lerp(warm, hot, (fraction - 0.5f) * 2f)
