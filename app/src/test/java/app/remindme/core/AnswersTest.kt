package app.remindme.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswersTest {
    private fun worldWithTaskDueAt3pm(): Pair<World, Task> {
        val world = World(now = "2026-11-17T10:00")
        val task = world.tools.create(TaskDraft(title = "Call the bank", due = at("2026-11-17T15:00"))).task()
        return world to task
    }

    @Test
    fun `a task not marked done after its due time is overdue and stays quiet`() {
        val (world, task) = worldWithTaskDueAt3pm()

        world.advanceTo("2026-11-19T15:00")

        assertEquals(listOf(task.id), world.tools.list().map { it.id })
        assertEquals(Section.OVERDUE, world.tools.list().single().section(world.clock.now()))
        assertEquals(1, world.announcer.announced.size)
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `done after the due time takes a one-time task off the list and clears its notification`() {
        val (world, task) = worldWithTaskDueAt3pm()
        world.advanceTo("2026-11-17T15:01")

        world.tools.done(task.id)

        assertEquals(emptyList<Task>(), world.tools.list())
        assertEquals(listOf(task.id), world.announcer.cleared)
    }

    @Test
    fun `done before the due time cancels the fire`() {
        val (world, task) = worldWithTaskDueAt3pm()

        world.tools.done(task.id)
        world.advanceTo("2026-11-17T16:00")

        assertEquals(emptyList<Announcement>(), world.announcer.announced)
        assertEquals(emptyList<Task>(), world.tools.list())
    }

    @Test
    fun `snooze announces the same occurrence again ten minutes later without changing its due time`() {
        val (world, task) = worldWithTaskDueAt3pm()
        world.advanceTo("2026-11-17T15:02")

        world.tools.snooze(task.id)

        assertEquals(listOf(task.id), world.announcer.cleared)
        assertEquals(listOf(Fire(task.id, FireKind.SNOOZE, at("2026-11-17T15:12"))), world.scheduler.pending())
        world.advanceTo("2026-11-17T15:11")
        assertEquals(1, world.announcer.announced.size)
        world.advanceTo("2026-11-17T15:12")
        assertEquals(2, world.announcer.announced.size)
        assertEquals(at("2026-11-17T15:00"), world.announcer.announced.last().due)
        assertEquals(at("2026-11-17T15:00"), world.tools.list().single().due)
    }

    @Test
    fun `snooze can be used again and again`() {
        val (world, task) = worldWithTaskDueAt3pm()
        world.advanceTo("2026-11-17T15:00")

        repeat(5) {
            world.tools.snooze(task.id)
            world.advanceTo(world.clock.now().plusMinutes(10).toString())
        }

        assertEquals(6, world.announcer.announced.size)
    }

    @Test
    fun `a snooze survives a restart`() {
        val (world, task) = worldWithTaskDueAt3pm()
        world.advanceTo("2026-11-17T15:00")
        world.tools.snooze(task.id)

        world.restartPhoneAt("2026-11-17T15:05")

        assertEquals(listOf(Fire(task.id, FireKind.SNOOZE, at("2026-11-17T15:10"))), world.scheduler.pending())
    }

    @Test
    fun `stop clears the notification and leaves the task overdue`() {
        val (world, task) = worldWithTaskDueAt3pm()
        world.advanceTo("2026-11-17T15:01")

        world.tools.stop(task.id)

        assertEquals(listOf(task.id), world.announcer.cleared)
        assertEquals(Section.OVERDUE, world.tools.list().single().section(world.clock.now()))
        assertEquals(emptyList<Fire>(), world.scheduler.pending())
    }

    @Test
    fun `tasks fall into overdue, today, tomorrow and later`() {
        val world = World(now = "2026-11-17T10:00")
        world.tools.create(TaskDraft(title = "Past", due = at("2026-11-17T10:01")))
        world.tools.create(TaskDraft(title = "Tonight", due = at("2026-11-17T23:59")))
        world.tools.create(TaskDraft(title = "Next morning", due = at("2026-11-18T00:00")))
        world.tools.create(TaskDraft(title = "Next week", due = at("2026-11-24T09:00")))
        world.advanceTo("2026-11-17T10:05")

        val sections = world.tools.list().associate { it.title to it.section(world.clock.now()) }

        assertEquals(
            mapOf(
                "Past" to Section.OVERDUE,
                "Tonight" to Section.TODAY,
                "Next morning" to Section.TOMORROW,
                "Next week" to Section.LATER,
            ),
            sections,
        )
    }

    @Test
    fun `editing the due time replaces the scheduled fire and the old time does not fire`() {
        val (world, task) = worldWithTaskDueAt3pm()
        val oldFire = world.scheduler.pending().single()

        world.tools.update(task.id, TaskDraft(title = "Call the bank", due = at("2026-11-17T17:00")))

        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T17:00"))), world.scheduler.pending())
        world.clock.local = at("2026-11-17T15:00")
        world.tools.fire(oldFire)
        assertEquals(emptyList<Announcement>(), world.announcer.announced)
        world.advanceTo("2026-11-17T17:00")
        assertEquals(1, world.announcer.announced.size)
    }

    @Test
    fun `editing an overdue task to a later time makes it upcoming again`() {
        val (world, task) = worldWithTaskDueAt3pm()
        world.advanceTo("2026-11-17T15:30")
        world.tools.snooze(task.id)

        world.tools.update(task.id, TaskDraft(title = "Call the bank tomorrow", due = at("2026-11-18T09:00")))

        val edited = world.tools.list().single()
        assertEquals("Call the bank tomorrow", edited.title)
        assertEquals(Section.TOMORROW, edited.section(world.clock.now()))
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-18T09:00"))), world.scheduler.pending())
    }

    @Test
    fun `an edit is refused for the same reasons as a new task`() {
        val (world, task) = worldWithTaskDueAt3pm()

        assertEquals(
            ToolResult.Refused("A task needs a title."),
            world.tools.update(task.id, TaskDraft(title = "", due = at("2026-11-17T17:00"))),
        )
        assertEquals(
            ToolResult.Refused("That due time has already passed."),
            world.tools.update(task.id, TaskDraft(title = "Call the bank", due = at("2026-11-17T09:00"))),
        )
        assertEquals(task, world.tools.list().single())
    }

    @Test
    fun `an overdue task can be edited without moving its due time`() {
        val (world, task) = worldWithTaskDueAt3pm()
        world.advanceTo("2026-11-17T15:30")

        val result = world.tools.update(task.id, TaskDraft(title = "Call the bank about the card", due = task.due))

        assertEquals("Call the bank about the card", result.task().title)
        assertEquals(Section.OVERDUE, world.tools.list().single().section(world.clock.now()))
        assertEquals(1, world.announcer.announced.size)
    }

    @Test
    fun `delete removes the task and its fires, and undo brings both back`() {
        val (world, task) = worldWithTaskDueAt3pm()

        val deleted = world.tools.delete(task.id)!!
        assertEquals(emptyList<Task>(), world.tools.list())
        assertEquals(emptyList<Fire>(), world.scheduler.pending())

        world.tools.restore(deleted)
        assertEquals(listOf(task), world.tools.list())
        assertEquals(listOf(Fire(task.id, FireKind.DUE, at("2026-11-17T15:00"))), world.scheduler.pending())
    }

    @Test
    fun `undoing done puts the task back as it was`() {
        val (world, task) = worldWithTaskDueAt3pm()

        val before = world.tools.done(task.id)!!
        world.tools.restore(before)

        assertEquals(listOf(task), world.tools.list())
        world.advanceTo("2026-11-17T15:00")
        assertEquals(1, world.announcer.announced.size)
    }
}
