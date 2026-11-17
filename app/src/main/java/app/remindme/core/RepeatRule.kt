package app.remindme.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * How a task comes round again. See GLOSSARY.md.
 *
 * Every occurrence is worked out from the first due time the user entered (the anchor), never from the
 * occurrence before it, so a task due on the 31st goes back to the 31st after a shorter month.
 */
data class RepeatRule(
    val cycle: Cycle,
    /** Every how many days or weeks, for the two cycles that count them. */
    val every: Int = 1,
    /** The days of the week a [Cycle.WEEKDAYS] task falls on. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** The last day the task can fall on; null when it goes on for good. */
    val until: LocalDate? = null,
) {
    enum class Cycle { ONCE, DAYS, WEEKDAYS, WEEKS, MONTHLY, YEARLY }

    val repeats: Boolean get() = cycle != Cycle.ONCE

    /** True when [other] puts a task on the same days, whatever day either of them ends. */
    fun sameDaysAs(other: RepeatRule): Boolean = copy(until = null) == other.copy(until = null)

    /** The least time there can be between one occurrence and the next; null for a task that happens once. */
    fun shortestGap(): Duration? = when (cycle) {
        Cycle.ONCE -> null
        Cycle.DAYS -> Duration.ofDays(every.toLong())
        Cycle.WEEKS -> Duration.ofDays(7L * every)
        // From the end of January to the end of a 28-day February.
        Cycle.MONTHLY -> Duration.ofDays(28)
        Cycle.YEARLY -> Duration.ofDays(365)
        Cycle.WEEKDAYS -> weekdays.minOfOrNull { day -> (1L..7L).first { day.plus(it) in weekdays } }?.let(Duration::ofDays)
    }

    /** The first occurrence strictly after [moment], or null when the rule has none left. */
    fun occurrenceAfter(anchor: LocalDateTime, moment: LocalDateTime): LocalDateTime? {
        val found = when (cycle) {
            Cycle.ONCE -> anchor.takeIf { it > moment }
            Cycle.DAYS -> stepped(anchor, moment, ChronoUnit.DAYS, every)
            Cycle.WEEKS -> stepped(anchor, moment, ChronoUnit.WEEKS, every)
            // Adding months or years falls back to the month's last day when it is too short: 31 Jan, 28 Feb, 31 Mar.
            Cycle.MONTHLY -> stepped(anchor, moment, ChronoUnit.MONTHS, 1)
            Cycle.YEARLY -> stepped(anchor, moment, ChronoUnit.YEARS, 1)
            Cycle.WEEKDAYS -> {
                val from = maxOf(anchor.toLocalDate(), moment.toLocalDate())
                // Eight days reach the same weekday a week on, for when today's time has already gone.
                (0L..7L).map { from.plusDays(it).atTime(anchor.toLocalTime()) }
                    .firstOrNull { it.dayOfWeek in weekdays && it > moment }
            }
        }
        return found?.takeIf { until == null || !it.toLocalDate().isAfter(until) }
    }

    /** The first of anchor, anchor + [size] units, anchor + 2 × [size] units… that comes after [moment]. */
    private fun stepped(anchor: LocalDateTime, moment: LocalDateTime, unit: ChronoUnit, size: Int): LocalDateTime {
        // Start one step short of the estimate: month and year counts can come out one low around short months.
        var step = maxOf(0, unit.between(anchor, moment) / size - 1)
        while (anchor.plus(step * size, unit) <= moment) step++
        return anchor.plus(step * size, unit)
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
