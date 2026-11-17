package app.remindme.wording

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How dates and times are worded everywhere the user reads them. */
object Times {
    private val dayAndMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val dayMonthYear = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

    fun time(context: Context, time: LocalTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
    }

    fun time(context: Context, moment: LocalDateTime): String = time(context, moment.toLocalTime())

    /** What an announcement at the due time says under the task's title: "Due 3:00 PM". */
    fun dueAt(context: Context, due: LocalDateTime): String = "Due ${time(context, due)}"

    fun date(date: LocalDate): String {
        val today = LocalDate.now()
        return when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(dayAndMonth)
            else -> date.format(dayMonthYear)
        }
    }

    fun dateAndTime(context: Context, moment: LocalDateTime): String =
        "${date(moment.toLocalDate())}, ${time(context, moment)}"
}
