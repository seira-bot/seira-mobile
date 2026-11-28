package bot.seira.companion.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import bot.seira.companion.SeiraApp

class PushMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        (application as? SeiraApp)?.pushDelivery?.receive(message.data)
    }

    override fun onNewToken(token: String) {
        (application as? SeiraApp)?.session?.setPushToken(token)
    }
}
