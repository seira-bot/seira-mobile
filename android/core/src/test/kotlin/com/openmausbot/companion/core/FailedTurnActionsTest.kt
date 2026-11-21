package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString

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

    private fun text(id: String, role: Message.Role) = Message(id, role, Message.Kind.TEXT, 1.0, text = "hi")
    private fun failed(id: String, claudeUpdate: Boolean = false) = Message(
        id, Message.Role.BOT, Message.Kind.ACTIVITY, 1.0,
        tool = ToolActivity(name = "error: limit", ok = false, claudeUpdate = if (claudeUpdate) true else null),
    )

    @Test
    fun theLatestFailedTurnOffersASwitch() {
        val messages = listOf(text("u1", Message.Role.USER), failed("f1"), text("u2", Message.Role.USER), failed("f2"))
        assertEquals("f2", FailedTurnActions.switchModelOfferMessageId(messages))
    }

    @Test
    fun noOfferOnceSomethingWasSaidAfterTheFailure() {
        assertNull(FailedTurnActions.switchModelOfferMessageId(listOf(failed("f1"), text("u2", Message.Role.USER))))
        assertNull(FailedTurnActions.switchModelOfferMessageId(listOf(failed("f1"), text("b2", Message.Role.BOT))))
        assertNull(FailedTurnActions.switchModelOfferMessageId(listOf(text("u1", Message.Role.USER))))
    }

    @Test
    fun noOfferBesideAClaudeUpdateCard() {
        assertNull(FailedTurnActions.switchModelOfferMessageId(listOf(failed("f1", claudeUpdate = true))))
    }

    @Test
    fun noOfferWhileAUsageLimitOfferIsOpen() {
        val card = CompanionJson.decodeFromString<OptionCard>(
            """{"title":"Switch?","subtitle":"","options":["Codex","Not now"],"requestId":"r1",
                "quotaSwitch":{"engines":[{"instanceId":"codex","name":"Codex"}],"messageId":"u1"}}""",
        )
        assertEquals("u1", card.quotaSwitch?.messageId)
        val open = Message("c1", Message.Role.BOT, Message.Kind.OPTIONS, 2.0, card = card)
        assertNull(FailedTurnActions.switchModelOfferMessageId(listOf(failed("f1"), open)))
        val answered = open.copy(card = card.copy(answered = "Not now"))
        assertEquals("f1", FailedTurnActions.switchModelOfferMessageId(listOf(failed("f1"), answered)))
    }

    @Test
    fun resetTimeNamesTheWeekdayOnlyWhenNotToday() {
        val utc = java.time.ZoneOffset.UTC
        val now = 1_795_277_400_000L
        val us = java.util.Locale.US
        val same = FailedTurnActions.continueTime(now + 600_000.0, now, utc, us)
        val later = FailedTurnActions.continueTime(now + 3 * 86_400_000.0, now, utc, us)
        assertTrue(same.contains(":"))
        assertTrue(later.length > same.length)
        assertTrue(later.matches(Regex("""[A-Z][a-z]{2} \d{1,2}:\d{2}.*""")), later)
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
