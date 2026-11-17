package app.remindme.fire

import androidx.compose.runtime.mutableStateListOf
import java.time.LocalDateTime

data class RingingTask(val taskId: Long, val title: String, val due: LocalDateTime)

/**
 * The alarms ringing right now, oldest first. Kept in memory only: if the process dies the ring dies with
 * it, and the task core still leaves the overdue notification when the ring would have given up.
 * Touched on the main thread only.
 */
object Ringing {
    val tasks = mutableStateListOf<RingingTask>()

    fun add(task: RingingTask) {
        remove(task.taskId)
        tasks += task
    }

    /** Returns true when the task was ringing. */
    fun remove(taskId: Long): Boolean = tasks.removeAll { it.taskId == taskId }
}
