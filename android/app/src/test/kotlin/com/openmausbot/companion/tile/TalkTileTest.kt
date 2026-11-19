package com.openmausbot.companion.tile

import android.app.Application
import android.content.Intent
import android.service.quicksettings.Tile
import com.openmausbot.companion.MainActivity
import com.openmausbot.companion.OpenMausApp
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.notifications.NotificationFixture
import com.openmausbot.companion.notifications.NotificationFixture.liveSession
import com.openmausbot.companion.notifications.NotificationFixture.unpairedSession
import com.openmausbot.companion.ui.LiveCallUi
import com.openmausbot.companion.ui.PendingThreadNavigation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TalkTileTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Application

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        context = RuntimeEnvironment.getApplication() as Application
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    class RecordingTile : TalkTileService() {
        var locked = false
        var started: Intent? = null
        var unlockRequests = 0

        override fun deviceLocked(): Boolean = locked

        override fun unlock(action: Runnable) {
            unlockRequests += 1
            action.run()
        }

        override fun startTalk(intent: Intent) {
            started = intent
        }
    }

    @Test
    fun `the talk intent opens the app with the talk action`() {
        val intent = TalkIntents.intent(context)

        assertEquals("bot.seira.companion.action.TALK", intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertNotEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun `a tap on an unlocked phone opens a call with the talk action`() {
        val tile = Robolectric.setupService(RecordingTile::class.java)

        tile.onClick()

        assertEquals(TalkIntents.ACTION_TALK, tile.started?.action)
        assertEquals(0, tile.unlockRequests)
    }

    @Test
    fun `a tap on a locked phone asks to unlock first`() {
        val tile = Robolectric.setupService(RecordingTile::class.java).apply { locked = true }

        tile.onClick()

        assertEquals(1, tile.unlockRequests)
        assertEquals(TalkIntents.ACTION_TALK, tile.started?.action)
    }

    @Test
    fun `the tile is unavailable without a pairing`() {
        assertEquals(Tile.STATE_UNAVAILABLE, TalkTileService.tileStateFor(paired = false))
        assertEquals(Tile.STATE_INACTIVE, TalkTileService.tileStateFor(paired = true))
    }

    @Test
    fun `the chief of staff is asked for a call and the chat opens`() = runTest {
        val chief = NotificationFixture.bot("chief", "Maren", "chief-thread", chief = true)
        val session = liveSession(server, Fleet(listOf(NotificationFixture.bot(), chief), emptyList()))
        val callUi = LiveCallUi()
        val navigation = PendingThreadNavigation()

        TalkRequestHandler(session, callUi, navigation, backgroundScope).handle()
        runCurrent()

        assertEquals("chief", callUi.requestedBotId.value)
        assertEquals("chief", navigation.pending.value?.botId)
        assertEquals("chief-thread", navigation.pending.value?.threadId)
    }

    @Test
    fun `an unanswered call request expires`() = runTest {
        val chief = NotificationFixture.bot("chief", "Maren", "chief-thread", chief = true)
        val session = liveSession(server, Fleet(listOf(chief), emptyList()))
        val callUi = LiveCallUi()

        TalkRequestHandler(session, callUi, PendingThreadNavigation(), backgroundScope).handle()
        runCurrent()
        assertEquals("chief", callUi.requestedBotId.value)

        advanceTimeBy(TalkIntents.REQUEST_TTL_MS + 1)
        runCurrent()

        assertNull(callUi.requestedBotId.value)
    }

    @Test
    fun `without a chief of staff nothing is requested`() = runTest {
        val session = liveSession(server, Fleet(listOf(NotificationFixture.bot()), emptyList()))
        val callUi = LiveCallUi()
        val navigation = PendingThreadNavigation()

        TalkRequestHandler(session, callUi, navigation, backgroundScope, chiefWaitMs = 100).handle()
        advanceTimeBy(200)
        runCurrent()

        assertNull(callUi.requestedBotId.value)
        assertNull(navigation.pending.value)
    }

    @Test
    fun `without a pairing nothing is requested`() = runTest {
        val session = unpairedSession()
        val callUi = LiveCallUi()

        TalkRequestHandler(session, callUi, PendingThreadNavigation(), backgroundScope).handle()
        runCurrent()

        assertNull(callUi.requestedBotId.value)
    }

    @Test
    fun `the main activity asks for a call with the chief of staff on the talk action`() = runTest {
        val chief = NotificationFixture.bot("chief", "Maren", "chief-thread", chief = true)
        val session = liveSession(server, Fleet(listOf(NotificationFixture.bot(), chief), emptyList()))
        val app = context as OpenMausApp
        OpenMausApp::class.java.getDeclaredField("session").apply { isAccessible = true }.set(app, session)

        val activity = Robolectric.buildActivity(MainActivity::class.java, TalkIntents.intent(app)).create().get()

        assertTrue(activity.callUi.requestedBotId.value == "chief")
    }
}
