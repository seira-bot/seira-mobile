package bot.seira.companion.glance

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import bot.seira.companion.core.Bot
import bot.seira.companion.core.CompanionState
import bot.seira.companion.core.Glance
import bot.seira.companion.core.ModelSelection
import bot.seira.companion.core.Session
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlanceNotifierTest {
    private lateinit var context: Application
    private lateinit var manager: NotificationManager

    @BeforeTest
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
    }

    @Test
    fun `no notification while no bot is active`() = runTest {
        val rig = rig(CompanionState(bots = listOf(bot("a"))))
        runCurrent()
        assertNull(posted())
    }

    @Test
    fun `no notification while the session is not live, and it goes away when the session leaves live`() = runTest {
        val rig = rig(CompanionState(bots = listOf(bot("a", busy = true))), Session.Status.Connecting)
        runCurrent()
        assertNull(posted())

        rig.status.value = Session.Status.Live
        runCurrent()
        assertNotNull(posted())

        rig.status.value = Session.Status.Offline("down")
        runCurrent()
        assertNull(posted())
    }

    @Test
    fun `two active bots show one notification for the one that works longest`() = runTest {
        val rig = rig(CompanionState(bots = listOf(bot("a", busy = true))))
        runCurrent()
        advanceTimeBy(10)
        rig.state.value = CompanionState(bots = listOf(bot("b", busy = true), bot("a", busy = true)))
        runCurrent()

        assertEquals(1, shadowOf(manager).allNotifications.size)
        assertEquals("Bot a", title())
    }

    @Test
    fun `a finished bot stays for the done time and then leaves`() = runTest {
        val rig = rig(CompanionState(bots = listOf(bot("a", busy = true))))
        runCurrent()
        rig.state.value = CompanionState(bots = listOf(bot("a")))
        runCurrent()
        assertEquals("Finished", text())

        advanceTimeBy(Glance.DONE_MS - 1)
        runCurrent()
        assertNotNull(posted())

        advanceTimeBy(2)
        runCurrent()
        assertNull(posted())
    }

    @Test
    fun `a fast changing line posts at most once a second and lands its last words`() = runTest {
        fun working(words: String) = CompanionState(
            bots = listOf(bot("a", busy = true)),
            streaming = mapOf("a-thread" to words),
        )
        val rig = rig(working("one"))
        runCurrent()
        assertEquals("one", text())

        rig.state.value = working("two")
        runCurrent()
        advanceTimeBy(100)
        rig.state.value = working("three")
        runCurrent()
        assertEquals("one", text())

        advanceTimeBy(GlanceNotifier.LINE_UPDATE_MS)
        runCurrent()
        assertEquals("three", text())
    }

    private class Rig(val state: MutableStateFlow<CompanionState>, val status: MutableStateFlow<Session.Status>)

    private fun TestScope.rig(initial: CompanionState, status: Session.Status = Session.Status.Live): Rig {
        val state = MutableStateFlow(initial)
        val statusFlow = MutableStateFlow(status)
        GlanceNotifier(
            context, state, statusFlow, avatarFor = { null }, now = { testScheduler.currentTime },
        ).start(backgroundScope)
        return Rig(state, statusFlow)
    }

    private fun posted() = shadowOf(manager).getNotification(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID)
    private fun title() = posted()!!.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().replace("‎", "")
    private fun text() = posted()!!.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().replace("‎", "")

    private fun bot(id: String, busy: Boolean? = null) = Bot(
        id = id, threadId = "$id-thread", name = "Bot $id", title = "Helper",
        description = "", notifications = true, color = "blue", unread = false,
        modelSelection = ModelSelection("i", "m"), createdAt = 1.0, busy = busy,
    )
}
