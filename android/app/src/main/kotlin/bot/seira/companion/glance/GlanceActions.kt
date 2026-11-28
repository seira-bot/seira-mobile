package bot.seira.companion.glance

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import bot.seira.companion.notifications.LocalNotificationPoster

/** The Deny and Allow once buttons of the Glance notification. */
object GlanceActions {
    enum class Choice(val action: String, val path: String) {
        DENY("bot.seira.companion.action.GLANCE_DENY", "deny"),
        ALLOW("bot.seira.companion.action.GLANCE_ALLOW", "allow"),
    }

    data class Request(val choice: Choice, val botId: String, val threadId: String, val requestId: String)

    private const val EXTRA_REQUEST_ID = "openmaus.glance.requestId"

    fun intent(context: Context, request: Request): Intent =
        Intent(context, GlanceActionReceiver::class.java).apply {
            action = request.choice.action
            data = Uri.parse(
                "openmaus://glance/${request.choice.path}/${Uri.encode(request.botId)}/" +
                    "${Uri.encode(request.threadId)}/${Uri.encode(request.requestId)}",
            )
            putExtra(LocalNotificationPoster.EXTRA_BOT_ID, request.botId)
            putExtra(LocalNotificationPoster.EXTRA_THREAD_ID, request.threadId)
            putExtra(EXTRA_REQUEST_ID, request.requestId)
        }

    fun request(intent: Intent): Request? {
        val choice = Choice.entries.firstOrNull { it.action == intent.action } ?: return null
        val botId = intent.getStringExtra(LocalNotificationPoster.EXTRA_BOT_ID)?.takeIf { it.isNotBlank() } ?: return null
        val threadId = intent.getStringExtra(LocalNotificationPoster.EXTRA_THREAD_ID)?.takeIf { it.isNotBlank() } ?: return null
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)?.takeIf { it.isNotBlank() } ?: return null
        return Request(choice, botId, threadId, requestId)
    }

    fun answerAction(
        context: Context,
        label: String,
        choice: Choice,
        botId: String,
        threadId: String,
        requestId: String,
    ): NotificationCompat.Action {
        val request = Request(choice, botId, threadId, requestId)
        val pending = PendingIntent.getBroadcast(
            context,
            "${choice.path}\n$botId\n$threadId\n$requestId".hashCode(),
            intent(context, request),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, label, pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_NONE)
            .setShowsUserInterface(false)
            .setAuthenticationRequired(choice == Choice.ALLOW)
            .build()
    }
}
