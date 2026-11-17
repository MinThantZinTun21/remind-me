package app.remindme.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How dates and times are worded everywhere the owner reads them. */
object Times {
    private val dayAndMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val dayMonthYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

    fun time(context: Context, time: LocalTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
    }

    fun time(context: Context, moment: LocalDateTime): String = time(context, moment.toLocalTime())

    fun date(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(dayAndMonth)
        else -> date.format(dayMonthYear)
    }

    fun dateAndTime(context: Context, moment: LocalDateTime): String =
        "${date(moment.toLocalDate())}, ${time(context, moment)}"
}
