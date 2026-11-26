package com.openmausbot.companion.ui.home

import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.core.RoutineRun
import com.openmausbot.companion.core.RoutineSchedule
import com.openmausbot.companion.ui.home.automations.AutomationsRules
import com.openmausbot.companion.ui.home.automations.ReceiptTone
import com.openmausbot.companion.ui.home.automations.RoutineMenuAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AutomationsLogicTest {
    private fun routine(
        id: String = "r1",
        enabled: Boolean = true,
        schedule: RoutineSchedule = RoutineSchedule.daily("09:00", listOf(1)),
    ) = Routine(
        id = id, name = "Daily briefing", prompt = "p", botId = "seira", runOn = "maus", enabled = enabled,
        schedule = schedule, durationMinutes = 30, createdAt = 0.0, updatedAt = 0.0,
    )

    private fun run(id: String, routineId: String = "r1", status: String = "completed", at: Double, thread: String? = "t-$id") =
        RoutineRun(
            id = id, routineId = routineId, routineName = "Daily briefing", botId = "seira", runOn = "maus",
            scheduledFor = at, status = status, manual = false, threadId = thread, createdAt = 0.0,
        )

    @Test
    fun `tone follows the run status`() {
        assertEquals(ReceiptTone.SUCCESS, AutomationsRules.tone("completed"))
        assertEquals(ReceiptTone.DANGER, AutomationsRules.tone("failed"))
        assertEquals(ReceiptTone.DANGER, AutomationsRules.tone("missed"))
        assertEquals(ReceiptTone.WARNING, AutomationsRules.tone("waiting"))
        assertEquals(ReceiptTone.QUIET, AutomationsRules.tone("running"))
    }

    @Test
    fun `receipts are newest first and capped`() {
        val runs = (1..40).map { run("r$it", at = it.toDouble()) }
        val receipts = AutomationsRules.receipts(runs)
        assertEquals(AutomationsRules.RECEIPTS_SHOWN, receipts.size)
        assertEquals("r40", receipts.first().id)
    }

    @Test
    fun `the results thread is the newest run that has one`() {
        val runs = listOf(
            run("old", at = 1.0),
            run("new-no-thread", at = 3.0, thread = null),
            run("mid", at = 2.0),
            run("other", routineId = "r2", at = 9.0),
        )
        assertEquals("mid", AutomationsRules.resultsRun("r1", runs)?.id)
        assertNull(AutomationsRules.resultsRun("r3", runs))
    }

    @Test
    fun `the switch shows the tapped state until the computer answers`() {
        val routine = routine(enabled = true)
        assertEquals(true, AutomationsRules.shownEnabled(routine, emptyMap()))
        assertEquals(false, AutomationsRules.shownEnabled(routine, mapOf("r1" to false)))
        assertEquals(true, AutomationsRules.shownEnabled(routine, mapOf("other" to false)))
    }

    @Test
    fun `the sheet toggle follows the tapped state, not the stored one`() {
        val paused = routine(enabled = false)
        assertEquals(true, AutomationsRules.shownEnabled(paused, mapOf("r1" to true)))
        assertEquals(false, AutomationsRules.shownEnabled(paused, emptyMap()))
    }

    @Test
    fun `the menu lists what the routine can do`() {
        val withResults = AutomationsRules.menu(routine(), listOf(run("a", at = 1.0)), 0.0)
        assertEquals(
            listOf(
                RoutineMenuAction.RUN_NOW, RoutineMenuAction.EDIT, RoutineMenuAction.TOGGLE,
                RoutineMenuAction.OPEN_RESULTS, RoutineMenuAction.DELETE,
            ),
            withResults,
        )

        val finishedOnce = routine(enabled = false, schedule = RoutineSchedule.once(100.0))
        val menu = AutomationsRules.menu(finishedOnce, emptyList(), 1_000.0)
        assertFalse(RoutineMenuAction.TOGGLE in menu)
        assertFalse(RoutineMenuAction.OPEN_RESULTS in menu)
        assertEquals(RoutineMenuAction.DELETE, menu.last())
    }
}
