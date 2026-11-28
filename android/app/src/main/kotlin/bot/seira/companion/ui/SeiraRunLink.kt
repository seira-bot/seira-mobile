package bot.seira.companion.ui

import bot.seira.companion.core.Connection
import bot.seira.companion.core.PairingInvite
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface SeiraRunRoute {
    data class Pair(val inviteUrl: String) : SeiraRunRoute
    data class SwitchTo(val connectionId: String) : SeiraRunRoute
    data class FillAddress(val origin: String) : SeiraRunRoute
    data class OpenInBrowser(val url: String) : SeiraRunRoute
    data object Ignore : SeiraRunRoute
}

/** Routes an `https://c-<32 hex>.seira.run` link that Android App Links delivered. */
internal object SeiraRunLink {
    private val HOST = Regex("^c-[0-9a-f]{32}\\.seira\\.run$")
    private val PAIR_PATHS = setOf("/pair", "/pair/")
    private val EMPTY_PATHS = setOf("", "/")
    private val PAIR_KEYS = setOf("code", "endpoints")

    fun route(url: String, saved: List<Connection>): SeiraRunRoute {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return SeiraRunRoute.Ignore
        val host = uri.host?.lowercase(Locale.ROOT) ?: return SeiraRunRoute.Ignore
        if (!uri.scheme.equals("https", ignoreCase = true) || !HOST.matches(host) ||
            uri.rawUserInfo != null || uri.port != -1
        ) return SeiraRunRoute.Ignore

        val path = uri.rawPath.orEmpty()
        val onPairPath = path in PAIR_PATHS
        val onOriginPath = onPairPath || path in EMPTY_PATHS
        val origin = "https://$host"
        val fragment = uri.rawFragment
        val keys = fragment?.let(::fragmentKeys).orEmpty()

        // A pairing code never leaves the app. A link that carries a `code` key is
        // never given to the browser, whatever its path or its other keys are.
        if ("code" in keys) {
            if (!onOriginPath) return SeiraRunRoute.Ignore
            if (!onPairPath || keys.any { it !in PAIR_KEYS }) return SeiraRunRoute.FillAddress(origin)
            val candidate = if (path == "/pair/") url.trim().replaceFirst("/pair/", "/pair") else url.trim()
            return if (PairingInvite.parse(candidate) != null) {
                SeiraRunRoute.Pair(candidate)
            } else {
                SeiraRunRoute.FillAddress(origin)
            }
        }

        if (!onOriginPath || fragment != null) return SeiraRunRoute.OpenInBrowser(url)

        val known = saved.firstOrNull { matches(it, origin) }
        return if (known != null) SeiraRunRoute.SwitchTo(known.id) else SeiraRunRoute.FillAddress(origin)
    }

    /** The fragment keys, as a browser reads them: percent-decoded and compared without case. */
    private fun fragmentKeys(fragment: String): List<String> = fragment.split('&').map { part ->
        val raw = part.substringBefore('=')
        runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw).lowercase(Locale.ROOT)
    }

    fun matches(connection: Connection, origin: String): Boolean {
        val wanted = origin.trimEnd('/').lowercase(Locale.ROOT)
        val urls = buildList {
            connection.activeEndpoint?.let { add(it.url) }
            connection.endpoints?.forEach { add(it.url) }
        }
        if (urls.any { it.trimEnd('/').lowercase(Locale.ROOT) == wanted }) return true
        return urls.isEmpty() && "https://${connection.host.lowercase(Locale.ROOT)}" == wanted
    }
}

/**
 * An address (never a credential) that a link handed to the pairing screen.
 * Kept in memory only. The screen takes it once, within [MAX_AGE_MILLIS] of the
 * offer, so a link that never opened the screen cannot fill a later one.
 */
internal object PairingAddressHandoff {
    const val MAX_AGE_MILLIS = 60_000L

    internal var clock: () -> Long = { System.nanoTime() / 1_000_000 }

    private class Offer(val origin: String, val atMillis: Long)

    private val pending = MutableStateFlow<Offer?>(null)
    private val current = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = current.asStateFlow()

    fun offer(origin: String) {
        pending.value = Offer(origin, clock())
        current.value = origin
    }

    fun take(): String? {
        while (true) {
            val offer = pending.value ?: return null
            if (!pending.compareAndSet(offer, null)) continue
            current.value = null
            return offer.origin.takeIf { clock() - offer.atMillis <= MAX_AGE_MILLIS }
        }
    }
}
