package bot.seira.companion.core

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BotSoulDecodingTest {
    private val base = """
        {"id":"bot-1","threadId":"thread-1","name":"Scout","title":"","description":"",
         "notifications":true,"color":"blue","unread":false,"createdAt":1.0,
         "modelSelection":{"instanceId":"codex","model":"gpt-5"}
    """.trimIndent()

    @Test
    fun anOlderComputerSendsNeitherFieldAndBothDecodeAsNull() {
        val bot = CompanionJson.decodeFromString<Bot>("$base}")
        assertNull(bot.soul)
        assertNull(bot.replyStyle)
    }

    @Test
    fun theInstructionsAndTheReplyStyleDecodeWhenSent() {
        val bot = CompanionJson.decodeFromString<Bot>("$base,\"soul\":\"Be brief.\",\"replyStyle\":\"conversational\"}")
        assertEquals("Be brief.", bot.soul)
        assertEquals(ReplyStyle.CONVERSATIONAL.wire, bot.replyStyle)
    }
}
