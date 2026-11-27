package com.openmausbot.companion.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutineWeekTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val monday = LocalDate.of(2026, 10, 5)

    private fun ms(day: LocalDate, hour: Int): Double =
        day.atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli().toDouble()

    private fun routine(id: String, schedule: String): Routine = CompanionJson.decodeFromString(
        """{"id":"$id","name":"$id name","prompt":"p","botId":"b1","runOn":"maus","enabled":true,
            "schedule":$schedule,"durationMinutes":30,"nextRunAt":null,"createdAt":${ms(monday.minusDays(30), 0)},"updatedAt":0}""",
    )

    private fun run(id: String, routine: String, at: Double, status: String): RoutineRun =
        CompanionJson.decodeFromString(
            """{"id":"$id","routineId":"$routine","routineName":"$routine name","botId":"b1","runOn":"maus",
                "scheduledFor":$at,"status":"$status","manual":false,"createdAt":$at}""",
        )

    @Test
    fun theWeekListCoversSevenDaysFromTheGivenDay() {
        val days = RoutineCalendar.nextSevenDays(monday)
        assertEquals(monday, days.first())
        assertEquals(monday.plusDays(6), days.last())
        assertEquals(7, days.size)
    }

    @Test
    fun runsAndProjectionsComeOldestFirstWithTheirStatus() {
        val brief = routine("brief", """{"type":"daily","time":"09:00","weekdays":[0,1,2,3,4,5,6]}""")
        val runs = listOf(
            run("r1", "brief", ms(monday, 9), "completed"),
            run("r2", "brief", ms(monday.plusDays(1), 9), "failed"),
        )
        val rows = RoutineCalendar.weekRows(listOf(brief), runs, monday, zone)
        assertEquals(7, rows.size)
        assertEquals(rows.map { it.item.at }.sorted(), rows.map { it.item.at })
        assertEquals(RoutineWeekStatus.COMPLETED, rows[0].status)
        assertEquals(RoutineWeekStatus.FAILED, rows[1].status)
        assertTrue(rows.drop(2).all { it.status == RoutineWeekStatus.SCHEDULED })
        assertEquals(monday.plusDays(2), rows[2].day)
    }

    @Test
    fun aRunStillMovingIsNeitherCompletedNorFailed() {
        val brief = routine("brief", """{"type":"daily","time":"09:00","weekdays":[1]}""")
        val rows = RoutineCalendar.weekRows(listOf(brief), listOf(run("r1", "brief", ms(monday, 9), "running")), monday, zone)
        assertEquals(RoutineWeekStatus.OTHER, rows.single().status)
    }

    @Test
    fun runsOutsideTheSevenDaysAreLeftOut() {
        val old = run("r1", "brief", ms(monday.minusDays(1), 9), "completed")
        val late = run("r2", "brief", ms(monday.plusDays(7), 9), "completed")
        assertTrue(RoutineCalendar.weekRows(emptyList(), listOf(old, late), monday, zone).isEmpty())
    }
}
