package com.openmausbot.companion.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.openmausbot.companion.core.GlanceAsk
import com.openmausbot.companion.core.GlanceAskKind
import com.openmausbot.companion.core.GlanceBot
import com.openmausbot.companion.core.GlanceEntry
import com.openmausbot.companion.core.GlanceState

/** Debug builds only. Posts a sample Glance notification for screenshots. It reads no session and uses no network. */
class GlanceDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        val state = intent.getStringExtra("state").orEmpty()
        if (state == "clear") {
            manager.cancel(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID)
            return
        }
        val budget = GlanceNotification.budget(context.resources.configuration.fontScale)
        fun approval(detail: String) =
            GlanceAsk(GlanceAskKind.APPROVAL, "demo-request", "demo-thread", "Approval needed", detail, 1.0)
        fun bot(s: GlanceState, line: String = "", ask: GlanceAsk? = null) =
            GlanceEntry(GlanceBot("demo-bot", "demo-thread", "Nara", null, s, line, ask), 0L)
        val entry = when (state) {
            "working" -> bot(GlanceState.WORKING, "Checking the login form against the new tests")
            "needs" -> bot(GlanceState.NEEDS, ask = approval("npm test -- --filter login"))
            "needs_long" -> bot(GlanceState.NEEDS, ask = approval("echo " + "long command ".repeat(46)))
            "question" -> bot(
                GlanceState.QUESTION,
                ask = GlanceAsk(GlanceAskKind.QUESTION, "demo-request", "demo-thread", "Which branch?", "", 1.0),
            )
            "failed" -> bot(GlanceState.FAILED)
            "done" -> bot(GlanceState.DONE)
            "could_not_send" -> bot(GlanceState.NEEDS, ask = approval("npm test -- --filter login"))
            else -> return
        }
        val notification = GlanceNotification.build(context, entry, null, budget, couldNotSend = state == "could_not_send")
        try {
            manager.notify(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
