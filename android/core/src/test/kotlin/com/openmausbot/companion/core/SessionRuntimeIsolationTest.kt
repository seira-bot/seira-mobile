package com.openmausbot.companion.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * A session call that is still in flight when the paired computer changes must
 * not touch the new computer: no state commit, no error banner, no success
 * result. Each test parks one request, switches the computer, releases the
 * request and checks the new computer's view. The control test shows the same
 * call still works when the computer does not change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRuntimeIsolationTest {
    private val air = Connection(id = "air", name = "MacBook Air", host = "air.local", port = 8810)
    private val pro = Connection(id = "pro", name = "MacBook Pro", host = "pro.local", port = 8810)

    private val scout = Bot(
        id = "bot-1",
        threadId = "thread-1",
        name = "Scout",
        title = "coder",
        description = "",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("i", "m"),
        createdAt = 1.0,
        tasks = listOf(BotTask("thread-1", "Task 1", 1.0), BotTask("thread-2", "Task 2", 2.0)),
    )

    @Test
    fun anApprovalChangeAnsweredByTheOldComputerIsRefusedAndNotCommitted() = runTest {
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(scout)}}""")
        val session = session(gate)

        val result = async { session.setApprovalMode(scout, "thread-1", ApprovalMode.EDITS) }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        assertTrue(result.await() is ApprovalModeResult.Refused, "the old computer's answer is not a change")
        assertTrue(session.state.value.bots.isEmpty(), "the old computer's bot must not appear on the new one")
        assertNull(session.actionError)
    }

    @Test
    fun anApprovalFailureFromTheOldComputerShowsNoReasonOnTheNewOne() = runTest {
        val gate = Gate(status = 403, body = """{"error":"old computer refused"}""")
        val session = session(gate)

        val result = async { session.setApprovalMode(scout, "thread-1", ApprovalMode.FULL) }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        val refusal = (result.await() as ApprovalModeResult.Refused).refusal
        assertTrue(refusal is ApprovalModeRefusal.Other, "a retired runtime gives no typed reason: $refusal")
        assertNull(session.actionError)
    }

    @Test
    fun aBotCreatedOnTheOldComputerIsNotAddedToTheNewOne() = runTest {
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(scout)}}""")
        val session = session(gate)

        val result = async { session.createBot() }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        assertNull(result.await())
        assertTrue(session.state.value.bots.isEmpty())
        assertNull(session.actionError)
    }

    @Test
    fun aFailedCreateOnTheOldComputerRaisesNoErrorOnTheNewOne() = runTest {
        val gate = Gate(status = 500, body = """{"error":"old computer failed"}""")
        val session = session(gate)

        val result = async { session.createBot() }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        assertNull(result.await())
        assertNull(session.actionError, "an error of the old computer must not show on the new one")
    }

    @Test
    fun aTaskChangeAnsweredByTheOldComputerGivesTheOfflineValueAndCommitsNothing() = runTest {
        val created = scout.copy(threadId = "thread-3")
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(created)}}""")
        val session = session(gate)

        val result = async { session.createTask(scout, null) }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        assertNull(result.await())
        assertTrue(session.state.value.bots.isEmpty())
        assertNull(session.actionError)
    }

    @Test
    fun aProfileChangeAnsweredByTheOldComputerIsDropped() = runTest {
        val renamed = scout.copy(name = "Renamed")
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(renamed)}}""")
        val session = session(gate)

        val result = async { session.updateProfile(BotProfilePatch(name = "Renamed"), scout) }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        assertNull(result.await())
        assertTrue(session.state.value.bots.isEmpty())
        assertNull(session.actionError)
    }

    @Test
    fun theSameCallsStillWorkWhenTheComputerDoesNotChange() = runTest {
        val edited = scout.copy()
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(edited)}}""")
        val session = session(gate)

        val result = async { session.setApprovalMode(scout, "thread-1", ApprovalMode.EDITS) }
        gate.awaitParked()
        gate.release()

        val changed = result.await()
        assertTrue(changed is ApprovalModeResult.Changed, "control: $changed")
        assertEquals(listOf("bot-1"), session.state.value.bots.map { it.id })
        assertNull(session.actionError)
    }

    private suspend fun TestScope.switchToPro(session: Session) {
        session.switchComputer(pro.id)
        runCurrent()
        assertEquals(pro, session.connection.value)
    }

    private suspend fun TestScope.session(gate: Gate): Session {
        val session = Session(
            scope = backgroundScope,
            connectionStore = RegistryStore(ConnectionRegistry(listOf(air, pro), air.id)),
            tokenStore = Tokens(mapOf(air.id to "air-token", pro.id to "pro-token")),
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Pixel" },
            clientFactory = { connection, token -> CompanionClient(connection, token, gate.http) },
            eventsFn = { _, _, _ -> flow { awaitCancellation() } },
            hydrateFn = { _, _ -> Fleet(emptyList(), emptyList()) },
        )
        session.awaitRestored()
        return session
    }

    /** Holds the first request until released, then answers with one fixed reply. */
    private class Gate(private val status: Int, private val body: String) {
        private val started = CountDownLatch(1)
        private val released = CountDownLatch(1)

        val http: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            started.countDown()
            check(released.await(5, TimeUnit.SECONDS)) { "request was never released" }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("Fixture")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        suspend fun awaitParked() {
            assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) }, "request never started")
        }

        fun release() = released.countDown()
    }

    private class RegistryStore(initial: ConnectionRegistry) : ConnectionStore {
        var registry = initial
        override suspend fun load(): Connection? = registry.activeConnection
        override suspend fun save(connection: Connection) {
            registry = registry.upsert(connection)
        }
        override suspend fun clear() {
            registry = ConnectionRegistry()
        }
        override suspend fun loadRegistry() = ConnectionRegistryRestore(registry, migratedLegacyConnection = false)
        override suspend fun saveRegistry(registry: ConnectionRegistry) {
            this.registry = registry.normalized()
        }
    }

    private class Tokens(initial: Map<String, String>) : TokenStore {
        val tokens = initial.toMutableMap()
        override suspend fun save(connectionId: String, token: String) {
            tokens[connectionId] = token
        }
        override suspend fun read(connectionId: String): TokenStore.ReadResult =
            tokens[connectionId]?.let(TokenStore.ReadResult::Found) ?: TokenStore.ReadResult.Missing
        override suspend fun remove(connectionId: String) {
            tokens.remove(connectionId)
        }
    }
}
