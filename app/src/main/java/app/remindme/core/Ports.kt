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

enum class FireKind(
    /** For an early warning, which of the task's lead times it belongs to; null for every other fire. */
    val leadTimeIndex: Int? = null,
) {
    /** The early warning of the task's longer (or only) lead time. */
    WARNING_1(leadTimeIndex = 0),

    /** The early warning of the shorter of two lead times. */
    WARNING_2(leadTimeIndex = 1),

    /** The notification or alarm at the due time. */
    DUE,

    /** A snooze coming back. */
    SNOOZE,
    ;

    /** True for the fires an occurrence has in its own right, its warnings and its due time, not for a snooze of them. */
    val startsWithOccurrence: Boolean get() = this != SNOOZE
}

/** One announcement the phone is to make for a task, at a time on the phone's own clock. */
data class Fire(val taskId: Long, val kind: FireKind, val at: LocalDateTime)

interface FireScheduler {
    /**
     * Makes [fires] everything the alarm system holds for the task, each fire with the real moment it is to
     * arrive, cancelling any others it had. [ringEnd] is the moment the task's ring is to give up; null
     * when the task is not ringing.
     */
    fun replace(taskId: Long, fires: Map<Fire, Instant>, ringEnd: Instant?)
}

/** What the user is told when a fire is delivered, or when a ring nobody answered gives up. */
data class Announcement(
    val taskId: Long,
    val title: String,
    val due: LocalDateTime,
    val form: Form,
) {
    /** The shape the telling takes, which decides its wording and its buttons. */
    sealed interface Form {
        /**
         * An early warning, always a notification. Given at its moment ([onTime]) it leads with how long is
         * left; given after it, it only says when the task is due.
         */
        data class Warning(val leadTime: Duration, val onTime: Boolean) : Form

        /** A notification at the due time, or of a snooze coming back. */
        data object DueNow : Form

        /** A notification given after its moment has passed, or left by a ring nobody answered, so it says when the task was due. */
        data object WasDue : Form

        /** A full-screen ring. */
        data object Ring : Form
    }
}

interface Announcer {
    /** Tells the user. This replaces whatever the task was showing before: a notification ends its ring. */
    fun announce(announcement: Announcement)

    /** Takes away whatever is still showing for the task and silences it if it is ringing. */
    fun clear(taskId: Long)
}

/** One line of the Fire log: what fired, when it was meant to, and how long after that it arrived. */
data class FireRecord(
    val taskId: Long,
    val title: String,
    val kind: FireKind,
    val scheduledFor: LocalDateTime,
    val deliveredAt: Instant,
    val lateness: Duration,
    /** True when the app found the fire's moment already passed at rescheduling, instead of the alarm system delivering it. */
    val caughtUp: Boolean,
)

interface FireLog {
    fun record(record: FireRecord)
}
