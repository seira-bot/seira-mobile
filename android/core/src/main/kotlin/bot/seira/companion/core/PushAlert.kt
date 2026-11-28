package bot.seira.companion.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * What a remote push says, after the data message passed validation. The relay
 * sends title and routing only: no bot name, no message text.
 *
 * Two shapes carry it. Version 1 puts every field in the data map. The older
 * control plane puts `{"v":1,...}` as JSON in `body` and `title`, `tag` and
 * `channelId` beside it. Anything else is not an alert.
 */
data class PushAlert(
    val kind: String,
    val botId: String,
    val threadId: String,
    val title: String,
    val tag: String,
    val channelId: String?,
    val pairing: String?,
    val roomId: String?,
    val requestId: String?,
) {
    val target: NotificationTarget? get() = NotificationTarget.from(botId, threadId, pairing)

    companion object {
        private val KIND = Regex("^[a-z][a-z-]{0,39}$")
        private val ID = Regex("^[\\w-]{1,120}$")
        private val TAG = Regex("^[\\w.-]{1,120}$")
        private val PAIRING = Regex("^[A-Za-z0-9_-]{8,64}$")
        private const val MAX_TITLE_CODE_POINTS = 200

        fun parse(data: Map<String, String>): PushAlert? {
            val fields = when (data["v"]) {
                "1" -> data
                null -> legacy(data) ?: return null
                else -> return null
            }
            val kind = fields["kind"]?.takeIf(KIND::matches) ?: return null
            val botId = fields["botId"]?.takeIf(ID::matches) ?: return null
            val threadId = fields["threadId"]?.takeIf(ID::matches) ?: return null
            val tag = fields["tag"]?.takeIf(TAG::matches) ?: return null
            val title = fields["title"]?.trim()?.takeIf {
                it.isNotEmpty() && it.codePointCount(0, it.length) <= MAX_TITLE_CODE_POINTS
            } ?: return null
            val pairing = optional(fields, "pairing", PAIRING) ?: return null
            val roomId = optional(fields, "roomId", ID) ?: return null
            val requestId = optional(fields, "requestId", ID) ?: return null
            return PushAlert(
                kind = kind,
                botId = botId,
                threadId = threadId,
                title = title,
                tag = tag,
                channelId = fields["channelId"],
                pairing = pairing.value,
                roomId = roomId.value,
                requestId = requestId.value,
            )
        }

        private class Field(val value: String?)

        /** A missing key is fine; a present key that fails its pattern fails the whole alert (null). */
        private fun optional(fields: Map<String, String>, key: String, pattern: Regex): Field? {
            val raw = fields[key] ?: return Field(null)
            return if (pattern.matches(raw)) Field(raw) else null
        }

        private fun legacy(data: Map<String, String>): Map<String, String>? {
            val raw = data["body"] ?: return null
            val body = try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return null
            if ((body["v"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull != 1) return null
            val inner = body.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it }
            }.toMap()
            return inner + listOf("title", "tag", "channelId").mapNotNull { key -> data[key]?.let { key to it } }
        }
    }
}
