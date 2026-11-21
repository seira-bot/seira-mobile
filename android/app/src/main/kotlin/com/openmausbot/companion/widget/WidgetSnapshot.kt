package com.openmausbot.companion.widget

import com.openmausbot.companion.core.Chat
import com.openmausbot.companion.core.ActivityDetail
import com.openmausbot.companion.core.CompanionState
import com.openmausbot.companion.core.OptionCard
import com.openmausbot.companion.notifications.NotificationApproval
import com.openmausbot.companion.ui.ChatUpdate
import com.openmausbot.companion.ui.UpdateKind
import com.openmausbot.companion.ui.updates
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
        val requestId: String? = null,
        /** The options of a live ask that is not a skill request. */
        val options: List<String> = emptyList(),
        val isPermission: Boolean = false,
        /**
         * The one label that approves this ask, set only for a plain permission
         * card (see [NotificationApproval]). Null means the row only opens the chat.
         */
        val plainAllow: String? = null,
        /**
         * True only when [line] is the whole request text: it had no line break,
         * no cut, and is short enough to show in full. Set at write time from the
         * original text. A button never shows next to a request that may be hidden.
         */
        val fullyShown: Boolean = false,
    ) {
        val canAnswer: Boolean
            get() = kind == Kind.NEEDS_YOU && requestId != null && plainAllow != null && fullyShown

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

    /**
     * The row that an answer button may still answer, or null when the rendered
     * button has gone stale. The write must be at most [maximumAgeMs] old, the
     * row must still hold a live ask with this exact request and option, and the
     * option must be the plain approval of a plain permission card.
     */
    fun answerableRow(
        threadId: String,
        requestId: String,
        choice: String,
        now: Long,
        maximumAgeMs: Long = ANSWER_MAXIMUM_AGE_MS,
    ): Row? {
        if (now - writtenAt > maximumAgeMs) return null
        val row = rows.firstOrNull { it.threadId == threadId } ?: return null
        if (!row.canAnswer || !row.isPermission) return null
        if (row.requestId != requestId || choice !in row.options || choice != row.plainAllow) return null
        return row
    }

    /** The snapshot with one answered ask gone; everything else stays as written, `writtenAt` included. */
    fun removingRow(answeredInThread: String): WidgetSnapshot =
        copy(rows = rows.filter { it.threadId != answeredInThread })

    companion object {
        const val RENEWAL_INTERVAL_MS = 60_000L
        const val WORKING_LINE_INTERVAL_MS = 30_000L
        const val ANSWER_MAXIMUM_AGE_MS = 600_000L
        const val STALE_AFTER_MS = 900_000L
        const val MAX_LINE = 160

        /** Widest request, in [lineWeight] units, that the 2x2 widget shows in full at normal font size. */
        const val MAX_ANSWER_WEIGHT = 36

        private val WIDE = listOf(
            0x1100..0x115F, 0x2E80..0xA4CF, 0xAC00..0xD7A3, 0xF900..0xFAFF, 0xFE30..0xFE4F,
            0xFF00..0xFF60, 0xFFE0..0xFFE6, 0x1F300..0x1FAFF, 0x20000..0x3FFFD,
        )

        private val REFUSED_TYPES = setOf(
            Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.CONTROL,
            Character.FORMAT, Character.SURROGATE, Character.UNASSIGNED, Character.PRIVATE_USE,
        ).map(Int::toByte).toSet()

        private val REFUSED_POINTS = setOf(0x85, 0x0B, 0x0C)

        /** A wide (CJK) character takes two units because it fills twice the width. */
        fun lineWeight(text: String): Int {
            var weight = 0
            var i = 0
            while (i < text.length) {
                val point = text.codePointAt(i)
                weight += if (WIDE.any { point in it }) 2 else 1
                i += Character.charCount(point)
            }
            return weight
        }

        /**
         * Allowlist: every code point must be one that draws as itself. A line
         * break, control, format (bidi, zero width), surrogate, unassigned or
         * private-use point could hide or reorder text next to a button.
         */
        fun isFullyShown(original: String): Boolean {
            if (original.isEmpty() || original.length > MAX_LINE) return false
            var i = 0
            while (i < original.length) {
                val point = original.codePointAt(i)
                if (point in REFUSED_POINTS || Character.getType(point).toByte() in REFUSED_TYPES) return false
                i += Character.charCount(point)
            }
            return lineWeight(original) <= MAX_ANSWER_WEIGHT
        }

        /** A larger system font fits fewer characters on the same lines. */
        fun fitsAtScale(original: String, fontScale: Float): Boolean =
            lineWeight(original) * maxOf(1f, fontScale) <= MAX_ANSWER_WEIGHT
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
    val rows = updates.map { update -> update.toRow(this, clock, now) }
    clock.forgetAbsentFrom(rows.map { it.botId to it.threadId })
    return WidgetSnapshot(now, connectionId, detail.wireValue, rows)
}

private fun ChatUpdate.toRow(state: CompanionState, clock: WidgetSinceClock, now: Long): WidgetSnapshot.Row {
    val widgetKind = kind.toWidgetKind()
    val liveCard = card?.takeIf { widgetKind == WidgetSnapshot.Kind.NEEDS_YOU && it.isPending && it.skillRequest == null }
    val plain = liveCard?.requestId?.let { requestId ->
        NotificationApproval.find(state, chat.id, chat.threadId, requestId)?.choice
    }
    return WidgetSnapshot.Row(
        botId = chat.id,
        threadId = chat.threadId,
        roomId = (chat as? Chat.RoomChat)?.room?.id,
        name = chat.name,
        color = chat.color,
        lookKey = lookKey(chat),
        kind = widgetKind,
        line = line.replace('\n', ' ').take(WidgetSnapshot.MAX_LINE),
        fullyShown = fullyShown(),
        since = clock.stamp(chat.id, chat.threadId, widgetKind, now),
        requestId = liveCard?.requestId,
        options = liveCard?.options.orEmpty(),
        isPermission = liveCard?.isPermission == true,
        plainAllow = plain,
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

/**
 * The card's own text is the request. The update line keeps only the first line
 * of it and may cut that, so the check reads the card, not the line.
 */
private fun ChatUpdate.fullyShown(): Boolean {
    val card = card ?: return false
    val original = card.requestText()
    return original == line.trim() && WidgetSnapshot.isFullyShown(original)
}

/** The whole text of the request that a button would approve. */
internal fun OptionCard.requestText(): String = subtitle.trim().ifEmpty { title.trim() }
