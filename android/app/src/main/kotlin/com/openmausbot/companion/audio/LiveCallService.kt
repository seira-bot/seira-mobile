package com.openmausbot.companion.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.openmausbot.companion.MainActivity
import com.openmausbot.companion.OpenMausApp
import com.openmausbot.companion.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps a live call going after the app leaves the screen. A foreground
 * service of type `microphone` holds the process and the capture, and an
 * ongoing call notification says so, with Hang up. The call itself stays in
 * [LiveCallManager]; this only keeps it alive and shows it.
 *
 * Android 14 and later accept a `microphone` foreground service only while
 * the app is visible, so [LiveCallManager] starts it with the call, and a
 * start the system refuses leaves the call to end when the app is stopped.
 */
class LiveCallService : Service() {
    /** What the service needs from the call. [LiveCallManager] is the production host. */
    interface Host {
        val state: StateFlow<LiveCallSnapshot>

        fun hangUp()

        /** The service could not become a foreground service. */
        fun onBackgroundFailed()
    }

    private var scope: CoroutineScope? = null
    private var watching: Job? = null
    private var avatarFor: Job? = null
    private var avatar: Bitmap? = null
    private var avatarBotId: String? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = resolveHost()
        if (host == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_HANG_UP) {
            host.hangUp()
            if (!foreground) stopSelf()
            return START_NOT_STICKY
        }
        if (!foreground && !promote(host)) return START_NOT_STICKY
        if (watching == null) watching = scope?.launch { host.state.collect { onState(host, it) } }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        watching?.cancel()
        avatarFor?.cancel()
        scope?.cancel()
        scope = null
        watching = null
        super.onDestroy()
    }

    private fun resolveHost(): Host? = host ?: (application as? OpenMausApp)?.liveCalls

    private fun promote(host: Host): Boolean {
        val notification = buildNotification(host.state.value)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (_: SecurityException) {
            return fail(host)
        } catch (_: IllegalStateException) {
            return fail(host)
        }
        foreground = true
        return true
    }

    private fun fail(host: Host): Boolean {
        host.onBackgroundFailed()
        stopSelf()
        return false
    }

    private fun onState(host: Host, snapshot: LiveCallSnapshot) {
        if (!snapshot.holdsMedia) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
            return
        }
        val botId = snapshot.botId
        if (botId != null && botId != avatarBotId) {
            avatarBotId = botId
            avatar = null
            avatarFor?.cancel()
            avatarFor = scope?.launch {
                avatar = avatarLoader(this@LiveCallService, botId)
                if (foreground) notify(host.state.value)
            }
        }
        notify(snapshot)
    }

    private fun notify(snapshot: LiveCallSnapshot) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: LiveCallSnapshot): Notification {
        val hangUp = PendingIntent.getService(
            this,
            REQUEST_HANG_UP,
            Intent(this, LiveCallService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val bot = Person.Builder()
            .setName(snapshot.botName.ifBlank { getString(R.string.call_service_live) })
            .setImportant(true)
            .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()
        val live = snapshot.phase == LiveCallPhase.LIVE
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_seira_mark)
            .setContentText(getString(if (live) R.string.call_service_live else R.string.call_service_connecting))
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(bot, hangUp))
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                val since = snapshot.liveSince
                if (live && since != null) setUsesChronometer(true).setWhen(since)
            }
            .build()
    }

    companion object {
        const val CHANNEL_ID = "live_calls"
        const val NOTIFICATION_ID = 2
        const val ACTION_HANG_UP = "com.openmausbot.companion.livecall.HANG_UP"
        private const val REQUEST_HANG_UP = 1
        private const val REQUEST_OPEN = 2

        /** Replaces the app's call manager; a test sets it. */
        @Volatile
        internal var host: Host? = null

        /** The bot's face for the notification, or null. Replaced by a test. */
        @Volatile
        internal var avatarLoader: suspend (Context, String) -> Bitmap? = { context, botId ->
            val app = context.applicationContext as? OpenMausApp
            val bot = app?.session?.state?.value?.bot(botId)
            if (app == null || bot == null) null else app.avatars.bitmapFor(bot)
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveCallService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveCallService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val system = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.call_service_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.call_service_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            system.createNotificationChannel(channel)
        }
    }
}

/** Starts and stops the call's foreground service; [None] keeps the old behaviour of ending the call in the background. */
interface LiveCallBackground {
    /** True when the service was asked to start and the system did not refuse. */
    fun start(): Boolean

    fun stop()

    object None : LiveCallBackground {
        override fun start(): Boolean = false

        override fun stop() = Unit
    }
}

internal class ServiceLiveCallBackground(private val context: Context) : LiveCallBackground {
    override fun start(): Boolean = try {
        LiveCallService.start(context)
        true
    } catch (_: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException is one, API 31.
        false
    } catch (_: SecurityException) {
        false
    }

    override fun stop() = LiveCallService.stop(context)
}
