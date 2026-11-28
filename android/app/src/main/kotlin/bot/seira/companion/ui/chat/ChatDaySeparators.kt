package bot.seira.companion.ui.chat

import bot.seira.companion.core.TranscriptRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal enum class DayKind { TODAY, YESTERDAY, DATE }

internal data class DayLabel(val kind: DayKind, val date: LocalDate)

internal object ChatDaySeparators {
    /** A label on the first row and on each row that starts a new calendar day. */
    fun label(
        rows: List<TranscriptRow>,
        index: Int,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DayLabel? {
        val row = rows.getOrNull(index) ?: return null
        val day = dayOf(row.at, zone)
        if (index > 0 && dayOf(rows[index - 1].endAt, zone) == day) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val kind = when (day) {
            today -> DayKind.TODAY
            today.minusDays(1) -> DayKind.YESTERDAY
            else -> DayKind.DATE
        }
        return DayLabel(kind, day)
    }

    fun dateText(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    private fun dayOf(millis: Double, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis.toLong()).atZone(zone).toLocalDate()
}
