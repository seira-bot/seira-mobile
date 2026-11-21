package com.openmausbot.companion.notifications

import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.ConnectionStore
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.InMemoryOnboardingStore
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.SkillRequestCardData
import com.openmausbot.companion.core.StreamFrame
import com.openmausbot.companion.core.TokenStore
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

internal object NotificationFixture {
    const val BOT_ID = "bot-1"
    const val THREAD_ID = "task-1"
    const val REQUEST_ID = "request-1"

    fun bot(
        id: String = BOT_ID,
        name: String = "Scout",
        threadId: String = THREAD_ID,
        color: String = "green",
        chief: Boolean? = null,
        messages: List<Message>? = null,
    ) = Bot(
        id = id,
        threadId = threadId,
        name = name,
        title = "research",
        description = "",
        notifications = true,
        color = color,
        unread = false,
        modelSelection = ModelSelection("instance-1", "model-1"),
        createdAt = 0.0,
        chiefOfStaff = chief,
        messages = messages,
    )

    fun permissionCard(
        options: List<String> = listOf("Allow", "Deny"),
        requestId: String = REQUEST_ID,
        skill: SkillRequestCardData? = null,
    ) = OptionCard(
        title = "Run a command",
        subtitle = "git push",
        options = options,
        requestId = requestId,
        tool = "Bash",
        allowKey = "Bash:git push",
        requestType = "permission",
        skillRequest = skill,
    )

    fun skill() = SkillRequestCardData(
        version = 1,
        requestId = REQUEST_ID,
        botId = BOT_ID,
        threadId = THREAD_ID,
        stagedId = "staged-1",
        action = "create",
        name = "Tidy notes",
        gist = "Sorts notes",
        createdAt = 0L,
    )

    fun cardMessage(card: OptionCard, id: String = "message-1") =
        Message(id = id, role = Message.Role.BOT, kind = Message.Kind.OPTIONS, at = 1.0, card = card)

    fun ok(body: String = "{}"): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    class AnswerDispatcher : okhttp3.mockwebserver.QueueDispatcher() {
        val answers = java.util.concurrent.LinkedBlockingQueue<okhttp3.mockwebserver.RecordedRequest>()

        override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
            if (request.method == "GET") return MockResponse().setResponseCode(404)
            answers.add(request)
            return super.dispatch(request)
        }
    }

    fun MockWebServer.answerDispatcher(): AnswerDispatcher =
        AnswerDispatcher().also { dispatcher = it }

    suspend fun TestScope.liveSession(server: MockWebServer, fleet: Fleet): Session {
        val connection = requireNotNull(Connection.parse(server.url("/").toString()))
        val session = Session(
            scope = backgroundScope,
            connectionStore = object : ConnectionStore {
                override suspend fun load(): Connection = connection
                override suspend fun save(connection: Connection) = Unit
                override suspend fun clear() = Unit
            },
            tokenStore = object : TokenStore {
                override suspend fun save(connectionId: String, token: String) = Unit
                override suspend fun read(connectionId: String): TokenStore.ReadResult =
                    TokenStore.ReadResult.Found("device-token")
                override suspend fun remove(connectionId: String) = Unit
            },
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Pixel" },
            eventsFn = { _, _, _ ->
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false), seq = 1))
                    awaitCancellation()
                }
            },
            hydrateFn = { _, _ -> fleet },
        )
        session.awaitRestored()
        session.connect()
        runCurrent()
        check(session.status.value is Session.Status.Live) { "session is ${session.status.value}" }
        return session
    }

    /** A session that restored without any pairing. */
    suspend fun TestScope.unpairedSession(): Session = Session(
        scope = backgroundScope,
        connectionStore = object : ConnectionStore {
            override suspend fun load(): Connection? = null
            override suspend fun save(connection: Connection) = Unit
            override suspend fun clear() = Unit
        },
        tokenStore = object : TokenStore {
            override suspend fun save(connectionId: String, token: String) = Unit
            override suspend fun read(connectionId: String): TokenStore.ReadResult =
                TokenStore.ReadResult.Missing
            override suspend fun remove(connectionId: String) = Unit
        },
        onboardingStore = InMemoryOnboardingStore(),
        deviceNameProvider = { "Pixel" },
        eventsFn = { _, _, _ -> flow { awaitCancellation() } },
        hydrateFn = { _, _ -> Fleet(emptyList(), emptyList()) },
    ).also { it.awaitRestored() }
}
