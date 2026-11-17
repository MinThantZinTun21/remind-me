package app.remindme.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/** A thing the user needs to do, with a due time. See GLOSSARY.md. */
data class Task(
    val id: Long = 0,
    val title: String,
    /**
     * The due time, on the phone's own clock: 9:00 stays 9:00 when the time zone changes.
     * For a repeating task this is the occurrence the list shows: the coming one, or the latest one not yet done.
     */
    val due: LocalDateTime,
    val repeat: RepeatRule = RepeatRule.ONCE,
    /** The due time the user entered, which every later occurrence of a repeating task is worked out from. */
    val firstDue: LocalDateTime = due,
    /** Every fire at or before this moment has been delivered or deliberately skipped. */
    val handledUntil: LocalDateTime,
    val style: DeliveryStyle = DeliveryStyle.NOTIFICATION,
    /** How long before the due time each early warning comes: none, one or two, longest first. */
    val leadTimes: List<Duration> = emptyList(),
    /**
     * When a snooze brings the occurrence back; null when none was asked for. A real moment, not a time on
     * the phone's clock: ten minutes stay ten minutes when the time zone changes.
     */
    val snoozedUntil: Instant? = null,
    /** When an alarm that is ringing unanswered gives up, also a real moment; null when the task is not ringing. */
    val ringingUntil: Instant? = null,
    /** When the user marked the task done; null while it is still to do. */
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

/** What the user (or, later, the agent) supplies to create or change a task. */
data class TaskDetails(
    val title: String,
    val due: LocalDateTime,
    val style: DeliveryStyle = DeliveryStyle.NOTIFICATION,
    val leadTimes: List<Duration> = emptyList(),
    val repeat: RepeatRule = RepeatRule.ONCE,
)

/**
 * The due time a task's occurrences are worked out from once [details] are applied. While the days stay as
 * they are only the time of day moves, so a monthly task entered on the 31st is still on the 31st after its
 * time is changed on an occurrence that fell on 28 February. Moving the date or the cycle starts from the new date.
 */
fun Task.anchorFor(details: TaskDetails): LocalDateTime {
    val sameDays = details.repeat.sameDaysAs(repeat) && details.due.toLocalDate() == due.toLocalDate()
    return if (sameDays) firstDue.toLocalDate().atTime(details.due.toLocalTime()) else details.due
}

/** How long a snooze waits before telling the user again. */
val SNOOZE_LENGTH: Duration = Duration.ofMinutes(10)

/** How long a done task stays in history. */
val HISTORY_KEPT: Duration = Duration.ofDays(30)

/** True while a done task is still in history. */
fun Task.inHistory(now: LocalDateTime): Boolean = doneAt != null && doneAt > now.minus(HISTORY_KEPT)

/** The task with nothing waiting on an answer: no snooze coming back and no ring to end. */
fun Task.quiet(): Task = copy(snoozedUntil = null, ringingUntil = null)
