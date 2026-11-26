package com.openmausbot.companion.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionRedesignTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun deleteBotRemovesItFromTheFleetOnSuccess() = runTest {
        val kept = bot("b2")
        val session = session(Fleet(listOf(bot("b1"), kept), emptyList()))
        server.enqueue(json("""{"ok":true}"""))

        assertTrue(session.deleteBot(bot("b1")))

        assertEquals(listOf("b2"), session.state.value.bots.map { it.id })
        assertEquals("DELETE /api/bots/b1", server.takeRequest().let { "${it.method} ${it.path}" })
        assertNull(session.actionError)
    }

    @Test
    fun deleteBotKeepsTheBotAndReportsWhenTheComputerRefuses() = runTest {
        val session = session(Fleet(listOf(bot("b1")), emptyList()))
        server.enqueue(json("""{"error":"The default bot cannot be deleted"}""", 403))

        assertEquals(false, session.deleteBot(bot("b1")))

        assertEquals(listOf("b1"), session.state.value.bots.map { it.id })
        assertEquals("The default bot cannot be deleted", session.actionError)
    }

    @Test
    fun approvalModeChangeUpdatesTheBotAndExposesTheMode() = runTest {
        val session = session(Fleet(listOf(bot("b1")), emptyList()))
        server.enqueue(json("""{"bot":${CompanionJson.encodeToString(bot("b1", "edits"))}}"""))

        val result = session.setApprovalMode(bot("b1"), "t1", ApprovalMode.EDITS)

        assertEquals(ApprovalMode.EDITS, (result as ApprovalModeResult.Changed).mode)
        assertEquals(ApprovalMode.EDITS, session.approvalMode(bot("b1"), "t1"))
    }

    @Test
    fun aRefusedApprovalChangeIsTypedAndNotTheAppWideError() = runTest {
        val session = session(Fleet(listOf(bot("b1")), emptyList()))
        server.enqueue(
            json(
                """{"error":"On a shared company workspace Full access comes only from the operator's dedicated policy"}""",
                403,
            ),
        )

        val result = session.setApprovalMode(bot("b1"), "t1", ApprovalMode.FULL)

        val refusal = (result as ApprovalModeResult.Refused).refusal
        assertTrue(refusal is ApprovalModeRefusal.FullNotAllowedHere)
        assertNull(session.actionError)
        assertNull(session.approvalMode(bot("b1"), "t1"))
    }

    @Test
    fun localAutoRefusalAsksForTheAcknowledgement() = runTest {
        val session = session(Fleet(listOf(bot("b1")), emptyList()))
        server.enqueue(
            json("""{"error":"Auto mode on this computer requires confirming the warning first (acknowledgeLocalAuto)"}""", 400),
        )
        val result = session.setApprovalMode(bot("b1"), "t1", ApprovalMode.AUTO)
        assertTrue((result as ApprovalModeResult.Refused).refusal is ApprovalModeRefusal.LocalAutoNeedsAcknowledgement)
    }

    @Test
    fun readsReturnNullAndReportWhenTheComputerFails() = runTest {
        val session = session(Fleet(listOf(bot("b1")), emptyList()))
        server.enqueue(json("""{"error":"no such bot"}""", 404))
        assertNull(session.botActivity(bot("b1")))
        assertEquals("no such bot", session.actionError)
    }

    private suspend fun TestScope.session(fleet: Fleet): Session {
        val connection = requireNotNull(Connection.parse(server.url("/").toString()))
        return Session(
            scope = backgroundScope,
            connectionStore = object : ConnectionStore {
                override suspend fun load(): Connection = connection
                override suspend fun save(connection: Connection) = Unit
                override suspend fun clear() = Unit
                override suspend fun loadRegistry() = ConnectionRegistryRestore(
                    ConnectionRegistry(listOf(connection), connection.id),
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
            hydrateFn = { _, _ -> fleet },
        ).also { it.awaitRestored() }
    }

    private fun bot(id: String, approvalMode: String? = null): Bot = Bot(
        id = id,
        threadId = "t1",
        name = id,
        title = "",
        description = "",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("instance", "model"),
        createdAt = 1.0,
        approvalMode = approvalMode,
        tasks = listOf(BotTask("t1", "Task 1", 1.0, approvalMode = approvalMode)),
    )

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
