package com.openmausbot.companion.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.openmausbot.companion.core.AvatarCrop
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.ModelSelection
import com.openmausbot.companion.core.SeiraLook
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.StreamFrame
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
class AvatarSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<Pair<String, JsonObject>>()
    private var scene: WiringScene? = null

    private val plain = Bot(
        id = "b1", threadId = "thread-b1", name = "Dina Pratama", title = "", description = "",
        notifications = true, color = "green", unread = false,
        modelSelection = ModelSelection("instance-1", "model-1"), createdAt = 0.0,
    )
    private val shown = plain.copy(avatarUrl = "/api/attachments/a.png", avatarCrop = AvatarCrop.CIRCLE)
    private val hidden = plain.copy(avatarUrl = "/api/attachments/a.png", avatarCrop = AvatarCrop.MASCOT)

    @After fun cleanup() {
        compose.runOnIdle { scene?.session?.disconnect() }
        server.shutdown()
    }

    private fun showBody(
        tab: AvatarTab,
        panels: AvatarPanels = AvatarPanels(),
        connected: Boolean = true,
        state: ChangeLookState = ChangeLookState(SeiraLook.from(plain), emptyList()),
    ) = compose.setContent {
        CompanionTheme(darkTheme = false) {
            ChangeLookSheetBody(
                botName = plain.name, state = state, connected = connected, tab = tab, panels = panels,
                onTab = {}, onClose = {}, onSave = {},
            )
        }
    }

    @Test
    fun `AC-01 the sheet shows exactly three tabs`() {
        var tab by mutableStateOf(AvatarTab.SEIRA)
        compose.setContent {
            CompanionTheme(darkTheme = false) {
                ChangeLookSheetBody(
                    botName = plain.name, state = ChangeLookState(SeiraLook.from(plain), emptyList()), connected = true,
                    tab = tab, onTab = { tab = it }, onClose = {}, onSave = {},
                )
            }
        }
        compose.onNodeWithTag("avatar-tab-seira").assertIsSelected()
        compose.onNodeWithTag("avatar-tab-generate").assertIsDisplayed().performClick()
        compose.onNodeWithTag("avatar-tab-generate").assertIsSelected()
        compose.onNodeWithTag("avatar-tab-upload").assertIsDisplayed().performClick()
        compose.onNodeWithTag("avatar-tab-upload").assertIsSelected()
        compose.onNodeWithTag("avatar-prompt").assertDoesNotExist()
        compose.onNodeWithTag("avatar-choose").assertIsDisplayed()
    }

    @Test
    fun `AC-02 the Seira tab offers Hair, Face, Extras, Colour and Surprise me`() {
        showBody(AvatarTab.SEIRA)
        listOf("Hair", "Face", "Extras", "Colour").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("change-look-surprise").assertIsDisplayed()
        compose.onNodeWithTag("change-look-save").assertIsDisplayed()
    }

    @Test
    fun `AC-03 no shape choice, Characters list, Reset or other-bots button is shown`() {
        showBody(AvatarTab.SEIRA)
        listOf("Characters", "Mascot", "Circle", "Rounded", "Square", "Reset", "More looks").forEach {
            compose.onNode(hasText(it)).assertDoesNotExist()
        }
    }

    @Test
    fun `the footer with Surprise and Save is only on the Seira tab`() {
        showBody(AvatarTab.GENERATE)
        compose.onNodeWithTag("change-look-save").assertDoesNotExist()
        compose.onNodeWithTag("change-look-surprise").assertDoesNotExist()
    }

    @Test
    fun `AC-09 Generate is off and one sentence names the next step while generation is not set up`() {
        val setup = "Set up image generation in Seira on your computer to use this."
        showBody(AvatarTab.GENERATE, AvatarPanels(generationReady = false, canGenerate = false, prompt = "a fox"))
        compose.onNodeWithTag("avatar-generate").assertIsNotEnabled().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, setup),
        )
        compose.onNodeWithText(setup).assertIsDisplayed()
        compose.onNode(hasText("provider", substring = true, ignoreCase = true)).assertDoesNotExist()
        compose.onNode(hasText("key", substring = true, ignoreCase = true)).assertDoesNotExist()
    }

    @Test
    fun `Generate is on when it is set up and there is something to ask for`() {
        showBody(AvatarTab.GENERATE, AvatarPanels(generationReady = true, canGenerate = true, prompt = "a fox"))
        compose.onNodeWithTag("avatar-generate").assertIsEnabled()
        compose.onNodeWithTag("avatar-generate-setup").assertDoesNotExist()
    }

    @Test
    fun `AC-06 Use this picture is offered while a stored picture is hidden, and Remove picture while one is stored`() {
        showBody(AvatarTab.UPLOAD, AvatarPanels(hasPicture = true, showsPicture = false))
        compose.onNodeWithTag("avatar-use").assertIsDisplayed()
        compose.onNodeWithTag("avatar-remove").assertIsDisplayed()
    }

    @Test
    fun `AC-07 a shown picture offers Remove picture and no Use this picture`() {
        showBody(AvatarTab.UPLOAD, AvatarPanels(hasPicture = true, showsPicture = true))
        compose.onNodeWithTag("avatar-use").assertDoesNotExist()
        compose.onNodeWithTag("avatar-remove").assertIsDisplayed()
    }

    @Test
    fun `a bot with no stored picture only offers Choose a picture`() {
        showBody(AvatarTab.UPLOAD)
        compose.onNodeWithTag("avatar-choose").assertIsDisplayed()
        compose.onNodeWithTag("avatar-use").assertDoesNotExist()
        compose.onNodeWithTag("avatar-remove").assertDoesNotExist()
    }

    @Test
    fun `AC-10 the upload line names the file types and no token`() {
        showBody(AvatarTab.UPLOAD)
        compose.onNodeWithText("PNG, JPEG, GIF, or WebP, up to 10 MB.").assertIsDisplayed()
        compose.onNode(hasText("token", substring = true, ignoreCase = true)).assertDoesNotExist()
        compose.onNode(hasText("paired", substring = true, ignoreCase = true)).assertDoesNotExist()
    }

    @Test
    fun `AC-12 a note under the tabs shows the error`() {
        showBody(AvatarTab.UPLOAD, AvatarPanels(note = "Couldn't change the picture. Try again."))
        compose.onNodeWithTag("avatar-note").assertIsDisplayed()
        compose.onNodeWithText("Couldn't change the picture. Try again.").assertIsDisplayed()
    }

    @Test
    fun `a bot that shows a picture opens on the Upload tab`() {
        start(served = shown)
        showSheet(shown)
        compose.onNodeWithTag("avatar-tab-upload").assertIsSelected()
        compose.onNodeWithTag("avatar-remove").assertIsDisplayed()
    }

    @Test
    fun `a bot with no picture opens on the Seira tab`() {
        start(served = plain)
        showSheet(plain)
        compose.onNodeWithTag("avatar-tab-seira").assertIsSelected()
    }

    @Test
    fun `AC-04 saving a look for a bot that shows a picture switches it to the mascot and keeps the picture`() {
        start(served = shown)
        showSheet(shown)
        compose.onNodeWithTag("avatar-tab-seira").performClick()
        compose.onNodeWithTag("change-look-save").assertIsEnabled()
        compose.onNodeWithText("Colour").performClick()
        compose.onNodeWithContentDescription("Blue").performClick()
        compose.onNodeWithTag("change-look-save").performClick()
        compose.waitUntil(10_000) { requests.size == 2 }
        assertEquals("PATCH /api/bots/b1", requests[0].first)
        assertEquals(setOf("color"), requests[0].second.keys)
        assertEquals("PATCH /api/bots/b1/profile", requests[1].first)
        assertEquals(setOf("avatarCrop"), requests[1].second.keys)
        assertEquals("mascot", requests[1].second.getValue("avatarCrop").jsonPrimitive.content)
    }

    @Test
    fun `AC-05 using a stored picture shows it in a circle`() {
        start(served = hidden)
        showSheet(hidden)
        compose.onNodeWithTag("avatar-tab-upload").performClick()
        compose.onNodeWithTag("avatar-use").performClick()
        compose.waitUntil(10_000) { requests.size == 1 }
        assertEquals("PATCH /api/bots/b1/profile", requests.single().first)
        assertEquals(setOf("avatarCrop"), requests.single().second.keys)
        assertEquals("circle", requests.single().second.getValue("avatarCrop").jsonPrimitive.content)
    }

    @Test
    fun `AC-07 removing the picture returns the bot to the mascot`() {
        start(served = shown)
        showSheet(shown)
        compose.onNodeWithTag("avatar-remove").performClick()
        compose.waitUntil(10_000) { requests.size == 1 }
        val sent = requests.single().second
        assertEquals(setOf("avatarUrl", "avatarCrop"), sent.keys)
        assertEquals(JsonNull, sent.getValue("avatarUrl"))
        assertEquals("mascot", sent.getValue("avatarCrop").jsonPrimitive.content)
    }

    @Test
    fun `AC-05 a generated picture that comes back as the mascot is shown in a circle`() {
        start(served = plain, generated = hidden.copy(avatarCrop = null), imageGen = true)
        showSheet(plain)
        compose.onNodeWithTag("avatar-tab-generate").performClick()
        compose.waitUntil(10_000) { scene!!.session.status.value is Session.Status.Live }
        compose.waitUntil(10_000) { requests.any { it.first == "GET /api/config" } }
        compose.onNodeWithTag("avatar-prompt").performTextInput("a fox")
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("avatar-generate").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("avatar-generate").performClick()
        compose.waitUntil(10_000) { requests.any { it.first == "PATCH /api/bots/b1/profile" } }
        assertEquals("POST /api/bots/b1/avatar/generate", requests.first { it.first.startsWith("POST") }.first)
        assertEquals("a fox", requests.first { it.first.startsWith("POST") }.second.getValue("prompt").jsonPrimitive.content)
        val patch = requests.first { it.first == "PATCH /api/bots/b1/profile" }.second
        assertEquals("circle", patch.getValue("avatarCrop").jsonPrimitive.content)
    }

    @Test
    fun `AC-12 a failed generation shows one line and keeps the tab`() {
        start(served = shown, generated = null, imageGen = true)
        showSheet(shown)
        compose.onNodeWithTag("avatar-tab-generate").performClick()
        compose.waitUntil(10_000) { requests.any { it.first == "GET /api/config" } }
        compose.onNodeWithTag("avatar-prompt").performTextInput("a fox")
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("avatar-generate").assertIsEnabled() }.isSuccess }
        compose.onNodeWithTag("avatar-generate").performClick()
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("avatar-note").assertIsDisplayed() }.isSuccess }
        compose.onNodeWithText("Couldn't make the picture. Try again.").assertIsDisplayed()
        compose.onNodeWithTag("avatar-tab-generate").assertIsSelected()
        assertEquals(shown.avatarUrl, scene!!.session.state.value.bot("b1")?.avatarUrl)
        assertEquals(AvatarCrop.CIRCLE, scene!!.session.state.value.bot("b1")?.avatarCrop)
    }

    @Test
    fun `AC-11 the profile sheet has one Avatar row and no Avatar or Generate sections`() {
        val source = java.io.File("src/main/kotlin/com/openmausbot/companion/ui/AgentProfileSheet.kt")
            .takeIf { it.isFile }
            ?: java.io.File("app/src/main/kotlin/com/openmausbot/companion/ui/AgentProfileSheet.kt")
        val text = source.readText()
        assertEquals(0, Regex("""header = "(Avatar|Generate an avatar)"""").findAll(text).count())
        assertEquals(1, Regex("""R\.string\.mobile_avatar_7631b26e""").findAll(text).count())
        assertEquals(0, Regex("""AvatarCrop|SegmentedButton|pickImage|AVATAR_FOOTER""").findAll(text).count())
    }

    private fun start(served: Bot, generated: Bot? = null, imageGen: Boolean = false) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.path}"
                val raw = request.body.readUtf8()
                val body = if (raw.isBlank()) buildJsonObject {} else CompanionJson.parseToJsonElement(raw).jsonObject
                if (key != "GET /api/stream" && !key.startsWith("GET /api/events")) requests.add(key to body)
                return when (key) {
                    "GET /api/config" -> ok(
                        """{"imageGen":{"configured":$imageGen}}""",
                    )
                    "PATCH /api/bots/b1" -> bot(served.copy(color = "blue"))
                    "PATCH /api/bots/b1/profile" -> {
                        val crop = body["avatarCrop"]?.jsonPrimitive?.content
                        val cleared = body["avatarUrl"] is JsonNull
                        bot(
                            served.copy(
                                avatarCrop = crop?.let { c -> AvatarCrop.entries.first { it.name.lowercase() == c } }
                                    ?: served.avatarCrop,
                                avatarUrl = if (cleared) null else served.avatarUrl,
                            ),
                        )
                    }
                    "POST /api/bots/b1/avatar/generate" ->
                        if (generated == null) {
                            MockResponse().setResponseCode(500)
                        } else {
                            ok(
                                buildJsonObject {
                                    put("avatarUrl", "/api/attachments/a.png")
                                    put("bot", CompanionJson.parseToJsonElement(CompanionJson.encodeToString(Bot.serializer(), generated)))
                                }.toString(),
                            )
                        }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        scene = WiringScene(
            connection = Connection(id = "avatar-fixture", name = "Fixture", host = "127.0.0.1", port = server.port),
            fleet = Fleet(listOf(served), emptyList()),
        ) { flow { emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false), seq = 1)); awaitCancellation() } }
    }

    private fun bot(value: Bot): MockResponse =
        ok(buildJsonObject { put("bot", CompanionJson.parseToJsonElement(CompanionJson.encodeToString(Bot.serializer(), value))) }.toString())

    private fun ok(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun showSheet(bot: Bot) {
        val wiring = scene!!
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides wiring.environment) {
                CompanionTheme(darkTheme = false) { ChangeLookSheet(bot = bot, onDismiss = {}) }
            }
        }
        compose.runOnIdle { wiring.session.connect() }
        compose.waitUntil(10_000) { wiring.session.status.value is Session.Status.Live }
    }
}
