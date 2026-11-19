package com.openmausbot.companion.audio

import android.app.Notification
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveCallServiceTest {
    private class FakeHost(initial: LiveCallSnapshot) : LiveCallService.Host {
        override val state = MutableStateFlow(initial)
        var hangUps = 0
        var failed = 0

        override fun hangUp() {
            hangUps++
        }

        override fun onBackgroundFailed() {
            failed++
        }
    }

    private val live = LiveCallSnapshot(phase = LiveCallPhase.LIVE, botId = "b1", threadId = "t1", botName = "Dina", liveSince = 1_000L)

    @After
    fun reset() {
        LiveCallService.host = null
    }

    private fun start(host: FakeHost): ServiceController<LiveCallService> {
        LiveCallService.host = host
        LiveCallService.avatarLoader = { _, _ -> null }
        val intent = Intent(ApplicationProvider.getApplicationContext(), LiveCallService::class.java)
        val controller = Robolectric.buildService(LiveCallService::class.java, intent)
        controller.create().startCommand(0, 1)
        return controller
    }

    @Test
    fun runsInTheForegroundWithAnOngoingCallStyleNotificationAndAHangUpAction() {
        val controller = start(FakeHost(live))
        val notification = assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        assertEquals(LiveCallService.CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(2, notification.extras.getInt("android.callType"), "ongoing call")
        val hangUp = assertNotNull(notification.actions?.firstOrNull(), "a hang up action")
        assertEquals(LiveCallService.ACTION_HANG_UP, shadowOf(hangUp.actionIntent).savedIntent.action)
        assertNotNull(notification.contentIntent)
    }

    @Test
    fun theHangUpActionEndsTheCallThroughTheHost() {
        val host = FakeHost(live)
        val controller = start(host)
        controller.get().onStartCommand(Intent(LiveCallService.ACTION_HANG_UP), 0, 2)
        assertEquals(1, host.hangUps)
    }

    @Test
    fun stopsWhenTheCallEnds() {
        val host = FakeHost(live)
        val controller = start(host)
        host.state.value = live.copy(phase = LiveCallPhase.ENDING)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun stopsAtOnceWhenThereIsNoCall() {
        val controller = start(FakeHost(LiveCallSnapshot()))
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }
}
