package com.openmausbot.companion.notifications

import android.content.Intent
import com.openmausbot.companion.core.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers the Approve and Reply actions of a notification through the same
 * [Session] paths the chat uses.
 *
 * Security invariant: an approval never records "always allow". It answers only
 * the pending plain permission card that the notification was posted for
 * (see [NotificationApproval]).
 */
class NotificationActionHandler(
    private val session: Session,
    private val poster: LocalNotificationPoster,
    private val readyWaitMs: Long = READY_WAIT_MS,
) {
    suspend fun handle(intent: Intent) {
        when (intent.action) {
            NotificationActions.ACTION_APPROVE ->
                NotificationActions.approveRequest(intent)?.let { approve(it) }
            NotificationActions.ACTION_REPLY -> {
                val request = NotificationActions.replyRequest(intent) ?: return
                val text = NotificationActions.replyText(intent)
                if (text == null) poster.showReplyFailed(request) else reply(request, text)
            }
        }
    }

    suspend fun approve(request: ApproveRequest): Boolean {
        val approved = attempt {
            if (!awaitLive()) return@attempt false
            val found = NotificationApproval.find(
                session.state.value,
                request.botId,
                request.threadId,
                request.requestId,
            ) ?: return@attempt false
            session.actionError = null
            session.answer(found.chat, found.card, found.choice, rememberingPermission = false)
            val landed = session.actionError == null && session.status.value !is Session.Status.Unauthorized
            session.actionError = null
            landed
        }
        poster.showApprovalOutcome(request, approved)
        return approved
    }

    suspend fun reply(request: ReplyRequest, text: String): Boolean {
        val sent = attempt {
            if (!awaitLive()) return@attempt false
            val chat = session.state.value.notificationChat(request.botId, request.threadId)
                ?: return@attempt false
            val landed = session.send(text, emptyList(), chat)
            session.actionError = null
            landed
        }
        if (sent) poster.showReplySent(request, text) else poster.showReplyFailed(request)
        return sent
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

    private suspend fun attempt(block: suspend () -> Boolean): Boolean =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        }

    companion object {
        const val READY_WAIT_MS = 8_000L
    }
}
