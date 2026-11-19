package com.openmausbot.companion.shortcuts

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import com.openmausbot.companion.avatar.AvatarImageStore
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.notifications.LocalNotificationPoster
import com.openmausbot.companion.notifications.NotificationFixture
import com.openmausbot.companion.notifications.NotificationFixture.liveSession
import com.openmausbot.companion.notifications.NotificationFixture.unpairedSession
import com.openmausbot.companion.tile.TalkTileState
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BotShortcutsTest {
    private lateinit var context: Application
    private lateinit var shortcuts: BotShortcuts

    @BeforeTest
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Application
        shortcuts = BotShortcuts(context)
        shortcuts.removeAll()
    }

    @Test
    fun `a recent bot becomes a long-lived conversation shortcut`() {
        shortcuts.publish("computer-1", listOf(identity("bot-1", "Scout") to null), setOf("bot-1"))

        val published = ShortcutManagerCompat.getDynamicShortcuts(context).single()
        assertEquals(shortcuts.shortcutId("computer-1", "bot-1"), published.id)
        assertEquals("Scout", published.shortLabel.toString())
        assertTrue(published.isLongLived)
        assertEquals(Intent.FLAG_ACTIVITY_SINGLE_TOP, published.intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP)
        assertEquals("bot-1", published.intent.getStringExtra(LocalNotificationPoster.EXTRA_BOT_ID))
    }

    @Test
    fun `the same bot on two computers keeps two shortcuts apart`() {
        assertTrue(shortcuts.shortcutId("computer-1", "bot-1") != shortcuts.shortcutId("computer-2", "bot-1"))
    }

    @Test
    fun `a bot that left the roster and another computer's bots are dropped`() {
        shortcuts.publish(
            "computer-1",
            listOf(identity("bot-1", "Scout") to null, identity("bot-2", "Maren") to null),
            setOf("bot-1", "bot-2"),
        )
        shortcuts.push("computer-2", identity("bot-3", "Rhea"), null)

        shortcuts.publish("computer-1", listOf(identity("bot-1", "Scout") to null), setOf("bot-1"))

        assertEquals(
            listOf(shortcuts.shortcutId("computer-1", "bot-1")),
            ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id },
        )
    }

    @Test
    fun `signing out removes every shortcut`() {
        shortcuts.publish("computer-1", listOf(identity("bot-1", "Scout") to null), setOf("bot-1"))

        shortcuts.removeAll()

        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty())
    }

    @Test
    fun `an unpaired session clears the shortcuts and the remembered chief`() = runTest {
        shortcuts.push("computer-1", identity("bot-1", "Scout"), null)
        val tile = TalkTileState(context).also { it.chiefName = "Maren" }
        val sync = BotShortcutSync(unpairedSession(), AvatarImageStore(fetch = { null }), shortcuts, tile)

        backgroundScope.launch { sync.run() }
        runCurrent()

        assertTrue(ShortcutManagerCompat.getDynamicShortcuts(context).isEmpty())
        assertNull(tile.chiefName)
    }

    @Test
    fun `a live roster publishes its four most recent bots and the chief name`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            val bots = listOf(
                NotificationFixture.bot("b1", "Anna", "t1", messages = listOf(text("m1", 10.0))),
                NotificationFixture.bot("b2", "Bela", "t2", messages = listOf(text("m2", 50.0))),
                NotificationFixture.bot("b3", "Cleo", "t3", messages = listOf(text("m3", 30.0))),
                NotificationFixture.bot("b4", "Dara", "t4", messages = listOf(text("m4", 40.0))),
                NotificationFixture.bot("b5", "Eli", "t5", messages = listOf(text("m5", 20.0)), chief = true),
            )
            val session = liveSession(server, Fleet(bots, emptyList()))
            val tile = TalkTileState(context)
            val sync = BotShortcutSync(session, AvatarImageStore(fetch = { null }), shortcuts, tile)

            backgroundScope.launch { sync.run() }
            advanceTimeBy(BotShortcutSync.SETTLE_MS + 1)
            runCurrent()

            val computerId = requireNotNull(session.connection.value).id
            assertEquals(
                setOf("b2", "b4", "b3", "b5").map { shortcuts.shortcutId(computerId, it) }.toSet(),
                ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.toSet(),
            )
            assertEquals("Eli", tile.chiefName)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the generated face is a square bitmap in the bot's colour with its initial`() {
        val circle = BotIcons.circle(null, "scout", "green")
        assertEquals(192, circle.width)
        assertEquals(192, circle.height)
        assertEquals("S", BotIcons.initial("scout"))
        assertEquals("?", BotIcons.initial("   "))
        val adaptive = BotIcons.adaptive(null, "Scout", "green")
        assertEquals(adaptive.width, adaptive.height)
    }

    private fun identity(id: String, name: String) = BotIdentity(id, "thread-$id", name, "green", null)

    private fun text(id: String, at: Double) =
        Message(id = id, role = Message.Role.BOT, kind = Message.Kind.TEXT, at = at, text = "Hello")
}
