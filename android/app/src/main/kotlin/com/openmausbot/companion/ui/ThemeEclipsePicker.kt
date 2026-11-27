package com.openmausbot.companion.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.openmausbot.companion.R
import com.openmausbot.companion.ui.theme.LocalThemeReveal
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraType
import com.openmausbot.companion.ui.theme.ThemeMode
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private val SUN = Color(0xFFF2B33D)
private val SUN_RAY = Color(0xFFF6C85F)
private val MOON = Color(0xFFD7DCEA)
private val MOON_SHADE = Color(0xFF1C2030)
private val MODES = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO)

internal fun themeTileTag(mode: ThemeMode): String = "theme-tile.${mode.name.lowercase()}"

private class Phases(val rotation: State<Float>, val blink: State<Float>, val drift: State<Float>)

@Composable
private fun rememberPhases(active: Boolean): Phases {
    if (!active) return remember { Phases(mutableFloatStateOf(0f), mutableFloatStateOf(0f), mutableFloatStateOf(0f)) }
    val loop = rememberInfiniteTransition()
    val rotation = loop.animateFloat(0f, 360f, infiniteRepeatable(tween(22_000, easing = LinearEasing)))
    val blink = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)))
    val drift = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Reverse))
    return remember(rotation, blink, drift) { Phases(rotation, blink, drift) }
}

/** The theme row: a sun, a moon and an eclipse under one halo that follows the choice. */
@Composable
internal fun ThemeEclipsePicker(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val index = MODES.indexOf(selected).coerceAtLeast(0)
    val halo by animateFloatAsState(
        index.toFloat(),
        if (reduced) tween(120) else spring(dampingRatio = 0.67f, stiffness = 380f),
    )
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.settings2_theme_label),
            style = SeiraType.bodyMedium,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val slot = if (rtl) 2f - halo else halo
                val center = Offset((slot + 0.5f) * size.width / MODES.size, 8.dp.toPx() + 28.dp.toPx())
                drawCircle(colors.primary.copy(alpha = 0.12f), radius = 36.dp.toPx(), center = center)
                drawCircle(colors.primary.copy(alpha = 0.35f), radius = 36.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
            }
            Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                for (mode in MODES) {
                    ThemeTile(
                        mode = mode,
                        selected = mode == selected,
                        reduced = reduced,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(
    mode: ThemeMode,
    selected: Boolean,
    reduced: Boolean,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier,
) {
    val colors = Seira.colors
    val reveal = LocalThemeReveal.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var centre by remember { mutableStateOf(Offset.Zero) }
    val phases = rememberPhases(selected && !reduced)
    val alpha by animateFloatAsState(if (selected) 1f else 0.6f, tween(if (reduced) 0 else 200))
    val label = stringResource(
        when (mode) {
            ThemeMode.LIGHT -> R.string.theme_mode_light
            ThemeMode.DARK -> R.string.theme_mode_dark
            ThemeMode.AUTO -> R.string.theme_mode_auto
        },
    )
    Column(
        modifier = modifier
            .testTag(themeTileTag(mode))
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    if (!selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        scope.launch { reveal.reveal(centre, reduced) { onSelect(mode) } }
                    }
                },
            )
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .size(56.dp)
                .onGloballyPositioned { centre = it.positionInWindow() + Offset(it.size.width / 2f, it.size.height / 2f) },
        ) {
            val c = Offset(size.width / 2, size.height / 2)
            when (mode) {
                ThemeMode.LIGHT -> drawSun(c, alpha, phases.rotation.value)
                ThemeMode.DARK -> drawMoon(c, alpha, phases.blink.value)
                ThemeMode.AUTO -> drawEclipse(c, alpha, phases.drift.value, colors.ink)
            }
        }
        Text(
            label,
            style = SeiraType.callout,
            color = if (selected) colors.ink else colors.ink2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun DrawScope.drawSun(c: Offset, alpha: Float, rotation: Float) {
    val unit = 1.dp.toPx()
    rotate(rotation, c) {
        for (ray in 0 until 8) {
            val angle = (2.0 * PI * ray / 8).toFloat()
            val dir = Offset(cos(angle), sin(angle))
            drawLine(SUN_RAY.copy(alpha = alpha), c + dir * (14f * unit), c + dir * (19f * unit), strokeWidth = 2.2f * unit, cap = StrokeCap.Round)
        }
    }
    drawCircle(SUN.copy(alpha = alpha), radius = 10f * unit, center = c)
}

private fun DrawScope.drawMoon(c: Offset, alpha: Float, blink: Float) {
    val unit = 1.dp.toPx()
    val body = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, 13f * unit)) }
    val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(7f * unit, -5f * unit), 11f * unit)) }
    val crescent = Path.combine(PathOperation.Difference, body, bite)
    drawPath(crescent, MOON.copy(alpha = alpha))
    val stars = listOf(Offset(13f, -12f), Offset(17f, 4f), Offset(6f, -19f))
    stars.forEachIndexed { i, offset ->
        val phase = sin(PI.toFloat() * (blink + i / 3f))
        val twinkle = 0.3f + 0.7f * phase * phase
        drawCircle(MOON.copy(alpha = alpha * twinkle), radius = 1.5f * unit, center = c + offset * unit)
    }
}

private fun DrawScope.drawEclipse(c: Offset, alpha: Float, drift: Float, moon: Color) {
    val unit = 1.dp.toPx()
    drawCircle(SUN_RAY.copy(alpha = alpha * 0.45f), radius = 15f * unit, center = c, style = Stroke(1.5f * unit))
    drawCircle(SUN.copy(alpha = alpha), radius = 11f * unit, center = c)
    val shift = 3f + 6f * drift
    drawCircle((if (moon.luminance() > 0.5f) MOON_SHADE else moon).copy(alpha = alpha), radius = 10f * unit, center = c + Offset(shift * unit, -1f * unit))
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
