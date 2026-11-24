package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The state of the workspace picker. The Swift twin is `WorkspacePickerTests.swift`. */
class WorkspacePickerTest {
    private class FakeSource(
        var list: List<PhoneWorkspace>? = emptyList(),
        var onOpen: (String, Boolean) -> List<PhoneWorkspace> = { _, _ -> list.orEmpty() },
    ) : WorkspaceSource {
        val opens = mutableListOf<Pair<String, Boolean>>()
        var changes = 0
        var listFails = false

        override suspend fun workspaces(): List<PhoneWorkspace>? {
            if (listFails) throw IOException("offline")
            return list
        }

        override suspend fun requestOpen(id: String, now: Boolean): List<PhoneWorkspace> {
            opens += id to now
            return onOpen(id, now)
        }

        override suspend fun workspaceChanged() {
            changes += 1
        }
    }

    private val home = PhoneWorkspace(id = "home", name = "Home", current = true)
    private val trading = PhoneWorkspace(id = "trading", name = "Trading")
    private val pentest = PhoneWorkspace(id = "pentest", name = "Pentest", state = "waiting", waitingFor = "Nadia")
    private val locked = PhoneWorkspace(id = "locked", name = "Locked", state = "unavailable")

    private fun model(source: FakeSource, canOpen: Boolean = true) = WorkspacePickerModel(source) { canOpen }

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
    fun aReadyWorkspaceOpensOnATap() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        source.onOpen = { _, _ -> listOf(home.copy(current = false), trading.copy(current = true)) }
        val m = model(source)
        m.refresh()

        val row = m.state.value.rows.first { it.workspace.id == "trading" }
        assertEquals(WorkspaceAction.OPEN, row.tap)
        assertEquals(WorkspaceStatus.NONE, row.status)

        m.perform(WorkspaceAction.OPEN, "trading")

        assertEquals(listOf("trading" to false), source.opens)
        assertEquals("trading", m.state.value.current?.id)
        assertEquals(1, source.changes)
        assertNull(m.state.value.notice)
        assertNull(m.state.value.busyId)
    }

    @Test
    fun theOpenWorkspaceHasNothingToOpen() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        val m = model(source)
        m.refresh()

        val row = m.state.value.rows.first { it.workspace.id == "home" }
        assertEquals(WorkspaceStatus.OPEN, row.status)
        assertNull(row.tap)
        assertNull(row.button)
        m.perform(WorkspaceAction.OPEN, "home")
        assertTrue(source.opens.isEmpty())
    }

    @Test
    fun aWaitingWorkspaceOffersOpenNowAndTheOpenOneOffersCancel() = runBlocking {
        val m = model(FakeSource(listOf(home, pentest)))
        m.refresh()

        val rows = m.state.value.rows.associateBy { it.workspace.id }
        assertEquals(WorkspaceStatus.WAITING, rows.getValue("pentest").status)
        assertEquals("Nadia", WorkspacePicker.waitingFor(rows.getValue("pentest").workspace))
        assertNull(rows.getValue("pentest").tap)
        assertEquals(WorkspaceAction.OPEN_NOW, rows.getValue("pentest").button)
        assertEquals(WorkspaceAction.CANCEL_WAIT, rows.getValue("home").button)
        assertTrue(m.state.value.polling)
    }

    @Test
    fun openNowSendsTheHurryFlagAndKeepsWaitingUntilTheComputerOpensIt() = runBlocking {
        val source = FakeSource(listOf(home, pentest))
        source.onOpen = { _, _ -> listOf(home, pentest) }
        val m = model(source)
        m.refresh()

        m.perform(WorkspaceAction.OPEN_NOW, "pentest")

        assertEquals(listOf("pentest" to true), source.opens)
        assertEquals(0, source.changes)
        assertTrue(m.state.value.polling)

        source.list = listOf(home.copy(current = false), pentest.copy(current = true, state = "ready", waitingFor = null))
        m.refresh()

        assertEquals("pentest", m.state.value.current?.id)
        assertEquals(1, source.changes)
        assertFalse(m.state.value.polling)
    }

    @Test
    fun cancelAsksForTheOpenWorkspaceAndKeepsIt() = runBlocking {
        val source = FakeSource(listOf(home, pentest))
        source.onOpen = { _, _ -> listOf(home, pentest.copy(state = "ready", waitingFor = null)) }
        val m = model(source)
        m.refresh()

        m.perform(WorkspaceAction.CANCEL_WAIT, "home")

        assertEquals(listOf("home" to false), source.opens)
        assertEquals(0, source.changes)
        assertEquals("home", m.state.value.current?.id)
        assertFalse(m.state.value.polling)
    }

    @Test
    fun aRowCannotRunAnActionItDoesNotOffer() = runBlocking {
        val source = FakeSource(listOf(home, pentest, locked))
        val m = model(source)
        m.refresh()

        m.perform(WorkspaceAction.OPEN, "pentest")
        m.perform(WorkspaceAction.OPEN, "locked")
        m.perform(WorkspaceAction.OPEN_NOW, "locked")
        m.perform(WorkspaceAction.CANCEL_WAIT, "pentest")
        m.perform(WorkspaceAction.OPEN, "missing")

        assertTrue(source.opens.isEmpty())
        assertEquals(WorkspaceStatus.UNAVAILABLE, m.state.value.rows.first { it.workspace.id == "locked" }.status)
    }

    @Test
    fun aRefusedSwitchWithTurnedOffSaysSoAndHidesThePickerOnTheNextList() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        source.onOpen = { _, _ ->
            source.list = listOf(home)
            throw APIError.Status(403)
        }
        val m = model(source)
        m.refresh()

        m.perform(WorkspaceAction.OPEN, "trading")

        assertEquals(WorkspaceNotice.TURNED_OFF, m.state.value.notice)
        assertFalse(m.state.value.visible)
        assertEquals("home", m.state.value.current?.id)
        assertEquals(0, source.changes)
    }

    @Test
    fun aWorkspaceThatCannotOpenNowGives409AndReadsTheListAgain() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        source.onOpen = { _, _ ->
            source.list = listOf(home, trading.copy(state = "unavailable"))
            throw APIError.Status(409)
        }
        val m = model(source)
        m.refresh()

        m.perform(WorkspaceAction.OPEN, "trading")

        assertEquals(WorkspaceNotice.UNAVAILABLE, m.state.value.notice)
        assertEquals(WorkspaceStatus.UNAVAILABLE, m.state.value.rows.first { it.workspace.id == "trading" }.status)
        assertEquals(0, source.changes)
        assertNull(m.state.value.busyId)
    }

    @Test
    fun aWorkspaceThatIsGoneGives404() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        source.onOpen = { _, _ ->
            source.list = listOf(home)
            throw APIError.Status(404)
        }
        val m = model(source)
        m.refresh()
        m.perform(WorkspaceAction.OPEN, "trading")
        assertEquals(WorkspaceNotice.GONE, m.state.value.notice)
        assertFalse(m.state.value.visible)
    }

    @Test
    fun aNetworkFailureKeepsTheListAndSaysFailed() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        source.onOpen = { _, _ -> throw IOException("offline") }
        val m = model(source)
        m.refresh()
        m.perform(WorkspaceAction.OPEN, "trading")
        assertEquals(WorkspaceNotice.FAILED, m.state.value.notice)
        assertTrue(m.state.value.visible)
        assertEquals("home", m.state.value.current?.id)
    }

    @Test
    fun aListReadThatFailsKeepsTheLastList() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        val m = model(source)
        m.refresh()
        source.listFails = true
        m.refresh()
        assertTrue(m.state.value.visible)
    }

    @Test
    fun aPairingWithAPinnedIdentityNeverStartsASwitch() = runBlocking {
        val source = FakeSource(listOf(home, trading, pentest))
        val m = model(source, canOpen = false)
        m.refresh()

        val rows = m.state.value.rows.associateBy { it.workspace.id }
        assertEquals(WorkspaceStatus.PAIR_AGAIN, rows.getValue("trading").status)
        assertNull(rows.getValue("trading").tap)
        assertEquals(WorkspaceStatus.WAITING, rows.getValue("pentest").status)
        assertNull(rows.getValue("pentest").button)
        assertEquals(WorkspaceAction.CANCEL_WAIT, rows.getValue("home").button)

        m.perform(WorkspaceAction.OPEN, "trading")
        m.perform(WorkspaceAction.OPEN_NOW, "pentest")

        assertTrue(source.opens.isEmpty())
        assertEquals(0, source.changes)
        assertEquals(WorkspaceNotice.NEEDS_PAIRING, m.state.value.notice)
    }

    @Test
    fun onlyAnUnpinnedPairingMayOpen() {
        assertFalse(WorkspacePicker.canOpen(null))
        val plain = requireNotNull(Connection.parse("http://192.0.2.1:3030"))
        assertTrue(WorkspacePicker.canOpen(plain))
        assertFalse(WorkspacePicker.canOpen(plain.copy(serverEnvironmentId = "9f2c1a54-1111-4222-8333-444455556666")))
    }

    @Test
    fun aSecondRequestWhileOneIsOutIsIgnored() = runBlocking {
        val source = FakeSource(listOf(home, trading))
        val m = model(source)
        m.refresh()
        source.onOpen = { _, _ ->
            runBlocking { m.perform(WorkspaceAction.OPEN, "trading") }
            listOf(home, trading)
        }
        m.perform(WorkspaceAction.OPEN, "trading")
        assertEquals(1, source.opens.size)
    }

    @Test
    fun statusCodesMapToNotices() {
        assertEquals(WorkspaceNotice.TURNED_OFF, WorkspacePicker.notice(APIError.Status(403)))
        assertEquals(WorkspaceNotice.UNAVAILABLE, WorkspacePicker.notice(APIError.Status(409)))
        assertEquals(WorkspaceNotice.GONE, WorkspacePicker.notice(APIError.Status(404)))
        assertEquals(WorkspaceNotice.FAILED, WorkspacePicker.notice(APIError.Status(500)))
    }
}
