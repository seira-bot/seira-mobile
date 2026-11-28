package bot.seira.companion.ui.chat.composer

import androidx.annotation.StringRes
import bot.seira.companion.R
import bot.seira.companion.core.ApprovalMode
import bot.seira.companion.core.ApprovalModeRefusal
import bot.seira.companion.core.ApprovalModeResult
import bot.seira.companion.core.Chat
import bot.seira.companion.core.GoalRuns
import bot.seira.companion.core.Message
import bot.seira.companion.core.QueuedSend
import bot.seira.companion.core.SteerResult
import bot.seira.companion.ui.ChatAction
import bot.seira.companion.ui.ChatActionId
import bot.seira.companion.ui.ChatActions
import bot.seira.companion.ui.SlashCommand
import bot.seira.companion.ui.SlashCommandId
import bot.seira.companion.ui.SlashEffect

internal enum class RoundButton { CALL, SEND, STOP }

internal object ComposerRound {
    /**
     * Text wins over everything, so a line typed while the bot works is still
     * sent into the queue. With an empty field the button stops a working bot,
     * and otherwise starts the call.
     */
    fun of(hasContent: Boolean, working: Boolean, callAvailable: Boolean): RoundButton = when {
        hasContent -> RoundButton.SEND
        working -> RoundButton.STOP
        callAvailable -> RoundButton.CALL
        else -> RoundButton.SEND
    }

    fun enabled(button: RoundButton, hasContent: Boolean): Boolean = button != RoundButton.SEND || hasContent

    /** With a line typed the round button sends, so Stop sits beside it while the bot works. */
    fun stopBesideSend(hasContent: Boolean, working: Boolean): Boolean = hasContent && working
}

internal object ComposerHint {
    /** A direct message with one person reads as a chat with them, not as a group. */
    fun isGroup(chat: Chat): Boolean = chat is Chat.RoomChat && chat.room.dm != true
}

internal object ModeChip {
    /** A group has no approval mode, so only a bot chat shows the chip. */
    fun visible(chat: Chat): Boolean = chat is Chat.BotChat

    /** Custom is listed only while it is the current mode, and cannot be picked. */
    fun options(current: ApprovalMode?): List<ApprovalMode> =
        ApprovalMode.phoneChoices + if (current == ApprovalMode.CUSTOM) listOf(ApprovalMode.CUSTOM) else emptyList<ApprovalMode>()

    @StringRes
    fun nameRes(mode: ApprovalMode?): Int = when (mode) {
        ApprovalMode.ASK -> R.string.composer_mode_ask
        ApprovalMode.EDITS -> R.string.composer_mode_edits
        ApprovalMode.AUTO -> R.string.composer_mode_auto
        ApprovalMode.FULL -> R.string.composer_mode_full
        ApprovalMode.CUSTOM -> R.string.composer_mode_custom
        null -> R.string.composer_mode_unset
    }

    @StringRes
    fun descriptionRes(mode: ApprovalMode): Int = when (mode) {
        ApprovalMode.ASK -> R.string.composer_mode_ask_sub
        ApprovalMode.EDITS -> R.string.composer_mode_edits_sub
        ApprovalMode.AUTO -> R.string.composer_mode_auto_sub
        ApprovalMode.FULL -> R.string.composer_mode_full_sub
        ApprovalMode.CUSTOM -> R.string.composer_mode_custom_sub
    }

    @StringRes
    fun refusalRes(refusal: ApprovalModeRefusal): Int = when (refusal) {
        is ApprovalModeRefusal.FullNotAllowedHere -> R.string.composer_mode_refused_full
        is ApprovalModeRefusal.LeaveCustomOnDesktop -> R.string.composer_mode_refused_leave_custom
        is ApprovalModeRefusal.CustomOnDesktop -> R.string.composer_mode_refused_custom
        is ApprovalModeRefusal.OwnerOnly -> R.string.composer_mode_refused_owner
        is ApprovalModeRefusal.ProviderUnsupported -> R.string.composer_mode_refused_provider
        is ApprovalModeRefusal.StillConfirming -> R.string.composer_mode_refused_confirming
        is ApprovalModeRefusal.LocalAutoNeedsAcknowledgement,
        is ApprovalModeRefusal.Other,
        -> R.string.composer_mode_refused_other
    }
}

internal sealed interface ModeChangeOutcome {
    data class Changed(val mode: ApprovalMode?) : ModeChangeOutcome

    /** The computer needs a yes first. Ask, then call again with `acknowledged = true`. */
    data class NeedsConfirmation(val mode: ApprovalMode) : ModeChangeOutcome

    /** The mode did not change: [kept] is still the mode, and [reason] says why. */
    data class Refused(val kept: ApprovalMode?, @StringRes val reason: Int) : ModeChangeOutcome
}

internal object ModeChange {
    suspend fun request(
        current: ApprovalMode?,
        target: ApprovalMode,
        acknowledged: Boolean,
        set: suspend (ApprovalMode, Boolean) -> ApprovalModeResult,
    ): ModeChangeOutcome {
        if (!target.phoneCanSet) return ModeChangeOutcome.Refused(current, R.string.composer_mode_refused_custom)
        return when (val result = set(target, acknowledged)) {
            is ApprovalModeResult.Changed -> ModeChangeOutcome.Changed(result.mode ?: target)
            is ApprovalModeResult.Refused -> {
                val refusal = result.refusal
                if (refusal is ApprovalModeRefusal.LocalAutoNeedsAcknowledgement && !acknowledged) {
                    ModeChangeOutcome.NeedsConfirmation(target)
                } else {
                    ModeChangeOutcome.Refused(current, ModeChip.refusalRes(refusal))
                }
            }
        }
    }
}

internal enum class SteerOutcome { DONE, WAITING, FAILED }

internal object QueuedSteering {
    /** The line shown under the composer for an outcome, or null when nothing needs saying. */
    @StringRes
    fun messageRes(outcome: SteerOutcome): Int? = when (outcome) {
        SteerOutcome.WAITING -> R.string.composer_steer_waiting
        SteerOutcome.FAILED -> R.string.composer_steer_failed
        SteerOutcome.DONE -> null
    }

    /** The only way Steer reaches the computer: through [steerQueued], never an interrupt. */
    suspend fun steer(
        send: QueuedSend,
        chat: Chat,
        steerQueued: suspend (QueuedSend, Chat) -> SteerResult?,
    ): SteerOutcome = when (steerQueued(send, chat)) {
        is SteerResult.Steered, SteerResult.Gone -> SteerOutcome.DONE
        is SteerResult.StillQueued -> SteerOutcome.WAITING
        null -> SteerOutcome.FAILED
    }
}

internal object GoalChip {
    private const val LIMIT = 36

    /** The words of the newest goal that is still running, or null when none is. */
    fun text(messages: List<Message>): String? =
        messages.asReversed()
            .firstNotNullOfOrNull { message ->
                message.goalRun?.takeIf { run -> GoalRuns.showsStop(run) }
            }
            ?.let { GoalRuns.compact(it.goal, LIMIT) }
            ?.takeIf { it.isNotEmpty() }
}

/** The four commands that insert their name and go out as plain message text. */
internal object ComposerCommands {
    val GOAL = SlashCommand(SlashCommandId.GOAL, "/goal", "Set a goal and let it work until done", SlashEffect.Insert("/goal "))
    val LEARN = SlashCommand(SlashCommandId.LEARN, "/learn", "Draft a reusable skill from this conversation", SlashEffect.Insert("/learn "))
    val SETUP = SlashCommand(SlashCommandId.SETUP, "/setup", "Have this bot interview you and set itself up", SlashEffect.Insert("/setup "))
    val BURST = SlashCommand(SlashCommandId.BURST, "/burst", "Run several tasks at once", SlashEffect.Insert("/burst "))
}

internal object ComposerPlus {
    /** What the + offers. The live call and Stop live on the composer's buttons. */
    fun actions(
        chat: Chat,
        hasPendingApproval: Boolean,
        canAddAttachment: Boolean,
        hasCamera: Boolean,
        offersSchedule: Boolean,
    ): List<ChatAction> {
        return ChatActions.sheet(
            chat = chat,
            hasPendingApproval = hasPendingApproval,
            canAddAttachment = canAddAttachment,
            hasCamera = hasCamera,
            offersCall = false,
            offersSchedule = offersSchedule,
        ).filterNot { it.id == ChatActionId.INTERRUPT || it.id == ChatActionId.LIVE_CALL }
    }
}
