package app.remindme.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

interface Clock {
    fun instant(): Instant
    fun zone(): ZoneId
}

fun Clock.now(): LocalDateTime = LocalDateTime.ofInstant(instant(), zone())

interface TaskStore {
    fun all(): List<Task>
    fun get(id: Long): Task?

    /** Saves the task, giving it an id when it has none (id 0), and returns the saved task. */
    fun put(task: Task): Task
    fun delete(id: Long)
}

enum class FireKind { DUE }

/** One announcement the phone is to make for a task, at a moment on the phone's own clock. */
data class Fire(val taskId: Long, val kind: FireKind, val at: LocalDateTime)

interface FireScheduler {
    /** Makes [fires] the complete set of scheduled fires for the task, cancelling any others it had. */
    fun replace(taskId: Long, fires: List<Fire>)
}

/** What the owner is told when a fire is delivered. */
data class Announcement(val taskId: Long, val kind: FireKind, val title: String, val due: LocalDateTime)

interface Announcer {
    fun announce(announcement: Announcement)
}

/** One line of the Fire log: what fired, when it was meant to, and how late it arrived. */
data class FireRecord(
    val taskId: Long,
    val title: String,
    val kind: FireKind,
    val scheduledFor: LocalDateTime,
    val deliveredAt: Instant,
    val late: Duration,
)

interface FireLog {
    fun record(record: FireRecord)
}
