package com.openmausbot.companion.core

import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class EngineUsageTest {
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

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun report(text: String): EngineLimitsReport = CompanionJson.decodeFromString(text)

    private fun seconds(value: Long): Instant = Instant.ofEpochSecond(value)

    @Test
    fun decodesTheFixtureWithAndWithoutWindows() {
        val decoded = decodeFixture<EngineLimitsReport>("limit-resets")
        assertEquals(listOf("claude", "codex", "grok", "opencode-local"), decoded.engines.map { it.engine })
        assertEquals(listOf("5-hour", "weekly"), decoded.engines[0].windows?.map { it.window })
        assertTrue(decoded.engines[0].blocked)
        assertNull(decoded.engines[1].windows)
        assertNull(decoded.engines[2].usedPercent)
        assertNull(decoded.engines[2].window)
    }

    @Test
    fun keepsAWindowNameThisBuildDoesNotKnow() {
        val decoded = decodeFixture<EngineLimitsReport>("limit-resets")
        assertEquals("monthly", decoded.engines[3].window)
        assertEquals("opencode", decoded.engines[3].driver)
        assertEquals(LimitWindowKind.OTHER, EngineUsage.windowKind("monthly"))
    }

    @Test
    fun readsAnEngineThatSendsNoWindowsAndNoHold() {
        val decoded = report(
            """{"checkedAt":1,"engines":[{"engine":"claude","name":"Claude","driver":"claude","window":"5-hour",
            "usedPercent":12,"resetsAt":null,"blocked":false}]}""",
        )
        assertNull(decoded.engines[0].windows)
        assertNull(decoded.engines[0].held)
    }

    @Test
    fun engineLimitsAsksForTheStoredReadWithoutRefresh() = runTest {
        server.enqueue(json(fixtureText("limit-resets")))
        val loaded = client.engineLimits()
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/limit-resets", request.path)
        assertEquals("Bearer paired-token", request.getHeader("Authorization"))
        assertEquals(4, loaded?.engines?.size)
    }

    @Test
    fun engineLimitsRefreshAddsTheQuery() = runTest {
        server.enqueue(json(fixtureText("limit-resets")))
        client.engineLimits(refresh = true)
        assertEquals("/api/limit-resets?refresh=1", server.takeRequest().path)
    }

    @Test
    fun forbiddenGivesNull() = runTest {
        server.enqueue(json("""{"error":"API keys can only be changed on your computer"}""", code = 403))
        assertNull(client.engineLimits())
    }

    @Test
    fun notFoundGivesNull() = runTest {
        server.enqueue(json("""{"error":"Not found"}""", code = 404))
        assertNull(client.engineLimits())
    }

    @Test
    fun aServerErrorStillThrows() = runTest {
        server.enqueue(json("""{"error":"boom"}""", code = 500))
        val error = assertFailsWith<APIError.Status> { client.engineLimits() }
        assertEquals(500, error.code)
    }

    @Test
    fun toneFollowsTheNearLimitBand() {
        assertEquals(UsageTone.FINE, EngineUsage.tone(40.0, false))
        assertEquals(UsageTone.NEAR, EngineUsage.tone(90.0, false))
        assertEquals(UsageTone.OUT, EngineUsage.tone(100.0, false))
        assertEquals(UsageTone.OUT, EngineUsage.tone(10.0, true))
        assertEquals(UsageTone.NEAR, usageTone(EngineLimit("a", "A", "claude", usedPercent = 95.0)))
        assertFalse(EngineUsage.nearLimit(89.9, false))
        assertTrue(EngineUsage.nearLimit(90.0, false))
    }

    @Test
    fun anEngineWithoutWindowsReportsNothing() {
        val apiKey = EngineLimit("grok", "Grok", "grok")
        assertFalse(EngineUsage.reportsUsage(apiKey))
        assertFalse(EngineUsage.reportsUsage(null))
        assertTrue(usageRows(apiKey).isEmpty())
        assertTrue(EngineUsage.reportsUsage(EngineLimit("a", "A", "claude", blocked = true)))
    }

    @Test
    fun noEngineReportingUsageLeavesTheListEmpty() {
        val engines = listOf(EngineLimit("grok", "Grok", "grok"), EngineLimit("local", "Local model", "opencode"))
        assertTrue(EngineUsage.reporting(engines).isEmpty())
        assertTrue(EngineUsage.reporting(emptyList()).isEmpty())
        val withPlan = engines + EngineLimit("claude", "Claude", "claude", usedPercent = 5.0)
        assertEquals(listOf("claude"), EngineUsage.reporting(withPlan).map { it.engine })
    }

    @Test
    fun rowsListEveryWindowWithTheShareLeft() {
        val limit = EngineLimit(
            "claude", "Claude", "claude", window = "5-hour", usedPercent = 92.0, resetsAt = 2_000_000.0,
            windows = listOf(
                LimitWindowUsage("5-hour", 92.0, 2_000_000.0),
                LimitWindowUsage("weekly", 40.4, 9_000_000.0),
            ),
        )
        val rows = usageRows(limit)
        assertEquals(listOf("5-hour", "weekly"), rows.map { it.window })
        assertEquals(listOf(8, 60), rows.map { it.left })
        assertEquals(listOf(UsageTone.NEAR, UsageTone.FINE), rows.map { it.tone })
        assertEquals(seconds(9_000), rows[1].resetsAt)
    }

    @Test
    fun aBlockedEngineIsAtLimitInItsDecidingWindowOnly() {
        val limit = EngineLimit(
            "claude", "Claude", "claude", window = "5-hour", usedPercent = 30.0, resetsAt = 5_000_000.0,
            blocked = true,
            windows = listOf(
                LimitWindowUsage("5-hour", 30.0, 1_000_000.0),
                LimitWindowUsage("weekly", 20.0, 9_000_000.0),
            ),
        )
        val rows = usageRows(limit)
        assertEquals(UsageTone.OUT, rows[0].tone)
        assertEquals(0, rows[0].left)
        assertEquals(seconds(5_000), rows[0].resetsAt)
        assertEquals(UsageTone.FINE, rows[1].tone)
        assertEquals(80, rows[1].left)
    }

    @Test
    fun aServerWithoutAListGivesTheDecidingWindowAlone() {
        val limit = EngineLimit("codex", "Codex", "codex", window = "weekly", usedPercent = 62.0, resetsAt = 7_000_000.0)
        val rows = usageRows(limit)
        assertEquals(1, rows.size)
        assertEquals("weekly", rows[0].window)
        assertEquals(38, rows[0].left)
    }

    @Test
    fun limitLineKeepsOnlyAResetStillAhead() {
        val now = seconds(1_000)
        val used = limitLine(EngineLimit("a", "Claude", "claude", usedPercent = 91.6, resetsAt = 5_000_000.0), now)
        assertEquals(92, used.usedPercent)
        assertFalse(used.atLimit)
        assertEquals(seconds(5_000), used.resetsAt)
        val blocked = limitLine(
            EngineLimit("a", "Claude", "claude", usedPercent = 100.0, resetsAt = 500_000.0, blocked = true),
            now,
        )
        assertTrue(blocked.atLimit)
        assertNull(blocked.usedPercent)
        assertNull(blocked.resetsAt)
    }

    @Test
    fun resetTimeNamesTheWeekdayOnlyWhenNotToday() {
        val now = seconds(1_795_277_400)
        val sameDay = limitResetTime(now.plusSeconds(600), now, Locale.US, ZoneOffset.UTC)
        val laterDay = limitResetTime(now.plusSeconds(3 * 86_400), now, Locale.US, ZoneOffset.UTC)
        assertTrue(sameDay.contains(":"))
        assertTrue(laterDay.length > sameDay.length)
        val morning = Instant.parse("2026-11-21T08:00:00Z")
        assertEquals("3:40 PM", limitResetTime(Instant.parse("2026-11-21T15:40:00Z"), morning, Locale.US, ZoneOffset.UTC))
        assertEquals("Mon 9:05 AM", limitResetTime(Instant.parse("2026-11-23T09:05:00Z"), morning, Locale.US, ZoneOffset.UTC))
    }

    @Test
    fun theDateOfAMissingResetIsNull() {
        assertNull(EngineUsage.date(null))
        assertNotNull(EngineUsage.date(1.0))
    }
}
