package bot.seira.companion.character

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

internal object SeiraCharacterMotion {
    const val SWAY_DEGREES = 8f
    const val SWAY_PERIOD_SECONDS = 2.4f
    const val BLINK_SECONDS = 0.15f

    private const val MIN_GAP = 3f
    private const val MAX_GAP = 6f
    private const val CYCLE = 48

    private val blinkStarts: FloatArray
    private val cycleLength: Float

    init {
        var at = 0f
        val starts = FloatArray(CYCLE)
        for (i in 0 until CYCLE) {
            at += MIN_GAP + (MAX_GAP - MIN_GAP) * unit(i)
            starts[i] = at - BLINK_SECONDS
        }
        blinkStarts = starts
        cycleLength = at
    }

    private fun unit(i: Int): Float {
        var h = (i + 1) * -0x61c88647
        h = (h xor (h ushr 16)) * 0x45d9f3b
        h = (h xor (h ushr 16)) * 0x45d9f3b
        h = h xor (h ushr 16)
        return (h ushr 8) / 16777216f
    }

    /** The sway angle in degrees at [seconds]. It is 0 when [sway] is off or motion is reduced. */
    fun swayDegrees(seconds: Float, sway: Boolean, reduceMotion: Boolean): Float =
        if (!sway || reduceMotion) 0f else SWAY_DEGREES * sin(2f * PI.toFloat() * seconds / SWAY_PERIOD_SECONDS)

    /** How closed the eyes are at [seconds], 0 open to 1 shut. It is 0 when motion is reduced. */
    fun blinkAmount(seconds: Float, reduceMotion: Boolean): Float {
        if (reduceMotion || seconds < 0f) return 0f
        val t = seconds - floor(seconds / cycleLength) * cycleLength
        for (start in blinkStarts) {
            val q = (t - start) / BLINK_SECONDS
            if (q in 0f..1f) return sin(q * PI.toFloat())
        }
        return 0f
    }
}
