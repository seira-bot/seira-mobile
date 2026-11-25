package com.openmausbot.companion.core

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * A workspace switch through the real [Session]: the pin, the stream and the bearer. The computer is a fake at the HTTP
 * layer. It changes the identity it shows when a switch finishes, as the real one does.
 */
class WorkspaceSwitchSessionTest {
    @Test
    fun aSwitchThePhoneStartedAndProvedMovesThePinInOneWriteAndRestartsTheStream() = runBlocking<Unit> {
        Fixture(proof = { _, sw -> proofFor(sw) }).use { f ->
            f.start()
            val result = f.session.switchWorkspace("trading")
            assertEquals(WorkspaceSwitchResult.Switched(NEW), result)
            assertEquals(NEW, f.store.saved?.serverEnvironmentId)
            assertEquals(NEW, f.session.connection.value?.serverEnvironmentId)
            f.awaitStreamStarts(2)
            f.assertBearerOnlyWhereIdentityIs(OLD, NEW)
        }
    }

    @Test
    fun withoutAProofCapabilityNoSwitchRequestLeavesThePhone() = runBlocking<Unit> {
        Fixture().use { f ->
            f.start()
            val before = f.requests.size
            assertEquals(WorkspaceSwitchResult.Inactive, f.session.switchWorkspace("trading"))
            assertEquals(WorkspaceSwitchResult.Inactive, f.session.finishWorkspaceSwitch())
            assertTrue(f.requests.drop(before).isEmpty(), "no request leaves the phone without a proof capability")
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
            assertEquals(OLD, f.session.connection.value?.serverEnvironmentId)
            assertEquals(1, f.streamStarts)
        }
    }

    @Test
    fun theSeamsHaveNoProofCapabilityByDefault() {
        assertEquals(null, WorkspaceSwitchSeams().proof)
    }

    @Test
    fun aProofThatReturnsNothingKeepsTheOldPinAndTheNewIdentityGetsNoBearer() = runBlocking<Unit> {
        Fixture(proof = { _, _ -> null }).use { f ->
            f.start()
            val result = f.session.switchWorkspace("trading")
            assertEquals(WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.VERIFICATION_FAILED), result)
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
            assertEquals(OLD, f.session.connection.value?.serverEnvironmentId)
            f.assertBearerOnlyWhereIdentityIs(OLD)
            assertEquals(1, f.streamStarts)
        }
    }

    @Test
    fun aProofThatTheComputerDidNotSignOverThePairingIsRejected() = runBlocking<Unit> {
        Fixture(proof = { _, sw -> proofFor(sw, authenticated = false) }).use { f ->
            f.start()
            assertEquals(
                WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.NOT_AUTHENTICATED),
                f.session.switchWorkspace("trading"),
            )
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
            f.assertBearerOnlyWhereIdentityIs(OLD)
        }
    }

    @Test
    fun aServerThatClaimsAnotherIdentityThanTheTargetIsRejected() = runBlocking<Unit> {
        Fixture(proof = { _, sw -> proofFor(sw).copy(environmentId = "env-other") }).use { f ->
            f.start()
            assertEquals(
                WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.WRONG_ENVIRONMENT),
                f.session.switchWorkspace("trading"),
            )
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
        }
    }

    @Test
    fun aProofForAnotherOperationOrWorkspaceIsRejected() = runBlocking<Unit> {
        Fixture(proof = { _, sw -> proofFor(sw).copy(operationId = "op-of-another-device") }).use { f ->
            f.start()
            assertEquals(
                WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.WRONG_OPERATION),
                f.session.switchWorkspace("trading"),
            )
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
        }
        Fixture(proof = { _, sw -> proofFor(sw).copy(workspaceId = "default") }).use { f ->
            f.start()
            assertEquals(
                WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.WRONG_WORKSPACE),
                f.session.switchWorkspace("trading"),
            )
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
        }
    }

    @Test
    fun aListWithoutTheTargetIdentityStartsNothingAndAsksForPairing() = runBlocking<Unit> {
        Fixture(
            listed = """{"workspaces":[{"id":"default","current":true,"state":"ready"},{"id":"trading","current":false,"state":"ready"}]}""",
            proof = { _, sw -> proofFor(sw) },
        )
            .use { f ->
                f.start()
                assertEquals(WorkspaceSwitchResult.NeedsPairing, f.session.switchWorkspace("trading"))
                assertTrue(f.requests.none { it.method == "POST" }, "no switch request goes out")
                assertEquals(OLD, f.store.saved?.serverEnvironmentId)
            }
    }

    @Test
    fun anAnswerThatNamesAnotherTargetThanTheListEndsTheSwitchAndKeepsThePin() = runBlocking<Unit> {
        Fixture(
            answer = """{"workspaces":[],"switch":{"id":"$OPERATION","workspaceId":"trading","environmentId":"env-other","state":"done"}}""",
            proof = { _, sw -> proofFor(sw) },
        ).use { f ->
            f.start()
            assertEquals(
                WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.WRONG_OPERATION),
                f.session.switchWorkspace("trading"),
            )
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
        }
    }

    @Test
    fun anAnswerWithoutASwitchKeepsThePin() = runBlocking<Unit> {
        Fixture(answer = """{"workspaces":[]}""", proof = { _, sw -> proofFor(sw) }).use { f ->
            f.start()
            assertIs<WorkspaceSwitchResult.Rejected>(f.session.switchWorkspace("trading"))
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
        }
    }

    @Test
    fun aSwitchThatWaitsMovesThePinOnlyWhenItIsDoneAndOnlyOnce() = runBlocking<Unit> {
        Fixture(
            answer = """{"workspaces":[],"switch":{"id":"$OPERATION","workspaceId":"trading","environmentId":"$NEW","state":"pending"}}""",
            proof = { _, sw -> proofFor(sw) },
            flipOnSwitch = false,
        ).use { f ->
            f.start()
            assertIs<WorkspaceSwitchResult.Waiting>(f.session.switchWorkspace("trading"))
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
            assertIs<WorkspaceSwitchResult.Waiting>(f.session.finishWorkspaceSwitch())
            f.status = "done"
            assertEquals(WorkspaceSwitchResult.Switched(NEW), f.session.finishWorkspaceSwitch())
            assertEquals(NEW, f.store.saved?.serverEnvironmentId)
            // The switch was used once: the same completion cannot move the pin again.
            assertEquals(
                WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH),
                f.session.finishWorkspaceSwitch(),
            )
        }
    }

    @Test
    fun aSwitchThatEndedOrExpiredKeepsThePin() = runBlocking<Unit> {
        for (state in listOf("failed", "cancelled", "superseded")) {
            Fixture(
                answer = """{"workspaces":[],"switch":{"id":"$OPERATION","workspaceId":"trading","environmentId":"$NEW","state":"$state"}}""",
                proof = { _, sw -> proofFor(sw) },
            ).use { f ->
                f.start()
                assertEquals(WorkspaceSwitchResult.Ended(state), f.session.switchWorkspace("trading"), state)
                assertEquals(OLD, f.store.saved?.serverEnvironmentId, state)
            }
        }
        Fixture(
            answer = """{"workspaces":[],"switch":{"id":"$OPERATION","workspaceId":"trading","environmentId":"$NEW","state":"pending"}}""",
            proof = { _, sw -> proofFor(sw) },
            flipOnSwitch = false,
        ).use { f ->
            f.start()
            assertIs<WorkspaceSwitchResult.Waiting>(f.session.switchWorkspace("trading"))
            f.statusFound = false
            assertEquals(WorkspaceSwitchResult.Ended("expired"), f.session.finishWorkspaceSwitch())
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
        }
    }

    @Test
    fun aSwitchStartedOnAnotherDeviceMovesNothing() = runBlocking<Unit> {
        Fixture(proof = { _, sw -> proofFor(sw) }).use { f ->
            f.start()
            assertEquals(
                WorkspaceSwitchResult.Rejected(WorkspaceSwitchOutcome.Reason.NO_PENDING_SWITCH),
                f.session.finishWorkspaceSwitch(),
            )
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
        }
    }

    @Test
    fun aDifferentServerAtTheSameAddressStillStopsTheStreamAndGetsNoBearer() = runBlocking<Unit> {
        Fixture(shown = NEW, proof = { _, sw -> proofFor(sw) }).use { f ->
            f.session.connect()
            withTimeout(5_000) { f.session.status.first { it == Session.Status.Unauthorized } }
            assertEquals(0, f.streamStarts)
            assertFailsWith<APIError.Status> { f.session.switchWorkspace("trading") }
            assertEquals(OLD, f.store.saved?.serverEnvironmentId)
            assertTrue(f.requests.all { it.header("Authorization") == null }, "a foreign server never sees the bearer")
        }
    }

    @Test
    fun aPairingWithNoPinDoesNotStartAndSendsNoBearer() = runBlocking<Unit> {
        Fixture(pinned = false, proof = { _, sw -> proofFor(sw) }).use { f ->
            f.start()
            val before = f.requests.size
            assertIs<WorkspaceSwitchResult.NeedsPairing>(f.session.switchWorkspace("trading"))
            assertEquals(null, f.store.saved?.serverEnvironmentId)
            assertTrue(f.requests.drop(before).isEmpty(), "no request leaves the phone without a pin")
        }
    }

    private fun proofFor(sw: PhoneWorkspaceSwitch, authenticated: Boolean = true) =
        WorkspaceSwitchCompletion(sw.id, sw.workspaceId, sw.environmentId.orEmpty(), authenticated)

    private class Fixture(
        val proof: (suspend (CompanionClient, PhoneWorkspaceSwitch) -> WorkspaceSwitchCompletion?)? = null,
        val listed: String = """{"workspaces":[{"id":"default","environmentId":"$OLD","current":true,"state":"ready"},{"id":"trading","environmentId":"$NEW","current":false,"state":"ready"}]}""",
        val answer: String = """{"workspaces":[],"switch":{"id":"$OPERATION","workspaceId":"trading","environmentId":"$NEW","state":"done"}}""",
        val flipOnSwitch: Boolean = true,
        shown: String = OLD,
        pinned: Boolean = true,
    ) : AutoCloseable {
        val requests = CopyOnWriteArrayList<okhttp3.Request>()

        /** The identity the address shows when each request with a bearer arrived. */
        val bearerAt = CopyOnWriteArrayList<String>()

        @Volatile var shownEnvironment = shown

        @Volatile var status = "pending"

        @Volatile var statusFound = true

        @Volatile var streamStarts = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tokens = Tokens()
        private val saved = assertNotNull(Connection.parse("https://mini.example")).copy(
            serverEnvironmentId = if (pinned) OLD else null,
        )
        val store = Store(saved)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            if (request.header("Authorization") != null) bearerAt += shownEnvironment
            val path = request.url.encodedPath
            val body = when {
                path.contains("well-known") -> """{"environmentId":"$shownEnvironment","label":"mini"}"""
                request.method == "POST" && path == "/api/phones/self/workspaces/switch" -> {
                    if (flipOnSwitch) shownEnvironment = NEW
                    answer
                }
                path == "/api/phones/self/workspaces/switch/$OPERATION" ->
                    if (statusFound) {
                        """{"switch":{"id":"$OPERATION","workspaceId":"trading","environmentId":"$NEW","state":"$status"}}"""
                    } else {
                        null
                    }
                path == "/api/phones/self/workspaces" -> listed
                else -> "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if (body == null) 404 else 200).message("fixture")
                .body((body ?: """{"error":"switch_not_found"}""").toResponseBody("application/json".toMediaType())).build()
        }.build()
        val session = Session(
            scope, store, tokens.also { it.values[saved.id] = "seira_sess_fixture" }, InMemoryOnboardingStore(), { "Pixel" },
            httpClient = http,
            eventsFn = { _, _, _ -> flow { streamStarts++; awaitCancellation() } },
            workspaceSwitch = WorkspaceSwitchSeams(now = { 1_000L }, proof = proof),
        )

        suspend fun start() {
            session.connect()
            awaitStreamStarts(1)
        }

        suspend fun awaitStreamStarts(count: Int) {
            withTimeout(5_000) { while (streamStarts < count) delay(10) }
        }

        fun assertBearerOnlyWhereIdentityIs(vararg allowed: String) {
            assertTrue(bearerAt.all { it in allowed }, "a bearer went to an identity that was not pinned then: $bearerAt")
        }

        override fun close() {
            session.disconnect()
            scope.cancel()
        }
    }

    private class Store(var saved: Connection?) : ConnectionStore {
        override suspend fun loadRegistry() = ConnectionRegistryRestore(
            ConnectionRegistry(listOfNotNull(saved), saved?.id), migratedLegacyConnection = false,
        )
        override suspend fun load() = saved
        override suspend fun save(connection: Connection) { saved = connection }
        override suspend fun clear() { saved = null }
    }

    private class Tokens : TokenStore {
        val values = mutableMapOf<String, String>()
        override suspend fun save(connectionId: String, token: String) { values[connectionId] = token }
        override suspend fun read(connectionId: String): TokenStore.ReadResult =
            values[connectionId]?.let { TokenStore.ReadResult.Found(it) } ?: TokenStore.ReadResult.Missing
        override suspend fun remove(connectionId: String) { values.remove(connectionId) }
    }

    private companion object {
        const val OLD = "env-old"
        const val NEW = "env-new"
        const val OPERATION = "0123456789abcdef0123456789abcdef"
    }
}
