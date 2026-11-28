package bot.seira.companion.glance

import bot.seira.companion.core.Glance
import bot.seira.companion.core.GlanceAskKind
import bot.seira.companion.core.GlanceBudget
import bot.seira.companion.core.Message
import bot.seira.companion.core.Session
import bot.seira.companion.notifications.NotificationApproval
import bot.seira.companion.notifications.notificationChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers Deny and Allow once from the Glance notification through the chat card's own answer path.
 *
 * Security invariant: an allow from Glance answers only the current plain permission
 * request whose whole text the notification showed. It never remembers the permission.
 */
class GlanceActionHandler(
    private val context: android.content.Context,
    private val session: Session,
    private val notifier: GlanceNotifier,
    private val budget: () -> GlanceBudget,
    private val readyWaitMs: Long = READY_WAIT_MS,
) {
    /** True when the answer landed. */
    suspend fun handle(request: GlanceActions.Request): Boolean {
        try {
            if (!awaitLive()) {
                notifier.markFailed(request.requestId)
                return false
            }
            val state = session.state.value
            val ask = Glance.currentAsk(state, request.botId, request.requestId) ?: return false
            if (ask.kind != GlanceAskKind.APPROVAL || ask.threadId != request.threadId) return false
            val allow = request.choice == GlanceActions.Choice.ALLOW
            if (allow && !GlanceNotification.canAllow(context, ask, budget())) return false

            val pending = state.messages[request.threadId].orEmpty()
                .filter { it.kind == Message.Kind.OPTIONS && it.card?.isPending == true && it.card?.requestId == request.requestId }
                .singleOrNull()?.card ?: return false
            if (!Glance.isPlainPermission(pending)) return false
            val chat = state.notificationChat(request.botId, request.threadId) ?: return false
            val choice = if (allow) {
                NotificationApproval.find(state, request.botId, request.threadId, request.requestId)?.choice
            } else {
                pending.options.firstOrNull { pending.responseBehavior(it) == "deny" }
            } ?: return false

            session.actionError = null
            val answered = session.answer(chat, pending, choice, rememberingPermission = false)
            val landed = session.actionError == null && session.status.value !is Session.Status.Unauthorized
            session.actionError = null
            if (!landed) {
                notifier.markFailed(request.requestId)
                return false
            }
            return answered != OUTCOME_UNAVAILABLE
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            notifier.markFailed(request.requestId)
            return false
        }
    }

    private suspend fun awaitLive(): Boolean {
        session.awaitRestored()
        if (session.connection.value == null) return false
        session.connect()
        val status = withTimeoutOrNull(readyWaitMs) {
            session.status.first { it is Session.Status.Live || it is Session.Status.Unauthorized }
        }
        return status is Session.Status.Live
    }

    companion object {
        const val READY_WAIT_MS = 8_000L
        private const val OUTCOME_UNAVAILABLE = "unavailable"
    }
}
