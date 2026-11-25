package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The state of the read-only workspace picker. The Swift twin is `WorkspacePickerTests.swift`. */
class WorkspacePickerTest {
    private class FakeSource(var list: List<PhoneWorkspace>? = emptyList()) : WorkspaceSource {
        val reads = mutableListOf<String>()
        val changes = mutableListOf<String>()
        var listFails = false

        /** Runs inside a read, before it answers: a place to change the active pairing mid-read. */
        var duringRead: () -> Unit = {}

        override suspend fun workspaces(pairingId: String): List<PhoneWorkspace>? {
            reads += pairingId
            duringRead()
            if (listFails) throw IOException("offline")
            return list
        }

        override suspend fun workspaceChanged(pairingId: String) {
            changes += pairingId
        }
    }

    private val home = PhoneWorkspace(id = "home", name = "Home", current = true)
    private val trading = PhoneWorkspace(id = "trading", name = "Trading")
    private val pentest = PhoneWorkspace(id = "pentest", name = "Pentest", state = "waiting", waitingFor = "Nadia")
    private val locked = PhoneWorkspace(id = "locked", name = "Locked", state = "unavailable")

    private fun model(source: FakeSource, pairing: String = "p1", active: () -> String? = { pairing }) =
        WorkspacePickerModel(pairing, source, active)

    @Test
    fun oneEntryGetsNoPicker() = runBlocking {
        val m = model(FakeSource(listOf(home)))
        m.refresh()
        assertFalse(m.state.value.visible)
    }

    @Test
    fun noListGetsNoPicker() = runBlocking {
        val m = model(FakeSource(null))
        m.refresh()
        assertFalse(m.state.value.visible)
        assertTrue(m.state.value.workspaces.isEmpty())
    }

    @Test
    fun noRowOffersAnAction() = runBlocking {
        val m = model(FakeSource(listOf(home, trading, pentest, locked)))
        m.refresh()

        val rows = m.state.value.rows.associateBy { it.workspace.id }
        assertEquals(WorkspaceStatus.OPEN, rows.getValue("home").status)
        assertEquals(WorkspaceStatus.NONE, rows.getValue("trading").status)
        assertEquals(WorkspaceStatus.WAITING, rows.getValue("pentest").status)
        assertEquals("Nadia", WorkspacePicker.waitingFor(rows.getValue("pentest").workspace))
        assertEquals(WorkspaceStatus.UNAVAILABLE, rows.getValue("locked").status)
        assertTrue(m.state.value.polling)
        // The row type has no tap and no button: nothing here can start, hurry or cancel a switch.
        assertEquals(setOf("workspace", "status"), WorkspaceRow::class.java.declaredFields.map { it.name }.toSet())
    }

    @Test
    fun theModelHasNoWayToAskTheComputerForASwitch() {
        val source = WorkspaceSource::class.java.declaredMethods.map { it.name }.toSet()
        assertEquals(setOf("workspaces", "workspaceChanged"), source)
    }

    @Test
    fun aChangeOfTheOpenWorkspaceReloadsOnceForThatPairing() = runBlocking {
        val source = FakeSource(listOf(home, pentest))
        val m = model(source)
        m.refresh()
        assertTrue(m.state.value.polling)

        source.list = listOf(home.copy(current = false), pentest.copy(current = true, state = "ready", waitingFor = null))
        m.refresh()

        assertEquals("pentest", m.state.value.current?.id)
        assertEquals(listOf("p1"), source.changes)
        assertFalse(m.state.value.polling)
    }

    @Test
    fun aListReadThatFailsKeepsTheLastList() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        val m = model(source)
        m.refresh()
        source.listFails = true
        m.refresh()
        assertTrue(m.state.value.visible)
        assertEquals("home", m.state.value.current?.id)
    }

    @Test
    fun aListThatArrivesAfterThePairingChangedIsDropped() = runBlocking {
        var active: String? = "p1"
        val source = FakeSource(listOf(home, trading))
        val m = model(source, active = { active })
        m.refresh()
        assertEquals(2, m.state.value.workspaces.size)

        source.list = listOf(home.copy(current = false), trading.copy(current = true))
        source.duringRead = { active = "p2" }
        m.refresh()

        assertEquals("home", m.state.value.current?.id, "the stale answer did not replace the list")
        assertTrue(source.changes.isEmpty(), "a stale answer never reloads the session")
    }

    @Test
    fun aModelOfAnOldPairingNeverReadsAgain() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        val m = model(source, active = { "p2" })
        m.refresh()
        assertTrue(source.reads.isEmpty())
        assertTrue(m.state.value.workspaces.isEmpty())
    }

    @Test
    fun aNewPairingStartsFromAnEmptyListAndReadsItsOwn() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        val first = model(source, pairing = "p1")
        first.refresh()
        val second = model(FakeSource(listOf(PhoneWorkspace(id = "other", name = "Other", current = true), trading)), pairing = "p2")

        assertTrue(second.state.value.workspaces.isEmpty(), "nothing of the first computer is shown")
        second.refresh()
        assertEquals("other", second.state.value.current?.id)
    }

    @Test
    fun aFailedReadAfterAPairingChangeLeavesTheOldListUnusedByTheNewModel() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        val first = model(source, pairing = "p1")
        first.refresh()
        source.listFails = true
        val second = model(source, pairing = "p2")
        second.refresh()
        assertTrue(second.state.value.workspaces.isEmpty(), "a failed refresh never falls back to another computer's list")
    }
}
