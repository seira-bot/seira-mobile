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
    fun aRefusedSwitchSurfacesTheServersWords() = runBlocking {
        server.enqueue(json("""{"error":"No such workspace"}""", 404))
        val error = assertFailsWith<APIError.Status> { client.switchWorkspace("gone") }
        assertEquals(404, error.code)
        assertEquals("No such workspace", error.serverMessage)
    }
}
