package com.openmausbot.companion.core

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudComputerNoticeTest {
    // The error text of desktop releases before 0.1.186, which the phone must still recognise.
    private val olderServerError =
        "error: Bots here can get their own cloud desktops from Boat, a paid service. " +
            "Connect your Boat account in AI › Other services."

    private fun failed(id: String, name: String = olderServerError, place: String? = null) = Message(
        id = id,
        role = Message.Role.BOT,
        kind = Message.Kind.ACTIVITY,
        at = 1.0,
        tool = ToolActivity(name = name, ok = false, place = place?.let { FailedPlace(it) }),
    )

    private fun user(id: String) = Message(id, Message.Role.USER, Message.Kind.TEXT, 1.0, text = "halo")
    private fun reply(id: String) = Message(id, Message.Role.BOT, Message.Kind.TEXT, 1.0, text = "hi")

    @Test
    fun anOlderServersErrorTextIsRecognisedAndWordedWithoutTheProvider() {
        val tool = failed("f1").tool!!
        assertTrue(CloudComputerNotice.matches(tool))
        assertEquals(CloudComputerNotice.TEXT, tool.label)
        assertFalse(tool.label.contains("Boat"))
    }

    @Test
    fun theStoredPlaceStateAloneIsEnough() {
        val tool = failed("f1", name = "error: something reworded", place = "cc-needs-key").tool!!
        assertTrue(CloudComputerNotice.matches(tool))
    }

    @Test
    fun otherFailuresAndOrdinaryRowsAreLeftAlone() {
        val other = ToolActivity(name = "error: Claude is overloaded right now.", ok = false)
        assertFalse(CloudComputerNotice.matches(other))
        assertEquals("Claude is overloaded right now.", other.label)
        assertFalse(CloudComputerNotice.matches(ToolActivity(name = "Connect your account", ok = true)))
        assertFalse(CloudComputerNotice.matches(failed("f2", place = "cc-asleep").tool!!.copy(name = "error: asleep")))
    }

    @Test
    fun oneNoticePerThreadWhileMessagesKeepFailing() {
        val thread = listOf(user("u1"), failed("f1"), user("u2"), failed("f2"), user("u3"), failed("f3"))
        assertEquals(setOf("f2", "f3"), CloudComputerNotice.repeatedIds(thread))
    }

    @Test
    fun aFailureAfterTheBotAnsweredIsAnnouncedAgain() {
        val thread = listOf(failed("f1"), user("u2"), failed("f2"), user("u3"), reply("r1"), user("u4"), failed("f3"), failed("f4"))
        assertEquals(setOf("f2", "f4"), CloudComputerNotice.repeatedIds(thread))
    }

    @Test
    fun otherFailuresNeitherCountNorBreakARun() {
        val other = Message("x", Message.Role.BOT, Message.Kind.ACTIVITY, 1.0, tool = ToolActivity("error: offline", ok = false))
        assertEquals(setOf("f2"), CloudComputerNotice.repeatedIds(listOf(failed("f1"), other, failed("f2"))))
    }

    @Test
    fun theTranscriptDropsTheRepeatedNotices() {
        val rows = transcriptRows(listOf(user("u1"), failed("f1"), user("u2"), failed("f2")), ActivityDetail.FULL)
        val ids = rows.mapNotNull { (it as? TranscriptRow.Single)?.message?.id }
        assertEquals(listOf("u1", "f1", "u2"), ids)
    }

    @Test
    fun aNotificationBodyWithTheOldErrorUsesTheSameWords() {
        val body = olderServerError.removePrefix("error: ")
        assertEquals(CloudComputerNotice.TEXT, CloudComputerNotice.words(body))
        assertEquals("Done", CloudComputerNotice.words("Done"))
    }

    @Test
    fun theChipsStayAwayUntilTheBotAnswers() {
        assertTrue(FailedTurnActions.lastTurnFailed(listOf(user("u1"), failed("f1"))))
        assertTrue(FailedTurnActions.lastTurnFailed(listOf(failed("f1"), user("u2"))))
        assertFalse(FailedTurnActions.lastTurnFailed(listOf(failed("f1"), user("u2"), reply("r1"))))
        assertFalse(FailedTurnActions.lastTurnFailed(listOf(user("u1"), reply("r1"))))
        assertFalse(FailedTurnActions.lastTurnFailed(emptyList()))
    }

    @Test
    fun aTurnWhoseReplyCameBackDidNotFail() {
        val partial = failed("p1").let { it.copy(tool = it.tool!!.copy(partial = true)) }
        assertFalse(FailedTurnActions.lastTurnFailed(listOf(user("u1"), reply("r1"), partial)))
    }

    private val serverNotice = """
        {"id":"n1","role":"bot","kind":"activity","at":1.0,
         "tool":{"name":"error: Scout answered without a computer because no cloud desktop is set up.",
                 "ok":false,"notice":true,
                 "place":{"state":"cc-not-set-up","params":{"bot":"Scout"},"source":"works-on"}}}
    """.trimIndent()

    @Test
    fun aNoticeFromAServerThatRanTheTurnIsNotAFailure() {
        val message = CompanionJson.decodeFromString<Message>(serverNotice)
        assertEquals("cc-not-set-up", message.tool?.place?.state)
        assertTrue(isTurnNotice(message))
        assertFalse(isFailedTurn(message))
        assertEquals(true, message.tool?.statusOk)
        assertEquals("Scout answered without a computer because no cloud desktop is set up.", message.tool?.label)
        assertFalse(FailedTurnActions.lastTurnFailed(listOf(user("u1"), message)))
    }

    @Test
    fun aNoticeStaysInTheTranscriptWhenActivityIsHidden() {
        val message = CompanionJson.decodeFromString<Message>(serverNotice)
        val ids = transcriptRows(listOf(user("u1"), message, reply("r1")), ActivityDetail.HIDDEN)
            .mapNotNull { (it as? TranscriptRow.Single)?.message?.id }
        assertEquals(listOf("u1", "n1", "r1"), ids)
    }
}
