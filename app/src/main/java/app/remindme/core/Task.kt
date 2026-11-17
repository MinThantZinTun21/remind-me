package app.remindme.core

import java.time.LocalDateTime

/** A thing the owner needs to do, with a due time. See GLOSSARY.md. */
data class Task(
    val id: Long = 0,
    val title: String,
    /** The due time, on the phone's own clock: 9:00 stays 9:00 when the time zone changes. */
    val due: LocalDateTime,
    /** Every fire at or before this moment has been delivered or deliberately skipped. */
    val handledUntil: LocalDateTime,
    val style: DeliveryStyle = DeliveryStyle.NOTIFICATION,
    /** When a snooze brings the occurrence back; null when none was asked for. */
    val snoozedUntil: LocalDateTime? = null,
    /** When an alarm that is ringing unanswered gives up; null when the task is not ringing. */
    val ringingUntil: LocalDateTime? = null,
    /** When the owner marked the task done; null while it is still to do. */
    val doneAt: LocalDateTime? = null,
)

/** Where a task that is still to do sits on the home screen. */
enum class Section { OVERDUE, TODAY, TOMORROW, LATER }

fun Task.section(now: LocalDateTime): Section = when {
    due <= now -> Section.OVERDUE
    due.toLocalDate() == now.toLocalDate() -> Section.TODAY
    due.toLocalDate() == now.toLocalDate().plusDays(1) -> Section.TOMORROW
    else -> Section.LATER
}

/** How a task announces itself at its due time. */
enum class DeliveryStyle { NOTIFICATION, ALARM }

/** What the owner (or, later, the agent) supplies to create or change a task. */
data class TaskDraft(
    val title: String,
    val due: LocalDateTime,
    val style: DeliveryStyle = DeliveryStyle.NOTIFICATION,
)
