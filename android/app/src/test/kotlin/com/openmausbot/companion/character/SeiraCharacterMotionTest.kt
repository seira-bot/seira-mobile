package com.openmausbot.companion.character

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeiraCharacterMotionTest {
    private val times = (0..4000).map { it * 0.0371f }

    @Test
    fun `reduced motion keeps the body still and the eyes open`() {
        for (t in times) {
            assertEquals(0f, SeiraCharacterMotion.swayDegrees(t, sway = true, reduceMotion = true))
            assertEquals(0f, SeiraCharacterMotion.blinkAmount(t, reduceMotion = true))
        }
    }

    @Test
    fun `sway is off unless it is asked for`() {
        for (t in times) assertEquals(0f, SeiraCharacterMotion.swayDegrees(t, sway = false, reduceMotion = false))
    }

    @Test
    fun `sway stays within eight degrees and reaches them`() {
        val peak = times.maxOf { abs(SeiraCharacterMotion.swayDegrees(it, sway = true, reduceMotion = false)) }
        assertTrue(peak <= 8f + 1e-4f, "peak $peak")
        assertTrue(peak > 7.9f, "peak $peak")
    }

    @Test
    fun `sway repeats every 2 point 4 seconds`() {
        for (t in listOf(0.1f, 0.7f, 1.3f, 5.55f)) {
            val a = SeiraCharacterMotion.swayDegrees(t, sway = true, reduceMotion = false)
            val b = SeiraCharacterMotion.swayDegrees(t + 2.4f, sway = true, reduceMotion = false)
            assertEquals(a, b, 1e-3f)
        }
        assertEquals(0f, SeiraCharacterMotion.swayDegrees(1.2f, sway = true, reduceMotion = false), 1e-3f)
    }

    @Test
    fun `blinks come three to six seconds apart`() {
        val step = 0.01f
        val starts = ArrayList<Float>()
        var wasShut = false
        var t = 0f
        while (t < 120f) {
            val shut = SeiraCharacterMotion.blinkAmount(t, reduceMotion = false) > 0.01f
            if (shut && !wasShut) starts.add(t)
            wasShut = shut
            t += step
        }
        assertTrue(starts.size >= 20, "blinks ${starts.size}")
        for (i in 1 until starts.size) {
            val gap = starts[i] - starts[i - 1]
            assertTrue(gap in 2.9f..6.1f, "gap $gap")
        }
        assertTrue(times.all { SeiraCharacterMotion.blinkAmount(it, reduceMotion = false) in 0f..1f })
    }
}
