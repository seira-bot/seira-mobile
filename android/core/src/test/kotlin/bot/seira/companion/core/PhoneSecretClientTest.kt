package bot.seira.companion.core

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** The provide call: one POST of the envelope, on a protected route, with a deadline longer than the computer's. */
class PhoneSecretClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private val timeouts = mutableListOf<List<Long>>()

    private val envelope = PhoneSecretEnvelope(
        version = 1,
        threadId = "thread-1",
        keyId = "taWSR_nZ7ojlH_0Z3tar6Q",
        deviceId = "paired-device-1",
        target = "ttsKey",
        requestKey = "credential-request-1",
        encapsulatedKey = "A".repeat(87),
        ciphertext = "B".repeat(32),
    )

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
            })
            .addInterceptor { chain ->
                synchronized(timeouts) {
                    timeouts += listOf(
                        TimeUnit.NANOSECONDS.toSeconds(chain.call().timeout().timeoutNanos()),
                        TimeUnit.MILLISECONDS.toSeconds(chain.readTimeoutMillis().toLong()),
                    )
                }
                chain.proceed(chain.request())
            }
            .build()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
    }

    private fun tailnetClient(): CompanionClient {
        val endpoint = assertNotNull(
            CompanionEndpoint.create("http://mac.fixture.ts.net:${server.port}", CompanionEndpointKind.TAILNET, 0),
        )
        val connection = Connection(
            id = "fixture",
            name = "Mac",
            host = endpoint.host,
            port = endpoint.port,
            activeEndpoint = endpoint,
            endpoints = listOf(endpoint),
        ).establishingRoutePolicyFromInvite()
        return CompanionClient(connection, "paired-token", http)
    }

    @Test
    fun postsOnlyTheEnvelopeToTheExactCard() = runBlocking {
        server.enqueue(json("""{"provided":true,"resumed":true}"""))

        val answer = tailnetClient().provideCredential("bot-1", "message-1", envelope)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bots/bot-1/secret-cards/message-1/provide", request.path)
        assertEquals("Bearer paired-token", request.getHeader("Authorization"))
        val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(
            setOf("version", "threadId", "keyId", "deviceId", "target", "requestKey", "encapsulatedKey", "ciphertext"),
            body.keys,
        )
        assertFalse("value" in body)
        assertEquals("1", body.getValue("version").jsonPrimitive.content)
        assertEquals("thread-1", body.getValue("threadId").jsonPrimitive.content)
        assertEquals("B".repeat(32), body.getValue("ciphertext").jsonPrimitive.content)
        assertEquals(CredentialProvided(provided = true, resumed = true), answer)
    }

    @Test
    fun waitsLongerThanTheComputerAndItsSidecar() = runBlocking {
        server.enqueue(json("""{"provided":true,"resumed":false}"""))

        tailnetClient().provideCredential("bot-1", "message-1", envelope)

        assertEquals(listOf(listOf(115L, 115L)), timeouts, "call and read deadlines")
    }

    @Test
    fun refusesALocalNetworkRouteWithoutCallingTheComputer() = runBlocking {
        val lan = CompanionClient(Connection(id = "lan", name = "Mac", host = "127.0.0.1", port = server.port), "paired-token", http)

        val error = assertFailsWith<PhoneSecretException> { lan.provideCredential("bot-1", "message-1", envelope) }

        assertEquals(PhoneSecretException.Reason.INSECURE_TRANSPORT, error.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun anUnreadableSuccessStillCountsAsProvided() = runBlocking {
        server.enqueue(json("ok"))

        assertEquals(CredentialProvided(), tailnetClient().provideCredential("bot-1", "message-1", envelope))
    }

    @Test
    fun theComputersRefusalReachesThePersonInItsOwnWords() = runBlocking {
        server.enqueue(json("""{"error":"A different value was already sent for this request."}""", code = 409))

        val error = assertFailsWith<APIError.Status> { tailnetClient().provideCredential("bot-1", "message-1", envelope) }

        assertEquals(409, error.code)
        assertEquals("A different value was already sent for this request.", error.message)
    }

    @Test
    fun routeIdsAreEscapedAsOnePathSegment() = runBlocking {
        server.enqueue(json("""{"provided":true}"""))

        tailnetClient().provideCredential("bot 1", "message/1", envelope)

        assertEquals("/api/bots/bot%201/secret-cards/message%2F1/provide", server.takeRequest().path)
    }

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
