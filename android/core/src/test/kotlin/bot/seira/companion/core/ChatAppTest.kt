package bot.seira.companion.core

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class ChatAppTest {
    private val messages by lazy { decodeFixture<ThreadPage>("app-messages").messages.associateBy { it.id } }
    private val documents by lazy {
        Json.parseToJsonElement(fixtureText("app-documents")).jsonObject.mapValues { ChatAppDocument(it.value.jsonObject) }
    }

    @Test
    fun appMessagesDecodeWithTheirPayload() {
        val counter = assertNotNull(messages.getValue("app-counter").app)
        assertEquals(Message.Kind.APP, messages.getValue("app-counter").kind)
        assertEquals("Coffee counter", counter.title)
        assertEquals("html", counter.source)
        assertEquals(0, counter.rev)
        assertEquals(200.0, counter.height)
        assertEquals(1, assertNotNull(messages.getValue("app-report").app).rev)
        val mcp = assertNotNull(messages.getValue("app-mcp-basic").app)
        assertEquals("mcp", mcp.source)
        assertEquals("get-time", mcp.mcp?.tool)
        assertEquals("too-large", messages.getValue("app-too-large").app?.blocked)
        assertEquals("[App] Coffee counter", messages.getValue("app-counter").text?.lineSequence()?.first())
    }

    @Test
    fun anUnreadablePayloadLeavesTheMessageTextAndTheRestOfThePage() {
        val broken = messages.getValue("app-broken")
        assertNull(broken.app)
        assertEquals("An app this build cannot read", broken.text)
        assertEquals(Message.Kind.TEXT, messages.getValue("app-sent").kind)
        assertNotNull(messages.getValue("app-future").app)
    }

    @Test
    fun onlyAStoredHtmlPageOffersOpen() {
        for (id in listOf("app-counter", "app-chart", "app-report", "app-fragment", "app-game")) {
            assertTrue(messages.getValue(id).app!!.isOpenablePage, id)
        }
        for (id in listOf("app-mcp-basic", "app-mcp-excalidraw", "app-mcp-debugError", "app-too-large", "app-future")) {
            assertFalse(messages.getValue(id).app!!.isOpenablePage, id)
        }
        assertFalse(ChatApp(source = "html", doc = "abc", live = true).isOpenablePage)
        assertFalse(ChatApp(source = "html", doc = "abc", blocked = "unavailable").isOpenablePage)
    }

    @Test
    fun viewOnlyKeepsOnlyOpenLinks() {
        val document = documents.getValue("app-counter")
        assertTrue(document.json.getValue("can").jsonObject.getValue("message").jsonPrimitive.boolean)
        val limited = document.viewOnly()
        val can = limited.json.getValue("can").jsonObject
        assertEquals(
            mapOf("serverTools" to false, "serverResources" to false, "message" to false, "modelContext" to false, "openLinks" to true),
            can.mapValues { it.value.jsonPrimitive.boolean },
        )
        assertEquals(document.json.getValue("srcdoc"), limited.json.getValue("srcdoc"))
        assertEquals(document.json.getValue("network"), limited.json.getValue("network"))
        val mcp = documents.getValue("app-mcp-basic").viewOnly()
        assertFalse(mcp.json.getValue("can").jsonObject.getValue("serverTools").jsonPrimitive.boolean)
    }

    @Test
    fun foldersAndMcpAppsAreRecognisedFromTheirDocument() {
        for (id in listOf("app-counter", "app-chart", "app-report", "app-fragment", "app-game")) {
            val document = documents.getValue(id)
            assertFalse(document.isMcpApp, id)
            assertFalse(document.isFolderPage, id)
            assertTrue(document.canOpenOnPhone, id)
        }
        for (id in listOf("app-mcp-basic", "app-mcp-excalidraw", "app-mcp-debugError")) {
            assertTrue(documents.getValue(id).isMcpApp, id)
            assertFalse(documents.getValue(id).canOpenOnPhone, id)
        }
        val folder = ChatAppDocument(
            buildJsonObject {
                put("srcdoc", "<base href=\"https://pc.example/files/\">")
                put("csp", "default-src 'none'; base-uri https://pc.example/files/")
            },
        )
        assertTrue(folder.isFolderPage)
        assertFalse(folder.canOpenOnPhone)
    }

    @Test
    fun allowedOriginsAreTheNetworkTheDocumentNames() {
        assertEquals(
            setOf("https://cdnjs.cloudflare.com", "https://cdn.jsdelivr.net", "https://unpkg.com", "https://fonts.googleapis.com", "https://fonts.gstatic.com"),
            documents.getValue("app-counter").allowedOrigins,
        )
        assertTrue(documents.getValue("app-mcp-basic").allowedOrigins.isEmpty())
    }

    @Test
    fun networkPolicyAllowsOnlyTheHostAndTheDocumentsOrigins() {
        val host = "https://appassets.androidplatform.net"
        val allowed = setOf("https://cdn.jsdelivr.net")
        assertTrue(ChatAppNetworkPolicy.allows("https://appassets.androidplatform.net/app/app-host.html", host, allowed))
        assertTrue(ChatAppNetworkPolicy.allows("https://cdn.jsdelivr.net/npm/x.js", host, allowed))
        assertTrue(ChatAppNetworkPolicy.allows("https://cdn.jsdelivr.net:443/npm/x.js", host, allowed))
        assertTrue(ChatAppNetworkPolicy.allows("data:text/plain,hi", host, allowed))
        assertFalse(ChatAppNetworkPolicy.allows("https://evil.example/x.js", host, allowed))
        assertFalse(ChatAppNetworkPolicy.allows("https://cdn.jsdelivr.net.evil.example/x.js", host, allowed))
        assertFalse(ChatAppNetworkPolicy.allows("http://cdn.jsdelivr.net/x.js", host, allowed))
        assertFalse(ChatAppNetworkPolicy.allows("https://cdn.jsdelivr.net:8443/x.js", host, allowed))
        assertFalse(ChatAppNetworkPolicy.allows("file:///etc/passwd", host, allowed))
        assertFalse(ChatAppNetworkPolicy.allows("not a url", host, allowed))
        assertFalse(ChatAppNetworkPolicy.allows("https://cdn.jsdelivr.net/x.js", host, emptySet()))
    }

    @Test
    fun bundledHostPageMatchesTheHashTheFixturesName() {
        val file = listOf("../app/src/main/assets/app/app-host.html", "app/src/main/assets/app/app-host.html")
            .map(::File).first(File::isFile)
        val html = file.readText(Charsets.UTF_8)
        val marker = Regex("^<!-- seira app-host v1 sha256:([0-9a-f]{64}) -->\n").find(html)
        assertNotNull(marker, "the host page lost its hash line")
        val body = html.substring(marker.value.length)
        val actual = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val named = Json.parseToJsonElement(fixtureText("app-expectations")).jsonObject.getValue("appHostSha256").jsonPrimitive.content
        assertEquals(named, marker.groupValues[1])
        assertEquals(named, actual)
    }

    @Test
    fun clientFetchesTheDocumentWithItsTokenAndNoQuery() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"v":1,"messageId":"m-1","rev":0,"srcdoc":"<p>hi</p>","csp":"","network":{"connect":[],"resources":[]},"can":{}}"""))
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "paired-token")
            val document = client.appDocument("thread-1", "m-1")
            assertEquals("<p>hi</p>", document.json.getValue("srcdoc").jsonPrimitive.content)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/threads/thread-1/messages/m-1/app", request.path)
            assertEquals("Bearer paired-token", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
