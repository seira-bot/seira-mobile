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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.openmausbot.companion.core.SeiraCast
import com.openmausbot.companion.core.SeiraCastMember
import com.openmausbot.companion.core.StreamFrame
import com.openmausbot.companion.ui.home.HomeShell
import com.openmausbot.companion.storage.ChatPreferences
import com.openmausbot.companion.ui.theme.Seira
import com.openmausbot.companion.ui.theme.ThemeMode
import java.io.File
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
 * Every screen is drawn with calls turned off, as in the release build: no Talk pill, call button or composer talk shows.
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

    @Before
    fun start() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/config" -> json("""{"profile":{"name":"Dina Pratama","email":"dina@example.com"}}""")
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

    // Avatar sheet

    @Test fun lightLookSeira() = look("store-look-seira", ThemeMode.LIGHT, AvatarTab.SEIRA)

    @Test fun darkLookSeira() = look("store-look-seira", ThemeMode.DARK, AvatarTab.SEIRA)

    @Test fun lightLookGenerate() = look("store-look-generate", ThemeMode.LIGHT, AvatarTab.GENERATE)

    @Test fun lightLookExtras() = look("store-look-extras", ThemeMode.LIGHT, AvatarTab.SEIRA, ChangeLookCategory.EXTRAS) {
        it.toggleExtra("glasses")
        it.toggleExtra("ribbon")
    }

    @Test fun lightLookColour() = look("store-look-colour", ThemeMode.LIGHT, AvatarTab.SEIRA, ChangeLookCategory.COLOUR)

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

    private fun cast(): List<SeiraCastMember> = SeiraCast.decode(
        File("src/main/assets/seira-character/cast.json").takeIf(File::isFile)?.readText()
            ?: File("app/src/main/assets/seira-character/cast.json").readText(),
    )

    /**
     * Gives each bot a different cast look. A bot first gets the unused member with its colour.
     * A bot with no match gets the next unused member from [FALLBACK_ORDER] and that member's colour.
     */
    private fun withCastLooks(bots: List<Bot>): List<Bot> {
        val members = cast()
        val used = mutableSetOf<String>()
        val picked = arrayOfNulls<SeiraCastMember>(bots.size)
        bots.forEachIndexed { i, bot ->
            val match = members.firstOrNull { it.id != "seira" && it.id !in used && it.color == bot.color }
            if (match != null) {
                used += match.id
                picked[i] = match
            }
        }
        bots.forEachIndexed { i, _ ->
            if (picked[i] == null) {
                val id = FALLBACK_ORDER.first { it !in used }
                used += id
                picked[i] = members.first { it.id == id }
            }
        }
        return bots.mapIndexed { i, bot ->
            val member = picked[i]!!
            bot.copy(
                mascotBody = member.mascotBody,
                mascotAccessories = member.mascotAccessories,
                color = member.color,
            ).also { println("CAST-MAP ${bot.name} -> ${member.id}") }
        }
    }

    private fun look(
        name: String,
        mode: ThemeMode,
        tab: AvatarTab,
        category: ChangeLookCategory = ChangeLookCategory.HAIR,
        prepare: (ChangeLookState) -> Unit = {},
    ) {
        val members = cast()
        val state = ChangeLookState(members.first { it.color == "pink" }.look, members)
        prepare(state)
        state.category = category
        val panels = AvatarPanels(
            prompt = "A friendly fox with round glasses",
            canGenerate = true,
        )
        val shot = compose.captureDesign(name, mode) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ChangeLookSheetBody(
                    botName = "Pixel", state = state, connected = true, tab = tab, panels = panels,
                    onTab = {}, onClose = {}, onSave = {},
                )
            }
        }
        assertTrue(shot.length() > 10_000, "the sheet picture is blank")
    }

    private fun shot(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        assertTrue(compose.captureDesign(name, mode, content = content).length() > 0)
    }

    private fun home(name: String, mode: ThemeMode) {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(ChatPreferences.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        scene = WiringScene(
            connection = Connection(id = "store-home", name = "Dina's MacBook", host = "127.0.0.1", port = server.port),
            fleet = RosterFixture.fleet().let { it.copy(bots = withCastLooks(it.bots)) },
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
                compose.waitUntil(10_000) {
                    compose.onAllNodesWithTag("roster-list").fetchSemanticsNodes().isNotEmpty()
                }
            },
        ) {
            val navigator = remember { CompanionNavigator() }
            val state by scene.session.state.collectAsState()
            CompositionLocalProvider(
                LocalCompanion provides scene.environment,
                LocalHomeHour provides { 9 },
            ) {
                if (state.bots.isNotEmpty()) HomeShell(navigator, callsEnabled = false)
            }
        }
        assertTrue(file.length() > 0)
    }

    private fun chat(name: String, mode: ThemeMode, messages: List<Message>, replies: List<QuickReply>) {
        val scout: Bot = withCastLooks(listOf(bot().copy(color = "cyan", messages = messages))).single()
        scene = WiringScene(
            connection = Connection(id = "store-chat", name = "Dina's MacBook", host = "127.0.0.1", port = server.port),
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
                                callsEnabled = false,
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
                callsEnabled = false,
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
        val FALLBACK_ORDER = listOf(
            "pippa", "felix", "rocco", "theo", "kiri", "milo", "rhea", "maren", "mira", "lumi", "iris", "bix",
            // Spill-over when the fixture has more bots than the order above covers.
            "corvin", "silas", "flint", "vera", "leo", "orin",
        )

        fun user(id: String, at: Double, text: String, parent: String? = null) =
            Message(id, Message.Role.USER, Message.Kind.TEXT, at, text = text, parentId = parent)

        fun reply(id: String, at: Double, text: String, parent: String? = null) =
            Message(id, Message.Role.BOT, Message.Kind.TEXT, at, text = text, parentId = parent)

        fun approval(id: String, at: Double, title: String, subtitle: String, parent: String) = Message(
            id, Message.Role.BOT, Message.Kind.OPTIONS, at, parentId = parent,
            card = OptionCard(
                title = title,
                subtitle = subtitle,
                // The card treats only "Deny", "Cancel", "Dismiss" and "Skip" as a refusal.
                options = listOf("Allow", "Deny"),
                requestId = "p",
                requestType = "permission",
                skillRequest = null,
            ),
        )

        val TABLE_EN = listOf(
            user("m1", 1.0, "Can you check which March invoices are still open for Dina Pratama?"),
            reply(
                "m4", 4.0,
                "Two invoices are still open for Dina Pratama.\n\n" +
                    "| Invoice | Total | Due |\n| --- | ---: | --- |\n" +
                    "| INV-1042 | 1.250.000 | 5 Apr |\n| INV-1043 | 480.000 | 12 Apr |\n\n" +
                    "The larger one is due first, so I would send that reminder today.",
                "m1",
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
            reply(
                "m4", 4.0,
                "Ada dua invoice Dina Pratama yang belum lunas.\n\n" +
                    "| Invoice | Total | Jatuh tempo |\n| --- | ---: | --- |\n" +
                    "| INV-1042 | 1.250.000 | 5 Apr |\n| INV-1043 | 480.000 | 12 Apr |\n\n" +
                    "Yang lebih besar jatuh tempo lebih dulu. Saya sarankan kirim pengingat hari ini.",
                "m1",
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
            reply(
                "m3", 3.0,
                "The reminder is ready: a short note with the invoice attached. Sending email needs your OK.",
                "m1",
            ),
            approval("m4", 4.0, "Approval needed", "Send the reminder email to Dina Pratama", "m3"),
        )

        val APPROVAL_ID = listOf(
            user("m1", 1.0, "Kirim pengingat INV-1042 ke Dina."),
            reply(
                "m3", 3.0,
                "Pengingatnya sudah siap: pesan singkat dengan invoice terlampir. Mengirim email perlu persetujuanmu.",
                "m1",
            ),
            approval("m4", 4.0, "Perlu persetujuan", "Kirim email pengingat ke Dina Pratama", "m3"),
        )
    }
}
