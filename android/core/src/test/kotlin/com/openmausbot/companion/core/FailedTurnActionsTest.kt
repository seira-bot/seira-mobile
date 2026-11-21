package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FailedTurnActionsTest {
    private val overloaded = ToolActivity(name = "error: Claude is overloaded right now.", ok = false)

    private fun offer(
        tool: ToolActivity,
        running: Boolean = false,
        modelForbidden: Boolean = false,
        continueForbidden: Boolean = false,
    ) = FailedTurnActions.offer(tool, running, modelForbidden, continueForbidden)

    @Test
    fun aModelFixOffersSwitchModelAndTryAgain() {
        val offer = offer(overloaded)!!
        assertTrue(offer.switchModel)
        assertTrue(offer.tryAgain)
        assertFalse(offer.checkUsage)
        assertNull(offer.continueAt)
        assertFalse(offer.askOwner)
    }

    @Test
    fun nothingShowsWhileATurnRuns() {
        assertNull(offer(overloaded, running = true))
    }

    @Test
    fun noModelListHidesSwitchModelKeepsTryAgainAndAsksTheOwner() {
        val offer = offer(overloaded, modelForbidden = true)!!
        assertFalse(offer.switchModel)
        assertTrue(offer.tryAgain)
        assertTrue(offer.askOwner)
    }

    @Test
    fun continueAtShowsOnlyWithAResetTimeAndHidesAfterRefusal() {
        val limited = overloaded.copy(limitResetsAt = 1790000000000.0)
        assertEquals(1790000000000.0, offer(limited)!!.continueAt)
        assertNull(offer(limited, continueForbidden = true)!!.continueAt)
        assertNull(offer(overloaded)!!.continueAt)
    }

    @Test
    fun checkUsageShowsOnlyForTheCodexUsageLimit() {
        val codex = ToolActivity(name = "error: $CODEX_USAGE_LIMIT_REACHED", ok = false)
        val offer = offer(codex)!!
        assertTrue(offer.checkUsage)
        assertFalse(offer.switchModel)
        assertFalse(offer(overloaded)!!.checkUsage)
    }

    @Test
    fun aPartialOrUnrelatedFailureOffersNothing() {
        assertNull(offer(overloaded.copy(partial = true)))
        assertNull(offer(ToolActivity(name = "error: Disk full.", ok = false)))
        assertNull(offer(ToolActivity(name = "Read file")))
    }

    @Test
    fun onlyTheNewestFailureBeforeAnyNewUserLineIsOffered() {
        val user = Message("u1", Message.Role.USER, Message.Kind.TEXT, 1.0, text = "hello")
        val first = Message("f1", Message.Role.BOT, Message.Kind.ACTIVITY, 2.0, tool = overloaded)
        val second = Message("f2", Message.Role.BOT, Message.Kind.ACTIVITY, 3.0, tool = overloaded)
        val notice = Message("n1", Message.Role.BOT, Message.Kind.ACTIVITY, 4.0, tool = ToolActivity(name = "notice: retrying"))
        assertTrue(FailedTurnActions.isLatestFailure(listOf(user, first, notice), first))
        assertFalse(FailedTurnActions.isLatestFailure(listOf(user, first, second), first))
        assertTrue(FailedTurnActions.isLatestFailure(listOf(user, first, second), second))
        val again = Message("u2", Message.Role.USER, Message.Kind.TEXT, 5.0, text = "again")
        assertFalse(FailedTurnActions.isLatestFailure(listOf(user, first, again), first))
        assertFalse(FailedTurnActions.isLatestFailure(listOf(user), first))
    }

    @Test
    fun lastUserMessageSkipsBotRowsAndBlankText() {
        val a = Message("u1", Message.Role.USER, Message.Kind.TEXT, 1.0, text = "first")
        val b = Message("u2", Message.Role.USER, Message.Kind.TEXT, 2.0, text = "second")
        val reply = Message("b1", Message.Role.BOT, Message.Kind.TEXT, 3.0, text = "answer")
        val blank = Message("u3", Message.Role.USER, Message.Kind.TEXT, 4.0, text = "  ")
        assertEquals("u2", FailedTurnActions.lastUserMessage(listOf(a, b, reply, blank))?.id)
        assertNull(FailedTurnActions.lastUserMessage(listOf(reply)))
    }
}
