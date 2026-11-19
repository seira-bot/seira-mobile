package com.openmausbot.companion.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutManagerCompat
import com.openmausbot.companion.OpenMausApp
import com.openmausbot.companion.avatar.AvatarImageStore
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.NotificationFrame
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.notifications.NotificationFixture.BOT_ID
import com.openmausbot.companion.notifications.NotificationFixture.THREAD_ID
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

/** Bot message notifications: the conversation look, the shortcut, and the Reply button. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationNotificationTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Application
    private lateinit var manager: NotificationManager
    private lateinit var poster: LocalNotificationPoster
    private lateinit var shortcuts: BotShortcuts

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        context = RuntimeEnvironment.getApplication() as Application
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        shortcuts = BotShortcuts(context)
        shortcuts.removeAll()
        poster = LocalNotificationPoster(context)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a bot message reads as a conversation with the bot as sender`() = runTest {
        val session = bind()

        poster.deliver(frame(), sequence = 1)
        runCurrent()

        val style = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(posted()))
        assertEquals("You", style.user.name)
        val message = style.messages.single()
        assertEquals("All done.", message.text.toString())
        val sender = assertNotNull(message.person)
        assertEquals("Scout", sender.name)
        assertTrue(sender.isBot)
        assertNotNull(sender.icon, "the bot has a face even without a server image")
        assertEquals(shortcutId(session), sender.key)
    }

    @Test
    fun `the notification joins the conversation section through its shortcut`() = runTest {
        val session = bind()

        poster.deliver(frame(), sequence = 1)
        runCurrent()

        val notification = posted()
        assertEquals(shortcutId(session), notification.shortcutId)
        assertEquals(shortcutId(session), notification.locusId?.id)
        assertEquals(Notification.CATEGORY_MESSAGE, notification.category)
        val published = ShortcutManagerCompat.getDynamicShortcuts(context).single { it.id == shortcutId(session) }
        assertEquals("Scout", published.shortLabel.toString())
        assertTrue(android.content.pm.ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION in published.categories.orEmpty())
        assertEquals(
            NotificationIntents.ACTION_OPEN,
            published.intent.action,
        )
        assertEquals(BOT_ID, published.intent.getStringExtra(LocalNotificationPoster.EXTRA_BOT_ID))
        assertEquals(THREAD_ID, published.intent.getStringExtra(LocalNotificationPoster.EXTRA_THREAD_ID))
    }

    @Test
    fun `Reply takes typed text, generated replies and a mutable intent`() = runTest {
        bind()

        poster.deliver(frame(), sequence = 1)
        runCurrent()

        val reply = posted().actions.single()
        assertEquals("Reply", reply.title.toString())
        assertEquals(Notification.Action.SEMANTIC_ACTION_REPLY, reply.semanticAction)
        assertTrue(reply.allowGeneratedReplies)
        assertEquals(1, reply.remoteInputs.size)
        assertNotEquals(0, shadowOf(reply.actionIntent).flags and PendingIntent.FLAG_MUTABLE)
        assertEquals(
            NotificationActionReceiver::class.java.name,
            shadowOf(reply.actionIntent).savedIntent.component?.className,
        )
    }

    @Test
    fun `a reply is sent to the bot and joins the notification`() = runTest {
        val session = bind()
        poster.deliver(frame(), sequence = 1)
        runCurrent()
        server.enqueue(NotificationFixture.ok())

        val sent = NotificationActionHandler(session, poster).reply(request(), "On my way")

        assertTrue(sent)
        val wire = server.takeRequest()
        assertEquals("/api/bots/$BOT_ID/messages", wire.path)
        val body = CompanionJson.parseToJsonElement(wire.body.readUtf8()).jsonObject
        assertEquals("On my way", body.getValue("text").jsonPrimitive.content)
        assertEquals(THREAD_ID, body.getValue("threadId").jsonPrimitive.content)
        val style = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(posted()))
        assertEquals(listOf("All done.", "On my way"), style.messages.map { it.text.toString() })
        assertNull(style.messages.last().person, "the reply is the user's own line")
        assertEquals(1, posted().actions.size, "Reply stays available")
    }

    @Test
    fun `a reply typed into the notification reaches the same send`() = runTest {
        val session = bind()
        poster.deliver(frame(), sequence = 1)
        runCurrent()
        server.enqueue(NotificationFixture.ok())
        val intent = NotificationActions.replyIntent(context, request())
        val input = RemoteInput.Builder(NotificationActions.KEY_REPLY).build()
        RemoteInput.addResultsToIntent(
            arrayOf(input),
            intent,
            Bundle().apply { putCharSequence(NotificationActions.KEY_REPLY, "  Thanks  ") },
        )

        NotificationActionHandler(session, poster).handle(intent)

        val body = CompanionJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("Thanks", body.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `a failed reply says so and offers the chat`() = runTest {
        val session = bind()
        poster.deliver(frame(), sequence = 1)
        runCurrent()
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        val sent = NotificationActionHandler(session, poster).reply(request(), "On my way")

        assertFalse(sent)
        val replaced = posted()
        assertEquals(
            "Couldn't send. Open the chat to try again.",
            replaced.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertNotNull(replaced.contentIntent)
    }

    @Test
    fun `the receiver reports an empty reply as a failure`() {
        val app = context as OpenMausApp
        val intent = NotificationActions.replyIntent(context, request())

        NotificationActionReceiver().onReceive(app, intent)

        val replaced = assertNotNull(shadowOf(manager).getNotification(request().tag, 0))
        assertEquals(
            "Couldn't send. Open the chat to try again.",
            replaced.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    private suspend fun TestScope.bind(): Session {
        val fleet = Fleet(listOf(NotificationFixture.bot()), emptyList())
        val session = liveSession(server, fleet)
        poster.bind(session, AvatarImageStore(fetch = { null }), shortcuts, backgroundScope)
        return session
    }

    private fun shortcutId(session: Session): String =
        shortcuts.shortcutId(requireNotNull(session.connection.value).id, BOT_ID)

    private fun frame() = NotificationFrame(
        kind = "done",
        botId = BOT_ID,
        botName = "Scout",
        threadId = THREAD_ID,
        title = "Scout",
        body = "All done.",
    )

    private fun request() = ReplyRequest(
        tag = NotificationMapping.dedupeId(frame(), 1),
        botId = BOT_ID,
        threadId = THREAD_ID,
        botName = "Scout",
    )

    private fun posted(): Notification =
        assertNotNull(shadowOf(manager).getNotification(request().tag, 0))
}
