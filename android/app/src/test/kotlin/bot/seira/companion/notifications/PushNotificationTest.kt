package bot.seira.companion.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import bot.seira.companion.core.Chat
import bot.seira.companion.core.Connection
import bot.seira.companion.core.ConnectionRegistry
import bot.seira.companion.core.ConnectionRegistryRestore
import bot.seira.companion.core.ConnectionStore
import bot.seira.companion.core.Fleet
import bot.seira.companion.core.Frame
import bot.seira.companion.core.InMemoryOnboardingStore
import bot.seira.companion.core.NotificationTarget
import bot.seira.companion.core.Session
import bot.seira.companion.core.StreamFrame
import bot.seira.companion.core.TokenStore
import bot.seira.companion.notifications.NotificationFixture.BOT_ID
import bot.seira.companion.notifications.NotificationFixture.THREAD_ID
import bot.seira.companion.notifications.NotificationFixture.liveSession
import bot.seira.companion.push.PushDelivery
import bot.seira.companion.ui.NotificationOpen
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
import org.robolectric.shadows.ShadowLog

/** A remote push, from the data message to the tap. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushNotificationTest {
    private lateinit var context: Application
    private lateinit var manager: NotificationManager
    private lateinit var poster: LocalNotificationPoster

    @BeforeTest
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Application
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        poster = LocalNotificationPoster(context)
        ShadowLog.clear()
    }

    private fun data(vararg overrides: Pair<String, String>): Map<String, String> = mapOf(
        "v" to "1",
        "kind" to "approval",
        "botId" to BOT_ID,
        "threadId" to THREAD_ID,
        "title" to "Scout needs approval",
        "tag" to "openmaus.$THREAD_ID.7",
        "channelId" to "approval_question",
        "pairing" to "0b6f2c1e-4d5a-4f43-8c5e-2d7f1a9b3c10",
        "requestId" to "request-1",
    ) + overrides

    private fun TestScope.unboundDelivery(): PushDelivery = PushDelivery(
        Session(
            scope = backgroundScope,
            connectionStore = object : ConnectionStore {
                override suspend fun load(): Connection? = null
                override suspend fun save(connection: Connection) = Unit
                override suspend fun clear() = Unit
            },
            tokenStore = object : TokenStore {
                override suspend fun save(connectionId: String, token: String) = Unit
                override suspend fun read(connectionId: String) = TokenStore.ReadResult.Missing
                override suspend fun remove(connectionId: String) = Unit
            },
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Pixel" },
            eventsFn = { _, _, _ -> flow { awaitCancellation() } },
        ),
        poster,
    )

    private fun posted(tag: String): Notification? = shadowOf(manager).getNotification(tag, 0)

    @Test
    fun `AC-03 a valid push posts one notification with the tag, id 0, the title and no text`() = runTest {
        unboundDelivery().receive(data())

        assertEquals(1, shadowOf(manager).allNotifications.size)
        val notification = assertNotNull(posted("openmaus.$THREAD_ID.7"))
        assertEquals("Scout needs approval", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertNull(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        assertEquals(0, shadowOf(manager).activeNotifications.single().id)
    }

    @Test
    fun `AC-03 a push with the same tag replaces the earlier one`() = runTest {
        val delivery = unboundDelivery()
        delivery.receive(data())
        delivery.receive(data("title" to "Scout still needs approval"))

        assertEquals(1, shadowOf(manager).allNotifications.size)
        assertEquals(
            "Scout still needs approval",
            posted("openmaus.$THREAD_ID.7")!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
    }

    @Test
    fun `AC-04 the channel the relay names wins when it is one of ours`() = runTest {
        val delivery = unboundDelivery()
        listOf("approval_question", "bot_messages", "routine_failed").forEachIndexed { index, channel ->
            delivery.receive(data("tag" to "openmaus.t.$index", "kind" to "done", "channelId" to channel))
            assertEquals(channel, posted("openmaus.t.$index")!!.channelId, channel)
        }
    }

    @Test
    fun `AC-04 an unknown or missing channel falls back to the kind`() = runTest {
        val delivery = unboundDelivery()
        delivery.receive(data("tag" to "openmaus.a.1", "kind" to "approval", "channelId" to "made_up"))
        delivery.receive(data("tag" to "openmaus.a.2", "kind" to "done", "channelId" to "made_up"))
        delivery.receive(data("tag" to "openmaus.a.3", "kind" to "routine-failed") - "channelId")
        delivery.receive(data("tag" to "openmaus.a.4", "kind" to "question") - "channelId")

        assertEquals(NotificationMapping.CHANNEL_BLOCKING, posted("openmaus.a.1")!!.channelId)
        assertEquals(NotificationMapping.CHANNEL_DONE, posted("openmaus.a.2")!!.channelId)
        assertEquals(NotificationMapping.CHANNEL_ROUTINE_FAILED, posted("openmaus.a.3")!!.channelId)
        assertEquals(NotificationMapping.CHANNEL_BLOCKING, posted("openmaus.a.4")!!.channelId)
    }

    @Test
    fun `AC-05 a message that fails validation posts nothing and logs nothing`() = runTest {
        val delivery = unboundDelivery()
        listOf(
            data("v" to "2"),
            data() - "botId",
            data("threadId" to "a/b"),
            data("title" to " "),
            data() - "tag",
            data("pairing" to "x"),
            mapOf("body" to "not json", "title" to "Secret title", "tag" to "openmaus.x.1"),
            emptyMap(),
        ).forEach { delivery.receive(it) }

        assertEquals(0, shadowOf(manager).allNotifications.size)
        val logged = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg}" }
        listOf("Secret title", "Scout needs approval", BOT_ID, THREAD_ID, "0b6f2c1e").forEach {
            assertFalse(logged.contains(it), "the log must not hold $it")
        }
    }

    @Test
    fun `AC-06 a Live stream to the named computer suppresses the push`() = runTest {
        val session = liveSession(MockWebServer().apply { start() }, Fleet(emptyList(), emptyList()))
        val live = requireNotNull(session.connection.value).id
        val delivery = PushDelivery(session, poster)

        delivery.receive(data("pairing" to live))
        assertEquals(0, shadowOf(manager).allNotifications.size)

        delivery.receive(data("pairing" to "another-computer-id"))
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `AC-06 a push with no pairing is posted`() = runTest {
        val session = liveSession(MockWebServer().apply { start() }, Fleet(emptyList(), emptyList()))

        PushDelivery(session, poster).receive(data() - "pairing")

        assertEquals(1, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun `AC-09 a push offers Review only, also with a request id and a card the phone holds`() = runTest {
        val card = NotificationFixture.permissionCard()
        val fleet = Fleet(
            listOf(NotificationFixture.bot(messages = listOf(NotificationFixture.cardMessage(card)))),
            emptyList(),
        )
        val session = liveSession(MockWebServer().apply { start() }, fleet)
        poster.bind(
            session,
            bot.seira.companion.avatar.AvatarImageStore(fetch = { null }),
            bot.seira.companion.shortcuts.BotShortcuts(context),
            backgroundScope,
        )
        runCurrent()

        PushDelivery(session, poster).receive(data("pairing" to "another-computer-id"))

        val notification = assertNotNull(posted("openmaus.$THREAD_ID.7"))
        assertEquals(listOf("Review"), notification.actions.map { it.title.toString() })
        val intent = shadowOf(notification.actions.single().actionIntent).savedIntent
        assertEquals(NotificationIntents.ACTION_OPEN, intent.action)
        assertFalse(
            intent.component?.className == NotificationActionReceiver::class.java.name,
            "Review opens the app; it never reaches the approve receiver",
        )
    }

    @Test
    fun `the tap carries the computer, in the extras and in the data URI`() = runTest {
        unboundDelivery().receive(data())

        val notification = assertNotNull(posted("openmaus.$THREAD_ID.7"))
        val tap = shadowOf(notification.contentIntent).savedIntent
        assertEquals("0b6f2c1e-4d5a-4f43-8c5e-2d7f1a9b3c10", tap.getStringExtra(LocalNotificationPoster.EXTRA_PAIRING))
        assertTrue(tap.data.toString().contains("pairing="))
        assertEquals(
            NotificationTarget.from(BOT_ID, THREAD_ID, "0b6f2c1e-4d5a-4f43-8c5e-2d7f1a9b3c10"),
            tap.notificationTarget(),
        )
        val other = NotificationIntents.openIntent(context, BOT_ID, THREAD_ID, "approval", "another-computer-id")
        assertNotEquals(tap.data, other.data)
    }

    // AC-07

    private class Registry(initial: ConnectionRegistry) : ConnectionStore {
        var registry = initial
        override suspend fun load(): Connection? = registry.activeConnection
        override suspend fun save(connection: Connection) { registry = registry.upsert(connection) }
        override suspend fun clear() { registry = ConnectionRegistry() }
        override suspend fun loadRegistry() = ConnectionRegistryRestore(registry, migratedLegacyConnection = false)
        override suspend fun saveRegistry(registry: ConnectionRegistry) { this.registry = registry.normalized() }
    }

    private suspend fun TestScope.twoComputers(): Session {
        val air = Connection(id = "air", name = "Air", host = "air.local", port = 8810)
        val pro = Connection(id = "pro", name = "Pro", host = "pro.local", port = 8810)
        return Session(
            scope = backgroundScope,
            connectionStore = Registry(ConnectionRegistry(listOf(air, pro), "air")),
            tokenStore = object : TokenStore {
                override suspend fun save(connectionId: String, token: String) = Unit
                override suspend fun read(connectionId: String) = TokenStore.ReadResult.Found("token-$connectionId")
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
            hydrateFn = { client, _ ->
                if (client.connection.id == "pro") {
                    Fleet(listOf(NotificationFixture.bot()), emptyList())
                } else {
                    Fleet(emptyList(), emptyList())
                }
            },
            metadataFn = { throw IllegalStateException("not under test") },
        ).also {
            it.awaitRestored()
            it.connect()
            runCurrent()
        }
    }

    @Test
    fun `AC-07 a tap on a push from another saved computer switches to it and opens the thread`() = runTest {
        val session = twoComputers()
        assertEquals("air", session.connection.value?.id)

        val result = NotificationOpen.open(
            session,
            requireNotNull(NotificationTarget.from(BOT_ID, THREAD_ID, "pro")),
        )

        assertEquals("pro", session.connection.value?.id)
        assertTrue(result.consumed)
        val chat = assertIs<Chat.BotChat>(result.chat)
        assertEquals(THREAD_ID, chat.threadId)
    }

    @Test
    fun `AC-07 a tap on a push from the active computer does not switch`() = runTest {
        val session = twoComputers()

        NotificationOpen.open(session, requireNotNull(NotificationTarget.from(BOT_ID, THREAD_ID, "air")))

        assertEquals("air", session.connection.value?.id)
    }

    @Test
    fun `AC-07 a tap on a push from a computer that was removed opens nothing and keeps the active one`() = runTest {
        val session = twoComputers()

        val result = NotificationOpen.open(
            session,
            requireNotNull(NotificationTarget.from(BOT_ID, THREAD_ID, "gone-computer")),
        )

        assertNull(result.chat)
        assertTrue(result.consumed)
        assertEquals("air", session.connection.value?.id)
    }
}
