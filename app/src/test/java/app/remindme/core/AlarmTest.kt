package app.remindme.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTest {
    private fun World.alarm(title: String, due: String): Task =
        tools.create(TaskDraft(title = title, due = at(due), style = DeliveryStyle.ALARM)).task()

    @Test
    fun `a task set to alarm rings at its due time and a notification task does not`() {
        val world = World(now = "2026-11-17T10:00")
        val alarm = world.alarm("Take the pills", "2026-11-17T15:00")
        world.tools.create(TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00")))

        world.advanceTo("2026-11-17T15:00")

        assertEquals(setOf(alarm.id), world.announcer.ringing)
        assertEquals(listOf(true, false), world.announcer.announced.sortedBy { it.taskId }.map { it.ring })
        assertEquals(DeliveryStyle.ALARM, world.tools.list().first { it.id == alarm.id }.style)
    }

    @Test
    fun `done silences the ring and marks the occurrence done`() {
        val world = World(now = "2026-11-17T10:00")
        val alarm = world.alarm("Take the pills", "2026-11-17T15:00")
        world.advanceTo("2026-11-17T15:01")

        world.tools.done(alarm.id)

        assertEquals(emptySet<Long>(), world.announcer.ringing)
        assertEquals(emptyList<Task>(), world.tools.list())
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `snooze silences the ring and rings again ten minutes later`() {
        val world = World(now = "2026-11-17T10:00")
        val alarm = world.alarm("Take the pills", "2026-11-17T15:00")
        world.advanceTo("2026-11-17T15:01")

        world.tools.snooze(alarm.id)
        assertEquals(emptySet<Long>(), world.announcer.ringing)
        assertEquals(listOf(Fire(alarm.id, FireKind.SNOOZE, at("2026-11-17T15:11"))), world.scheduler.pending())

        world.advanceTo("2026-11-17T15:11")
        assertEquals(setOf(alarm.id), world.announcer.ringing)
    }

    @Test
    fun `stop silences the ring and leaves the task overdue with nothing more to come`() {
        val world = World(now = "2026-11-17T10:00")
        val alarm = world.alarm("Take the pills", "2026-11-17T15:00")
        world.advanceTo("2026-11-17T15:01")

        world.tools.stop(alarm.id)

        assertEquals(emptySet<Long>(), world.announcer.ringing)
        assertEquals(Section.OVERDUE, world.tools.list().single().section(world.clock.now()))
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
        world.advanceTo("2026-11-17T16:00")
        assertEquals(1, world.announcer.announced.size)
    }

    @Test
    fun `an unanswered ring stops after ten minutes and leaves an overdue notification`() {
        val world = World(now = "2026-11-17T10:00")
        val alarm = world.alarm("Take the pills", "2026-11-17T15:00")

        world.advanceTo("2026-11-17T15:09")
        assertEquals(setOf(alarm.id), world.announcer.ringing)

        world.advanceTo("2026-11-17T15:10")
        assertEquals(emptySet<Long>(), world.announcer.ringing)
        val left = world.announcer.announced.last()
        assertFalse(left.ring)
        assertTrue(left.late)
        assertEquals(at("2026-11-17T15:00"), left.due)
        assertEquals(Section.OVERDUE, world.tools.list().single().section(world.clock.now()))
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `the ring giving up is not written to the fire log`() {
        val world = World(now = "2026-11-17T10:00")
        world.alarm("Take the pills", "2026-11-17T15:00")

        world.advanceTo("2026-11-17T15:30")

        assertEquals(listOf(FireKind.DUE), world.fireLog.entries.map { it.kind })
    }

    @Test
    fun `a second alarm joins the first, and each is answered on its own`() {
        val world = World(now = "2026-11-17T10:00")
        val pills = world.alarm("Take the pills", "2026-11-17T15:00")
        val bus = world.alarm("Catch the bus", "2026-11-17T15:05")

        world.advanceTo("2026-11-17T15:06")
        assertEquals(setOf(pills.id, bus.id), world.announcer.ringing)

        world.tools.done(pills.id)
        assertEquals(setOf(bus.id), world.announcer.ringing)

        world.tools.stop(bus.id)
        assertEquals(emptySet<Long>(), world.announcer.ringing)
    }

    @Test
    fun `with two alarms ringing each gives up ten minutes after its own start`() {
        val world = World(now = "2026-11-17T10:00")
        val pills = world.alarm("Take the pills", "2026-11-17T15:00")
        val bus = world.alarm("Catch the bus", "2026-11-17T15:05")

        world.advanceTo("2026-11-17T15:12")
        assertEquals(setOf(bus.id), world.announcer.ringing)

        world.advanceTo("2026-11-17T15:15")
        assertEquals(emptySet<Long>(), world.announcer.ringing)
        assertEquals(setOf(pills.id, bus.id), world.tools.list().map { it.id }.toSet())
    }

    @Test
    fun `an alarm missed while the phone was off is told late as a notification, not rung`() {
        val world = World(now = "2026-11-17T10:00")
        world.alarm("Take the pills", "2026-11-17T15:00")

        world.restartPhoneAt("2026-11-17T18:00")

        val told = world.announcer.announced.single()
        assertFalse(told.ring)
        assertTrue(told.late)
        assertEquals(emptySet<Long>(), world.announcer.ringing)
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `an alarm found due a few seconds ago when the app reschedules still rings`() {
        val world = World(now = "2026-11-17T10:00")
        val alarm = world.alarm("Take the pills", "2026-11-17T15:00")

        world.clock.local = at("2026-11-17T15:00:05")
        world.tools.reschedule()

        assertEquals(setOf(alarm.id), world.announcer.ringing)
        assertFalse(world.announcer.announced.single().late)
    }

    @Test
    fun `a phone that restarts while an alarm rings leaves the overdue notification`() {
        val world = World(now = "2026-11-17T10:00")
        world.alarm("Take the pills", "2026-11-17T15:00")
        world.advanceTo("2026-11-17T15:02")

        world.restartPhoneAt("2026-11-17T15:30")

        val left = world.announcer.announced.last()
        assertFalse(left.ring)
        assertTrue(left.late)
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `changing a ringing alarm's due time silences it`() {
        val world = World(now = "2026-11-17T10:00")
        val alarm = world.alarm("Take the pills", "2026-11-17T15:00")
        world.advanceTo("2026-11-17T15:01")

        world.tools.update(alarm.id, TaskDraft(title = "Take the pills", due = at("2026-11-17T20:00"), style = DeliveryStyle.ALARM))

        assertEquals(emptySet<Long>(), world.announcer.ringing)
        assertEquals(listOf(Fire(alarm.id, FireKind.DUE, at("2026-11-17T20:00"))), world.scheduler.pending())
    }

    @Test
    fun `a task can be changed from notification to alarm`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.tools.create(TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00"))).task()

        world.tools.update(task.id, TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00"), style = DeliveryStyle.ALARM))
        world.advanceTo("2026-11-17T15:00")

        assertEquals(setOf(task.id), world.announcer.ringing)
    }
}
