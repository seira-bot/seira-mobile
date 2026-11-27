package bot.seira.companion.ui

import android.content.Context
import android.os.Looper
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import bot.seira.companion.core.Bot
import bot.seira.companion.core.FailedPlace
import bot.seira.companion.core.Chat
import bot.seira.companion.core.Connection
import bot.seira.companion.core.Fleet
import bot.seira.companion.core.Message
import bot.seira.companion.core.StreamFrame
import bot.seira.companion.core.Frame
import bot.seira.companion.core.ToolActivity
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class WorksOnActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene
    private var ownServerConfigured = true
    private var phoneWorksOn = true
    private var patchStatus = 200
    private var patchBody = BOT_OFF
    private val patches = mutableListOf<String>()

    private val notice = Message(
        "n1", Message.Role.BOT, Message.Kind.ACTIVITY, 3.0,
        tool = ToolActivity(name = "This bot's cloud computer isn't set up.", ok = true, notice = true, place = FailedPlace("cc-not-set-up")),
    )
    private val user = Message("u1", Message.Role.USER, Message.Kind.TEXT, 1.0, text = "Check the invoices")

    @Before
    fun start() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "PATCH" && request.path == "/api/bots/bot-1" -> {
                    patches += request.body.readUtf8()
                    json(patchBody, patchStatus)
                }
                request.path == "/.well-known/seira/environment" || request.path == "/.well-known/openmausbot/environment" ->
                    json("""{"environmentId":"environment-1","label":"Fixture"}""")
                request.path == "/api/config" -> json("""{"phoneWorksOn":$phoneWorksOn,"vps":{"configured":$ownServerConfigured}}""")
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

    private fun show(busy: Boolean? = null, pairedWithServer: Boolean = true) {
        val bot: Bot = bot(busy = busy).copy(computer = "cloud", messages = listOf(user, notice))
        scene = WiringScene(
            connection = Connection(
                id = "fx", name = "Fixture", host = "127.0.0.1", port = server.port,
                serverEnvironmentId = "environment-1".takeIf { pairedWithServer },
                serverScopes = listOf("admin", "client").takeIf { pairedWithServer },
            ),
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
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { MessageRow(Chat.BotChat(bot), notice) }
                }
            }
        }
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5_000) {
            // Robolectric: waitUntil does not run the main looper, where the session resumes after a network reply.
            shadowOf(Looper.getMainLooper()).idle()
            scene.session.state.value.bot(bot.id) != null
        }
        compose.waitForIdle()
    }

    private fun count(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size

    @Test
    fun offersBothActionsWhenTheOwnServerIsReady() {
        show()
        compose.waitUntil(5_000) { count("Use your own server") > 0 }
        compose.onNodeWithText("Work without a computer").assertIsDisplayed()
    }

    @Test
    fun nothingShowsForAPhonePairedThroughTheComputer() {
        show(pairedWithServer = false)
        compose.waitForIdle()
        assertEquals(0, count("Work without a computer"))
        assertEquals(0, count("Use your own server"))
    }

    @Test
    fun offersOnlyNoComputerWhenThereIsNoOwnServer() {
        ownServerConfigured = false
        show()
        compose.waitUntil(5_000) { count("Work without a computer") > 0 }
        compose.onNodeWithText("Work without a computer").assertIsDisplayed()
        assertEquals(0, count("Use your own server"))
    }

    @Test
    fun nothingShowsWhenTheServerDoesNotAcceptTheChange() {
        phoneWorksOn = false
        show()
        compose.waitForIdle()
        assertEquals(0, count("Work without a computer"))
    }

    @Test
    fun aForbiddenAnswerSaysToChangeItOnTheComputer() {
        patchStatus = 403
        patchBody = """{"error":"a paired device may change how a bot looks"}"""
        show()
        compose.waitUntil(5_000) { count("Work without a computer") > 0 }
        compose.onNodeWithText("Work without a computer").performClick()
        compose.waitUntil(5_000) { count("Change this on the computer.") > 0 }
        assertEquals(0, count("a paired device may change how a bot looks"))
    }

    @Test
    fun nothingShowsWhileATurnRuns() {
        show(busy = true)
        assertEquals(0, count("Work without a computer"))
    }

    @Test
    fun aTapSendsExactlyComputerOffAndShowsTheNewPlace() {
        show()
        compose.waitUntil(5_000) { count("Work without a computer") > 0 }
        compose.onNodeWithText("Work without a computer").performClick()
        compose.waitUntil(5_000) { count("Now works without a computer.") > 0 }
        assertEquals(listOf("""{"computer":"off"}"""), patches)
    }

    @Test
    fun aRefusalShowsTheServersSentence() {
        patchStatus = 409
        patchBody = """{"error":"Set up your own server first."}"""
        show()
        compose.waitUntil(5_000) { count("Use your own server") > 0 }
        compose.onNodeWithText("Use your own server").performClick()
        compose.waitUntil(5_000) { count("Set up your own server first.") > 0 }
        assertEquals(listOf("""{"computer":"cloud","cloudBackend":"vps"}"""), patches)
    }

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val BOT_OFF = """{"bot":{"id":"bot-1","threadId":"thread-1","name":"Scout","title":"","description":"",
            "notifications":true,"color":"#3366ff","unread":false,"createdAt":1.0,
            "modelSelection":{"instanceId":"codex","model":"gpt-5"},"computer":"off"}}"""
    }
}
