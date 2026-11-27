package com.openmausbot.companion.core

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DepartmentSectionsTest {
    @Test
    fun theFleetCarriesTheDepartmentNamesInOrder() {
        val fleet = CompanionJson.decodeFromString<Fleet>("""{"bots":[],"groups":[],"sections":["Work","Empty","Content"]}""")
        assertEquals(listOf("Work", "Empty", "Content"), fleet.sections)
    }

    @Test
    fun anOlderComputerLeavesTheNamesAbsent() {
        val fleet = CompanionJson.decodeFromString<Fleet>("""{"bots":[],"groups":[]}""")
        assertNull(fleet.sections)
    }

    @Test
    fun aListThatIsNotNamesReadsAsAbsent() {
        val fleet = CompanionJson.decodeFromString<Fleet>("""{"bots":[],"groups":[],"sections":["Work",7]}""")
        assertNull(fleet.sections)
    }

    @Test
    fun theSectionsFrameDecodes() {
        val stream = CompanionJson.decodeFromString<StreamFrame>("""{"kind":"sections","sections":["A","B"],"seq":4}""")
        assertEquals(Frame.Sections(listOf("A", "B")), stream.frame)
        assertEquals(4, stream.seq)
    }

    @Test
    fun aBrokenSectionsFrameIsUnknown() {
        val frame = CompanionJson.decodeFromString<StreamFrame>("""{"kind":"sections","sections":"A"}""").frame
        assertEquals(Frame.Unknown("sections"), frame)
    }

    @Test
    fun anEmptyDepartmentShowsInTheSidebarSections() {
        val state = CompanionState().hydrate(Fleet(bots = emptyList(), groups = emptyList(), sections = listOf("Work", "Empty")))
        assertEquals(listOf("Work", "Empty"), state.sidebarSections.map(SidebarSection::name))
        assertEquals(emptyList(), state.sidebarSections.last().bots)
    }

    @Test
    fun aFleetWithoutNamesKeepsTheNamesAlreadyKnown() {
        val known = CompanionState().apply(Frame.Sections(listOf("Work")))
        val next = known.hydrate(Fleet(bots = emptyList(), groups = emptyList()))
        assertEquals(listOf("Work"), next.declaredSections)
    }

    @Test
    fun theFrameReplacesTheNamesAndDropsBlankAndRepeatedOnes() {
        val state = CompanionState().apply(Frame.Sections(listOf("Work", "  ", "Work", " Ops ")))
        assertEquals(listOf("Work", "Ops"), state.declaredSections)
    }

    @Test
    fun aDepartmentWithBotsComesAfterTheListedNamesWithoutRepeating() {
        val base = CompanionState().hydrate(Fleet(bots = emptyList(), groups = emptyList(), sections = listOf("Work")))
        val bot = departmentBot("b1").copy(section = "Work")
        val other = departmentBot("b2").copy(section = "Sales")
        val state = base.copy(bots = listOf(bot, other))
        assertEquals(listOf("Work", "Sales"), state.sidebarSections.map(SidebarSection::name))
        assertEquals(listOf("b1"), state.sidebarSections.first().bots.map(Bot::id))
    }

    private fun departmentBot(id: String) = Bot(
        id = id,
        threadId = "thread-$id",
        name = id,
        title = "",
        description = "",
        notifications = true,
        color = "blue",
        unread = false,
        modelSelection = ModelSelection("instance", "model"),
        createdAt = 1.0,
    )
}
