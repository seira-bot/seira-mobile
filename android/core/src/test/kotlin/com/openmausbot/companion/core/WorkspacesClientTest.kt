package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** The two workspace routes. The Swift twin is `WorkspacesTests.swift`. */
class WorkspacesClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CompanionClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connection = requireNotNull(Connection.parse(server.url("/").toString()))
        client = CompanionClient(connection, "paired-token")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun listsTheWorkspacesWithThePairedToken() = runBlocking {
        server.enqueue(json("""{"workspaces":[{"id":"a","current":true},{"id":"b","name":"Pentest"}]}"""))

        val list = requireNotNull(client.workspaces())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/phones/self/workspaces", request.path)
        assertEquals("Bearer paired-token", request.getHeader("Authorization"))
        assertEquals(listOf("a", "b"), list.map { it.id })
    }

    @Test
    fun aComputerWithoutTheRouteGivesNoList() = runBlocking {
        server.enqueue(json("""{"error":"Not found"}""", 404))
        assertNull(client.workspaces())
    }

    @Test
    fun aPairingThatMayNotReadItGivesNoList() = runBlocking {
        server.enqueue(json("""{"error":"Forbidden"}""", 403))
        assertNull(client.workspaces())
    }

    @Test
    fun aServerErrorIsNotReadAsNoWorkspaces() = runBlocking {
        server.enqueue(json("""{"error":"boom"}""", 500))
        val error = assertFailsWith<APIError.Status> { client.workspaces() }
        assertEquals(500, error.code)
    }

    @Test
    fun anExpiredPairingStillSaysSo() = runBlocking {
        server.enqueue(json("""{"error":"Unauthorized"}""", 401))
        val error = assertFailsWith<APIError.Status> { client.workspaces() }
        assertEquals(true, error.isUnauthorized)
    }

    @Test
    fun switchingSendsOnlyTheIdAndReadsTheListBack() = runBlocking {
        server.enqueue(
            json(
                """{"workspaces":[{"id":"a","current":true,"state":"waiting","waitingFor":"Nadia"},{"id":"b"}]}""",
            ),
        )

        val list = client.switchWorkspace("b")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/phones/self/workspaces/switch", request.path)
        assertEquals("""{"id":"b"}""", request.body.readUtf8())
        assertEquals(WorkspaceAvailability.WAITING, list[0].availability)
    }

    @Test
    fun openNowSendsTheHurryFlagAndAPlainOpenDoesNot() = runBlocking {
        server.enqueue(json("""{"workspaces":[{"id":"a"},{"id":"b","current":true}]}"""))
        server.enqueue(json("""{"workspaces":[{"id":"a"},{"id":"b","current":true}]}"""))

        client.openWorkspace("b", now = true)
        client.openWorkspace("a")

        assertEquals("""{"id":"b","now":true}""", server.takeRequest().body.readUtf8())
        assertEquals("""{"id":"a"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun aSwitchTheOwnerTurnedOffGives403AndAnUnavailableOneGives409() = runBlocking {
        server.enqueue(json("""{"error":"This phone cannot open other workspaces."}""", 403))
        server.enqueue(json("""{"error":"workspace_unavailable"}""", 409))

        assertEquals(403, assertFailsWith<APIError.Status> { client.openWorkspace("b") }.code)
        assertEquals(409, assertFailsWith<APIError.Status> { client.openWorkspace("b") }.code)
    }

    @Test
    fun aRefusedSwitchSurfacesTheServersWords() = runBlocking {
        server.enqueue(json("""{"error":"No such workspace"}""", 404))
        val error = assertFailsWith<APIError.Status> { client.switchWorkspace("gone") }
        assertEquals(404, error.code)
        assertEquals("No such workspace", error.serverMessage)
    }

    @Test
    fun readsTheListTheServerSends() = runBlocking {
        server.enqueue(json(fixtureText("phone-workspaces")))

        val list = requireNotNull(client.workspaces())

        assertEquals(listOf("local", "ws-a", "ws-b", "ws-c", "ws-d"), list.map { it.id })
        assertEquals(listOf(true, false, false, false, false), list.map { it.current })
        assertEquals(
            listOf(
                WorkspaceAvailability.READY,
                WorkspaceAvailability.READY,
                WorkspaceAvailability.WAITING,
                WorkspaceAvailability.UNAVAILABLE,
                WorkspaceAvailability.READY,
            ),
            list.map { it.availability },
        )
        assertEquals("Bot Sintetis", list[2].waitingFor)
        assertEquals("#112233", list[0].color)
        assertNull(list[4].name)
    }

    @Test
    fun aComputerThatAnswersNoRouteGivesNoList() = runBlocking {
        server.enqueue(json(fixtureText("phone-workspaces-no-route"), 404))
        assertNull(client.workspaces())
    }

    @Test
    fun theServersRefusalsReachTheCaller() = runBlocking {
        server.enqueue(json(fixtureText("phone-workspaces-not-found"), 404))
        val missing = assertFailsWith<APIError.Status> { client.switchWorkspace("nope") }
        assertEquals(404, missing.code)
        assertEquals("workspace_not_found", missing.serverMessage)

        server.enqueue(json(fixtureText("phone-workspaces-unavailable"), 409))
        val unavailable = assertFailsWith<APIError.Status> { client.switchWorkspace("ws-c") }
        assertEquals(409, unavailable.code)
        assertEquals("workspace_unavailable", unavailable.serverMessage)
    }
}
