package com.openmausbot.companion.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * A bot-authored widget: the tree behind a message of kind `widget`. Port of the
 * helpers in `packages/phone-protocol/src/widget.ts`. The tree stays a
 * [JsonObject], so a node type that a newer computer adds never breaks decoding.
 */
typealias WidgetNode = JsonObject

object WidgetLimits {
    const val DEPTH = 32
    const val TABLE_ROWS = 60
    const val CHART_VALUE = 1e12
    const val FALLBACK = 1_500
    const val LABEL = 80
    const val CHART_ROWS = 100
    const val CHART_SERIES = 4
    const val TABLE_COLS = 8
}

val WIDGET_ROOT_TYPES: Set<String> = setOf("Card", "ListView")

val WIDGET_NODE_TYPES: Set<String> = WIDGET_ROOT_TYPES + setOf(
    "ListViewItem", "Box", "Row", "Col", "Form", "Spacer", "Divider", "Table", "Table.Row", "Table.Cell",
    "Text", "Title", "Caption", "Label", "Markdown", "Image", "Icon", "Chart", "Badge", "Progress", "Button",
    "Select", "DatePicker", "TimePicker", "DayPicker", "Checkbox", "RadioGroup", "Input", "Textarea", "Transition",
)

private val WIDGET_FIELD_TYPES = setOf("Select", "DatePicker", "TimePicker", "DayPicker", "Checkbox", "RadioGroup", "Input", "Textarea")
private val THREAD_ID = Regex("^[\\w-]{1,128}$")
private val WEB_URL = Regex("^https?://", RegexOption.IGNORE_CASE)

const val WIDGET_PHONE_PAIRING_ACTION = "open_phone_pairing"

fun widgetString(node: WidgetNode, key: String): String? = (node[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A number from a bot: never NaN or infinite, so it is safe to calculate with. */
fun widgetNumber(node: WidgetNode, key: String): Double? =
    (node[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }

/** A whole number from a bot, held between [min] and [max]. Null when the prop is missing or not a number. */
fun widgetInt(node: WidgetNode, key: String, min: Int, max: Int): Int? =
    widgetNumber(node, key)?.coerceIn(min.toDouble(), max.toDouble())?.toInt()

/** A fraction from a bot, held between 0 and 1. */
fun widgetFraction(node: WidgetNode, key: String): Float =
    (widgetNumber(node, key) ?: 0.0).coerceIn(0.0, 1.0).toFloat()

fun widgetType(node: WidgetNode): String? = widgetString(node, "type")

fun widgetChildren(node: WidgetNode): List<WidgetNode> =
    (node["children"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

private fun widgetObject(node: WidgetNode, key: String): JsonObject? = node[key] as? JsonObject

/** One answer a person gave: the text of a field, or the state of a checkbox. */
sealed interface WidgetValue {
    data class Text(val value: String) : WidgetValue
    data class Flag(val on: Boolean) : WidgetValue

    fun toJson(): JsonPrimitive = when (this) {
        is Text -> JsonPrimitive(value)
        is Flag -> JsonPrimitive(on)
    }

    companion object {
        fun of(element: JsonElement?): WidgetValue? {
            val primitive = element as? JsonPrimitive ?: return null
            if (primitive.isString) return Text(primitive.content)
            return primitive.booleanOrNull?.let(::Flag)
        }
    }
}

enum class WidgetTone { CONFIRM, CANCEL, SUBMIT;

    companion object {
        fun of(name: String?): WidgetTone? = when (name) {
            "confirm" -> CONFIRM
            "cancel" -> CANCEL
            "submit" -> SUBMIT
            else -> null
        }
    }
}

/** A widget that took its one answer. It folds to a line and takes no more presses. */
data class WidgetSettled(
    val ref: String,
    val label: String,
    val tone: WidgetTone,
    val at: Double,
    val sendId: String? = null,
    val values: Map<String, WidgetValue> = emptyMap(),
    val other: Map<String, String> = emptyMap(),
)

/** The parts of a widget payload the phone reads. */
data class WidgetView(val root: WidgetNode, val rev: Int, val settled: WidgetSettled?, val truncated: Boolean = false)

/**
 * The widget to draw, or null when the phone must show the message text instead:
 * no payload, no root, or a root type the phone does not know.
 */
fun widgetView(widget: JsonObject?): WidgetView? {
    val root = widget?.let { widgetObject(it, "root") } ?: return null
    if (widgetType(root) !in WIDGET_ROOT_TYPES) return null
    val capped = widgetCapDepth(root)
    return WidgetView(
        capped.root,
        widgetInt(widget, "rev", 0, 1_000_000_000) ?: 0,
        readSettled(widgetObject(widget, "settled")),
        capped.cut,
    )
}

/** The tree without anything nested deeper than [WidgetLimits.DEPTH], so no walk of it can overflow the stack. */
fun widgetCapDepth(root: WidgetNode): WidgetCut {
    var cut = false
    fun cap(node: WidgetNode, depth: Int): WidgetNode {
        val children = node["children"] as? JsonArray ?: return node
        if (depth >= WidgetLimits.DEPTH) {
            cut = cut || children.isNotEmpty()
            return JsonObject(node + ("children" to JsonArray(emptyList())))
        }
        return JsonObject(node + ("children" to JsonArray(children.map { (it as? JsonObject)?.let { child -> cap(child, depth + 1) } ?: it })))
    }
    return WidgetCut(cap(root, 1), cut)
}

private fun readSettled(raw: JsonObject?): WidgetSettled? {
    raw ?: return null
    val ref = widgetString(raw, "ref") ?: return null
    val tone = WidgetTone.of(widgetString(raw, "tone")) ?: return null
    return WidgetSettled(
        ref = ref,
        label = widgetString(raw, "label").orEmpty(),
        tone = tone,
        at = widgetNumber(raw, "at") ?: 0.0,
        sendId = widgetString(raw, "sendId"),
        values = widgetObject(raw, "values")?.mapNotNull { (key, value) -> WidgetValue.of(value)?.let { key to it } }.orEmpty().toMap(),
        other = widgetObject(raw, "other")?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content } }.orEmpty().toMap(),
    )
}

/** What of a widget can be drawn: the tree up to the first node type the phone does not know. */
data class WidgetCut(val root: WidgetNode, val cut: Boolean)

fun widgetCutAtUnknown(root: WidgetNode): WidgetCut {
    var cut = false
    fun prune(node: WidgetNode): WidgetNode? {
        if (widgetType(node) !in WIDGET_NODE_TYPES) {
            cut = true
            return null
        }
        val children = node["children"] as? JsonArray ?: return node
        val kept = mutableListOf<JsonElement>()
        for (child in children) {
            if (cut) break
            val drawn = (child as? JsonObject)?.let(::prune)
            if (drawn != null) kept += drawn
        }
        return JsonObject(node + ("children" to JsonArray(kept)))
    }
    val pruned = prune(root) ?: root
    return WidgetCut(pruned, cut)
}

/** The message text under the part of a widget the phone cannot draw, cut at the fallback limit. */
fun widgetFallbackText(text: String?): String? {
    val clean = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (clean.length <= WidgetLimits.FALLBACK) clean else clean.take(WidgetLimits.FALLBACK - 1).trimEnd() + "…"
}

// MARK: - Actions

data class WidgetAction(val type: String, val payload: JsonElement?)

enum class WidgetActionKind { BUTTON, ITEM, CONFIRM, CANCEL }

data class WidgetActionEntry(
    /** "confirm", "cancel", or the node id of the button or list item. */
    val ref: String,
    val label: String,
    val action: WidgetAction,
    val tone: WidgetTone? = null,
    /** Handled on the phone and never sent. */
    val client: Boolean = false,
    val kind: WidgetActionKind,
) {
    val opensPairing: Boolean get() = action.type == WIDGET_PHONE_PAIRING_ACTION

    /** A press the phone cannot do: pairing is what this phone already is. */
    val hiddenOnPhone: Boolean get() = opensPairing
}

private fun readAction(raw: JsonElement?): WidgetAction? {
    val record = raw as? JsonObject ?: return null
    val type = widgetString(record, "type") ?: return null
    return WidgetAction(type, record["payload"])
}

private fun isClientAction(action: WidgetAction) =
    action.type == "open_url" || action.type == "open_thread" || action.type == WIDGET_PHONE_PAIRING_ACTION

private fun walk(node: WidgetNode, ancestors: List<WidgetNode> = emptyList(), visit: (WidgetNode, List<WidgetNode>) -> Unit) {
    visit(node, ancestors)
    val next = ancestors + node
    widgetChildren(node).forEach { walk(it, next, visit) }
}

/** What a row is called: its first Title or Text on one line. A text with a fixed width is a column label and never names a row. */
fun widgetFirstText(node: WidgetNode): String {
    var found = ""
    walk(node) { inner, _ ->
        val type = widgetType(inner)
        if (found.isNotEmpty() || (type != "Title" && type != "Text")) return@walk
        val value = widgetString(inner, "value") ?: return@walk
        if (widgetNumber(inner, "width") != null) return@walk
        found = value.replace(Regex("\\s+"), " ").trim().take(WidgetLimits.LABEL)
    }
    return found
}

private fun buttonSpec(root: WidgetNode, key: String): Pair<String, WidgetAction>? {
    val spec = widgetObject(root, key) ?: return null
    val label = widgetString(spec, "label") ?: return null
    val action = readAction(spec["action"]) ?: return null
    return label to action
}

/** Every pressable thing in a widget, in document order. */
fun widgetActions(root: WidgetNode): List<WidgetActionEntry> {
    data class Found(val entry: WidgetActionEntry, val row: String?)
    val found = mutableListOf<Found>()
    walk(root) { node, ancestors ->
        when (widgetType(node)) {
            "Button" -> {
                if (node["disabled"] == JsonPrimitive(true)) return@walk
                val form = ancestors.lastOrNull { widgetType(it) == "Form" }
                val own = readAction(node["onClickAction"])
                val submitted = if (node["submit"] == JsonPrimitive(true)) form?.let { readAction(it["onSubmitAction"]) } else null
                val picked = own ?: submitted ?: return@walk
                val row = ancestors.lastOrNull { widgetType(it) in setOf("ListViewItem", "Table.Row", "Row") }
                found += Found(
                    WidgetActionEntry(
                        ref = widgetString(node, "id").orEmpty(),
                        label = widgetString(node, "label").orEmpty(),
                        action = picked,
                        tone = if (node["submit"] == JsonPrimitive(true)) WidgetTone.SUBMIT else null,
                        kind = WidgetActionKind.BUTTON,
                    ),
                    row?.let(::widgetFirstText),
                )
            }
            "ListViewItem" -> {
                val action = readAction(node["onClickAction"]) ?: return@walk
                found += Found(
                    WidgetActionEntry(
                        ref = widgetString(node, "id").orEmpty(),
                        label = widgetFirstText(node),
                        action = action,
                        kind = WidgetActionKind.ITEM,
                    ),
                    null,
                )
            }
        }
    }
    for (key in listOf("confirm", "cancel")) {
        val (label, action) = buttonSpec(root, key) ?: continue
        val tone = if (key == "confirm") WidgetTone.CONFIRM else WidgetTone.CANCEL
        val kind = if (key == "confirm") WidgetActionKind.CONFIRM else WidgetActionKind.CANCEL
        found += Found(WidgetActionEntry(key, label, action, tone, kind = kind), null)
    }
    val counts = found.groupingBy { it.entry.label }.eachCount()
    return found.map { (entry, row) ->
        val label = if (row != null && entry.label.isNotEmpty() && (counts[entry.label] ?: 0) > 1) {
            "${entry.label}: $row".take(WidgetLimits.LABEL * 2)
        } else {
            entry.label
        }
        entry.copy(client = isClientAction(entry.action), label = label)
    }
}

/** Where a press collects its values: the nearest Form around the pressed node, or the whole widget. */
fun widgetScope(root: WidgetNode, ref: String): WidgetNode {
    var scope = root
    walk(root) { node, ancestors ->
        if (widgetString(node, "id") == ref) scope = ancestors.lastOrNull { widgetType(it) == "Form" } ?: root
    }
    return scope
}

data class WidgetField(val name: String, val type: String, val label: String, val node: WidgetNode)

/** The named fields under [scope]. Labels are looked up across [root]. */
fun widgetFields(scope: WidgetNode, root: WidgetNode = scope): List<WidgetField> {
    val labels = mutableMapOf<String, String>()
    walk(root) { node, _ ->
        if (widgetType(node) != "Label") return@walk
        val name = widgetString(node, "fieldName") ?: return@walk
        val value = widgetString(node, "value") ?: return@walk
        labels[name] = value
    }
    val fields = mutableListOf<WidgetField>()
    walk(scope) { node, _ ->
        val name = widgetString(node, "name") ?: return@walk
        val type = widgetType(node) ?: return@walk
        if (type !in WIDGET_FIELD_TYPES && type != "ListView") return@walk
        val own = if (type == "ListView") widgetString(node, "title") else widgetString(node, "label")
        fields += WidgetField(name, type, labels[name] ?: own ?: name, node)
    }
    return fields
}

data class WidgetOption(val label: String, val value: String)

/** What a choice field offers: the options of a Select or RadioGroup, or the rows of a named list. */
fun widgetFieldOptions(node: WidgetNode): List<WidgetOption> {
    if (widgetType(node) == "ListView") {
        return widgetChildren(node).map { row ->
            val value = widgetString(row, "value").orEmpty()
            WidgetOption(widgetFirstText(row).ifEmpty { value }, value)
        }
    }
    return (node["options"] as? JsonArray).orEmpty().mapNotNull { item ->
        val option = item as? JsonObject ?: return@mapNotNull null
        val value = widgetString(option, "value") ?: return@mapNotNull null
        WidgetOption(widgetString(option, "label") ?: value, value)
    }
}

/** Whether a field offers Other: the person may answer in their own words. */
fun widgetTakesOther(node: WidgetNode): Boolean {
    if (node["allowOther"] != JsonPrimitive(true)) return false
    return when (widgetType(node)) {
        "Select", "RadioGroup" -> true
        "ListView" -> widgetString(node, "name") != null
        else -> false
    }
}

/** What each field holds before the person touches it. */
fun widgetInitialValues(scope: WidgetNode): Map<String, WidgetValue> =
    widgetFields(scope).associate { field ->
        field.name to if (field.type == "Checkbox") {
            WidgetValue.Flag(field.node["defaultChecked"] == JsonPrimitive(true))
        } else {
            WidgetValue.Text(widgetString(field.node, "defaultValue").orEmpty())
        }
    }

// MARK: - Pressing

private fun isEmptyField(field: WidgetField, values: Map<String, WidgetValue>, other: Map<String, String>): Boolean {
    other[field.name]?.let { return it.isBlank() }
    val value = values[field.name]
    return if (field.type == "Checkbox") value != WidgetValue.Flag(true) else (value as? WidgetValue.Text)?.value?.isBlank() != false
}

sealed interface WidgetPressPlan {
    /** A link or a thread jump: it runs on the phone and nothing is sent. */
    data class OpenOnPhone(val entry: WidgetActionEntry) : WidgetPressPlan

    /** A required field in the pressed scope is empty. */
    data class Missing(val field: WidgetField) : WidgetPressPlan

    data class Send(
        val entry: WidgetActionEntry,
        val values: Map<String, WidgetValue>?,
        val other: Map<String, String>?,
        /** The same press gives the same key, so a retry can reuse its send id. */
        val key: String,
    ) : WidgetPressPlan

    /** The card is settled, view-only, or has no such button. */
    data object Ignore : WidgetPressPlan
}

/** A widget in a group chat is view-only: a press there would speak for everyone. */
fun widgetViewOnly(chat: Chat): Boolean = chat is Chat.RoomChat

fun planWidgetPress(
    root: WidgetNode,
    ref: String,
    values: Map<String, WidgetValue>,
    other: Map<String, String>,
    settled: Boolean,
    viewOnly: Boolean,
): WidgetPressPlan {
    val entry = widgetActions(root).firstOrNull { it.ref == ref } ?: return WidgetPressPlan.Ignore
    if (entry.client) return if (entry.hiddenOnPhone) WidgetPressPlan.Ignore else WidgetPressPlan.OpenOnPhone(entry)
    if (settled || viewOnly) return WidgetPressPlan.Ignore
    var sending: Map<String, WidgetValue>? = null
    var sendingOther: Map<String, String>? = null
    if (entry.tone != WidgetTone.CANCEL) {
        val scope = widgetFields(widgetScope(root, ref), root)
        if (entry.tone != null) {
            scope.firstOrNull { it.node["required"] == JsonPrimitive(true) && isEmptyField(it, values, other) }
                ?.let { return WidgetPressPlan.Missing(it) }
        }
        sending = scope.associate { field ->
            field.name to if (other.containsKey(field.name)) {
                WidgetValue.Text("")
            } else {
                values[field.name] ?: if (field.type == "Checkbox") WidgetValue.Flag(false) else WidgetValue.Text("")
            }
        }
        val typed = scope.filter { other[it.name]?.isNotBlank() == true }
        if (typed.isNotEmpty()) sendingOther = typed.associate { it.name to other.getValue(it.name) }
    }
    val key = "$ref\n${sending?.let(::valuesJson)}\n${sendingOther?.let(::otherJson)}"
    return WidgetPressPlan.Send(entry, sending, sendingOther, key)
}

private fun valuesJson(values: Map<String, WidgetValue>) = JsonObject(values.mapValues { it.value.toJson() })

private fun otherJson(other: Map<String, String>) = JsonObject(other.mapValues { JsonPrimitive(it.value) })

/** One press, as the computer takes it. */
data class WidgetPress(
    val messageId: String,
    val ref: String,
    val rev: Int,
    val values: Map<String, WidgetValue>? = null,
    val other: Map<String, String>? = null,
    val sendId: String,
) {
    fun body(threadId: String): JsonObject = buildJsonObject {
        put("threadId", threadId)
        put("sendId", sendId)
        put(
            "widgetAction",
            buildJsonObject {
                put("messageId", messageId)
                put("ref", ref)
                put("rev", rev)
                values?.let { put("values", valuesJson(it)) }
                other?.let { put("other", otherJson(it)) }
            },
        )
    }
}

sealed interface WidgetPressResult {
    data object Sent : WidgetPressResult

    /** The computer said no, in [message]. The same press must not be sent again. */
    data class Refused(val message: String?) : WidgetPressResult

    /** No answer came. The same press may go again under the same send id. */
    data object Failed : WidgetPressResult
}

/** A press that got no answer goes again under the same send id, so the computer counts it once. */
class WidgetSendIds(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private var key: String? = null
    private var id: String? = null

    fun idFor(key: String): String {
        val kept = id
        if (kept != null && this.key == key) return kept
        return newId().also {
            this.key = key
            id = it
        }
    }

    fun clear() {
        key = null
        id = null
    }
}

// MARK: - Client actions

data class WidgetThreadTarget(val botId: String?, val groupId: String?, val threadId: String)

/** `open_url`: a web address from a bot, so only its scheme is checked here. */
fun widgetActionUrl(entry: WidgetActionEntry): String? {
    if (!entry.client || entry.action.type != "open_url") return null
    val url = ((entry.action.payload as? JsonObject)?.get("url") as? JsonPrimitive)?.takeIf { it.isString }?.content
    return url?.takeIf { WEB_URL.containsMatchIn(it) }
}

/** `open_thread`: the ids are written by a bot, so only their shape is checked. */
fun widgetThreadTarget(entry: WidgetActionEntry): WidgetThreadTarget? {
    if (!entry.client || entry.action.type != "open_thread") return null
    val payload = entry.action.payload as? JsonObject ?: return null
    fun id(key: String): String? {
        val value = payload[key]
        if (value == null || value is JsonNull) return null
        return (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { THREAD_ID.matches(it) } ?: ""
    }
    val threadId = id("threadId")?.takeIf { it.isNotEmpty() } ?: return null
    val botId = id("botId")
    val groupId = id("groupId")
    return when {
        botId == "" || groupId == "" -> null
        botId != null && groupId == null -> WidgetThreadTarget(botId, null, threadId)
        groupId != null && botId == null -> WidgetThreadTarget(null, groupId, threadId)
        else -> null
    }
}

// MARK: - Tables

/** A cell's words: the text of what it holds. */
fun widgetInlineText(node: WidgetNode): String = when (widgetType(node)) {
    "Text", "Title", "Caption", "Label" -> widgetString(node, "value").orEmpty()
    "Markdown" -> widgetString(node, "value").orEmpty().replace(Regex("\\s+"), " ")
    "Badge" -> widgetString(node, "label").orEmpty()
    "Button" -> widgetString(node, "label").orEmpty()
    "Image" -> widgetString(node, "alt").orEmpty()
    "Progress" -> "${((widgetNumber(node, "value") ?: 0.0) * 100).toInt()}%"
    else -> widgetChildren(node).map(::widgetInlineText).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** A Table node as the grid the chat already draws. The first row marked as a header names the columns. */
fun widgetTable(node: WidgetNode): TranscriptCard.Table {
    val tableRows = widgetChildren(node).take(WidgetLimits.TABLE_ROWS)
    val rows = tableRows.map { row -> widgetChildren(row).map(::widgetInlineText).take(WidgetLimits.TABLE_COLS) }
    val headerIndex = tableRows.indexOfFirst { it["header"] == JsonPrimitive(true) }
    val width = rows.maxOfOrNull { it.size } ?: 0
    val headers = if (headerIndex >= 0) rows[headerIndex] else List(width) { "" }
    val body = rows.filterIndexed { index, _ -> index != headerIndex }
    return TranscriptCard.Table(headers, body)
}

// MARK: - Charts

data class WidgetChartSeries(val type: String, val label: String, val stack: String?, val values: List<Double?>)

data class WidgetChartModel(val xKey: String, val categories: List<String>, val series: List<WidgetChartSeries>) {
    /** The data as a table, for a chart whose numbers cannot be drawn. */
    fun asTable(): TranscriptCard.Table = TranscriptCard.Table(
        headers = listOf(xKey) + series.map { it.label },
        rows = categories.indices.map { row ->
            listOf(categories[row]) + series.map { it.values.getOrNull(row)?.let(::formatWidgetNumber).orEmpty() }
        },
    )

    val drawable: Boolean get() = categories.isNotEmpty() && series.any { line -> line.values.any { it != null } }
}

fun formatWidgetNumber(value: Double): String =
    BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** A Chart node, up to the limits the computer keeps. Null when it has no rows, no series or no x axis. */
fun widgetChart(node: WidgetNode): WidgetChartModel? {
    val xKey = widgetObject(node, "xAxis")?.let { widgetString(it, "dataKey") } ?: return null
    val rows = (node["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.take(WidgetLimits.CHART_ROWS)
    val specs = (node["series"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.take(WidgetLimits.CHART_SERIES)
    if (rows.isEmpty() || specs.isEmpty()) return null
    val categories = rows.map { row ->
        val cell = row[xKey] as? JsonPrimitive
        when {
            cell == null || cell is JsonNull -> ""
            cell.isString -> cell.content
            else -> cell.doubleOrNull?.takeIf { it.isFinite() }?.let(::formatWidgetNumber) ?: cell.contentOrNull.orEmpty()
        }
    }
    val series = specs.mapNotNull { spec ->
        val key = widgetString(spec, "dataKey") ?: return@mapNotNull null
        WidgetChartSeries(
            type = widgetString(spec, "type") ?: "bar",
            label = widgetString(spec, "label") ?: key,
            stack = widgetString(spec, "stack"),
            values = rows.map { row ->
                (row[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
                    ?.coerceIn(-WidgetLimits.CHART_VALUE, WidgetLimits.CHART_VALUE)
            },
        )
    }
    if (series.isEmpty()) return null
    return WidgetChartModel(xKey, categories, series)
}

/** An `Image` source the phone may fetch: a secure web address and nothing else. */
fun widgetImageUrl(node: WidgetNode): String? =
    widgetString(node, "src")?.takeIf { it.startsWith("https://", ignoreCase = true) && it.length > "https://".length }

/** The weekdays a DayPicker holds, as `mon`..`sun`, in the order it draws them. */
fun widgetDays(days: String): List<String> {
    val picked = days.split(",").toSet()
    return listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").filter { it in picked }
}
