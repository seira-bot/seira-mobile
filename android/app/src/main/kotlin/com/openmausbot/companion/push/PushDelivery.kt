package com.openmausbot.companion.push

import com.openmausbot.companion.core.PushAlert
import com.openmausbot.companion.core.Session
import com.openmausbot.companion.notifications.LocalNotificationPoster

/**
 * Turns a data message into a notification. Never opens the stream: the push
 * exists for the time the app is closed.
 */
class PushDelivery(
    private val session: Session,
    private val poster: LocalNotificationPoster,
) {
    fun receive(data: Map<String, String>) {
        val alert = PushAlert.parse(data) ?: return
        if (alert.pairing?.let(session::holdsLiveStream) == true) return
        poster.deliverPush(alert)
    }
}
