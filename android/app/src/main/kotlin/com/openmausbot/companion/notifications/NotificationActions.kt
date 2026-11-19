package com.openmausbot.companion.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/** Requests that a notification action sends to [NotificationActionReceiver]. */
data class ApproveRequest(
    val tag: String,
    val botId: String,
    val threadId: String,
    val requestId: String,
    val title: String,
)

data class ReplyRequest(
    val tag: String,
    val botId: String,
    val threadId: String,
    val botName: String,
)

object NotificationActions {
    const val ACTION_APPROVE = "bot.seira.companion.action.APPROVE"
    const val ACTION_REPLY = "bot.seira.companion.action.REPLY"
    const val KEY_REPLY = "openmaus.reply"

    private const val EXTRA_TAG = "openmaus.notificationTag"
    private const val EXTRA_REQUEST_ID = "openmaus.requestId"
    private const val EXTRA_TITLE = "openmaus.title"
    private const val EXTRA_BOT_NAME = "openmaus.botName"

    fun approveIntent(context: Context, request: ApproveRequest): Intent =
        Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_APPROVE
            data = Uri.parse("openmaus://approve/${Uri.encode(request.tag)}")
            putExtra(EXTRA_TAG, request.tag)
            putExtra(LocalNotificationPoster.EXTRA_BOT_ID, request.botId)
            putExtra(LocalNotificationPoster.EXTRA_THREAD_ID, request.threadId)
            putExtra(EXTRA_REQUEST_ID, request.requestId)
            putExtra(EXTRA_TITLE, request.title)
        }

    fun replyIntent(context: Context, request: ReplyRequest): Intent =
        Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REPLY
            data = Uri.parse("openmaus://reply/${Uri.encode(request.tag)}")
            putExtra(EXTRA_TAG, request.tag)
            putExtra(LocalNotificationPoster.EXTRA_BOT_ID, request.botId)
            putExtra(LocalNotificationPoster.EXTRA_THREAD_ID, request.threadId)
            putExtra(EXTRA_BOT_NAME, request.botName)
        }

    fun approveRequest(intent: Intent): ApproveRequest? {
        val tag = intent.getStringExtra(EXTRA_TAG)?.takeIf { it.isNotBlank() } ?: return null
        val botId = intent.getStringExtra(LocalNotificationPoster.EXTRA_BOT_ID)?.takeIf { it.isNotBlank() } ?: return null
        val threadId = intent.getStringExtra(LocalNotificationPoster.EXTRA_THREAD_ID)?.takeIf { it.isNotBlank() } ?: return null
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)?.takeIf { it.isNotBlank() } ?: return null
        return ApproveRequest(tag, botId, threadId, requestId, intent.getStringExtra(EXTRA_TITLE).orEmpty())
    }

    fun replyRequest(intent: Intent): ReplyRequest? {
        val tag = intent.getStringExtra(EXTRA_TAG)?.takeIf { it.isNotBlank() } ?: return null
        val botId = intent.getStringExtra(LocalNotificationPoster.EXTRA_BOT_ID)?.takeIf { it.isNotBlank() } ?: return null
        val threadId = intent.getStringExtra(LocalNotificationPoster.EXTRA_THREAD_ID)?.takeIf { it.isNotBlank() } ?: return null
        return ReplyRequest(tag, botId, threadId, intent.getStringExtra(EXTRA_BOT_NAME).orEmpty())
    }

    fun replyText(intent: Intent): String? =
        RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** One request code per notification; the data URI keeps colliding codes apart. */
    private fun requestCode(tag: String, salt: Int): Int = tag.hashCode() * 31 + salt

    fun approveAction(context: Context, label: String, request: ApproveRequest): NotificationCompat.Action {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(request.tag, 1),
            approveIntent(context, request),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, label, pending)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_NONE)
            .setShowsUserInterface(false)
            .setAuthenticationRequired(true)
            .build()
    }

    fun reviewAction(context: Context, label: String, botId: String, threadId: String, kind: String?): NotificationCompat.Action =
        NotificationCompat.Action.Builder(0, label, NotificationIntents.openPending(context, botId, threadId, kind))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_NONE)
            .setShowsUserInterface(true)
            .build()

    fun replyAction(context: Context, label: String, request: ReplyRequest): NotificationCompat.Action {
        // RemoteInput needs a mutable PendingIntent so the system can attach the typed text.
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(request.tag, 2),
            replyIntent(context, request),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val input = RemoteInput.Builder(KEY_REPLY).setLabel(label).build()
        return NotificationCompat.Action.Builder(0, label, pending)
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
    }
}
