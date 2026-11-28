package bot.seira.companion.widget

import bot.seira.companion.core.Chat
import bot.seira.companion.core.ActivityDetail
import bot.seira.companion.core.CompanionState
import bot.seira.companion.ui.ChatUpdate
import bot.seira.companion.ui.UpdateKind
import bot.seira.companion.ui.updates
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the home screen widget draws: the app's updates, frozen on disk. Port
 * of `ios/Sources/CompanionCore/WidgetSnapshot.swift`.
 *
 * The widget reads this file and never the live session, so a cold process and
 * a running one show the same rows. Times are epoch milliseconds.
 */
@Serializable
data class WidgetSnapshot(
    val writtenAt: Long,
    val connectionId: String,
    /** The Activity setting the rows were folded under. */
    val detail: String? = null,
    val rows: List<Row> = emptyList(),
) {
    @Serializable
    enum class Kind {
        @SerialName("needsYou") NEEDS_YOU,
        @SerialName("working") WORKING,
        @SerialName("toReview") TO_REVIEW,
    }

    @Serializable
    data class Row(
        val botId: String,
        val threadId: String,
        val roomId: String? = null,
        val name: String,
        val color: String,
        /** `hair|color|accessories` of a bot's face, or `room|color` for a room. */
        val lookKey: String,
        val kind: Kind,
        val line: String,
        /** When this chat's current kind began. Null means not known. */
        val since: Long? = null,
    ) {
        internal fun matchesExceptLine(other: Row): Boolean = copy(line = "") == other.copy(line = "")
    }

    /**
     * Payload changes publish at once. A working row whose line alone moved is
     * narration and waits [workingLineIntervalMs]. An unchanged view renews once
     * a minute.
     */
    fun shouldReplace(previous: WidgetSnapshot?, workingLineIntervalMs: Long = WORKING_LINE_INTERVAL_MS): Boolean {
        if (previous == null || connectionId != previous.connectionId || rows.size != previous.rows.size) return true
        var narrationOnly = false
        for ((current, held) in rows.zip(previous.rows)) {
            if (!current.matchesExceptLine(held)) return true
            if (current.line != held.line) {
                if (current.kind != Kind.WORKING) return true
                narrationOnly = true
            }
        }
        val elapsed = writtenAt - previous.writtenAt
        if (elapsed < 0) return true
        val interval = if (narrationOnly) minOf(workingLineIntervalMs, RENEWAL_INTERVAL_MS) else RENEWAL_INTERVAL_MS
        return elapsed >= interval
    }

    companion object {
        const val RENEWAL_INTERVAL_MS = 60_000L
        const val WORKING_LINE_INTERVAL_MS = 30_000L
        const val STALE_AFTER_MS = 900_000L
        const val MAX_LINE = 160
    }
}

/** The elapsed clock a widget keeps per chat: it starts when a chat's kind changes and runs while the kind holds. */
class WidgetSinceClock(seed: WidgetSnapshot? = null) {
    private data class Stamp(val kind: WidgetSnapshot.Kind, val at: Long)

    private val stamps = mutableMapOf<String, Stamp>()

    init {
        for (row in seed?.rows.orEmpty()) {
            val at = row.since ?: continue
            stamps[key(row.botId, row.threadId)] = Stamp(row.kind, at)
        }
    }

    fun stamp(botId: String, threadId: String, kind: WidgetSnapshot.Kind, now: Long): Long {
        val key = key(botId, threadId)
        stamps[key]?.let { if (it.kind == kind) return it.at }
        stamps[key] = Stamp(kind, now)
        return now
    }

    fun forgetAbsentFrom(live: Collection<Pair<String, String>>) {
        val keep = live.mapTo(HashSet()) { key(it.first, it.second) }
        stamps.keys.retainAll(keep)
    }

    private fun key(botId: String, threadId: String) = "$botId/$threadId"
}

internal fun UpdateKind.toWidgetKind(): WidgetSnapshot.Kind = when (this) {
    UpdateKind.NEEDS_YOU -> WidgetSnapshot.Kind.NEEDS_YOU
    UpdateKind.WORKING -> WidgetSnapshot.Kind.WORKING
    UpdateKind.TO_REVIEW -> WidgetSnapshot.Kind.TO_REVIEW
}

/** Freezes the current updates for the widget, one row per update. */
internal fun CompanionState.widgetSnapshot(
    connectionId: String,
    detail: ActivityDetail,
    now: Long,
    clock: WidgetSinceClock = WidgetSinceClock(),
): WidgetSnapshot {
    val updates = updates(detail)
    val rows = updates.map { update -> update.toRow(clock, now) }
    clock.forgetAbsentFrom(rows.map { it.botId to it.threadId })
    return WidgetSnapshot(now, connectionId, detail.wireValue, rows)
}

private fun ChatUpdate.toRow(clock: WidgetSinceClock, now: Long): WidgetSnapshot.Row {
    val widgetKind = kind.toWidgetKind()
    return WidgetSnapshot.Row(
        botId = chat.id,
        threadId = chat.threadId,
        roomId = (chat as? Chat.RoomChat)?.room?.id,
        name = chat.name,
        color = chat.color,
        lookKey = lookKey(chat),
        kind = widgetKind,
        line = line.replace('\n', ' ').take(WidgetSnapshot.MAX_LINE),
        since = clock.stamp(chat.id, chat.threadId, widgetKind, now),
    )
}

internal fun lookKey(chat: Chat): String = when (chat) {
    is Chat.BotChat -> listOf(
        chat.bot.mascotBody.orEmpty(),
        chat.color,
        chat.bot.mascotAccessories.orEmpty().joinToString(","),
    ).joinToString("|")
    is Chat.RoomChat -> "room|${chat.color}"
}
