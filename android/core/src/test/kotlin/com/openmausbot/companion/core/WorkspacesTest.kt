package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The workspace list and what a picker row says. The Swift twin is `WorkspacesTests.swift`. */
class WorkspacesTest {
    private fun decode(text: String) = CompanionJson.decodeFromString<PhoneWorkspaceList>(text).workspaces

    @Test
    fun readsTheListWithEveryField() {
        val list = decode(
            """{"workspaces":[
                {"id":"default","name":null,"color":null,"current":true,"state":"ready"},
                {"id":"trading","name":"Trading","color":"teal","current":false,"state":"waiting","waitingFor":"Nadia"}
            ]}""",
        )
        assertEquals(listOf("default", "trading"), list.map { it.id })
        assertTrue(list[0].current)
        assertEquals("teal", list[1].color)
        assertEquals(WorkspaceAvailability.WAITING, list[1].availability)
        assertEquals("Nadia", list[1].waitingFor)
    }

    @Test
    fun anOlderShapeWithOnlyAnIdStillReads() {
        val only = decode("""{"workspaces":[{"id":"a"}]}""").single()
        assertFalse(only.current)
        assertEquals(WorkspaceAvailability.READY, only.availability)
        assertNull(only.name)
    }

    @Test
    fun aMissingListIsEmpty() {
        assertEquals(emptyList(), decode("{}"))
    }

    @Test
    fun aStateThisBuildDoesNotKnowIsUnavailableNotAFailure() {
        val list = decode("""{"workspaces":[{"id":"a","state":"hibernating"},{"id":"b"}]}""")
        assertEquals(WorkspaceAvailability.UNAVAILABLE, list[0].availability)
        assertEquals("hibernating", list[0].state)
        assertEquals(2, list.size)
    }

    @Test
    fun normalizingDropsBlankAndRepeatedIdsAndKeepsOneOpenWorkspace() {
        val list = WorkspacePolicy.normalized(
            listOf(
                PhoneWorkspace(id = "a", current = true),
                PhoneWorkspace(id = " "),
                PhoneWorkspace(id = "a", name = "again"),
                PhoneWorkspace(id = "b", current = true),
                PhoneWorkspace(id = "c"),
            ),
        )
        assertEquals(listOf("a", "b", "c"), list.map { it.id })
        assertEquals(listOf(true, false, false), list.map { it.current })
        assertNull(list[0].name)
    }

    @Test
    fun theServerOrderIsKept() {
        val ids = listOf("z", "m", "a")
        assertEquals(ids, WorkspacePolicy.normalized(ids.map { PhoneWorkspace(id = it) }).map { it.id })
    }

    @Test
    fun noPickerForOneWorkspaceOrNone() {
        assertFalse(WorkspacePolicy.showsPicker(emptyList()))
        assertFalse(WorkspacePolicy.showsPicker(listOf(PhoneWorkspace(id = "a", current = true))))
        assertFalse(WorkspacePolicy.showsPicker(listOf(PhoneWorkspace(id = "a"), PhoneWorkspace(id = "a"))))
        assertTrue(WorkspacePolicy.showsPicker(listOf(PhoneWorkspace(id = "a"), PhoneWorkspace(id = "b"))))
    }

    @Test
    fun anUnnamedWorkspaceIsThisComputer() {
        assertEquals("This computer", WorkspacePolicy.displayName(PhoneWorkspace(id = "a")))
        assertEquals("This computer", WorkspacePolicy.displayName(PhoneWorkspace(id = "a", name = "  ")))
        assertEquals("Trading", WorkspacePolicy.displayName(PhoneWorkspace(id = "a", name = " Trading ")))
    }

    @Test
    fun statusLinesFollowTheDesktop() {
        assertEquals("Open", WorkspacePolicy.statusLine(PhoneWorkspace(id = "a", current = true)))
        assertNull(WorkspacePolicy.statusLine(PhoneWorkspace(id = "a")))
        assertEquals(
            "Waiting for Nadia to finish",
            WorkspacePolicy.statusLine(PhoneWorkspace(id = "a", state = "waiting", waitingFor = "Nadia")),
        )
        assertEquals(
            "Waiting for work to finish",
            WorkspacePolicy.statusLine(PhoneWorkspace(id = "a", state = "waiting", waitingFor = " ")),
        )
        assertEquals("Not available", WorkspacePolicy.statusLine(PhoneWorkspace(id = "a", state = "unavailable")))
    }

    @Test
    fun onlyAnotherAvailableWorkspaceCanBeOpened() {
        assertFalse(WorkspacePolicy.canSwitch(PhoneWorkspace(id = "a", current = true)))
        assertFalse(WorkspacePolicy.canSwitch(PhoneWorkspace(id = "a", state = "unavailable")))
        assertTrue(WorkspacePolicy.canSwitch(PhoneWorkspace(id = "a")))
        assertTrue(WorkspacePolicy.canSwitch(PhoneWorkspace(id = "a", state = "waiting")))
    }
}
