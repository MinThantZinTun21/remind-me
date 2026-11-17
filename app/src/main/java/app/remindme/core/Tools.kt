package app.remindme.core

import java.time.Duration
import java.time.LocalDateTime

/** What a tool hands back: the task as it now stands, or a refusal the owner (or the agent) can read. */
sealed interface ToolResult {
    data class Ok(val task: Task) : ToolResult
    data class Refused(val reason: String) : ToolResult
}

/**
 * The only way anything changes a task. The manual form, the notification buttons, the alarm screen
 * and (in the second build) the agent all go through here.
 */
class Tools(
    private val store: TaskStore,
    private val scheduler: FireScheduler,
    private val announcer: Announcer,
    private val fireLog: FireLog,
    private val clock: Clock,
) {
    @Synchronized
    fun create(draft: TaskDraft): ToolResult {
        val now = clock.now()
        refusal(draft)?.let { return it }
        // A repeating task begins at its first occurrence after now, even when the owner entered an earlier day.
        val due = draft.repeat.occurrenceAfter(draft.due, now) ?: return noOccurrence(draft)
        // Starting the watermark at now is what skips a warning whose moment has already passed.
        val task = store.put(
            Task(
                title = draft.title.trim(),
                due = due,
                repeat = draft.repeat,
                firstDue = draft.due,
                handledUntil = now,
                style = draft.style,
                leadTimes = draft.leadTimes.sortedDescending(),
            ),
        )
        schedule(task)
        return ToolResult.Ok(task)
    }

    /**
     * Changes a task. Moving the due time or changing how it repeats starts the task afresh from now: fires
     * from before the edit are dropped, along with any snooze, and a repeating task is worked out from the
     * new due time. An overdue task can keep its due time while the rest changes. Either way nothing whose
     * moment has already passed is fired because of an edit.
     */
    @Synchronized
    fun update(id: Long, draft: TaskDraft): ToolResult {
        val task = store.get(id)?.takeIf { it.doneAt == null } ?: return ToolResult.Refused(NO_SUCH_TASK)
        val now = clock.now()
        refusal(draft)?.let { return it }
        val changed = task.copy(
            title = draft.title.trim(),
            style = draft.style,
            leadTimes = draft.leadTimes.sortedDescending(),
            handledUntil = maxOf(task.handledUntil, now),
        )
        // The form shows a repeating task's current occurrence, so an unchanged due time means "leave the days alone".
        val sameDays = draft.due == task.due && draft.repeat.copy(until = null) == task.repeat.copy(until = null)
        val edited = if (sameDays) {
            val until = draft.repeat.until
            if (until != null && task.due > now && task.due.toLocalDate() > until) return ToolResult.Refused(RULE_ENDED)
            changed.copy(repeat = draft.repeat)
        } else {
            val due = draft.repeat.occurrenceAfter(draft.due, now) ?: return noOccurrence(draft)
            announcer.clear(id)
            changed.copy(
                due = due,
                repeat = draft.repeat,
                firstDue = draft.due,
                handledUntil = now,
                snoozedUntil = null,
                ringingUntil = null,
            )
        }
        schedule(store.put(edited))
        return ToolResult.Ok(edited)
    }

    /** What is wrong with a draft whatever the time is, or null when nothing is. */
    private fun refusal(draft: TaskDraft): ToolResult.Refused? = when {
        draft.title.isBlank() -> ToolResult.Refused(NEEDS_TITLE)
        draft.leadTimes.size > 2 -> ToolResult.Refused("A task can have at most two lead times.")
        draft.leadTimes.distinct().size < draft.leadTimes.size -> ToolResult.Refused("The two lead times are the same.")
        draft.leadTimes.any { it < Duration.ofMinutes(1) } -> ToolResult.Refused("A lead time has to be at least one minute.")
        draft.repeat.cycle == RepeatRule.Cycle.WEEKDAYS && draft.repeat.weekdays.isEmpty() ->
            ToolResult.Refused("Pick at least one weekday.")
        draft.repeat.every < 1 -> ToolResult.Refused("A task repeats every 1 or more.")
        else -> null
    }

    /** Why a draft has no occurrence left after now. */
    private fun noOccurrence(draft: TaskDraft) = ToolResult.Refused(if (draft.repeat.repeats) RULE_ENDED else ALREADY_PASSED)

    /** Removes a task and its fires. Returns the task as it was, for [restore] to undo, or null when there is none. */
    @Synchronized
    fun delete(id: Long): Task? {
        val task = store.get(id) ?: return null
        store.delete(id)
        scheduler.replace(id, emptyList(), clock.zone())
        announcer.clear(id)
        return task
    }

    /** Undo: puts a task back exactly as [delete] or [done] found it, fires included. */
    @Synchronized
    fun restore(task: Task) {
        schedule(store.put(task))
    }

    /** The tasks still to do, soonest first. */
    @Synchronized
    fun list(): List<Task> = store.all().filter { it.doneAt == null }.sortedBy { it.due }

    /** The one-time tasks marked done in the last thirty days, most recently done first. */
    @Synchronized
    fun history(): List<Task> {
        forgetOldHistory()
        return store.all().filter { it.doneAt != null }.sortedByDescending { it.doneAt }
    }

    /**
     * The owner takes a task back out of history. It returns to the list: overdue and quiet when its due
     * time has passed, otherwise with the fires still ahead of it scheduled again.
     */
    @Synchronized
    fun notDone(id: Long) {
        forgetOldHistory()
        val task = store.get(id)?.takeIf { it.doneAt != null } ?: return
        val now = clock.now()
        schedule(store.put(task.copy(doneAt = null, snoozedUntil = null, handledUntil = maxOf(task.handledUntil, now))))
    }

    private fun forgetOldHistory() {
        val cutoff = clock.now().minus(HISTORY_KEPT)
        store.all().filter { it.doneAt != null && it.doneAt <= cutoff }.forEach { store.delete(it.id) }
    }

    /**
     * The owner says they did it. A one-time task leaves the list for history. A repeating task moves to its next
     * occurrence: done before the due time skips the coming occurrence, and after the last occurrence the
     * task is gone. Returns the task as it was just before, for [restore] to undo, or null when there is no
     * such task still to do.
     */
    @Synchronized
    fun done(id: Long): Task? {
        val task = store.get(id)?.takeIf { it.doneAt == null } ?: return null
        val now = clock.now()
        if (!task.repeat.repeats) {
            schedule(store.put(task.copy(doneAt = now, ringingUntil = null)))
            announcer.clear(id)
            return task
        }
        val next = task.repeat.occurrenceAfter(task.firstDue, maxOf(task.due, now)) ?: return delete(id)
        schedule(
            store.put(
                task.copy(due = next, handledUntil = maxOf(task.handledUntil, now), snoozedUntil = null, ringingUntil = null),
            ),
        )
        announcer.clear(id)
        return task
    }

    /** The owner asks to be told about the occurrence again in ten minutes. The due time does not move. */
    @Synchronized
    fun snooze(id: Long) {
        val task = store.get(id)?.takeIf { it.doneAt == null } ?: return
        schedule(store.put(task.copy(snoozedUntil = clock.now().plus(SNOOZE_LENGTH), ringingUntil = null)))
        announcer.clear(id)
    }

    /** The owner silences the task without saying it is done: it stays overdue and says nothing more. */
    @Synchronized
    fun stop(id: Long) {
        val task = store.get(id)?.takeIf { it.doneAt == null } ?: return
        schedule(store.put(task.copy(snoozedUntil = null, ringingUntil = null)))
        announcer.clear(id)
    }

    /** A scheduled fire has arrived from the phone's alarm system. */
    @Synchronized
    fun fire(fire: Fire) {
        val task = store.get(fire.taskId) ?: return
        // The alarm system can deliver a fire the app has already caught up on, or one from before an edit.
        if (fire !in pendingFires(task)) return
        // The first fire of the next occurrence is what moves a repeating task on to it.
        val startsNext = fire.kind in OCCURRENCE_FIRES && fire.at > task.due
        deliver(if (startsNext) task.movedTo(nextDue(task)!!) else task, fire, caughtUp = false)
    }

    /**
     * Hands every pending fire to the alarm system again. Called when the phone starts, when the clock or
     * the time zone changes, when the app is updated and when it is opened. A fire that came due meanwhile
     * is announced now, once, as late: for a repeating task, only the latest occurrence that was missed.
     */
    @Synchronized
    fun reschedule() {
        val now = clock.now()
        forgetOldHistory()
        for (stored in store.all()) {
            val task = occurrenceReachedBy(stored, now)
            val missed = pendingFires(task).filter { it.at <= now }.maxByOrNull { it.at }
            if (missed != null) deliver(task, missed, caughtUp = true) else schedule(task)
        }
    }

    /** The task moved on past every occurrence that began (with a warning or its due time) while nothing was delivered. */
    private fun occurrenceReachedBy(task: Task, now: LocalDateTime): Task {
        if (task.doneAt != null) return task
        var current = task
        while (true) {
            val next = nextDue(current) ?: return current
            val begun = occurrenceFires(current, next).any { it.at > current.due && it.at > current.handledUntil && it.at <= now }
            if (!begun) return current
            current = current.movedTo(next)
        }
    }

    private fun nextDue(task: Task): LocalDateTime? = task.repeat.occurrenceAfter(task.firstDue, task.due)

    /** The next occurrence replaces the one before it: an unanswered occurrence and its snooze are dropped. */
    private fun Task.movedTo(next: LocalDateTime) = copy(due = next, snoozedUntil = null, ringingUntil = null)

    private fun deliver(task: Task, fire: Fire, caughtUp: Boolean) {
        val handledUntil = maxOf(task.handledUntil, fire.at)
        if (fire.kind == FireKind.RING_END) {
            // Nobody answered: the ring gives way to a notification that stays until the owner deals with it.
            val quiet = store.put(task.copy(handledUntil = handledUntil, ringingUntil = null))
            announcer.announce(Announcement(task.id, fire.kind, task.title, task.due, late = true, ring = false))
            schedule(quiet)
            return
        }
        val delivered = clock.instant()
        val lateness = Duration.between(fire.at.atZone(clock.zone()).toInstant(), delivered)
        val late = lateness >= LATE_AFTER
        // Only the due time rings, never an early warning. An alarm the phone slept through is told quietly
        // at start-up; one merely delivered late still rings.
        val leadTime = leadTimeOf(task, fire.kind)
        val ring = leadTime == null && task.style == DeliveryStyle.ALARM && !(caughtUp && late)
        val handled = store.put(
            task.copy(
                handledUntil = handledUntil,
                ringingUntil = if (ring) clock.now().plus(RING_LENGTH) else task.ringingUntil,
            ),
        )
        fireLog.record(FireRecord(task.id, task.title, fire.kind, fire.at, delivered, lateness, caughtUp))
        announcer.announce(Announcement(task.id, fire.kind, task.title, task.due, late = late, ring = ring, leadTime = leadTime))
        schedule(handled)
    }

    private fun schedule(task: Task) {
        scheduler.replace(task.id, pendingFires(task), clock.zone())
    }

    /**
     * The fires still to come. Only one occurrence of a repeating task is planned at a time: the next one's
     * fires join once the current occurrence's due time has been dealt with.
     */
    private fun pendingFires(task: Task): List<Fire> {
        if (task.doneAt != null) return emptyList()
        val answers = listOfNotNull(
            task.snoozedUntil?.let { Fire(task.id, FireKind.SNOOZE, it) },
            task.ringingUntil?.let { Fire(task.id, FireKind.RING_END, it) },
        )
        val next = if (task.handledUntil >= task.due) nextDue(task) else null
        return (occurrenceFires(task, task.due) + answers + next?.let { occurrenceFires(task, it) }.orEmpty())
            .filter { it.at > task.handledUntil }
    }

    /** The warnings and the due-time fire of the occurrence due at [due]. */
    private fun occurrenceFires(task: Task, due: LocalDateTime): List<Fire> = listOfNotNull(
        task.leadTimes.getOrNull(0)?.let { Fire(task.id, FireKind.WARNING_1, due.minus(it)) },
        task.leadTimes.getOrNull(1)?.let { Fire(task.id, FireKind.WARNING_2, due.minus(it)) },
        Fire(task.id, FireKind.DUE, due),
    )

    private fun leadTimeOf(task: Task, kind: FireKind): Duration? = when (kind) {
        FireKind.WARNING_1 -> task.leadTimes.getOrNull(0)
        FireKind.WARNING_2 -> task.leadTimes.getOrNull(1)
        else -> null
    }

    private companion object {
        val SNOOZE_LENGTH: Duration = Duration.ofMinutes(10)
        val RING_LENGTH: Duration = Duration.ofMinutes(10)
        val HISTORY_KEPT: Duration = Duration.ofDays(30)

        /** From this much lateness on, the owner is told when the task was due instead of "due now". */
        val LATE_AFTER: Duration = Duration.ofMinutes(1)
        const val NEEDS_TITLE = "A task needs a title."
        const val ALREADY_PASSED = "That due time has already passed."
        const val NO_SUCH_TASK = "That task is no longer on the list."
        const val RULE_ENDED = "That repeat rule ends before its first occurrence."
        val OCCURRENCE_FIRES = setOf(FireKind.WARNING_1, FireKind.WARNING_2, FireKind.DUE)
    }
}
