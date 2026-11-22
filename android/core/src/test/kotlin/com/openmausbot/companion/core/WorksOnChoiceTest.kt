package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorksOnChoiceTest {
    private fun bot(computer: String? = "cloud", backend: String? = null, vpsAlias: String? = null) = Bot(
        id = "bot-1", threadId = "t1", name = "Scout", title = "", description = "",
        notifications = true, color = "#3366ff", unread = false,
        modelSelection = ModelSelection("codex", "gpt-5"), createdAt = 1.0,
        computer = computer, cloudBackend = backend, vpsAlias = vpsAlias,
    )

    private fun offer(admin: Boolean, running: Boolean, bot: Bot, ready: Boolean) =
        WorksOnChoice.offer(admin, running, bot.computer, bot.cloudBackend, ready)

    private fun notice(id: String) = Message(
        id, Message.Role.BOT, Message.Kind.ACTIVITY, 1.0,
        tool = ToolActivity(name = "This bot's cloud computer isn't set up.", ok = true, notice = true, place = FailedPlace("cc-not-set-up")),
    )

    private fun legacy(id: String) = Message(
        id, Message.Role.BOT, Message.Kind.ACTIVITY, 1.0,
        tool = ToolActivity(name = "error: Connect your Boat account in AI.", ok = false),
    )

    private fun user(id: String) = Message(id, Message.Role.USER, Message.Kind.TEXT, 1.0, text = "hi")
    private fun reply(id: String) = Message(id, Message.Role.BOT, Message.Kind.TEXT, 1.0, text = "ok")

    @Test
    fun bothActionsWhenAdminIdleAndOwnServerReady() {
        assertEquals(WorksOnOffer(true, true), offer(true, false, bot(), true))
    }

    @Test
    fun onlyWithoutAComputerWhenNoOwnServer() {
        assertEquals(WorksOnOffer(true, false), offer(true, false, bot(), false))
    }

    @Test
    fun nothingForAPhoneThatMayNotAdminister() {
        assertNull(offer(false, false, bot(), true))
    }

    @Test
    fun nothingWhileATurnRuns() {
        assertNull(offer(true, true, bot(), true))
    }

    @Test
    fun aBotWithoutAComputerOffersOnlyItsOwnServer() {
        assertEquals(WorksOnOffer(false, true), offer(true, false, bot(computer = "off"), true))
        assertNull(offer(true, false, bot(computer = "off"), false))
    }

    @Test
    fun aBotOnItsOwnServerOffersOnlyNoComputer() {
        assertEquals(WorksOnOffer(true, false), offer(true, false, bot(backend = "vps"), true))
    }

    @Test
    fun ownServerIsReadyFromTheBotOrTheConfig() {
        assertTrue(WorksOnChoice.ownServerReady(bot(vpsAlias = "my-vps"), null))
        assertTrue(WorksOnChoice.ownServerReady(bot(), ConfigStatus(vps = VpsStatus(true))))
        assertFalse(WorksOnChoice.ownServerReady(bot(vpsAlias = " "), ConfigStatus(vps = VpsStatus(false))))
        assertFalse(WorksOnChoice.ownServerReady(bot(), ConfigStatus()))
        assertFalse(WorksOnChoice.ownServerReady(null, null))
    }

    @Test
    fun onlyTheNewestNoticeCarriesTheActions() {
        val messages = listOf(user("u1"), notice("n1"), reply("r1"), user("u2"), notice("n2"))
        assertEquals("n2", WorksOnChoice.noticeMessageId(messages))
    }

    @Test
    fun theNewServerRowAndTheOlderServerRowBothCount() {
        assertEquals("n1", WorksOnChoice.noticeMessageId(listOf(user("u1"), notice("n1"))))
        assertEquals("l1", WorksOnChoice.noticeMessageId(listOf(user("u1"), legacy("l1"))))
    }

    @Test
    fun aRepeatedRowTheTranscriptHidesIsNotTheNewest() {
        val messages = listOf(user("u1"), legacy("l1"), user("u2"), legacy("l2"))
        assertEquals("l1", WorksOnChoice.noticeMessageId(messages))
    }

    @Test
    fun otherRowsAreNoNotice() {
        val other = Message("o1", Message.Role.BOT, Message.Kind.ACTIVITY, 1.0, tool = ToolActivity(name = "error: overloaded", ok = false))
        assertNull(WorksOnChoice.noticeMessageId(listOf(user("u1"), other)))
        val unrelatedNotice = Message(
            "o2", Message.Role.BOT, Message.Kind.ACTIVITY, 1.0,
            tool = ToolActivity(name = "x", notice = true, place = FailedPlace("cc-asleep")),
        )
        assertNull(WorksOnChoice.noticeMessageId(listOf(unrelatedNotice)))
    }

    @Test
    fun theWireReadsTheOptionalOwnServerFields() {
        val config = CompanionJson.decodeFromString<ConfigStatus>("""{"vps":{"configured":true}}""")
        assertEquals(true, config.vps?.configured)
        assertNull(CompanionJson.decodeFromString<ConfigStatus>("{}").vps)
        assertNull(CompanionJson.decodeFromString<ConfigStatus>("""{"vps":{}}""").vps?.configured)
    }

    @Test
    fun aRefusalKeepsTheServersSentenceWhenThereIsOne() {
        assertEquals("Set up your own server first.", WorksOnChoice.refusal(APIError.Status(409, "Set up your own server first.")))
        assertNull(WorksOnChoice.refusal(APIError.Status(403)))
        assertNull(WorksOnChoice.refusal(APIError.Transport("timeout")))
    }
}

class WorksOnClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CompanionClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "paired-token")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun botBody(computer: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
        """{"bot":{"id":"bot-1","threadId":"t1","name":"Scout","title":"","description":"",
            "notifications":true,"color":"#3366ff","unread":false,"createdAt":1.0,
            "modelSelection":{"instanceId":"codex","model":"gpt-5"},"computer":"$computer"}}""",
    )

    @Test
    fun withoutAComputerSendsExactlyComputerOff() = runBlocking {
        server.enqueue(botBody("off"))
        val bot = client.setWorksOn("bot-1", ownServer = false)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/bots/bot-1", request.path)
        val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("computer"), body.keys)
        assertEquals("off", body.getValue("computer").jsonPrimitive.content)
        assertEquals("off", bot.computer)
    }

    @Test
    fun ownServerSendsExactlyCloudAndVps() = runBlocking {
        server.enqueue(botBody("cloud"))
        client.setWorksOn("bot-1", ownServer = true)
        val body = CompanionJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(setOf("computer", "cloudBackend"), body.keys)
        assertEquals("cloud", body.getValue("computer").jsonPrimitive.content)
        assertEquals("vps", body.getValue("cloudBackend").jsonPrimitive.content)
    }

    @Test
    fun aRefusalCarriesTheServersSentence() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"Set up your own server first."}"""))
        val error = runCatching { client.setWorksOn("bot-1", ownServer = true) }.exceptionOrNull()
        assertEquals("Set up your own server first.", WorksOnChoice.refusal(error!!))
    }
}
