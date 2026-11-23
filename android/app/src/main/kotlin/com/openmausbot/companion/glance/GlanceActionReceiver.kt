package com.openmausbot.companion.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openmausbot.companion.OpenMausApp
import kotlinx.coroutines.launch

/** Runs the Deny and Allow once buttons of Glance. Not exported: only this app's PendingIntents reach it. */
class GlanceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? OpenMausApp ?: return
        val request = GlanceActions.request(intent) ?: return
        val pending: PendingResult? = goAsync()
        app.appScope.launch {
            try {
                app.glanceActions.handle(request)
            } finally {
                pending?.finish()
            }
        }
    }
}
