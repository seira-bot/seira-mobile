package com.openmausbot.companion.ui

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.Bot
import com.openmausbot.companion.core.ChatTarget
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.Profile
import com.openmausbot.companion.core.QuickReply
import com.openmausbot.companion.core.RosterDensity
import com.openmausbot.companion.core.StreamFrame
import com.openmausbot.companion.core.ToolActivity
import com.openmausbot.companion.storage.ChatPreferences
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.ThemeMode
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
 * The screens for the Play Store listing, drawn from the real app UI with made-up
 * data to `build/design-screens/store-*.png`. Phone images are 9:16.
 * Tablet images are landscape and light only.
 * The computer reports a call on a different chat, so no Talk pill, call button or composer talk shows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h731dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class StoreScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale { override val scaleFactor = 0f },
    )

    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene
    private val callReads = java.util.concurrent.atomic.AtomicInteger()

    @Before
    fun start() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/config" -> json("""{"profile":{"name":"Dina Pratama","email":"dina@example.com"}}""")
                request.path == "/api/live/call" -> {
                    callReads.incrementAndGet()
                    json(OTHER_CHAT_CALL)
                }
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

    // Home

    @Test fun lightHome() = home("store-home", ThemeMode.LIGHT)

    @Test fun darkHome() = home("store-home", ThemeMode.DARK)

    // Chat with a table

    @Test fun lightChatTableEn() = chat("store-chat-table-en", ThemeMode.LIGHT, TABLE_EN, TABLE_EN_REPLIES)

    @Test fun darkChatTableEn() = chat("store-chat-table-en", ThemeMode.DARK, TABLE_EN, TABLE_EN_REPLIES)

    @Test fun lightChatTableId() = chat("store-chat-table-id", ThemeMode.LIGHT, TABLE_ID, TABLE_ID_REPLIES)

    @Test fun darkChatTableId() = chat("store-chat-table-id", ThemeMode.DARK, TABLE_ID, TABLE_ID_REPLIES)

    // Chat with an approval card

    @Test fun lightChatApprovalEn() = chat("store-chat-approval-en", ThemeMode.LIGHT, APPROVAL_EN, emptyList())

    @Test fun darkChatApprovalEn() = chat("store-chat-approval-en", ThemeMode.DARK, APPROVAL_EN, emptyList())

    @Test fun lightChatApprovalId() = chat("store-chat-approval-id", ThemeMode.LIGHT, APPROVAL_ID, emptyList())

    @Test fun darkChatApprovalId() = chat("store-chat-approval-id", ThemeMode.DARK, APPROVAL_ID, emptyList())

    // Pair and settings

    @Test fun lightPair() = shot("store-pair", ThemeMode.LIGHT) { Pair() }

    @Test fun darkPair() = shot("store-pair", ThemeMode.DARK) { Pair() }

    @Test fun lightPairConnected() = shot("store-pair-connected", ThemeMode.LIGHT) { Connected() }

    @Test fun darkPairConnected() = shot("store-pair-connected", ThemeMode.DARK) { Connected() }

    @Test fun lightSettings() = shot("store-settings", ThemeMode.LIGHT) { SettingsPage() }

    @Test fun darkSettings() = shot("store-settings", ThemeMode.DARK) { SettingsPage() }

    // Tablets, landscape, light only

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
    fun tablet7Home() = home("store-tablet7-home", ThemeMode.LIGHT)

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
    fun tablet7ChatTableEn() = chat("store-tablet7-chat-table-en", ThemeMode.LIGHT, TABLE_EN, TABLE_EN_REPLIES)

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h720dp-xhdpi")
    fun tablet10Home() = home("store-tablet10-home", ThemeMode.LIGHT)

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h720dp-xhdpi")
    fun tablet10ChatTableEn() = chat("store-tablet10-chat-table-en", ThemeMode.LIGHT, TABLE_EN, TABLE_EN_REPLIES)

    // Scenes

    private fun shot(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        assertTrue(compose.captureDesign(name, mode, content = content).length() > 0)
    }

    private fun home(name: String, mode: ThemeMode) {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(ChatPreferences.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        scene = WiringScene(
            connection = Connection(id = "store-home", name = "Fixture", host = "127.0.0.1", port = server.port),
            fleet = RosterFixture.fleet(),
            events = helloOnly(),
        )
        scene.environment.chatPreferences.setRosterDensity(RosterDensity.COMPACT)
        scene.session.connect()
        val deadline = System.currentTimeMillis() + 10_000
        while (scene.session.state.value.bots.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        val file = compose.captureDesign(
            name,
            mode,
            beforeCapture = {
                // The computer reports a call on another chat: Home offers no Talk pill.
                compose.waitUntil(10_000) { scene.session.state.value.liveCall != null }
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithText("Good morning, Dina").fetchSemanticsNodes().isNotEmpty()
                }
            },
        ) {
            val navigator = remember { CompanionNavigator() }
            val state by scene.session.state.collectAsState()
            CompositionLocalProvider(
                LocalCompanion provides scene.environment,
                LocalHomeHour provides { 9 },
            ) {
                if (state.bots.isNotEmpty()) RosterScreen(navigator)
            }
        }
        assertTrue(file.length() > 0)
    }

    private fun chat(name: String, mode: ThemeMode, messages: List<Message>, replies: List<QuickReply>) {
        val scout: Bot = bot().copy(messages = messages)
        scene = WiringScene(
            connection = Connection(id = "store-chat", name = "Fixture", host = "127.0.0.1", port = server.port),
            fleet = Fleet(listOf(scout), emptyList()),
            events = helloOnly(),
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
        scene.environment.chatPreferences.setActivityDetail(ActivityDetail.REDUCED)
        scene.environment.chatPreferences.setQuickReplies(replies)
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5_000) { scene.session.state.value.bot(scout.id) != null }
        // The computer reports a call on another chat: this chat offers no call button.
        val deadline = System.currentTimeMillis() + 20_000
        while (scene.session.state.value.liveCall == null && System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            Thread.sleep(100)
        }
        assertTrue(scene.session.state.value.liveCall != null, "call reads on the server: ${callReads.get()}")
        compose.waitForIdle()
        assertTrue(compose.captureWindow(name, mode).length() > 0)
    }

    @Composable
    private fun SettingsPage() {
        val settingsScene = remember {
            WiringScene(
                connection = Connection(id = "c1", name = "Dina's MacBook", host = "192.168.1.20", port = 8810),
            )
        }
        CompositionLocalProvider(LocalCompanion provides settingsScene.environment) {
            SettingsScreen(
                onBack = {},
                onOpenRoutines = {},
                onOpenConnectedApps = {},
                accounts = AccountInfoSource {
                    AccountInfo(Profile(name = "Dina Pratama", email = "dina@example.com"), connectedApps = 3)
                },
            )
        }
    }

    @Composable
    private fun Pair() {
        PairFrame(
            phase = PairPhase.FOUND,
            statusText = "Code found",
            statusIsError = false,
            torch = PairDesign.torch(hasFlashUnit = true, on = false),
            onTorch = {},
            onBack = {},
            backEnabled = true,
            viewfinder = null,
            card = {
                PairFoundCard(
                    name = "Dina's MacBook",
                    detail = "Same Wi-Fi · found just now",
                    connecting = false,
                    onConnect = {},
                    onEnterCode = {},
                )
            },
        )
    }

    @Composable
    private fun Connected() {
        PairFrame(
            phase = PairPhase.CONNECTED,
            statusText = "Paired with Dina's MacBook",
            statusIsError = false,
            torch = null,
            onTorch = {},
            onBack = {},
            backEnabled = true,
            viewfinder = null,
            card = { PairConnectedCard(onDone = {}, onPairAnother = {}) },
        )
    }

    private fun helloOnly(): (Int) -> kotlinx.coroutines.flow.Flow<StreamFrame> = {
        flow {
            emit(StreamFrame(Frame.Hello(cursor = "fixture:1", resumed = false), seq = 1))
            awaitCancellation()
        }
    }

    private fun json(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val OTHER_CHAT_CALL =
            """{"call":{"callId":"c-other","botId":"other-bot","threadId":"other-thread","client":"desktop","voice":"marin","startedAt":1,"status":"live"}}"""

        fun user(id: String, at: Double, text: String, parent: String? = null) =
            Message(id, Message.Role.USER, Message.Kind.TEXT, at, text = text, parentId = parent)

        fun reply(id: String, at: Double, text: String, parent: String? = null) =
            Message(id, Message.Role.BOT, Message.Kind.TEXT, at, text = text, parentId = parent)

        fun tool(id: String, at: Double, name: String, parent: String) =
            Message(id, Message.Role.BOT, Message.Kind.ACTIVITY, at, tool = ToolActivity(name = name, ok = true), parentId = parent)

        fun approval(id: String, at: Double, title: String, subtitle: String, parent: String) = Message(
            id, Message.Role.BOT, Message.Kind.OPTIONS, at, parentId = parent,
            card = OptionCard(
                title = title,
                subtitle = subtitle,
                // The card treats only "Deny", "Cancel", "Dismiss" and "Skip" as a refusal.
                options = listOf("Allow", "Deny"),
                requestId = "p",
                tool = "Email",
                requestType = "permission",
                skillRequest = null,
            ),
        )

        val TABLE_EN = listOf(
            user("m1", 1.0, "Can you check which March invoices are still open for Dina Pratama?"),
            tool("m2", 2.0, "Read invoices", "m1"),
            tool("m3", 3.0, "Check totals", "m2"),
            reply(
                "m4", 4.0,
                "Two invoices are still open for Dina Pratama.\n\n" +
                    "| Invoice | Total | Due |\n| --- | ---: | --- |\n" +
                    "| INV-1042 | 1.250.000 | 5 Apr |\n| INV-1043 | 480.000 | 12 Apr |\n\n" +
                    "The larger one is due first, so I would send that reminder today.",
                "m3",
            ),
            user("m5", 5.0, "Yes, draft the reminder.", "m4"),
            reply("m6", 6.0, "Done. The draft is in your outbox, ready to send.", "m5"),
        )

        val TABLE_EN_REPLIES = listOf(
            QuickReply("fx.remind", "Send reminder", "Send the reminder for INV-1042", "send"),
            QuickReply("fx.invoice", "Open invoice", "Show invoice INV-1042", "document"),
            QuickReply("fx.next", "What's next?", "What should we do next?", "next"),
        )

        val TABLE_ID = listOf(
            user("m1", 1.0, "Tolong cek invoice Maret atas nama Dina Pratama yang belum lunas."),
            tool("m2", 2.0, "Baca invoice", "m1"),
            tool("m3", 3.0, "Cek total", "m2"),
            reply(
                "m4", 4.0,
                "Ada dua invoice Dina Pratama yang belum lunas.\n\n" +
                    "| Invoice | Total | Jatuh tempo |\n| --- | ---: | --- |\n" +
                    "| INV-1042 | 1.250.000 | 5 Apr |\n| INV-1043 | 480.000 | 12 Apr |\n\n" +
                    "Yang lebih besar jatuh tempo lebih dulu. Saya sarankan kirim pengingat hari ini.",
                "m3",
            ),
            user("m5", 5.0, "Oke, buatkan draf pengingatnya.", "m4"),
            reply("m6", 6.0, "Sudah. Drafnya ada di kotak keluar, siap dikirim.", "m5"),
        )

        val TABLE_ID_REPLIES = listOf(
            QuickReply("fx.remind", "Kirim pengingat", "Kirim pengingat untuk INV-1042", "send"),
            QuickReply("fx.invoice", "Buka invoice", "Tampilkan invoice INV-1042", "document"),
            QuickReply("fx.next", "Lanjut apa?", "Apa langkah berikutnya?", "next"),
        )

        val APPROVAL_EN = listOf(
            user("m1", 1.0, "Send the reminder for INV-1042 to Dina."),
            tool("m2", 2.0, "Draft email", "m1"),
            reply(
                "m3", 3.0,
                "The reminder is ready: a short note with the invoice attached. Sending email needs your OK.",
                "m2",
            ),
            approval("m4", 4.0, "Approval needed", "Send the reminder email to Dina Pratama", "m3"),
        )

        val APPROVAL_ID = listOf(
            user("m1", 1.0, "Kirim pengingat INV-1042 ke Dina."),
            tool("m2", 2.0, "Tulis email", "m1"),
            reply(
                "m3", 3.0,
                "Pengingatnya sudah siap: pesan singkat dengan invoice terlampir. Mengirim email perlu persetujuan Anda.",
                "m2",
            ),
            approval("m4", 4.0, "Perlu persetujuan", "Kirim email pengingat ke Dina Pratama", "m3"),
        )
    }
}
