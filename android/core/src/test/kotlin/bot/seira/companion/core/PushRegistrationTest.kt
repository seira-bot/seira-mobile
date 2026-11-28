package bot.seira.companion.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy

/** Push registration against a fake computer, on the wall clock because the requests are real. */
class PushRegistrationTest {
    private lateinit var scope: CoroutineScope
    private lateinit var airServer: MockWebServer
    private lateinit var proServer: MockWebServer
    private lateinit var air: Connection
    private lateinit var pro: Connection
    private val seen = LinkedBlockingQueue<Seen>()
    private val tokens = mutableMapOf("air" to "air-token", "pro" to "pro-token")
    private var hang = false

    private data class Seen(val computer: String, val request: RecordedRequest, val credentialKept: Boolean)

    @BeforeTest
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        airServer = server("air")
        proServer = server("pro")
        air = requireNotNull(Connection.parse(airServer.url("/").toString())).copy(id = "air", name = "Air")
        pro = requireNotNull(Connection.parse(proServer.url("/").toString())).copy(id = "pro", name = "Pro")
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        runCatching { airServer.shutdown() }
        runCatching { proServer.shutdown() }
    }

    private fun server(computer: String): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path != "/api/push/device") return MockResponse().setResponseCode(404)
                seen += Seen(computer, request, tokens.containsKey(computer))
                if (hang) return MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        if (request.method == "PUT") {
                            """{"push":"on","device":{"platform":"fcm","pairing":"$computer","kinds":[],"updatedAt":1},"relay":"registered"}"""
                        } else {
                            """{"push":"off","device":null}"""
                        },
                    )
            }
        }
        start()
    }

    private class Registry(initial: ConnectionRegistry) : ConnectionStore {
        var registry = initial
        override suspend fun load(): Connection? = registry.activeConnection
        override suspend fun save(connection: Connection) { registry = registry.upsert(connection) }
        override suspend fun clear() { registry = ConnectionRegistry() }
        override suspend fun loadRegistry() = ConnectionRegistryRestore(registry, migratedLegacyConnection = false)
        override suspend fun saveRegistry(registry: ConnectionRegistry) { this.registry = registry.normalized() }
    }

    private fun session(store: PushRegistrationStore, vararg saved: Connection): Session {
        val tokenStore = object : TokenStore {
            override suspend fun save(connectionId: String, token: String) { tokens[connectionId] = token }
            override suspend fun read(connectionId: String): TokenStore.ReadResult =
                tokens[connectionId]?.let(TokenStore.ReadResult::Found) ?: TokenStore.ReadResult.Missing
            override suspend fun remove(connectionId: String) { tokens.remove(connectionId) }
        }
        return Session(
            scope = scope,
            connectionStore = Registry(ConnectionRegistry(saved.toList(), saved.first().id)),
            tokenStore = tokenStore,
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Pixel" },
            eventsFn = { _, _, _ ->
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "c:1", resumed = false), seq = 1))
                    awaitCancellation()
                }
            },
            hydrateFn = { _, _ -> Fleet(emptyList(), emptyList()) },
            metadataFn = { throw IllegalStateException("not under test") },
            pushStore = store,
        )
    }

    private fun Session.awaitLive(id: String) = runBlocking {
        withTimeout(10_000) { status.first { it is Session.Status.Live && connection.value?.id == id } }
    }

    private fun next(): Seen = assertNotNull(seen.poll(10, TimeUnit.SECONDS), "no push request arrived")

    private fun assertQuiet() = assertNull(seen.poll(400, TimeUnit.MILLISECONDS), "an unexpected push request arrived")

    private fun bodyOf(request: RecordedRequest): JsonObject =
        Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    @Test
    fun `AC-01 a Live stream registers with exactly platform, token and pairing`(): Unit = runBlocking {
        val session = session(InMemoryPushRegistrationStore(), air)
        session.awaitRestored()
        session.applyPushToken("token-one")
        session.connect()
        session.awaitLive("air")

        val put = next()
        assertEquals("PUT", put.request.method)
        assertEquals("Bearer air-token", put.request.getHeader("Authorization"))
        val body = bodyOf(put.request)
        assertEquals(setOf("platform", "token", "pairing"), body.keys)
        assertEquals("fcm", body.getValue("platform").jsonPrimitive.content)
        assertEquals("token-one", body.getValue("token").jsonPrimitive.content)
        assertEquals("air", body.getValue("pairing").jsonPrimitive.content)
    }

    @Test
    fun `AC-01 the same token and pairing are not sent again on the next Live stream`(): Unit = runBlocking {
        val session = session(InMemoryPushRegistrationStore(), air)
        session.awaitRestored()
        session.applyPushToken("token-one")
        session.connect()
        session.awaitLive("air")
        next()

        session.activateComputer("air")
        session.awaitLive("air")
        assertQuiet()
    }

    @Test
    fun `AC-01 a token that arrives after the stream is Live registers at once`(): Unit = runBlocking {
        val session = session(InMemoryPushRegistrationStore(), air)
        session.awaitRestored()
        session.connect()
        session.awaitLive("air")
        assertQuiet()

        session.applyPushToken("late-token")
        assertEquals("late-token", bodyOf(next().request).getValue("token").jsonPrimitive.content)
    }

    @Test
    fun `AC-01 a computer that keeps no registration is asked again next time`(): Unit = runBlocking {
        airServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += Seen("air", request, true)
                return MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"push":"off","device":null,"relay":"off"}""")
            }
        }
        val store = InMemoryPushRegistrationStore()
        val session = session(store, air)
        session.awaitRestored()
        session.applyPushToken("token-one")
        session.connect()
        session.awaitLive("air")
        next()
        assertNull(store.accepted("air"))

        session.activateComputer("air")
        session.awaitLive("air")
        next()
    }

    @Test
    fun `AC-02 a new token marks every saved computer and registers the active one now`(): Unit = runBlocking {
        val store = InMemoryPushRegistrationStore()
        val session = session(store, air, pro)
        session.awaitRestored()
        session.applyPushToken("token-one")
        session.connect()
        session.awaitLive("air")
        assertEquals("token-one", bodyOf(next().request).getValue("token").jsonPrimitive.content)
        val before = store.accepted("air")

        session.applyPushToken("token-two")
        val renewed = next()
        assertEquals("air", renewed.computer)
        assertEquals("token-two", bodyOf(renewed.request).getValue("token").jsonPrimitive.content)
        assertNotNull(store.accepted("air"))
        assertTrue(store.accepted("air") != before, "the saved registration must follow the new token")

        session.activateComputer("pro")
        session.awaitLive("pro")
        val second = next()
        assertEquals("pro", second.computer)
        val body = bodyOf(second.request)
        assertEquals("token-two", body.getValue("token").jsonPrimitive.content)
        assertEquals("pro", body.getValue("pairing").jsonPrimitive.content)
    }

    @Test
    fun `AC-08 removing a computer sends DELETE before its credential goes`(): Unit = runBlocking {
        val session = session(InMemoryPushRegistrationStore(), air, pro)
        session.awaitRestored()
        session.applyPushToken("token-one")
        session.connect()
        session.awaitLive("air")
        next()

        session.forgetConnection("air")
        val delete = next()
        assertEquals("DELETE", delete.request.method)
        assertEquals("air", delete.computer)
        assertEquals("Bearer air-token", delete.request.getHeader("Authorization"))
        assertTrue(delete.credentialKept, "the credential must still be stored when DELETE arrives")
        withTimeout(10_000) { session.connections.first { list -> list.none { it.id == "air" } } }
        assertNull(tokens["air"])
    }

    @Test
    fun `AC-08 a saved computer that is not active gets DELETE with its own credential`(): Unit = runBlocking {
        val store = InMemoryPushRegistrationStore().apply {
            runBlocking { save("pro", PushSignature.of("token-one", "pro")) }
        }
        val session = session(store, air, pro)
        session.awaitRestored()

        session.forgetConnection("pro")
        val delete = next()
        assertEquals("pro", delete.computer)
        assertEquals("DELETE", delete.request.method)
        assertEquals("Bearer pro-token", delete.request.getHeader("Authorization"))
        withTimeout(10_000) { session.connections.first { list -> list.none { it.id == "pro" } } }
        assertNull(store.accepted("pro"))
    }

    @Test
    fun `AC-08 removal goes on when the computer cannot be reached`(): Unit = runBlocking {
        val store = InMemoryPushRegistrationStore().apply {
            runBlocking { save("pro", PushSignature.of("token-one", "pro")) }
        }
        val session = session(store, air, pro)
        session.awaitRestored()
        proServer.shutdown()

        session.forgetConnection("pro")
        withTimeout(15_000) { session.connections.first { list -> list.none { it.id == "pro" } } }
        assertNull(tokens["pro"])
    }

    @Test
    fun `AC-08 removal goes on when the computer never answers`(): Unit = runBlocking {
        val store = InMemoryPushRegistrationStore().apply {
            runBlocking { save("pro", PushSignature.of("token-one", "pro")) }
        }
        val session = session(store, air, pro)
        session.awaitRestored()
        hang = true

        session.forgetConnection("pro")
        assertEquals("DELETE", next().request.method)
        withTimeout(15_000) { session.connections.first { list -> list.none { it.id == "pro" } } }
        assertNull(tokens["pro"])
    }

    @Test
    fun `a phone that never held a push token sends nothing when a computer is removed`(): Unit = runBlocking {
        val session = session(InMemoryPushRegistrationStore(), air, pro)
        session.awaitRestored()

        session.forgetConnection("pro")
        withTimeout(10_000) { session.connections.first { list -> list.none { it.id == "pro" } } }
        assertQuiet()
    }

    @Test
    fun `holdsLiveStream names only the active computer while it is Live`(): Unit = runBlocking {
        val session = session(InMemoryPushRegistrationStore(), air, pro)
        session.awaitRestored()
        assertTrue(!session.holdsLiveStream("air"))
        session.connect()
        session.awaitLive("air")
        assertTrue(session.holdsLiveStream("air"))
        assertTrue(!session.holdsLiveStream("pro"))
    }

    @Test
    fun `the answer decodes without a token`() {
        val answer = CompanionJson.decodeFromString<PushAnswer>(
            """{"push":"no-account","device":{"platform":"fcm","environment":"production","pairing":"air","kinds":["approval"],"updatedAt":5},"relay":"pending"}""",
        )
        assertEquals("no-account", answer.push)
        assertEquals("air", answer.device?.pairing)
        assertEquals("pending", answer.relay)
    }
}
