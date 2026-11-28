package bot.seira.companion.core

import kotlinx.serialization.Serializable

/**
 * The receipt of a group goal run (`kind == "goal.run"`), patched in place as
 * the run moves. Port of `GroupGoalRunCardData` in `phone-protocol`.
 *
 * Every field defaults and [status] stays a string: a newer computer may add a
 * status, and an unknown one is shown as spelled.
 */
@Serializable
data class GoalRunCard(
    val runId: String = "",
    val goal: String = "",
    val status: String = "",
    val coordinatorBotId: String = "",
    val coordinatorName: String = "",
    val turnCount: Int = 0,
    val maxTurns: Int = 0,
    val detail: String? = null,
    val startedAt: Double? = null,
    val finishedAt: Double? = null,
)

enum class GoalRunTone { ACTIVE, SUCCESS, ATTENTION, ERROR, NEUTRAL }

sealed interface TurnLine {
    data class Working(val turn: Int, val max: Int) : TurnLine
    data class Finished(val count: Int) : TurnLine
}

object GoalRuns {
    const val GOAL_LIMIT = 180
    const val DETAIL_LIMIT = 280
    const val WORKING = "working"

    val knownStatuses: Set<String> = setOf(
        "working", "completed", "needs-input", "blocked", "limit-reached", "paused", "stopped", "failed",
    )

    fun tone(status: String): GoalRunTone = when (status) {
        "working" -> GoalRunTone.ACTIVE
        "completed" -> GoalRunTone.SUCCESS
        "needs-input", "blocked", "limit-reached", "paused" -> GoalRunTone.ATTENTION
        "failed" -> GoalRunTone.ERROR
        else -> GoalRunTone.NEUTRAL
    }

    fun turnLine(card: GoalRunCard): TurnLine =
        if (card.status == WORKING) {
            TurnLine.Working(minOf(card.turnCount + 1, card.maxTurns), card.maxTurns)
        } else {
            TurnLine.Finished(card.turnCount)
        }

    fun showsStop(card: GoalRunCard): Boolean = card.status == WORKING

    fun compact(text: String?, limit: Int): String {
        val clean = text.orEmpty().replace(Regex("\\s+"), " ").trim()
        return if (clean.length > limit) clean.take(limit - 1).trimEnd() + "…" else clean
    }
}
