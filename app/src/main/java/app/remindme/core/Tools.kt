package app.remindme.core

import java.time.Duration

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
        val title = draft.title.trim()
        if (title.isEmpty()) return ToolResult.Refused(NEEDS_TITLE)
        if (draft.due <= now) return ToolResult.Refused(ALREADY_PASSED)
        val task = store.put(Task(title = title, due = draft.due, handledUntil = now))
        schedule(task)
        return ToolResult.Ok(task)
    }

    /**
     * Changes a task. Moving the due time starts the task afresh from now: fires from before the edit
     * are dropped, along with any snooze. An overdue task can keep its due time while its title changes.
     */
    @Synchronized
    fun update(id: Long, draft: TaskDraft): ToolResult {
        val task = store.get(id)?.takeIf { it.doneAt == null } ?: return ToolResult.Refused(NO_SUCH_TASK)
        val now = clock.now()
        val title = draft.title.trim()
        if (title.isEmpty()) return ToolResult.Refused(NEEDS_TITLE)
        val edited = if (draft.due == task.due) {
            task.copy(title = title)
        } else {
            if (draft.due <= now) return ToolResult.Refused(ALREADY_PASSED)
            announcer.clear(id)
            task.copy(title = title, due = draft.due, handledUntil = now, snoozedUntil = null)
        }
        schedule(store.put(edited))
        return ToolResult.Ok(edited)
    }

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

    /**
     * The owner says they did it. Returns the task as it was just before, for [restore] to undo,
     * or null when there is no such task still to do.
     */
    @Synchronized
    fun done(id: Long): Task? {
        val task = store.get(id)?.takeIf { it.doneAt == null } ?: return null
        schedule(store.put(task.copy(doneAt = clock.now())))
        announcer.clear(id)
        return task
    }

    /** The owner asks to be told about the occurrence again in ten minutes. The due time does not move. */
    @Synchronized
    fun snooze(id: Long) {
        val task = store.get(id)?.takeIf { it.doneAt == null } ?: return
        schedule(store.put(task.copy(snoozedUntil = clock.now().plus(SNOOZE_LENGTH))))
        announcer.clear(id)
    }

    /** The owner silences the task without saying it is done: it stays overdue and says nothing more. */
    @Synchronized
    fun stop(id: Long) {
        val task = store.get(id)?.takeIf { it.doneAt == null } ?: return
        schedule(store.put(task.copy(snoozedUntil = null)))
        announcer.clear(id)
    }

    /** A scheduled fire has arrived from the phone's alarm system. */
    @Synchronized
    fun fire(fire: Fire) {
        val task = store.get(fire.taskId) ?: return
        // The alarm system can deliver a fire the app has already caught up on, or one from before an edit.
        if (fire !in pendingFires(task)) return
        deliver(task, fire, caughtUp = false)
    }

    /**
     * Hands every pending fire to the alarm system again. Called when the phone starts, when the clock or
     * the time zone changes, when the app is updated and when it is opened. A fire that came due meanwhile
     * is announced now, once, as late.
     */
    @Synchronized
    fun reschedule() {
        val now = clock.now()
        for (task in store.all()) {
            val missed = pendingFires(task).filter { it.at <= now }.maxByOrNull { it.at }
            if (missed != null) deliver(task, missed, caughtUp = true) else schedule(task)
        }
    }

    private fun deliver(task: Task, fire: Fire, caughtUp: Boolean) {
        val handled = store.put(task.copy(handledUntil = maxOf(task.handledUntil, fire.at)))
        val delivered = clock.instant()
        val scheduled = fire.at.atZone(clock.zone()).toInstant()
        fireLog.record(
            FireRecord(task.id, task.title, fire.kind, fire.at, delivered, Duration.between(scheduled, delivered), caughtUp),
        )
        announcer.announce(Announcement(task.id, fire.kind, task.title, task.due, late = caughtUp))
        schedule(handled)
    }

    private fun schedule(task: Task) {
        scheduler.replace(task.id, pendingFires(task), clock.zone())
    }

    private fun pendingFires(task: Task): List<Fire> {
        if (task.doneAt != null) return emptyList()
        return listOfNotNull(
            Fire(task.id, FireKind.DUE, task.due),
            task.snoozedUntil?.let { Fire(task.id, FireKind.SNOOZE, it) },
        ).filter { it.at > task.handledUntil }
    }

    private companion object {
        val SNOOZE_LENGTH: Duration = Duration.ofMinutes(10)
        const val NEEDS_TITLE = "A task needs a title."
        const val ALREADY_PASSED = "That due time has already passed."
        const val NO_SUCH_TASK = "That task is no longer on the list."
    }
}
