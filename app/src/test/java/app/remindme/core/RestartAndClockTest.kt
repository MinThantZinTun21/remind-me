package app.remindme.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RestartAndClockTest {
    @Test
    fun `after a restart every pending fire is scheduled again`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"))).task()

        world.restartPhoneAt("2026-11-17T12:00")

        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
        assertEquals(emptyList<Announcement>(), world.announcer.announced)
    }

    @Test
    fun `a fire whose moment passed while the phone was off is announced once, saying when the task was due, when the phone starts`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00")))

        world.restartPhoneAt("2026-11-17T18:30")

        val announcement = world.announcer.announced.single()
        assertEquals("Call the bank", announcement.title)
        assertEquals(at("2026-11-17T15:00"), announcement.due)
        assertEquals(true, announcement.saysWasDue)

        world.restartPhoneAt("2026-11-17T19:00")
        assertEquals(1, world.announcer.announced.size)
    }

    @Test
    fun `a fire delivered on time does not say when the task was due`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00")))

        world.advanceTo("2026-11-17T15:00")

        assertEquals(false, world.announcer.announced.single().saysWasDue)
    }

    @Test
    fun `a fire caught up at start-up is recorded in the fire log with how long after its moment it came`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00")))

        world.restartPhoneAt("2026-11-17T18:30")

        val record = world.fireLog.entries.single()
        assertEquals(at("2026-11-17T15:00"), record.scheduledFor)
        assertEquals(210, record.lateness.toMinutes())
        assertEquals(true, record.caughtUp)
    }

    @Test
    fun `a fire the alarm system delivers after it was already caught up is not announced twice`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00")))
        val stale = world.scheduler.pending().single()

        world.clock.local = at("2026-11-17T15:00:05")
        world.fires.reschedule()
        world.fires.deliver(stale)

        assertEquals(1, world.announcer.announced.size)
    }

    @Test
    fun `a task keeps its wall-clock time when the time zone changes`() {
        val world = World(now = "2026-11-17T10:00", zone = "Asia/Yangon")
        world.tools.create(TaskDetails(title = "Take medicine", due = at("2026-11-18T09:00")))
        // 09:00 in Yangon (UTC+6:30) is 02:30 UTC.
        assertEquals(listOf(Instant.parse("2026-11-18T02:30:00Z")), world.scheduler.pendingInstants())

        world.clock.zoneId = ZoneId.of("Asia/Bangkok")
        world.fires.reschedule()

        // 09:00 in Bangkok (UTC+7) is 02:00 UTC.
        assertEquals(listOf(Instant.parse("2026-11-18T02:00:00Z")), world.scheduler.pendingInstants())
        assertEquals(at("2026-11-18T09:00"), world.tools.list().single().due)
    }

    /** A task due at 15:00 in Yangon, announced, then snoozed at 15:05: the snooze is for 15:15 Yangon, 08:45 UTC. */
    private fun worldWithSnoozeUntil0845Utc(): World {
        val world = World(now = "2026-11-17T10:00", zone = "Asia/Yangon")
        val task = world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"))).task()
        world.advanceTo("2026-11-17T15:05")
        world.tools.snooze(task.id)
        assertEquals(listOf(Instant.parse("2026-11-17T08:45:00Z")), world.scheduler.pendingInstants())
        return world
    }

    /** The same moment, read on the clock of another time zone. */
    private fun World.travelTo(zone: String, localTimeThere: String) {
        val before = clock.instant()
        clock.zoneId = ZoneId.of(zone)
        clock.local = at(localTimeThere)
        assertEquals(before, clock.instant())
        fires.reschedule()
    }

    @Test
    fun `a snooze still comes back ten minutes on when the time zone moves west`() {
        val world = worldWithSnoozeUntil0845Utc()

        // Kolkata (UTC+5:30) is an hour behind Yangon.
        world.travelTo("Asia/Kolkata", localTimeThere = "2026-11-17T14:05")

        assertEquals(listOf(Instant.parse("2026-11-17T08:45:00Z")), world.scheduler.pendingInstants())
        world.advanceTo("2026-11-17T14:15")
        assertEquals(listOf(FireKind.DUE, FireKind.SNOOZE), world.fired)
        assertEquals(Announcement.Form.DueNow, world.announcer.announced.last().form)
    }

    @Test
    fun `a snooze does not come back early when the time zone moves east`() {
        val world = worldWithSnoozeUntil0845Utc()

        // Bangkok (UTC+7) is half an hour ahead of Yangon.
        world.travelTo("Asia/Bangkok", localTimeThere = "2026-11-17T15:35")

        assertEquals(listOf(FireKind.DUE), world.fired)
        assertEquals(listOf(Instant.parse("2026-11-17T08:45:00Z")), world.scheduler.pendingInstants())
    }

    @Test
    fun `a snooze asked for after the clock went back past the due time still comes back`() {
        val world = World(now = "2026-11-17T10:00", zone = "Asia/Yangon")
        val task = world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"))).task()
        world.advanceTo("2026-11-17T15:05")
        // Tehran (UTC+3:30) is three hours behind Yangon: the phone now reads 12:05, before the due time it already announced.
        world.travelTo("Asia/Tehran", localTimeThere = "2026-11-17T12:05")

        world.tools.snooze(task.id)
        world.advanceTo("2026-11-17T12:15")

        assertEquals(listOf(FireKind.DUE, FireKind.SNOOZE), world.fired)
    }

    @Test
    fun `an unanswered alarm still gives up after ten minutes when the time zone moves`() {
        val world = World(now = "2026-11-17T10:00", zone = "Asia/Yangon")
        val task = world.tools.create(
            TaskDetails(title = "Take the pills", due = at("2026-11-17T15:00"), style = DeliveryStyle.ALARM),
        ).task()
        world.advanceTo("2026-11-17T15:01")
        assertEquals(setOf(task.id), world.announcer.ringing)

        world.travelTo("Asia/Kolkata", localTimeThere = "2026-11-17T14:01")

        // Ten minutes after 15:00 in Yangon is 08:40 UTC.
        assertEquals(listOf(Instant.parse("2026-11-17T08:40:00Z")), world.scheduler.pendingInstants())
        world.advanceTo("2026-11-17T14:10")
        assertEquals(emptySet<Long>(), world.announcer.ringing)
    }

    @Test
    fun `a snooze asked for just before the clock repeats an hour comes back ten minutes on, not an hour early`() {
        // New York's clocks go back at 02:00 on 1 November 2026, so 01:00 to 02:00 happens twice.
        val world = World(now = "2026-10-31T10:00", zone = "America/New_York")
        val task = world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-01T01:50"))).task()
        // 01:55 the first time round is 05:55 UTC.
        world.advanceTo("2026-11-01T01:55")

        world.tools.snooze(task.id)

        // Ten minutes on the clock reads 01:05 for the second time: 06:05 UTC, not 05:05.
        assertEquals(listOf(Instant.parse("2026-11-01T06:05:00Z")), world.scheduler.pendingInstants())
    }

    @Test
    fun `an alarm that starts ringing just before the clock repeats an hour still rings for ten minutes`() {
        val world = World(now = "2026-10-31T10:00", zone = "America/New_York")
        world.tools.create(TaskDetails(title = "Take the pills", due = at("2026-11-01T01:55"), style = DeliveryStyle.ALARM))

        world.advanceTo("2026-11-01T01:55")

        assertEquals(listOf(Instant.parse("2026-11-01T06:05:00Z")), world.scheduler.pendingInstants())
    }
}
