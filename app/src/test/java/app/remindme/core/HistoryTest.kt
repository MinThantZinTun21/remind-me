package app.remindme.core

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTest {
    private fun World.task(title: String, due: String, leadTimes: List<Duration> = emptyList()): Task =
        tools.create(TaskDetails(title = title, due = at(due), leadTimes = leadTimes)).task()

    @Test
    fun `a done one-time task moves to history, most recently done first`() {
        val world = World(now = "2026-11-17T10:00")
        val bank = world.task("Call the bank", "2026-11-17T15:00")
        val plants = world.task("Water the plants", "2026-11-17T16:00")

        world.tools.done(plants.id)
        world.advanceTo("2026-11-17T15:10")
        world.tools.done(bank.id)

        assertEquals(emptyList<Task>(), world.tools.list())
        assertEquals(
            listOf("Call the bank" to at("2026-11-17T15:10"), "Water the plants" to at("2026-11-17T10:00")),
            world.tools.history().map { it.title to it.doneAt },
        )
    }

    @Test
    fun `a task is removed thirty days after it was marked done`() {
        val world = World(now = "2026-11-17T10:00")
        val bank = world.task("Call the bank", "2026-11-17T15:00")
        world.advanceTo("2026-11-17T15:10")
        world.tools.done(bank.id)

        world.advanceTo("2026-12-17T15:09")
        assertEquals(listOf(bank.id), world.tools.history().map { it.id })

        world.advanceTo("2026-12-17T15:10")
        assertEquals(emptyList<Task>(), world.tools.history())
    }

    @Test
    fun `old history is cleared out when the phone starts, without the history page being opened`() {
        val world = World(now = "2026-11-17T10:00")
        val bank = world.task("Call the bank", "2026-11-17T15:00")
        world.tools.done(bank.id)

        world.restartPhoneAt("2027-01-05T08:00")

        assertEquals(emptyList<Task>(), world.store.all())
    }

    @Test
    fun `not done returns a task past its due time to the list as overdue, and it stays quiet`() {
        val world = World(now = "2026-11-17T10:00")
        val bank = world.task("Call the bank", "2026-11-17T15:00")
        world.advanceTo("2026-11-17T15:10")
        world.tools.done(bank.id)
        world.advanceTo("2026-11-18T09:00")

        world.tools.notDone(bank.id)

        assertEquals(emptyList<Task>(), world.tools.history())
        assertEquals(Section.OVERDUE, world.tools.list().single().section(world.clock.now()))
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
        world.advanceTo("2026-11-19T09:00")
        assertEquals(1, world.announcer.announced.size)
    }

    @Test
    fun `not done before the due time schedules the task's fires again`() {
        val world = World(now = "2026-11-17T10:00")
        val bank = world.task("Call the bank", "2026-11-17T15:00")
        world.tools.done(bank.id)
        world.advanceTo("2026-11-17T11:00")

        world.tools.notDone(bank.id)

        assertEquals(listOf(Fire(bank.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
        world.advanceTo("2026-11-17T15:00")
        assertEquals(listOf(at("2026-11-17T15:00")), world.announcer.announced.map { it.due })
    }

    @Test
    fun `not done skips a task that was due while it sat in history`() {
        val world = World(now = "2026-11-17T10:00")
        val bank = world.task("Call the bank", "2026-11-17T15:00", leadTimes = listOf(Duration.ofHours(1)))
        world.tools.done(bank.id)
        world.advanceTo("2026-11-17T14:30")

        world.tools.notDone(bank.id)

        assertEquals(listOf(Fire(bank.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
    }

    @Test
    fun `a repeating task never appears in history`() {
        val world = World(now = "2026-11-17T10:00")
        val pills = world.tools.create(
            TaskDetails(
                title = "Take the pills",
                due = at("2026-11-17T15:00"),
                repeat = RepeatRule.daily(until = java.time.LocalDate.parse("2026-11-18")),
            ),
        ).task()

        world.tools.done(pills.id)
        assertEquals(emptyList<Task>(), world.tools.history())

        world.tools.done(pills.id)
        assertEquals(emptyList<Task>(), world.tools.list())
        assertEquals(emptyList<Task>(), world.tools.history())
    }

    @Test
    fun `not done on a task that is no longer in history is refused and changes nothing`() {
        val world = World(now = "2026-11-17T10:00")
        val bank = world.task("Call the bank", "2026-11-17T15:00")
        world.tools.done(bank.id)
        world.advanceTo("2026-12-20T10:00")

        val result = world.tools.notDone(bank.id)

        assertEquals(ToolResult.Refused("That task is not in history."), result)
        assertEquals(emptyList<Task>(), world.tools.list())
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `reading history is the same every time and leaves the list alone`() {
        val world = World(now = "2026-11-17T10:00")
        val bank = world.task("Call the bank", "2026-11-17T15:00")
        val plants = world.task("Water the plants", "2026-11-17T16:00")
        world.tools.done(bank.id)

        assertEquals(world.tools.history(), world.tools.history())
        assertEquals(listOf(plants.id), world.tools.list().map { it.id })
    }
}
