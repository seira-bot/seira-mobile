package bot.seira.companion.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PushAlertTest {
    private val valid = mapOf(
        "v" to "1",
        "kind" to "approval",
        "botId" to "bot-1",
        "threadId" to "task_1",
        "title" to "Scout needs approval",
        "tag" to "openmaus.task_1.7",
        "channelId" to "approval_question",
        "pairing" to "0b6f2c1e-4d5a-4f43-8c5e-2d7f1a9b3c10",
        "roomId" to "room-1",
        "requestId" to "req-9",
    )

    @Test
    fun `a version 1 data message is read in full`() {
        val alert = assertNotNull(PushAlert.parse(valid))
        assertEquals("approval", alert.kind)
        assertEquals("bot-1", alert.botId)
        assertEquals("task_1", alert.threadId)
        assertEquals("Scout needs approval", alert.title)
        assertEquals("openmaus.task_1.7", alert.tag)
        assertEquals("approval_question", alert.channelId)
        assertEquals("0b6f2c1e-4d5a-4f43-8c5e-2d7f1a9b3c10", alert.pairing)
        assertEquals("room-1", alert.roomId)
        assertEquals("req-9", alert.requestId)
        assertEquals(NotificationTarget.from("bot-1", "task_1", alert.pairing), alert.target)
    }

    @Test
    fun `the optional keys may be missing`() {
        val alert = assertNotNull(PushAlert.parse(valid - "pairing" - "roomId" - "requestId" - "channelId"))
        assertNull(alert.pairing)
        assertNull(alert.roomId)
        assertNull(alert.requestId)
        assertNull(alert.channelId)
    }

    @Test
    fun `the older control plane shape is read from its body`() {
        val alert = assertNotNull(
            PushAlert.parse(
                mapOf(
                    "title" to "Scout finished",
                    "tag" to "openmaus.task_1.8",
                    "channelId" to "bot_messages",
                    "body" to """{"v":1,"kind":"done","botId":"bot-1","botName":"Scout","threadId":"task_1","pairing":"0b6f2c1e-4d5a-4f43"}""",
                ),
            ),
        )
        assertEquals("done", alert.kind)
        assertEquals("Scout finished", alert.title)
        assertEquals("bot_messages", alert.channelId)
        assertEquals("0b6f2c1e-4d5a-4f43", alert.pairing)
    }

    @Test
    fun `anything that is not a valid alert is dropped`() {
        val drops = listOf(
            "wrong version" to valid + ("v" to "2"),
            "no version and no body" to valid - "v",
            "no kind" to valid - "kind",
            "kind with capitals" to valid + ("kind" to "Approval"),
            "no bot" to valid - "botId",
            "bot with a slash" to valid + ("botId" to "a/b"),
            "no thread" to valid - "threadId",
            "empty thread" to valid + ("threadId" to ""),
            "no title" to valid - "title",
            "blank title" to valid + ("title" to "   "),
            "title over 200 code points" to valid + ("title" to "x".repeat(201)),
            "no tag" to valid - "tag",
            "tag with a space" to valid + ("tag" to "a b"),
            "short pairing" to valid + ("pairing" to "abc"),
            "room with a slash" to valid + ("roomId" to "a/b"),
            "request with a space" to valid + ("requestId" to "a b"),
            "body that is not JSON" to mapOf("body" to "{nope", "title" to "t", "tag" to "t"),
            "body with another version" to mapOf(
                "body" to """{"v":2,"kind":"done","botId":"b","threadId":"t"}""",
                "title" to "t",
                "tag" to "t",
            ),
        )
        drops.forEach { (name, data) -> assertNull(PushAlert.parse(data), name) }
    }

    @Test
    fun `a title of exactly 200 code points passes`() {
        assertNotNull(PushAlert.parse(valid + ("title" to "x".repeat(200))))
    }
}
