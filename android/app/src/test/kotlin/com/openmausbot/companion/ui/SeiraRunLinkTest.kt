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

    @Test
    fun `a browser sign-in link goes to the browser`() {
        val url = "$origin/pair#signin=abc"
        assertEquals(SeiraRunRoute.OpenInBrowser(url), route(url))
        val mixed = "$origin/pair#code=7KQ2-M9XD-H4TP&signin=abc"
        assertEquals(SeiraRunRoute.OpenInBrowser(mixed), route(mixed))
    }

    @Test
    fun `a fragment on a path that is not pair goes to the browser`() {
        val url = "$origin/#code=7KQ2-M9XD-H4TP"
        assertEquals(SeiraRunRoute.OpenInBrowser(url), route(url))
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
    fun `new routes keep the finish before the hand-over`() {
        val order = mutableListOf<String>()
        PairingHandoff.runThen(
            relaunch = { order += "relaunch" },
            markFinishing = { order += "finish" },
        ) { order += "deliver" }
        assertEquals(listOf("relaunch", "finish", "deliver"), order)
    }
}
