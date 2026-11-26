package com.openmausbot.companion.core

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class RedesignClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CompanionClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "paired-token")
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun RecordedRequest.bodyJson(): JsonObject =
        CompanionJson.parseToJsonElement(body.readUtf8()).jsonObject

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun bot(approvalMode: String? = null): Bot = Bot(
        id = "bot-1",
        threadId = "t1",
        name = "Ayu",
        title = "",
        description = "",
        notifications = true,
        color = "green",
        unread = false,
        modelSelection = ModelSelection("instance", "model"),
        createdAt = 1.0,
        approvalMode = approvalMode,
        tasks = listOf(BotTask("t1", "Task 1", 1.0, approvalMode = approvalMode)),
    )

    @Test
    fun createBotWithoutArgumentsStillSendsAnEmptyBody() = runBlocking {
        server.enqueue(json("""{"bot":${CompanionJson.encodeToString(bot())}}"""))
        client.createBot()
        val request = server.takeRequest()
        assertEquals("POST /api/bots", "${request.method} ${request.path}")
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun createBotSendsOnlyTheFieldsItWasGiven() = runBlocking {
        server.enqueue(json("""{"bot":${CompanionJson.encodeToString(bot())}}"""))
        client.createBot(
            NewBot(
                name = "Ayu",
                soul = "Kind.",
                replyStyle = ReplyStyle.CONVERSATIONAL,
                mascotAccessories = listOf("hat"),
                retryKey = "retry-1",
            ),
        )
        val body = server.takeRequest().bodyJson()
        assertEquals(setOf("name", "soul", "replyStyle", "mascotAccessories", "retryKey"), body.keys)
        assertEquals("conversational", body.getValue("replyStyle").jsonPrimitive.content)
        assertEquals("hat", body.getValue("mascotAccessories").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun steerQueuedPostsTheThreadAndReadsTheOutcome() = runBlocking {
        server.enqueue(json("""{"ok":true,"steered":true,"threadId":"t1","queueIds":["q1","q2"]}"""))
        val result = client.steerQueued("q1", MessageDestination.Bot("bot-1", "t1"))
        assertEquals(SteerResult.Steered(listOf("q1", "q2")), result)
        val request = server.takeRequest()
        assertEquals("POST /api/bots/bot-1/queue/q1/steer", "${request.method} ${request.path}")
        assertEquals("t1", request.bodyJson().getValue("threadId").jsonPrimitive.content)
    }

    @Test
    fun steerQueuedInARoomUsesTheGroupRouteAndKeepsABlockedEntryQueued() = runBlocking {
        server.enqueue(json("""{"ok":true,"queued":true,"threadId":"t1","blockedBy":"images"}"""))
        val result = client.steerQueued("q1", MessageDestination.Room("g1", "t1"))
        assertEquals(SteerResult.StillQueued("images"), result)
        assertEquals("/api/groups/g1/queue/q1/steer", server.takeRequest().path)
    }

    @Test
    fun steerQueuedReadsADrainedEntryAsGoneAndAnUnknownRouteAsAnOldComputer() = runBlocking {
        server.enqueue(json("""{"error":"no such queued message"}""", 404))
        assertEquals(SteerResult.Gone, client.steerQueued("q1", MessageDestination.Bot("bot-1", "t1")))
        server.enqueue(json("""{"error":"no route"}""", 404))
        val failure = assertFailsWith<APIError.Status> {
            client.steerQueued("q1", MessageDestination.Bot("bot-1", "t1"))
        }
        assertTrue(failure.message.orEmpty().contains("too old"))
    }

    @Test
    fun setApprovalModePatchesTheTaskAndReturnsTheBot() = runBlocking {
        server.enqueue(json("""{"task":{"threadId":"t1","title":"Task 1","updatedAt":1},"bot":${CompanionJson.encodeToString(bot("edits"))}}"""))
        val updated = client.setApprovalMode("bot-1", "t1", ApprovalMode.EDITS)
        assertEquals(ApprovalMode.EDITS, updated.approvalModeIn("t1"))
        val request = server.takeRequest()
        assertEquals("PATCH /api/bots/bot-1/tasks/t1", "${request.method} ${request.path}")
        assertEquals(setOf("approvalMode"), request.bodyJson().keys)
    }

    @Test
    fun setApprovalModeSendsTheAcknowledgementOnlyWhenGiven() = runBlocking {
        server.enqueue(json("""{"bot":${CompanionJson.encodeToString(bot("auto"))}}"""))
        client.setApprovalMode("bot-1", "t1", ApprovalMode.AUTO, acknowledgeLocalAuto = true)
        val body = server.takeRequest().bodyJson()
        assertEquals("auto", body.getValue("approvalMode").jsonPrimitive.content)
        assertEquals("true", body.getValue("acknowledgeLocalAuto").jsonPrimitive.content)
    }

    @Test
    fun customCannotBeSentFromAPhone() {
        assertFailsWith<IllegalArgumentException> {
            runBlocking { client.setApprovalMode("bot-1", "t1", ApprovalMode.CUSTOM) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun refusalsAreToldApartByTheirWording() {
        fun refusal(code: Int, text: String) = ApprovalModeRefusal.from(APIError.Status(code, text))
        assertTrue(
            refusal(403, "On a shared company workspace Full access comes only from the operator's dedicated policy")
                is ApprovalModeRefusal.FullNotAllowedHere,
        )
        assertTrue(
            refusal(403, "Leaving Custom approval requires confirmation in the packaged desktop app")
                is ApprovalModeRefusal.LeaveCustomOnDesktop,
        )
        assertTrue(refusal(403, "Custom access requires trusted desktop confirmation") is ApprovalModeRefusal.CustomOnDesktop)
        assertTrue(
            refusal(400, "Auto mode on this computer requires confirming the warning first (acknowledgeLocalAuto)")
                is ApprovalModeRefusal.LocalAutoNeedsAcknowledgement,
        )
        assertTrue(
            refusal(403, "On this Cloud only its owner can change how a bot asks for approval, or its default model.")
                is ApprovalModeRefusal.OwnerOnly,
        )
        assertTrue(
            refusal(400, "This provider does not support the selected approval level")
                is ApprovalModeRefusal.ProviderUnsupported,
        )
        assertTrue(
            refusal(409, "the bot's approval mode is still being confirmed") is ApprovalModeRefusal.StillConfirming,
        )
        assertTrue(refusal(500, "boom") is ApprovalModeRefusal.Other)
    }

    @Test
    fun deleteBotUsesTheBotRoute() = runBlocking {
        server.enqueue(json("""{"ok":true}"""))
        client.deleteBot("bot-1")
        val request = server.takeRequest()
        assertEquals("DELETE /api/bots/bot-1", "${request.method} ${request.path}")
    }

    @Test
    fun activityReadsRowsAndPassesTheLimit() = runBlocking {
        server.enqueue(
            json(
                """{"rows":[{"at":"2026-11-26T01:02:03Z","threadId":"t1","turnId":"u1","tool":"shell","label":"Ran a command","outcome":"ran","summary":"ls"}]}""",
            ),
        )
        val rows = client.activity("bot-1", limit = 50)
        assertEquals("Ran a command", rows.single().label)
        assertEquals("ran", rows.single().outcome)
        assertEquals("/api/bots/bot-1/activity?limit=50", server.takeRequest().path)
    }

    @Test
    fun skillsDecodeWithTheirOrigin() = runBlocking {
        server.enqueue(
            json(
                """{"skills":[{"name":"research","description":"Finds things","enabled":true,"editable":false,"origin":"library","warnings":["slow"]}]}""",
            ),
        )
        val skill = client.skills("bot-1").single()
        assertEquals("research", skill.name)
        assertTrue(skill.enabled)
        assertEquals("library", skill.origin)
        assertEquals(listOf("slow"), skill.warnings)
        assertEquals("GET /api/bots/bot-1/skills", server.takeRequest().let { "${it.method} ${it.path}" })
    }

    @Test
    fun botDataDecodesTheSheetAndTables() = runBlocking {
        server.enqueue(
            json(
                """{"sheet":{"version":1,"botId":"bot-1","updatedAt":"2026-11-26","sources":[],"cards":[{"id":"c_1","kind":"table","title":"Sales","status":"ready","sql":"select 1","result":"r1","rowCount":2,"by":"bot","createdAt":"a","updatedAt":"b","columns":[{"name":"n","type":"INTEGER"}]}]},"tables":[{"name":"sales","rowCount":2,"columns":[{"name":"n","type":"INTEGER","nullPct":0}]}]}""",
            ),
        )
        val data = client.botData("bot-1")
        assertEquals("Sales", data.sheet.cards.single().title)
        assertEquals(2L, data.tables.single().rowCount)
        assertEquals("/api/bots/bot-1/data", server.takeRequest().path)
    }

    @Test
    fun dataPagePostsOneSourceAndSortAndDecodesMixedCells() = runBlocking {
        server.enqueue(json("""{"columns":[{"name":"n","type":"BIGINT"}],"rows":[[1],["9007199254740993"],[null]],"rowCount":3,"offset":0}"""))
        val page = client.dataPage(
            "bot-1",
            DataPageRequest(table = "sales", offset = 0, limit = 100, sortColumn = "n", sortDescending = true),
        )
        assertEquals(3, page.rows.size)
        assertEquals(3L, page.rowCount)
        val request = server.takeRequest()
        assertEquals("POST /api/bots/bot-1/data/page", "${request.method} ${request.path}")
        val body = request.bodyJson()
        assertEquals("sales", body.getValue("table").jsonPrimitive.content)
        assertEquals("desc", body.getValue("sort").jsonObject.getValue("direction").jsonPrimitive.content)
        assertNull(body["cardId"])
    }

    @Test
    fun dataPageRefusesBothOrNeitherSource() {
        assertFailsWith<IllegalArgumentException> {
            runBlocking { client.dataPage("bot-1", DataPageRequest(table = "a", cardId = "c_1")) }
        }
        assertFailsWith<IllegalArgumentException> {
            runBlocking { client.dataPage("bot-1", DataPageRequest()) }
        }
    }

    @Test
    fun dataErrorsAreObjectsAndStillReachThePerson() = runBlocking {
        server.enqueue(json("""{"error":{"code":"table_not_found","message":"No table sales."}}""", 404))
        val failure = assertFailsWith<APIError.Status> { client.botData("bot-1") }
        assertEquals("No table sales.", failure.serverMessage)
    }

    @Test
    fun teamMapDecodesLinksAndHandoffs() = runBlocking {
        server.enqueue(
            json(
                """{"collaborations":[{"groupId":"g1","botIds":["a","b"],"lastAt":5}],"queued":[],"running":[{"sourceBotId":"a","targetBotId":"b","threadId":"t1"}]}""",
            ),
        )
        val map = client.teamMap()
        assertEquals(listOf("a", "b"), map.collaborations.single().botIds)
        assertEquals("b", map.running.single().targetBotId)
        assertEquals("/api/team-map", server.takeRequest().path)
    }

    @Test
    fun teamMemoryReadsASectionByQuery() = runBlocking {
        server.enqueue(
            json(
                """{"section":"Sales","label":"Sales","entries":[{"id":"m1","kind":"person","name":"Budi","status":"accepted","revision":"r1","updatedAt":3,"source":{"botId":"a","botName":"Ayu","threadId":"t1","at":2}}],"loadedIds":["m1"]}""",
            ),
        )
        val page = client.teamMemory("Sales")
        assertEquals("r1", page.entries.single().revision)
        assertEquals(listOf("m1"), page.loadedIds)
        assertEquals("/api/team-memory?section=Sales", server.takeRequest().path)
    }

    @Test
    fun teamMemoryWritesCarryTheSectionAndTheRevision() = runBlocking {
        val edit = """{"entries":[],"loadedIds":[]}"""
        server.enqueue(json(edit, 201))
        server.enqueue(json(edit))
        server.enqueue(json(edit))
        client.addTeamMemory("", TeamMemoryKind.TERM, "ARR", "Annual recurring revenue", listOf("arr"))
        client.updateTeamMemory("Sales", "m1", "r1", detail = "New", pinned = true)
        client.deleteTeamMemory("Sales", "m1", "r2")

        val add = server.takeRequest()
        assertEquals("POST /api/team-memory?section=", "${add.method} ${add.path}")
        assertEquals("term", add.bodyJson().getValue("kind").jsonPrimitive.content)
        val update = server.takeRequest()
        assertEquals("PATCH /api/team-memory/m1?section=Sales", "${update.method} ${update.path}")
        val patch = update.bodyJson()
        assertEquals("r1", patch.getValue("expectedRevision").jsonPrimitive.content)
        assertEquals(setOf("expectedRevision", "detail", "pinned"), patch.keys)
        val delete = server.takeRequest()
        assertEquals("DELETE /api/team-memory/m1?section=Sales&revision=r2", "${delete.method} ${delete.path}")
    }

    @Test
    fun acceptingAProposedMemoryRidesTheSamePatch() = runBlocking {
        server.enqueue(json("""{"entries":[],"loadedIds":[]}"""))
        client.updateTeamMemory("", "m1", "r1", accept = true)
        val body = server.takeRequest().bodyJson()
        assertEquals("true", body.getValue("accept").jsonPrimitive.content)
    }

    @Test
    fun deleteConnectorAccountNamesBothIds() = runBlocking {
        server.enqueue(json("""{"ok":true}"""))
        client.deleteConnectorAccount("gmail", "ca_123")
        val request = server.takeRequest()
        assertEquals("DELETE /api/connectors/gmail/accounts/ca_123", "${request.method} ${request.path}")
    }

    @Test
    fun profilePatchSendsSoulAndReplyStyle() = runBlocking {
        server.enqueue(json("""{"bot":${CompanionJson.encodeToString(bot())}}"""))
        client.updateProfile("bot-1", BotProfilePatch(soul = "Be brief.", replyStyle = ReplyStyle.DEFAULT))
        val request = server.takeRequest()
        assertEquals("PATCH /api/bots/bot-1/profile", "${request.method} ${request.path}")
        val body = request.bodyJson()
        assertEquals(setOf("soul", "replyStyle"), body.keys)
        assertEquals("default", body.getValue("replyStyle").jsonPrimitive.content)
    }

    @Test
    fun computerControlReadsAndTakesWithALease() = runBlocking {
        server.enqueue(json("""{"held":false}"""))
        server.enqueue(json("""{"held":true,"owned":true,"acquired":true,"heldSinceMs":10}"""))
        server.enqueue(json("""{"held":false,"released":true}"""))
        assertEquals(false, client.computerControl("bot-1", "t1").held)
        val taken = client.computerControl("bot-1", ComputerControlAction.TAKE, leaseId = "lease-1", threadId = "t1")
        assertEquals(true, taken.owned)
        client.computerControl("bot-1", ComputerControlAction.RELEASE, leaseId = "lease-1", threadId = "t1")

        val read = server.takeRequest()
        assertEquals("GET /api/bots/bot-1/computer/control?threadId=t1", "${read.method} ${read.path}")
        val take = server.takeRequest()
        assertEquals("POST /api/bots/bot-1/computer/control?threadId=t1", "${take.method} ${take.path}")
        val body = take.bodyJson()
        assertEquals("take", body.getValue("action").jsonPrimitive.content)
        assertEquals("lease-1", body.getValue("controlLeaseId").jsonPrimitive.content)
        assertEquals("release", server.takeRequest().bodyJson().getValue("action").jsonPrimitive.content)
    }

    @Test
    fun unsafeIdsNeverReachTheWire() {
        assertFailsWith<APIError> { runBlocking { client.deleteBot("..") } }
        assertFailsWith<APIError> { runBlocking { client.deleteConnectorAccount("gmail", "") } }
        assertEquals(0, server.requestCount)
    }
}
