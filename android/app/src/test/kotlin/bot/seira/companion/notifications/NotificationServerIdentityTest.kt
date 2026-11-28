package bot.seira.companion.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import bot.seira.companion.avatar.AvatarImageStore
import bot.seira.companion.core.Chat
import bot.seira.companion.core.Connection
import bot.seira.companion.core.ConnectionRegistry
import bot.seira.companion.core.ConnectionRegistryRestore
import bot.seira.companion.core.ConnectionStore
import bot.seira.companion.core.Fleet
import bot.seira.companion.core.Frame
import bot.seira.companion.core.InMemoryOnboardingStore
import bot.seira.companion.core.NotificationFrame
import bot.seira.companion.core.NotificationTarget
import bot.seira.companion.core.Session
import bot.seira.companion.core.StreamFrame
import bot.seira.companion.core.TokenStore
import bot.seira.companion.shortcuts.BotShortcuts
import bot.seira.companion.ui.NotificationOpen
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A local notification names the computer that posted it (seira-mobile#67).
 *
 * Two synthetic computers are paired. A banner posted while one is on screen
 * must still open that computer's chat after the person switched to the
 * other, not search the wrong computer for the same ids.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationServerIdentityTest {
    private lateinit var context: Application
    private lateinit var manager: NotificationManager
    private lateinit var poster: LocalNotificationPoster
    private lateinit var shortcuts: BotShortcuts

    private val alpha = Connection(id = "alpha", name = "Alpha", host = "alpha.local", port = 8810)
    private val beta = Connection(id = "beta", name = "Beta", host = "beta.local", port = 8810)

    @BeforeTest
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Application
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        shortcuts = BotShortcuts(context)
        shortcuts.removeAll()
        poster = LocalNotificationPoster(context)
    }

    @Test
    fun `a bot message banner carries the computer that posted it`() = runTest {
        bind()

        poster.deliver(frame("done"), sequence = 1)
        runCurrent()

        val tap = assertNotNull(shadowOf(posted("done").contentIntent).savedIntent)
        assertEquals(
            NotificationTarget.from(BOT_ID, THREAD_ID, alpha.id),
            tap.notificationTarget(),
        )
        assertTrue(tap.data.toString().contains("pairing=alpha"))
    }

    @Test
    fun `an approval banner and its Review button carry the computer that posted it`() = runTest {
        bind()

        poster.deliver(frame("approval"), sequence = 2)
        runCurrent()

        val notification = posted("approval", sequence = 2)
        val body = assertNotNull(shadowOf(notification.contentIntent).savedIntent)
        assertEquals(alpha.id, body.notificationTarget()?.pairing)
        val review = notification.actions.last()
        val reviewIntent = assertNotNull(shadowOf(review.actionIntent).savedIntent)
        assertEquals(alpha.id, reviewIntent.notificationTarget()?.pairing)
    }

    @Test
    fun `the same bot and thread on two computers do not share one tap`() = runTest {
        val session = bind()
        poster.deliver(frame("done"), sequence = 1)
        runCurrent()
        val fromAlpha = assertNotNull(shadowOf(posted("done").contentIntent).savedIntent)

        session.activateComputer(beta.id)
        runCurrent()
        poster.deliver(frame("done"), sequence = 3)
        runCurrent()
        val fromBeta = assertNotNull(shadowOf(posted("done", sequence = 3, pairing = beta.id).contentIntent).savedIntent)

        assertEquals(beta.id, fromBeta.notificationTarget()?.pairing)
        assertNotEquals(fromAlpha.data, fromBeta.data)
    }

    @Test
    fun `tapping a banner from alpha while beta is open returns to alpha's chat`() = runTest {
        val session = bind()
        poster.deliver(frame("done"), sequence = 1)
        runCurrent()
        val tap = assertNotNull(shadowOf(posted("done").contentIntent).savedIntent)
        session.activateComputer(beta.id)
        runCurrent()
        assertEquals(beta.id, session.connection.value?.id)

        val result = NotificationOpen.open(session, assertNotNull(tap.notificationTarget()))

        assertEquals(alpha.id, session.connection.value?.id)
        assertTrue(result.consumed)
        assertEquals(THREAD_ID, assertIs<Chat.BotChat>(result.chat).threadId)
        assertNull(session.actionError)
    }

    @Test
    fun `a banner from a computer that was removed says so instead of doing nothing`() = runTest {
        val session = bind()
        val target = requireNotNull(NotificationTarget.from(BOT_ID, THREAD_ID, "removed-computer"))

        val result = NotificationOpen.open(session, target)

        assertNull(result.chat)
        assertTrue(result.consumed)
        assertEquals(NotificationOpen.COMPUTER_GONE_MESSAGE, session.actionError)
        assertEquals(alpha.id, session.connection.value?.id)
    }

    private fun frame(kind: String) = NotificationFrame(
        kind = kind,
        botId = BOT_ID,
        botName = "Scout",
        threadId = THREAD_ID,
        title = "Scout",
        body = "Finished.",
        requestId = if (kind == "approval") "request-1" else null,
    )

    private fun posted(
        kind: String,
        sequence: Int = if (kind == "approval") 2 else 1,
        pairing: String = alpha.id,
    ): Notification =
        assertNotNull(shadowOf(manager).getNotification(NotificationMapping.dedupeId(frame(kind), sequence, pairing), 0))

    private suspend fun TestScope.bind(): Session {
        val session = twoComputers()
        poster.bind(session, AvatarImageStore(fetch = { null }), shortcuts, backgroundScope)
        return session
    }

    private class Registry(initial: ConnectionRegistry) : ConnectionStore {
        var registry = initial
        override suspend fun load(): Connection? = registry.activeConnection
        override suspend fun save(connection: Connection) { registry = registry.upsert(connection) }
        override suspend fun clear() { registry = ConnectionRegistry() }
        override suspend fun loadRegistry() = ConnectionRegistryRestore(registry, migratedLegacyConnection = false)
        override suspend fun saveRegistry(registry: ConnectionRegistry) { this.registry = registry.normalized() }
    }

    /** Both computers know the same bot and thread ids, which is what makes the wrong guess look right. */
    private suspend fun TestScope.twoComputers(): Session = Session(
        scope = backgroundScope,
        connectionStore = Registry(ConnectionRegistry(listOf(alpha, beta), alpha.id)),
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
        hydrateFn = { _, _ -> Fleet(listOf(NotificationFixture.bot()), emptyList()) },
        metadataFn = { throw IllegalStateException("not under test") },
    ).also {
        it.awaitRestored()
        it.connect()
        runCurrent()
    }

    private companion object {
        const val BOT_ID = NotificationFixture.BOT_ID
        const val THREAD_ID = NotificationFixture.THREAD_ID
    }
}
