package bot.seira.companion.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import bot.seira.companion.BuildConfig
import bot.seira.companion.storage.AlwaysOnPreferences

/**
 * Restarts [AlwaysOnConnectionService] after a reboot, if the user had it
 * enabled. Android does not carry a running foreground service across a
 * restart on its own — without this, always-on mode would silently stop
 * working every time the phone rebooted until the user next opened the app.
 */
class AlwaysOnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.ALWAYS_ON) return
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (AlwaysOnPreferences(context).enabled.value) {
            AlwaysOnConnectionService.start(context)
        }
    }
}
