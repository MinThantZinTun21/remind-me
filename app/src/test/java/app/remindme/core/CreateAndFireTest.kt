package app.remindme.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateAndFireTest {
    @Test
    fun `a created task is listed`() {
        val world = World(now = "2026-11-17T10:00")

        world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00")))

        assertEquals(listOf("Call the bank"), world.tools.list().map { it.title })
    }

    @Test
    fun `a created task has exactly one fire scheduled, at its due time`() {
        val world = World(now = "2026-11-17T10:00")

        val task = world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"))).task()

        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
    }

    @Test
    fun `a task announces its title at its due time and not before`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00")))

        world.advanceTo("2026-11-17T14:59")
        assertEquals(emptyList<Announcement>(), world.announcer.announced)

        world.advanceTo("2026-11-17T15:00")
        assertEquals(listOf("Call the bank"), world.announcer.announced.map { it.title })
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `a task with an empty title is refused`() {
        val world = World(now = "2026-11-17T10:00")

        val result = world.tools.create(TaskDetails(title = "   ", due = at("2026-11-17T15:00")))

        assertEquals(ToolResult.Refused("A task needs a title."), result)
        assertEquals(emptyList<Task>(), world.tools.list())
    }

    @Test
    fun `a one-time task whose due time has passed is refused`() {
        val world = World(now = "2026-11-17T10:00")

        val result = world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T09:59")))

        assertEquals(ToolResult.Refused("That due time has already passed."), result)
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `a title is stored without the spaces around it`() {
        val world = World(now = "2026-11-17T10:00")

        val task = world.tools.create(TaskDetails(title = "  Call the bank ", due = at("2026-11-17T15:00"))).task()

        assertEquals("Call the bank", task.title)
    }

    @Test
    fun `tasks are listed soonest first`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDetails(title = "Later", due = at("2026-11-19T08:00")))
        world.tools.create(TaskDetails(title = "Soon", due = at("2026-11-17T11:00")))
        world.tools.create(TaskDetails(title = "Tomorrow", due = at("2026-11-18T08:00")))

        assertEquals(listOf("Soon", "Tomorrow", "Later"), world.tools.list().map { it.title })
    }

    @Test
    fun `the fire log records what fired, when it was meant to and how long after that it arrived`() {
        val world = World(now = "2026-11-17T10:00")
        val task = world.tools.create(TaskDetails(title = "Call the bank", due = at("2026-11-17T15:00"))).task()

        // The phone's alarm system delivers the fire two seconds after its moment.
        world.clock.local = at("2026-11-17T15:00:02")
        world.fires.deliver(world.scheduler.pending().single())

        val record = world.fireLog.entries.single()
        assertEquals(task.id, record.taskId)
        assertEquals("Call the bank", record.title)
        assertEquals(at("2026-11-17T15:00"), record.scheduledFor)
        assertEquals(2, record.lateness.seconds)
    }
}

fun ToolResult.task(): Task = when (this) {
    is ToolResult.Ok -> task
    is ToolResult.Refused -> throw AssertionError("refused: $reason")
}
