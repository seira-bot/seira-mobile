package com.openmausbot.companion.glance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import com.openmausbot.companion.R
import com.openmausbot.companion.core.GlanceAsk
import com.openmausbot.companion.core.GlanceAskKind
import com.openmausbot.companion.core.GlanceBudget
import com.openmausbot.companion.core.GlanceEntry
import com.openmausbot.companion.core.GlanceState
import com.openmausbot.companion.notifications.NotificationActions
import com.openmausbot.companion.notifications.NotificationIntents
import com.openmausbot.companion.notifications.NotificationText

/** Builds the one ongoing Glance notification. Pure: it reads nothing but its arguments. */
object GlanceNotification {
    const val CHANNEL_ID = "glance"
    const val TAG = "glance"
    const val NOTIFICATION_ID = 4101

    private const val LINE_CHARS = 30
    private const val LINES = 6
    private const val MIN_LINE_CHARS = 10
    private const val SHADE_MARGIN_DP = 128

    /** The text one Glance notification shows without a cut. */
    fun budget(fontScale: Float): GlanceBudget =
        GlanceBudget((LINE_CHARS / fontScale.coerceAtLeast(0.5f)).toInt().coerceAtLeast(MIN_LINE_CHARS), LINES)

    /**
     * Security: Allow once shows only when the char budget passes and a real layout of the exact
     * big text, at 14sp in the narrow shade, fits [LINES] lines. The budget alone is a pre-filter.
     */
    fun canAllow(context: Context, ask: GlanceAsk, budget: GlanceBudget, widthPx: Int = shadeWidthPx(context)): Boolean =
        ask.canAllow(budget) && fitsLayout(context, ask.title + "\n" + ask.detail, widthPx)

    fun fitsLayout(context: Context, text: String, widthPx: Int = shadeWidthPx(context)): Boolean {
        if (widthPx <= 0) return false
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx).build()
        return layout.lineCount <= LINES
    }

    private fun shadeWidthPx(context: Context): Int {
        val configuration = context.resources.configuration
        val dp = minOf(configuration.screenWidthDp, configuration.screenHeightDp) - SHADE_MARGIN_DP
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        if (system.getNotificationChannel(CHANNEL_ID) != null) return
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.glance_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.glance_channel_description)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            },
        )
    }

    fun stateWord(context: Context, state: GlanceState): String = context.getString(
        when (state) {
            GlanceState.WORKING -> R.string.glance_working
            GlanceState.NEEDS -> R.string.glance_needs_you
            GlanceState.QUESTION -> R.string.glance_question
            GlanceState.FAILED -> R.string.glance_failed
            GlanceState.DONE -> R.string.glance_finished
        },
    )

    fun build(
        context: Context,
        entry: GlanceEntry,
        avatar: Bitmap?,
        budget: GlanceBudget,
        couldNotSend: Boolean,
        promotionSdk: Int = Build.VERSION.SDK_INT,
    ): Notification {
        ensureChannel(context)
        val bot = entry.bot
        val ask = bot.ask
        val word = stateWord(context, bot.state)
        val text = when {
            couldNotSend -> context.getString(R.string.glance_could_not_send)
            bot.state == GlanceState.WORKING -> bot.line.ifBlank { word }
            else -> word
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_seira_mark)
            .setContentTitle(NotificationText.anchored(bot.name))
            .setContentText(NotificationText.anchored(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(
                if (bot.state == GlanceState.WORKING) NotificationCompat.CATEGORY_PROGRESS else NotificationCompat.CATEGORY_STATUS,
            )
            .setContentIntent(NotificationIntents.openPending(context, bot.botId, bot.threadId, kindOf(ask)))
        if (avatar != null) builder.setLargeIcon(avatar)

        val showAsk = ask != null && !couldNotSend && bot.state == GlanceState.NEEDS
        if (couldNotSend) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(NotificationText.anchored(text)))
        } else if (showAsk) {
            val allow = canAllow(context, ask!!, budget)
            val body = if (allow) ask.title + "\n" + ask.detail else ask.title
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(NotificationText.anchored(body)))
        } else if (bot.state == GlanceState.WORKING) {
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setStyle(NotificationCompat.ProgressStyle().setProgressIndeterminate(true))
            } else {
                builder.setProgress(0, 0, true)
            }
        }

        if (showAsk && ask!!.canDeny) {
            builder.addAction(
                GlanceActions.answerAction(
                    context, context.getString(R.string.glance_deny), GlanceActions.Choice.DENY,
                    bot.botId, bot.threadId, ask.requestId,
                ),
            )
            if (canAllow(context, ask, budget)) {
                builder.addAction(
                    GlanceActions.answerAction(
                        context, context.getString(R.string.glance_allow_once), GlanceActions.Choice.ALLOW,
                        bot.botId, bot.threadId, ask.requestId,
                    ),
                )
            }
        }
        builder.addAction(
            NotificationActions.reviewAction(
                context, context.getString(R.string.glance_open_chat), bot.botId, bot.threadId, kindOf(ask),
            ),
        )

        if (promotionSdk >= 36) {
            builder.setRequestPromotedOngoing(true).setShortCriticalText(word)
        }
        return builder.build()
    }

    private fun kindOf(ask: GlanceAsk?): String? = when (ask?.kind) {
        GlanceAskKind.APPROVAL -> "approval"
        GlanceAskKind.QUESTION -> "question"
        else -> null
    }
}
