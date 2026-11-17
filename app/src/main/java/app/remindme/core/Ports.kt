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

enum class FireKind {
    /** The early warning of the task's longer (or only) lead time. */
    WARNING_1,

    /** The early warning of the shorter of two lead times. */
    WARNING_2,

    /** The notification or alarm at the due time. */
    DUE,

    /** A snooze coming back. */
    SNOOZE,

    /** Not an announcement of its own: the moment an alarm left ringing gives up and leaves an overdue notification. */
    RING_END,
}

/** One announcement the phone is to make for a task, at a moment on the phone's own clock. */
data class Fire(val taskId: Long, val kind: FireKind, val at: LocalDateTime)

interface FireScheduler {
    /**
     * Makes [fires] the complete set of scheduled fires for the task, cancelling any others it had.
     * Each fire's wall-clock time is to be read in [zone].
     */
    fun replace(taskId: Long, fires: List<Fire>, zone: ZoneId)
}

/** What the owner is told when a fire is delivered. */
data class Announcement(
    val taskId: Long,
    val kind: FireKind,
    val title: String,
    val due: LocalDateTime,
    /** True when the owner is being told after the moment has passed, so the wording says when it was due. */
    val late: Boolean,
    /** True for a full-screen ring; false for a notification. */
    val ring: Boolean,
    /** For an early warning, how long before the due time it was meant to come; null for every other fire. */
    val leadTime: Duration? = null,
)

interface Announcer {
    /** Tells the owner. This replaces whatever the task was showing before: a notification ends its ring. */
    fun announce(announcement: Announcement)

    /** Takes away whatever is still showing for the task and silences it if it is ringing. */
    fun clear(taskId: Long)
}

/** One line of the Fire log: what fired, when it was meant to, and how late it arrived. */
data class FireRecord(
    val taskId: Long,
    val title: String,
    val kind: FireKind,
    val scheduledFor: LocalDateTime,
    val deliveredAt: Instant,
    val late: Duration,
    /** True when the app found the fire past due at rescheduling, instead of the alarm system delivering it. */
    val caughtUp: Boolean,
)

interface FireLog {
    fun record(record: FireRecord)
}
