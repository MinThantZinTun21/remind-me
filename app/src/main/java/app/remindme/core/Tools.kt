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
        if (title.isEmpty()) return ToolResult.Refused("A task needs a title.")
        if (draft.due <= now) return ToolResult.Refused("That due time has already passed.")
        val task = store.put(Task(title = title, due = draft.due, handledUntil = now))
        schedule(task)
        return ToolResult.Ok(task)
    }

    /** The tasks still to do, soonest first. */
    @Synchronized
    fun list(): List<Task> = store.all().sortedBy { it.due }

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

    private fun pendingFires(task: Task): List<Fire> =
        listOf(Fire(task.id, FireKind.DUE, task.due)).filter { it.at > task.handledUntil }
}
