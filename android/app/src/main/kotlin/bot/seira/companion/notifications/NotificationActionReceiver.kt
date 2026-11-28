package bot.seira.companion.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import bot.seira.companion.SeiraApp
import kotlinx.coroutines.launch

/** Runs the Approve and Reply actions of a notification. Not exported: only this app's PendingIntents reach it. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? SeiraApp ?: return
        val pending: PendingResult? = goAsync()
        app.appScope.launch {
            try {
                app.notificationActions.handle(intent)
            } finally {
                pending?.finish()
            }
        }
    }
}
