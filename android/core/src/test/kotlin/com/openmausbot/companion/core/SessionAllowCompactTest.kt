package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionAllowCompactTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CompanionClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "device-token")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun card(allowSession: Boolean?, tool: String? = "Bash", answered: String? = null) = OptionCard(
        title = "Run a command?",
        subtitle = "git status",
        options = listOf("Allow once", "Deny", "Always allow"),
        requestId = "r1",
        tool = tool,
        answered = answered,
        allowSession = allowSession,
    )

    private fun reply(body: String = "{}") = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `respond puts always in the body only for an allow with always`() = runBlocking {
        repeat(3) { server.enqueue(reply()) }
        client.respond("t1", "r1", "allow", always = true)
        client.respond("t1", "r1", "allow")
        client.respond("t1", "r1", "deny", always = true)
        val sent = List(3) { CompanionJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject }
        assertEquals("true", sent[0].getValue("always").jsonPrimitive.content)
        assertEquals("allow", sent[0].getValue("behavior").jsonPrimitive.content)
        assertFalse(sent[1].containsKey("always"))
        assertFalse(sent[2].containsKey("always"))
    }

    @Test
    fun `a card decodes allowSession and the button needs advanced mode, a permission card and a pending request`() {
        val decoded = CompanionJson.decodeFromString(
            OptionCard.serializer(),
            """{"title":"t","subtitle":"s","options":["Allow once","Deny"],"requestId":"r","tool":"Bash","allowSession":true}""",
        )
        assertEquals(true, decoded.allowSession)
        assertTrue(decoded.offersSessionAllow(advancedMode = true))
        assertFalse(decoded.offersSessionAllow(advancedMode = false))
        assertFalse(card(allowSession = false).offersSessionAllow(advancedMode = true))
        assertFalse(card(allowSession = null).offersSessionAllow(advancedMode = true))
        assertFalse(card(allowSession = true, tool = null).offersSessionAllow(advancedMode = true))
        assertFalse(card(allowSession = true, answered = "allow").offersSessionAllow(advancedMode = true))
    }

    @Test
    fun `compact posts the thread and sends nothing else`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202).setHeader("Content-Type", "application/json").setBody("""{"ok":true,"threadId":"t2"}"""))
        server.enqueue(reply())
        client.compact("b1", "t2")
        client.compact("b1")
        val first = server.takeRequest()
        assertEquals("POST /api/bots/b1/compact", "${first.method} ${first.path}")
        assertEquals("t2", CompanionJson.parseToJsonElement(first.body.readUtf8()).jsonObject.getValue("threadId").jsonPrimitive.content)
        val second = server.takeRequest()
        assertFalse(CompanionJson.parseToJsonElement(second.body.readUtf8().ifEmpty { "{}" }).jsonObject.containsKey("threadId"))
    }
}
