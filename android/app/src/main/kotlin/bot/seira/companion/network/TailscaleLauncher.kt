package bot.seira.companion.network

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object TailscaleLauncher {
    const val PACKAGE = "com.tailscale.ipn"

    internal val storeUrls = listOf(
        "market://details?id=$PACKAGE",
        "https://play.google.com/store/apps/details?id=$PACKAGE",
    )

    /** The intents to try in order: the app itself, then the store app, then the store page. */
    internal fun intents(context: Context): List<Intent> {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        val store = storeUrls.map { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
        return (listOfNotNull(launch) + store).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    /** True when some intent opened. */
    fun open(context: Context): Boolean {
        for (intent in intents(context)) {
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }
}
