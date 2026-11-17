package app.remindme.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 17 November 2026, where most of these start, is a Tuesday. */
class RepeatTest {
    private fun World.repeating(
        due: String,
        repeat: RepeatRule,
        leadTimes: List<Duration> = emptyList(),
        title: String = "Take the pills",
    ): Task = tools.create(TaskDetails(title = title, due = at(due), repeat = repeat, leadTimes = leadTimes)).task()

    private fun World.currentDue() = tools.list().single().due

    /** Presses Done [times] times and gives the due time the task moved to after each. */
    private fun World.dueAfterEachDone(task: Task, times: Int) = List(times) {
        tools.done(task.id)
        currentDue()
    }

    @Test
    fun `a daily task announces every day and plans one occurrence at a time`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.daily())
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())

        world.advanceTo("2026-11-17T15:00")
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-18T15:00"))), world.scheduler.pending())

        world.advanceTo("2026-11-19T15:00")
        assertEquals(
            listOf(at("2026-11-17T15:00"), at("2026-11-18T15:00"), at("2026-11-19T15:00")),
            world.announcer.announced.map { it.due },
        )
        assertEquals(at("2026-11-19T15:00"), world.currentDue())
    }

    @Test
    fun `a repeating task starts at its first occurrence after now`() {
        val world = World(now = "2026-11-17T10:00")

        val startedLastWeek = world.repeating("2026-11-10T09:00", RepeatRule.daily())
        val laterToday = world.repeating("2026-11-17T15:00", RepeatRule.daily())

        assertEquals(at("2026-11-18T09:00"), startedLastWeek.due)
        assertEquals(at("2026-11-17T15:00"), laterToday.due)
    }

    @Test
    fun `chosen weekdays repeat on those days each week`() {
        val world = World(now = "2026-11-17T10:00")
        val rule = RepeatRule.weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))

        val task = world.repeating("2026-11-17T08:00", rule)

        assertEquals(at("2026-11-18T08:00"), task.due)
        assertEquals(
            listOf(at("2026-11-20T08:00"), at("2026-11-23T08:00"), at("2026-11-25T08:00")),
            world.dueAfterEachDone(task, 3),
        )
    }

    @Test
    fun `every three days`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.everyDays(3))

        assertEquals(listOf(at("2026-11-20T15:00"), at("2026-11-23T15:00")), world.dueAfterEachDone(task, 2))
    }

    @Test
    fun `every two weeks repeats on the weekday of the first due time`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.everyWeeks(2))

        val next = world.dueAfterEachDone(task, 2)

        assertEquals(listOf(at("2026-12-01T15:00"), at("2026-12-15T15:00")), next)
        assertTrue(next.all { it.dayOfWeek == DayOfWeek.TUESDAY })
    }

    @Test
    fun `monthly on the 31st falls on the last day of shorter months`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2027-01-31T09:00", RepeatRule.monthly())

        assertEquals(
            listOf(at("2027-02-28T09:00"), at("2027-03-31T09:00"), at("2027-04-30T09:00")),
            world.dueAfterEachDone(task, 3),
        )
    }

    @Test
    fun `a monthly task that began in the past keeps its day of the month`() {
        val world = World(now = "2026-11-17T10:00")

        val task = world.repeating("2026-01-31T09:00", RepeatRule.monthly())

        assertEquals(at("2026-11-30T09:00"), task.due)
        assertEquals(listOf(at("2026-12-31T09:00")), world.dueAfterEachDone(task, 1))
    }

    @Test
    fun `yearly on 29 February falls on 28 February in other years`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2028-02-29T09:00", RepeatRule.yearly())

        assertEquals(
            listOf(at("2029-02-28T09:00"), at("2030-02-28T09:00"), at("2031-02-28T09:00"), at("2032-02-29T09:00")),
            world.dueAfterEachDone(task, 4),
        )
    }

    @Test
    fun `the end date is inclusive and the task leaves the list after its last occurrence is done`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.daily(until = LocalDate.parse("2026-11-19")))

        assertEquals(listOf(at("2026-11-18T15:00"), at("2026-11-19T15:00")), world.dueAfterEachDone(task, 2))

        world.tools.done(task.id)
        assertEquals(emptyList<Task>(), world.tools.list())
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `a last occurrence that is not done stays overdue with nothing more to come`() {
        val world = World(now = "2026-11-17T10:00")
        world.repeating("2026-11-17T15:00", RepeatRule.daily(until = LocalDate.parse("2026-11-17")))

        world.advanceTo("2026-11-20T15:00")

        assertEquals(at("2026-11-17T15:00"), world.currentDue())
        assertEquals(1, world.announcer.announced.size)
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `done before the due time skips the coming occurrence's fires`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.daily(), leadTimes = listOf(Duration.ofHours(1)))

        world.tools.done(task.id)

        assertEquals(at("2026-11-18T15:00"), world.currentDue())
        assertEquals(
            listOf(Fire(task.id, FireKind.WARNING_1, at("2026-11-18T14:00")), Fire(task.id, FireKind.DUE, at("2026-11-18T15:00"))),
            world.scheduler.pending(),
        )
        world.advanceTo("2026-11-17T23:00")
        assertEquals(emptyList<Announcement>(), world.announcer.announced)
    }

    @Test
    fun `done after the due time covers that occurrence and moves to the next`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.daily())
        world.advanceTo("2026-11-17T15:30")

        world.tools.done(task.id)

        assertEquals(at("2026-11-18T15:00"), world.currentDue())
        assertEquals(Section.TOMORROW, world.tools.list().single().section(world.clock.now()))
        assertEquals(listOf(task.id), world.announcer.cleared)
    }

    @Test
    fun `undoing done on a repeating task brings its occurrence back`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.daily(until = LocalDate.parse("2026-11-17")))

        world.tools.done(task.id)
        world.tools.restore(task)

        assertEquals(listOf(task), world.tools.list())
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
    }

    @Test
    fun `the next occurrence replaces an overdue one and drops its pending snooze`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.daily())
        world.advanceTo("2026-11-18T14:55")
        assertEquals(at("2026-11-17T15:00"), world.currentDue())
        assertEquals(Section.OVERDUE, world.tools.list().single().section(world.clock.now()))
        world.tools.snooze(task.id)

        world.advanceTo("2026-11-18T15:00")

        assertEquals(at("2026-11-18T15:00"), world.currentDue())
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-19T15:00"))), world.scheduler.pending())
        world.advanceTo("2026-11-18T15:30")
        assertEquals(2, world.announcer.announced.size)
    }

    @Test
    fun `lead times apply to every occurrence`() {
        val world = World(now = "2026-11-17T10:00")
        world.repeating("2026-11-17T15:00", RepeatRule.daily(), leadTimes = listOf(Duration.ofHours(1)))

        world.advanceTo("2026-11-18T15:00")

        assertEquals(
            listOf(
                FireKind.WARNING_1 to at("2026-11-17T15:00"),
                FireKind.DUE to at("2026-11-17T15:00"),
                FireKind.WARNING_1 to at("2026-11-18T15:00"),
                FireKind.DUE to at("2026-11-18T15:00"),
            ),
            world.announcer.announced.map { it.kind to it.due },
        )
    }

    @Test
    fun `after the phone was off across several occurrences only the latest is announced`() {
        val world = World(now = "2026-11-17T08:00")
        val task = world.repeating("2026-11-17T09:00", RepeatRule.daily())

        world.restartPhoneAt("2026-11-20T12:00")

        val told = world.announcer.announced.single()
        assertEquals(at("2026-11-20T09:00"), told.due)
        assertTrue(told.saysWasDue)
        assertEquals(at("2026-11-20T09:00"), world.currentDue())
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-21T09:00"))), world.scheduler.pending())
    }

    @Test
    fun `when the latest fire to come due while the phone was off is the next occurrence's warning the task moves on to that occurrence`() {
        val world = World(now = "2026-11-17T07:00")
        val task = world.repeating("2026-11-17T09:00", RepeatRule.daily(), leadTimes = listOf(Duration.ofHours(1)))

        world.restartPhoneAt("2026-11-18T08:30")

        val told = world.announcer.announced.single()
        assertEquals(Duration.ofHours(1), told.leadTime)
        assertEquals(at("2026-11-18T09:00"), told.due)
        assertEquals(at("2026-11-18T09:00"), world.currentDue())
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-18T09:00"))), world.scheduler.pending())
    }

    @Test
    fun `an edit changes the whole task from now on`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.daily())

        world.tools.update(
            task.id,
            TaskDetails(title = "Take the pills", due = at("2026-11-17T18:00"), repeat = RepeatRule.everyDays(2)),
        )

        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T18:00"))), world.scheduler.pending())
        world.advanceTo("2026-11-17T18:00")
        assertEquals(1, world.announcer.announced.size)
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-19T18:00"))), world.scheduler.pending())
    }

    @Test
    fun `changing only the title keeps the rule's place, so a 31st stays a 31st`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2027-01-31T09:00", RepeatRule.monthly())
        world.tools.done(task.id)
        assertEquals(at("2027-02-28T09:00"), world.currentDue())

        world.tools.update(task.id, TaskDetails(title = "Pay the rent", due = at("2027-02-28T09:00"), repeat = RepeatRule.monthly()))

        assertEquals(listOf(at("2027-03-31T09:00")), world.dueAfterEachDone(task, 1))
    }

    @Test
    fun `changing only the end date keeps the rule's place too`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2027-01-31T09:00", RepeatRule.monthly())
        world.tools.done(task.id)
        val endOfApril = RepeatRule.monthly(until = LocalDate.parse("2027-04-30"))

        world.tools.update(task.id, TaskDetails(title = "Take the pills", due = at("2027-02-28T09:00"), repeat = endOfApril))

        assertEquals(listOf(at("2027-03-31T09:00"), at("2027-04-30T09:00")), world.dueAfterEachDone(task, 2))
        world.tools.done(task.id)
        assertEquals(emptyList<Task>(), world.tools.list())
    }

    @Test
    fun `a one-time task can be made to repeat`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.tools.create(TaskDetails(title = "Water the plants", due = at("2026-11-17T15:00"))).task()

        world.tools.update(
            task.id,
            TaskDetails(title = "Water the plants", due = at("2026-11-17T15:00"), repeat = RepeatRule.everyWeeks(1)),
        )

        assertEquals(listOf(at("2026-11-24T15:00")), world.dueAfterEachDone(task, 1))
    }

    @Test
    fun `a repeating task keeps its wall-clock time across a time zone change`() {
        val world = World(now = "2026-11-17T08:00", zone = "Asia/Yangon")
        world.repeating("2026-11-17T09:00", RepeatRule.daily())
        world.advanceTo("2026-11-17T09:00")

        world.clock.zoneId = ZoneId.of("Asia/Tokyo")
        world.clock.local = at("2026-11-17T11:31")
        world.fires.reschedule()

        assertEquals(listOf(Instant.parse("2026-11-18T00:00:00Z")), world.scheduler.pendingInstants())
    }

    @Test
    fun `rules that cannot produce an occurrence are refused`() {
        val world = World(now = "2026-11-17T10:00")
        fun refusal(repeat: RepeatRule) =
            world.tools.create(TaskDetails(title = "Take the pills", due = at("2026-11-17T15:00"), repeat = repeat))

        assertEquals(ToolResult.Refused("Pick at least one weekday."), refusal(RepeatRule.weekdays(emptySet())))
        assertEquals(ToolResult.Refused("A task repeats every 1 or more."), refusal(RepeatRule.everyDays(0)))
        assertEquals(
            ToolResult.Refused("That repeat rule ends before its first occurrence."),
            refusal(RepeatRule.daily(until = LocalDate.parse("2026-11-16"))),
        )
        assertEquals(emptyList<Task>(), world.tools.list())
    }

    @Test
    fun `done on a repeating task hands back the task at its next occurrence`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.repeating("2026-11-17T15:00", RepeatRule.daily())

        assertEquals(at("2026-11-18T15:00"), world.tools.done(task.id).task().due)
    }

    @Test
    fun `changing only the time of a monthly task on the 31st keeps it on the 31st`() {
        val world = World(now = "2027-02-01T10:00")
        val task = world.repeating("2027-01-31T09:00", RepeatRule.monthly())
        assertEquals(at("2027-02-28T09:00"), task.due)

        val edited = world.tools.update(
            task.id,
            TaskDetails(title = task.title, due = at("2027-02-28T10:00"), repeat = RepeatRule.monthly()),
        ).task()

        assertEquals(at("2027-02-28T10:00"), edited.due)
        assertEquals(
            listOf(at("2027-03-31T10:00"), at("2027-04-30T10:00"), at("2027-05-31T10:00")),
            world.dueAfterEachDone(task, times = 3),
        )
    }

    @Test
    fun `changing only the time of a yearly 29 February task keeps it on leap day`() {
        val world = World(now = "2029-01-10T10:00")
        val task = world.repeating("2028-02-29T09:00", RepeatRule.yearly())
        assertEquals(at("2029-02-28T09:00"), task.due)

        world.tools.update(task.id, TaskDetails(title = task.title, due = at("2029-02-28T08:00"), repeat = RepeatRule.yearly()))

        assertEquals(
            listOf(at("2030-02-28T08:00"), at("2031-02-28T08:00"), at("2032-02-29T08:00")),
            world.dueAfterEachDone(task, times = 3),
        )
    }

    @Test
    fun `moving a monthly task to another day counts from that day`() {
        val world = World(now = "2027-02-01T10:00")
        val task = world.repeating("2027-01-31T09:00", RepeatRule.monthly())

        world.tools.update(task.id, TaskDetails(title = task.title, due = at("2027-02-27T09:00"), repeat = RepeatRule.monthly()))

        assertEquals(at("2027-02-27T09:00"), world.currentDue())
        assertEquals(listOf(at("2027-03-27T09:00"), at("2027-04-27T09:00")), world.dueAfterEachDone(task, times = 2))
    }
}
