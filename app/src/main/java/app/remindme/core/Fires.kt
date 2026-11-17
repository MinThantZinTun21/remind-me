package app.remindme.core

import app.remindme.core.Announcement.Form
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Plans every task's fires with the phone's alarm system and announces each one when it arrives.
 * The tools change tasks; this decides what the phone says about them and when.
 */
class Fires(
    private val store: TaskStore,
    private val scheduler: FireScheduler,
    private val announcer: Announcer,
    private val fireLog: FireLog,
    private val clock: Clock,
) {
    /** A scheduled fire has arrived from the phone's alarm system. */
    @Synchronized
    fun deliver(fire: Fire) {
        val task = store.get(fire.taskId) ?: return
        // The alarm system can deliver a fire the app has already caught up on, or one from before an edit.
        if (fire !in task.pendingFires(clock.zone())) return
        // The first fire of the next occurrence is what moves a repeating task on to it.
        val startsNext = fire.kind.startsWithOccurrence && fire.at > task.due
        announce(if (startsNext) task.movedTo(task.nextDue()!!) else task, fire, caughtUp = false)
    }

    /** The alarm system says a ring has gone on for its full length. */
    @Synchronized
    fun endRing(taskId: Long) {
        val task = store.get(taskId) ?: return
        // An answer, or a newer ring, can get in just ahead of this.
        if (task.ringIsOverBy(clock.instant())) giveUp(task)
    }

    /**
     * Hands everything pending to the alarm system again. Called when the phone starts, when the clock or
     * the time zone changes, when the app is updated and when it is opened. A fire whose moment passed
     * meanwhile is announced now, once: for a repeating task, only the latest occurrence that came round.
     * It is also when tasks that have been in history long enough are removed.
     */
    @Synchronized
    fun reschedule() {
        val now = clock.now()
        val moment = clock.instant()
        val zone = clock.zone()
        store.all().filter { it.doneAt != null && !it.inHistory(now) }.forEach { store.delete(it.id) }
        for (stored in store.all()) {
            val task = stored.movedOnBy(now)
            val passed = task.pendingFires(zone).filter { task.momentOf(it, zone) <= moment }.maxByOrNull { task.momentOf(it, zone) }
            when {
                passed != null -> announce(task, passed, caughtUp = true)
                task.ringIsOverBy(moment) -> giveUp(task)
                else -> schedule(task)
            }
        }
    }

    /** Makes the alarm system hold exactly what the task still has coming. */
    @Synchronized
    fun schedule(task: Task) {
        val zone = clock.zone()
        scheduler.replace(task.id, task.pendingFires(zone).associateWith { task.momentOf(it, zone) }, task.ringingUntil)
    }

    /** Takes back everything the alarm system holds for a task. */
    @Synchronized
    fun cancel(taskId: Long) {
        scheduler.replace(taskId, emptyMap(), ringEnd = null)
    }

    private fun announce(task: Task, fire: Fire, caughtUp: Boolean) {
        val delivered = clock.instant()
        val handled = task.copy(
            // Every fire up to this one is dealt with.
            handledUntil = maxOf(task.handledUntil, fire.at),
            // The snooze that just came back is spent, and so is a ring whose time is up.
            snoozedUntil = task.snoozedUntil?.takeIf { fire.kind != FireKind.SNOOZE && it > delivered },
            ringingUntil = task.ringingUntil?.takeIf { it > delivered },
        )
        val lateness = Duration.between(task.momentOf(fire, clock.zone()), delivered)
        val onTime = lateness < ON_TIME_WITHIN
        val leadTime = task.leadTimeOf(fire.kind)
        val form = when {
            // Only the due time rings, never an early warning.
            leadTime != null -> Form.Warning(leadTime, onTime)
            // An alarm the phone slept through is told quietly at start-up; one that merely arrives a little after its moment still rings.
            task.style == DeliveryStyle.ALARM && (onTime || !caughtUp) -> Form.Ring
            onTime -> Form.DueNow
            else -> Form.WasDue
        }
        val ringing = if (form == Form.Ring) handled.copy(ringingUntil = delivered.plus(RING_LENGTH)) else handled
        fireLog.record(FireRecord(task.id, task.title, fire.kind, fire.at, delivered, lateness, caughtUp))
        announcer.announce(Announcement(task.id, task.title, task.due, form))
        schedule(store.put(ringing))
    }

    /** Nobody answered: the ring gives way to a notification that stays until the user deals with it. This is no fire of its own. */
    private fun giveUp(task: Task) {
        announcer.announce(Announcement(task.id, task.title, task.due, Form.WasDue))
        schedule(store.put(task.copy(ringingUntil = null)))
    }

    private companion object {
        /** How long an alarm rings unanswered before it gives up. */
        val RING_LENGTH: Duration = Duration.ofMinutes(10)

        /** A fire that arrives within this long of its moment is on time; after that the user is told when the task was due. */
        val ON_TIME_WITHIN: Duration = Duration.ofMinutes(1)
    }
}

/**
 * The fires still to come, at their times on a clock in [zone]. Only one occurrence of a repeating task is
 * planned at a time: the next one's fires join once the current occurrence's due time has been dealt with.
 */
internal fun Task.pendingFires(zone: ZoneId): List<Fire> {
    if (doneAt != null) return emptyList()
    val next = if (handledUntil >= due) nextDue() else null
    val planned = (occurrenceFires(due) + next?.let { occurrenceFires(it) }.orEmpty()).filter { it.at > handledUntil }
    // A snooze is a real moment the user's answer set, so the clock going back does not lose it.
    val snooze = snoozedUntil?.let { Fire(id, FireKind.SNOOZE, LocalDateTime.ofInstant(it, zone)) }
    return planned + listOfNotNull(snooze)
}

/**
 * The real moment [fire] is to arrive: a snooze at the moment the user's answer set, every other fire at
 * its time on a clock in [zone].
 */
private fun Task.momentOf(fire: Fire, zone: ZoneId): Instant =
    snoozedUntil?.takeIf { fire.kind == FireKind.SNOOZE } ?: fire.at.atZone(zone).toInstant()

/** True when the task is ringing and the ring's full length is up by [moment]. */
private fun Task.ringIsOverBy(moment: Instant): Boolean = ringingUntil?.let { it <= moment } ?: false

/** The warnings and the due-time fire of the occurrence due at [due]. */
private fun Task.occurrenceFires(due: LocalDateTime): List<Fire> =
    FireKind.entries.mapNotNull { kind -> leadTimeOf(kind)?.let { Fire(id, kind, due.minus(it)) } } + Fire(id, FireKind.DUE, due)

/** The lead time an early warning belongs to; null for every other kind of fire. */
private fun Task.leadTimeOf(kind: FireKind): Duration? = kind.leadTimeIndex?.let(leadTimes::getOrNull)

/** The due time of the occurrence after the current one; null when the task has no more. */
internal fun Task.nextDue(): LocalDateTime? = repeat.occurrenceAfter(firstDue, due)

/** The next occurrence replaces the one before it: an unanswered occurrence and its snooze are dropped. */
internal fun Task.movedTo(next: LocalDateTime): Task = quiet().copy(due = next)

/** The task moved on past every occurrence that began (with a warning or its due time) by [now] while nothing was delivered. */
private fun Task.movedOnBy(now: LocalDateTime): Task {
    if (doneAt != null) return this
    var current = this
    while (true) {
        val next = current.nextDue() ?: return current
        val begun = current.occurrenceFires(next).any { it.at > current.due && it.at > current.handledUntil && it.at <= now }
        if (!begun) return current
        current = current.movedTo(next)
    }
}
