package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.core.StreamFrame
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChangeLookSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )
    private val server = MockWebServer()
    private val patches = ConcurrentLinkedQueue<RecordedRequest>()
    private var scene: WiringScene? = null

    private val bot = Bot(
        id = "b1",
        threadId = "thread-b1",
        name = "Dina Pratama",
        title = "research",
        description = "",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("instance-1", "model-1"),
        createdAt = 0.0,
    )

    @After fun cleanup() {
        compose.runOnIdle { scene?.session?.disconnect() }
        server.shutdown()
    }

    private fun hasState(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun showBody(state: ChangeLookState, connected: Boolean) = compose.setContent {
        CompanionTheme(darkTheme = false) {
            ChangeLookSheetBody(botName = bot.name, state = state, connected = connected, onClose = {}, onSave = {})
        }
    }

    @Test
    fun `AC-07 save is off with the reason no changes yet`() {
        val state = ChangeLookState(SeiraLook.from(bot), emptyList())
        showBody(state, connected = true)
        compose.onNodeWithTag("change-look-save").assertIsNotEnabled().assert(hasState("No changes yet"))
        compose.runOnIdle { state.setColor("blue") }
        compose.onNodeWithTag("change-look-save").assertIsEnabled()
    }

    @Test
    fun `AC-08 save is off with the reason connect to your computer`() {
        val state = ChangeLookState(SeiraLook.from(bot), emptyList())
        compose.runOnIdle { state.setColor("blue") }
        showBody(state, connected = false)
        compose.onNodeWithTag("change-look-save").assertIsNotEnabled().assert(hasState("Connect to your computer to save."))
        compose.onNodeWithText("Connect to your computer to save.").assertIsDisplayed()
    }

    @Test
    fun `AC-09 a failed save shows the message and keeps the sheet open`() {
        start(succeed = false)
        var closed = false
        showSheet { closed = true }
        pickBlueAndSave()
        compose.waitUntil(10_000) { compose.onAllNodesWithTextCount("Couldn't save. Try again.") > 0 }
        compose.onNodeWithText("Couldn't save. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Change look").assertIsDisplayed()
        assertEquals(false, closed)
        assertEquals(1, patches.size)
    }

    @Test
    fun `AC-10 a good save sends only the changed field and closes the sheet`() {
        start(succeed = true)
        var closed = false
        showSheet { closed = true }
        pickBlueAndSave()
        compose.waitUntil(10_000) { closed }
        assertEquals(1, patches.size)
        val body = CompanionJson.parseToJsonElement(patches.single().body.readUtf8()) as JsonObject
        assertEquals(setOf("color"), body.keys)
    }

    @Test
    fun `a picked colour and the open tab survive a restore`() {
        start(succeed = true)
        val wiring = scene!!
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalCompanion provides wiring.environment) {
                CompanionTheme(darkTheme = false) { ChangeLookSheet(bot = bot, onDismiss = {}) }
            }
        }
        compose.runOnIdle { wiring.session.connect() }
        compose.waitUntil(10_000) { wiring.session.status.value is Session.Status.Live }
        compose.onNodeWithText("Colour").performClick()
        compose.onNodeWithContentDescription("Blue").performClick()
        compose.onNodeWithContentDescription("Blue").assertIsSelected()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Blue").assertIsSelected()
        compose.onNodeWithTag("change-look-save").assertIsEnabled()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size

    private fun start(succeed: Boolean) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method != "PATCH" || request.path != "/api/bots/b1") return MockResponse().setResponseCode(404)
                patches.add(request)
                if (!succeed) return MockResponse().setResponseCode(500)
                val updated = CompanionJson.encodeToString(Bot.serializer(), bot.copy(color = "blue"))
                val body = buildJsonObject { put("bot", CompanionJson.parseToJsonElement(updated)) }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body.toString())
            }
        }
        server.start()
        scene = WiringScene(
            connection = Connection(id = "look-fixture", name = "Fixture", host = "127.0.0.1", port = server.port),
            fleet = Fleet(listOf(bot), emptyList()),
        ) { flow { emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false), seq = 1)); awaitCancellation() } }
    }

    private fun showSheet(onDismiss: () -> Unit) {
        val wiring = scene!!
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides wiring.environment) {
                CompanionTheme(darkTheme = false) { ChangeLookSheet(bot = bot, onDismiss = onDismiss) }
            }
        }
        compose.runOnIdle { wiring.session.connect() }
        compose.waitUntil(10_000) { wiring.session.status.value is Session.Status.Live }
    }

    private fun pickBlueAndSave() {
        compose.onNodeWithText("Colour").performClick()
        compose.onNodeWithContentDescription("Blue").performClick()
        compose.onNodeWithContentDescription("Blue").assertIsSelected()
        compose.onNodeWithTag("change-look-save").assertIsEnabled().performClick()
    }
}
