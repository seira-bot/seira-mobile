package bot.seira.companion.core

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalRunCardTest {
    private fun decode(json: String): Message = CompanionJson.decodeFromString(json)

    private fun card(status: String, turns: Int = 2, max: Int = 6) =
        GoalRunCard(runId = "r1", goal = "Ship", status = status, turnCount = turns, maxTurns = max)

    @Test
    fun decodesAGoalRunWithItsCard() {
        val message = decode(
            """{"id":"m1","role":"bot","kind":"goal.run","at":1,"text":"Goal in progress",
               "goalRun":{"runId":"r1","goal":"Ship the beta","status":"working","coordinatorBotId":"b1",
               "coordinatorName":"Ada","turnCount":2,"maxTurns":6,"detail":"Waiting on tests",
               "detailCode":"x","detailParams":{"n":1},"startedAt":1700000000000,"newerField":true}}""",
        )
        assertEquals(Message.Kind.GOAL_RUN, message.kind)
        val run = assertNotNull(message.goalRun)
        assertEquals("Ship the beta", run.goal)
        assertEquals("Ada", run.coordinatorName)
        assertEquals("Waiting on tests", run.detail)
        assertNull(run.finishedAt)
    }

    @Test
    fun aGoalRunWithoutItsCardKeepsItsText() {
        val message = decode("""{"id":"m1","role":"bot","kind":"goal.run","at":1,"text":"Goal failed: no budget"}""")
        assertEquals(Message.Kind.GOAL_RUN, message.kind)
        assertNull(message.goalRun)
        assertEquals("Goal failed: no budget", previewText(message))
    }

    @Test
    fun anUnknownStatusDecodesAndReadsNeutral() {
        val message = decode(
            """{"id":"m1","role":"bot","kind":"goal.run","at":1,"goalRun":{"runId":"r1","status":"rehearsing"}}""",
        )
        assertEquals("rehearsing", message.goalRun?.status)
        assertEquals(GoalRunTone.NEUTRAL, GoalRuns.tone("rehearsing"))
        assertFalse("rehearsing" in GoalRuns.knownStatuses)
    }

    @Test
    fun reEncodesTheKindTheWayTheComputerSpellsIt() {
        val message = Message("m", Message.Role.BOT, Message.Kind.GOAL_RUN, 1.0)
        val raw = CompanionJson.encodeToString(Message.serializer(), message)
        assertTrue("\"kind\":\"goal.run\"" in raw)
        assertEquals(Message.Kind.GOAL_RUN, decode(raw).kind)
    }

    @Test
    fun everyKnownStatusHasItsTone() {
        assertEquals(GoalRunTone.ACTIVE, GoalRuns.tone("working"))
        assertEquals(GoalRunTone.SUCCESS, GoalRuns.tone("completed"))
        for (status in listOf("needs-input", "blocked", "limit-reached", "paused")) {
            assertEquals(GoalRunTone.ATTENTION, GoalRuns.tone(status), status)
        }
        assertEquals(GoalRunTone.NEUTRAL, GoalRuns.tone("stopped"))
        assertEquals(GoalRunTone.ERROR, GoalRuns.tone("failed"))
    }

    @Test
    fun turnLineCountsTheTurnBeingWorkedOn() {
        assertEquals(TurnLine.Working(3, 6), GoalRuns.turnLine(card("working", turns = 2)))
        assertEquals(TurnLine.Working(6, 6), GoalRuns.turnLine(card("working", turns = 9)))
    }

    @Test
    fun turnLineOfAFinishedRunIsTheCount() {
        assertEquals(TurnLine.Finished(1), GoalRuns.turnLine(card("completed", turns = 1)))
        assertEquals(TurnLine.Finished(4), GoalRuns.turnLine(card("failed", turns = 4)))
    }

    @Test
    fun stopShowsOnlyWhileWorking() {
        assertTrue(GoalRuns.showsStop(card("working")))
        for (status in GoalRuns.knownStatuses - "working") assertFalse(GoalRuns.showsStop(card(status)), status)
        assertFalse(GoalRuns.showsStop(card("rehearsing")))
    }

    @Test
    fun compactCollapsesSpaceAndCutsWithAnEllipsis() {
        assertEquals("a b c", GoalRuns.compact("  a \n b\t c ", 10))
        assertEquals("abcd…", GoalRuns.compact("abcdefgh", 5))
        assertEquals("abcde", GoalRuns.compact("abcde", 5))
        assertEquals("", GoalRuns.compact(null, 5))
    }
}
