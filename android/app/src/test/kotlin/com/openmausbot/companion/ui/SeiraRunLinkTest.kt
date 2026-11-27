package com.openmausbot.companion.ui

import com.openmausbot.companion.core.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeiraRunLinkTest {
    private val host = "c-0123456789abcdef0123456789abcdef.seira.run"
    private val origin = "https://$host"
    private val saved = Connection.parse(origin)!!

    private fun route(url: String, vararg computers: Connection) =
        SeiraRunLink.route(url, computers.toList())

    @Test
    fun `a pair link with a code pairs`() {
        val url = "$origin/pair#code=7KQ2-M9XD-H4TP"
        assertEquals(SeiraRunRoute.Pair(url), route(url))
    }

    @Test
    fun `a pair link with a trailing slash pairs on the plain path`() {
        assertEquals(
            SeiraRunRoute.Pair("$origin/pair#code=7KQ2-M9XD-H4TP"),
            route("$origin/pair/#code=7KQ2-M9XD-H4TP"),
        )
    }

    @Test
    fun `a code that does not read lets the person type it`() {
        assertEquals(SeiraRunRoute.FillAddress(origin), route("$origin/pair#code=nope"))
    }

    @Test
    fun `an address without a saved computer fills the address`() {
        assertEquals(SeiraRunRoute.FillAddress(origin), route("$origin/"))
        assertEquals(SeiraRunRoute.FillAddress(origin), route(origin))
        assertEquals(SeiraRunRoute.FillAddress(origin), route("$origin/pair"))
    }

    @Test
    fun `an upper case host is the same origin`() {
        assertEquals(SeiraRunRoute.FillAddress(origin), route("HTTPS://${host.uppercase()}/"))
    }

    @Test
    fun `a saved computer at the origin is switched to`() {
        assertEquals(SeiraRunRoute.SwitchTo(saved.id), route("$origin/", saved))
        assertEquals(SeiraRunRoute.SwitchTo(saved.id), route("$origin/pair", saved))
    }

    @Test
    fun `a saved computer at another origin is not switched to`() {
        val other = Connection.parse("https://c-ffffffffffffffffffffffffffffffff.seira.run")!!
        assertEquals(SeiraRunRoute.FillAddress(origin), route("$origin/", other))
    }

    /** No route may hand a pairing code to the browser. */
    private fun assertNoCodeInBrowser(url: String, expected: SeiraRunRoute) {
        val actual = route(url)
        assertEquals(expected, actual, url)
        if (actual is SeiraRunRoute.OpenInBrowser) {
            assertFalse(actual.url.contains("code=", ignoreCase = true), "browser route carries a code: $url")
        }
    }

    @Test
    fun `a browser sign-in link goes to the browser`() {
        val url = "$origin/pair#signin=abc"
        assertEquals(SeiraRunRoute.OpenInBrowser(url), route(url))
        val root = "$origin/#signin=abc"
        assertEquals(SeiraRunRoute.OpenInBrowser(root), route(root))
    }

    @Test
    fun `a code on the root path never goes to the browser`() {
        assertNoCodeInBrowser("$origin/#code=7KQ2-M9XD-H4TP", SeiraRunRoute.FillAddress(origin))
        assertNoCodeInBrowser("$origin#code=7KQ2-M9XD-H4TP", SeiraRunRoute.FillAddress(origin))
    }

    @Test
    fun `a code with an extra key on the pair path never goes to the browser`() {
        assertNoCodeInBrowser("$origin/pair#code=7KQ2-M9XD-H4TP&x=1", SeiraRunRoute.FillAddress(origin))
        assertNoCodeInBrowser("$origin/pair#code=7KQ2-M9XD-H4TP&signin=abc", SeiraRunRoute.FillAddress(origin))
        assertNoCodeInBrowser("$origin/pair#signin=abc&code=7KQ2-M9XD-H4TP", SeiraRunRoute.FillAddress(origin))
    }

    @Test
    fun `a code on another path never goes to the browser`() {
        assertNoCodeInBrowser("$origin/settings#code=7KQ2-M9XD-H4TP", SeiraRunRoute.Ignore)
        assertNoCodeInBrowser("$origin/pair/x#code=7KQ2-M9XD-H4TP", SeiraRunRoute.Ignore)
    }

    @Test
    fun `a code key that is encoded or in another case never goes to the browser`() {
        assertNoCodeInBrowser("$origin/#c%6Fde=7KQ2-M9XD-H4TP", SeiraRunRoute.FillAddress(origin))
        assertNoCodeInBrowser("$origin/pair#CODE=7KQ2-M9XD-H4TP&x=1", SeiraRunRoute.FillAddress(origin))
    }

    @Test
    fun `another page goes to the browser`() {
        val url = "$origin/settings"
        assertEquals(SeiraRunRoute.OpenInBrowser(url), route(url, saved))
    }

    @Test
    fun `a host that is not a server tunnel is ignored`() {
        assertEquals(SeiraRunRoute.Ignore, route("https://evil.seira.run/pair#code=7KQ2-M9XD-H4TP"))
        assertEquals(SeiraRunRoute.Ignore, route("https://seira.run/"))
        assertEquals(SeiraRunRoute.Ignore, route("https://x.$host/"))
        assertEquals(SeiraRunRoute.Ignore, route("https://c-0123456789abcdef0123456789abcdeg.seira.run/"))
    }

    @Test
    fun `a port, user info or plain http is ignored`() {
        assertEquals(SeiraRunRoute.Ignore, route("https://$host:8443/"))
        assertEquals(SeiraRunRoute.Ignore, route("https://user@$host/"))
        assertEquals(SeiraRunRoute.Ignore, route("http://$host/"))
        assertEquals(SeiraRunRoute.Ignore, route("not a url"))
    }

    @Test
    fun `matches reads the host, the active endpoint and the endpoint list`() {
        assertTrue(SeiraRunLink.matches(saved, origin))
        assertTrue(SeiraRunLink.matches(saved.copy(activeEndpoint = null), origin))
        assertTrue(SeiraRunLink.matches(saved.copy(activeEndpoint = null, endpoints = null), origin))
        assertFalse(SeiraRunLink.matches(saved, "https://c-ffffffffffffffffffffffffffffffff.seira.run"))
        assertFalse(
            SeiraRunLink.matches(saved.copy(host = "10.0.0.2", activeEndpoint = null, endpoints = null), origin),
        )
    }

    @Test
    fun `the address hand-off is taken once`() {
        PairingAddressHandoff.offer(origin)
        assertEquals(origin, PairingAddressHandoff.take())
        assertNull(PairingAddressHandoff.take())
    }

    @Test
    fun `an address offered 59 seconds ago is still taken`() {
        var now = 1_000L
        PairingAddressHandoff.clock = { now }
        try {
            PairingAddressHandoff.offer(origin)
            now += 59_000L
            assertEquals(origin, PairingAddressHandoff.take())
        } finally {
            PairingAddressHandoff.clock = { System.nanoTime() / 1_000_000 }
        }
    }

    @Test
    fun `an address offered more than 60 seconds ago is dropped`() {
        var now = 1_000L
        PairingAddressHandoff.clock = { now }
        try {
            PairingAddressHandoff.offer(origin)
            assertEquals(origin, PairingAddressHandoff.address.value)
            now += 60_001L
            assertNull(PairingAddressHandoff.take())
            assertNull(PairingAddressHandoff.address.value)
        } finally {
            PairingAddressHandoff.clock = { System.nanoTime() / 1_000_000 }
        }
    }

    @Test
    fun `new routes keep the finish before the hand-over`() {
        val order = mutableListOf<String>()
        PairingHandoff.runThen(
            relaunch = { order += "relaunch" },
            markFinishing = { order += "finish" },
        ) { order += "deliver" }
        assertEquals(listOf("relaunch", "finish", "deliver"), order)
    }
}
