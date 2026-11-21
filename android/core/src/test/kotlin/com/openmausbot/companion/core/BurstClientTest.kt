package com.openmausbot.companion.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BurstClientTest {
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

    @Test
    fun readsTheTaskList() = runBlocking {
        server.enqueue(
            json(
                """{"burstId":"bu-1","phase":"running","instructions":"Be brief","items":["a","b"],
                   "children":[{"seq":1,"title":"a","status":"running","threadId":"t-1"}]}""",
            ),
        )
        val view = client.burst("bu-1")
        server.takeRequest().let { assertEquals("GET /api/bursts/bu-1", "${it.method} ${it.path}") }
        assertEquals("t-1", view.children.single().threadId)
    }

    @Test
    fun runCancelAndRetryPostWithoutAPlan() = runBlocking {
        repeat(3) { server.enqueue(json("{}")) }
        client.burstAction("bu-1", BurstAction.RUN)
        client.burstAction("bu-1", BurstAction.CANCEL)
        client.burstAction("bu-1", BurstAction.RETRY)
        listOf("run", "cancel", "retry").forEach { action ->
            val request = server.takeRequest()
            assertEquals("POST /api/bursts/bu-1/$action", "${request.method} ${request.path}")
            assertEquals("Bearer device-token", request.getHeader("Authorization"))
            assertEquals(0L, request.bodySize)
        }
    }

    @Test
    fun editPostsTheInstructionsAndTheItems() = runBlocking {
        server.enqueue(json("{}"))
        client.burstAction("bu-1", BurstAction.EDIT, BurstEdit("Be brief", listOf("one", "two")))
        val request = server.takeRequest()
        assertEquals("POST /api/bursts/bu-1/edit", "${request.method} ${request.path}")
        val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("instructions", "items"), body.keys)
        assertEquals("Be brief", body.getValue("instructions").jsonPrimitive.content)
        assertEquals(listOf("one", "two"), body.getValue("items").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun onlyAnEditCarriesAPlan() {
        assertFailsWith<IllegalArgumentException> { runBlocking { client.burstAction("bu-1", BurstAction.EDIT) } }
        assertFailsWith<IllegalArgumentException> {
            runBlocking { client.burstAction("bu-1", BurstAction.RUN, BurstEdit("", listOf("a", "b"))) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun anUnsafeBurstIdNeverReachesThePath() {
        for (id in listOf("", "../x", "a/b", "a b", "a?b", "a%2Fb")) {
            assertFailsWith<APIError.BadUrl>(id) { runBlocking { client.burst(id) } }
            assertFailsWith<APIError.BadUrl>(id) { runBlocking { client.burstAction(id, BurstAction.RUN) } }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun theSessionReportsTheComputersWordsOnAFailure() = runTest {
        val session = session()
        server.enqueue(json("""{"error":"Already running"}""", code = 409))
        val failed = session.burstAction("bu-1", BurstAction.RUN)
        assertEquals(BurstOutcome.Failed("Already running"), failed)
        assertNull(session.actionError)

        server.enqueue(json("{}"))
        assertEquals(BurstOutcome.Done, session.burstAction("bu-1", BurstAction.CANCEL))
        server.takeRequest()
        assertEquals("POST /api/bursts/bu-1/cancel", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun theSessionReadsTheTaskListAndGivesNullWhenItCannot() = runTest {
        val session = session()
        server.enqueue(json("""{"burstId":"bu-1","phase":"done","children":[{"seq":1,"title":"a","status":"done"}]}"""))
        assertEquals("a", session.burstView("bu-1")?.children?.single()?.title)
        server.enqueue(json("""{"error":"gone"}""", code = 404))
        assertNull(session.burstView("bu-1"))
        assertNull(session.burstView("../x"))
    }

    @Test
    fun anUnpairedSessionFailsWithoutAskingTheComputer() = runTest {
        val session = session(paired = false)
        assertIs<BurstOutcome.Failed>(session.burstAction("bu-1", BurstAction.RUN))
        assertNull(session.burstView("bu-1"))
        assertEquals(0, server.requestCount)
    }

    private suspend fun TestScope.session(paired: Boolean = true): Session {
        val connection = requireNotNull(Connection.parse(server.url("/").toString()))
        return Session(
            scope = backgroundScope,
            connectionStore = object : ConnectionStore {
                override suspend fun load(): Connection? = if (paired) connection else null
                override suspend fun save(connection: Connection) = Unit
                override suspend fun clear() = Unit
                override suspend fun loadRegistry() = ConnectionRegistryRestore(
                    ConnectionRegistry(if (paired) listOf(connection) else emptyList(), if (paired) connection.id else null),
                    migratedLegacyConnection = false,
                )
            },
            tokenStore = object : TokenStore {
                override suspend fun save(connectionId: String, token: String) = Unit
                override suspend fun read(connectionId: String): TokenStore.ReadResult =
                    TokenStore.ReadResult.Found("device-token")
                override suspend fun remove(connectionId: String) = Unit
            },
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Pixel" },
            eventsFn = { _, _, _ -> emptyFlow() },
            hydrateFn = { _, _ -> Fleet(emptyList(), emptyList()) },
        ).also { it.awaitRestored() }
    }

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
