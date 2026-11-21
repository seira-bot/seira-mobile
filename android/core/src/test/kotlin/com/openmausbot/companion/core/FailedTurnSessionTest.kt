package com.openmausbot.companion.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

@OptIn(ExperimentalCoroutinesApi::class)
class FailedTurnSessionTest {
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

    private val failure = Message(
        "f1", Message.Role.BOT, Message.Kind.ACTIVITY, 4.0,
        tool = ToolActivity(name = "error: Claude is overloaded right now.", ok = false),
    )
    private val messages = listOf(
        Message("u1", Message.Role.USER, Message.Kind.TEXT, 1.0, text = "first question"),
        Message("b1", Message.Role.BOT, Message.Kind.TEXT, 2.0, text = "first answer"),
        Message("u2", Message.Role.USER, Message.Kind.TEXT, 3.0, text = "second question"),
        failure,
    )

    @Test
    fun retryLastSendsTheLastUserMessageAgainThroughTheEditRoute() = runTest {
        val bot = bot()
        val session = session(bot)
        server.enqueue(json("{}"))

        assertTrue(session.retryLast(Chat.BotChat(bot)))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bots/b1/messages/u2/edit", request.path)
        val body = body(request)
        assertEquals("second question", body["text"])
        assertEquals("task-a", body["threadId"])
        assertNull(session.actionError)
    }

    @Test
    fun retryLastWithNoUserMessageSendsNothing() = runTest {
        val bot = bot(messages = listOf(failure))
        val session = session(bot)
        assertFalse(session.retryLast(Chat.BotChat(bot)))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun switchingTheModelPatchesThisConversationOnly() = runTest {
        val bot = bot()
        val session = session(bot)
        server.enqueue(json(botResponse("codex", "gpt-5")))

        val updated = session.updateModel(ModelSelection("codex", "gpt-5"), bot, quietly = true)

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/bots/b1/tasks/task-a", request.path)
        val sent = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(setOf("modelSelection", "requireAvailableModel"), sent.keys)
        assertEquals("gpt-5", sent.getValue("modelSelection").jsonObject.getValue("model").jsonPrimitive.content)
        assertEquals("gpt-5", updated?.modelSelection?.model)
    }

    @Test
    fun aQuietSwitchFailureRaisesNoBanner() = runTest {
        val bot = bot()
        val session = session(bot)
        server.enqueue(json("""{"error":"model is not available"}""", code = 409))
        assertNull(session.updateModel(ModelSelection("codex", "gpt-5"), bot, quietly = true))
        assertNull(session.actionError)
    }

    @Test
    fun modelListRefusalHidesSwitchModelAndRaisesNoBanner() = runTest {
        val session = session(bot())
        server.enqueue(json("""{"error":"forbidden"}""", code = 403))

        assertIs<ModelListResult.Forbidden>(session.switchableModels())

        assertTrue(session.modelListForbidden.value)
        assertNull(session.actionError)
        val offer = FailedTurnActions.offer(failure.tool!!, false, session.modelListForbidden.value, false)!!
        assertFalse(offer.switchModel)
        assertTrue(offer.tryAgain)
        assertTrue(offer.askOwner)
    }

    @Test
    fun aReadableModelListIsReturned() = runTest {
        val session = session(bot())
        server.enqueue(json("""{"instances":[]}"""))
        assertEquals(ModelListResult.Loaded(emptyList()), session.switchableModels())
        assertFalse(session.modelListForbidden.value)
    }

    @Test
    fun continueAtResetPostsTheBotAndThread() = runTest {
        val bot = bot()
        val session = session(bot)
        server.enqueue(json("{}"))

        assertTrue(session.continueAtReset(Chat.BotChat(bot)))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/limit-resets/continue", request.path)
        assertEquals(mapOf("botId" to "b1", "threadId" to "task-a"), body(request))
        assertFalse(session.limitResetForbidden.value)
    }

    @Test
    fun continueAtResetRefusalHidesItForTheSession() = runTest {
        val bot = bot()
        val session = session(bot)
        server.enqueue(json("""{"error":"forbidden"}""", code = 403))

        assertFalse(session.continueAtReset(Chat.BotChat(bot)))

        assertTrue(session.limitResetForbidden.value)
        assertNull(session.actionError)
        val limited = failure.tool!!.copy(limitResetsAt = 1790000000000.0)
        assertNull(FailedTurnActions.offer(limited, false, false, session.limitResetForbidden.value)!!.continueAt)
    }

    private suspend fun TestScope.session(bot: Bot): Session {
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
            hydrateFn = { _, _ -> Fleet(listOf(bot), emptyList()) },
        ).also { it.awaitRestored() }
    }

    private fun bot(messages: List<Message> = this.messages): Bot = Bot(
        id = "b1",
        threadId = "task-a",
        name = "b1",
        title = "",
        description = "",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("claude", "opus"),
        createdAt = 1.0,
        tasks = listOf(BotTask("task-a", "Task 1", 0.0)),
        messages = messages,
    )

    private fun botResponse(instance: String, model: String) = """
        {"bot":{"id":"b1","threadId":"task-a","name":"b1","title":"","description":"",
         "notifications":true,"color":"green","unread":false,"createdAt":1.0,
         "modelSelection":{"instanceId":"$instance","model":"$model"},
         "tasks":[{"threadId":"task-a","title":"Task 1","createdAt":0.0,"modelSelection":{"instanceId":"$instance","model":"$model"}}]}}
    """.trimIndent()

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun body(request: RecordedRequest): Map<String, String> =
        CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            .mapValues { it.value.jsonPrimitive.content }
}
