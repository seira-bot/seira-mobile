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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import bot.seira.companion.R
import bot.seira.companion.core.Bot
import bot.seira.companion.core.BurstCard
import bot.seira.companion.core.CallRecord
import bot.seira.companion.core.Chat
import bot.seira.companion.core.Connection
import bot.seira.companion.core.Fleet
import bot.seira.companion.core.Frame
import bot.seira.companion.core.GoalRunCard
import bot.seira.companion.core.Message
import bot.seira.companion.core.StreamFrame
import bot.seira.companion.core.ThreadRef
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class RunCardsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: MockWebServer
    private lateinit var scene: WiringScene
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val burstChildren = """{"burstId":"burst-1","phase":"running","instructions":"Summarise","items":["a","b","c"],
        "children":[
          {"seq":1,"title":"Invoice March","status":"done","threadId":"thread-child-1"},
          {"seq":2,"title":"Invoice April","status":"running","threadId":"thread-child-2"},
          {"seq":3,"title":"Invoice May","status":"queued"}]}"""

    @Before
    fun start() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/api/instances" -> json("""{"instances":[]}""")
                    path == "/api/live/call" -> json("""{"call":null}""")
                    path.startsWith("/api/threads/") -> json("""{"messages":[],"hasMore":false}""")
                    path == "/api/bursts/burst-1" -> json(burstChildren)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun stop() {
        if (::scene.isInitialized) scene.session.disconnect()
        server.shutdown()
    }

    private fun show(
        message: Message,
        history: List<Message> = emptyList(),
        openThread: ((ThreadRef) -> Unit)? = null,
    ) {
        val bot: Bot = bot().copy(messages = history + message)
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
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        MessageRow(Chat.BotChat(bot), message, openThread = openThread)
                    }
                }
            }
        }
        compose.runOnIdle { scene.session.connect() }
        compose.waitUntil(5_000) { scene.session.state.value.bot(bot.id) != null }
        compose.waitForIdle()
    }

    private fun goalRun(status: String, turns: Int = 2) = Message(
        "g1", Message.Role.BOT, Message.Kind.GOAL_RUN, 2.0,
        goalRun = GoalRunCard(
            runId = "run-1", goal = "Ship the monthly report", status = status,
            coordinatorBotId = "bot-1", coordinatorName = "Scout", turnCount = turns, maxTurns = 10,
        ),
    )

    private fun burst(phase: String, counts: Map<String, Int> = emptyMap(), total: Int = 5) = Message(
        "b1", Message.Role.BOT, Message.Kind.BURST, 2.0,
        burst = BurstCard(
            burstId = "burst-1", botId = "bot-1", task = "Summarise every invoice", phase = phase,
            total = total, requested = total, parallel = 2, modelName = "Fast model", estimateMinutes = 4,
            counts = counts, sample = listOf("Invoice March", "Invoice April"),
        ),
    )

    private fun callMessage() = Message(
        "c1", Message.Role.BOT, Message.Kind.CALL, 9.0,
        call = CallRecord(
            callId = "call-1", botId = "bot-1", startedAt = 1_000.0, endedAt = 103_000.0,
            seconds = 102.0, endReason = "hung-up", title = "Plan the launch",
        ),
    )

    private fun spoken(id: String, text: String, at: Double) =
        Message(id, Message.Role.USER, Message.Kind.TEXT, at, text = text, via = "call", callId = "call-1")

    // The task list comes over a real socket, so the main looper needs a turn after each poll.
    private fun awaitText(text: String) {
        compose.waitUntil(TASKS_LOAD_MS) {
            Thread.sleep(25)
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private fun absent(text: String) =
        assertEquals(0, compose.onAllNodes(hasText(text)).fetchSemanticsNodes().size, text)

    @Test
    fun aWorkingGoalRunShowsItsSpinnerLabelGoalAndTurnLine() {
        show(goalRun("working"))
        compose.onNodeWithText("Ship the monthly report").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_goal_status_working)).assertIsDisplayed()
        compose.onNodeWithText("Scout · Turn 3 of 10").assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_goal_stop)).assertIsDisplayed()
    }

    @Test
    fun aFinishedGoalRunShowsItsLabelAndTurnCountWithoutSpinnerOrStop() {
        show(goalRun("completed", turns = 4))
        compose.onNodeWithText(text(R.string.chat_goal_status_completed)).assertIsDisplayed()
        compose.onNodeWithText("Scout · 4 turns").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().size)
        absent(text(R.string.chat_goal_stop))
    }

    @Test
    fun aGoalRunReadsAsOneDescribedCard() {
        show(goalRun("blocked"))
        compose.onNode(hasContentDescription("Goal run: Blocked. Ship the monthly report")).assertIsDisplayed()
    }

    @Test
    fun aPreviewBurstShowsThePlanTheSampleNamesAndRunEditCancel() {
        show(burst("preview"))
        compose.onNodeWithText("Burst: Summarise every invoice").assertIsDisplayed()
        compose.onNodeWithText("5 tasks · Fast model · 2 at once · about 4 min").assertIsDisplayed()
        compose.onNodeWithText("Invoice March · Invoice April · +3 more").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_burst_run)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_burst_edit)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_burst_cancel)).assertIsDisplayed()
        absent(text(R.string.chat_burst_show_tasks))
    }

    @Test
    fun aRunningBurstShowsProgressCountsAndCancelAll() {
        show(burst("running", mapOf("done" to 2, "running" to 2, "queued" to 1)))
        compose.onNodeWithText("2 of 5 done").assertIsDisplayed()
        compose.onNodeWithText("2 running · 1 waiting").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_burst_cancel_all)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_burst_show_tasks)).assertIsDisplayed()
        absent(text(R.string.chat_burst_run))
    }

    @Test
    fun aMergingBurstShowsItsPhaseLineAndCancelAll() {
        show(burst("merging", mapOf("done" to 5)))
        compose.onNodeWithText(text(R.string.chat_burst_phase_merging)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_burst_cancel_all)).assertIsDisplayed()
    }

    @Test
    fun showTasksListsOneRowPerTaskAndARowWithAConversationOpensIt() {
        val opened = mutableListOf<ThreadRef>()
        show(burst("running", mapOf("done" to 1, "running" to 1, "queued" to 1), total = 3), openThread = { opened += it })
        compose.onNodeWithText(text(R.string.chat_burst_show_tasks)).performClick()
        awaitText("Invoice April")
        compose.onNodeWithText(text(R.string.chat_burst_tasks_title)).assertIsDisplayed()
        compose.onNodeWithText("Invoice March").assertIsDisplayed()
        compose.onNodeWithText("Invoice April").assertIsDisplayed()
        compose.onNodeWithText("Invoice May").assertIsDisplayed()

        compose.onNode(hasContentDescription(text(R.string.chat_burst_row_open, "Invoice April"))).performClick()
        compose.waitForIdle()
        assertEquals(listOf(ThreadRef("bot-1", "thread-child-2", "Invoice April")), opened)
        assertEquals(
            0,
            compose.onAllNodes(hasContentDescription(text(R.string.chat_burst_row_open, "Invoice May"))).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun aCallShowsOneRowWithItsTitleAndDurationAsMinutesAndSeconds() {
        show(callMessage())
        compose.onNodeWithText("Plan the launch").assertIsDisplayed()
        compose.onNodeWithText("· 1:42").assertIsDisplayed()
        compose.onNode(
            hasContentDescription(text(R.string.call_record_open, "Plan the launch", "1:42")),
        ).assertIsDisplayed()
    }

    @Test
    fun aCallWithoutATitleIsNamedAfterTheBot() {
        val untitled = callMessage().let { it.copy(call = it.call?.copy(title = null)) }
        show(untitled)
        compose.onNodeWithText(text(R.string.call_record_title, "Scout")).assertIsDisplayed()
    }

    @Test
    fun tappingACallOpensASheetWithEveryLineSpokenOnIt() {
        val lines = listOf(
            spoken("s1", "Let us plan the launch", 3.0),
            spoken("s2", "Start with the pricing page", 4.0),
            spoken("s3", "Then the announcement email", 5.0),
            spoken("s4", "Last, the support macros", 6.0),
        )
        show(callMessage(), history = lines)
        compose.onNodeWithText("Let us plan the launch").assertIsDisplayed()
        absent("Last, the support macros")

        compose.onNode(
            hasContentDescription(text(R.string.call_record_open, "Plan the launch", "1:42")),
        ).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Plan the launch · 1:42").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.call_record_said)).assertIsDisplayed()
        compose.onNodeWithText("Last, the support macros").assertIsDisplayed()
        assertEquals(2, compose.onAllNodes(hasText("Start with the pricing page")).fetchSemanticsNodes().size)
    }

    @Test
    fun aCallWithNoLoadedLinesSaysSoInItsSheet() {
        show(callMessage())
        compose.onNode(
            hasContentDescription(text(R.string.call_record_open, "Plan the launch", "1:42")),
        ).performClick()
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.call_record_nothing)).assertIsDisplayed()
    }

    private companion object {
        const val TASKS_LOAD_MS = 20_000L
    }

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
