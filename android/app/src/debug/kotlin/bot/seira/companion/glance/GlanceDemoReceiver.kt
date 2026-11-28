package bot.seira.companion.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.app.NotificationManagerCompat
import bot.seira.companion.core.GlanceAsk
import bot.seira.companion.core.GlanceAskKind
import bot.seira.companion.core.GlanceBot
import bot.seira.companion.core.GlanceEntry
import bot.seira.companion.core.GlanceState
import bot.seira.companion.core.SeiraCast
import bot.seira.companion.character.SeiraAssets
import bot.seira.companion.character.SeiraCharacterSnapshot
import bot.seira.companion.ui.CharacterMood

/**
 * Debug builds only. Posts a sample Glance notification for screenshots. It reads no session and uses no network.
 * Optional string extras: `name`, `detail`, `line`, and `avatar` (a cast member id).
 */
class GlanceDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        val state = intent.getStringExtra("state").orEmpty()
        if (state == "clear") {
            manager.cancel(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID)
            return
        }
        val name = intent.getStringExtra("name") ?: "Nara"
        val avatar = intent.getStringExtra("avatar")?.let { avatarFor(context, it) }
        val budget = GlanceNotification.budget(context.resources.configuration.fontScale)
        fun approval(detail: String) =
            GlanceAsk(GlanceAskKind.APPROVAL, "demo-request", "demo-thread", "Approval needed", detail, 1.0)
        fun bot(s: GlanceState, line: String = "", ask: GlanceAsk? = null) =
            GlanceEntry(GlanceBot("demo-bot", "demo-thread", name, null, s, line, ask), 0L)
        val entry = when (state) {
            "working" -> bot(GlanceState.WORKING, intent.getStringExtra("line") ?: "Checking the login form against the new tests")
            "needs" -> bot(GlanceState.NEEDS, ask = approval(intent.getStringExtra("detail") ?: "npm test -- --filter login"))
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
        val notification = GlanceNotification.build(context, entry, avatar, budget, couldNotSend = state == "could_not_send")
        try {
            manager.notify(GlanceNotification.TAG, GlanceNotification.NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    /** The cast portrait of [id] on a round background in the member's colour; null for an unknown id. */
    private fun avatarFor(context: Context, id: String): Bitmap? {
        val member = SeiraCast.decode(SeiraAssets.forContext(context).castJson()).firstOrNull { it.id == id } ?: return null
        val size = 256
        val portrait = SeiraCharacterSnapshot.render(
            context, member.mascotBody, member.color, member.mascotAccessories, null, size, CharacterMood.IDLE, 0f,
        )
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xB8, 0xE2, 0xEE) }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        canvas.drawBitmap(portrait, 0f, 0f, null)
        return out
    }
}
