package bot.seira.companion.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import bot.seira.companion.core.Connection
import bot.seira.companion.core.Frame
import bot.seira.companion.core.RosterDensity
import bot.seira.companion.core.StreamFrame
import bot.seira.companion.ui.home.HomeShell
import bot.seira.companion.storage.ChatPreferences
import bot.seira.companion.ui.theme.ThemeMode
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

/** Writes the Home screen, light and dark, to `build/design-screens` for the design review. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )

    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene

    @Before
    fun startServer() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/config" -> MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"profile":{"name":"Dina Pratama","email":"dina@example.com"}}""")
                else -> MockResponse().setResponseCode(404)
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
    fun lightHomeIsWritten() = assertTrue(capture("home", ThemeMode.LIGHT, RosterDensity.COMPACT))

    @Test
    fun darkHomeIsWritten() = assertTrue(capture("home", ThemeMode.DARK, RosterDensity.COMPACT))

    @Test
    fun lightComfortableHomeIsWritten() =
        assertTrue(capture("home-comfortable", ThemeMode.LIGHT, RosterDensity.COMFORTABLE))

    @Test
    fun darkComfortableHomeIsWritten() =
        assertTrue(capture("home-comfortable", ThemeMode.DARK, RosterDensity.COMFORTABLE))

    // The whole list in one picture: a screen tall enough that nothing scrolls.
    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h1800dp-xxhdpi")
    fun lightTallHomeIsWritten() =
        assertTrue(capture("home-tall", ThemeMode.LIGHT, RosterDensity.COMPACT, scrolled = false))

    @Test
    @Config(sdk = [34], qualifiers = "w390dp-h1800dp-xxhdpi")
    fun lightTallComfortableHomeIsWritten() =
        assertTrue(capture("home-comfortable-tall", ThemeMode.LIGHT, RosterDensity.COMFORTABLE, scrolled = false))

    private fun capture(name: String, mode: ThemeMode, density: RosterDensity, scrolled: Boolean = true): Boolean {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(ChatPreferences.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        scene = WiringScene(
            connection = Connection(id = "home-shot", name = "Offline fixture", host = "127.0.0.1", port = server.port),
            fleet = RosterFixture.fleet(),
            events = {
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false), seq = 1))
                    awaitCancellation()
                }
            },
        )
        scene.environment.chatPreferences.setRosterDensity(density)
        scene.session.connect()
        val deadline = System.currentTimeMillis() + 10_000
        while (scene.session.state.value.bots.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        val file = compose.captureDesign(
            name,
            mode,
            beforeCapture = {
                // Same state in both modes: the chat list is on screen.
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("roster-list").fetchSemanticsNodes().isNotEmpty()
                }
                // The comfortable rows start under Needs attention: bring them up.
                if (scrolled && density == RosterDensity.COMFORTABLE) {
                    val list = compose.onNodeWithTag("roster-list")
                    list.performScrollToNode(hasTestTag("chat-row.${RosterFixture.CHIEF}"))
                    compose.waitForIdle()
                    list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_700f) }
                }
            },
        ) { Home() }
        return file.length() > 0
    }

    @Composable
    private fun Home() {
        val navigator = remember { CompanionNavigator() }
        val state by scene.session.state.collectAsState()
        CompositionLocalProvider(
            LocalCompanion provides scene.environment,
            LocalHomeHour provides { 9 },
        ) {
            if (state.bots.isNotEmpty()) HomeShell(navigator)
        }
    }
}
