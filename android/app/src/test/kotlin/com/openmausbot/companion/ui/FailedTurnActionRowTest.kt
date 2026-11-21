package com.openmausbot.companion.ui

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CODEX_USAGE_LIMIT_REACHED
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.StreamFrame
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.ToolActivity
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class FailedTurnActionRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene
    private var modelListStatus = 200

    private fun failure(tool: ToolActivity) = Message("f1", Message.Role.BOT, Message.Kind.ACTIVITY, 3.0, tool = tool)
    private val overloaded = ToolActivity(name = "error: Claude is overloaded right now.", ok = false)
    private val user = Message("u1", Message.Role.USER, Message.Kind.TEXT, 1.0, text = "Check the invoices")

    @Before
    fun start() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/instances" ->
                    json(if (modelListStatus == 200) """{"instances":[]}""" else """{"error":"forbidden"}""", modelListStatus)
                request.path == "/api/live/call" -> json("""{"call":null}""")
                request.path?.startsWith("/api/threads/") == true -> json("""{"messages":[],"hasMore":false}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After
    fun stop() {
        if (::scene.isInitialized) scene.session.disconnect()
        server.shutdown()
    }

    private fun show(tool: ToolActivity, vararg after: Message, busy: Boolean? = null) {
        val failed = failure(tool)
        val bot: Bot = bot(busy = busy).copy(messages = listOf(user, failed) + after)
        scene = WiringScene(
            connection = Connection(id = "fx", name = "Fixture", host = "127.0.0.1", port = server.port),
            fleet = Fleet(listOf(bot), emptyList()),
            events = {
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false), seq = 1))
                    awaitCancellation()
                }
            },
        )
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                CompanionTheme(darkTheme = false) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { MessageRow(Chat.BotChat(bot), failed) }
                }
            }
        }
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5_000) { scene.session.state.value.bot(bot.id) != null }
        compose.waitForIdle()
    }

    @Test
    fun aModelFailureOffersSwitchModelAndTryAgain() {
        show(overloaded)
        compose.onNodeWithText("Switch model").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun noModelListHidesSwitchModelAndAsksTheOwner() {
        modelListStatus = 403
        show(overloaded)
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Ask the owner of this workspace to add another AI."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Try again").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasText("Switch model")).fetchSemanticsNodes().size)
    }

    @Test
    fun aResetTimeOffersContinueAt() {
        show(overloaded.copy(limitResetsAt = 1_790_000_000_000.0))
        compose.onNode(androidx.compose.ui.test.hasText("Continue at", substring = true)).assertIsDisplayed()
    }

    @Test
    fun theCodexUsageLimitOffersCheckUsageOnly() {
        show(ToolActivity(name = "error: $CODEX_USAGE_LIMIT_REACHED", ok = false))
        compose.onNodeWithText("Check usage").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasText("Switch model")).fetchSemanticsNodes().size)
    }

    @Test
    fun nothingShowsWhileATurnRuns() {
        show(overloaded, busy = true)
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasText("Try again")).fetchSemanticsNodes().size)
    }

    @Test
    fun anOlderFailureShowsNothing() {
        show(overloaded, Message("u2", Message.Role.USER, Message.Kind.TEXT, 4.0, text = "again"))
        assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasText("Try again")).fetchSemanticsNodes().size)
    }

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
