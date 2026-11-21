package com.openmausbot.companion.ui

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatAppWebViewConfigTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun request(url: String, mainFrame: Boolean = true) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun client(allowed: Set<String>) = ChatAppWebViewClient(hostAssetLoader(context), allowed)

    @Test
    fun theWebViewIsLockedDown() {
        val webView = WebView(context)
        lockDownChatAppWebView(webView)
        val settings = webView.settings
        assertTrue(settings.javaScriptEnabled)
        assertFalse(settings.domStorageEnabled)
        assertFalse(settings.allowFileAccess)
        assertFalse(settings.allowContentAccess)
        assertFalse(settings.javaScriptCanOpenWindowsAutomatically)
        assertFalse(settings.supportMultipleWindows())
        assertTrue(settings.mediaPlaybackRequiresUserGesture)
        assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.mixedContentMode)
        assertFalse(android.webkit.CookieManager.getInstance().acceptCookie())
    }

    @Test
    fun theHostPageIsServedFromTheBundledAsset() {
        val response = assertNotNull(client(emptySet()).shouldInterceptRequest(WebView(context), request(ChatAppWebPolicy.HOST_URL)))
        assertEquals(200, response.statusCode)
        val head = response.data.readBytes().toString(Charsets.UTF_8).take(40)
        assertTrue(head.startsWith("<!-- seira app-host v1 sha256:"), head)
    }

    @Test
    fun nothingElseOnTheHostOriginIsServed() {
        val view = WebView(context)
        val web = client(emptySet())
        for (path in listOf("/app/other.html", "/assets/app/app-host.html", "/app/", "/app/../x", "/")) {
            val response = assertNotNull(web.shouldInterceptRequest(view, request("${ChatAppWebPolicy.HOST_ORIGIN}$path")), path)
            assertTrue(response.statusCode == 403 || response.statusCode == 404, "$path ${response.statusCode}")
        }
    }

    @Test
    fun aRequestOutsideTheDocumentsNetworkAnswers403() {
        val view = WebView(context)
        val allowed = client(setOf("https://cdn.jsdelivr.net"))
        assertNull(allowed.shouldInterceptRequest(view, request("https://cdn.jsdelivr.net/npm/x.js", mainFrame = false)))
        assertEquals(403, allowed.shouldInterceptRequest(view, request("https://evil.example/x.js", mainFrame = false))?.statusCode)
        assertEquals(403, allowed.shouldInterceptRequest(view, request("http://cdn.jsdelivr.net/npm/x.js", mainFrame = false))?.statusCode)
        assertEquals(403, allowed.shouldInterceptRequest(view, request("file:///data/data/x", mainFrame = false))?.statusCode)
        val closed = client(emptySet())
        assertEquals(403, closed.shouldInterceptRequest(view, request("https://cdn.jsdelivr.net/npm/x.js", mainFrame = false))?.statusCode)
    }

    @Test
    fun navigationFollowsThePolicyForMainFrameAndSubframe() {
        val view = WebView(context)
        val web = client(emptySet())
        assertFalse(web.shouldOverrideUrlLoading(view, request(ChatAppWebPolicy.HOST_URL, mainFrame = true)))
        assertTrue(web.shouldOverrideUrlLoading(view, request("https://evil.example/", mainFrame = true)))
        assertFalse(web.shouldOverrideUrlLoading(view, request("about:srcdoc", mainFrame = false)))
        assertFalse(web.shouldOverrideUrlLoading(view, request("about:blank", mainFrame = false)))
        assertTrue(web.shouldOverrideUrlLoading(view, request("https://evil.example/", mainFrame = false)))
        assertTrue(web.shouldOverrideUrlLoading(view, request(ChatAppWebPolicy.HOST_URL, mainFrame = false)))
    }

    @Test
    fun theWebViewSourceNeverAddsAJavaScriptInterfaceOrPassesAToken() {
        val root = File("src/main/kotlin/com/openmausbot/companion/ui").takeIf(File::isDirectory)
            ?: File("app/src/main/kotlin/com/openmausbot/companion/ui")
        val source = File(root, "ChatAppWebView.kt").readText() + File(root, "ChatAppScreen.kt").readText()
        assertFalse(source.contains("addJavascriptInterface"))
        assertFalse(source.contains("Authorization"))
        assertFalse(source.contains("file:///android_asset"))
        assertFalse(source.contains("addCookie") || source.contains("setCookie"))
    }
}
