package com.openmausbot.companion.character

import com.openmausbot.companion.ui.CharacterMood
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CarvedPoseTest {
    private val seira = CarvedLook.bodyOf("seira")

    @Test
    fun restPoseMatchesTheDesktopFormula() {
        val g = carvedGeometry(seira, REST_POSE)
        assertEquals(0.0, g.lean, 0.001)
        assertEquals(1.0, g.scaleX, 0.001)
        assertEquals(1.0, g.scaleY, 0.001)
        val (left, right) = g.eyes
        assertEquals(73.0, left.x, 0.01)
        assertEquals(87.0, left.y, 0.01)
        assertEquals(20.0, left.width, 0.01)
        assertEquals(38.0, left.height, 0.01)
        assertEquals(10.0, left.radius, 0.01)
        assertEquals(147.0, right.x, 0.01)
        assertEquals(125.02, g.smile.startY, 0.01)
        assertEquals(102.98, g.smile.startX, 0.01)
        assertEquals(137.02, g.smile.endX, 0.01)
        assertEquals(136.51, g.smile.controlY, 0.01)
        assertEquals(1.0, g.smile.opacity, 0.001)
        assertEquals(0.0, g.open.opacity, 0.001)
    }

    @Test
    fun sleepingShutsTheEyes() {
        val rest = carvedGeometry(seira, REST_POSE).eyes[0].height
        val asleep = carvedGeometry(seira, moodPose(CharacterMood.SLEEPING)).eyes[0].height
        assertTrue(asleep < rest * 0.2, "asleep $asleep, rest $rest")
    }

    @Test
    fun surprisedOpensTheMouthAndStretchesTheBody() {
        val g = carvedGeometry(seira, moodPose(CharacterMood.SURPRISED))
        assertEquals(1.0, g.open.opacity, 0.001)
        assertTrue(g.open.rx > 0 && g.open.ry > 0)
        assertEquals(0.0, g.smile.opacity, 0.001)
        assertEquals(0.94, g.scaleX, 0.001)
        assertEquals(1.09, g.scaleY, 0.001)
        assertEquals(32.0, g.eyes[0].width, 0.01)
    }

    @Test
    fun theLidCurveClosesThenOpens() {
        assertEquals(0.0, blinkLid(0.0), 0.001)
        assertEquals(1.0, blinkLid(90.0), 0.001)
        assertEquals(0.0, blinkLid(200.0), 0.001)
        assertTrue(blinkLid(45.0) in 0.4..0.6)
    }

    @Test
    fun accessoriesKeepOnePerSlotAndAtMostThree() {
        val worn = CarvedLook.worn(listOf("glasses", "sunglasses", "earring", "cursor", "cap", "bowtie", "scarf")).map { it.id }
        assertEquals(listOf("glasses", "earring", "cap"), worn)
    }

    @Test
    fun unknownHairIsSeira() {
        assertEquals("seira", CarvedLook.bodyOf("cursor").id)
        assertEquals("seira", CarvedLook.bodyOf(null).id)
    }

    @Test
    fun anIdleFaceSettlesAndWaitsForTheNextBlink() {
        val animator = CarvedAnimator(CharacterMood.IDLE, Random(7))
        var now = 0L
        var next = animator.step(now)
        repeat(200) {
            now = next
            next = animator.step(now)
        }
        assertTrue(next >= now)
        assertTrue(animator.pose.shut in 0.0..1.0)
    }
}
