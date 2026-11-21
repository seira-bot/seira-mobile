package com.openmausbot.companion.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.openmausbot.companion.OpenMausApp

class PushMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        (application as? OpenMausApp)?.pushDelivery?.receive(message.data)
    }

    override fun onNewToken(token: String) {
        (application as? OpenMausApp)?.session?.setPushToken(token)
    }
}
