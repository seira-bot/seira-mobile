package com.openmausbot.companion.ui.computers

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.ModelSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputersRulesTest {
    private val laptop = Connection(id = "laptop", name = "Laptop", host = "laptop.local", port = 8810)
    private val studio = Connection(id = "studio", name = "Studio", host = "studio.local", port = 8810)

    private fun bot(id: String) = Bot(
        id = id, threadId = "t-$id", name = id, title = "", description = "",
        notifications = true, color = "red", unread = false,
        modelSelection = ModelSelection("i", "m"), createdAt = 0.0,
    )

    @Test
    fun `the current computer is first and marked`() {
        val entries = ComputersRules.entries(listOf(laptop, studio), "studio")
        assertEquals(listOf("studio", "laptop"), entries.map { it.connection.id })
        assertEquals(listOf(true, false), entries.map { it.current })
    }

    @Test
    fun `no active computer marks none`() {
        assertTrue(ComputersRules.entries(listOf(laptop, studio), null).none { it.current })
    }

    @Test
    fun `computers with one name carry their address`() {
        val twin = studio.copy(name = "Laptop")
        val entries = ComputersRules.entries(listOf(laptop, twin), "laptop")
        assertEquals(laptop.displayAddress, entries.first().distinguishingAddress)
        assertNull(ComputersRules.entries(listOf(laptop, studio), "laptop").first().distinguishingAddress)
    }

    @Test
    fun `only visible cloud bots get a screen and the state follows what they do`() {
        val working = bot("working").copy(computer = "cloud", busy = true)
        val waiting = bot("waiting").copy(computer = "cloud", waitingOnTeammate = true)
        val idle = bot("idle").copy(computer = "cloud")
        val hidden = bot("hidden").copy(computer = "cloud", hidden = true)
        val local = bot("local").copy(computer = "local", busy = true)
        val none = bot("none")

        val screens = ComputersRules.botScreens(listOf(working, waiting, idle, hidden, local, none))

        assertEquals(listOf("working", "waiting", "idle"), screens.map { it.bot.id })
        assertEquals(
            listOf(BotScreenState.WORKING, BotScreenState.WAITING, BotScreenState.IDLE),
            screens.map { it.state },
        )
    }

    @Test
    fun `a busy bot that also waits is working`() {
        val both = bot("both").copy(computer = "cloud", busy = true, waitingOnTeammate = true)
        assertEquals(BotScreenState.WORKING, ComputersRules.botScreens(listOf(both)).single().state)
    }

    @Test
    fun `AI usage shows only with a page, a paired computer and no hidden mark`() {
        assertTrue(ComputersRules.showsAiUsage(true, "laptop", null))
        assertTrue(ComputersRules.showsAiUsage(true, "laptop", "studio"))
        assertEquals(false, ComputersRules.showsAiUsage(true, "laptop", "laptop"))
        assertEquals(false, ComputersRules.showsAiUsage(true, null, null))
        assertEquals(false, ComputersRules.showsAiUsage(false, "laptop", null))
    }
}
