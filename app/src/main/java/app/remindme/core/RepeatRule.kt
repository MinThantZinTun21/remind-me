package app.remindme.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * How a task comes round again. See GLOSSARY.md.
 *
 * Every occurrence is worked out from the first due time the owner entered (the anchor), never from the
 * occurrence before it, so a task due on the 31st goes back to the 31st after a shorter month.
 */
data class RepeatRule(
    val cycle: Cycle,
    /** Every how many days, weeks, months or years. */
    val every: Int = 1,
    /** The days of the week a [Cycle.WEEKDAYS] task falls on. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** The last day the task can fall on; null when it goes on for good. */
    val until: LocalDate? = null,
) {
    enum class Cycle { ONCE, DAYS, WEEKDAYS, WEEKS, MONTHLY, YEARLY }

    val repeats: Boolean get() = cycle != Cycle.ONCE

    /** The first occurrence strictly after [moment], or null when the rule has none left. */
    fun occurrenceAfter(anchor: LocalDateTime, moment: LocalDateTime): LocalDateTime? {
        val found = when (cycle) {
            Cycle.ONCE -> anchor.takeIf { it > moment }
            Cycle.DAYS -> firstAfter(moment, ChronoUnit.DAYS.between(anchor, moment)) { anchor.plusDays(it * every) }
            Cycle.WEEKS -> firstAfter(moment, ChronoUnit.WEEKS.between(anchor, moment)) { anchor.plusWeeks(it * every) }
            // plusMonths and plusYears fall back to the month's last day when it is too short: 31 Jan, 28 Feb, 31 Mar.
            Cycle.MONTHLY -> firstAfter(moment, ChronoUnit.MONTHS.between(anchor, moment)) { anchor.plusMonths(it * every) }
            Cycle.YEARLY -> firstAfter(moment, ChronoUnit.YEARS.between(anchor, moment)) { anchor.plusYears(it * every) }
            Cycle.WEEKDAYS -> {
                val from = maxOf(anchor.toLocalDate(), moment.toLocalDate())
                // Eight days reach the same weekday a week on, for when today's time has already gone.
                (0L..7L).map { from.plusDays(it).atTime(anchor.toLocalTime()) }
                    .firstOrNull { it.dayOfWeek in weekdays && it > moment }
            }
        }
        return found?.takeIf { until == null || !it.toLocalDate().isAfter(until) }
    }

    /** [periods] is how many whole days, weeks, months or years lie between the anchor and [moment]. */
    private fun firstAfter(moment: LocalDateTime, periods: Long, occurrence: (Long) -> LocalDateTime): LocalDateTime {
        // Start one step short of the estimate: month and year counts can come out one low around short months.
        var step = maxOf(0, periods / every - 1)
        while (occurrence(step) <= moment) step++
        return occurrence(step)
    }

    companion object {
        val ONCE = RepeatRule(Cycle.ONCE)
        fun daily(until: LocalDate? = null) = RepeatRule(Cycle.DAYS, until = until)
        fun everyDays(every: Int, until: LocalDate? = null) = RepeatRule(Cycle.DAYS, every = every, until = until)
        fun weekdays(days: Set<DayOfWeek>, until: LocalDate? = null) = RepeatRule(Cycle.WEEKDAYS, weekdays = days, until = until)
        fun everyWeeks(every: Int, until: LocalDate? = null) = RepeatRule(Cycle.WEEKS, every = every, until = until)
        fun monthly(until: LocalDate? = null) = RepeatRule(Cycle.MONTHLY, until = until)
        fun yearly(until: LocalDate? = null) = RepeatRule(Cycle.YEARLY, until = until)
    }
}
