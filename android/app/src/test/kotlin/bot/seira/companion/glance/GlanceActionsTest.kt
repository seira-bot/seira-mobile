package bot.seira.companion.glance

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import bot.seira.companion.core.Fleet
import bot.seira.companion.core.Session
import bot.seira.companion.core.GlanceBudget
import bot.seira.companion.notifications.NotificationFixture
import bot.seira.companion.notifications.NotificationFixture.BOT_ID
import bot.seira.companion.notifications.NotificationFixture.REQUEST_ID
import bot.seira.companion.notifications.NotificationFixture.THREAD_ID
import bot.seira.companion.notifications.NotificationFixture.answerDispatcher
import bot.seira.companion.notifications.NotificationFixture.liveSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlanceActionsTest {
    private lateinit var server: MockWebServer
    private lateinit var dispatcher: NotificationFixture.AnswerDispatcher
    private lateinit var context: Application
    private var budget = GlanceBudget(lineChars = 30, lines = 6)

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        dispatcher = server.answerDispatcher()
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Allow once answers allow and remembers nothing`() = runTest {
        server.enqueue(NotificationFixture.ok())
        val handler = handler(fleet(NotificationFixture.permissionCard()))

        assertTrue(handler.handle(request(GlanceActions.Choice.ALLOW)))

        val body = dispatcher.answers.poll(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8()
        assertEquals("allow", behaviorOf(body))
        assertFalse(body.contains("always", ignoreCase = true))
    }

    @Test
    fun `Deny answers deny`() = runTest {
        server.enqueue(NotificationFixture.ok())
        val handler = handler(fleet(NotificationFixture.permissionCard()))

        assertTrue(handler.handle(request(GlanceActions.Choice.DENY)))

        assertEquals("deny", behaviorOf(dispatcher.answers.poll(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8()))
    }

    @Test
    fun `an allow for a request that is not current sends nothing`() = runTest {
        val handler = handler(fleet(NotificationFixture.permissionCard(requestId = "other")))

        assertFalse(handler.handle(request(GlanceActions.Choice.ALLOW)))
        assertFalse(handler.handle(request(GlanceActions.Choice.DENY)))
        assertEquals(0, dispatcher.answers.size)
    }

    @Test
    fun `an allow for a detail that is empty or does not fit sends nothing`() = runTest {
        val empty = NotificationFixture.permissionCard().copy(subtitle = "")
        assertFalse(handler(fleet(empty)).handle(request(GlanceActions.Choice.ALLOW)))

        budget = GlanceBudget(lineChars = 3, lines = 1)
        assertFalse(handler(fleet(NotificationFixture.permissionCard())).handle(request(GlanceActions.Choice.ALLOW)))
        assertEquals(0, dispatcher.answers.size)
    }

    @Test
    fun `a skill request or a question is never answered from Glance`() = runTest {
        val skill = NotificationFixture.permissionCard(skill = NotificationFixture.skill())
        assertFalse(handler(fleet(skill)).handle(request(GlanceActions.Choice.ALLOW)))
        val question = NotificationFixture.permissionCard().copy(tool = null, requestType = "question")
        assertFalse(handler(fleet(question)).handle(request(GlanceActions.Choice.DENY)))
        assertEquals(0, dispatcher.answers.size)
    }

    @Test
    fun `a failed send is marked so the notification keeps the ask and says Could not send`() = runTest {
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(500).setBody("{}"))
        val session = liveSession(server, fleet(NotificationFixture.permissionCard()))
        val notifier = notifier(session)
        runCurrent()
        assertEquals("Needs you", postedText())

        GlanceActionHandler(context, session, notifier, { budget }).handle(request(GlanceActions.Choice.ALLOW))
        runCurrent()

        assertEquals("Could not send. Open the chat to answer.", postedText())
        val titles = shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID).actions.map { it.title.toString() }
        assertEquals(listOf("Open chat"), titles)
    }

    private fun postedText(): String =
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID)
            .extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().replace("‎", "")

    private fun TestScope.notifier(session: Session): GlanceNotifier =
        GlanceNotifier(context, session.state, session.status, avatarFor = { null }, now = { testScheduler.currentTime })
            .also { it.start(backgroundScope) }

    private suspend fun TestScope.handler(fleet: Fleet): GlanceActionHandler {
        val session = liveSession(server, fleet)
        return GlanceActionHandler(context, session, notifier(session), { budget })
    }

    private fun fleet(card: bot.seira.companion.core.OptionCard) = Fleet(
        listOf(NotificationFixture.bot(messages = listOf(NotificationFixture.cardMessage(card)))),
        emptyList(),
    )

    private fun request(choice: GlanceActions.Choice) = GlanceActions.Request(choice, BOT_ID, THREAD_ID, REQUEST_ID)

    private fun behaviorOf(body: String): String =
        kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["behavior"]!!.jsonPrimitive.content
}
