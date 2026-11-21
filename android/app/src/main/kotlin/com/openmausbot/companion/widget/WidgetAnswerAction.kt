package com.openmausbot.companion.widget

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.openmausbot.companion.OpenMausApp
import com.openmausbot.companion.R
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.notifications.ApproveRequest
import com.openmausbot.companion.notifications.NotificationApproval
import com.openmausbot.companion.notifications.NotificationIntents

/**
 * Approves the ask on a widget button.
 *
 * Security invariants: a widget is a frozen picture, and it can show on a locked
 * screen. A tap answers only when the phone is unlocked, the snapshot is under
 * ten minutes old, the row holds this exact request and option with its whole
 * text shown, and the live session still holds the same plain permission card
 * with the same whole text.
 * Anything else changes nothing and opens the chat, where the system asks to
 * unlock.
 */
class WidgetAnswerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as? OpenMausApp ?: return
        val locked = context.getSystemService(KeyguardManager::class.java)
            ?.let { it.isKeyguardLocked || it.isDeviceLocked } ?: true
        WidgetAnswerHandler(
            context = context,
            store = WidgetSnapshotStore.forContext(context),
            clock = System::currentTimeMillis,
            isLocked = { locked },
            state = { app.session.state.value },
            approve = { app.notificationActions.approve(it) },
            startActivity = { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            tell = { message ->
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            redraw = { UpdatesWidget().updateAll(context) },
        ).onTap(
            threadId = parameters[ThreadKey] ?: return,
            botId = parameters[BotKey] ?: return,
            requestId = parameters[RequestKey] ?: return,
            choice = parameters[ChoiceKey] ?: return,
        )
    }

    companion object {
        val ThreadKey = ActionParameters.Key<String>("thread")
        val BotKey = ActionParameters.Key<String>("bot")
        val RequestKey = ActionParameters.Key<String>("request")
        val ChoiceKey = ActionParameters.Key<String>("choice")
    }
}

internal class WidgetAnswerHandler(
    private val context: Context,
    private val store: WidgetSnapshotStore,
    private val clock: () -> Long,
    private val isLocked: () -> Boolean,
    private val state: () -> CompanionState,
    private val approve: suspend (ApproveRequest) -> Boolean,
    private val startActivity: (Intent) -> Unit,
    private val tell: (Int) -> Unit,
    private val redraw: suspend () -> Unit,
) {
    suspend fun onTap(threadId: String, botId: String, requestId: String, choice: String) {
        val snapshot = store.read()
        if (isLocked()) {
            startActivity(NotificationIntents.openIntent(context, botId, threadId, "needsYou"))
            return
        }
        val row = snapshot?.answerableRow(threadId, requestId, choice, clock())
        val found = row?.let { NotificationApproval.find(state(), botId, threadId, requestId) }
        val cardText = found?.card?.requestText()
        if (found != null && row != null &&
            (cardText != row.line.trim() || !WidgetSnapshot.isFullyShown(cardText))
        ) {
            startActivity(NotificationIntents.openIntent(context, botId, threadId, "needsYou"))
            return
        }
        if (snapshot == null || row == null || found == null || found.choice != choice) {
            tell(R.string.widget_request_changed)
            redraw()
            return
        }
        val answered = approve(
            ApproveRequest(
                tag = "widget:$threadId:$requestId",
                botId = botId,
                threadId = threadId,
                requestId = requestId,
                title = found.card.title,
            ),
        )
        if (answered) store.write(snapshot.removingRow(threadId))
        redraw()
    }
}
