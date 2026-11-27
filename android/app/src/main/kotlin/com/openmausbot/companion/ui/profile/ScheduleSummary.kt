package com.openmausbot.companion.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.openmausbot.companion.R
import com.openmausbot.companion.core.RoutineSchedule
import com.openmausbot.companion.ui.RelativeStamp
import java.time.ZoneId
import java.util.Locale

/** One line that says when a routine runs, in the language of the phone. */
@Composable
internal fun localizedScheduleSummary(schedule: RoutineSchedule): String = when (schedule.type) {
    RoutineSchedule.Kind.ONCE -> schedule.at?.let {
        RelativeStamp.dateAndTime(it, ZoneId.systemDefault(), Locale.getDefault())
    } ?: stringResource(R.string.mobile_routine_date_unavailable)

    RoutineSchedule.Kind.INTERVAL -> schedule.everyMinutes?.let { minutes ->
        val cadence = pluralStringResource(R.plurals.mobile_routine_every_minutes, minutes, minutes)
        schedule.anchorAt?.let { anchor ->
            stringResource(
                R.string.mobile_routine_interval_with_start,
                cadence,
                RelativeStamp.dateAndTime(anchor.toDouble(), ZoneId.systemDefault(), Locale.getDefault()),
            )
        } ?: cadence
    } ?: stringResource(R.string.mobile_routine_interval_unavailable)

    RoutineSchedule.Kind.DAILY -> {
        val selectedDays = schedule.weekdays.orEmpty()
        val days = when {
            selectedDays.size == 7 -> stringResource(R.string.mobile_every_day_3b2eb513)
            selectedDays == listOf(1, 2, 3, 4, 5) -> stringResource(R.string.mobile_weekdays_4ffc67b6)
            else -> {
                val names = mutableListOf<String>()
                for (day in selectedDays) {
                    weekdayShort(day)?.let { names += stringResource(it) }
                }
                names.joinToString(stringResource(R.string.mobile_routine_weekday_separator))
            }
        }
        stringResource(R.string.mobile_routine_daily_at, days, schedule.time ?: "—")
    }

    RoutineSchedule.Kind.UNKNOWN -> stringResource(R.string.mobile_routine_newer_schedule)
}

private fun weekdayShort(day: Int): Int? = when (day) {
    0 -> R.string.mobile_routine_sunday_short
    1 -> R.string.mobile_routine_monday_short
    2 -> R.string.mobile_routine_tuesday_short
    3 -> R.string.mobile_routine_wednesday_short
    4 -> R.string.mobile_routine_thursday_short
    5 -> R.string.mobile_routine_friday_short
    6 -> R.string.mobile_routine_saturday_short
    else -> null
}
