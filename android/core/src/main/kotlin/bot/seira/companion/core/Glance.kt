package bot.seira.companion.core

/** A bot's state in Glance. A lower [rank] comes first. */
enum class GlanceState(val rank: Int) { NEEDS(0), QUESTION(0), FAILED(1), WORKING(2), DONE(3) }

enum class GlanceAskKind {
    /** A plain tool permission. Glance can deny it, and allow it once next to its whole text. */
    APPROVAL,

    /** The bot asks the person something. Glance only opens the chat. */
    QUESTION,

    /** A skill, a send on the person's behalf, a team memory or a proposal. Only the chat can answer it. */
    REVIEW,
}

data class GlanceAsk(
    val kind: GlanceAskKind,
    val requestId: String,
    val threadId: String,
    val title: String,
    /**
     * Security: the whole text that the approval covers, or empty. Never a cut text: a cut
     * command can hide its tail. Only an [GlanceAskKind.APPROVAL] has one.
     */
    val detail: String,
    val at: Double,
) {
    val canDeny: Boolean get() = kind == GlanceAskKind.APPROVAL

    /** Security: Allow once shows only where the surface can show [title] and [detail] whole. */
    fun canAllow(budget: GlanceBudget): Boolean =
        kind == GlanceAskKind.APPROVAL && detail.isNotEmpty() &&
            budget.fitsWhole(if (title.isEmpty()) detail else "$title\n$detail")
}

/** How much text one Glance surface shows without a cut: [lines] rows of [lineChars] characters. */
data class GlanceBudget(val lineChars: Int, val lines: Int) {
    /**
     * Security: an overestimate, so a text that fits is never cut on screen. Only printable ASCII,
     * new lines and tabs qualify: other characters can draw much wider than one cell. Rows wrap
     * greedily at spaces, a longer word breaks at the row end, and a tab takes 8 cells.
     * A surface must still measure its real layout before it offers Allow once.
     */
    fun fitsWhole(text: String): Boolean {
        if (text.isEmpty() || lineChars <= 0) return false
        if (!text.all { it == '\n' || it == '\t' || it in ' '..'~' }) return false
        var used = 0
        for (line in text.split('\n')) {
            used += rows(line)
            if (used > lines) return false
        }
        return true
    }

    private fun rows(line: String): Int {
        var rows = 1
        var column = 0
        for (word in line.split(' ')) {
            val width = word.length + 7 * word.count { it == '\t' }
            val gap = if (column == 0) 0 else 1
            if (column + gap + width <= lineChars) {
                column += gap + width
                continue
            }
            if (column > 0) rows++
            val extra = maxOf(0, width - 1) / lineChars
            rows += extra
            column = width - extra * lineChars
        }
        return rows
    }
}

data class GlanceBot(
    val botId: String,
    val threadId: String,
    val name: String,
    val color: String?,
    val state: GlanceState,
    /** The bot's own words while it works; empty in every other state. */
    val line: String,
    val ask: GlanceAsk?,
) {
    val key: String get() = "$botId\n$threadId"
}

data class GlanceEntry(
    val bot: GlanceBot,
    /** When the model first saw this bot in this thread, in ms. Working bots rank by it. */
    val startedAt: Long,
    /** When a finished entry leaves, in ms; null for every other state. */
    val until: Long? = null,
)

data class GlanceMemory(
    val seen: Map<String, GlanceEntry> = emptyMap(),
    val failedShown: Set<String> = emptySet(),
    val done: Map<String, GlanceEntry> = emptyMap(),
)

data class GlanceView(
    /** In rank order: the focus first. */
    val entries: List<GlanceEntry>,
    /** When a finished entry leaves and the view must be worked out again, in ms. */
    val nextAt: Long?,
) {
    val focus: GlanceEntry? get() = entries.firstOrNull()
}

/**
 * What Glance shows, worked out from the phone's own state. The rules are the desktop's
 * (`electron/glance-model.mjs` in the Seira repository): a bot that needs the person or asks
 * comes first, then one that failed, then the one that works longest, then one that finished.
 */
object Glance {
    const val DONE_MS = 4_000L
    const val NAME_LIMIT = 80
    const val LINE_LIMIT = 160
    const val TITLE_LIMIT = 240
    const val DETAIL_LIMIT = 400

    fun step(memory: GlanceMemory, bots: List<GlanceBot>, now: Long): Pair<GlanceMemory, GlanceView> {
        val present = bots.map(GlanceBot::key).toSet()
        val seen = LinkedHashMap<String, GlanceEntry>()
        val failedShown = mutableSetOf<String>()
        for (bot in bots) {
            val before = memory.seen[bot.key]
            seen[bot.key] = GlanceEntry(bot, before?.startedAt ?: now)
            val failureIsNew = bot.key in memory.failedShown || (before != null && before.bot.state != GlanceState.FAILED)
            if (bot.state == GlanceState.FAILED && failureIsNew) failedShown += bot.key
        }
        val done = LinkedHashMap<String, GlanceEntry>()
        for ((key, entry) in memory.done) {
            if (key !in present && (entry.until ?: 0) > now) done[key] = entry
        }
        for ((key, before) in memory.seen) {
            if (key in present || before.bot.state != GlanceState.WORKING) continue
            val finished = before.bot.copy(state = GlanceState.DONE, line = "", ask = null)
            done[key] = GlanceEntry(finished, before.startedAt, now + DONE_MS)
        }
        val shown = seen.values.filter { it.bot.state != GlanceState.FAILED || it.bot.key in failedShown }
        val entries = (shown + done.values)
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<GlanceEntry>> { it.value.bot.state.rank }
                    .thenBy { if (it.value.bot.state == GlanceState.WORKING) it.value.startedAt else 0L }
                    .thenByDescending { if (it.value.bot.state == GlanceState.DONE) it.value.until ?: 0L else 0L }
                    .thenBy { it.index },
            )
            .map { it.value }
        val next = done.values.mapNotNull(GlanceEntry::until).minOrNull()
        return GlanceMemory(seen, failedShown, done) to GlanceView(entries, next)
    }

    /** One entry per bot that works, waits on the person, asks, or failed, in fleet order. */
    fun bots(state: CompanionState): List<GlanceBot> {
        val pending = state.pendingApprovals
        return state.bots.filter { it.hidden != true }.mapNotNull { bot -> botOf(state, bot, pending) }
    }

    /** The ask Glance shows now for [requestId] of [botId], or null when the request changed or settled. */
    fun currentAsk(state: CompanionState, botId: String, requestId: String): GlanceAsk? {
        val bot = state.bot(botId)?.takeIf { it.hidden != true } ?: return null
        return botOf(state, bot, state.pendingApprovals)?.ask?.takeIf { it.requestId == requestId }
    }

    /**
     * Security: the card's whole text, for a plain permission card whose text has 1 to
     * [DETAIL_LIMIT] characters. Empty for every other card and for a longer text.
     */
    fun approvalDetail(card: OptionCard): String {
        if (!isPlainPermission(card)) return ""
        val text = card.subtitle
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(CONTROL_KEEP_LINES, "")
            .replace(FORMAT, "")
            .trim()
        val count = text.codePointCount(0, text.length)
        return if (count in 1..DETAIL_LIMIT) text else ""
    }

    /** The last line the bot wrote in its running turn, without markup or code. */
    fun line(streaming: String?): String {
        if (streaming.isNullOrBlank()) return ""
        val prose = mutableListOf<String>()
        var inFence = false
        for (raw in streaming.lines()) {
            val trimmed = raw.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                continue
            }
            if (!inFence) prose += trimmed
        }
        val last = prose.lastOrNull { it.isNotEmpty() } ?: return ""
        val plain = last
            .replace(LIST_MARK, "")
            .replace(MARKUP, "")
            .replace(FORMAT, "")
            .replace(CONTROL, "")
            .replace(SPACES, " ")
            .trim()
        return clip(plain, LINE_LIMIT, fromEnd = true)
    }

    /** Security: the chat's own classifier must also draw the card as an approval; a proposal never qualifies. */
    fun isPlainPermission(card: OptionCard): Boolean =
        card.isPermission &&
            card.presentation == CardPresentation.APPROVAL &&
            card.requestType != "question" &&
            card.skillRequest == null &&
            card.questionRequest == null &&
            card.outboundRequest == null &&
            card.teamMemoryRequest == null &&
            card.quotaSwitch == null

    private fun botOf(state: CompanionState, bot: Bot, pending: List<PendingApproval>): GlanceBot? {
        val threads = threadsOf(bot)
        val threadIds = threads.map(BotTask::threadId).toSet() + bot.threadId
        val ask = pending
            .filter { it.threadId in threadIds }
            .mapNotNull { approval -> approval.message.card?.let { askOf(it, approval) } }
            .sortedWith(compareBy<GlanceAsk> { if (it.kind == GlanceAskKind.QUESTION) 1 else 0 }.thenBy { it.at })
            .firstOrNull()
        val name = clip(clean(bot.name), NAME_LIMIT)
        fun entry(threadId: String, state: GlanceState, line: String = "", ask: GlanceAsk? = null) =
            GlanceBot(bot.id, threadId, name, bot.color, state, line, ask)
        if (ask != null) {
            val askState = if (ask.kind == GlanceAskKind.QUESTION) GlanceState.QUESTION else GlanceState.NEEDS
            return entry(ask.threadId, askState, ask = ask)
        }
        threads.firstOrNull { it.activity == "waiting-on-you" }?.let { return entry(it.threadId, GlanceState.NEEDS) }
        val working = threads.firstOrNull { it.isWorking && !it.isWaitingOnTeammate }?.threadId
            ?: bot.threadId.takeIf { bot.busy == true && bot.waitingOnTeammate != true }
        if (working != null) return entry(working, GlanceState.WORKING, line(state.streaming[working]))
        val failed = threads.firstOrNull { task ->
            task.unread == true && FailedTurnActions.lastTurnFailed(state.visibleTranscript(task.threadId))
        }
        return failed?.let { entry(it.threadId, GlanceState.FAILED) }
    }

    private fun threadsOf(bot: Bot): List<BotTask> =
        if (bot.tasks == null) {
            listOf(bot.legacyTask)
        } else {
            bot.visibleTasks.map { task ->
                if (task.threadId != bot.threadId) return@map task
                task.copy(
                    activity = task.activity ?: bot.activity,
                    busy = task.busy ?: bot.busy,
                    unread = task.unread ?: bot.unread,
                )
            }
        }

    private fun askOf(card: OptionCard, approval: PendingApproval): GlanceAsk? {
        val requestId = card.requestId ?: return null
        val kind = when {
            card.requestType == "question" || card.questionRequest != null -> GlanceAskKind.QUESTION
            isPlainPermission(card) -> GlanceAskKind.APPROVAL
            else -> GlanceAskKind.REVIEW
        }
        val detail = if (kind == GlanceAskKind.APPROVAL) approvalDetail(card) else ""
        return GlanceAsk(kind, requestId, approval.threadId, clip(clean(card.title), TITLE_LIMIT), detail, approval.message.at)
    }

    private fun clean(text: String): String = text.replace(FORMAT, "").replace(CONTROL, " ").replace(SPACES, " ").trim()

    private fun clip(text: String, limit: Int, fromEnd: Boolean = false): String {
        if (text.codePointCount(0, text.length) <= limit) return text
        return if (fromEnd) {
            "…" + text.takeLastCharacters(limit - 1).trimStart()
        } else {
            text.substring(0, text.offsetByCodePoints(0, limit - 1)).trimEnd() + "…"
        }
    }

    private val CONTROL = Regex("[\\x00-\\x1f\\x7f-\\x9f\\u200b-\\u200f\\u2028-\\u202e\\u2060-\\u2069\\ufeff]")
    private val CONTROL_KEEP_LINES = Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f-\\x9f\\u200b-\\u200f\\u2028-\\u202e\\u2060-\\u2069\\ufeff]")
    private val FORMAT = Regex("\\p{Cf}")
    private val SPACES = Regex("\\s+")
    private val LIST_MARK = Regex("^(?:#{1,6}\\s+|>\\s*|[-*+]\\s+|\\d+[.)]\\s+)")
    private val MARKUP = Regex("\\*+|`+|~~")
}
