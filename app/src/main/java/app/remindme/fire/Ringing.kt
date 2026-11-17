package app.remindme.fire

import androidx.compose.runtime.mutableStateListOf
import app.remindme.core.Announcement

/**
 * The alarms ringing right now, oldest first. Kept in memory only: if the process dies the ring dies with
 * it, and the task core still leaves the overdue notification when the ring would have given up.
 * Touched on the main thread only.
 */
object Ringing {
    val alarms = mutableStateListOf<Announcement>()

    fun add(alarm: Announcement) {
        remove(alarm.taskId)
        alarms += alarm
    }

    /** Returns true when the task was ringing. */
    fun remove(taskId: Long): Boolean = alarms.removeAll { it.taskId == taskId }
}
