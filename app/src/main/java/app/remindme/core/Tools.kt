package app.remindme.core

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * What a tool hands back: the task as it now stands (as it last stood, when the tool removed it), or a
 * refusal the user (or the agent) can read.
 */
sealed interface ToolResult {
    data class Ok(val task: Task) : ToolResult
    data class Refused(val reason: String) : ToolResult
}

/**
 * The only way anything changes a task. The manual form, the notification buttons, the alarm screen
 * and (in the second build) the agent all go through here. What the phone then says, and when, is
 * for [Fires] to work out.
 */
class Tools(
    private val store: TaskStore,
    private val fires: Fires,
    private val announcer: Announcer,
    private val clock: Clock,
) {
    fun create(details: TaskDetails): ToolResult = exclusively {
        val now = clock.now()
        refusal(details)?.let { return it }
        // A repeating task begins at its first occurrence after now, even when the user entered an earlier day.
        val due = details.repeat.occurrenceAfter(details.due, now) ?: return noOccurrence(details)
        // Starting the watermark at now is what skips a warning whose moment has already passed.
        val task = store.put(
            Task(
                title = details.title.trim(),
                due = due,
                repeat = details.repeat,
                firstDue = details.due,
                handledUntil = now,
                style = details.style,
                leadTimes = details.leadTimes.sortedDescending(),
            ),
        )
        fires.schedule(task)
        ToolResult.Ok(task)
    }

    /**
     * Changes a task. Moving the due time or changing how it repeats starts the task afresh from now: fires
     * from before the edit are dropped, along with any snooze, and a repeating task is worked out from the
     * new due time (see [anchorFor]). An overdue task can keep its due time while the rest changes. Either way nothing whose
     * moment has already passed is fired because of an edit.
     */
    fun update(id: Long, details: TaskDetails): ToolResult = exclusively {
        val task = toDo(id) ?: return ToolResult.Refused(NO_SUCH_TASK)
        val now = clock.now()
        refusal(details)?.let { return it }
        val changed = task.copy(
            title = details.title.trim(),
            style = details.style,
            leadTimes = details.leadTimes.sortedDescending(),
            handledUntil = maxOf(task.handledUntil, now),
        )
        // The form shows a repeating task's current occurrence, so an unchanged due time means "leave the days alone".
        val unmoved = details.due == task.due && details.repeat.sameDaysAs(task.repeat)
        val edited = if (unmoved) {
            val until = details.repeat.until
            if (until != null && task.due > now && task.due.toLocalDate() > until) return ToolResult.Refused(RULE_ENDED)
            changed.copy(repeat = details.repeat)
        } else {
            val anchor = task.anchorFor(details)
            // The first occurrence from the date the user gave that is still to come.
            val from = maxOf(now, details.due.minusNanos(1))
            val due = details.repeat.occurrenceAfter(anchor, from) ?: return noOccurrence(details)
            announcer.clear(id)
            changed.quiet().copy(due = due, repeat = details.repeat, firstDue = anchor, handledUntil = now)
        }
        saved(edited)
    }

    /** What is wrong with the details whatever the time is, or null when nothing is. */
    private fun refusal(details: TaskDetails): ToolResult.Refused? = when {
        details.title.isBlank() -> ToolResult.Refused(NEEDS_TITLE)
        details.leadTimes.size > 2 -> ToolResult.Refused("A task can have at most two lead times.")
        details.leadTimes.distinct().size < details.leadTimes.size -> ToolResult.Refused("The two lead times are the same.")
        details.leadTimes.any { it < Duration.ofMinutes(1) || it != it.truncatedTo(ChronoUnit.MINUTES) } ->
            ToolResult.Refused("A lead time is a whole number of minutes, at least one.")
        details.repeat.cycle == RepeatRule.Cycle.WEEKDAYS && details.repeat.weekdays.isEmpty() ->
            ToolResult.Refused("Pick at least one weekday.")
        details.repeat.every < 1 -> ToolResult.Refused("A task repeats every 1 or more.")
        // A warning that long would come at or before the occurrence before it.
        details.repeat.shortestGap()?.let { gap -> details.leadTimes.any { it >= gap } } == true ->
            ToolResult.Refused("A lead time has to be shorter than the time between occurrences.")
        else -> null
    }

    /** Why the details leave no occurrence after now. */
    private fun noOccurrence(details: TaskDetails) = ToolResult.Refused(if (details.repeat.repeats) RULE_ENDED else ALREADY_PASSED)

    /** Removes a task and its fires, and hands back the task as it was. */
    fun delete(id: Long): ToolResult = exclusively {
        val task = store.get(id) ?: return ToolResult.Refused(NO_SUCH_TASK)
        store.delete(id)
        fires.cancel(id)
        announcer.clear(id)
        ToolResult.Ok(task)
    }

    /** Undo: puts a task back exactly as it was before [delete] or [done], fires included. */
    fun restore(task: Task): ToolResult = exclusively {
        fires.schedule(store.put(task))
        ToolResult.Ok(task)
    }

    /** The tasks still to do, soonest first. */
    fun list(): List<Task> = exclusively { store.all().filter { it.doneAt == null }.sortedBy { it.due } }

    /** The one-time tasks marked done and still in history, most recently done first. */
    fun history(): List<Task> = exclusively {
        val now = clock.now()
        store.all().filter { it.inHistory(now) }.sortedByDescending { it.doneAt }
    }

    /**
     * The user takes a task back out of history. It returns to the list: overdue and quiet when its due
     * time has passed, otherwise with the fires still ahead of it scheduled again.
     */
    fun notDone(id: Long): ToolResult = exclusively {
        val now = clock.now()
        val task = store.get(id)?.takeIf { it.inHistory(now) } ?: return ToolResult.Refused(NOT_IN_HISTORY)
        saved(task.copy(doneAt = null, snoozedUntil = null, handledUntil = maxOf(task.handledUntil, now)))
    }

    /**
     * The user says they did it. A one-time task leaves the list for history. A repeating task moves to its next
     * occurrence: done before the due time skips the coming occurrence, and after the last occurrence the
     * task is gone.
     */
    fun done(id: Long): ToolResult = exclusively {
        val task = toDo(id) ?: return ToolResult.Refused(NO_SUCH_TASK)
        val now = clock.now()
        val after = if (task.repeat.repeats) {
            val next = task.repeat.occurrenceAfter(task.firstDue, maxOf(task.due, now)) ?: return delete(id)
            task.movedTo(next).copy(handledUntil = maxOf(task.handledUntil, now))
        } else {
            task.copy(doneAt = now, ringingUntil = null)
        }
        announcer.clear(id)
        saved(after)
    }

    /** The user asks to be told about the occurrence again in ten minutes. The due time does not move. */
    fun snooze(id: Long): ToolResult = exclusively {
        val task = toDo(id) ?: return ToolResult.Refused(NO_SUCH_TASK)
        announcer.clear(id)
        saved(task.copy(snoozedUntil = clock.instant().plus(SNOOZE_LENGTH), ringingUntil = null))
    }

    /** The user silences the task without saying it is done: it stays overdue and says nothing more. */
    fun stop(id: Long): ToolResult = exclusively {
        val task = toDo(id) ?: return ToolResult.Refused(NO_SUCH_TASK)
        announcer.clear(id)
        saved(task.quiet())
    }

    /** Stores the task as a tool left it and plans its fires. */
    private fun saved(task: Task): ToolResult.Ok = ToolResult.Ok(store.put(task).also(fires::schedule))

    /** The task with this id, when it is still to do. */
    private fun toDo(id: Long): Task? = store.get(id)?.takeIf { it.doneAt == null }

    /** Tools and fires change the same tasks, so they take turns. */
    private inline fun <T> exclusively(block: () -> T): T = synchronized(fires, block)

    private companion object {
        const val NEEDS_TITLE = "A task needs a title."
        const val ALREADY_PASSED = "That due time has already passed."
        const val NO_SUCH_TASK = "That task is no longer on the list."
        const val NOT_IN_HISTORY = "That task is not in history."
        const val RULE_ENDED = "That repeat rule ends before its first occurrence."
    }
}
