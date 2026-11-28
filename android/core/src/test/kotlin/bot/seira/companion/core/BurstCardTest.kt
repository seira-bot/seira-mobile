package bot.seira.companion.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BurstCardTest {
    private fun decode(json: String): Message = CompanionJson.decodeFromString(json)

    private val preview = """{"id":"m1","role":"bot","kind":"burst","at":1,"text":"Burst ready: 4 tasks.",
        "burst":{"version":1,"burstId":"bu-1","botId":"b1","task":"Review the docs","phase":"preview",
        "total":4,"requested":6,"parallel":2,"taskLimit":4,"smallPlan":true,"modelName":"Opus","estimateMinutes":6,
        "counts":{"queued":4,"running":0,"done":0,"failed":0,"cancelled":0},"sample":["Intro","Setup","FAQ"],"extra":1}}"""

    @Test
    fun decodesAPreview() {
        val message = decode(preview)
        assertEquals(Message.Kind.BURST, message.kind)
        val card = assertNotNull(message.burst)
        assertTrue(card.isPreview)
        assertEquals(4, card.total)
        assertEquals(6, card.requested)
        assertEquals(4, card.taskLimit)
        assertEquals(listOf("Intro", "Setup", "FAQ"), card.sample)
        assertEquals(4, card.count("queued"))
    }

    @Test
    fun aBurstWithoutItsCardKeepsItsText() {
        val message = decode("""{"id":"m1","role":"bot","kind":"burst","at":1,"text":"Burst cancelled."}""")
        assertEquals(Message.Kind.BURST, message.kind)
        assertNull(message.burst)
        assertEquals("Burst cancelled.", previewText(message))
    }

    @Test
    fun anUnknownPhaseAndAnUnknownCountKeyStillDecode() {
        val message = decode(
            """{"id":"m1","role":"bot","kind":"burst","at":1,"burst":{"burstId":"bu-1","phase":"polishing","total":3,
               "counts":{"running":1,"done":1,"paused":1}}}""",
        )
        val card = assertNotNull(message.burst)
        assertEquals("polishing", card.phase)
        assertFalse(card.isPreview)
        assertFalse(card.isActive)
        assertEquals(1, card.count("paused"))
        assertEquals(listOf("running" to 1), Bursts.countParts(card))
    }

    @Test
    fun theCountsLineKeepsOnlyWhatIsNonZero() {
        val card = BurstCard(counts = mapOf("running" to 2, "queued" to 0, "failed" to 1, "done" to 5))
        assertEquals(listOf("running" to 2, "failed" to 1), Bursts.countParts(card))
    }

    @Test
    fun retryShowsOnlyForFailedPartsInARunningOrDoneBurst() {
        val failed = mapOf("failed" to 1)
        assertTrue(BurstCard(phase = "running", counts = failed).canRetry)
        assertTrue(BurstCard(phase = "done", counts = failed).canRetry)
        assertFalse(BurstCard(phase = "cancelled", counts = failed).canRetry)
        assertFalse(BurstCard(phase = "running", counts = mapOf("failed" to 0)).canRetry)
    }

    @Test
    fun runningAndMergingAreTheActivePhases() {
        assertTrue(BurstCard(phase = "running").isActive)
        assertTrue(BurstCard(phase = "merging").isActive)
        assertFalse(BurstCard(phase = "done").isActive)
    }

    @Test
    fun decodesTheTaskListWithAnUnstartedPart() {
        val view: BurstView = CompanionJson.decodeFromString(
            """{"burstId":"bu-1","phase":"running","instructions":"Be brief","items":["a","b"],
               "children":[{"seq":1,"title":"a","status":"done","threadId":"t-1"},
               {"seq":2,"title":"b","status":"queued"},{"seq":3,"title":"c","status":"vanished","error":"x"}]}""",
        )
        assertEquals(listOf("a", "b"), view.items)
        assertTrue(Bursts.opensThread(view.children[0]))
        assertFalse(Bursts.opensThread(view.children[1]))
        assertEquals("vanished", view.children[2].status)
    }

    @Test
    fun theItemsFieldIsOneTaskPerNonBlankLine() {
        assertEquals(listOf("a", "b c", "d"), Bursts.parseItems(" a \n\n b c\n   \nd\n"))
    }

    @Test
    fun saveNeedsTwoTasksAndNoRequestInFlight() {
        assertFalse(Bursts.canSave(listOf("a"), busy = false))
        assertTrue(Bursts.canSave(listOf("a", "b"), busy = false))
        assertFalse(Bursts.canSave(listOf("a", "b"), busy = true))
    }

    @Test
    fun buttonsStayOffWhileARequestRuns() = runTest {
        val gate = CompletableDeferred<BurstOutcome>()
        val seen = mutableListOf<BurstAction>()
        val runner = BurstActionRunner({ action, _ -> seen += action; gate.await() }, "fallback")

        val first = async { runner.press(BurstAction.RUN) }
        runCurrent()
        assertTrue(runner.state.value.busy)
        assertFalse(runner.press(BurstAction.CANCEL))
        assertEquals(listOf(BurstAction.RUN), seen)

        gate.complete(BurstOutcome.Done)
        assertTrue(first.await())
        assertEquals(BurstActionRunner.State(), runner.state.value)
    }

    @Test
    fun aFailureShowsTheComputersWordsAndTurnsTheButtonsBackOn() = runTest {
        val runner = BurstActionRunner({ _, _ -> BurstOutcome.Failed("Already running") }, "fallback")
        assertFalse(runner.press(BurstAction.RUN))
        assertEquals(BurstActionRunner.State(busy = false, error = "Already running"), runner.state.value)
    }

    @Test
    fun aFailureWithoutWordsShowsTheFallbackAndTheNextPressClearsIt() = runTest {
        var outcome: BurstOutcome = BurstOutcome.Failed("  ")
        val runner = BurstActionRunner({ _, _ -> outcome }, "Try again")
        runner.press(BurstAction.RETRY)
        assertEquals("Try again", runner.state.value.error)

        outcome = BurstOutcome.Done
        assertTrue(runner.press(BurstAction.RETRY))
        assertNull(runner.state.value.error)
    }

    @Test
    fun aThrowingActionIsAFailure() = runTest {
        val runner = BurstActionRunner({ _, _ -> error("boom") }, "Try again")
        assertFalse(runner.press(BurstAction.RUN))
        assertEquals(BurstActionRunner.State(error = "Try again"), runner.state.value)
    }
}
