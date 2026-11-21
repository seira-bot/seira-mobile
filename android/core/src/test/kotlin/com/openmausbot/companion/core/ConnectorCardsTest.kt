package com.openmausbot.companion.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class ConnectorCardsTest {
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

    private fun message(json: String) = CompanionJson.decodeFromString<Message>(json)

    private fun connectorMessage(card: String, kind: String = "connector") =
        message("""{"id":"m1","role":"bot","kind":"$kind","at":1,"text":"fallback","connector":$card}""")

    private fun signInMessage(card: String) =
        message("""{"id":"m2","role":"bot","kind":"signin","at":1,"text":"fallback","signIn":$card}""")

    @Test
    fun aConnectorMessageDecodesItsCard() {
        val decoded = connectorMessage(
            """{"source":"mcp","slug":"linear","label":"Linear","description":"Read issues","status":"authorizing","resumeKey":"k","alias":"work","error":"oops","resumed":false,"stopped":true,"dismissed":false,"superseded":true}""",
        )
        assertEquals(Message.Kind.CONNECTOR, decoded.kind)
        val card = assertNotNull(decoded.connector)
        assertEquals("linear", card.slug)
        assertEquals("Linear", card.label)
        assertTrue(card.isMcp)
        assertEquals(ConnectorCardData.Status.AUTHORIZING, card.connectorStatus)
        assertEquals("oops", card.error)
        assertTrue(card.isGone)
        assertEquals(true, card.stopped)
    }

    @Test
    fun anUnknownConnectorStatusStillAsksForAConnection() {
        val card = assertNotNull(connectorMessage("""{"slug":"gmail","label":"Gmail","status":"quantum"}""").connector)
        assertEquals(ConnectorCardData.Status.REQUIRED, card.connectorStatus)
        assertFalse(card.isMcp)
        assertFalse(card.isGone)
        assertEquals(ConnectorCardData.Status.FAILED, ConnectorCardData(slug = "a", label = "A", status = "failed").connectorStatus)
        assertEquals(ConnectorCardData.Status.CONNECTED, ConnectorCardData(slug = "a", label = "A", status = "connected").connectorStatus)
    }

    @Test
    fun aSignInMessageDecodesItsCardAndAnUnknownPlaceHasNoScreen() {
        val decoded = signInMessage(
            """{"url":"https://example.com/login","site":"example.com","reason":"Needs your account","place":"cloud","done":true,"savedLogin":"example.com","resumed":true,"error":"x","stopped":false,"answeredInChat":false}""",
        )
        assertEquals(Message.Kind.SIGNIN, decoded.kind)
        val card = assertNotNull(decoded.signIn)
        assertEquals("example.com", card.site)
        assertEquals(SignInRequestCardData.Place.CLOUD, card.signInPlace)
        assertEquals(SignInRequestCardData.Outcome.DONE, card.outcome())
        assertEquals("Needs your account", card.reason)

        val future = assertNotNull(signInMessage("""{"url":"https://example.com","site":"example.com","place":"hologram"}""").signIn)
        assertEquals(SignInRequestCardData.Place.LOCAL, future.signInPlace)
        assertNull(ConnectorCards.openTarget(future))
        val missing = assertNotNull(signInMessage("""{"url":"https://example.com","site":"example.com"}""").signIn)
        assertEquals(SignInRequestCardData.Place.LOCAL, missing.signInPlace)
    }

    @Test
    fun anUnreadableCardLeavesTheTextAndTheKind() {
        val broken = connectorMessage("""{"slug":17}""")
        assertNull(broken.connector)
        assertEquals("fallback", broken.text)
        assertEquals(Message.Kind.CONNECTOR, broken.kind)
        assertNull(signInMessage("""{"site":"example.com"}""").signIn)
        assertNull(message("""{"id":"m3","role":"bot","kind":"signin","at":1,"text":"t"}""").signIn)
    }

    @Test
    fun waitsAndOutcomeFollowTheDesktopHelpers() {
        fun card(
            done: Boolean? = null,
            cancelled: Boolean? = null,
            superseded: Boolean? = null,
            answeredInChat: Boolean? = null,
        ) = SignInRequestCardData("https://a.example", "a.example", done = done, cancelled = cancelled, superseded = superseded, answeredInChat = answeredInChat)

        assertTrue(card().waits())
        assertNull(card().outcome())
        assertFalse(card(done = true).waits())
        assertFalse(card(cancelled = true).waits())
        assertFalse(card(superseded = true).waits())
        assertFalse(card(answeredInChat = true).waits())
        assertTrue(card(done = false, cancelled = false).waits())
        assertEquals(SignInRequestCardData.Outcome.DONE, card(done = true).outcome())
        assertEquals(SignInRequestCardData.Outcome.CANCELLED, card(cancelled = true).outcome())
        assertEquals(SignInRequestCardData.Outcome.DONE, card(done = true, cancelled = true).outcome())
    }

    @Test
    fun openGoesToTheScreenOfThePlace() {
        fun target(place: String) = ConnectorCards.openTarget(SignInRequestCardData("https://a.example", "a.example", place = place))
        assertEquals(SignInOpenTarget.BrowserScreen, target("browser"))
        assertEquals(SignInOpenTarget.ComputerScreen, target("cloud"))
        assertEquals(SignInOpenTarget.ComputerScreen, target("vm"))
        assertNull(target("local"))
    }

    @Test
    fun onlyAnHttpsLinkWithAHostIsOpened() {
        assertNotNull(ConnectorCards.secureLink("https://auth.example.com/start?x=1"))
        assertNotNull(ConnectorCards.secureLink("HTTPS://auth.example.com"))
        listOf(
            null,
            "",
            "http://auth.example.com",
            "javascript:alert(1)",
            "intent://x#Intent;end",
            "file:///etc/passwd",
            "https:///nohost",
            "https://",
            "auth.example.com",
            "not a url",
        ).forEach { assertNull(ConnectorCards.secureLink(it), "must refuse $it") }
    }

    @Test
    fun authorizeSendsTheThreadAndReturnsTheLink() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"url":"https://auth.example.com/start"}"""))
        val link = client.authorizeConnectorCard("bot-1", "msg-1", "thread-9")
        assertEquals("https://auth.example.com/start", link.toString())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bots/bot-1/connector-cards/msg-1/authorize", request.path)
        assertEquals("thread-9", CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject["threadId"]?.jsonPrimitive?.content)
        assertEquals("Bearer paired-token", request.getHeader("Authorization"))
    }

    @Test
    fun authorizeRefusesALinkThatIsNotHttps() = runBlocking {
        for (url in listOf("http://auth.example.com", "javascript:alert(1)", "https:///x", "")) {
            server.enqueue(MockResponse().setBody("""{"url":"$url"}"""))
            assertFailsWith<APIError.BadUrl>("must refuse $url") { client.authorizeConnectorCard("bot-1", "msg-1", "t") }
        }
    }

    @Test
    fun authorizeSurfacesTheServerSentence() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"Sign in on your computer."}"""))
        val error = assertFailsWith<APIError.Status> { client.authorizeConnectorCard("bot-1", "msg-1", "t") }
        assertEquals(409, error.code)
        assertEquals("Sign in on your computer.", error.message)
    }

    @Test
    fun statusIsAGetWithTheThreadInTheQuery() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"connected":true,"pending":false,"status":"connected"}"""))
        val status = client.connectorCardStatus("bot-1", "msg-1", "thread 9&x")
        assertTrue(status.connected)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/bots/bot-1/connector-cards/msg-1/status?threadId=thread%209%26x", request.path)

        server.enqueue(MockResponse().setBody("""{"connected":false}"""))
        assertFalse(client.connectorCardStatus("bot-1", "msg-1", "t").connected)
    }

    @Test
    fun resumeAndDismissPostTheThread() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"resumed":true}"""))
        client.resumeConnectorCard("bot-1", "msg-1", "thread-9")
        val resume = server.takeRequest()
        assertEquals("POST", resume.method)
        assertEquals("/api/bots/bot-1/connector-cards/msg-1/resume", resume.path)
        assertTrue(resume.body.readUtf8().contains("\"threadId\":\"thread-9\""))

        server.enqueue(MockResponse().setBody("""{"dismissed":true}"""))
        client.dismissConnectorCard("bot-1", "msg-1", "thread-9")
        val dismiss = server.takeRequest()
        assertEquals("/api/bots/bot-1/connector-cards/msg-1/dismiss", dismiss.path)
        assertTrue(dismiss.body.readUtf8().contains("\"threadId\":\"thread-9\""))

        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"Finish connecting every requested app first."}"""))
        val refused = assertFailsWith<APIError.Status> { client.resumeConnectorCard("bot-1", "msg-1", "t") }
        assertEquals("Finish connecting every requested app first.", refused.message)
    }

    @Test
    fun signInAnswersGoToDoneAndCancelWithTheThread() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        client.answerSignIn("bot-1", "msg-2", "thread-9", SignInChoice.DONE)
        val done = server.takeRequest()
        assertEquals("POST", done.method)
        assertEquals("/api/bots/bot-1/sign-in-cards/msg-2/done", done.path)
        assertTrue(done.body.readUtf8().contains("\"threadId\":\"thread-9\""))

        server.enqueue(MockResponse().setBody("{}"))
        client.answerSignIn("bot-1", "msg-2", "thread-9", SignInChoice.CANCEL)
        assertEquals("/api/bots/bot-1/sign-in-cards/msg-2/cancel", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"no such sign-in request"}"""))
        assertFailsWith<APIError.Status> { client.answerSignIn("bot-1", "msg-2", "t", SignInChoice.DONE) }
    }

    @Test
    fun anIdIsOneEscapedPathSegment() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        client.answerSignIn("bot/../x", "m 1", "t", SignInChoice.DONE)
        assertEquals("/api/bots/bot%2F..%2Fx/sign-in-cards/m%201/done", server.takeRequest().path)
        assertFailsWith<APIError.BadUrl> { client.dismissConnectorCard("..", "m", "t") }
        assertFailsWith<APIError.BadUrl> { client.authorizeConnectorCard("b", "", "t") }
    }

    private class FakeClock {
        var now = 1_000_000L
        val pauses = mutableListOf<Long>()
        suspend fun pause(ms: Long) {
            pauses += ms
            now += ms
        }
    }

    @Test
    fun theStatusLoopChecksEveryFourSecondsAndStopsWhenConnected() = runTest {
        val clock = FakeClock()
        var checks = 0
        val connected = ConnectorCards.watchStatus(clock.now, { clock.now }, clock::pause) { ++checks == 4 }
        assertTrue(connected)
        assertEquals(4, checks)
        assertEquals(listOf(4_000L, 4_000L, 4_000L), clock.pauses)
    }

    @Test
    fun theStatusLoopStopsAfterTwelveMinutes() = runTest {
        val clock = FakeClock()
        var checks = 0
        val connected = ConnectorCards.watchStatus(clock.now, { clock.now }, clock::pause) { checks++; false }
        assertFalse(connected)
        assertEquals(12 * 60 * 1_000L, clock.pauses.sum())
        assertEquals(180, checks)
    }

    @Test
    fun theStatusLoopKeepsItsStartAcrossARestart() = runTest {
        val clock = FakeClock()
        val started = clock.now
        clock.now += 11 * 60 * 1_000L + 59_000L
        var checks = 0
        assertFalse(ConnectorCards.watchStatus(started, { clock.now }, clock::pause) { checks++; false })
        assertEquals(1, checks, "one last check fits before the twelve minutes end")
        checks = 0
        clock.now = started + ConnectorCards.STATUS_WATCH_MS
        assertFalse(ConnectorCards.watchStatus(started, { clock.now }, clock::pause) { checks++; false })
        assertEquals(0, checks, "an expired watch asks nothing")
    }

    @Test
    fun aFailedCheckCountsAsNotConnected() = runTest {
        val clock = FakeClock()
        var checks = 0
        val connected = ConnectorCards.watchStatus(clock.now, { clock.now }, clock::pause) {
            checks++
            if (checks < 3) error("offline") else true
        }
        assertTrue(connected)
        assertEquals(3, checks)
    }
}
