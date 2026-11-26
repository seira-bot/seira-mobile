package com.openmausbot.companion.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The failure the data routes answer with. [message] is the engine's own text. */
@Serializable
data class DataError(
    val code: String,
    val message: String,
    val sql: String? = null,
    val line: Int? = null,
    val hint: String? = null,
    val retryable: Boolean? = null,
    val candidates: List<String>? = null,
)

@Serializable
internal data class DataErrorBody(val error: DataError)

@Serializable
data class DataColumn(
    val name: String,
    val type: String,
    val nullPct: Double? = null,
    val approxUnique: Double? = null,
    val min: String? = null,
    val max: String? = null,
    val sample: List<String>? = null,
)

@Serializable
data class DataTable(
    val name: String,
    val sqlName: String? = null,
    val rowCount: Long = 0,
    val columns: List<DataColumn> = emptyList(),
)

@Serializable
data class DataSource(
    val name: String,
    val sqlName: String? = null,
    val kind: String,
    val source: String = "",
    val rowCount: Long = 0,
    val columns: List<DataColumn> = emptyList(),
    val loadedAt: String = "",
)

/** One card on a bot's data sheet. [chart], [vegaLite] and [reduction] are kept as the computer sent them. */
@Serializable
data class DataCard(
    val id: String,
    /** table, chart or text. */
    val kind: String,
    val title: String,
    val sql: String? = null,
    val result: String? = null,
    val columns: List<DataColumn>? = null,
    val rowCount: Long? = null,
    val truncated: Boolean? = null,
    val chart: JsonObject? = null,
    val vegaLite: JsonObject? = null,
    val reduction: JsonObject? = null,
    val text: String? = null,
    /** running, ready or failed. */
    val status: String,
    val error: DataError? = null,
    /** bot or person. */
    val by: String = "bot",
    val pinned: Boolean? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val elapsedMs: Double? = null,
)

@Serializable
data class DataSheet(
    val version: Int = 1,
    val botId: String = "",
    val cards: List<DataCard> = emptyList(),
    val sources: List<DataSource> = emptyList(),
    val updatedAt: String = "",
)

/** A bot's data sheet and the tables in its database. */
@Serializable
data class BotData(
    val sheet: DataSheet,
    val tables: List<DataTable> = emptyList(),
)

/** One page of rows. Cells are JSON: big numbers arrive as strings. */
@Serializable
data class DataPage(
    val columns: List<DataColumn> = emptyList(),
    val rows: List<List<JsonElement>> = emptyList(),
    val rowCount: Long = 0,
    val offset: Int = 0,
)

/** Ask for rows of exactly one source: a table by [table] or a card's result by [cardId]. */
data class DataPageRequest(
    val table: String? = null,
    val cardId: String? = null,
    val offset: Int = 0,
    val limit: Int = DEFAULT_LIMIT,
    val sortColumn: String? = null,
    val sortDescending: Boolean = false,
    val filter: String? = null,
    val filterColumn: String? = null,
) {
    companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 1_024
    }
}
