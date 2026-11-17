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
)

/** What the owner (or, later, the agent) supplies to create or change a task. */
data class TaskDraft(
    val title: String,
    val due: LocalDateTime,
)
