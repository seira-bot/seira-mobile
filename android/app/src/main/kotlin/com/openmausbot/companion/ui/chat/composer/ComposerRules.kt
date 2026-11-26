package com.openmausbot.companion.ui.chat.composer

import androidx.annotation.StringRes
import com.openmausbot.companion.R
import com.openmausbot.companion.core.ApprovalMode
import com.openmausbot.companion.core.ApprovalModeRefusal
import com.openmausbot.companion.core.ApprovalModeResult
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.GoalRuns
import com.openmausbot.companion.core.Message
import com.openmausbot.companion.core.QueuedSend
import com.openmausbot.companion.core.SteerResult
import com.openmausbot.companion.ui.ChatAction
import com.openmausbot.companion.ui.ChatActionId
import com.openmausbot.companion.ui.ChatActions
import com.openmausbot.companion.ui.SlashCommand
import com.openmausbot.companion.ui.SlashCommandId
import com.openmausbot.companion.ui.SlashEffect

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

/** The four commands that go to the bot as plain message text. */
internal object ComposerCommands {
    val GOAL = SlashCommand(SlashCommandId.GOAL, "/goal", "Set a goal and let it work until done", SlashEffect.Insert("/goal "))
    val LEARN = SlashCommand(SlashCommandId.LEARN, "/learn", "Save something to remember for next time", SlashEffect.Insert("/learn "))
    val SETUP = SlashCommand(SlashCommandId.SETUP, "/setup", "Set up a skill or a tool", SlashEffect.Insert("/setup "))
    val BURST = SlashCommand(SlashCommandId.BURST, "/burst", "Run several tasks at once", SlashEffect.Insert("/burst "))
}

internal object ComposerPlus {
    /**
     * What the + offers. Every entry has an action behind it. The live call
     * moved to the round button and Stop lives there too.
     */
    fun actions(
        chat: Chat,
        hasPendingApproval: Boolean,
        canAddAttachment: Boolean,
        hasCamera: Boolean,
        offersSchedule: Boolean,
    ): List<ChatAction> {
        val base = ChatActions.sheet(
            chat = chat,
            hasPendingApproval = hasPendingApproval,
            canAddAttachment = canAddAttachment,
            hasCamera = hasCamera,
            offersCall = false,
            offersSchedule = offersSchedule,
        ).filterNot { it.id == ChatActionId.INTERRUPT || it.id == ChatActionId.LIVE_CALL }
        val goal = ChatAction(ChatActionId.WORK_UNTIL_DONE, "Work until done", "Give it a goal and let it keep going")
        val at = base.indexOfFirst { it.id == ChatActionId.NEW_TASK || it.id == ChatActionId.SHARE_MARKDOWN }
        return if (at < 0) base + goal else base.take(at) + goal + base.drop(at)
    }
}
