package com.openmausbot.companion.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Message as WebMessage
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.JsPromptResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.openmausbot.companion.core.ChatAppDocument
import com.openmausbot.companion.core.ChatAppNetworkPolicy
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Locale
import java.util.TimeZone

private const val BRIDGE_NAME = "appHost"
private const val HOST_ASSET = "app/app-host.html"

/**
 * A bot's page in a locked-down web view. The device token, cookies and files never reach it: it loads only the
 * bundled host page, which holds the page's own sandboxed frame.
 *
 * [onUnsupported] runs when this phone's web view cannot carry the page bridge.
 */
@Composable
fun ChatAppWebView(
    document: ChatAppDocument,
    heightDp: Int,
    dark: Boolean,
    hostVersion: String,
    onOpenLink: (String) -> Unit,
    onUnsupported: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = remember(document) { ChatAppWebSession(document, heightDp, dark, hostVersion, onOpenLink) }
    AndroidView(
        modifier = modifier,
        factory = { context -> createChatAppWebView(context, session, onUnsupported) },
        onRelease = { view -> releaseChatAppWebView(view) },
    )
}

internal class ChatAppWebSession(
    val document: ChatAppDocument,
    val heightDp: Int,
    val dark: Boolean,
    val hostVersion: String,
    val onOpenLink: (String) -> Unit,
) {
    @Volatile var proxy: JavaScriptReplyProxy? = null
}

/** One call: every setting that keeps the page inside its frame. */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
internal fun lockDownChatAppWebView(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        // The host page and the page's opaque-origin frame keep nothing, so storage stays off.
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = true
        safeBrowsingEnabled = true
        setGeolocationEnabled(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
        cacheMode = WebSettings.LOAD_NO_CACHE
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(false)
        setAcceptThirdPartyCookies(webView, false)
    }
    webView.isHorizontalScrollBarEnabled = false
    webView.overScrollMode = android.view.View.OVER_SCROLL_NEVER
}

@SuppressLint("ClickableViewAccessibility")
private fun createChatAppWebView(context: Context, session: ChatAppWebSession, onUnsupported: () -> Unit): WebView {
    val webView = WebView(context)
    webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    lockDownChatAppWebView(webView)
    webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    webView.webChromeClient = ChatAppChromeClient()
    webView.webViewClient = ChatAppWebViewClient(hostAssetLoader(context), session.document.allowedOrigins)

    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        onUnsupported()
        return webView
    }
    // The object exists only in frames of the host page's origin, so the page's own frame never sees it.
    WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, setOf(ChatAppWebPolicy.HOST_ORIGIN)) { view, message, sourceOrigin, isMainFrame, replyProxy ->
        if (!isMainFrame || sourceOrigin.toString() != ChatAppWebPolicy.HOST_ORIGIN) return@addWebMessageListener
        val raw = message.data ?: return@addWebMessageListener
        handlePageMessage(session, raw, replyProxy)
    }
    webView.setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            session.proxy?.postMessage(ChatAppWebPolicy.engagedMessage(System.currentTimeMillis()))
        }
        false
    }
    webView.loadUrl(ChatAppWebPolicy.HOST_URL)
    return webView
}

private fun handlePageMessage(session: ChatAppWebSession, raw: String, replyProxy: JavaScriptReplyProxy) {
    when (val message = ChatAppWebPolicy.parse(raw)) {
        ChatAppWebPolicy.PageMessage.Ready -> {
            session.proxy = replyProxy
            replyProxy.postMessage(
                ChatAppWebPolicy.loadMessage(
                    document = session.document,
                    heightCssPixels = session.heightDp,
                    dark = session.dark,
                    locale = Locale.getDefault().toLanguageTag(),
                    timeZone = TimeZone.getDefault().id,
                    hostVersion = session.hostVersion,
                    engagedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        is ChatAppWebPolicy.PageMessage.Call -> {
            val decision = ChatAppWebPolicy.decide(message.op, message.params)
            if (decision is ChatAppWebPolicy.CallDecision.OpenLink) session.onOpenLink(decision.url)
            replyProxy.postMessage(ChatAppWebPolicy.replyMessage(message.callId, decision))
        }
        ChatAppWebPolicy.PageMessage.Other -> Unit
    }
}

private fun releaseChatAppWebView(view: WebView) {
    view.stopLoading()
    view.setOnTouchListener(null)
    view.loadUrl("about:blank")
    (view.parent as? ViewGroup)?.removeView(view)
    view.destroy()
}

/** Serves exactly the bundled host page, and nothing else from the app's assets. */
private class HostPageHandler(private val context: Context) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        if (path != "app-host.html") return null
        return try {
            WebResourceResponse("text/html", "utf-8", context.applicationContext.assets.open(HOST_ASSET))
        } catch (_: IOException) {
            null
        }
    }
}

internal fun hostAssetLoader(context: Context): WebViewAssetLoader =
    WebViewAssetLoader.Builder()
        .setDomain(Uri.parse(ChatAppWebPolicy.HOST_ORIGIN).host!!)
        .addPathHandler("/app/", HostPageHandler(context))
        .build()

private fun blocked(status: Int, reason: String) =
    WebResourceResponse("text/plain", "utf-8", status, reason, emptyMap(), ByteArrayInputStream(ByteArray(0)))

internal class ChatAppWebViewClient(
    private val assets: WebViewAssetLoader,
    private val allowedOrigins: Set<String>,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !ChatAppWebPolicy.allowsNavigation(request.url.toString(), request.isForMainFrame)

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (url.startsWith(ChatAppWebPolicy.HOST_ORIGIN)) {
            return if (request.url.path == ChatAppWebPolicy.HOST_PATH) {
                assets.shouldInterceptRequest(request.url) ?: blocked(404, "Not Found")
            } else {
                blocked(403, "Forbidden")
            }
        }
        return if (ChatAppNetworkPolicy.allows(url, ChatAppWebPolicy.HOST_ORIGIN, allowedOrigins)) null else blocked(403, "Forbidden")
    }
}

internal class ChatAppChromeClient : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
        callback.invoke(origin, false, false)

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        filePathCallback.onReceiveValue(null)
        return true
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: WebMessage): Boolean = false

    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String?, result: JsPromptResult): Boolean {
        result.cancel()
        return true
    }

    override fun onJsBeforeUnload(view: WebView, url: String, message: String, result: JsResult): Boolean {
        result.cancel()
        return true
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
}
