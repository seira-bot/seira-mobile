package bot.seira.companion.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** The colour of a bot's name over its reply: its identity, pulled until the words can be read. */
internal object ChatNames {
    const val MINIMUM_CONTRAST = 4.5f

    fun contrast(a: Color, b: Color): Float {
        val la = a.compositeOver(b).luminance()
        val lb = b.luminance()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /**
     * Mixes [color] toward black on a light [background] and toward white on a
     * dark one, in small steps, and stops at the first that reads at [ratio].
     */
    fun readableOn(color: Color, background: Color, ratio: Float = MINIMUM_CONTRAST): Color {
        val opaque = color.copy(alpha = 1f)
        if (contrast(opaque, background) >= ratio) return opaque
        val toward = if (background.luminance() > 0.5f) Color.Black else Color.White
        for (step in 1..20) {
            val mixed = androidx.compose.ui.graphics.lerp(opaque, toward, step / 20f)
            if (contrast(mixed, background) >= ratio) return mixed
        }
        return toward
    }
}
