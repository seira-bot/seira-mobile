package com.openmausbot.companion.ui

import com.openmausbot.companion.core.ChatAppDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ChatAppWebPolicyTest {
    private val host = ChatAppWebPolicy.HOST_URL

    @Test
    fun theMainFrameMayLoadOnlyTheHostPage() {
        assertTrue(ChatAppWebPolicy.allowsNavigation(host, isMainFrame = true))
        assertFalse(ChatAppWebPolicy.allowsNavigation("https://evil.example/", isMainFrame = true))
        assertFalse(ChatAppWebPolicy.allowsNavigation("about:blank", isMainFrame = true))
        assertFalse(ChatAppWebPolicy.allowsNavigation("about:srcdoc", isMainFrame = true))
        assertFalse(ChatAppWebPolicy.allowsNavigation("$host?x=1", isMainFrame = true))
        assertFalse(ChatAppWebPolicy.allowsNavigation("file:///android_asset/app/app-host.html", isMainFrame = true))
        assertFalse(ChatAppWebPolicy.allowsNavigation("intent://x#Intent;end", isMainFrame = true))
    }

    @Test
    fun aSubframeMayLoadOnlyItsInlineDocument() {
        assertTrue(ChatAppWebPolicy.allowsNavigation("about:srcdoc", isMainFrame = false))
        assertTrue(ChatAppWebPolicy.allowsNavigation("about:blank", isMainFrame = false))
        assertFalse(ChatAppWebPolicy.allowsNavigation(host, isMainFrame = false))
        assertFalse(ChatAppWebPolicy.allowsNavigation("https://cdn.jsdelivr.net/x", isMainFrame = false))
        assertFalse(ChatAppWebPolicy.allowsNavigation("javascript:alert(1)", isMainFrame = false))
        assertFalse(ChatAppWebPolicy.allowsNavigation("data:text/html,hi", isMainFrame = false))
    }

    private fun params(url: String?) = buildJsonObject { if (url != null) put("url", url) }

    @Test
    fun onlyAWebLinkIsOpened() {
        assertEquals(
            ChatAppWebPolicy.CallDecision.OpenLink("https://example.com/a?b=c"),
            ChatAppWebPolicy.decide("open-link", params("https://example.com/a?b=c")),
        )
        assertEquals(
            ChatAppWebPolicy.CallDecision.OpenLink("http://example.com"),
            ChatAppWebPolicy.decide("open-link", params("http://example.com")),
        )
        for (bad in listOf("javascript:alert(1)", "intent://x#Intent;end", "file:///etc/passwd", "content://x/y", "tel:123", "mailto:a@b.c", "https://", "https://exa mple.com", "")) {
            assertEquals(ChatAppWebPolicy.CallDecision.Refuse, ChatAppWebPolicy.decide("open-link", params(bad)), bad)
        }
        assertEquals(ChatAppWebPolicy.CallDecision.Refuse, ChatAppWebPolicy.decide("open-link", params(null)))
        assertEquals(ChatAppWebPolicy.CallDecision.Refuse, ChatAppWebPolicy.decide("open-link", buildJsonObject { put("url", 5) }))
        assertEquals(ChatAppWebPolicy.CallDecision.Refuse, ChatAppWebPolicy.decide("open-link", params("https://example.com/" + "a".repeat(2_100))))
    }

    @Test
    fun everyOtherCallIsRefused() {
        for (op in listOf("rpc", "message", "context", "teardown", "", "OPEN-LINK")) {
            assertEquals(ChatAppWebPolicy.CallDecision.Refuse, ChatAppWebPolicy.decide(op, params("https://example.com")), op)
        }
    }

    @Test
    fun pageMessagesParseAndGarbageIsIgnored() {
        assertEquals(ChatAppWebPolicy.PageMessage.Ready, ChatAppWebPolicy.parse("""{"v":1,"type":"ready"}"""))
        assertEquals(
            ChatAppWebPolicy.PageMessage.Call("c1", "open-link", params("https://example.com")),
            ChatAppWebPolicy.parse("""{"v":1,"type":"call","callId":"c1","op":"open-link","params":{"url":"https://example.com"}}"""),
        )
        for (raw in listOf("", "nope", "[]", """{"type":"ready"}""", """{"v":2,"type":"ready"}""", """{"v":1,"type":"call"}""", """{"v":1,"type":"size","height":10}""")) {
            assertEquals(ChatAppWebPolicy.PageMessage.Other, ChatAppWebPolicy.parse(raw), raw)
        }
    }

    @Test
    fun repliesCarryTheCallIdAndAResultOrARefusal() {
        val ok = Json.parseToJsonElement(ChatAppWebPolicy.replyMessage("c1", ChatAppWebPolicy.CallDecision.OpenLink("https://example.com"))).jsonObject
        assertEquals("reply", ok.getValue("call").jsonPrimitive.content)
        assertEquals("c1", ok.getValue("args").jsonArray[0].jsonPrimitive.content)
        assertTrue("result" in ok.getValue("args").jsonArray[1].jsonObject)
        val refused = Json.parseToJsonElement(ChatAppWebPolicy.replyMessage("c2", ChatAppWebPolicy.CallDecision.Refuse)).jsonObject
        assertEquals("-32000", refused.getValue("args").jsonArray[1].jsonObject.getValue("error").jsonObject.getValue("code").jsonPrimitive.content)
    }

    @Test
    fun theLoadMessageCarriesTheDocumentAndTheWebViewHeightAndNoSecret() {
        val document = ChatAppDocument(buildJsonObject { put("srcdoc", "<p>hi</p>"); put("messageId", "m1") })
        val text = ChatAppWebPolicy.loadMessage(document, 640, dark = true, locale = "en-US", timeZone = "UTC", hostVersion = "android-1.0", engagedAtMs = 5L)
        val args = Json.parseToJsonElement(text).jsonObject.getValue("args").jsonArray[0].jsonObject
        assertEquals("<p>hi</p>", args.getValue("doc").jsonObject.getValue("srcdoc").jsonPrimitive.content)
        val limits = args.getValue("limits").jsonObject
        assertEquals("640", limits.getValue("maxHeight").jsonPrimitive.content)
        assertEquals("640", limits.getValue("height").jsonPrimitive.content)
        assertEquals("dark", args.getValue("hostContext").jsonObject.getValue("theme").jsonPrimitive.content)
        assertEquals("mobile", args.getValue("hostContext").jsonObject.getValue("platform").jsonPrimitive.content)
        assertFalse(text.contains("Bearer", ignoreCase = true))
        assertFalse(text.contains("token", ignoreCase = true))
        assertEquals("engaged", Json.parseToJsonElement(ChatAppWebPolicy.engagedMessage(9)).jsonObject.getValue("call").jsonPrimitive.content)
    }

    @Test
    fun viewOnlyIsAppliedBeforeAMcpOrFolderPageIsRefused() {
        val mcp = ChatAppDocument(buildJsonObject { put("srcdoc", "x"); put("tool", buildJsonObject { put("name", "t") }) })
        assertEquals(ChatAppLoad.Unavailable, chatAppLoadOf(mcp))
        val html = ChatAppDocument(buildJsonObject { put("srcdoc", "<p>hi</p>") })
        val ready = chatAppLoadOf(html) as ChatAppLoad.Ready
        assertFalse(ready.document.json.getValue("can").jsonObject.getValue("message").jsonPrimitive.content.toBoolean())
        assertTrue(ready.document.json.getValue("can").jsonObject.getValue("openLinks").jsonPrimitive.content.toBoolean())
        assertEquals(ChatAppLoad.Unavailable, chatAppLoadOf(ChatAppDocument(JsonObject(emptyMap()))))
    }

    @Test
    fun theRowTextDropsTheMarkerLine() {
        assertEquals("Three cups so far.", chatAppPreviewText("[App] Coffee counter\nThree cups so far."))
        assertEquals("A custom note", chatAppPreviewText("A custom note"))
        assertEquals("", chatAppPreviewText("[App] Only a title"))
        assertEquals("", chatAppPreviewText(null))
    }
}
