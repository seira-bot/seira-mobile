package com.openmausbot.companion.core

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class AiUsageTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private val now = Instant.parse("2026-11-21T08:00:00Z").toEpochMilli()
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli().toDouble()

    private fun report(json: String): EngineLimitsReport = CompanionJson.decodeFromString(json)

    @Test
    fun knownWindowsDecodeIntoOneRowEach() {
        val cards = AiUsageRules.cards(
            report(
                """{"checkedAt":1,"engines":[{"engine":"claude-1","name":"Claude","driver":"claude",
                "window":"5-hour","usedPercent":40,"resetsAt":${at("2026-11-21T10:00:00Z")},"blocked":false,
                "windows":[{"window":"5-hour","usedPercent":40,"resetsAt":${at("2026-11-21T10:00:00Z")}},
                {"window":"weekly","usedPercent":12.4,"resetsAt":${at("2026-11-25T10:00:00Z")}}]}]}""",
            ),
            now,
        )
        val card = cards.single()
        assertEquals("Claude", card.name)
        assertEquals(listOf(LimitWindowKind.FIVE_HOUR, LimitWindowKind.WEEKLY), card.rows.map { it.kind })
        assertEquals(listOf(60, 88), card.rows.map { it.percentLeft })
        assertEquals(at("2026-11-25T10:00:00Z").toLong(), card.rows[1].resetsAt)
        assertFalse(card.paused)
    }

    @Test
    fun anUnknownWindowIsOtherAndTheRestStay() {
        val card = AiUsageRules.cards(
            report(
                """{"engines":[{"engine":"e","name":"Codex","driver":"codex","usedPercent":10,"blocked":false,
                "windows":[{"window":"monthly","usedPercent":10,"resetsAt":null},
                {"window":"weekly","usedPercent":20,"resetsAt":null}]}]}""",
            ),
            now,
        ).single()
        assertEquals(listOf(LimitWindowKind.OTHER, LimitWindowKind.WEEKLY), card.rows.map { it.kind })
        assertEquals(listOf(90, 80), card.rows.map { it.percentLeft })
    }

    @Test
    fun anUnknownDriverIsKeptAndAMissingWindowListFallsBackToTheTopLevelWindow() {
        val parsed = report(
            """{"engines":[{"engine":"x","name":"Future","driver":"quantum","window":"weekly","usedPercent":30,
            "resetsAt":${at("2026-11-22T00:00:00Z")},"blocked":false}]}""",
        )
        assertEquals("quantum", parsed.engines.single().driver)
        val card = AiUsageRules.cards(parsed, now).single()
        assertEquals(listOf(LimitWindowKind.WEEKLY), card.rows.map { it.kind })
        assertEquals(70, card.rows.single().percentLeft)
    }

    @Test
    fun aBlockedOrHeldEngineIsPausedUntilItsReset() {
        val reset = at("2026-11-21T12:30:00Z")
        val blocked = AiUsageRules.cards(
            report("""{"engines":[{"engine":"a","name":"A","driver":"claude","usedPercent":100,"resetsAt":$reset,"blocked":true}]}"""),
            now,
        ).single()
        assertTrue(blocked.paused)
        assertEquals(reset.toLong(), blocked.pausedUntil)
        val held = AiUsageRules.cards(
            report("""{"engines":[{"engine":"b","name":"B","driver":"grok","usedPercent":50,"resetsAt":$reset,"blocked":false,"held":true}]}"""),
            now,
        ).single()
        assertTrue(held.paused)
        assertEquals(reset.toLong(), held.pausedUntil)
        val idle = AiUsageRules.cards(
            report("""{"engines":[{"engine":"c","name":"C","driver":"claude","usedPercent":50,"resetsAt":$reset,"blocked":false}]}"""),
            now,
        ).single()
        assertFalse(idle.paused)
        assertNull(idle.pausedUntil)
    }

    @Test
    fun aResetInThePastIsDropped() {
        val card = AiUsageRules.cards(
            report("""{"engines":[{"engine":"a","name":"A","driver":"claude","window":"5-hour","usedPercent":50,"resetsAt":${at("2026-11-21T07:00:00Z")},"blocked":false}]}"""),
            now,
        ).single()
        assertNull(card.rows.single().resetsAt)
    }

    @Test
    fun percentLeftRoundsAndStaysInRange() {
        assertEquals(8, AiUsageRules.percentLeft(92.4))
        assertEquals(0, AiUsageRules.percentLeft(130.0))
        assertEquals(100, AiUsageRules.percentLeft(-5.0))
    }

    @Test
    fun theNearLimitToneStartsAtNinetyPercentUsed() {
        assertFalse(AiUsageRules.isNear(89.9))
        assertTrue(AiUsageRules.isNear(90.0))
        assertTrue(AiUsageRules.isNear(10.0, blocked = true))
        assertFalse(AiUsageRules.isNear(null))
    }

    @Test
    fun resetTimeIsAClockTimeTodayAndAWeekdayLater() {
        val zone = ZoneOffset.UTC
        assertEquals(
            "3:40 PM",
            AiUsageRules.resetTime(Instant.parse("2026-11-21T15:40:00Z").toEpochMilli(), now, zone, Locale.US),
        )
        assertEquals(
            "Mon 9:05 AM",
            AiUsageRules.resetTime(Instant.parse("2026-11-23T09:05:00Z").toEpochMilli(), now, zone, Locale.US),
        )
    }

    @Test
    fun theClientReadsTheReportAndAsksAgainWithRefresh() = runTest {
        val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
        server.enqueue(json("""{"checkedAt":5,"engines":[]}"""))
        server.enqueue(json("""{"checkedAt":6,"engines":[]}"""))
        assertEquals(5.0, client.limitResets().checkedAt)
        assertEquals(6.0, client.limitResets(refresh = true).checkedAt)
        assertEquals("/api/limit-resets", server.takeRequest().path)
        assertEquals("/api/limit-resets?refresh=1", server.takeRequest().path)
    }

    @Test
    fun aRefusedReadHidesTheEntryForTheSession() = runTest {
        val session = session()
        assertFalse(session.aiUsageHidden)
        server.enqueue(json("""{"error":"admin only"}""", code = 403))
        assertIs<AiUsageResult.NotAvailable>(session.aiUsage())
        assertTrue(session.aiUsageHidden)
        assertNull(session.actionError)
    }

    @Test
    fun aMissingRouteOnAnOlderComputerHidesTheEntryToo() = runTest {
        val session = session()
        server.enqueue(MockResponse().setResponseCode(404))
        assertIs<AiUsageResult.NotAvailable>(session.aiUsage())
        assertTrue(session.aiUsageHidden)
    }

    @Test
    fun anEmptyEngineListHidesTheEntry() = runTest {
        val session = session()
        server.enqueue(json("""{"checkedAt":1,"engines":[]}"""))
        assertTrue(assertIs<AiUsageResult.Report>(session.aiUsage()).report.engines.isEmpty())
        assertTrue(session.aiUsageHidden)
    }

    @Test
    fun aReportWithEnginesKeepsTheEntryAndAServerErrorDoesNotHideIt() = runTest {
        val session = session()
        server.enqueue(json("""{"checkedAt":1,"engines":[{"engine":"a","name":"A","driver":"claude","usedPercent":1,"blocked":false}]}"""))
        assertIs<AiUsageResult.Report>(session.aiUsage())
        assertFalse(session.aiUsageHidden)
        server.enqueue(json("""{"error":"boom"}""", code = 500))
        assertIs<AiUsageResult.Failed>(session.aiUsage(refresh = true))
        assertFalse(session.aiUsageHidden)
    }

    @Test
    fun taskUsageDecodesOnATaskAndOnARoom() {
        val usage = """{"input":1200,"output":300,"cachedInput":900,"costUsd":null,"turns":3,
            "lastTurn":{"input":100,"output":20,"costUsd":null},"context":{"tokens":50000,"window":200000}}"""
        val task = CompanionJson.decodeFromString<BotTask>(
            """{"threadId":"t","title":"T","createdAt":1,"usage":$usage}""",
        )
        assertEquals(1500L, task.usage?.let { it.input + it.output })
        assertNull(task.usage?.costUsd)
        assertEquals(200000L, task.usage?.context?.window)
        val room = CompanionJson.decodeFromString<Room>(
            """{"id":"r","threadId":"t","name":"R","memberIds":[],"defaultResponder":{"kind":"mentions"},
            "bulletin":"","unread":false,"createdAt":1,"usage":{"input":10,"output":5,"costUsd":0.5,"turns":1,
            "lastSpeaker":{"botId":"b","name":"B"}}}""",
        )
        assertEquals(1, room.usage?.turns)
        assertNull(CompanionJson.decodeFromString<BotTask>("""{"threadId":"t","title":"T","createdAt":1}""").usage)
    }

    @Test
    fun theTokenLineCarriesCountAndTurnsAndCostOnlyWhenKnown() {
        val unpriced = AiUsageRules.conversationUsage(TaskUsage(input = 1200, output = 300, turns = 3))
        assertEquals(1500L, unpriced?.tokens)
        assertEquals(3, unpriced?.turns)
        assertNull(unpriced?.costUsd)
        assertNull(unpriced?.contextTokens)
        val priced = AiUsageRules.conversationUsage(
            TaskUsage(input = 10, output = 5, costUsd = 0.4231, turns = 1, context = ContextUsage(50_000, 200_000)),
        )
        assertEquals(0.4231, priced?.costUsd)
        assertEquals(50_000L, priced?.contextTokens)
        assertEquals(200_000L, priced?.contextWindow)
        assertEquals("$0.42", AiUsageRules.formatCost(0.4231, Locale.US))
        assertEquals("$0.01", AiUsageRules.formatCost(0.002, Locale.US))
        assertEquals("1,500", AiUsageRules.formatCount(1500, Locale.US))
        assertNull(AiUsageRules.conversationUsage(null))
        assertNull(AiUsageRules.conversationUsage(TaskUsage()))
        val noWindow = AiUsageRules.conversationUsage(TaskUsage(input = 1, turns = 1, context = ContextUsage(500, null)))
        assertNull(noWindow?.contextTokens)
    }

    private suspend fun TestScope.session(): Session {
        val connection = requireNotNull(Connection.parse(server.url("/").toString()))
        return Session(
            scope = backgroundScope,
            connectionStore = object : ConnectionStore {
                override suspend fun load(): Connection = connection
                override suspend fun save(connection: Connection) = Unit
                override suspend fun clear() = Unit
                override suspend fun loadRegistry() = ConnectionRegistryRestore(
                    ConnectionRegistry(listOf(connection), connection.id),
                    migratedLegacyConnection = false,
                )
            },
            tokenStore = object : TokenStore {
                override suspend fun save(connectionId: String, token: String) = Unit
                override suspend fun read(connectionId: String): TokenStore.ReadResult =
                    TokenStore.ReadResult.Found("device-token")
                override suspend fun remove(connectionId: String) = Unit
            },
            onboardingStore = InMemoryOnboardingStore(),
            deviceNameProvider = { "Pixel" },
            eventsFn = { _, _, _ -> emptyFlow() },
            hydrateFn = { _, _ -> Fleet(emptyList(), emptyList()) },
        ).also { it.awaitRestored() }
    }

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
