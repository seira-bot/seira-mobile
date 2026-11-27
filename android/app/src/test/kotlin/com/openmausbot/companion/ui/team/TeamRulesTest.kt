package com.openmausbot.companion.ui.team

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.TeamHandoff
import com.openmausbot.companion.core.TeamMap
import com.openmausbot.companion.core.TeamMemoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamRulesTest {
    private fun bot(id: String, section: String? = null, chief: Boolean? = null) = Bot(
        id = id,
        threadId = "t-$id",
        name = id,
        title = "",
        description = "",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("i", "m"),
        createdAt = 0.0,
        chiefOfStaff = chief,
        section = section,
    )

    private fun handoff(target: String) = TeamHandoff(sourceBotId = "x", targetBotId = target, threadId = "t")

    private fun entry(id: String, status: String = "accepted", revision: String? = "1") =
        TeamMemoryEntry(id = id, kind = "term", name = id, status = status, revision = revision)

    @Test
    fun `layout puts the chief first, then departments, then general`() {
        val state = CompanionState(
            bots = listOf(
                bot("chief", chief = true),
                bot("a", section = "Sales"),
                bot("b"),
                bot("lead", section = "Sales", chief = true),
            ),
        )
        val layout = TeamMapRules.layout(state)
        assertEquals("chief", layout.chief?.id)
        assertEquals(listOf("Sales", null), layout.groups.map { it.name })
        assertEquals(listOf("lead", "a"), layout.groups[0].bots.map { it.id })
        assertEquals(listOf("b"), layout.groups[1].bots.map { it.id })
    }

    @Test
    fun `a department the computer lists shows with no bot`() {
        val state = CompanionState(bots = listOf(bot("a", section = "Sales")), declaredSections = listOf("Sales", "Empty"))
        val layout = TeamMapRules.layout(state)
        assertEquals(listOf("Sales", "Empty"), layout.groups.map { it.name })
        assertTrue(layout.groups[1].bots.isEmpty())
    }

    @Test
    fun `an empty fleet makes an empty layout`() {
        assertTrue(TeamMapRules.layout(CompanionState()).isEmpty)
    }

    @Test
    fun `status reads running before queued`() {
        val map = TeamMap(running = listOf(handoff("a")), queued = listOf(handoff("a"), handoff("b")))
        assertEquals(TeamStatus.WORKING, TeamMapRules.status(map, "a"))
        assertEquals(TeamStatus.WAITING, TeamMapRules.status(map, "b"))
        assertNull(TeamMapRules.status(map, "c"))
        assertNull(TeamMapRules.status(null, "a"))
    }

    @Test
    fun `memory sections start with general`() {
        val state = CompanionState(bots = listOf(bot("a", section = "Sales")))
        assertEquals(listOf("", "Sales"), TeamMemoryRules.sections(state))
    }

    @Test
    fun `split makes the first line the note and the rest its detail`() {
        assertEquals("Ana" to "Runs finance\nOn Tuesdays", TeamMemoryRules.split("  Ana\n\nRuns finance\nOn Tuesdays "))
        assertEquals("Ana" to "", TeamMemoryRules.split("Ana"))
        assertNull(TeamMemoryRules.split("  \n "))
    }

    @Test
    fun `split cuts a long first line and keeps the overflow as detail`() {
        val (name, detail) = TeamMemoryRules.split("x".repeat(TeamMemoryRules.NAME_LIMIT + 5))!!
        assertEquals(TeamMemoryRules.NAME_LIMIT, name.length)
        assertEquals("xxxxx", detail)
        val (_, long) = TeamMemoryRules.split("n\n" + "y".repeat(TeamMemoryRules.DETAIL_LIMIT + 50))!!
        assertEquals(TeamMemoryRules.DETAIL_LIMIT, long.length)
    }

    @Test
    fun `accepted and proposed split the list`() {
        val list = listOf(entry("a"), entry("b", "proposed"), entry("c", "superseded"))
        assertEquals(listOf("a"), TeamMemoryRules.accepted(list).map { it.id })
        assertEquals(listOf("b"), TeamMemoryRules.proposed(list).map { it.id })
    }

    @Test
    fun `an entry changed when it is gone or has another revision`() {
        val mine = entry("a", revision = "1")
        assertFalse(TeamMemoryRules.changedSince(mine, listOf(entry("a", revision = "1"))))
        assertTrue(TeamMemoryRules.changedSince(mine, listOf(entry("a", revision = "2"))))
        assertTrue(TeamMemoryRules.changedSince(mine, emptyList()))
    }

    @Test
    fun `a pinned bot stays in its department`() {
        val state = CompanionState(
            bots = listOf(bot("lead", section = "Ops", chief = true), bot("p", section = "Ops").copy(pinned = true)),
        )
        val layout = TeamMapRules.layout(state)
        assertEquals(listOf("Ops"), layout.groups.map { it.name })
        assertEquals(listOf("p"), layout.groups[0].bots.map { it.id })
    }

    @Test
    fun `a pinned bot without a department joins the general group`() {
        val state = CompanionState(bots = listOf(bot("chief", chief = true), bot("p").copy(pinned = true)))
        assertEquals(listOf("p"), TeamMapRules.layout(state).groups.single().bots.map { it.id })
    }

    @Test
    fun `a chief with a department shows once, at the top`() {
        val state = CompanionState(bots = listOf(bot("lead", section = "Ops", chief = true), bot("a", section = "Ops")))
        val layout = TeamMapRules.layout(state)
        assertEquals("lead", layout.chief?.id)
        assertEquals(listOf("a"), layout.groups.single().bots.map { it.id })
    }
}
