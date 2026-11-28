package bot.seira.companion.ui

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import bot.seira.companion.ui.theme.SeiraMotion
import bot.seira.companion.ui.theme.seiraTween
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class MotionSpecTest {
    @Test
    fun `reduced motion snaps`() {
        assertTrue(seiraTween<Float>(SeiraMotion.BASE, reduced = true) is SnapSpec<Float>)
    }

    @Test
    fun `normal motion tweens with the design duration`() {
        val spec = seiraTween<Float>(SeiraMotion.SHEET, reduced = false)
        assertTrue(spec is TweenSpec<Float>)
        assertEquals(SeiraMotion.SHEET, (spec as TweenSpec<Float>).durationMillis)
        assertEquals(SeiraMotion.Ease, spec.easing)
    }

    @Test
    fun `durations stay between 120 and 300 ms`() {
        listOf(SeiraMotion.FAST, SeiraMotion.BASE, SeiraMotion.SHEET, SeiraMotion.SLOW)
            .forEach { assertTrue(it in 120..300, "$it") }
    }
}
