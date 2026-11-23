package com.openmausbot.companion.ui.launch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.R
import kotlin.math.hypot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

private const val START_FALLBACK_MS = 1000L
private const val FALLBACK_OFFSET_MS = 500L
private const val NOT_REVEALING = -1L

// Lid pivot as a fraction of the 288dp eye box; shared with ic_seira_splash.xml.
private val LidOrigin = TransformOrigin(0.5f, 0.52125f)

/**
 * Night visor launch overlay. It repeats the system splash frame, plays the
 * blink, then opens a circular hole from the eyes to show the content below.
 *
 * Time comes from frame callbacks, not from animation specs, so the animator
 * duration scale cannot skip the sequence. One frame advances the clock by at
 * most [LaunchVisorTimeline.MAX_FRAME_STEP_MS]: the first screen composes on
 * the frame the reveal starts, and a long frame there would skip the reveal.
 */
@Composable
fun LaunchVisor(state: LaunchVisorState, ready: Boolean, onFinished: () -> Unit) {
    val readyNow by rememberUpdatedState(ready)
    val finishedNow by rememberUpdatedState(onFinished)
    var sinceStartMs by remember { mutableLongStateOf(0L) }
    var revealMs by remember { mutableLongStateOf(NOT_REVEALING) }
    val reduced = state.reduceMotion

    LaunchedEffect(state) {
        delay(START_FALLBACK_MS)
        state.start(FALLBACK_OFFSET_MS, forceFast = true)
    }

    LaunchedEffect(state) {
        val startOffset = snapshotFlow { state.startOffsetMs }.filterNotNull().first()
        val fast = state.fast || state.forcedFast
        var frame = withFrameMillis { it }
        var clock = 0L
        var revealClock = NOT_REVEALING
        while (true) {
            sinceStartMs = clock
            val t = startOffset + clock
            if (revealClock == NOT_REVEALING) {
                val go = if (reduced) {
                    fast || readyNow || t >= LaunchVisorTimeline.WAIT_CAP_MS
                } else {
                    LaunchVisorTimeline.shouldReveal(t, readyNow, fast)
                }
                if (go) revealClock = clock
            }
            if (revealClock != NOT_REVEALING) {
                revealMs = clock - revealClock
                val total = if (reduced) {
                    LaunchVisorTimeline.REDUCED_FADE_MS
                } else {
                    LaunchVisorTimeline.REVEAL_MS
                }
                if (!state.barsOverContent && LaunchVisorTimeline.uncoversBars(revealMs, reduced)) {
                    state.uncoverBars()
                }
                if (revealMs >= total) break
            }
            val next = withFrameMillis { it }
            clock += LaunchVisorTimeline.frameStep(next - frame)
            frame = next
        }
        finishedNow()
    }

    val ink = colorResource(R.color.seira_visor_ink)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .graphicsLayer {
                alpha = if (reduced && revealMs >= 0) {
                    1f - (revealMs.toFloat() / LaunchVisorTimeline.REDUCED_FADE_MS).coerceIn(0f, 1f)
                } else {
                    1f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fraction = if (reduced || revealMs < 0) 0f else LaunchVisorTimeline.holeFraction(revealMs)
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                val radius = fraction * hypot(size.width / 2f, size.height / 2f)
                if (radius > 0f) {
                    addOval(Rect(center = center, radius = radius))
                }
            }
            drawPath(path, ink)
        }

        Box(
            modifier = Modifier.graphicsLayer {
                if (!reduced && revealMs >= 0) {
                    val scale = LaunchVisorTimeline.faceScale(revealMs)
                    scaleX = scale
                    scaleY = scale
                    alpha = LaunchVisorTimeline.faceAlpha(revealMs)
                }
            },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_seira_splash),
                contentDescription = null,
                modifier = Modifier
                    .size(288.dp)
                    .graphicsLayer {
                        val startOffset = state.startOffsetMs
                        scaleY = when {
                            reduced || startOffset == null -> 1f
                            state.fast || state.forcedFast -> LaunchVisorTimeline.lidScale(
                                minOf(startOffset + sinceStartMs, LaunchVisorTimeline.FAST_LID_CAP_MS),
                            )
                            else -> LaunchVisorTimeline.lidScale(startOffset + sinceStartMs)
                        }
                        transformOrigin = LidOrigin
                    },
            )
        }

        Image(
            painter = painterResource(R.drawable.seira_splash_branding),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .size(width = 200.dp, height = 80.dp)
                .graphicsLayer {
                    val base = when {
                        state.brandingFromSplash -> 1f
                        !state.started -> 0f
                        else -> (sinceStartMs.toFloat() / LaunchVisorTimeline.WORDMARK_FADE_IN_MS)
                            .coerceIn(0f, 1f)
                    }
                    val out = if (!reduced && revealMs >= 0) {
                        LaunchVisorTimeline.wordmarkAlpha(revealMs)
                    } else {
                        1f
                    }
                    alpha = base * out
                },
        )
    }
}
