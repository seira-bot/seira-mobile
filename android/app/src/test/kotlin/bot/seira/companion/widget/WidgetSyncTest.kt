package bot.seira.companion.widget

import android.app.Application
import bot.seira.companion.core.ActivityDetail
import bot.seira.companion.core.Fleet
import bot.seira.companion.notifications.NotificationFixture
import bot.seira.companion.notifications.NotificationFixture.cardMessage
import bot.seira.companion.notifications.NotificationFixture.liveSession
import bot.seira.companion.notifications.NotificationFixture.unpairedSession
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSyncTest {
    private lateinit var context: Application
    private lateinit var store: WidgetSnapshotStore
    private var refreshes = 0
    private val scheduled = mutableListOf<Long>()
    private val detail = MutableStateFlow(ActivityDetail.REDUCED)

    @BeforeTest
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Application
        store = WidgetSnapshotStore.forContext(context)
        store.remove()
        refreshes = 0
        scheduled.clear()
    }

    private fun sync(session: bot.seira.companion.core.Session) = WidgetSync(
        session = session,
        store = store,
        detail = detail,
        faces = { },
        refresh = { refreshes++ },
        freshness = WidgetFreshnessScheduler { writtenAt, _ -> scheduled += writtenAt },
        clock = { WidgetFixture.NOW },
    )

    private fun askFleet() = Fleet(
        listOf(
            NotificationFixture.bot(
                messages = listOf(
                    cardMessage(NotificationFixture.permissionCard()),
                ),
            ),
        ),
        emptyList(),
    )

    @Test
    fun `an update is written and the widget refreshes within a second`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            val session = liveSession(server, askFleet())
            backgroundScope.launch { sync(session).run() }
            advanceTimeBy(WidgetSync.WINDOW_MS + 1)
            runCurrent()

            val written = requireNotNull(store.read())
            val row = written.rows.single()
            assertEquals(WidgetSnapshot.Kind.NEEDS_YOU, row.kind)
            assertEquals("git push", row.line)
            assertEquals(NotificationFixture.THREAD_ID, row.threadId)
            assertEquals(requireNotNull(session.connection.value).id, written.connectionId)
            assertEquals(ActivityDetail.REDUCED.wireValue, written.detail)
            assertTrue(refreshes >= 1)
            assertEquals(listOf(WidgetFixture.NOW), scheduled.distinct())
            assertTrue(WidgetSync.WINDOW_MS < 1_000)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `an unpaired phone removes the snapshot and refreshes`() = runTest {
        store.write(WidgetFixture.snapshot())
        backgroundScope.launch { sync(unpairedSession()).run() }
        runCurrent()

        assertNull(store.read())
        assertFalse(store.file.exists())
        assertTrue(refreshes >= 1)
    }

    @Test
    fun `the snapshot file lives under the no backup folder`() {
        val noBackup = context.noBackupFilesDir.canonicalFile
        assertTrue(store.file.canonicalFile.path.startsWith(noBackup.path + File.separator))
        assertEquals(WidgetSnapshotStore.FILE_NAME, store.file.name)
        assertTrue(store.facesDirectory.canonicalFile.path.startsWith(noBackup.path + File.separator))
    }

    @Test
    fun `the backup rules leave no way to copy the widget folder`() {
        val root = File("src/main/res/xml").takeIf(File::isDirectory) ?: File("app/src/main/res/xml")
        for (name in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val text = File(root, name).readText()
            assertFalse(text.contains("domain=\"file\""), "$name includes the files domain")
            assertFalse(text.contains("widget"), "$name names the widget folder")
        }
    }

    @Test
    fun `the widget code makes no network call`() {
        val root = File("src/main/kotlin/bot/seira/companion/widget")
            .takeIf(File::isDirectory) ?: File("app/src/main/kotlin/bot/seira/companion/widget")
        val forbidden = listOf("okhttp", "java.net.", "HttpURLConnection", "Session.send", "session.send", "approve(")
        for (file in root.listFiles().orEmpty()) {
            val text = file.readText()
            for (token in forbidden) {
                assertFalse(text.contains(token), "${file.name} uses $token")
            }
        }
    }
}
