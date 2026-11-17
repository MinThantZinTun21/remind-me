package app.remindme.core

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LeadTimeTest {
    private val oneHour = Duration.ofHours(1)
    private val fifteenMinutes = Duration.ofMinutes(15)

    private fun World.bankTask(vararg leadTimes: Duration, style: DeliveryStyle = DeliveryStyle.NOTIFICATION): Task =
        tools.create(
            TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00"), style = style, leadTimes = leadTimes.toList()),
        ).task()

    @Test
    fun `each lead time warns that long before the due time, and the due time still announces`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour, fifteenMinutes)

        assertEquals(
            listOf(
                Fire(task.id, FireKind.WARNING_1, at("2026-11-17T14:00")),
                Fire(task.id, FireKind.WARNING_2, at("2026-11-17T14:45")),
                Fire(task.id, FireKind.DUE, at("2026-11-17T15:00")),
            ),
            world.scheduler.pending(),
        )
        world.advanceTo("2026-11-17T15:00")

        assertEquals(
            listOf(FireKind.WARNING_1 to oneHour, FireKind.WARNING_2 to fifteenMinutes, FireKind.DUE to null),
            world.announcer.announced.map { it.kind to it.leadTime },
        )
        assertEquals(at("2026-11-17T15:00"), world.announcer.announced.first().due)
    }

    @Test
    fun `a task keeps its lead times longest first`() {
        val world = World(now = "2026-11-17T10:00")

        val task = world.bankTask(fifteenMinutes, oneHour)

        assertEquals(listOf(oneHour, fifteenMinutes), task.leadTimes)
    }

    @Test
    fun `a warning on an alarm task is a notification and only the due time rings`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour, style = DeliveryStyle.ALARM)

        world.advanceTo("2026-11-17T14:00")
        assertFalse(world.announcer.announced.single().ring)
        assertEquals(emptySet<Long>(), world.announcer.ringing)

        world.advanceTo("2026-11-17T15:00")
        assertEquals(setOf(task.id), world.announcer.ringing)
    }

    @Test
    fun `a warning whose moment has already passed when the task is saved is skipped`() {
        val world = World(now = "2026-11-17T14:30")

        val task = world.bankTask(oneHour, fifteenMinutes)

        assertEquals(
            listOf(Fire(task.id, FireKind.WARNING_2, at("2026-11-17T14:45")), Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))),
            world.scheduler.pending(),
        )
        world.advanceTo("2026-11-17T15:00")
        assertEquals(listOf(fifteenMinutes, null), world.announcer.announced.map { it.leadTime })
    }

    @Test
    fun `done on a warning cancels the later warning and the due-time fire`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour, fifteenMinutes)
        world.advanceTo("2026-11-17T14:05")

        world.tools.done(task.id)
        world.advanceTo("2026-11-17T16:00")

        assertEquals(1, world.announcer.announced.size)
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
        assertEquals(emptyList<Task>(), world.tools.list())
    }

    @Test
    fun `swiping a warning away leaves the later fires in place`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour, fifteenMinutes)
        world.advanceTo("2026-11-17T14:05")

        world.tools.stop(task.id)

        assertEquals(
            listOf(Fire(task.id, FireKind.WARNING_2, at("2026-11-17T14:45")), Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))),
            world.scheduler.pending(),
        )
        world.advanceTo("2026-11-17T15:00")
        assertEquals(3, world.announcer.announced.size)
    }

    @Test
    fun `a third lead time is refused`() {
        val world = World(now = "2026-11-17T10:00")

        val result = world.tools.create(
            TaskDraft(
                title = "Call the bank",
                due = at("2026-11-17T15:00"),
                leadTimes = listOf(oneHour, fifteenMinutes, Duration.ofMinutes(5)),
            ),
        )

        assertEquals(ToolResult.Refused("A task can have at most two lead times."), result)
    }

    @Test
    fun `two identical lead times are refused`() {
        val world = World(now = "2026-11-17T10:00")

        val result = world.tools.create(
            TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00"), leadTimes = listOf(oneHour, oneHour)),
        )

        assertEquals(ToolResult.Refused("The two lead times are the same."), result)
    }

    @Test
    fun `a lead time has to be at least a minute`() {
        val world = World(now = "2026-11-17T10:00")

        val result = world.tools.create(
            TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00"), leadTimes = listOf(Duration.ZERO)),
        )

        assertEquals(ToolResult.Refused("A lead time has to be at least one minute."), result)
    }

    @Test
    fun `editing the due time moves the warnings with it`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour)

        world.tools.update(
            task.id,
            TaskDraft(title = "Call the bank", due = at("2026-11-17T18:00"), leadTimes = listOf(oneHour)),
        )

        assertEquals(
            listOf(Fire(task.id, FireKind.WARNING_1, at("2026-11-17T17:00")), Fire(task.id, FireKind.DUE, at("2026-11-17T18:00"))),
            world.scheduler.pending(),
        )
        world.advanceTo("2026-11-17T16:00")
        assertEquals(emptyList<Announcement>(), world.announcer.announced)
    }

    @Test
    fun `editing the lead times reschedules the warnings without repeating one already given`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour)
        world.advanceTo("2026-11-17T14:10")

        world.tools.update(
            task.id,
            TaskDraft(
                title = "Call the bank",
                due = at("2026-11-17T15:00"),
                leadTimes = listOf(Duration.ofHours(2), fifteenMinutes),
            ),
        )

        assertEquals(
            listOf(Fire(task.id, FireKind.WARNING_2, at("2026-11-17T14:45")), Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))),
            world.scheduler.pending(),
        )
        world.advanceTo("2026-11-17T15:00")
        assertEquals(listOf(oneHour, fifteenMinutes, null), world.announcer.announced.map { it.leadTime })
    }

    @Test
    fun `warnings missed while the phone was off give way to the due-time fire`() {
        val world = World(now = "2026-11-17T10:00")
        world.bankTask(oneHour, fifteenMinutes)

        world.restartPhoneAt("2026-11-17T15:30")

        val told = world.announcer.announced.single()
        assertEquals(FireKind.DUE, told.kind)
        assertEquals(true, told.late)
    }

    @Test
    fun `a warning missed while the phone was off is given late when the due time is still ahead`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour)

        world.restartPhoneAt("2026-11-17T14:20")

        assertEquals(listOf(oneHour), world.announcer.announced.map { it.leadTime })
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
    }
}
