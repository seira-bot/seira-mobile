package com.openmausbot.companion.core

import java.net.URI
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The row of a bot's page, as the server sends it in a message of kind `app`. */
@Serializable
data class ChatApp(
    val source: String = "",
    val title: String = "",
    val doc: String = "",
    val rev: Int = 0,
    val bytes: Long = 0,
    val height: Double? = null,
    val border: Boolean = true,
    val blocked: String? = null,
    val live: Boolean? = null,
    val mcp: ChatAppMcp? = null,
) {
    /** One HTML page whose document a phone can show: not stored, blocked, from a folder that reloads, or an MCP app. */
    val isOpenablePage: Boolean
        get() = source == "html" && doc.isNotEmpty() && blocked == null && mcp == null && live != true
}

@Serializable
data class ChatAppMcp(
    val server: String? = null,
    val serverTitle: String? = null,
    val tool: String? = null,
)

/** A payload this build cannot read is no app: the message keeps its text and the rest of the page still decodes. */
internal object ChatAppSerializer : KSerializer<ChatApp?> {
    override val descriptor: SerialDescriptor = ChatApp.serializer().nullable.descriptor

    override fun deserialize(decoder: Decoder): ChatApp? {
        val json = decoder as? JsonDecoder ?: return null
        val element = json.decodeJsonElement()
        return try {
            json.json.decodeFromJsonElement(ChatApp.serializer(), element)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: ChatApp?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(ChatApp.serializer(), value)
    }
}

/**
 * The AppDocument the server returns for one app message, kept as it came so the host page gets it unchanged.
 * It holds the page's HTML, so it never goes in a log or on disk.
 */
data class ChatAppDocument(val json: JsonObject) {
    val isMcpApp: Boolean get() = json["tool"] is JsonObject

    /** A folder page loads its files from the computer, which a phone does not serve. */
    val isFolderPage: Boolean
        get() {
            val csp = (json["csp"] as? JsonPrimitive)?.contentOrNull ?: return false
            val baseUri = csp.split(";").map(String::trim).firstOrNull { it.startsWith("base-uri ") } ?: return false
            return baseUri.removePrefix("base-uri ").trim() != "'none'"
        }

    val canOpenOnPhone: Boolean get() = !isMcpApp && !isFolderPage && (json["srcdoc"] as? JsonPrimitive)?.contentOrNull?.isNotEmpty() == true

    /** The origins the page may reach: `network.connect` and `network.resources`. */
    val allowedOrigins: Set<String>
        get() {
            val network = json["network"] as? JsonObject ?: return emptySet()
            return listOf("connect", "resources").flatMap { key ->
                runCatching { network[key]?.jsonArray.orEmpty().map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
            }.toSet()
        }

    /** The page may only open links: it cannot send messages, change what the bot knows, or call a server. */
    fun viewOnly(): ChatAppDocument = ChatAppDocument(
        JsonObject(
            json + ("can" to buildJsonObject {
                put("serverTools", false)
                put("serverResources", false)
                put("message", false)
                put("modelContext", false)
                put("openLinks", true)
            }),
        ),
    )
}

/** Whether a request to [url] may leave the web view: the host page's own origin, `data:`/`blob:`/`about:`, or an allowed https origin. */
object ChatAppNetworkPolicy {
    fun allows(url: String, hostOrigin: String, allowedOrigins: Set<String>): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "data" || scheme == "blob" || scheme == "about") return true
        if (scheme != "https") return false
        val origin = originOf(uri) ?: return false
        return origin == hostOrigin || origin in allowedOrigins.mapNotNull(::originOfText)
    }

    private fun originOfText(text: String): String? = runCatching { URI(text) }.getOrNull()?.let(::originOf)

    private fun originOf(uri: URI): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port == -1 || (scheme == "https" && uri.port == 443)) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }
}
