package bot.seira.companion.ui

import bot.seira.companion.core.Bot
import bot.seira.companion.core.BotLookPatch
import bot.seira.companion.core.ModelSelection
import bot.seira.companion.core.SeiraCastMember
import bot.seira.companion.core.SeiraLook
import androidx.compose.runtime.saveable.SaverScope
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ChangeLookStateTest {
    private val cast = listOf(
        SeiraCastMember("bob", "Dina Pratama", "bob", listOf("glasses", "earring"), "blue"),
        SeiraCastMember("seira", "Seira", "seira", emptyList(), "red"),
        SeiraCastMember("spiky", "Spiky", "spiky", listOf("cap"), "orange"),
    )

    private fun state(original: SeiraLook = SeiraLook.SEIRA) = ChangeLookState(original, cast, Random(1))

    private val saved = Bot(
        id = "b1", threadId = "t1", name = "Dina Pratama", title = "", description = "",
        notifications = true, color = "red", unread = false,
        modelSelection = ModelSelection("i", "m"), createdAt = 0.0,
    )

    @Test
    fun `a pick in a taken slot replaces the extra`() {
        val state = state()
        state.toggleExtra("glasses")
        state.toggleExtra("sunglasses")
        assertEquals(listOf("sunglasses"), state.look.accessories)
    }

    @Test
    fun `a 4th pick drops the oldest extra and the count stays 3`() {
        val state = state()
        listOf("glasses", "ribbon", "cap").forEach(state::toggleExtra)
        assertEquals(3, state.look.accessories.size)
        state.toggleExtra("scarf")
        assertEquals(listOf("ribbon", "cap", "scarf"), state.look.accessories)
    }

    @Test
    fun `a pick on an extra that is on takes it off`() {
        val state = state()
        state.toggleExtra("glasses")
        state.toggleExtra("glasses")
        assertEquals(emptyList(), state.look.accessories)
        assertFalse(state.changed)
    }

    @Test
    fun `save is off while nothing changed, and off again after the change is undone`() {
        val state = state()
        assertFalse(state.canSave(connected = true))
        state.setHair("bob")
        assertTrue(state.canSave(connected = true))
        state.setHair("seira")
        assertFalse(state.canSave(connected = true))
    }

    @Test
    fun `save is off while not connected`() {
        val state = state()
        state.setColor("blue")
        assertFalse(state.canSave(connected = false))
    }

    @Test
    fun `the patch has only the changed fields`() {
        val state = state()
        state.setColor("blue")
        assertEquals(BotLookPatch(color = "blue"), state.patch())
        state.setHair("bob")
        assertEquals(BotLookPatch(color = "blue", mascotBody = "bob"), state.patch())
    }

    @Test
    fun `the Seira tab has Hair, Face, Extras and Colour and no Characters list`() {
        assertEquals(
            listOf("HAIR", "FACE", "EXTRAS", "COLOUR"),
            ChangeLookCategory.entries.map { it.name },
        )
        assertEquals(ChangeLookCategory.HAIR, state().category)
    }

    @Test
    fun `save is on for a bot that shows a picture even when the look did not change`() {
        val state = state()
        assertFalse(state.canSave(connected = true, showsPicture = false))
        assertTrue(state.canSave(connected = true, showsPicture = true))
        assertFalse(state.canSave(connected = false, showsPicture = true))
    }

    @Test
    fun `a save for a bot that shows a picture sends the look and then switches to the mascot`() = runBlocking {
        val state = state()
        state.setColor("blue")
        val calls = mutableListOf<String>()
        val ok = state.save(
            showsPicture = true,
            showMascot = { calls += "mascot"; saved },
            update = { patch -> calls += "look:" + patch.color; saved },
        )
        assertTrue(ok)
        assertEquals(listOf("look:blue", "mascot"), calls)
    }

    @Test
    fun `a save with no look change for a bot that shows a picture only switches to the mascot`() = runBlocking {
        val state = state()
        val calls = mutableListOf<String>()
        assertTrue(
            state.save(
                showsPicture = true,
                showMascot = { calls += "mascot"; saved },
                update = { calls += "look"; saved },
            ),
        )
        assertEquals(listOf("mascot"), calls)
    }

    @Test
    fun `a bot that shows the mascot is not switched`() = runBlocking {
        val state = state()
        state.setColor("blue")
        var switched = 0
        assertTrue(state.save(showsPicture = false, showMascot = { switched++; saved }, update = { saved }))
        assertEquals(0, switched)
    }

    @Test
    fun `a failed switch to the mascot is a failed save`() = runBlocking {
        val state = state()
        assertFalse(state.save(showsPicture = true, showMascot = { null }, update = { saved }))
        assertTrue(state.saveFailed)
    }

    @Test
    fun `the cast lists Seira first`() {
        assertEquals(listOf("seira", "bob", "spiky"), state().cast.map { it.id })
    }

    @Test
    fun `calm clears the expression unless the bot already had idle`() {
        val state = state()
        state.setExpression("happy")
        assertEquals(BotLookPatch(mascotExpression = BotLookPatch.Change.Set("happy")), state.patch())
        state.setExpression("idle")
        assertNull(state.look.expression)
        assertTrue(state.isExpressionSelected("idle"))
        assertTrue(state.patch().isEmpty)

        val idle = state(SeiraLook.of("seira", "red", expression = "idle"))
        idle.setExpression("happy")
        idle.setExpression("idle")
        assertEquals("idle", idle.look.expression)
        assertFalse(idle.changed)
    }

    @Test
    fun `surprise gives another look from the cast`() {
        val state = state()
        repeat(20) {
            val before = state.look
            state.surprise()
            assertTrue(state.look != before)
            assertTrue(cast.any { it.look == state.look })
        }
    }

    @Test
    fun `a failed save keeps the look and says so until the next change`() = runBlocking {
        val state = state()
        state.setColor("blue")
        assertFalse(state.save { null })
        assertTrue(state.saveFailed)
        assertFalse(state.saving)
        assertEquals("blue", state.look.color)
        state.setColor("pink")
        assertFalse(state.saveFailed)
    }

    @Test
    fun `a good save sends the patch once`() = runBlocking {
        val state = state()
        state.setColor("blue")
        val sent = mutableListOf<BotLookPatch>()
        assertTrue(state.save { sent += it; saved })
        assertEquals(listOf(BotLookPatch(color = "blue")), sent)
        assertFalse(state.saveFailed)
        assertFalse(state.saving)
    }

    private fun roundTrip(state: ChangeLookState): ChangeLookState {
        val saver = ChangeLookState.saver(cast)
        val saved = with(saver) { SaverScope { true }.save(state) }
        return requireNotNull(saver.restore(requireNotNull(saved)))
    }

    @Test
    fun `the saver keeps the original, the look, the tab and the failed flag`() {
        val state = ChangeLookState(SeiraLook.from(saved), cast, Random(1))
        state.setHair("bob")
        state.setColor("blue")
        state.setExpression("happy")
        state.toggleExtra("glasses")
        state.category = ChangeLookCategory.FACE
        runBlocking { state.save { null } }
        val back = roundTrip(state)
        assertEquals(state.original, back.original)
        assertEquals(state.look, back.look)
        assertEquals(ChangeLookCategory.FACE, back.category)
        assertEquals(state.changed, back.changed)
        assertEquals(state.patch(), back.patch())
        assertTrue(back.saveFailed)
        assertFalse(back.saving)
    }

    @Test
    fun `the saver restores a tab name it does not know as Hair`() {
        val state = ChangeLookState(SeiraLook.from(saved), cast, Random(1))
        val saver = ChangeLookState.saver(cast)
        val stored = (requireNotNull(with(saver) { SaverScope { true }.save(state) }) as List<*>).toMutableList()
        stored[10] = "CHARACTERS"
        val back = requireNotNull(saver.restore(stored))
        assertEquals(ChangeLookCategory.HAIR, back.category)
    }

    @Test
    fun `the saver keeps a look without a face ink, an expression or extras`() {
        val bare = SeiraLook(hair = "bob", color = "teal", faceInk = null, expression = null, accessories = emptyList())
        val state = ChangeLookState(bare, cast, Random(1))
        val back = roundTrip(state)
        assertEquals(bare, back.original)
        assertEquals(bare, back.look)
        assertNull(back.look.faceInk)
        assertNull(back.look.expression)
        assertEquals(emptyList(), back.look.accessories)
        assertFalse(back.changed)
        assertFalse(back.saveFailed)
    }
}
