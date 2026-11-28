package bot.seira.companion.update

import android.app.Activity
import android.content.Context
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.model.AppUpdateType
import bot.seira.companion.audio.LiveCallPhase
import bot.seira.companion.audio.LiveCallSnapshot
import bot.seira.companion.core.CompanionState
import bot.seira.companion.core.PendingEdit
import bot.seira.companion.core.QueuedSend
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fake = FakeAppUpdateManager(context)
    private val updates = RecordingUpdates(PlayAppUpdates(fake, NoopLauncher))
    private val log = MemoryLog()
    private var clock = 1_000_000_000L
    private var waiting = false
    private var onPlay = true

    private lateinit var controller: AppUpdateController

    @Before
    fun setUp() {
        controller = AppUpdateController(
            updates = updates,
            scope = CoroutineScope(Dispatchers.Unconfined),
            enabled = true,
            installedByPlay = { onPlay },
            busy = { waiting },
            log = log,
            now = { clock },
        )
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun start() {
        controller.checkOnStart()
        settle()
    }

    @Test
    fun flexibleUpdateStartsWhenPlayHasOne() {
        fake.setUpdateAvailable(10004, AppUpdateType.FLEXIBLE)
        start()
        assertEquals(listOf(AppUpdateType.FLEXIBLE), updates.started)
        assertTrue(fake.isConfirmationDialogVisible)
    }

    @Test
    fun finishedDownloadShowsTheRestartBanner() {
        fake.setUpdateAvailable(10004, AppUpdateType.FLEXIBLE)
        start()
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        settle()
        assertTrue(controller.state.value is UpdateUiState.Downloading)
        fake.downloadCompletes()
        settle()
        assertEquals(UpdateUiState.ReadyToRestart, controller.state.value)
        assertTrue(controller.restart())
        assertTrue(fake.isInstallSplashScreenVisible)
    }

    @Test
    fun priorityFourStartsAnImmediateUpdate() {
        fake.setUpdateAvailable(10004, AppUpdateType.IMMEDIATE)
        fake.setUpdatePriority(4)
        start()
        assertEquals(listOf(AppUpdateType.IMMEDIATE), updates.started)
        assertEquals(UpdateUiState.Required, controller.state.value)
        assertTrue(fake.isImmediateFlowVisible)
    }

    @Test
    fun priorityThreeStaysFlexible() {
        fake.setUpdateAvailable(10004, AppUpdateType.FLEXIBLE)
        fake.setUpdatePriority(3)
        start()
        assertEquals(listOf(AppUpdateType.FLEXIBLE), updates.started)
    }

    @Test
    fun aLiveCallBlocksTheRestart() {
        fake.setUpdateAvailable(10004, AppUpdateType.FLEXIBLE)
        start()
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        fake.downloadCompletes()
        settle()
        waiting = true
        assertFalse(controller.restart())
        assertEquals(0, updates.completed)
        assertFalse(fake.isInstallSplashScreenVisible)
        waiting = false
        assertTrue(controller.restart())
        assertEquals(1, updates.completed)
    }

    @Test
    fun anImmediateUpdateWaitsWhileBusyAndKeepsTheCheckOpen() {
        fake.setUpdateAvailable(10004, AppUpdateType.IMMEDIATE)
        fake.setUpdatePriority(5)
        waiting = true
        start()
        assertEquals(emptyList(), updates.started)
        assertEquals(0L, log.lastCheckedAt)
        waiting = false
        start()
        assertEquals(listOf(AppUpdateType.IMMEDIATE), updates.started)
    }

    @Test
    fun gateIsBusyForCallPairingInviteAndSend() {
        val idle = LiveCallSnapshot()
        val state = CompanionState()
        assertFalse(UpdateGate.busy(idle, false, false, state))
        assertTrue(UpdateGate.busy(idle.copy(phase = LiveCallPhase.LIVE), false, false, state))
        assertTrue(UpdateGate.busy(idle.copy(phase = LiveCallPhase.ENDING), false, false, state))
        assertTrue(UpdateGate.busy(idle, true, false, state))
        assertTrue(UpdateGate.busy(idle, false, true, state))
        assertTrue(UpdateGate.busy(idle, false, false, state.copy(pendingQueued = mapOf("t" to listOf(QueuedSend("q", "hi"))))))
        assertTrue(UpdateGate.busy(idle, false, false, state.copy(pendingEdits = mapOf("t" to PendingEdit("m", "hi")))))
    }

    @Test
    fun anInstallFromAnotherSourceMakesNoUpdateCall() {
        onPlay = false
        controller = AppUpdateController(updates, CoroutineScope(Dispatchers.Unconfined), true, { onPlay }, { waiting }, log, { clock })
        fake.setUpdateAvailable(10004, AppUpdateType.FLEXIBLE)
        start()
        controller.resume()
        settle()
        val manual: Deferred<Boolean> = CoroutineScope(Dispatchers.Unconfined).async { controller.checkNow() }
        settle()
        assertFalse(manual.getCompleted())
        assertEquals(0, updates.calls)
    }

    @Test
    fun aBuildWithUpdatesOffMakesNoUpdateCall() {
        controller = AppUpdateController(updates, CoroutineScope(Dispatchers.Unconfined), false, { true }, { waiting }, log, { clock })
        fake.setUpdateAvailable(10004, AppUpdateType.FLEXIBLE)
        start()
        assertEquals(0, updates.calls)
    }

    @Test
    fun aRejectedUpdateIsNotOfferedAgainForADay() {
        fake.setUpdateAvailable(10004, AppUpdateType.FLEXIBLE)
        start()
        fake.userRejectsUpdate()
        settle()
        updates.started.clear()
        clock += 60 * 60 * 1000L
        start()
        assertEquals(emptyList(), updates.started)
        clock += AppUpdateController.CHECK_INTERVAL_MS
        start()
        assertEquals(listOf(AppUpdateType.FLEXIBLE), updates.started)
    }

    @Test
    fun aManualCheckSkipsTheDayWait() {
        fake.setUpdateAvailable(10004, AppUpdateType.FLEXIBLE)
        log.lastCheckedAt = clock
        start()
        assertEquals(emptyList(), updates.started)
        val manual = CoroutineScope(Dispatchers.Unconfined).async { controller.checkNow() }
        settle()
        assertTrue(manual.getCompleted())
        assertEquals(listOf(AppUpdateType.FLEXIBLE), updates.started)
    }

    @Test
    fun noUpdateLeavesTheStateEmptyAndStampsTheCheck() {
        fake.setUpdateNotAvailable()
        start()
        assertEquals(UpdateUiState.None, controller.state.value)
        assertEquals(clock, log.lastCheckedAt)
    }

    @Test
    fun aCancelledImmediateFlowClearsTheState() {
        fake.setUpdateAvailable(10004, AppUpdateType.IMMEDIATE)
        fake.setUpdatePriority(4)
        start()
        controller.onFlowResult(Activity.RESULT_CANCELED)
        assertEquals(UpdateUiState.None, controller.state.value)
    }

    @Test
    fun closeRemovesTheListener() {
        assertEquals(1, updates.listeners)
        controller.close()
        assertEquals(0, updates.listeners)
    }

    @Test
    fun playInstallIsRecognised() {
        shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, null, "com.android.vending")
        assertTrue(InstallSource.isGooglePlay(context))
    }

    @Test
    fun anotherInstallerIsNotPlay() {
        shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, null, "com.example.store")
        assertFalse(InstallSource.isGooglePlay(context))
    }

    @Test
    fun aSideloadIsNotPlay() {
        shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, null, null)
        assertFalse(InstallSource.isGooglePlay(context))
    }

    @Test
    @Config(sdk = [28])
    fun playInstallIsRecognisedBelowApi30() {
        @Suppress("DEPRECATION")
        context.packageManager.setInstallerPackageName(context.packageName, "com.android.vending")
        assertTrue(InstallSource.isGooglePlay(context))
    }

    private object NoopLauncher : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract: ActivityResultContract<IntentSenderRequest, *>
            get() = ActivityResultContracts.StartIntentSenderForResult()
    }

    private class MemoryLog(override var lastCheckedAt: Long = 0L) : UpdateCheckLog

    private class RecordingUpdates(private val inner: AppUpdates) : AppUpdates {
        val started = mutableListOf<Int>()
        var completed = 0
        var calls = 0
        var listeners = 0

        override suspend fun fetch(): AppUpdateInfo? {
            calls++
            return inner.fetch()
        }

        override fun start(info: AppUpdateInfo, type: Int): Boolean {
            calls++
            started += type
            return inner.start(info, type)
        }

        override fun complete() {
            calls++
            completed++
            inner.complete()
        }

        override fun listen(onState: (InstallState) -> Unit): () -> Unit {
            listeners++
            val stop = inner.listen(onState)
            return {
                listeners--
                stop()
            }
        }
    }
}
