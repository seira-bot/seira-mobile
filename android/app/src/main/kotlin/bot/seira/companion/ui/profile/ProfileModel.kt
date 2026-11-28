package bot.seira.companion.ui.profile

import bot.seira.companion.core.ActivityRow
import bot.seira.companion.core.Bot
import bot.seira.companion.core.BotData
import bot.seira.companion.core.BotProfilePatch
import bot.seira.companion.core.DataPageRequest
import bot.seira.companion.core.ReplyStyle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The four tabs of a bot's profile page, addressed by [key] in the navigation stack. */
internal enum class ProfileTab(val key: String) {
    ACTIVITY("activity"),
    SETTINGS("settings"),
    LIBRARY("library"),
    COMPUTER("computer"),
    ;

    companion object {
        fun fromKey(key: String?): ProfileTab = entries.firstOrNull { it.key == key } ?: ACTIVITY
    }
}

/** The views of the Computer tab. */
internal enum class ComputerView { SCREEN, BROWSER, DATA }

/**
 * What the Settings tab edits. The instructions and the reply style start from what the
 * computer sent, or empty when it sent none, and are sent only after the person changes them.
 */
internal data class ProfileDraft(
    val name: String,
    val title: String,
    val description: String,
    val soul: String,
    val replyStyle: ReplyStyle?,
) {
    companion object {
        fun of(bot: Bot): ProfileDraft = ProfileDraft(
            name = bot.name,
            title = bot.title,
            description = bot.description,
            soul = bot.soul.orEmpty(),
            replyStyle = ReplyStyle.entries.firstOrNull { it.wire == bot.replyStyle },
        )
    }
}

internal object ProfileDraftRules {
    /** Only the fields that differ from [baseline], trimmed. */
    fun patch(draft: ProfileDraft, baseline: ProfileDraft): BotProfilePatch = BotProfilePatch(
        name = changed(draft.name, baseline.name),
        title = changed(draft.title, baseline.title),
        description = changed(draft.description, baseline.description),
        soul = changed(draft.soul, baseline.soul),
        replyStyle = draft.replyStyle.takeIf { it != baseline.replyStyle },
    )

    fun isDirty(draft: ProfileDraft, baseline: ProfileDraft): Boolean =
        patch(draft, baseline) != BotProfilePatch()

    fun canSave(draft: ProfileDraft, baseline: ProfileDraft, busy: Boolean): Boolean =
        !busy && draft.name.trim().isNotEmpty() && isDirty(draft, baseline)

    private fun changed(value: String, baseline: String): String? =
        value.trim().takeIf { it != baseline.trim() }
}

internal object ProfileActivityRules {
    /** The rows that started on [today] in [zone], newest first. */
    fun today(
        rows: List<ActivityRow>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ActivityRow> = rows
        .mapNotNull { row -> instantOf(row)?.let { it to row } }
        .filter { (instant, _) -> instant.atZone(zone).toLocalDate() == today }
        .sortedByDescending { (instant, _) -> instant }
        .map { it.second }

    fun instantOf(row: ActivityRow): Instant? = runCatching { Instant.parse(row.at) }.getOrNull()
}

internal object ProfileComputerRules {
    /** A cloud computer has its own viewer; any other may be driven, and the computer confirms or refuses. */
    fun mayTakeControl(bot: Bot): Boolean = bot.computer != "cloud"
}

/** One thing in a bot's library: a table, the table behind a card, or a card of text. */
internal data class LibraryItem(
    val id: String,
    val title: String,
    val count: Long?,
    val table: String?,
    val cardId: String?,
    val text: String?,
)

internal object ProfileLibraryRules {
    const val PAGE_SIZE = 50

    /** Tables first, then the cards that finished and hold a table or text. Charts are left out. */
    fun items(data: BotData): List<LibraryItem> {
        val tables = data.tables.map {
            LibraryItem("table:${it.name}", it.name, it.rowCount, table = it.name, cardId = null, text = null)
        }
        val cards = data.sheet.cards
            .filter { it.status == "ready" && (it.kind == "table" || it.kind == "text") }
            .map { card ->
                val isTable = card.kind == "table"
                LibraryItem(
                    id = "card:${card.id}",
                    title = card.title,
                    count = if (isTable) card.rowCount else null,
                    table = null,
                    cardId = if (isTable) card.id else null,
                    text = if (isTable) null else card.text.orEmpty(),
                )
            }
        return tables + cards
    }

    fun request(item: LibraryItem, offset: Int): DataPageRequest =
        DataPageRequest(table = item.table, cardId = item.cardId, offset = offset, limit = PAGE_SIZE)

    fun cell(element: JsonElement): String = when (element) {
        is JsonNull -> ""
        is JsonPrimitive -> element.content
        else -> element.toString()
    }

    fun hasMore(loaded: Int, total: Long): Boolean = loaded < total
}
