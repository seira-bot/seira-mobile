package bot.seira.companion.character

import bot.seira.companion.ui.CharacterMood
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeiraFaceTest {
    @Test
    fun `no ink is the brand face`() {
        val ink = seiraFaceInk(null)
        assertEquals(0xFF0C0F14.toInt(), ink.screen)
        assertEquals(0xFFFF2A57.toInt(), ink.feature)
        assertFalse(ink.lightScreen)
    }

    @Test
    fun `white ink is a light face on the dark screen`() {
        val ink = seiraFaceInk("white")
        assertEquals(0xFF0C0F14.toInt(), ink.screen)
        assertEquals(0xFFF2F2F3.toInt(), ink.feature)
        assertFalse(ink.lightScreen)
    }

    @Test
    fun `black ink is a dark face on a light screen`() {
        val ink = seiraFaceInk("black")
        assertEquals(0xFF0C0F14.toInt(), ink.feature)
        assertTrue(ink.lightScreen)
        assertTrue((ink.screen and 0xFF) > 0xC0)
    }

    @Test
    fun `an unknown ink is the brand face`() {
        assertEquals(seiraFaceInk(null), seiraFaceInk("green"))
    }

    @Test
    fun `every mood has a face that exists`() {
        for (mood in CharacterMood.entries) {
            val name = seiraExpressionFor(mood)
            assertTrue(name in SEIRA_EXPRESSIONS, "${mood.id} maps to missing face $name")
        }
    }

    @Test
    fun `moods map to the agreed faces`() {
        val expected = mapOf(
            CharacterMood.HAPPY to "happy", CharacterMood.PROUD to "proud", CharacterMood.CURIOUS to "thinking",
            CharacterMood.SHY to "shy", CharacterMood.DROWSY to "sleepy", CharacterMood.BORED to "bored",
            CharacterMood.PLAYFUL to "wink", CharacterMood.SURPRISED to "surprised", CharacterMood.SAD to "sad",
            CharacterMood.LAUGHING to "laugh", CharacterMood.CONFUSED to "confused", CharacterMood.ANGRY to "angry",
            CharacterMood.SCARED to "scared", CharacterMood.SLEEPING to "asleep", CharacterMood.EXCITED to "happy",
            CharacterMood.THINKING to "thinking", CharacterMood.WORKING to "determined",
            CharacterMood.LISTENING to "calm", CharacterMood.IDLE to "calm", CharacterMood.SPAWNING to "calm",
        )
        for ((mood, face) in expected) assertEquals(face, seiraExpressionFor(mood), mood.id)
    }
}
