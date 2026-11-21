package com.openmausbot.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A credential card as the computer sends it, and what it means for the thread around it. */
class SecretCardMessageTest {
    private fun decode(secret: String) = CompanionJson.decodeFromString<Message>(
        """{"id":"m1","role":"bot","kind":"secret","at":1,"text":"Voice key needed","secret":$secret}""",
    )

    @Test
    fun decodesTheCardWithoutAValue() {
        val message = decode(
            """{"target":"ttsKey","label":"Voice key","description":"For spoken replies.","placeholder":"sk-…",""" +
                """"helpUrl":"https://example.com/keys","requestKey":"credential-request-1"}""",
        )
        assertEquals(Message.Kind.SECRET, message.kind)
        val secret = assertNotNull(message.secret)
        assertEquals("ttsKey", secret.target)
        assertEquals("credential-request-1", secret.requestKey)
        assertTrue(secret.isPending)
        assertEquals("Voice key", previewText(message))
    }

    @Test
    fun aSettledCardIsNoLongerPending() {
        listOf("provided", "dismissed", "superseded", "stopped").forEach { outcome ->
            assertFalse(decode("""{"target":"ttsKey","requestKey":"r1","$outcome":true}""").secret!!.isPending, outcome)
        }
    }

    @Test
    fun aPendingCardInThisTurnHoldsTheThreadForAnAnswer() {
        val pending = decode("""{"target":"ttsKey","requestKey":"r1"}""")
        val user = Message("u1", Message.Role.USER, Message.Kind.TEXT, 2.0, text = "Never mind")

        assertTrue(TurnTail.awaitsAnswer(listOf(pending)))
        assertFalse(TurnTail.awaitsAnswer(listOf(pending, user)), "a card from an earlier turn")
        assertFalse(TurnTail.awaitsAnswer(listOf(decode("""{"target":"ttsKey","requestKey":"r1","provided":true}"""))))
    }
}
