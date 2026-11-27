package com.openmausbot.companion.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class BotFlagsClientTest {
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

    private fun botBody() = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
        """{"bot":{"id":"bot-1","threadId":"t1","name":"Scout","title":"","description":"",
            "notifications":true,"color":"#3366ff","unread":true,"pinned":true,"createdAt":1.0,
            "modelSelection":{"instanceId":"codex","model":"gpt-5"}}}""",
    )

    @Test
    fun pinSendsOnlyPinnedAndThePairedHeader() = runBlocking {
        server.enqueue(botBody())
        val bot = client.setBotFlags("bot-1", pinned = true)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/bots/bot-1", request.path)
        assertEquals("1", request.getHeader("x-seira-companion"))
        val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("pinned"), body.keys)
        assertEquals("true", body.getValue("pinned").jsonPrimitive.content)
        assertEquals(true, bot.pinned)
    }

    @Test
    fun markUnreadSendsOnlyUnread() = runBlocking {
        server.enqueue(botBody())
        client.setBotFlags("bot-1", unread = true)
        val request = server.takeRequest()
        assertEquals("1", request.getHeader("x-seira-companion"))
        val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("unread"), body.keys)
    }
}

class BotDepartmentsTest {
    private fun bot(id: String, section: String?): Bot {
        val extra = if (section == null) "" else ",\"section\":\"$section\""
        return CompanionJson.decodeFromString(
            """{"id":"$id","threadId":"t-$id","name":"$id","title":"","description":"","notifications":true,
                "color":"#000000","unread":false,"createdAt":1.0,
                "modelSelection":{"instanceId":"codex","model":"m"}$extra}""",
        )
    }

    @Test
    fun namesAreDistinctSortedAndSkipBlank() {
        val bots = listOf(bot("a", "Sales"), bot("b", " "), bot("c", "ops"), bot("d", "sales"), bot("e", null))
        assertEquals(listOf("ops", "Sales"), BotDepartments.names(bots))
    }

    @Test
    fun namesKeepADepartmentWithNoBot() {
        val bots = listOf(bot("a", "Sales"))
        assertEquals(listOf("Legal", "sales"), BotDepartments.names(bots, listOf("sales", "Legal", " ")))
    }

    @Test
    fun aNewNameNeedsOneToSixtyCharacters() {
        assertNull(BotDepartments.cleanName("   "))
        assertNull(BotDepartments.cleanName("x".repeat(61)))
        assertEquals("Sales", BotDepartments.cleanName("  Sales "))
        assertEquals(60, BotDepartments.cleanName("x".repeat(60))?.length)
    }
}
