package bot.seira.companion.core

import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * The session side of the credential card: it seals only for a pairing that has the
 * computer's key on a protected route, keeps one envelope per card so a retry resends the
 * same bytes, and forgets every envelope when the pairing or the computer changes.
 */
class SessionPhoneSecretTest {
    private val publicKey = "BIPBQ12_dWnF1DZLsTZO3Vg0NGjds5-jp9h3jhjr2To7bJelczS0LM82rfXV68PmSJhz2ePosj3fL974XckCpDU"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private val answers = ConcurrentLinkedQueue<MockResponse>()
    private val provides = ConcurrentLinkedQueue<RecordedRequest>()
    /** While set, the computer holds each provide request open until it counts down. */
    @Volatile private var holdProvides: CountDownLatch? = null

    private val bot = Bot(
        id = "bot-1",
        threadId = "thread-1",
        name = "Mochi",
        title = "role",
        description = "",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("i", "m"),
        createdAt = 0.0,
    )
    private val chat = Chat.BotChat(bot)

    private fun card(target: String = "ttsKey") = Message(
        "message-1",
        Message.Role.BOT,
        Message.Kind.SECRET,
        1.0,
        secret = SecretRequestCardData(target = target, requestKey = "credential-request-1", label = "Voice key"),
    )

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.endsWith("/provide") != true) return MockResponse().setResponseCode(404)
                provides.add(request)
                holdProvides?.await(10, TimeUnit.SECONDS)
                return answers.poll() ?: MockResponse().setResponseCode(500)
            }
        }
        server.start()
        http = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
            })
            .build()
    }

    @AfterTest
    fun tearDown() {
        holdProvides?.countDown()
        scope.cancel()
        server.shutdown()
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
    }

    @Test
    fun pairingKeepsTheKeyAndStoresThisPhonesDeviceId() = runBlocking {
        val session = session(
            pairFn = { invited, _, _, _ ->
                PairingOutcome(PairResponse("device-token", PairedDevice("device-9", "Fixture phone", 1.0, 1.0), "Mac"), invited)
            },
        )
        val invite = assertNotNull(
            PairingInvite.parse(
                "seira://pair?address=mac.fixture.ts.net%3A${server.port}&token=seira_pair_${"b".repeat(43)}&secretKey=$publicKey",
            ),
        )
        try {
            session.pair(invite)
            val paired = assertNotNull(session.connection.value)
            assertEquals(publicKey, paired.secretPublicKey)
            assertEquals("device-9", paired.companionDeviceId)
            assertTrue(paired.pairedForSecrets)
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun aRetryResendsTheSameBytesAndNeverSealsAgain() = runBlocking {
        answers.add(json("""{"error":"The computer could not save it yet."}""", code = 500))
        answers.add(json("""{"provided":true,"resumed":false}"""))
        val session = live(tailnet())
        try {
            val prepared = session.prepareCredential("sk-voice-secret", chat, card())
            val failed = assertFailsWith<APIError.Status> { session.provideCredential(prepared) }
            assertEquals("The computer could not save it yet.", failed.message)

            assertSame(prepared, session.preparedCredential(chat, card()), "the failed envelope is kept")
            assertSame(prepared, session.prepareCredential("another value", chat, card()), "a kept envelope is never sealed again")
            assertEquals(CredentialProvided(provided = true, resumed = false), session.provideCredential(prepared))

            assertEquals(2, provides.size)
            val (first, second) = provides.toList()
            assertEquals("/api/bots/bot-1/secret-cards/message-1/provide", first.path)
            assertEquals("Bearer fixture-token", first.getHeader("Authorization"))
            val firstBody = first.body.readByteArray()
            assertContentEquals(firstBody, second.body.readByteArray())
            assertFalse(firstBody.toString(Charsets.UTF_8).contains("sk-voice-secret"))
            assertFalse(firstBody.toString(Charsets.UTF_8).contains("another value"))
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun aSendRunsInTheSessionAndARetrySendsTheSameBytes() = runBlocking {
        answers.add(json("""{"error":"The computer could not save it yet."}""", code = 500))
        answers.add(json("""{"provided":true,"resumed":true}"""))
        val session = live(tailnet())
        try {
            val prepared = session.prepareCredential("sk-voice-secret", chat, card())
            session.sendCredential(prepared)
            val failed = withTimeout(5_000) { session.credentialSends.first { it[prepared] is CredentialSend.Failed } }
            assertEquals("The computer could not save it yet.", (failed[prepared] as CredentialSend.Failed).error.message)
            assertSame(prepared, session.preparedCredential(chat, card()), "a failed send keeps its envelope")

            session.sendCredential(prepared)
            val sent = withTimeout(5_000) { session.credentialSends.first { it[prepared] is CredentialSend.Sent } }
            assertEquals(CredentialSend.Sent(CredentialProvided(provided = true, resumed = true)), sent[prepared])

            val (first, second) = provides.toList()
            assertContentEquals(first.body.readByteArray(), second.body.readByteArray())
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun leavingTheAppStopsASendAndKeepsItsEnvelope() = runBlocking {
        answers.add(json("""{"provided":true,"resumed":false}"""))
        answers.add(json("""{"provided":true,"resumed":false}"""))
        val hold = CountDownLatch(1).also { holdProvides = it }
        val session = live(tailnet())
        try {
            val prepared = session.prepareCredential("sk-voice-secret", chat, card())
            session.sendCredential(prepared)
            withTimeout(5_000) { while (provides.isEmpty()) delay(10) }
            assertEquals(CredentialSend.Sending, session.credentialSends.value[prepared])

            session.disconnect()
            assertNull(session.credentialSends.value[prepared], "a stopped send leaves no state")
            assertSame(prepared, session.preparedCredential(chat, card()), "the envelope stays for the retry")

            holdProvides = null
            hold.countDown()
            session.connect()
            withTimeout(5_000) { session.status.first { it is Session.Status.Live } }
            session.sendCredential(prepared)
            withTimeout(5_000) { session.credentialSends.first { it[prepared] is CredentialSend.Sent } }

            val (first, second) = provides.toList()
            assertContentEquals(first.body.readByteArray(), second.body.readByteArray())
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun aLocalNetworkRouteSealsNothing() = runBlocking {
        val session = live(lan())
        try {
            val error = assertFailsWith<PhoneSecretException> { session.prepareCredential("sk-voice-secret", chat, card()) }
            assertEquals(PhoneSecretException.Reason.INSECURE_TRANSPORT, error.reason)
            assertEquals(0, session.preparedCredentialCount)
            assertNull(session.preparedCredential(chat, card()))
            assertTrue(provides.isEmpty())
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun aPairingWithoutTheKeyOrDeviceIdAsksToPairAgain() = runBlocking {
        listOf(tailnet(key = null), tailnet(device = null)).forEach { connection ->
            val session = live(connection)
            try {
                val error = assertFailsWith<PhoneSecretException> { session.prepareCredential("sk-voice-secret", chat, card()) }
                assertEquals(PhoneSecretException.Reason.UNAVAILABLE, error.reason)
                assertEquals(0, session.preparedCredentialCount)
            } finally {
                session.disconnect()
            }
        }
    }

    @Test
    fun aComputerOnlyTargetIsNotSealed() = runBlocking {
        val session = live(tailnet())
        try {
            val error = assertFailsWith<PhoneSecretException> {
                session.prepareCredential("sk-openai", chat, card(target = "openaiApiKey"))
            }
            assertEquals(PhoneSecretException.Reason.COMPUTER_ONLY, error.reason)
            assertEquals(0, session.preparedCredentialCount)
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun aRefusedDeviceMarksThePairingRevoked() = runBlocking<Unit> {
        answers.add(json("""{"error":"revoked"}""", code = 401))
        val session = live(tailnet())
        try {
            val prepared = session.prepareCredential("sk-voice-secret", chat, card())
            assertFailsWith<APIError.Status> { session.provideCredential(prepared) }
            withTimeout(5_000) { session.status.first { it is Session.Status.Unauthorized } }
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun switchingComputersForgetsEveryEnvelope() = runBlocking {
        val session = live(tailnet(), other())
        try {
            val prepared = session.prepareCredential("sk-voice-secret", chat, card())
            session.sendCredential(prepared)
            withTimeout(5_000) { session.credentialSends.first { it[prepared] is CredentialSend.Failed } }
            val generation = session.credentialResetGeneration.value
            assertEquals(1, session.preparedCredentialCount)

            session.switchComputer("other")
            withTimeout(5_000) { session.connection.first { it?.id == "other" } }

            assertEquals(0, session.preparedCredentialCount)
            assertTrue(session.credentialSends.value.isEmpty(), "the old computer's sends are forgotten")
            assertTrue(session.credentialResetGeneration.value > generation)
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun forgettingAComputerForgetsItsEnvelopes() = runBlocking {
        val session = live(tailnet(), other())
        try {
            session.prepareCredential("sk-voice-secret", chat, card())

            session.forgetConnection("other")
            withTimeout(5_000) { session.connections.first { it.size == 1 } }
            assertEquals(1, session.preparedCredentialCount, "another computer's removal keeps this one's envelope")

            val generation = session.credentialResetGeneration.value
            session.forgetConnection("fixture")
            withTimeout(5_000) { session.status.first { it is Session.Status.Unpaired } }
            assertEquals(0, session.preparedCredentialCount)
            assertTrue(session.credentialResetGeneration.value > generation)
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun signingOutForgetsEveryEnvelope() = runBlocking {
        val session = live(tailnet())
        try {
            session.prepareCredential("sk-voice-secret", chat, card())
            val generation = session.credentialResetGeneration.value

            session.signOutAndAwait()

            assertEquals(0, session.preparedCredentialCount)
            assertTrue(session.credentialResetGeneration.value > generation)
        } finally {
            session.disconnect()
        }
    }

    @Test
    fun leavingTheAppClearsTheFieldButKeepsTheEnvelope() = runBlocking {
        val session = live(tailnet())
        try {
            val prepared = session.prepareCredential("sk-voice-secret", chat, card())
            val generation = session.credentialResetGeneration.value

            session.disconnect()

            assertTrue(session.credentialResetGeneration.value > generation, "the card clears its field")
            assertSame(prepared, session.preparedCredential(chat, card()), "a foreground retry sends the same operation")
        } finally {
            session.disconnect()
        }
    }

    private fun tailnet(key: String? = publicKey, device: String? = "paired-device-1"): Connection {
        val endpoint = assertNotNull(
            CompanionEndpoint.create("http://mac.fixture.ts.net:${server.port}", CompanionEndpointKind.TAILNET, 0),
        )
        return Connection(
            id = "fixture",
            name = "Mac",
            host = endpoint.host,
            port = endpoint.port,
            activeEndpoint = endpoint,
            endpoints = listOf(endpoint),
            secretPublicKey = key,
            companionDeviceId = device,
        ).establishingRoutePolicyFromInvite()
    }

    private fun lan() = Connection(
        id = "fixture",
        name = "Mac",
        host = "127.0.0.1",
        port = server.port,
        secretPublicKey = publicKey,
        companionDeviceId = "paired-device-1",
    )

    private fun other() = Connection(id = "other", name = "Other", host = "127.0.0.1", port = 1)

    private suspend fun live(vararg saved: Connection): Session {
        val session = session(*saved)
        session.connect()
        withTimeout(5_000) { session.status.first { it is Session.Status.Live } }
        return session
    }

    private suspend fun session(
        vararg saved: Connection,
        pairFn: suspend (Connection, String, String, String) -> PairingOutcome = { _, _, _, _ -> error("no pairing here") },
    ): Session {
        val session = Session(
            scope = scope,
            connectionStore = Store(ConnectionRegistry(saved.toList(), saved.firstOrNull()?.id)),
            tokenStore = Tokens(),
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Fixture phone" },
            httpClient = http,
            pairFn = pairFn,
            eventsFn = { _, _, _ ->
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false)))
                    awaitCancellation()
                }
            },
            hydrateFn = { _, _ -> Fleet(emptyList(), emptyList()) },
            instancesFn = { emptyList() },
            metadataFn = { throw APIError.Status(404) },
            liveCallFn = { null },
        )
        session.awaitRestored()
        return session
    }

    private class Store(var registry: ConnectionRegistry) : ConnectionStore {
        override suspend fun load() = registry.activeConnection
        override suspend fun save(connection: Connection) {
            registry = registry.upsert(connection)
        }
        override suspend fun clear() {
            registry = ConnectionRegistry()
        }
        override suspend fun loadRegistry() = ConnectionRegistryRestore(registry, migratedLegacyConnection = false)
        override suspend fun saveRegistry(registry: ConnectionRegistry) {
            this.registry = registry
        }
    }

    private class Tokens : TokenStore {
        override suspend fun read(connectionId: String) = TokenStore.ReadResult.Found("fixture-token")
        override suspend fun save(connectionId: String, token: String) = Unit
        override suspend fun remove(connectionId: String) = Unit
    }

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
