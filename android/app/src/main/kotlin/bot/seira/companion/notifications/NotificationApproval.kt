package bot.seira.companion.notifications

import bot.seira.companion.core.Chat
import bot.seira.companion.core.ChatTarget
import bot.seira.companion.core.CompanionState
import bot.seira.companion.core.Message
import bot.seira.companion.core.OptionCard
import bot.seira.companion.core.chat

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
     * The pending permission card of [threadId] that Approve may answer, or null.
     * With [requestId], only that exact request qualifies, whatever else waits
     * in the thread. Without it, the card must be the only pending one: two
     * pending cards make the notification ambiguous, so none qualifies.
     */
    fun find(
        state: CompanionState,
        botId: String,
        threadId: String,
        requestId: String? = null,
    ): Found? {
        val pending = state.messages[threadId].orEmpty()
            .filter { it.kind == Message.Kind.OPTIONS && it.card?.isPending == true }
        val card = pending
            .filter { requestId == null || it.card?.requestId == requestId }
            .singleOrNull()?.card ?: return null
        if (!isPlainPermission(card)) return null
        val choice = plainAllow(card) ?: return null
        if (card.responseBehavior(choice) != "allow" || card.shouldRememberPermission(choice)) return null
        val chat = state.notificationChat(botId, threadId) ?: return null
        return Found(chat, card, choice)
    }

    /** True when [threadId] holds the card of [requestId] and nothing waits on it any more. */
    fun isSettled(state: CompanionState, threadId: String, requestId: String): Boolean =
        state.messages[threadId].orEmpty().any { message ->
            val card = message.card
            message.kind == Message.Kind.OPTIONS && card != null && card.requestId == requestId && !card.isPending
        }

    private fun isPlainPermission(card: OptionCard): Boolean =
        card.isPermission &&
            card.requestType != "question" &&
            card.skillRequest == null &&
            card.questionRequest == null &&
            card.outboundRequest == null &&
            card.teamMemoryRequest == null
}

/** What became of an Approve tap. */
enum class ApprovalOutcome {
    APPROVED,

    /** The request was answered somewhere else first; this tap answered nothing. */
    SETTLED,
    FAILED,
}

internal fun CompanionState.notificationChat(botId: String, threadId: String): Chat? {
    roomOwningTask(threadId)?.let { room -> return chat(ChatTarget.Room(room.id, threadId)) }
    return chat(ChatTarget.Bot(botId, threadId))
}
