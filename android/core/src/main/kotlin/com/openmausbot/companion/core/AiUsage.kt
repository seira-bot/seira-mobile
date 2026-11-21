package com.openmausbot.companion.core

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * Wire shapes of `GET /api/limit-resets` and of the task usage that rides on
 * each task and room. Window and driver names stay strings, so a name that a
 * newer computer adds never fails a decode.
 */
@Serializable
data class LimitWindowUsage(
    val window: String = "other",
    val usedPercent: Double = 0.0,
    val resetsAt: Double? = null,
)

@Serializable
data class EngineLimit(
    val engine: String,
    val name: String = "",
    val driver: String = "",
    val window: String? = null,
    val usedPercent: Double? = null,
    val resetsAt: Double? = null,
    val blocked: Boolean = false,
    val held: Boolean? = null,
    val windows: List<LimitWindowUsage>? = null,
)

@Serializable
data class EngineLimitsReport(
    val checkedAt: Double = 0.0,
    val engines: List<EngineLimit> = emptyList(),
)

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

enum class LimitWindowKind { FIVE_HOUR, WEEKLY, OTHER }

data class UsageWindowRow(
    val kind: LimitWindowKind,
    val percentLeft: Int,
    /** Epoch milliseconds; null when unknown or already past. */
    val resetsAt: Long?,
    val nearLimit: Boolean,
)

data class AiUsageCard(
    val engine: String,
    val name: String,
    val driver: String,
    val usedPercent: Int,
    val rows: List<UsageWindowRow>,
    val paused: Boolean,
    val pausedUntil: Long?,
    val nearLimit: Boolean,
)

data class ConversationUsageLine(
    val tokens: Long,
    val turns: Int,
    val costUsd: Double?,
    val contextTokens: Long?,
    val contextWindow: Long?,
)

object AiUsageRules {
    const val NEAR_LIMIT_PERCENT = 90

    fun windowKind(raw: String?): LimitWindowKind = when (raw) {
        "5-hour" -> LimitWindowKind.FIVE_HOUR
        "weekly" -> LimitWindowKind.WEEKLY
        else -> LimitWindowKind.OTHER
    }

    fun percentUsed(used: Double): Int = if (used.isNaN()) 0 else used.roundToInt().coerceIn(0, 100)

    fun percentLeft(used: Double): Int = 100 - percentUsed(used)

    fun isNear(used: Double?, blocked: Boolean = false): Boolean =
        blocked || (used != null && !used.isNaN() && used >= NEAR_LIMIT_PERCENT)

    /** A computer that predates this report, or a phone without the right to read it, answers like this. */
    fun isNotAvailable(code: Int): Boolean = code == 403 || code == 404

    fun hidden(hiddenFor: String?, connectionId: String?): Boolean =
        connectionId != null && hiddenFor == connectionId

    fun cards(report: EngineLimitsReport, now: Long): List<AiUsageCard> =
        report.engines.map { card(it, now) }

    private fun card(limit: EngineLimit, now: Long): AiUsageCard {
        val windows = limit.windows.orEmpty().ifEmpty {
            limit.usedPercent?.let { listOf(LimitWindowUsage(limit.window ?: "other", it, limit.resetsAt)) }.orEmpty()
        }
        val rows = windows.map { window ->
            UsageWindowRow(
                kind = windowKind(window.window),
                percentLeft = percentLeft(window.usedPercent),
                resetsAt = future(window.resetsAt, now),
                nearLimit = isNear(window.usedPercent),
            )
        }
        val paused = limit.blocked || limit.held == true
        val used = limit.usedPercent ?: windows.maxOfOrNull { it.usedPercent } ?: if (limit.blocked) 100.0 else 0.0
        val pausedUntil = if (paused) {
            future(limit.resetsAt, now) ?: windows.mapNotNull { future(it.resetsAt, now) }.maxOrNull()
        } else {
            null
        }
        return AiUsageCard(
            engine = limit.engine,
            name = limit.name.ifBlank { limit.engine.replaceFirstChar { it.uppercase() } },
            driver = limit.driver,
            usedPercent = percentUsed(used),
            rows = rows,
            paused = paused,
            pausedUntil = pausedUntil,
            nearLimit = isNear(used, limit.blocked),
        )
    }

    private fun future(at: Double?, now: Long): Long? =
        at?.takeIf { it.isFinite() && it > now }?.toLong()

    /** A clock time today, or a short weekday and time further out. */
    fun resetTime(at: Long, now: Long, zone: ZoneId, locale: Locale): String {
        val moment = Instant.ofEpochMilli(at).atZone(zone)
        val sameDay = moment.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val formatter = if (sameDay) {
            time
        } else {
            DateTimeFormatterBuilder().appendPattern("EEE ").append(time).toFormatter(locale)
        }
        return formatter.format(moment).replace(' ', ' ')
    }

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
