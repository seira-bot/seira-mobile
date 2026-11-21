package com.openmausbot.companion.update

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object None : UpdateUiState

    /** [progress] is null until Play reports the size. */
    data class Downloading(val progress: Float?) : UpdateUiState

    data object ReadyToRestart : UpdateUiState

    /** Play shows its own full-screen update. */
    data object Required : UpdateUiState
}

/** When Play was last asked, in epoch milliseconds. Zero means never. */
interface UpdateCheckLog {
    var lastCheckedAt: Long
}

class PreferencesUpdateCheckLog(context: Context) : UpdateCheckLog {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override var lastCheckedAt: Long
        get() = preferences.getLong(KEY, 0L)
        set(value) = preferences.edit().putLong(KEY, value).apply()

    private companion object {
        const val NAME = "app_updates"
        const val KEY = "last_checked_at"
    }
}

/**
 * Offers Play updates inside the app. Every Play failure counts as "no update"
 * and shows nothing.
 */
class AppUpdateController(
    private val updates: AppUpdates,
    private val scope: CoroutineScope,
    enabled: Boolean,
    installedByPlay: () -> Boolean,
    private val busy: () -> Boolean,
    private val log: UpdateCheckLog,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.None)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** False for a build or an install that Play does not manage. No update call is made then. */
    val available: Boolean = enabled && installedByPlay()

    private val stopListening: () -> Unit =
        if (available) updates.listen(::onInstallState) else ({})

    fun checkOnStart() {
        if (!available) return
        val last = log.lastCheckedAt
        if (last != 0L && now() - last in 0 until CHECK_INTERVAL_MS) return
        scope.launch { evaluate() }
    }

    /** The person asked. Skips the 24 hour wait. True when an update is on its way. */
    suspend fun checkNow(): Boolean = available && evaluate()

    fun resume() {
        if (!available) return
        scope.launch {
            val info = updates.fetch() ?: return@launch
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> _state.value = UpdateUiState.ReadyToRestart
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && !busy() -> startImmediate(info)
            }
        }
    }

    /** False while the person is in a call, pairing or sending: the restart waits. */
    fun restart(): Boolean {
        if (_state.value != UpdateUiState.ReadyToRestart || busy()) return false
        updates.complete()
        return true
    }

    fun onFlowResult(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK && _state.value == UpdateUiState.Required) {
            _state.value = UpdateUiState.None
        }
    }

    fun close() = stopListening()

    private suspend fun evaluate(): Boolean {
        val info = updates.fetch() ?: return false
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            log.lastCheckedAt = now()
            _state.value = UpdateUiState.ReadyToRestart
            return true
        }
        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
            log.lastCheckedAt = now()
            return false
        }
        val urgent = info.updatePriority() >= URGENT_PRIORITY
        if (urgent && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            if (busy()) return false
            log.lastCheckedAt = now()
            return startImmediate(info)
        }
        log.lastCheckedAt = now()
        if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return false
        return updates.start(info, AppUpdateType.FLEXIBLE)
    }

    private fun startImmediate(info: AppUpdateInfo): Boolean {
        _state.value = UpdateUiState.Required
        val started = updates.start(info, AppUpdateType.IMMEDIATE)
        if (!started) _state.value = UpdateUiState.None
        return started
    }

    private fun onInstallState(install: InstallState) {
        _state.value = when (install.installStatus()) {
            InstallStatus.PENDING -> UpdateUiState.Downloading(null)
            InstallStatus.DOWNLOADING -> {
                val total = install.totalBytesToDownload()
                UpdateUiState.Downloading(
                    if (total > 0) (install.bytesDownloaded().toFloat() / total).coerceIn(0f, 1f) else null,
                )
            }
            InstallStatus.DOWNLOADED -> UpdateUiState.ReadyToRestart
            InstallStatus.INSTALLING -> return
            else -> UpdateUiState.None
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val URGENT_PRIORITY = 4
    }
}
