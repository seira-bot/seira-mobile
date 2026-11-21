package com.openmausbot.companion.widget

import android.app.Application
import android.content.Intent
import com.openmausbot.companion.core.Fleet
import com.openmausbot.companion.notifications.ApproveRequest
import com.openmausbot.companion.notifications.NotificationFixture
import com.openmausbot.companion.notifications.NotificationFixture.cardMessage
import com.openmausbot.companion.notifications.NotificationFixture.liveSession
import com.openmausbot.companion.notifications.NotificationIntents
import com.openmausbot.companion.widget.WidgetFixture.NOW
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetAnswerHandlerTest {
    private lateinit var context: Application
    private lateinit var store: WidgetSnapshotStore
    private val approved = mutableListOf<ApproveRequest>()
    private val started = mutableListOf<Intent>()
    private val told = mutableListOf<Int>()

    @BeforeTest
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Application
        store = WidgetSnapshotStore(File(context.cacheDir, "widget-test-${System.nanoTime()}"))
        approved.clear(); started.clear(); told.clear()
    }

    private suspend fun kotlinx.coroutines.test.TestScope.handler(locked: Boolean, now: Long = NOW): WidgetAnswerHandler {
        val server = MockWebServer().apply { start() }
        val fleet = Fleet(
            listOf(NotificationFixture.bot(messages = listOf(cardMessage(NotificationFixture.permissionCard())))),
            emptyList(),
        )
        val session = liveSession(server, fleet)
        return WidgetAnswerHandler(
            context = context,
            store = store,
            clock = { now },
            isLocked = { locked },
            state = { session.state.value },
            approve = { approved += it; true },
            startActivity = { started += it },
            tell = { told += it },
            redraw = { },
        )
    }

    private val ask = WidgetFixture.row(threadId = NotificationFixture.THREAD_ID).copy(botId = NotificationFixture.BOT_ID)

    private suspend fun WidgetAnswerHandler.tap() =
        onTap(NotificationFixture.THREAD_ID, NotificationFixture.BOT_ID, "request-1", "Allow")

    @Test
    fun `a locked phone sends no answer and opens the chat`() = runTest {
        store.write(WidgetFixture.snapshot(listOf(ask), NOW))
        handler(locked = true).tap()

        assertTrue(approved.isEmpty())
        val intent = started.single()
        assertTrue(
            intent.filterEquals(
                NotificationIntents.openIntent(context, NotificationFixture.BOT_ID, NotificationFixture.THREAD_ID, "needsYou"),
            ),
        )
        assertEquals(1, store.read()?.rows?.size)
    }

    @Test
    fun `an unlocked phone sends the answer for a short plain ask and removes the row`() = runTest {
        store.write(WidgetFixture.snapshot(listOf(ask), NOW))
        handler(locked = false).tap()

        assertEquals("request-1", approved.single().requestId)
        assertTrue(started.isEmpty())
        assertTrue(store.read()!!.rows.isEmpty())
    }

    @Test
    fun `a request that may be cut sends no answer`() = runTest {
        store.write(WidgetFixture.snapshot(listOf(ask.copy(fullyShown = false)), NOW))
        handler(locked = false).tap()

        assertTrue(approved.isEmpty())
        assertEquals(1, told.size)
    }

    @Test
    fun `a live card whose text differs from the snapshot sends no answer and opens the chat`() = runTest {
        store.write(WidgetFixture.snapshot(listOf(ask.copy(line = "git status")), NOW))
        handler(locked = false).tap()

        assertTrue(approved.isEmpty())
        assertEquals(1, started.size)
    }

    @Test
    fun `a snapshot older than ten minutes sends no answer`() = runTest {
        store.write(WidgetFixture.snapshot(listOf(ask), NOW))
        handler(locked = false, now = NOW + 601_000).tap()

        assertTrue(approved.isEmpty())
        assertEquals(1, told.size)
    }

    @Test
    fun `no snapshot sends no answer`() = runTest {
        handler(locked = false).tap()

        assertTrue(approved.isEmpty())
        assertNull(store.read())
    }
}
