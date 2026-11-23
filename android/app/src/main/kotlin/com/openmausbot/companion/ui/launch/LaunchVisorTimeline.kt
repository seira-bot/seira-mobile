package com.openmausbot.companion.ui.launch

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing

/**
 * Timing of the launch overlay. The eye keyframes match
 * `ic_seira_splash_animated.xml`, so the system splash and the overlay show the
 * same blink.
 */
object LaunchVisorTimeline {
    const val REVEAL_MS = 450L
    const val REDUCED_FADE_MS = 200L
    const val BLINK_END_MS = 800L
    const val WAIT_CAP_MS = 2000L
    const val FAST_LID_CAP_MS = 500L
    const val WORDMARK_FADE_IN_MS = 300L

    private const val FACE_MS = 250L
    private const val WORDMARK_OUT_MS = 200L
    private const val LID_CLOSED = 0.1f
    private const val LID_OVERSHOOT = 1.05f
    private const val FACE_ZOOM = 0.25f

    private val holeEasing = CubicBezierEasing(0.55f, 0f, 0.2f, 1f)

    fun lidScale(tMs: Long): Float = when {
        tMs <= 150 -> LID_CLOSED
        tMs < 420 -> lerp(LID_CLOSED, LID_OVERSHOOT, decelerateCubic(fraction(tMs, 150, 420)))
        tMs < 500 -> lerp(LID_OVERSHOOT, 1f, FastOutSlowInEasing.transform(fraction(tMs, 420, 500)))
        tMs <= 640 -> 1f
        tMs < 720 -> lerp(1f, LID_CLOSED, fraction(tMs, 640, 720).let { it * it })
        tMs < BLINK_END_MS -> lerp(LID_CLOSED, 1f, decelerateQuad(fraction(tMs, 720, 800)))
        else -> 1f
    }

    fun shouldReveal(tMs: Long, ready: Boolean, fast: Boolean): Boolean =
        fast || (tMs >= BLINK_END_MS && (ready || tMs >= WAIT_CAP_MS))

    fun holeFraction(revealMs: Long): Float =
        holeEasing.transform(fraction(revealMs, 0, REVEAL_MS))

    fun faceScale(revealMs: Long): Float =
        1f + FACE_ZOOM * FastOutSlowInEasing.transform(fraction(revealMs, 0, FACE_MS))

    fun faceAlpha(revealMs: Long): Float =
        1f - FastOutSlowInEasing.transform(fraction(revealMs, 0, FACE_MS))

    fun wordmarkAlpha(revealMs: Long): Float = 1f - fraction(revealMs, 0, WORDMARK_OUT_MS)

    private fun fraction(t: Long, from: Long, to: Long): Float =
        ((t - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

    private fun decelerateCubic(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

    private fun decelerateQuad(x: Float): Float = 1f - (1f - x) * (1f - x)
}
