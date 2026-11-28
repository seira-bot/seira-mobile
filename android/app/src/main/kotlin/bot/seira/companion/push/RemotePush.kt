package bot.seira.companion.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import bot.seira.companion.BuildConfig
import bot.seira.companion.core.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds the push token while a computer is paired. Calls no Firebase API in a
 * build made without `google-services.json`.
 */
class RemotePush(
    private val context: Context,
    private val session: Session,
    private val scope: CoroutineScope,
) {
    fun start() {
        if (!BuildConfig.REMOTE_PUSH) return
        scope.launch {
            session.awaitRestored()
            var wasPaired = false
            session.connections
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { paired ->
                    if (paired) enable() else disable(releaseToken = wasPaired)
                    wasPaired = paired
                }
        }
    }

    private fun messaging(): FirebaseMessaging? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseMessaging.getInstance()

    private fun enable() {
        val messaging = messaging() ?: return
        messaging.isAutoInitEnabled = true
        messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) session.setPushToken(task.result)
        }
    }

    private fun disable(releaseToken: Boolean) {
        val messaging = messaging() ?: return
        messaging.isAutoInitEnabled = false
        if (releaseToken) {
            messaging.deleteToken()
            session.setPushToken(null)
        }
    }
}
