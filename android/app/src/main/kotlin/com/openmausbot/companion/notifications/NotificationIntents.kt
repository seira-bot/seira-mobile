package com.openmausbot.companion.notifications

import android.content.Intent
import android.net.Uri
import com.openmausbot.companion.MainActivity
import com.openmausbot.companion.core.NotificationFrame
import com.openmausbot.companion.core.NotificationTarget

/**
 * The two ids a notification tap carries — the same pair iOS reads from
 * `userInfo` in `Notifications.swift:65-69`. Both are required; a partial
 * extra is not a destination.
 */
fun Intent.notificationTarget(): NotificationTarget? =
    NotificationTarget.from(
        getStringExtra(LocalNotificationPoster.EXTRA_BOT_ID),
        getStringExtra(LocalNotificationPoster.EXTRA_THREAD_ID),
        getStringExtra(LocalNotificationPoster.EXTRA_PAIRING),
    )

/**
 * Filterable identity for notification content Intents.
 *
 * PendingIntent equality uses request code plus Intent.filterEquals fields
 * (action, data, type, package, component, categories) — **extras do not
 * participate**. Putting `botId` and `threadId` only in extras lets two
 * notifications for the same room thread, or two thread ids whose
 * `String.hashCode()` collide, share one PendingIntent and overwrite each
 * other's target under `FLAG_UPDATE_CURRENT`.
 *
 * The data URI below is the unambiguous discriminator; it is never hashed.
 */
object NotificationIntents {
    const val ACTION_OPEN = "com.openmausbot.companion.OPEN_NOTIFICATION"

    /**
     * Stable, unhashed identity string for [android.content.Intent.setData].
     * Distinct for every `(botId, threadId)` pair — including UUID strings
     * that collide under 32-bit [String.hashCode].
     */
    fun contentIdentity(botId: String, threadId: String, pairing: String? = null): String =
        "openmaus://notification/${encode(botId)}/${encode(threadId)}" +
            (pairing?.let { "?pairing=${encode(it)}" } ?: "")

    fun contentUri(botId: String, threadId: String, pairing: String? = null): Uri =
        Uri.parse(contentIdentity(botId, threadId, pairing))

    const val EXTRA_SHORTCUT_ID = "openmaus.shortcutId"

    fun contentIntent(
        packageContext: android.content.Context,
        notification: NotificationFrame,
        pairing: String? = null,
    ): Intent = openIntent(
        packageContext,
        notification.botId,
        notification.threadId,
        notification.kind,
        pairing,
    )

    fun openIntent(
        packageContext: android.content.Context,
        botId: String,
        threadId: String,
        kind: String? = null,
        pairing: String? = null,
    ): Intent = Intent(packageContext, MainActivity::class.java).apply {
        action = ACTION_OPEN
        data = contentUri(botId, threadId, pairing)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(LocalNotificationPoster.EXTRA_THREAD_ID, threadId)
        putExtra(LocalNotificationPoster.EXTRA_BOT_ID, botId)
        if (kind != null) putExtra(LocalNotificationPoster.EXTRA_KIND, kind)
        if (pairing != null) putExtra(LocalNotificationPoster.EXTRA_PAIRING, pairing)
    }

    fun openPending(
        packageContext: android.content.Context,
        botId: String,
        threadId: String,
        kind: String? = null,
        pairing: String? = null,
    ): android.app.PendingIntent = android.app.PendingIntent.getActivity(
        packageContext,
        0,
        openIntent(packageContext, botId, threadId, kind, pairing),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
    )

    /** Android's URI encoder is available throughout this app's API range. */
    private fun encode(value: String): String = Uri.encode(value)
}
