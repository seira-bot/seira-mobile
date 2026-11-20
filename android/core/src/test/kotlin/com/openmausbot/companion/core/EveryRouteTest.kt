package com.openmausbot.companion.core

import java.io.IOException
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class EveryRouteTest {
    private val tailnetName = "server-x.tail1234.ts.net"
    private val tunnelName = "x.seira.run"
    private val code = "ABCD-EFGH-JKLM"

    private fun encode(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun link(host: String, endpoints: String?): String =
        "https://$host/pair#code=$code" + (endpoints?.let { "&endpoints=$it" } ?: "")

    private fun routeJson(url: String, priority: Int, kind: String = "hosted") =
        """{"url":"$url","kind":"$kind","priority":$priority}"""

    private fun endpoint(url: String, priority: Int) =
        assertNotNull(CompanionEndpoint.create(url, CompanionEndpointKind.HOSTED, priority))

    @Test
    fun serverLinkWithEndpointsKeepsEveryRouteInPriorityOrder() {
        val encoded = encode(
            "[" + routeJson("https://$tunnelName", 5) + "," + routeJson("https://$tailnetName", 1) + "]",
        )
        val invite = assertNotNull(PairingInvite.parse(link(tailnetName, encoded)))
        assertEquals(
            listOf("https://$tailnetName", "https://$tunnelName"),
            invite.connection.orderedEndpoints.map { it.url },
        )
        assertEquals("https://$tailnetName", invite.connection.activeEndpoint?.url)
        assertEquals(1, invite.connection.activeEndpoint?.priority)
    }

    @Test
    fun serverLinkTreatsAnAbsentPrimaryAsPriorityZero() {
        val encoded = encode("[" + routeJson("https://$tunnelName", 0) + "]")
        val invite = assertNotNull(PairingInvite.parse(link(tailnetName, encoded)))
        assertEquals(
            listOf("https://$tailnetName", "https://$tunnelName"),
            invite.connection.orderedEndpoints.map { it.url },
        )
        assertEquals(0, invite.connection.activeEndpoint?.priority)
    }

    @Test
    fun serverLinkDropsInvalidEntriesAndKeepsTheLink() {
        val encoded = encode(
            "[" + listOf(
                routeJson("http://plain.example", 1),
                routeJson("https://path.example/api", 2),
                routeJson("https://user:pw@cred.example", 3),
                routeJson("https://tail.example", 4, kind = "tailnet"),
                routeJson("https://$tunnelName", 6),
            ).joinToString(",") + "]",
        )
        val invite = assertNotNull(PairingInvite.parse(link(tailnetName, encoded)))
        assertEquals(
            listOf("https://$tailnetName", "https://$tunnelName"),
            invite.connection.orderedEndpoints.map { it.url },
        )
    }

    @Test
    fun serverLinkWithUndecodableEndpointsStaysOneRoute() {
        for (bad in listOf("!!!", encode("not json"), encode("""{"url":"https://a.example"}"""))) {
            val invite = assertNotNull(PairingInvite.parse(link(tailnetName, bad)), bad)
            assertEquals(listOf("https://$tailnetName"), invite.connection.orderedEndpoints.map { it.url })
        }
        val plain = assertNotNull(PairingInvite.parse(link(tailnetName, null)))
        assertEquals(listOf("https://$tailnetName"), plain.connection.orderedEndpoints.map { it.url })
    }

    @Test
    fun serverLinkKeepsAtMostEightRoutes() {
        val routes = (0 until 12).joinToString(",", "[", "]") { routeJson("https://r$it.example", it) }
        val invite = assertNotNull(PairingInvite.parse(link("r0.example", encode(routes))))
        assertEquals(8, invite.connection.orderedEndpoints.size)
    }

    @Test
    fun serverPairingFallsBackFromANonResolvingTailnetRouteToTheTunnel() = runBlocking<Unit> {
        val stub = RouteStub { request ->
            when {
                request.url.host == tailnetName -> throw UnknownHostException("Unable to resolve host \"$tailnetName\"")
                request.url.encodedPath == "/api/auth/pair" -> 200 to PAIRED
                else -> 200 to ENVIRONMENT
            }
        }
        val invite = assertNotNull(PairingInvite.parse(link(tailnetName, twoRoutes())))

        val outcome = CompanionClient.pairWithServerFirstReachable(
            invite.connection, "ABCDEFGHJKLM", "Pixel", "attempt-1", stub.client,
        )

        assertEquals(tunnelName, outcome.connection.activeEndpoint?.host)
        assertEquals(listOf(tunnelName), stub.requests.filter { it.url.encodedPath == "/api/auth/pair" }.map { it.url.host })
    }

    @Test
    fun serverPairingPrefersTheFirstRouteInPriorityOrderThatAnswered() = runBlocking<Unit> {
        val stub = RouteStub { request ->
            when {
                request.url.encodedPath == "/api/auth/pair" -> 200 to PAIRED
                request.url.host == tailnetName -> {
                    Thread.sleep(120)
                    200 to ENVIRONMENT
                }
                else -> 200 to ENVIRONMENT
            }
        }
        val invite = assertNotNull(PairingInvite.parse(link(tailnetName, twoRoutes())))

        val outcome = CompanionClient.pairWithServerFirstReachable(
            invite.connection, "ABCDEFGHJKLM", "Pixel", "attempt-1", stub.client,
        )

        assertEquals(tailnetName, outcome.connection.activeEndpoint?.host)
        assertEquals(
            setOf(tailnetName, tunnelName),
            stub.requests.filter { it.url.encodedPath.contains("well-known") }.map { it.url.host }.toSet(),
        )
    }

    @Test
    fun serverPairingTriesTheNextRouteAfterAGatewayFailure() = runBlocking<Unit> {
        val stub = RouteStub { request ->
            when {
                request.url.encodedPath == "/api/auth/pair" && request.url.host == tailnetName -> 502 to "{}"
                request.url.encodedPath == "/api/auth/pair" -> 200 to PAIRED
                else -> 200 to ENVIRONMENT
            }
        }
        val invite = assertNotNull(PairingInvite.parse(link(tailnetName, twoRoutes())))

        val outcome = CompanionClient.pairWithServerFirstReachable(
            invite.connection, "ABCDEFGHJKLM", "Pixel", "attempt-1", stub.client,
        )

        assertEquals(tunnelName, outcome.connection.activeEndpoint?.host)
        assertEquals(
            listOf(tailnetName, tunnelName),
            stub.requests.filter { it.url.encodedPath == "/api/auth/pair" }.map { it.url.host },
        )
    }

    @Test
    fun serverPairingWithEveryRouteDownWarnsThatTailscaleIsOff() = runBlocking<Unit> {
        val stub = RouteStub { request ->
            throw if (request.url.host == tailnetName) {
                UnknownHostException("Unable to resolve host \"$tailnetName\"")
            } else {
                IOException("Failed to connect to ${request.url.host}")
            }
        }
        val invite = assertNotNull(PairingInvite.parse(link(tailnetName, twoRoutes())))

        val error = assertFailsWith<ServerAddressError> {
            CompanionClient.pairWithServerFirstReachable(
                invite.connection, "ABCDEFGHJKLM", "Pixel", "attempt-1", stub.client, vpnActive = { false },
            )
        }

        assertTrue(error.offersTailscale)
        assertEquals(
            "Couldn't reach ${invite.connection.name}. ${ConnectionAdvice.TAILSCALE_OFF} " +
                "The internet address did not answer either. Check that the computer is on.",
            error.message,
        )
        assertTrue(stub.requests.none { it.url.encodedPath == "/api/auth/pair" })
    }

    @Test
    fun tailscaleAdviceDependsOnWhetherTheVpnIsOn() {
        val failures = mapOf<String, Throwable>(
            "https://$tailnetName" to UnknownHostException("Unable to resolve host \"$tailnetName\""),
        )

        assertNull(ConnectionAdvice.tailscaleAdvice(failures, vpnActive = null))

        val off = assertNotNull(ConnectionAdvice.tailscaleAdvice(failures, vpnActive = false))
        assertEquals("Tailscale is off on this phone. Turn it on, then try again.", off.text)
        assertTrue(off.offersTailscale)

        val on = assertNotNull(ConnectionAdvice.tailscaleAdvice(failures, vpnActive = true))
        assertTrue("Private DNS" in on.text, on.text)
        assertTrue(on.text.endsWith("This phone may be signed in to a different tailnet."), on.text)
        assertFalse(on.offersTailscale)

        val noTailnet = mapOf<String, Throwable>("https://$tunnelName" to IOException("Failed to connect"))
        assertNull(ConnectionAdvice.tailscaleAdvice(noTailnet, vpnActive = false))
    }

    @Test
    fun tokenPairingErrorOffersTailscaleOnlyWhenTheVpnIsOff() {
        val failures = mapOf<String, Throwable>(
            "https://$tailnetName" to UnknownHostException("Unable to resolve host \"$tailnetName\""),
        )
        val routes = failures.keys.toList()
        assertTrue(PairingRouteError(routes, failures, "Mac", vpnActive = false).offersTailscale)
        assertFalse(PairingRouteError(routes, failures, "Mac", vpnActive = true).offersTailscale)
        assertFalse(PairingRouteError(routes, failures, "Mac").offersTailscale)
    }

    @Test
    fun firstReachableRouteSkipsARouteThatDoesNotAnswer() = runBlocking<Unit> {
        val stub = RouteStub { request ->
            if (request.url.host == tailnetName) throw UnknownHostException("no such host") else 200 to ENVIRONMENT
        }
        val connection = savedServer(active = tunnelName)

        val choice = CompanionClient.firstReachableRoute(connection, stub.client)

        assertEquals(tunnelName, choice.endpoint?.host)
        assertEquals(setOf("https://$tailnetName"), choice.failures.keys)
    }

    @Test
    fun firstReachableRouteKeepsPriorityOrderWhenBothAnswer() = runBlocking<Unit> {
        val stub = RouteStub { request ->
            if (request.url.host == tailnetName) Thread.sleep(100)
            200 to ENVIRONMENT
        }

        val choice = CompanionClient.firstReachableRoute(savedServer(active = tunnelName), stub.client)

        assertEquals(tailnetName, choice.endpoint?.host)
    }

    @Test
    fun firstReachableRouteRejectsARouteShowingAnotherServer() = runBlocking<Unit> {
        val stub = RouteStub { request ->
            if (request.url.host == tailnetName) 200 to """{"environmentId":"other","label":"x"}"""
            else 200 to ENVIRONMENT
        }

        val choice = CompanionClient.firstReachableRoute(savedServer(active = tailnetName), stub.client)

        assertEquals(tunnelName, choice.endpoint?.host)
    }

    @Test
    fun launchStartsOnTheRememberedServerRoute() = runBlocking<Unit> {
        Harness(savedServer(active = tunnelName)).use { h ->
            h.session.connect()
            h.waitForStream()
            assertEquals(tunnelName, h.streamHosts.first())
        }
    }

    @Test
    fun launchConnectsOnTheFirstAnsweringRouteAndRemembersIt() = runBlocking<Unit> {
        val saved = savedServer(active = tailnetName)
        val choice = RouteChoice(endpoint(saved, tunnelName), emptyMap())
        Harness(saved, routeChoice = { choice }).use { h ->
            h.session.connect()
            h.waitForStream()
            assertEquals(tunnelName, h.streamHosts.first())
            withTimeout(5_000) {
                while (h.store.saved?.activeEndpoint?.host != tunnelName) delay(20)
            }
        }
    }

    @Test
    fun launchWithNoAnsweringRouteExplainsThatTailscaleIsOff() = runBlocking<Unit> {
        val saved = savedServer(active = tailnetName)
        val failures = mapOf<String, Throwable>(
            "https://$tailnetName" to UnknownHostException("no such host"),
            "https://$tunnelName" to IOException("Failed to connect"),
        )
        Harness(saved, routeChoice = { RouteChoice(null, failures) }, vpn = false).use { h ->
            h.session.connect()
            withTimeout(5_000) { while (h.session.routeAdvice.value == null) delay(20) }
            val advice = assertNotNull(h.session.routeAdvice.value)
            assertTrue(advice.offersTailscale)
            assertTrue(advice.text.startsWith("Tailscale is off on this phone."), advice.text)
        }
    }

    @Test
    fun serverConnectionRefreshesItsRoutesFromTheComputer() = runBlocking<Unit> {
        val saved = savedServer(active = tailnetName)
        val added = endpoint("https://new.example", 2)
        val snapshot = CompanionConnectionMetadata(
            serverName = "headless mini",
            endpoints = saved.orderedEndpoints + added,
        )
        Harness(saved, metadata = { snapshot }).use { h ->
            h.session.connect()
            withTimeout(5_000) {
                while (h.store.saved?.endpoints.orEmpty().none { it.url == added.url }) delay(20)
            }
            assertEquals(Session.Status.Live, h.session.status.value)
        }
    }

    @Test
    fun anOlderServerWithoutTheRouteListKeepsItsStoredRoutes() = runBlocking<Unit> {
        val saved = savedServer(active = tailnetName)
        var asked = false
        Harness(saved, metadata = {
            asked = true
            throw APIError.Status(404)
        }).use { h ->
            h.session.connect()
            withTimeout(5_000) { while (!asked) delay(20) }
            delay(200)
            assertEquals(Session.Status.Live, h.session.status.value)
            assertNull(h.session.actionError)
            assertEquals(saved.orderedEndpoints.map { it.url }, h.session.connection.value?.orderedEndpoints?.map { it.url })
        }
    }

    @Test
    fun aNetworkChangeProbesAgainAndMovesToTheAnsweringRoute() = runBlocking<Unit> {
        val saved = savedServer(active = tailnetName)
        val changes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
        val answering = java.util.concurrent.atomic.AtomicReference(tailnetName)
        Harness(
            saved,
            routeChoice = { RouteChoice(endpoint(saved, answering.get()), emptyMap()) },
            changes = changes,
        ).use { h ->
            h.session.connect()
            h.waitForStream()
            assertEquals(tailnetName, h.streamHosts.last())
            answering.set(tunnelName)
            changes.emit(Unit)
            withTimeout(5_000) { while (h.streamHosts.last() != tunnelName) delay(20) }
        }
    }

    private fun twoRoutes() = encode(
        "[" + routeJson("https://$tailnetName", 0) + "," + routeJson("https://$tunnelName", 1) + "]",
    )

    private fun endpoint(connection: Connection, host: String) =
        connection.orderedEndpoints.first { it.host == host }

    private fun savedServer(active: String): Connection {
        val routes = listOf(endpoint("https://$tailnetName", 0), endpoint("https://$tunnelName", 1))
        val base = assertNotNull(Connection.parse("https://$active"))
        return base.copy(
            name = "headless mini",
            endpoints = routes,
            activeEndpoint = routes.first { it.host == active },
            allowedRouteKinds = setOf(CompanionEndpointKind.HOSTED),
            allowedLocalRouteURLs = emptySet(),
            serverEnvironmentId = "env-fixture",
            serverScopes = listOf("client"),
        )
    }

    private class RouteStub(private val action: (Request) -> Pair<Int, String>) : Interceptor {
        val requests = CopyOnWriteArrayList<Request>()
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val (status, body) = action(request)
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("stub")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }
    }

    private class Harness(
        saved: Connection,
        routeChoice: (suspend (Connection) -> RouteChoice)? = null,
        metadata: suspend (CompanionClient) -> CompanionConnectionMetadata = { throw APIError.Status(404) },
        vpn: Boolean? = null,
        changes: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    ) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val streamHosts = CopyOnWriteArrayList<String>()
        val store = object : ConnectionStore {
            var saved: Connection? = saved
            override suspend fun loadRegistry() = ConnectionRegistryRestore(
                ConnectionRegistry(listOfNotNull(this.saved), this.saved?.id), migratedLegacyConnection = false,
            )
            override suspend fun load() = saved
            override suspend fun save(connection: Connection) { this.saved = connection }
            override suspend fun clear() { saved = null }
        }
        private val tokens = object : TokenStore {
            override suspend fun save(connectionId: String, token: String) {}
            override suspend fun read(connectionId: String): TokenStore.ReadResult = TokenStore.ReadResult.Found("seira_sess_fixture")
            override suspend fun remove(connectionId: String) {}
        }
        private val http = RouteStub { 200 to ENVIRONMENT }.client
        val session = Session(
            scope, store, tokens, InMemoryOnboardingStore(), { "Pixel" }, httpClient = http,
            eventsFn = { client, _, _ ->
                streamHosts += client.connection.host
                flow {
                    emit(StreamFrame(Frame.Hello(cursor = "s:1", resumed = true), seq = 1))
                    awaitCancellation()
                }
            },
            metadataFn = metadata,
            networkMonitor = object : NetworkMonitor {
                override fun vpnActive() = vpn
                override val changes = changes
            },
            routeChoiceFn = routeChoice,
        )

        suspend fun waitForStream() = withTimeout(5_000) { while (streamHosts.isEmpty()) delay(20) }

        override fun close() {
            session.disconnect()
            scope.cancel()
        }
    }

    private companion object {
        const val ENVIRONMENT = """{"environmentId":"env-fixture","label":"headless mini"}"""
        const val PAIRED =
            """{"token":"seira_sess_fixture","session":{"id":"s1","label":"Pixel","scopes":["client"]},"environment":{"environmentId":"env-fixture","label":"headless mini"}}"""
    }
}
