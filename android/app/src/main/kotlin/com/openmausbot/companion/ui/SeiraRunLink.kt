package com.openmausbot.companion.ui

import com.openmausbot.companion.core.Connection
import com.openmausbot.companion.core.PairingInvite
import java.net.URI
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
        if (!onPairPath && path !in EMPTY_PATHS) return SeiraRunRoute.OpenInBrowser(url)

        val origin = "https://$host"
        val fragment = uri.rawFragment
        if (fragment != null) {
            if (!onPairPath) return SeiraRunRoute.OpenInBrowser(url)
            val keys = fragment.split('&').map { it.substringBefore('=') }
            if (keys.any { it !in PAIR_KEYS } || "code" !in keys) return SeiraRunRoute.OpenInBrowser(url)
            val candidate = if (path == "/pair/") url.trim().replaceFirst("/pair/", "/pair") else url.trim()
            return if (PairingInvite.parse(candidate) != null) {
                SeiraRunRoute.Pair(candidate)
            } else {
                SeiraRunRoute.FillAddress(origin)
            }
        }

        val known = saved.firstOrNull { matches(it, origin) }
        return if (known != null) SeiraRunRoute.SwitchTo(known.id) else SeiraRunRoute.FillAddress(origin)
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
 * Kept in memory only. The screen takes it once.
 */
internal object PairingAddressHandoff {
    private val pending = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = pending.asStateFlow()

    fun offer(origin: String) {
        pending.value = origin
    }

    fun take(): String? {
        while (true) {
            val current = pending.value ?: return null
            if (pending.compareAndSet(current, null)) return current
        }
    }
}
