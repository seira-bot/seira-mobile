package com.openmausbot.companion.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import com.openmausbot.companion.avatar.AvatarImageStore
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.NotificationFrame
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.notifications.NotificationFixture.REQUEST_ID
import com.openmausbot.companion.notifications.NotificationFixture.THREAD_ID
import com.openmausbot.companion.notifications.NotificationFixture.BOT_ID
import com.openmausbot.companion.notifications.NotificationFixture.liveSession
import com.openmausbot.companion.shortcuts.BotShortcuts
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Approve and Review buttons of an approval notification, from posting to
 * the answer on the wire. The server is a real socket, so "answers allow and
 * remembers nothing" is a count of requests, not a mock expectation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApprovalNotificationTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Application
    private lateinit var manager: NotificationManager
    private lateinit var poster: LocalNotificationPoster

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        context = RuntimeEnvironment.getApplication() as Application
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        poster = LocalNotificationPoster(context)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a plain permission card gets Approve and Review`() = runTest {
        bind(fleetWith(NotificationFixture.permissionCard()))

        poster.deliver(approvalFrame(), sequence = 1)

        val posted = posted(approvalFrame(), 1)
        assertEquals(listOf("Approve", "Review"), posted.actions.map { it.title.toString() })
    }

    @Test
    fun `Approve needs an unlocked phone and an immutable intent`() = runTest {
        bind(fleetWith(NotificationFixture.permissionCard()))

        poster.deliver(approvalFrame(), sequence = 1)

        val approve = posted(approvalFrame(), 1).actions.first()
        assertTrue(approve.isAuthenticationRequired)
        val intent = shadowOf(approve.actionIntent)
        assertTrue(intent.isBroadcastIntent)
        assertEquals(NotificationActionReceiver::class.java.name, intent.savedIntent.component?.className)
        assertNotEquals(0, intent.flags and PendingIntent.FLAG_IMMUTABLE)
    }

    @Test
    fun `each notification gets its own Approve request code`() = runTest {
        bind(fleetWith(NotificationFixture.permissionCard()))

        poster.deliver(approvalFrame(), sequence = 1)
        poster.deliver(approvalFrame(), sequence = 2)

        val first = shadowOf(posted(approvalFrame(), 1).actions.first().actionIntent).requestCode
        val second = shadowOf(posted(approvalFrame(), 2).actions.first().actionIntent).requestCode
        assertNotEquals(first, second)
    }

    @Test
    fun `a skill request only gets Review`() = runTest {
        val card = NotificationFixture.permissionCard(skill = NotificationFixture.skill())
        bind(fleetWith(card))

        poster.deliver(approvalFrame(), sequence = 1)

        assertEquals(listOf("Review"), posted(approvalFrame(), 1).actions.map { it.title.toString() })
    }

    @Test
    fun `a card the session does not hold only gets Review`() = runTest {
        bind(Fleet(listOf(NotificationFixture.bot()), emptyList()))

        poster.deliver(approvalFrame(), sequence = 1)

        assertEquals(listOf("Review"), posted(approvalFrame(), 1).actions.map { it.title.toString() })
    }

    @Test
    fun `Approve answers allow once and records no grant`() = runTest {
        val session = bind(fleetWith(NotificationFixture.permissionCard(listOf("Always allow", "Allow", "Deny"))))
        poster.deliver(approvalFrame(), sequence = 1)
        server.enqueue(NotificationFixture.ok())

        val approved = NotificationActionHandler(session, poster).approve(request())

        assertTrue(approved)
        val sent = server.takeRequest()
        assertEquals("/api/threads/$THREAD_ID/respond", sent.path)
        assertEquals(
            mapOf("requestId" to REQUEST_ID, "behavior" to "allow"),
            CompanionJson.parseToJsonElement(sent.body.readUtf8()).jsonObject
                .mapValues { it.value.jsonPrimitive.content },
        )
        assertEquals(1, server.requestCount, "no always-allow grant is written")
        assertEquals("Approved", text(posted(approvalFrame(), 1)))
        assertTrue(posted(approvalFrame(), 1).flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun `Approve through the intent reaches the same answer`() = runTest {
        val session = bind(fleetWith(NotificationFixture.permissionCard()))
        poster.deliver(approvalFrame(), sequence = 1)
        server.enqueue(NotificationFixture.ok())

        NotificationActionHandler(session, poster)
            .handle(NotificationActions.approveIntent(context, request()))

        assertEquals("/api/threads/$THREAD_ID/respond", server.takeRequest().path)
    }

    @Test
    fun `a failed answer replaces the text and keeps Review`() = runTest {
        val session = bind(fleetWith(NotificationFixture.permissionCard()))
        poster.deliver(approvalFrame(), sequence = 1)
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        val approved = NotificationActionHandler(session, poster).approve(request())

        assertFalse(approved)
        val replaced = posted(approvalFrame(), 1)
        assertEquals("Couldn't approve. Open the chat to review.", text(replaced))
        assertEquals(listOf("Review"), replaced.actions.map { it.title.toString() })
    }

    @Test
    fun `an answered card is not answered again`() = runTest {
        val answered = NotificationFixture.permissionCard().copy(answered = "Allow")
        val session = bind(fleetWith(answered))

        val approved = NotificationActionHandler(session, poster).approve(request())

        assertFalse(approved)
        assertEquals(0, server.requestCount)
        assertEquals("Couldn't approve. Open the chat to review.", text(posted(approvalFrame(), 1)))
    }

    @Test
    fun `a skill request is never approved from the shade`() = runTest {
        val card = NotificationFixture.permissionCard(skill = NotificationFixture.skill())
        val session = bind(fleetWith(card))

        val approved = NotificationActionHandler(session, poster).approve(request())

        assertFalse(approved)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a card without a plain allow choice is never approved from the shade`() = runTest {
        val card = NotificationFixture.permissionCard(listOf("Always allow", "Deny"))
        val session = bind(fleetWith(card))

        val approved = NotificationActionHandler(session, poster).approve(request())

        assertFalse(approved)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a different request id is never approved`() = runTest {
        val session = bind(fleetWith(NotificationFixture.permissionCard(requestId = "other")))

        val approved = NotificationActionHandler(session, poster).approve(request())

        assertFalse(approved)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `two pending cards make the approval ambiguous`() = runTest {
        val first = NotificationFixture.cardMessage(NotificationFixture.permissionCard(), "m1")
        val second = NotificationFixture.cardMessage(NotificationFixture.permissionCard(requestId = "r2"), "m2")
        val state = stateWith(listOf(first, second))

        assertNull(NotificationApproval.find(state, BOT_ID, THREAD_ID))
    }

    @Test
    fun `a question card never qualifies`() = runTest {
        val question = NotificationFixture.permissionCard().copy(tool = null, requestType = "question")
        val state = stateWith(listOf(NotificationFixture.cardMessage(question)))

        assertNull(NotificationApproval.find(state, BOT_ID, THREAD_ID))
    }

    @Test
    fun `a plain permission card qualifies with its Allow choice`() = runTest {
        val state = stateWith(listOf(NotificationFixture.cardMessage(NotificationFixture.permissionCard())))

        val found = assertNotNull(NotificationApproval.find(state, BOT_ID, THREAD_ID, REQUEST_ID))
        assertEquals("Allow", found.choice)
    }

    private suspend fun TestScope.stateWith(messages: List<Message>): CompanionState =
        liveSession(server, Fleet(listOf(NotificationFixture.bot(messages = messages)), emptyList())).state.value

    private fun fleetWith(card: com.openmausbot.companion.core.OptionCard) = Fleet(
        listOf(NotificationFixture.bot(messages = listOf(NotificationFixture.cardMessage(card)))),
        emptyList(),
    )

    private suspend fun TestScope.bind(fleet: Fleet): Session {
        val session = liveSession(server, fleet)
        poster.bind(session, AvatarImageStore(fetch = { null }), BotShortcuts(context), backgroundScope)
        runCurrent()
        return session
    }

    private fun approvalFrame() = NotificationFrame(
        kind = "approval",
        botId = BOT_ID,
        botName = "Scout",
        threadId = THREAD_ID,
        title = "Approval needed",
        body = "git push",
    )

    private fun request() = ApproveRequest(
        tag = NotificationMapping.dedupeId(approvalFrame(), 1),
        botId = BOT_ID,
        threadId = THREAD_ID,
        requestId = REQUEST_ID,
        title = "Approval needed",
    )

    private fun posted(frame: NotificationFrame, sequence: Int): Notification =
        assertNotNull(shadowOf(manager).getNotification(NotificationMapping.dedupeId(frame, sequence), 0))

    private fun text(notification: Notification): String =
        notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
}
