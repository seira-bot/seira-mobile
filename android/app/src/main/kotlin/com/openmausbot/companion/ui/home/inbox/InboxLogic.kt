package com.openmausbot.companion.ui.home.inbox

import com.openmausbot.companion.core.BotTask
import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.core.Routine
import com.openmausbot.companion.core.RoutineRun
import com.openmausbot.companion.ui.ApprovalChoices
import com.openmausbot.companion.ui.ChatUpdate
import com.openmausbot.companion.ui.RoutineRules
import com.openmausbot.companion.ui.UpdateKind

internal enum class DecisionKind { APPROVAL, QUESTION, WAITING }

/** How a decision card can be answered without leaving the Inbox. */
internal sealed interface DecisionAnswer {
    data class Options(val labels: List<String>, val primary: String?) : DecisionAnswer
    data object InChat : DecisionAnswer
    data object ReviewSkill : DecisionAnswer
    data object None : DecisionAnswer
}

internal object InboxDecisions {
    fun decisions(updates: List<ChatUpdate>): List<ChatUpdate> =
        updates.filter { it.kind == UpdateKind.NEEDS_YOU }

    /** The same count the tab bar badge and the drawer show. */
    fun count(updates: List<ChatUpdate>): Int = decisions(updates).size

    fun kind(card: OptionCard?): DecisionKind = when {
        card == null -> DecisionKind.WAITING
        card.isPermission -> DecisionKind.APPROVAL
        else -> DecisionKind.QUESTION
    }

    fun answer(card: OptionCard?): DecisionAnswer = when {
        card == null || !card.isPending -> DecisionAnswer.None
        card.skillRequest != null -> DecisionAnswer.ReviewSkill
        card.outboundRequest != null || card.teamMemoryRequest != null || card.quotaSwitch != null -> DecisionAnswer.InChat
        card.questions.isNotEmpty() -> DecisionAnswer.InChat
        card.options.isEmpty() -> DecisionAnswer.None
        else -> DecisionAnswer.Options(card.options, primaryOption(card))
    }

    /** The one option that lets the bot continue. It gets the filled button. */
    fun primaryOption(card: OptionCard): String? =
        if (card.isPermission) {
            ApprovalChoices.allowChoice(card.options, isPermission = true)
        } else {
            card.options.firstOrNull { !ApprovalChoices.isRefusal(it) }
        }

    fun title(update: ChatUpdate): String =
        update.card?.title?.trim().orEmpty().ifEmpty { update.chat.name }

    /** The kind label names the bot when the title does not. */
    fun namesBot(update: ChatUpdate): Boolean =
        title(update).contains(update.chat.name, ignoreCase = true)

    fun detail(update: ChatUpdate): String? {
        if (update.card == null) return update.chat.threadTitle.trim().takeIf { it.isNotEmpty() }
        return update.line.trim().takeIf { it.isNotEmpty() && it != title(update) }
    }
}

internal sealed interface NewsItem {
    val id: String
    val at: Double?

    data class Finished(val update: ChatUpdate, override val at: Double?) : NewsItem {
        override val id: String get() = "done-${update.id}"
    }

    data class RoutineProblem(val routine: Routine, val run: RoutineRun, override val at: Double?) : NewsItem {
        override val id: String get() = "routine-${run.id}"
    }
}

internal object InboxNews {
    const val LIMIT = 20
    const val PROBLEM_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun build(
        updates: List<ChatUpdate>,
        routines: List<Routine>,
        runs: List<RoutineRun>,
        nowMillis: Long,
        stampOf: (ChatUpdate) -> Double? = ::taskStamp,
    ): List<NewsItem> {
        val finished = updates
            .filter { it.kind == UpdateKind.TO_REVIEW }
            .map { NewsItem.Finished(it, stampOf(it)) }
        val problems = problems(routines, runs, nowMillis)
            .map { (routine, run) -> NewsItem.RoutineProblem(routine, run, run.finishedAt ?: run.scheduledFor) }
        return (finished + problems)
            .sortedWith(compareByDescending<NewsItem> { it.at != null }.thenByDescending { it.at ?: 0.0 })
            .take(LIMIT)
    }

    /**
     * A routine is a problem while its newest run failed. A newer run that
     * started or finished well clears it.
     */
    fun problems(routines: List<Routine>, runs: List<RoutineRun>, nowMillis: Long): List<Pair<Routine, RoutineRun>> {
        val byId = routines.associateBy { it.id }
        return runs.groupBy { it.routineId }.mapNotNull { (id, list) ->
            val routine = byId[id] ?: return@mapNotNull null
            val latest = list.maxByOrNull { it.scheduledFor } ?: return@mapNotNull null
            if (RoutineRules.runStatus(latest.status) != RoutineRules.RunStatus.FAILED) return@mapNotNull null
            val at = latest.finishedAt ?: latest.scheduledFor
            if (nowMillis - at > PROBLEM_WINDOW_MILLIS) null else routine to latest
        }
    }

    fun taskStamp(update: ChatUpdate): Double? {
        val tasks: List<BotTask>? = when (val chat = update.chat) {
            is Chat.BotChat -> chat.bot.tasks
            is Chat.RoomChat -> chat.room.tasks
        }
        return tasks?.firstOrNull { it.threadId == update.chat.threadId }?.let { it.updatedAt ?: it.createdAt }
    }
}
