package bot.seira.companion.core

import kotlinx.serialization.Serializable

@Serializable
internal data class TaskPatchResponse(val bot: Bot)

/**
 * How much a bot may do in one conversation before it asks. The names are the
 * server's own. A phone may set the first four; [CUSTOM] is made and left only
 * in the desktop app.
 */
enum class ApprovalMode(val wire: String) {
    ASK("ask"),
    EDITS("edits"),
    AUTO("auto"),
    FULL("full"),
    CUSTOM("custom"),
    ;

    val phoneCanSet: Boolean get() = this != CUSTOM

    companion object {
        val phoneChoices: List<ApprovalMode> = listOf(ASK, EDITS, AUTO, FULL)

        fun parse(wire: String?): ApprovalMode? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * The level a conversation runs at, from its task record and then its bot's.
 * Null when the computer sent none; the computer then runs the conversation
 * at its own default (Full access, or Ask on a shared company workspace).
 */
fun Bot.approvalModeIn(threadId: String): ApprovalMode? {
    val shown = forTask(threadId) ?: return null
    return ApprovalMode.parse(shown.approvalMode)
        ?: shown.autoApprove?.let { if (it) ApprovalMode.AUTO else ApprovalMode.ASK }
}

/** Why the computer refused a change of approval level. [message] is the computer's own sentence. */
sealed interface ApprovalModeRefusal {
    val message: String?

    /** Full access is not allowed on a shared company workspace. */
    data class FullNotAllowedHere(override val message: String?) : ApprovalModeRefusal

    /** The conversation is on Custom. Leaving it needs the desktop app. */
    data class LeaveCustomOnDesktop(override val message: String?) : ApprovalModeRefusal

    /** Custom cannot be chosen from a phone. */
    data class CustomOnDesktop(override val message: String?) : ApprovalModeRefusal

    /** Auto on the computer itself needs the person to confirm a warning first. Send the change again with the acknowledgement. */
    data class LocalAutoNeedsAcknowledgement(override val message: String?) : ApprovalModeRefusal

    /** Only the owner of this Cloud may change the level. */
    data class OwnerOnly(override val message: String?) : ApprovalModeRefusal

    /** The bot's AI provider has no such level. */
    data class ProviderUnsupported(override val message: String?) : ApprovalModeRefusal

    /** The bot's previous level change is still being confirmed. */
    data class StillConfirming(override val message: String?) : ApprovalModeRefusal

    data class Other(override val message: String?) : ApprovalModeRefusal

    companion object {
        /**
         * Matched on the computer's own wording, the way [CompanionClient.ALREADY_DRAINED]
         * is: the refusals share a few status codes and carry no code field.
         */
        fun from(error: Throwable): ApprovalModeRefusal {
            val status = error as? APIError.Status ?: return Other(error.message)
            val text = status.serverMessage
            fun has(part: String) = text?.contains(part, ignoreCase = true) == true
            return when {
                status.code == 403 && has("shared company workspace") -> FullNotAllowedHere(text)
                status.code == 403 && has("Leaving Custom") -> LeaveCustomOnDesktop(text)
                status.code == 403 && has("Custom") -> CustomOnDesktop(text)
                status.code == 403 && has("only its owner") -> OwnerOnly(text)
                status.code == 400 && has("acknowledgeLocalAuto") -> LocalAutoNeedsAcknowledgement(text)
                status.code == 400 && has("does not support the selected approval level") -> ProviderUnsupported(text)
                status.code == 409 && has("still being confirmed") -> StillConfirming(text)
                else -> Other(text ?: status.message)
            }
        }
    }
}

sealed interface ApprovalModeResult {
    data class Changed(val bot: Bot, val mode: ApprovalMode?) : ApprovalModeResult
    data class Refused(val refusal: ApprovalModeRefusal) : ApprovalModeResult
}
