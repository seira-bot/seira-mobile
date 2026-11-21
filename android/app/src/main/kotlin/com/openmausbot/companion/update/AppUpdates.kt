package com.openmausbot.companion.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** The part of Google Play's update API that [AppUpdateController] uses. */
interface AppUpdates {
    /** The offer Play holds for this app, or null when Play cannot answer. */
    suspend fun fetch(): AppUpdateInfo?

    fun start(info: AppUpdateInfo, type: Int): Boolean

    fun complete()

    /** Returns the function that removes the listener. */
    fun listen(onState: (InstallState) -> Unit): () -> Unit
}

class PlayAppUpdates(
    private val manager: AppUpdateManager,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>,
) : AppUpdates {
    override suspend fun fetch(): AppUpdateInfo? = suspendCancellableCoroutine { continuation ->
        runCatching {
            manager.appUpdateInfo
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        }.onFailure { if (continuation.isActive) continuation.resume(null) }
    }

    override fun start(info: AppUpdateInfo, type: Int): Boolean = runCatching {
        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(type).build())
    }.getOrDefault(false)

    override fun complete() {
        runCatching { manager.completeUpdate() }
    }

    override fun listen(onState: (InstallState) -> Unit): () -> Unit {
        val listener = InstallStateUpdatedListener { onState(it) }
        runCatching { manager.registerListener(listener) }
        return { runCatching { manager.unregisterListener(listener) } }
    }
}

object InstallSource {
    private const val PLAY_STORE = "com.android.vending"

    fun isGooglePlay(context: Context): Boolean = runCatching {
        val packages = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            packages.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packages.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE
    }.getOrDefault(false)
}
