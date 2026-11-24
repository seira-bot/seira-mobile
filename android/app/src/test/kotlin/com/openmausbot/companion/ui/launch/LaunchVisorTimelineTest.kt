package com.openmausbot.companion.ui.launch

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LaunchVisorTimelineTest {
    private fun assertLid(expected: Float, tMs: Long) =
        assertEquals(expected, LaunchVisorTimeline.lidScale(tMs), 1e-3f, "t=$tMs")

    @Test
    fun `lid follows the system splash keyframes`() {
        assertLid(0.1f, 0)
        assertLid(0.1f, 150)
        assertLid(1.05f, 420)
        assertLid(1.0f, 500)
        assertLid(1.0f, 640)
        assertLid(0.1f, 720)
        assertLid(1.0f, 800)
        assertLid(1.0f, 5000)
    }

    @Test
    fun `fast launch reveals at once`() {
        assertTrue(LaunchVisorTimeline.shouldReveal(0, ready = false, fast = true))
    }

    @Test
    fun `reveal waits for the blink end`() {
        assertFalse(LaunchVisorTimeline.shouldReveal(799, ready = true, fast = false))
        assertTrue(LaunchVisorTimeline.shouldReveal(800, ready = true, fast = false))
    }

    @Test
    fun `reveal waits for restore until the cap`() {
        assertFalse(LaunchVisorTimeline.shouldReveal(1500, ready = false, fast = false))
        assertTrue(LaunchVisorTimeline.shouldReveal(2000, ready = false, fast = false))
    }

    @Test
    fun `reveal helpers start and end in place`() {
        val end = LaunchVisorTimeline.REVEAL_MS
        assertEquals(0f, LaunchVisorTimeline.holeFraction(0), 1e-3f)
        assertEquals(1f, LaunchVisorTimeline.holeFraction(end), 1e-3f)
        assertEquals(1f, LaunchVisorTimeline.faceScale(0), 1e-3f)
        assertEquals(1.25f, LaunchVisorTimeline.faceScale(end), 1e-3f)
        assertEquals(1f, LaunchVisorTimeline.faceAlpha(0), 1e-3f)
        assertEquals(0f, LaunchVisorTimeline.faceAlpha(end), 1e-3f)
        assertEquals(1f, LaunchVisorTimeline.wordmarkAlpha(0), 1e-3f)
        assertEquals(0f, LaunchVisorTimeline.wordmarkAlpha(end), 1e-3f)
    }

    @Test
    fun `splash start in wall-clock time gives the elapsed blink`() {
        val wall = 1_795_000_000_000L
        assertEquals(300L, LaunchVisorTimeline.splashElapsedMs(wall - 300, wall, uptimeNowMs = 90_000))
        assertEquals(800L, LaunchVisorTimeline.splashElapsedMs(wall - 1700, wall, uptimeNowMs = 90_000))
    }

    @Test
    fun `splash start in uptime gives the elapsed blink`() {
        assertEquals(250L, LaunchVisorTimeline.splashElapsedMs(89_750, 1_795_000_000_000L, uptimeNowMs = 90_000))
    }

    @Test
    fun `unknown or future splash start stays in the blink`() {
        assertEquals(800L, LaunchVisorTimeline.splashElapsedMs(0, 1_795_000_000_000L, 90_000))
        assertEquals(0L, LaunchVisorTimeline.splashElapsedMs(90_500, 1_795_000_000_000L, 90_000))
    }

    @Test
    fun `bars take the content style late in the reveal`() {
        assertFalse(LaunchVisorTimeline.uncoversBars(0, reduced = false))
        assertFalse(LaunchVisorTimeline.uncoversBars(200, reduced = false))
        assertTrue(LaunchVisorTimeline.uncoversBars(LaunchVisorTimeline.REVEAL_MS, reduced = false))
        assertFalse(LaunchVisorTimeline.uncoversBars(50, reduced = true))
        assertTrue(LaunchVisorTimeline.uncoversBars(100, reduced = true))
    }

    @Test
    fun `a long frame advances the clock by one step only`() {
        assertEquals(16L, LaunchVisorTimeline.frameStep(16))
        assertEquals(LaunchVisorTimeline.MAX_FRAME_STEP_MS, LaunchVisorTimeline.frameStep(600))
        assertEquals(0L, LaunchVisorTimeline.frameStep(-5))
    }

    @Test
    fun `state start is idempotent`() {
        val state = LaunchVisorState(fast = false, reduceMotion = false, brandingFromSplash = true)
        state.start(120)
        state.start(500, forceFast = true)
        assertEquals(120L, state.startOffsetMs)
        assertFalse(state.forcedFast)
    }
}
