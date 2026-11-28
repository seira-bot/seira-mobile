package bot.seira.companion.core

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlinx.serialization.Serializable

@Serializable
data class TurnUsage(
    val input: Long = 0,
    val output: Long = 0,
    val cachedInput: Long? = null,
    val costUsd: Double? = null,
)

@Serializable
data class ContextUsage(val tokens: Long = 0, val window: Long? = null)

/** What a conversation has spent. A null [costUsd] means no price is known. */
@Serializable
data class TaskUsage(
    val input: Long = 0,
    val output: Long = 0,
    val cachedInput: Long? = null,
    val costUsd: Double? = null,
    val turns: Int = 0,
    val lastTurn: TurnUsage? = null,
    val context: ContextUsage? = null,
)

sealed interface AiUsageResult {
    data class Report(val report: EngineLimitsReport) : AiUsageResult
    data object NotAvailable : AiUsageResult
    data object Failed : AiUsageResult
}

data class ConversationUsageLine(
    val tokens: Long,
    val turns: Int,
    val costUsd: Double?,
    val contextTokens: Long?,
    val contextWindow: Long?,
)

object AiUsageRules {
    fun hidden(hiddenFor: String?, connectionId: String?): Boolean =
        connectionId != null && hiddenFor == connectionId

    fun conversationUsage(usage: TaskUsage?): ConversationUsageLine? {
        usage ?: return null
        val tokens = usage.input + usage.output
        if (tokens <= 0 && usage.turns <= 0) return null
        val context = usage.context
        return ConversationUsageLine(
            tokens = tokens,
            turns = usage.turns,
            costUsd = usage.costUsd,
            contextTokens = context?.takeIf { it.window != null && it.window > 0 }?.tokens,
            contextWindow = context?.window?.takeIf { it > 0 },
        )
    }

    fun formatCount(count: Long, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(count)

    fun formatCost(cost: Double, locale: Locale): String =
        "$" + String.format(locale, "%.2f", if (cost > 0) max(cost, 0.01) else 0.0)
}
