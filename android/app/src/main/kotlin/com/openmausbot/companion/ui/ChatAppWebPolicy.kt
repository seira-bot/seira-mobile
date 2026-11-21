package com.openmausbot.companion.ui

import com.openmausbot.companion.core.ChatAppDocument
import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * What a bot's page may do inside its web view, as pure functions. The web view classes call these and nothing else
 * decides: the page itself is untrusted.
 */
object ChatAppWebPolicy {
    const val HOST_ORIGIN = "https://appassets.androidplatform.net"
    const val HOST_PATH = "/app/app-host.html"
    const val HOST_URL = HOST_ORIGIN + HOST_PATH

    private const val MAXIMUM_LINK_CHARACTERS = 2_048
    private const val REFUSED = -32000

    /** The main frame loads the host page once; the page's own frame loads only its inline document. */
    fun allowsNavigation(url: String, isMainFrame: Boolean): Boolean =
        if (isMainFrame) url == HOST_URL else url == "about:srcdoc" || url == "about:blank"

    sealed interface PageMessage {
        data object Ready : PageMessage
        data class Call(val callId: String, val op: String, val params: JsonObject) : PageMessage
        data object Other : PageMessage
    }

    fun parse(raw: String): PageMessage {
        val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return PageMessage.Other
        if ((root["v"] as? JsonPrimitive)?.doubleOrNull != 1.0) return PageMessage.Other
        return when ((root["type"] as? JsonPrimitive)?.contentOrNull) {
            "ready" -> PageMessage.Ready
            "call" -> {
                val callId = (root["callId"] as? JsonPrimitive)?.contentOrNull ?: return PageMessage.Other
                val op = (root["op"] as? JsonPrimitive)?.contentOrNull ?: return PageMessage.Other
                PageMessage.Call(callId, op, root["params"] as? JsonObject ?: JsonObject(emptyMap()))
            }
            else -> PageMessage.Other
        }
    }

    sealed interface CallDecision {
        data class OpenLink(val url: String) : CallDecision
        data object Refuse : CallDecision
    }

    /** Only `open-link` is answered, and only for a web address. */
    fun decide(op: String, params: JsonObject): CallDecision {
        if (op != "open-link") return CallDecision.Refuse
        val url = (params["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return CallDecision.Refuse
        return if (isWebLink(url)) CallDecision.OpenLink(url) else CallDecision.Refuse
    }

    fun isWebLink(url: String): Boolean {
        if (url.length > MAXIMUM_LINK_CHARACTERS || url.any { it.isWhitespace() || it.isISOControl() }) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
    }

    fun replyMessage(callId: String, decision: CallDecision): String = buildJsonObject {
        put("v", 1)
        put("call", "reply")
        putJsonArray("args") {
            add(JsonPrimitive(callId))
            add(
                when (decision) {
                    is CallDecision.OpenLink -> buildJsonObject { put("result", buildJsonObject { }) }
                    CallDecision.Refuse -> buildJsonObject {
                        put("error", buildJsonObject {
                            put("code", REFUSED)
                            put("message", "This page can't do that here.")
                        })
                    }
                },
            )
        }
    }.toString()

    /** The page's frame is as tall as the web view, so a long page scrolls inside it. */
    fun loadMessage(
        document: ChatAppDocument,
        heightCssPixels: Int,
        dark: Boolean,
        locale: String,
        timeZone: String,
        hostVersion: String,
        engagedAtMs: Long,
    ): String = buildJsonObject {
        put("v", 1)
        put("call", "load")
        putJsonArray("args") {
            add(
                buildJsonObject {
                    put("v", 1)
                    put("doc", document.json)
                    put(
                        "hostContext",
                        buildJsonObject {
                            put("theme", if (dark) "dark" else "light")
                            put("displayMode", "inline")
                            putJsonArray("availableDisplayModes") { add(JsonPrimitive("inline")) }
                            put("platform", "mobile")
                            put("locale", locale)
                            put("timeZone", timeZone)
                            put("deviceCapabilities", buildJsonObject { put("touch", true); put("hover", false) })
                            put("safeAreaInsets", buildJsonObject { put("top", 0); put("right", 0); put("bottom", 0); put("left", 0) })
                        },
                    )
                    put("limits", buildJsonObject { put("maxHeight", heightCssPixels); put("height", heightCssPixels) })
                    put("engagedAt", engagedAtMs)
                    put("hostVersion", hostVersion)
                },
            )
        }
    }.toString()

    fun engagedMessage(atMs: Long): String = buildJsonObject {
        put("v", 1)
        put("call", "engaged")
        putJsonArray("args") { add(JsonPrimitive(atMs)) }
    }.toString()
}
