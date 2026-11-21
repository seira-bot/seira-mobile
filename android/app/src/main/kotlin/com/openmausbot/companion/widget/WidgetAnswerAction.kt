package com.openmausbot.companion.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.openmausbot.companion.OpenMausApp
import com.openmausbot.companion.R
import com.openmausbot.companion.notifications.ApproveRequest
import com.openmausbot.companion.notifications.NotificationApproval

/**
 * Approves the ask on a widget button.
 *
 * Security invariant: a widget is a frozen picture. The tap answers only when the
 * snapshot is under ten minutes old and still holds this exact request and
 * option, and the live session still holds the same plain permission card.
 * Anything else changes nothing and sends the person to the chat.
 */
class WidgetAnswerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val threadId = parameters[ThreadKey] ?: return
        val botId = parameters[BotKey] ?: return
        val requestId = parameters[RequestKey] ?: return
        val choice = parameters[ChoiceKey] ?: return
        val app = context.applicationContext as? OpenMausApp ?: return
        val store = WidgetSnapshotStore.forContext(context)
        val snapshot = store.read()
        val row = snapshot?.answerableRow(threadId, requestId, choice, System.currentTimeMillis())
        val found = row?.let {
            NotificationApproval.find(app.session.state.value, botId, threadId, requestId)
        }
        if (snapshot == null || row == null || found == null || found.choice != choice) {
            toast(context, R.string.widget_request_changed)
            UpdatesWidget().updateAll(context)
            return
        }
        val answered = app.notificationActions.approve(
            ApproveRequest(
                tag = "widget:$threadId:$requestId",
                botId = botId,
                threadId = threadId,
                requestId = requestId,
                title = found.card.title,
            ),
        )
        if (answered) store.write(snapshot.removingRow(threadId))
        UpdatesWidget().updateAll(context)
    }

    private fun toast(context: Context, message: Int) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    companion object {
        val ThreadKey = ActionParameters.Key<String>("thread")
        val BotKey = ActionParameters.Key<String>("bot")
        val RequestKey = ActionParameters.Key<String>("request")
        val ChoiceKey = ActionParameters.Key<String>("choice")
    }
}
