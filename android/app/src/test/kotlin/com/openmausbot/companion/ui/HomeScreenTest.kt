package com.openmausbot.companion.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.BotTask
import com.openmausbot.companion.core.ChatTarget
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.StreamFrame
import com.openmausbot.companion.storage.ChatPreferences
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Home screen of the design: greeting and profile, the Chief of Staff card,
 * the team rail, and the bar with search and New chat. It runs over a real
 * session, the synthetic [RosterFixture] fleet and a loopback server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene
    private val requests = ConcurrentLinkedQueue<RecordedRequest>()
    private var profileJson = """{"profile":{"name":"Dina Pratama","email":"dina@example.com"}}"""

    @Before
    fun startServer() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return when {
                    request.path == "/api/config" -> json(profileJson)
                    request.method == "POST" && request.path == "/api/bots/${RosterFixture.CHIEF}/tasks" -> createdAtlas()
                    request.path?.startsWith("/api/search") == true -> json("""{"hits":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun stopServer() {
        if (::scene.isInitialized) scene.session.disconnect()
        server.shutdown()
    }

    @Test
    fun `the greeting follows the hour and names the person by first name`() {
        mount(hour = 8)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Good morning, Dina").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home-profile").assert(hasContentDescription("Settings")).assert(hasText("DP"))
    }

    @Test
    fun `the greeting is afternoon until six`() {
        mount(hour = 17)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Good afternoon, Dina").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `without a profile the greeting stands alone and the circle shows a person`() {
        profileJson = "{}"
        mount(hour = 20)
        compose.onNodeWithText("Good evening").assertIsDisplayed()
        compose.onNodeWithTag("home-profile").assert(hasContentDescription("Settings"))
    }

    @Test
    fun `the profile circle opens Settings`() {
        val navigator = mount()
        compose.onNodeWithTag("home-profile").performClick()
        assertEquals(Destination.Settings, navigator.current)
    }

    @Test
    fun `the hero shows the Chief of Staff and opens the chat`() {
        val navigator = mount()
        compose.onNodeWithTag("home-hero").assertIsDisplayed()
            .assert(hasText("Atlas"))
            .assert(hasText("Chief of Staff"))
        compose.onNodeWithTag("home-hero-open").performClick()
        assertEquals(RosterFixture.CHIEF, ((navigator.current as Destination.Chat).target as ChatTarget.Bot).botId)
    }

    @Test
    fun `no Chief of Staff, no hero`() {
        val fleet = RosterFixture.fleet().let { it.copy(bots = it.bots.filter { bot -> bot.chiefOfStaff != true }) }
        mount(fleet = fleet)
        compose.onNodeWithTag("home-hero").assertDoesNotExist()
    }

    @Test
    fun `Talk asks for a call and opens the chat`() {
        val navigator = mount()
        compose.onNodeWithTag("home-talk").assert(hasContentDescription("Talk to Atlas")).performClick()
        assertEquals(RosterFixture.CHIEF, scene.environment.callUi.requestedBotId.value)
        assertEquals(RosterFixture.CHIEF, ((navigator.current as Destination.Chat).target as ChatTarget.Bot).botId)
    }

    @Test
    fun `the rail marks who works and who waits, for TalkBack too`() {
        mount()
        compose.onNodeWithTag("team-member.${RosterFixture.WORKING}").assert(hasContentDescription("Forge, working"))
        compose.onNodeWithTag("team-member.${RosterFixture.WAITING}").assert(hasContentDescription("Scout, waiting on you"))
        // The chief is the hero, not a member.
        compose.onNodeWithTag("team-member.${RosterFixture.CHIEF}").assertDoesNotExist()
    }

    @Test
    fun `a member opens its chat`() {
        val navigator = mount()
        compose.onNodeWithTag("team-member.${RosterFixture.WAITING}").performClick()
        assertEquals(RosterFixture.WAITING, ((navigator.current as Destination.Chat).target as ChatTarget.Bot).botId)
    }

    @Test
    fun `See all wraps the whole team and Show less takes it back`() {
        mount()
        val toggle = compose.onNodeWithTag("home-team-toggle")
        toggle.assert(hasText("See all", substring = true)).performClick()
        compose.onNodeWithText("Show less").assertIsDisplayed()
        compose.onNodeWithText("See all", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Show less").performClick()
        compose.onNodeWithText("See all", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a long press on a member offers New thread and Manage threads`() {
        mount()
        compose.onNodeWithTag("team-member.${RosterFixture.WAITING}").performTouchInput { longClick() }
        compose.onNodeWithTag("team-new-thread.${RosterFixture.WAITING}").assertIsDisplayed()
        compose.onNodeWithTag("team-manage-threads.${RosterFixture.WAITING}").performClick()
        compose.onNodeWithText("Scout's threads").assertIsDisplayed()
    }

    @Test
    fun `the Chats heading and the list follow the hero and the team`() {
        mount()
        compose.onNodeWithText("Chats").assertIsDisplayed()
        compose.onNodeWithTag("chat-row.${RosterFixture.CHIEF}").assertExists()
    }

    @Test
    fun `New chat lists every bot and a thread starts from the one picked`() {
        val navigator = mount()
        compose.onNodeWithTag("home-new-chat").assert(hasContentDescription("New chat")).performClick()
        compose.onNodeWithTag("new-chat-sheet").assertIsDisplayed()
        compose.onNodeWithTag("new-chat-group").assertIsDisplayed()
        compose.onNodeWithTag("new-chat-with.${RosterFixture.WORKING}").assertExists()
        compose.onNodeWithTag("new-chat-with.${RosterFixture.CHIEF}").performClick()
        compose.waitUntil(5_000) {
            (navigator.current as? Destination.Chat)?.target == ChatTarget.Bot(RosterFixture.CHIEF, "roster-atlas-new")
        }
        assertEquals(1, requests.count { it.method == "POST" && it.path == "/api/bots/${RosterFixture.CHIEF}/tasks" })
    }

    @Test
    fun `the search pill opens search and Cancel closes it`() {
        mount()
        compose.onNodeWithText("Search chats and bots").performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithTag("home-hero").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Search chats and bots").assertIsDisplayed()
        compose.onNodeWithTag("home-hero").assertIsDisplayed()
    }

    @Test
    fun `every control is at least 48 dp and the icon ones are labelled`() {
        mount()
        listOf("home-profile", "home-new-chat", "home-talk", "home-team-toggle", "team-member.${RosterFixture.WAITING}")
            .forEach { tag ->
                val bounds = compose.onNodeWithTag(tag).getBoundsInRoot()
                assertTrue(bounds.right - bounds.left >= 48.dp, "$tag is ${bounds.right - bounds.left} wide")
                assertTrue(bounds.bottom - bounds.top >= 48.dp, "$tag is ${bounds.bottom - bounds.top} high")
            }
        compose.onNodeWithTag("home-profile").assert(hasContentDescription("Settings"))
        compose.onNodeWithTag("home-new-chat").assert(hasContentDescription("New chat"))
    }

    private fun mount(
        hour: Int = 9,
        fleet: Fleet = RosterFixture.fleet(),
    ): CompanionNavigator {
        context.getSharedPreferences(ChatPreferences.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val navigator = CompanionNavigator()
        scene = WiringScene(
            connection = Connection(id = "home-fixture", name = "Offline fixture", host = "127.0.0.1", port = server.port),
            fleet = fleet,
            events = {
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false), seq = 1))
                    awaitCancellation()
                }
            },
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalCompanion provides scene.environment,
                LocalHomeHour provides { hour },
            ) {
                CompanionTheme(darkTheme = false) {
                    Surface(Modifier.fillMaxSize()) {
                        val state by scene.session.state.collectAsState()
                        if (state.bots.isNotEmpty()) Screens(navigator)
                    }
                }
            }
        }
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5_000) { scene.session.state.value.bots.isNotEmpty() }
        compose.waitForIdle()
        return navigator
    }

    @Composable
    private fun Screens(navigator: CompanionNavigator) {
        when (navigator.current) {
            Destination.Roster -> RosterScreen(navigator)
            Destination.Settings -> Text("Settings")
            else -> Text("Conversation")
        }
    }

    private fun createdAtlas(): MockResponse {
        val atlas = RosterFixture.fleet().bots.first { it.id == RosterFixture.CHIEF }
        val created = atlas.copy(
            threadId = "roster-atlas-new",
            tasks = atlas.tasks.orEmpty() + BotTask(threadId = "roster-atlas-new", title = "", createdAt = 2.0),
            messages = emptyList(),
        )
        return json("""{"bot":${CompanionJson.encodeToString(Bot.serializer(), created)}}""")
    }

    private fun json(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
