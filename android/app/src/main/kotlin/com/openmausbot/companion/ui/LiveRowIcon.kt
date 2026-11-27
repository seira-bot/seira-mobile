package com.openmausbot.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.SeiraIcons
import com.openmausbot.companion.ui.theme.SeiraMotion
import com.openmausbot.companion.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal enum class LiveIconKind(val millis: Int) {
    BELL(600),
    CHECK(520),
    WARNING(600),
    OTHER(300),
}

internal object LiveRowIconRules {
    fun kindOf(icon: ImageVector): LiveIconKind = when (icon) {
        SeiraIcons.Bell -> LiveIconKind.BELL
        SeiraIcons.Check -> LiveIconKind.CHECK
        SeiraIcons.Warning -> LiveIconKind.WARNING
        else -> LiveIconKind.OTHER
    }

    /** The bell turns by up to 14 degrees each way while it rings. */
    fun bellAngle(t: Float): Float = 14f * sin(4f * PI.toFloat() * t) * (1f - t)

    /** One beat: 1, then 1 plus [peak], then 1 again. */
    fun pulse(t: Float, peak: Float): Float = 1f + peak * sin(PI.toFloat() * t.coerceIn(0f, 1f))
}

private const val DOTS = 8

/** A row icon that plays its own effect when the row switches on or off, and never on first composition. */
@Composable
internal fun LiveRowIcon(icon: ImageVector, on: Boolean, modifier: Modifier = Modifier) {
    val colors = Seira.colors
    val reduced = rememberReducedMotion()
    val kind = LiveRowIconRules.kindOf(icon)
    val tint by animateColorAsState(
        if (on) colors.ink2 else colors.ink3,
        if (reduced) tween(0) else tween(SeiraMotion.BASE),
        label = "liveIconTint",
    )
    val effect = remember { Animatable(0f) }
    val strike = remember { Animatable(if (on) 0f else 1f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(on) {
        val strikeTarget = if (on) 0f else 1f
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        if (reduced) {
            strike.snapTo(strikeTarget)
            return@LaunchedEffect
        }
        coroutineScope {
            if (kind == LiveIconKind.BELL) launch { strike.animateTo(strikeTarget, tween(300, easing = SeiraMotion.Ease)) }
            if (on) {
                launch {
                    effect.snapTo(0f)
                    effect.animateTo(1f, tween(kind.millis, easing = LinearEasing))
                    effect.snapTo(0f)
                }
            }
        }
    }
    val t = effect.value
    Box(
        modifier = modifier
            .size(20.dp)
            .drawWithContent {
                drawContent()
                val unit = 1.dp.toPx()
                val center = Offset(size.width / 2, size.height / 2)
                if (kind == LiveIconKind.BELL) {
                    if (strike.value > 0f) {
                        val from = Offset(3f * unit, 3f * unit)
                        val to = Offset(17f * unit, 17f * unit)
                        drawLine(tint, from, from + (to - from) * strike.value, strokeWidth = 1.85f * unit, cap = StrokeCap.Round)
                    }
                    if (t > 0f && t < 1f) {
                        for (ring in 0..1) {
                            val radius = (12f + 3f * ring) * unit
                            val alpha = (1f - t) * (1f - 0.35f * ring)
                            for (side in listOf(-1f, 1f)) {
                                drawArc(
                                    color = colors.ink2.copy(alpha = alpha),
                                    startAngle = if (side > 0) -30f else 150f,
                                    sweepAngle = 60f,
                                    useCenter = false,
                                    topLeft = center - Offset(radius, radius),
                                    size = Size(radius * 2, radius * 2),
                                    style = Stroke(width = 1.5f * unit, cap = StrokeCap.Round),
                                )
                            }
                        }
                    }
                }
                if (kind == LiveIconKind.CHECK && t > 0f && t < 1f) {
                    for (dot in 0 until DOTS) {
                        val angle = (2.0 * PI * dot / DOTS).toFloat()
                        val reach = (if (dot % 2 == 0) 17f else 22f) * unit
                        val distance = 9f * unit + (reach - 9f * unit) * t
                        drawCircle(
                            colors.success.copy(alpha = 1f - t),
                            radius = 1.6f * unit,
                            center = center + Offset(cos(angle) * distance, sin(angle) * distance),
                        )
                    }
                }
            },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    when (kind) {
                        LiveIconKind.BELL -> {
                            transformOrigin = TransformOrigin(0.5f, 0.1f)
                            rotationZ = if (t > 0f) LiveRowIconRules.bellAngle(t) else 0f
                        }
                        LiveIconKind.WARNING -> LiveRowIconRules.pulse(t, 0.18f).let { scaleX = it; scaleY = it }
                        LiveIconKind.OTHER -> LiveRowIconRules.pulse(t, 0.12f).let { scaleX = it; scaleY = it }
                        LiveIconKind.CHECK -> Unit
                    }
                },
        )
    }
}
