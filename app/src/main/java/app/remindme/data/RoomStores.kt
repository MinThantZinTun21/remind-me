package app.remindme.data

import app.remindme.core.DeliveryStyle
import app.remindme.core.FireKind
import app.remindme.core.FireLog
import app.remindme.core.FireRecord
import app.remindme.core.RepeatRule
import app.remindme.core.Task
import app.remindme.core.TaskStore
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class RoomTaskStore(private val dao: TaskDao, private val onChange: () -> Unit) : TaskStore {
    override fun all(): List<Task> = dao.all().map { it.toTask() }

    override fun get(id: Long): Task? = dao.get(id)?.toTask()

    override fun put(task: Task): Task {
        val saved = task.copy(id = dao.save(task.toRow()))
        onChange()
        return saved
    }

    override fun delete(id: Long) {
        dao.delete(id)
        onChange()
    }

    private fun TaskRow.toTask() = Task(
        id = id,
        title = title,
        due = LocalDateTime.parse(due),
        repeat = RepeatRule(
            cycle = RepeatRule.Cycle.valueOf(repeatCycle),
            every = repeatEvery,
            weekdays = repeatWeekdays.listed().map { DayOfWeek.of(it.toInt()) }.toSet(),
            until = repeatUntil?.let(LocalDate::parse),
        ),
        firstDue = LocalDateTime.parse(firstDue),
        handledUntil = LocalDateTime.parse(handledUntil),
        style = DeliveryStyle.valueOf(style),
        leadTimes = leadTimes.listed().map { Duration.ofMinutes(it.toLong()) },
        snoozedUntil = snoozedUntil?.let(Instant::parse),
        ringingUntil = ringingUntil?.let(Instant::parse),
        doneAt = doneAt?.let(LocalDateTime::parse),
    )

    /** The items of a comma-separated column; none when it is empty. */
    private fun String.listed(): List<String> = split(',').filter { it.isNotEmpty() }

    private fun Task.toRow() = TaskRow(
        id = id,
        title = title,
        due = due.toString(),
        repeatCycle = repeat.cycle.name,
        repeatEvery = repeat.every,
        repeatWeekdays = repeat.weekdays.sorted().joinToString(",") { it.value.toString() },
        repeatUntil = repeat.until?.toString(),
        firstDue = firstDue.toString(),
        handledUntil = handledUntil.toString(),
        style = style.name,
        leadTimes = leadTimes.joinToString(",") { it.toMinutes().toString() },
        snoozedUntil = snoozedUntil?.toString(),
        ringingUntil = ringingUntil?.toString(),
        doneAt = doneAt?.toString(),
    )
}

class RoomFireLog(private val dao: FireLogDao, private val onChange: () -> Unit) : FireLog {
    override fun record(record: FireRecord) {
        dao.insert(
            FireLogRow(
                id = 0,
                taskId = record.taskId,
                title = record.title,
                kind = record.kind.name,
                scheduledFor = record.scheduledFor.toString(),
                deliveredAtMillis = record.deliveredAt.toEpochMilli(),
                latenessMillis = record.lateness.toMillis(),
                caughtUp = record.caughtUp,
            ),
        )
        dao.trim(KEPT_RECORDS)
        onChange()
    }

    fun latest(): List<FireRecord> = dao.latest(KEPT_RECORDS).map {
        FireRecord(
            taskId = it.taskId,
            title = it.title,
            kind = FireKind.valueOf(it.kind),
            scheduledFor = LocalDateTime.parse(it.scheduledFor),
            deliveredAt = Instant.ofEpochMilli(it.deliveredAtMillis),
            lateness = Duration.ofMillis(it.latenessMillis),
            caughtUp = it.caughtUp,
        )
    }

    private companion object {
        const val KEPT_RECORDS = 500
    }
}
