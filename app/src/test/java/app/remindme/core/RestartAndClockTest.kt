package app.remindme.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RestartAndClockTest {
    @Test
    fun `after a restart every pending fire is scheduled again`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.tools.create(TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00"))).task()

        world.restartPhoneAt("2026-11-17T12:00")

        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
        assertEquals(emptyList<Announcement>(), world.announcer.announced)
    }

    @Test
    fun `a fire missed while the phone was off is announced once, late, when it starts`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00")))

        world.restartPhoneAt("2026-11-17T18:30")

        val announcement = world.announcer.announced.single()
        assertEquals("Call the bank", announcement.title)
        assertEquals(at("2026-11-17T15:00"), announcement.due)
        assertEquals(true, announcement.late)

        world.restartPhoneAt("2026-11-17T19:00")
        assertEquals(1, world.announcer.announced.size)
    }

    @Test
    fun `a fire delivered on time is not announced as late`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00")))

        world.advanceTo("2026-11-17T15:00")

        assertEquals(false, world.announcer.announced.single().late)
    }

    @Test
    fun `a missed fire is recorded in the fire log with how late it was`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00")))

        world.restartPhoneAt("2026-11-17T18:30")

        val record = world.fireLog.entries.single()
        assertEquals(at("2026-11-17T15:00"), record.scheduledFor)
        assertEquals(210, record.late.toMinutes())
        assertEquals(true, record.caughtUp)
    }

    @Test
    fun `a fire the alarm system delivers after it was already caught up is not announced twice`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00")))
        val stale = world.scheduler.pending().single()

        world.clock.local = at("2026-11-17T15:00:05")
        world.tools.reschedule()
        world.tools.fire(stale)

        assertEquals(1, world.announcer.announced.size)
    }

    @Test
    fun `a task keeps its wall-clock time when the time zone changes`() {
        val world = World(now = "2026-11-17T10:00", zone = "Asia/Yangon")
        world.tools.create(TaskDraft(title = "Take medicine", due = at("2026-11-18T09:00")))
        // 09:00 in Yangon (UTC+6:30) is 02:30 UTC.
        assertEquals(listOf(Instant.parse("2026-11-18T02:30:00Z")), world.scheduler.pendingInstants())

        world.clock.zoneId = ZoneId.of("Asia/Bangkok")
        world.tools.reschedule()

        // 09:00 in Bangkok (UTC+7) is 02:00 UTC.
        assertEquals(listOf(Instant.parse("2026-11-18T02:00:00Z")), world.scheduler.pendingInstants())
        assertEquals(at("2026-11-18T09:00"), world.tools.list().single().due)
    }
}
