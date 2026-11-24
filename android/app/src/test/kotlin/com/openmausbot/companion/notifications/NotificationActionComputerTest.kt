package com.openmausbot.companion.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.openmausbot.companion.R
import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.ConnectionRegistry
import com.openmausbot.companion.core.ConnectionRegistryRestore
import com.openmausbot.companion.core.ConnectionStore
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Frame
import com.openmausbot.companion.core.InMemoryOnboardingStore
import com.openmausbot.companion.core.NotificationFrame
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.core.StreamFrame
import com.openmausbot.companion.core.TokenStore
import com.openmausbot.companion.notifications.NotificationFixture.answerDispatcher
import com.openmausbot.companion.ui.NotificationOpen
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Approve and Reply on a banner act on the computer that posted it, not on the
 * computer on screen when the button is pressed.
 *
 * Two computers answer to the same bot, thread and request ids. Each has its own
 * server, so a message that reached the wrong one shows up in its queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationActionComputerTest {
    private lateinit var alphaServer: MockWebServer
    private lateinit var betaServer: MockWebServer
    private lateinit var alphaAnswers: NotificationFixture.AnswerDispatcher
    private lateinit var betaAnswers: NotificationFixture.AnswerDispatcher
    private lateinit var context: Application
    private lateinit var manager: NotificationManager
    private lateinit var poster: LocalNotificationPoster
    private lateinit var alpha: Connection
    private lateinit var beta: Connection

    @BeforeTest
    fun setUp() {
        alphaServer = MockWebServer().apply { start() }
        betaServer = MockWebServer().apply { start() }
        alphaAnswers = alphaServer.answerDispatcher()
        betaAnswers = betaServer.answerDispatcher()
        alpha = requireNotNull(Connection.parse(alphaServer.url("/").toString())).copy(id = "alpha", name = "Alpha")
        beta = requireNotNull(Connection.parse(betaServer.url("/").toString())).copy(id = "beta", name = "Beta")
        context = RuntimeEnvironment.getApplication() as Application
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        poster = LocalNotificationPoster(context)
    }

    @AfterTest
    fun tearDown() {
        alphaServer.shutdown()
        betaServer.shutdown()
    }

    @Test
    fun `banners from two computers with the same thread and sequence stay apart`() = runTest {
        val session = bind()
        poster.deliver(approvalFrame(), sequence = 1)
        session.activateComputer(beta.id)
        runCurrent()
        poster.deliver(approvalFrame(), sequence = 1)
        runCurrent()

        val fromAlpha = posted(alpha.id)
        val fromBeta = posted(beta.id)
        assertEquals(alpha.id, NotificationActions.approveRequest(approveIntent(fromAlpha))?.pairing)
        assertEquals(beta.id, NotificationActions.approveRequest(approveIntent(fromBeta))?.pairing)
        assertEquals(2, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `Reply on alpha's banner while beta is open reaches alpha and not beta`() = runTest {
        val session = bind()
        poster.deliver(doneFrame(), sequence = 1)
        runCurrent()
        val request = replyRequestOf(posted(alpha.id, doneFrame()))
        session.activateComputer(beta.id)
        runCurrent()
        assertEquals(beta.id, session.connection.value?.id)
        alphaServer.enqueue(NotificationFixture.ok())

        val sent = NotificationActionHandler(session, poster).reply(request, "On my way")

        assertTrue(sent)
        assertNotNull(alphaAnswers.answers.poll(5, TimeUnit.SECONDS))
        assertEquals(0, betaAnswers.answers.size)
        assertEquals(alpha.id, session.connection.value?.id)
    }

    @Test
    fun `Approve on alpha's banner while beta is open reaches alpha and not beta`() = runTest {
        val session = bind()
        poster.deliver(approvalFrame(), sequence = 1)
        runCurrent()
        val request = approveRequestOf(posted(alpha.id))
        session.activateComputer(beta.id)
        runCurrent()
        alphaServer.enqueue(NotificationFixture.ok())

        val approved = NotificationActionHandler(session, poster).approve(request)

        assertTrue(approved)
        assertNotNull(alphaAnswers.answers.poll(5, TimeUnit.SECONDS))
        assertEquals(0, betaAnswers.answers.size)
    }

    @Test
    fun `an action from a computer that was removed sends nothing and says so`() = runTest {
        val session = bind()
        val reply = ReplyRequest("openmaus.$THREAD_ID.1@gone", BOT_ID, THREAD_ID, "Scout", "gone")
        val approve = ApproveRequest("openmaus.$THREAD_ID.2@gone", BOT_ID, THREAD_ID, REQUEST_ID, "Approval needed", "gone")
        val handler = NotificationActionHandler(session, poster)

        assertFalse(handler.reply(reply, "On my way"))
        assertFalse(handler.approve(approve))

        assertEquals(0, alphaAnswers.answers.size)
        assertEquals(0, betaAnswers.answers.size)
        assertEquals(NotificationOpen.COMPUTER_GONE_MESSAGE, text(reply.tag))
        assertEquals(NotificationOpen.COMPUTER_GONE_MESSAGE, text(approve.tag))
        assertEquals(alpha.id, session.connection.value?.id)
        assertNull(session.actionError)
    }

    @Test
    fun `an action from a computer that cannot be activated sends nothing and says why`() = runTest {
        val session = bind(unreadable = setOf(beta.id))
        val reply = ReplyRequest("openmaus.$THREAD_ID.1@${beta.id}", BOT_ID, THREAD_ID, "Scout", beta.id)

        assertFalse(NotificationActionHandler(session, poster).reply(reply, "On my way"))

        assertEquals(0, alphaAnswers.answers.size)
        assertEquals(0, betaAnswers.answers.size)
        assertTrue(text(reply.tag).isNotBlank())
        assertEquals(alpha.id, session.connection.value?.id)
    }

    @Test
    fun `an action intent without a computer is not an action`() {
        val intent = NotificationActions.replyIntent(
            context,
            ReplyRequest("tag", BOT_ID, THREAD_ID, "Scout", "alpha"),
        ).apply { removeExtra(LocalNotificationPoster.EXTRA_PAIRING) }

        assertNull(NotificationActions.replyRequest(intent))
    }

    @Test
    fun `Approve on a banner that names no computer sends nothing and says it is out of date`() = runTest {
        val session = bind()
        val request = ApproveRequest("openmaus.$THREAD_ID.2@old", BOT_ID, THREAD_ID, REQUEST_ID, "Approval needed", "alpha")
        val intent = NotificationActions.approveIntent(context, request).apply { removeExtra(LocalNotificationPoster.EXTRA_PAIRING) }

        NotificationActionHandler(session, poster).handle(intent)

        assertEquals(0, alphaAnswers.answers.size)
        assertEquals(0, betaAnswers.answers.size)
        assertEquals(context.getString(R.string.notification_action_unavailable), text(request.tag))
        assertEquals("Approval needed", title(request.tag))
        assertEquals(alpha.id, session.connection.value?.id)
    }

    @Test
    fun `Reply on a banner that names no computer sends nothing and says it is out of date`() = runTest {
        val session = bind()
        val request = ReplyRequest("openmaus.$THREAD_ID.1@old", BOT_ID, THREAD_ID, "Scout", "alpha")
        val intent = NotificationActions.replyIntent(context, request).apply { removeExtra(LocalNotificationPoster.EXTRA_PAIRING) }

        NotificationActionHandler(session, poster).handle(intent)

        assertEquals(0, alphaAnswers.answers.size)
        assertEquals(0, betaAnswers.answers.size)
        assertEquals(context.getString(R.string.notification_action_unavailable), text(request.tag))
        assertEquals("Scout", title(request.tag))
    }

    @Test
    fun `an action with no computer and no tag still says it is out of date`() = runTest {
        val session = bind()
        val intent = NotificationActions.approveIntent(
            context,
            ApproveRequest("tag", BOT_ID, THREAD_ID, REQUEST_ID, "", "alpha"),
        ).apply {
            removeExtra(LocalNotificationPoster.EXTRA_PAIRING)
            removeExtra("openmaus.notificationTag")
        }

        NotificationActionHandler(session, poster).handle(intent)

        assertEquals(0, alphaAnswers.answers.size)
        assertEquals(context.getString(R.string.notification_action_unavailable), text(LocalNotificationPoster.UNAVAILABLE_TAG))
    }

    @Test
    fun `a tapped action from a removed computer says so through the receiver path`() = runTest {
        val session = bind()
        val request = ApproveRequest("openmaus.$THREAD_ID.2@gone", BOT_ID, THREAD_ID, REQUEST_ID, "Approval needed", "gone")

        NotificationActionHandler(session, poster).handle(NotificationActions.approveIntent(context, request))

        assertEquals(0, alphaAnswers.answers.size)
        assertEquals(0, betaAnswers.answers.size)
        assertEquals(NotificationOpen.COMPUTER_GONE_MESSAGE, text(request.tag))
    }

    private fun doneFrame() = NotificationFrame(
        kind = "done",
        botId = BOT_ID,
        botName = "Scout",
        threadId = THREAD_ID,
        title = "Scout",
        body = "All done.",
    )

    private fun approvalFrame() = NotificationFrame(
        kind = "approval",
        botId = BOT_ID,
        botName = "Scout",
        threadId = THREAD_ID,
        title = "Approval needed",
        body = "Run a command",
        requestId = REQUEST_ID,
    )

    private fun posted(pairing: String, frame: NotificationFrame = approvalFrame()): Notification =
        assertNotNull(shadowOf(manager).getNotification(NotificationMapping.dedupeId(frame, 1, pairing), 0))

    private fun text(tag: String): String =
        assertNotNull(shadowOf(manager).getNotification(tag, 0)).extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun title(tag: String): String =
        assertNotNull(shadowOf(manager).getNotification(tag, 0)).extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun approveIntent(notification: Notification) =
        shadowOf(notification.actions.first { it.title.toString() == "Approve" }.actionIntent).savedIntent

    private fun approveRequestOf(notification: Notification): ApproveRequest =
        assertNotNull(NotificationActions.approveRequest(approveIntent(notification)))

    private fun replyRequestOf(notification: Notification): ReplyRequest {
        val reply = notification.actions.first { it.title.toString() == "Reply" }
        return assertNotNull(NotificationActions.replyRequest(shadowOf(reply.actionIntent).savedIntent))
    }

    private class Registry(initial: ConnectionRegistry) : ConnectionStore {
        var registry = initial
        override suspend fun load(): Connection? = registry.activeConnection
        override suspend fun save(connection: Connection) { registry = registry.upsert(connection) }
        override suspend fun clear() { registry = ConnectionRegistry() }
        override suspend fun loadRegistry() = ConnectionRegistryRestore(registry, migratedLegacyConnection = false)
        override suspend fun saveRegistry(registry: ConnectionRegistry) { this.registry = registry.normalized() }
    }

    private suspend fun TestScope.bind(unreadable: Set<String> = emptySet()): Session {
        val fleet = Fleet(
            listOf(NotificationFixture.bot(messages = listOf(NotificationFixture.cardMessage(NotificationFixture.permissionCard())))),
            emptyList(),
        )
        val session = Session(
            scope = backgroundScope,
            connectionStore = Registry(ConnectionRegistry(listOf(alpha, beta), alpha.id)),
            tokenStore = object : TokenStore {
                override suspend fun save(connectionId: String, token: String) = Unit
                override suspend fun read(connectionId: String): TokenStore.ReadResult =
                    if (connectionId in unreadable) TokenStore.ReadResult.Missing else TokenStore.ReadResult.Found("token-$connectionId")
                override suspend fun remove(connectionId: String) = Unit
            },
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Pixel" },
            eventsFn = { _, _, _ ->
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "c:1", resumed = false), seq = 1))
                    awaitCancellation()
                }
            },
            hydrateFn = { _, _ -> fleet },
            metadataFn = { throw IllegalStateException("not under test") },
        )
        session.awaitRestored()
        session.connect()
        runCurrent()
        poster.bind(session, com.openmausbot.companion.avatar.AvatarImageStore(fetch = { null }), com.openmausbot.companion.shortcuts.BotShortcuts(context), backgroundScope)
        return session
    }

    private companion object {
        const val BOT_ID = NotificationFixture.BOT_ID
        const val THREAD_ID = NotificationFixture.THREAD_ID
        const val REQUEST_ID = NotificationFixture.REQUEST_ID
    }
}
