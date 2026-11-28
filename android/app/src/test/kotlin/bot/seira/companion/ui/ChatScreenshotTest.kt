package bot.seira.companion.ui

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import bot.seira.companion.core.ActivityDetail
import bot.seira.companion.core.Bot
import bot.seira.companion.core.ChatTarget
import bot.seira.companion.core.Connection
import bot.seira.companion.core.Fleet
import bot.seira.companion.core.Frame
import bot.seira.companion.core.Message
import bot.seira.companion.core.QuickReply
import bot.seira.companion.core.StreamFrame
import bot.seira.companion.core.ToolActivity
import bot.seira.companion.ui.theme.Seira
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chat screen and its + sheet, drawn at 390 by 844 dp in both themes to
 * `build/design-screens/`, for a side-by-side look with the approved design.
 * The data is made up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ChatScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )

    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene

    private val scout: Bot = bot().copy(
        messages = listOf(
            Message("m1", Message.Role.USER, Message.Kind.TEXT, 1.0, text = "Can you check which March invoices are still open for Dina Pratama?"),
            tool("m2", 2.0, "Read invoices", parent = "m1"),
            tool("m3", 3.0, "Check totals", parent = "m2"),
            Message(
                "m4", Message.Role.BOT, Message.Kind.TEXT, 4.0, parentId = "m3",
                text = "Two invoices are still open for Dina Pratama.\n\n" +
                    "| Invoice | Total | Due |\n| --- | ---: | --- |\n" +
                    "| INV-1042 | 1.250.000 | 5 Apr |\n| INV-1043 | 480.000 | 12 Apr |\n\n" +
                    "The larger one is due first, so I would send that reminder today.",
            ),
            Message("m5", Message.Role.USER, Message.Kind.TEXT, 5.0, text = "Yes, draft the reminder.", parentId = "m4"),
            Message("m6", Message.Role.BOT, Message.Kind.TEXT, 6.0, text = "Done. The draft is in your outbox, ready to send.", parentId = "m5"),
        ),
    )

    @Before
    fun start() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/live/call" -> json("""{"call":null}""")
                request.path == "/api/instances" -> json("""{"instances":[]}""")
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

    @Test
    fun lightChatIsWritten() = chat(ThemeMode.LIGHT, sheet = false)

    @Test
    fun darkChatIsWritten() = chat(ThemeMode.DARK, sheet = false)

    @Test
    fun lightSheetIsWritten() = chat(ThemeMode.LIGHT, sheet = true)

    @Test
    fun darkSheetIsWritten() = chat(ThemeMode.DARK, sheet = true)

    private fun chat(mode: ThemeMode, sheet: Boolean) {
        scene = WiringScene(
            connection = Connection(id = "shot", name = "Fixture", host = "127.0.0.1", port = server.port),
            fleet = Fleet(listOf(scout), emptyList()),
            events = {
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false), seq = 1))
                    awaitCancellation()
                }
            },
        )
        compose.setContent {
            CompositionLocalProvider(LocalCompanion provides scene.environment) {
                CompanionTheme(mode = mode, darkTheme = mode == ThemeMode.DARK) {
                    Box(Modifier.fillMaxSize().background(Seira.colors.app)) {
                        val state by scene.session.state.collectAsState()
                        if (state.bot(scout.id) != null) {
                            ChatScreen(
                                destination = Destination.Chat(ChatTarget.Bot(scout.id, scout.threadId)),
                                onResolved = {},
                                onBack = {},
                                onOpenComputer = {},
                                onOpenOverview = {},
                            )
                        }
                    }
                }
            }
        }
        // The app's own settings: folded steps show as one pill, and the chips
        // above the composer are the ones that fit this conversation.
        scene.environment.chatPreferences.setActivityDetail(ActivityDetail.REDUCED)
        scene.environment.chatPreferences.setQuickReplies(
            listOf(
                QuickReply("fx.remind", "Send reminder", "Send the reminder for INV-1042", "send"),
                QuickReply("fx.invoice", "Open invoice", "Show invoice INV-1042", "document"),
                QuickReply("fx.next", "What's next?", "What should we do next?", "next"),
            ),
        )
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5_000) { scene.session.state.value.bot(scout.id) != null }
        compose.waitForIdle()
        if (sheet) {
            compose.onNodeWithContentDescription("More").performClick()
            compose.waitForIdle()
        }
        val name = if (sheet) "chat-sheet" else "chat"
        assertTrue(compose.captureWindow(name, mode).length() > 0)
    }

    private fun tool(id: String, at: Double, name: String, parent: String) =
        Message(id, Message.Role.BOT, Message.Kind.ACTIVITY, at, tool = ToolActivity(name = name, ok = true), parentId = parent)

    private fun json(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
