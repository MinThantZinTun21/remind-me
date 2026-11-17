package app.remindme.core

import java.time.DayOfWeek
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LeadTimeTest {
    private val oneHour = Duration.ofHours(1)
    private val fifteenMinutes = Duration.ofMinutes(15)

    private fun World.bankTask(vararg leadTimes: Duration, style: DeliveryStyle = DeliveryStyle.NOTIFICATION): Task =
        tools.create(
            TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"), style = style, leadTimes = leadTimes.toList()),
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
        assertFalse(world.announcer.announced.single().rings)
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
            TaskDetails(
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
            TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"), leadTimes = listOf(oneHour, oneHour)),
        )

        assertEquals(ToolResult.Refused("The two lead times are the same."), result)
    }

    @Test
    fun `a lead time has to be at least a minute`() {
        val world = World(now = "2026-11-17T10:00")

        val result = world.tools.create(
            TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"), leadTimes = listOf(Duration.ZERO)),
        )

        assertEquals(ToolResult.Refused("A lead time is a whole number of minutes, at least one."), result)
    }

    @Test
    fun `editing the due time moves the warnings with it`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour)

        world.tools.update(
            task.id,
            TaskDetails(title = "Call the bank", due = at("2026-11-17T18:00"), leadTimes = listOf(oneHour)),
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
            TaskDetails(
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
    fun `warnings that came due while the phone was off give way to the due-time fire`() {
        val world = World(now = "2026-11-17T10:00")
        world.bankTask(oneHour, fifteenMinutes)

        world.restartPhoneAt("2026-11-17T15:30")

        val told = world.announcer.announced.single()
        assertEquals(FireKind.DUE, told.kind)
        assertEquals(true, told.saysWasDue)
    }

    @Test
    fun `a warning that came due while the phone was off is given at start-up when the due time is still ahead`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.bankTask(oneHour)

        world.restartPhoneAt("2026-11-17T14:20")

        assertEquals(listOf(oneHour), world.announcer.announced.map { it.leadTime })
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
    }

    @Test
    fun `a lead time that is not a whole number of minutes is refused`() {
        val world = World(now = "2026-11-17T10:00")

        val result = world.tools.create(
            TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"), leadTimes = listOf(Duration.ofSeconds(90))),
        )

        assertEquals(ToolResult.Refused("A lead time is a whole number of minutes, at least one."), result)
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `a lead time as long as the time between occurrences is refused`() {
        val world = World(now = "2026-11-16T10:00")
        val tooLong = ToolResult.Refused("A lead time has to be shorter than the time between occurrences.")
        fun create(repeat: RepeatRule, leadTime: Duration) = world.tools.create(
            TaskDetails(title = "Take the pills", due = at("2026-11-17T09:00"), repeat = repeat, leadTimes = listOf(leadTime)),
        )
        val mondayAndThursday = RepeatRule.weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        assertEquals(tooLong, create(RepeatRule.daily(), Duration.ofDays(1)))
        assertEquals(tooLong, create(RepeatRule.everyDays(2), Duration.ofDays(3)))
        assertEquals(tooLong, create(mondayAndThursday, Duration.ofDays(3)))
        assertEquals(tooLong, create(RepeatRule.everyWeeks(1), Duration.ofDays(7)))
        assertEquals(tooLong, create(RepeatRule.monthly(), Duration.ofDays(28)))
        assertEquals(tooLong, create(RepeatRule.yearly(), Duration.ofDays(365)))
        assertEquals(emptyList<Task>(), world.tools.list())

        create(RepeatRule.daily(), Duration.ofHours(23)).task()
        create(mondayAndThursday, Duration.ofDays(2)).task()
        create(RepeatRule.ONCE, Duration.ofDays(40)).task()
    }

    @Test
    fun `a lead time just short of the time between occurrences warns before every occurrence`() {
        val world = World(now = "2026-11-16T09:00")
        world.tools.create(
            TaskDetails(
                title = "Take the pills",
                due = at("2026-11-17T09:00"),
                repeat = RepeatRule.daily(),
                leadTimes = listOf(Duration.ofHours(23)),
            ),
        )

        world.advanceTo("2026-11-19T09:00")

        assertEquals(
            listOf(
                FireKind.WARNING_1 to at("2026-11-17T09:00"),
                FireKind.DUE to at("2026-11-17T09:00"),
                FireKind.WARNING_1 to at("2026-11-18T09:00"),
                FireKind.DUE to at("2026-11-18T09:00"),
                FireKind.WARNING_1 to at("2026-11-19T09:00"),
                FireKind.DUE to at("2026-11-19T09:00"),
            ),
            world.announcer.announced.map { it.kind to it.due },
        )
    }

    @Test
    fun `a warning given after its moment no longer says how long is left`() {
        val world = World(now = "2026-11-17T10:00")
        world.bankTask(oneHour)

        world.restartPhoneAt("2026-11-17T14:20")

        assertEquals(Announcement.Form.Warning(oneHour, onTime = false), world.announcer.announced.single().form)
    }
}
