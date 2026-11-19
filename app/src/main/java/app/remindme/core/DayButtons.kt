package app.remindme.core

import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

/** What the seven day buttons under a task stand for. See GLOSSARY.md. */
sealed interface DayRow {
    /** A task that repeats every day or on chosen weekdays: the lit buttons are its days. */
    data class RepeatsOn(val days: Set<DayOfWeek>) : DayRow

    /** A one-time task: the one lit button is the day it falls on, and tapping another moves it there. */
    data class MoveTo(val day: DayOfWeek) : DayRow
}

/** The day row this task gets; null for a task that repeats some other way (every few days or weeks, monthly, yearly). */
fun Task.dayRow(): DayRow? = when {
    repeat.cycle == RepeatRule.Cycle.ONCE -> DayRow.MoveTo(due.dayOfWeek)
    repeat.cycle == RepeatRule.Cycle.WEEKDAYS -> DayRow.RepeatsOn(repeat.weekdays)
    repeat.cycle == RepeatRule.Cycle.DAYS && repeat.every == 1 -> DayRow.RepeatsOn(DayOfWeek.entries.toSet())
    else -> null
}

/**
 * The one-tap corrections the day buttons make. Each goes through [Tools.update], so the task is worked out
 * afresh from now and the change can be put back with [Tools.restore].
 */
class DayButtons(private val tools: Tools, private val clock: Clock) {

    /** A tap on [day] under [task]: turns that day on or off, or moves a one-time task to it. */
    fun tap(task: Task, day: DayOfWeek): ToolResult = when (val row = task.dayRow()) {
        is DayRow.RepeatsOn -> {
            val days = if (day in row.days) row.days - day else row.days + day
            if (days.isEmpty()) {
                ToolResult.Refused(LAST_DAY)
            } else {
                val rule = if (days.size == 7) RepeatRule.daily(task.repeat.until) else RepeatRule.weekdays(days, task.repeat.until)
                changed(task, rule)
            }
        }
        is DayRow.MoveTo -> if (day == row.day) ToolResult.Refused(ALREADY_THAT_DAY) else moved(task, day)
        null -> ToolResult.Refused(NO_DAY_ROW)
    }

    /** Makes a one-time task come round every week on the day it falls on. */
    fun repeatWeekly(task: Task): ToolResult =
        if (task.repeat.repeats) ToolResult.Refused(ALREADY_REPEATS) else changed(task, RepeatRule.weekdays(setOf(task.due.dayOfWeek)))

    /** Takes back one day change: the task as it was before that tap. */
    fun undo(before: Task): ToolResult = tools.restore(before)

    /**
     * The new days, from the next time the task's clock time comes round after now. Asking from today
     * rather than from the task's own due time is what lets a day added for tomorrow come before the old next one.
     */
    private fun changed(task: Task, rule: RepeatRule): ToolResult {
        val today = clock.now().toLocalDate()
        val from = if (task.repeat.repeats) today.atTime(task.due.toLocalTime()) else task.due
        return tools.update(task.id, task.details().copy(due = from, repeat = rule))
    }

    /** The next [day] at the task's time: today, if it is that day and the time has not passed yet. */
    private fun moved(task: Task, day: DayOfWeek): ToolResult {
        val now = clock.now()
        val time = task.due.toLocalTime()
        val today = now.toLocalDate()
        val date = if (today.dayOfWeek == day && today.atTime(time) > now) today else today.with(TemporalAdjusters.next(day))
        return tools.update(task.id, task.details().copy(due = date.atTime(time)))
    }

    private fun Task.details() = TaskDetails(title, due, style, leadTimes, repeat)

    companion object {
        const val LAST_DAY = "A repeating task needs at least one day. To make it one-time, use Edit."
        private const val ALREADY_THAT_DAY = "It is already on that day."
        private const val ALREADY_REPEATS = "It already repeats."
        private const val NO_DAY_ROW = "This task does not repeat on weekdays."
    }
}
