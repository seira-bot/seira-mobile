package bot.seira.companion.core

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallRecordTest {
    private fun decode(json: String): Message = CompanionJson.decodeFromString(json)

    private fun said(id: String, text: String, callId: String?, via: String? = "call", at: Double = 1.0) =
        Message(id, Message.Role.USER, Message.Kind.TEXT, at, text = text, via = via, callId = callId)

    @Test
    fun decodesACallWithItsRecord() {
        val message = decode(
            """{"id":"m1","role":"bot","kind":"call","at":5,"text":"Call with Ada · 1:42",
               "call":{"callId":"c1","botId":"b1","client":"android","startedAt":1000,"endedAt":103000,
               "seconds":102,"endReason":"hung-up","title":"Plan the launch","newerField":1}}""",
        )
        assertEquals(Message.Kind.CALL, message.kind)
        val call = assertNotNull(message.call)
        assertEquals("c1", call.callId)
        assertEquals(102.0, call.seconds)
        assertEquals("Plan the launch", call.title)
    }

    @Test
    fun aCallWithoutItsRecordKeepsItsText() {
        val message = decode("""{"id":"m1","role":"bot","kind":"call","at":1,"text":"Call with Ada · 0:07"}""")
        assertEquals(Message.Kind.CALL, message.kind)
        assertNull(message.call)
        assertEquals("Call with Ada · 0:07", previewText(message))
    }

    @Test
    fun anUnknownEndReasonDecodes() {
        val message = decode(
            """{"id":"m1","role":"bot","kind":"call","at":1,"call":{"callId":"c1","endReason":"solar-flare","seconds":3}}""",
        )
        assertEquals("solar-flare", message.call?.endReason)
        assertNull(message.call?.title)
    }

    @Test
    fun aSpokenLineCarriesItsCallId() {
        val message = decode("""{"id":"u1","role":"user","kind":"text","at":1,"text":"hi","via":"call","callId":"c1"}""")
        assertEquals("c1", message.callId)
        assertNull(decode("""{"id":"u2","role":"user","kind":"text","at":1,"text":"hi"}""").callId)
    }

    @Test
    fun reEncodesTheKind() {
        val message = Message("m", Message.Role.BOT, Message.Kind.CALL, 1.0)
        assertEquals(Message.Kind.CALL, decode(CompanionJson.encodeToString(Message.serializer(), message)).kind)
    }

    @Test
    fun durationReadsAsMinutesAndSeconds() {
        assertEquals("0:00", CallRecords.formatCallDuration(0.0))
        assertEquals("0:07", CallRecords.formatCallDuration(7.0))
        assertEquals("1:42", CallRecords.formatCallDuration(102.0))
        assertEquals("59:59", CallRecords.formatCallDuration(3_599.9))
    }

    @Test
    fun durationReadsAsHoursFromOneHour() {
        assertEquals("1:00:00", CallRecords.formatCallDuration(3_600.0))
        assertEquals("1:02:05", CallRecords.formatCallDuration(3_725.0))
    }

    @Test
    fun durationOfSomethingThatIsNotALengthIsZero() {
        assertEquals("0:00", CallRecords.formatCallDuration(-5.0))
        assertEquals("0:00", CallRecords.formatCallDuration(Double.NaN))
        assertEquals("0:00", CallRecords.formatCallDuration(Double.POSITIVE_INFINITY))
    }

    @Test
    fun linesAreWhatThePersonSaidOnThatCall() {
        val transcript = listOf(
            said("u1", "Start the report", "c1"),
            said("u2", "Another call", "c2"),
            said("u3", "Typed message", null, via = null),
            Message("b1", Message.Role.BOT, Message.Kind.TEXT, 2.0, text = "On it"),
            said("u4", "  ", "c1"),
            said("u5", "Send it", "c1"),
        )
        assertEquals(listOf("u1", "u5"), CallRecords.callRecordLines(transcript, "c1").map { it.id })
        assertEquals(emptyList(), CallRecords.callRecordLines(transcript, ""))
        assertEquals(emptyList(), CallRecords.callRecordLines(transcript, "missing"))
    }

    @Test
    fun theCallIsPartialWhenItsStartIsNotLoaded() {
        val call = CallRecord(callId = "c1", startedAt = 100.0)
        val loaded = listOf(said("u1", "hi", "c1", at = 150.0))
        assertTrue(CallRecords.isPartial(loaded, call, hasMore = true))
        assertFalse(CallRecords.isPartial(loaded, call, hasMore = false))
        assertFalse(CallRecords.isPartial(listOf(said("u0", "hi", "c1", at = 90.0)), call, hasMore = true))
        assertFalse(CallRecords.isPartial(emptyList(), call, hasMore = true))
    }
}
