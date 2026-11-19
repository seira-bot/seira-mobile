package com.openmausbot.companion.notifications

import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ChatTarget
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.chat

/**
 * Which approval a notification may answer without opening the chat.
 *
 * Security invariant: only a plain permission card qualifies. A skill request
 * needs a reviewed hash, a question needs words, and an outbound or team-memory
 * card asks for more than a tool permission. None of them may be approved from
 * the shade.
 */
object NotificationApproval {
    data class Found(val chat: Chat, val card: OptionCard, val choice: String)

    /** The labels that mean "allow this once"; "Always allow" is never among them. */
    private val plainAllowLabels = setOf("allow", "approve", "yes", "allow once")

    fun plainAllow(card: OptionCard): String? =
        card.options.firstOrNull { it.trim().lowercase() in plainAllowLabels }

    /**
     * The one pending permission card of [threadId] that Approve may answer, or
     * null. Two pending cards make the notification ambiguous, so none qualifies.
     * When [requestId] is given, the card must be that exact request.
     */
    fun find(
        state: CompanionState,
        botId: String,
        threadId: String,
        requestId: String? = null,
    ): Found? {
        val pending = state.messages[threadId].orEmpty()
            .filter { it.kind == Message.Kind.OPTIONS && it.card?.isPending == true }
        val card = pending.singleOrNull()?.card ?: return null
        if (requestId != null && card.requestId != requestId) return null
        if (!isPlainPermission(card)) return null
        val choice = plainAllow(card) ?: return null
        if (card.responseBehavior(choice) != "allow" || card.shouldRememberPermission(choice)) return null
        val chat = state.notificationChat(botId, threadId) ?: return null
        return Found(chat, card, choice)
    }

    private fun isPlainPermission(card: OptionCard): Boolean =
        card.isPermission &&
            card.requestType != "question" &&
            card.skillRequest == null &&
            card.questionRequest == null &&
            card.outboundRequest == null &&
            card.teamMemoryRequest == null
}

internal fun CompanionState.notificationChat(botId: String, threadId: String): Chat? {
    roomOwningTask(threadId)?.let { room -> return chat(ChatTarget.Room(room.id, threadId)) }
    return chat(ChatTarget.Bot(botId, threadId))
}
