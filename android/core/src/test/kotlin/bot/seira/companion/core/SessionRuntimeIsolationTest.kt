package bot.seira.companion.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

        val shown = session.runtimeBinding()!!
        val result = async { session.setApprovalMode(scout, "thread-1", ApprovalMode.EDITS, expected = shown) }
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

        val shown = session.runtimeBinding()!!
        val result = async { session.setApprovalMode(scout, "thread-1", ApprovalMode.FULL, expected = shown) }
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
    fun aModelChangeAnsweredByTheOldComputerIsDropped() = runTest {
        val changed = scout.copy(modelSelection = ModelSelection("i", "other"))
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(changed)}}""")
        val session = session(gate)

        val result = async { session.updateModel(ModelSelection("i", "other"), scout) }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        assertNull(result.await(), "the old computer's bot is not this computer's answer")
        assertTrue(session.state.value.bots.isEmpty(), "approval information of the old computer must not be applied")
        assertNull(session.actionError)
    }

    @Test
    fun aThreadReadThatFailsOnTheOldComputerShowsNoErrorOnTheNewOne() = runTest {
        val gate = Gate(status = 500, body = """{"error":"old computer failed"}""")
        val session = session(gate)

        val read = async { session.loadThread("thread-1") }
        gate.awaitParked()
        switchToPro(session)
        gate.release()
        read.await()

        assertNull(session.actionError, "an error of the old computer must not show on the new one")
    }

    @Test
    fun aRefusedSignInOfTheOldComputerDoesNotRevokeTheNewOne() = runTest {
        val gate = Gate(status = 401, body = """{"error":"old session revoked"}""")
        val session = session(gate)

        val call = async { session.interrupt(scout) }
        gate.awaitParked()
        switchToPro(session)
        gate.release()
        call.await()

        assertTrue(session.status.value !is Session.Status.Unauthorized, "the new computer is not unauthorized")
        assertNull(session.actionError)
    }

    @Test
    fun aRequestStartedBeforeASwitchBackToTheSameComputerIsStillRetired() = runTest {
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(scout)}}""")
        val session = session(gate)

        val shown = session.runtimeBinding()!!
        val result = async { session.setApprovalMode(scout, "thread-1", ApprovalMode.EDITS, expected = shown) }
        gate.awaitParked()
        switchToPro(session)
        session.switchComputer(air.id)
        runCurrent()
        assertEquals(air, session.connection.value, "the same pairing id is active again")
        gate.release()

        assertTrue(result.await() is ApprovalModeResult.Refused, "a replaced runtime with the same id is another runtime")
        assertTrue(session.state.value.bots.isEmpty())
    }

    @Test
    fun aWorkspaceChangeRetiresARequestOfTheSamePairing() = runTest {
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(scout)}}""")
        val session = session(gate)

        val shown = session.runtimeBinding()!!
        val result = async { session.setApprovalMode(scout, "thread-1", ApprovalMode.EDITS, expected = shown) }
        gate.awaitParked()
        session.reloadAfterWorkspaceChange(air.id)
        runCurrent()
        gate.release()

        assertTrue(result.await() is ApprovalModeResult.Refused, "the answer belongs to the workspace that was left")
        assertTrue(session.state.value.bots.isEmpty())
    }

    @Test
    fun anApprovalSheetOpenedForAnotherComputerSendsNothing() = runTest {
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(scout)}}""")
        val session = session(gate)
        val shownFor = session.runtimeBinding()
        assertTrue(shownFor != null && session.isCurrent(shownFor), "the binding is current while nothing changes")
        switchToPro(session)
        assertTrue(!session.isCurrent(shownFor!!), "the binding of the left computer is retired")

        val result = session.setApprovalMode(scout, "thread-1", ApprovalMode.EDITS, expected = shownFor!!)

        assertTrue(result is ApprovalModeResult.Refused)
        assertEquals(0, gate.requests.get(), "a retired sheet must not reach the network")
        assertTrue(session.state.value.bots.isEmpty())
    }

    private val unreadScout = scout.copy(
        unread = true,
        tasks = listOf(BotTask("thread-1", "Task 1", 1.0), BotTask("thread-2", "Task 2", 2.0, unread = true)),
    )

    private suspend fun TestScope.sessionShowingUnreadBot(gate: Gate): Session {
        val session = session(gate, Fleet(listOf(unreadScout), emptyList()))
        // Opening the task loads the bot into the state without a request to the gate.
        session.openNotification(requireNotNull(NotificationTarget.from("bot-1", "thread-1")))
        assertEquals(listOf("thread-1", "thread-2"), session.state.value.bot("bot-1")?.unreadThreadIds())
        return session
    }

    @Test
    fun aMarkAllReadThatOutlivesAComputerSwitchSendsNoFurtherRequest() = runTest {
        val gate = Gate(status = 200, body = "{}")
        val session = sessionShowingUnreadBot(gate)

        val result = async { session.markBotAllRead("bot-1") }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        assertEquals(false, result.await(), "a batch of the old computer reports nothing for the new one")
        assertEquals(1, gate.requests.get(), "the second read must not be sent once the runtime retired")
    }

    @Test
    fun aMarkAllReadStillSendsEveryReadWhenTheComputerDoesNotChange() = runTest {
        val gate = Gate(status = 200, body = "{}")
        val session = sessionShowingUnreadBot(gate)

        val result = async { session.markBotAllRead("bot-1") }
        gate.awaitParked()
        gate.release()

        assertEquals(true, result.await(), "control")
        assertEquals(2, gate.requests.get())
    }

    @Test
    fun aTranscriptExportAnsweredByTheOldComputerIsDropped() = runTest {
        val gate = Gate(status = 200, body = "old computer transcript")
        val session = session(gate)

        val shown = session.runtimeBinding()!!
        val result = async { session.export("thread-1", "md") }
        gate.awaitParked()
        switchToPro(session)
        gate.release()

        assertNull(result.await(), "the old computer's transcript must not reach the new computer's screen")
        assertEquals(false, session.isCurrent(shown), "the screen that asked must see its runtime retired")
        assertNull(session.actionError)
    }

    @Test
    fun aTranscriptExportStillArrivesWhenTheComputerDoesNotChange() = runTest {
        val gate = Gate(status = 200, body = "transcript")
        val session = session(gate)

        val result = async { session.export("thread-1", "md") }
        gate.awaitParked()
        gate.release()

        assertEquals("transcript", result.await()?.data?.toString(Charsets.UTF_8), "control")
    }

    @Test
    fun theRuntimeEpochMovesOnWhenTheComputerChanges() = runTest {
        val session = session(Gate(status = 200, body = "{}"))
        val before = session.runtimeEpoch.value

        switchToPro(session)

        assertTrue(session.runtimeEpoch.value > before)
    }

    @Test
    fun theSameCallsStillWorkWhenTheComputerDoesNotChange() = runTest {
        val edited = scout.copy()
        val gate = Gate(status = 200, body = """{"bot":${CompanionJson.encodeToString(edited)}}""")
        val session = session(gate)

        val shown = session.runtimeBinding()!!
        val result = async { session.setApprovalMode(scout, "thread-1", ApprovalMode.EDITS, expected = shown) }
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

    private suspend fun TestScope.session(gate: Gate, fleet: Fleet = Fleet(emptyList(), emptyList())): Session {
        val session = Session(
            scope = backgroundScope,
            connectionStore = RegistryStore(ConnectionRegistry(listOf(air, pro), air.id)),
            tokenStore = Tokens(mapOf(air.id to "air-token", pro.id to "pro-token")),
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Pixel" },
            clientFactory = { connection, token -> CompanionClient(connection, token, gate.http) },
            eventsFn = { _, _, _ -> flow { awaitCancellation() } },
            hydrateFn = { _, _ -> fleet },
        )
        session.awaitRestored()
        return session
    }

    /** Holds the first request until released, then answers with one fixed reply. */
    private class Gate(private val status: Int, private val body: String) {
        private val started = CountDownLatch(1)
        private val released = CountDownLatch(1)
        val requests = AtomicInteger()

        val http: OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
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
