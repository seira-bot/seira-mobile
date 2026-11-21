package com.openmausbot.companion.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * One usage window as an engine reported it. [window] stays a string: a newer
 * computer can name a window this build has never heard of.
 */
@Serializable
data class LimitWindowUsage(
    val window: String,
    /** 0 to 100. */
    val usedPercent: Double,
    /** Epoch milliseconds when the window resets; null when not known. */
    val resetsAt: Double? = null,
)

/** One engine's usage limit, from `GET /api/limit-resets`. */
@Serializable
data class EngineLimit(
    /** The engine instance id. */
    val engine: String,
    val name: String = "",
    val driver: String = "",
    /** The window that decides the reset: the fullest, or the one refusing work. */
    val window: String? = null,
    val usedPercent: Double? = null,
    val resetsAt: Double? = null,
    /** The engine refuses work until [resetsAt]. */
    val blocked: Boolean = false,
    val held: Boolean? = null,
    /** Every window the engine reported. Older computers send only the deciding window. */
    val windows: List<LimitWindowUsage>? = null,
)

@Serializable
data class EngineLimitsReport(
    val checkedAt: Double = 0.0,
    val engines: List<EngineLimit> = emptyList(),
)

enum class UsageTone { FINE, NEAR, OUT }

enum class LimitWindowKind { FIVE_HOUR, WEEKLY, OTHER }

object EngineUsage {
    /** From this share of a window used, an engine counts as near its limit. */
    const val nearLimitPercent: Double = 90.0

    fun nearLimit(usedPercent: Double?, blocked: Boolean): Boolean =
        blocked || (usedPercent ?: 0.0) >= nearLimitPercent

    /** An engine that reports usage. API keys and local models report none. */
    fun reportsUsage(limit: EngineLimit?): Boolean =
        limit != null && (limit.blocked || limit.usedPercent != null)

    /** The engines the usage screen lists. Empty means its empty state. */
    fun reporting(engines: List<EngineLimit>): List<EngineLimit> = engines.filter { reportsUsage(it) }

    fun tone(usedPercent: Double?, blocked: Boolean): UsageTone {
        if (blocked || (usedPercent ?: 0.0) >= 100.0) return UsageTone.OUT
        return if (nearLimit(usedPercent, blocked)) UsageTone.NEAR else UsageTone.FINE
    }

    fun date(fromMilliseconds: Double?): Instant? =
        fromMilliseconds?.takeIf { it.isFinite() }?.let { Instant.ofEpochMilli(it.toLong()) }

    fun windowKind(raw: String?): LimitWindowKind = when (raw) {
        "5-hour" -> LimitWindowKind.FIVE_HOUR
        "weekly" -> LimitWindowKind.WEEKLY
        else -> LimitWindowKind.OTHER
    }
}

/** One line of the usage screen: a window, the share left and its reset. */
data class UsageRow(
    val window: String,
    /** Whole percent left, 0 to 100. */
    val left: Int,
    val resetsAt: Instant?,
    val tone: UsageTone,
)

/**
 * One row per window the engine reported, or the deciding window alone from a
 * computer that sends no list. The deciding window of a blocked engine is at
 * its limit until the engine's own reset time.
 */
fun usageRows(limit: EngineLimit): List<UsageRow> {
    var windows = limit.windows.orEmpty()
    if (windows.isEmpty() && EngineUsage.reportsUsage(limit)) {
        windows = listOf(
            LimitWindowUsage(limit.window ?: "other", limit.usedPercent ?: 100.0, limit.resetsAt),
        )
    }
    val deciding = if (windows.any { it.window == limit.window }) limit.window else windows.firstOrNull()?.window
    return windows.map { entry ->
        val refused = limit.blocked && entry.window == deciding
        val tone = if (refused) UsageTone.OUT else EngineUsage.tone(entry.usedPercent, false)
        UsageRow(
            window = entry.window,
            left = if (tone == UsageTone.OUT) 0 else maxOf(0, 100 - entry.usedPercent.roundToInt()),
            resetsAt = EngineUsage.date(if (refused) limit.resetsAt else entry.resetsAt),
            tone = tone,
        )
    }
}

fun usageTone(limit: EngineLimit): UsageTone = EngineUsage.tone(limit.usedPercent, limit.blocked)

/** What a one-line summary of an engine says, without the words. */
data class LimitLine(
    val name: String,
    /** Null when the engine is at its limit. */
    val usedPercent: Int?,
    val atLimit: Boolean,
    /** Only a reset still ahead of `now`. */
    val resetsAt: Instant?,
)

fun limitLine(limit: EngineLimit, now: Instant = Instant.now()): LimitLine {
    val reset = EngineUsage.date(limit.resetsAt)?.takeIf { it > now }
    return LimitLine(
        name = limit.name,
        usedPercent = if (limit.blocked) null else (limit.usedPercent ?: 0.0).roundToInt(),
        atLimit = limit.blocked,
        resetsAt = reset,
    )
}

/** "3:40 PM" for a reset today, "Fri 3:40 PM" further out. */
fun limitResetTime(
    date: Instant,
    now: Instant = Instant.now(),
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val moment = date.atZone(zone)
    val sameDay = moment.toLocalDate() == now.atZone(zone).toLocalDate()
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val formatter = if (sameDay) {
        time
    } else {
        DateTimeFormatterBuilder().appendPattern("EEE ").append(time).toFormatter(locale)
    }
    return formatter.format(moment).replace(' ', ' ')
}
