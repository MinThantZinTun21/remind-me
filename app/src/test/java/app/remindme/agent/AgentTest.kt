package app.remindme.agent

import app.remindme.core.DeliveryStyle
import app.remindme.core.RepeatRule
import app.remindme.core.TaskDetails
import app.remindme.core.ToolResult
import app.remindme.core.World
import app.remindme.core.at
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tuesday 17 November 2026, 10:00. */
class AgentTest {
    private val world = World(now = "2026-11-17T10:00")
    private val agent = Agent(world.tools, world.clock)

    private fun call(name: String, vararg args: Pair<String, String>) = ToolCall(name, linkedMapOf(*args))

    private fun act(name: String, vararg args: Pair<String, String>) = agent.act(listOf(call(name, *args))).single()

    @Test
    fun `the reply parser reads calls in the model's own form and the sentence after them`() {
        val reply = """
            <think></think><tool_call>
            <function=create_task>
            <parameter=title>
            Call the bank
            </parameter>
            <parameter=due>
            2026-11-18T09:00
            </parameter>
            </function>
            </tool_call>
            <tool_call><function=delete_task><parameter=id>3</parameter></function></tool_call>
            I set it for 9 in the morning.
        """.trimIndent()

        val parsed = ReplyParser.parse(reply)

        assertEquals(
            listOf(
                call("create_task", "title" to "Call the bank", "due" to "2026-11-18T09:00"),
                call("delete_task", "id" to "3"),
            ),
            parsed.calls,
        )
        assertEquals("I set it for 9 in the morning.", parsed.answer)
        assertNull(parsed.problem)
    }

    @Test
    fun `a reply cut off inside a call is a problem, and a plain answer is not`() {
        assertEquals(
            "The agent's reply stopped in the middle of a tool call.",
            ReplyParser.parse("<tool_call>\n<function=create_task>\n<parameter=title>\nCall").problem,
        )
        val plain = ReplyParser.parse("I only handle reminders.")
        assertEquals(emptyList<ToolCall>(), plain.calls)
        assertEquals("I only handle reminders.", plain.answer)
        assertNull(plain.problem)
    }

    @Test
    fun `create_task makes the task with every property the agent chose and schedules its fires`() {
        val card = act(
            "create_task",
            "title" to "Dentist",
            "due" to "2026-11-20T15:30",
            "warn_before" to "60,1440",
            "style" to "alarm",
        )

        val task = world.tools.list().single()
        assertTrue(card.went)
        assertEquals(task, card.task)
        assertEquals("Dentist", task.title)
        assertEquals(at("2026-11-20T15:30"), task.due)
        assertEquals(DeliveryStyle.ALARM, task.style)
        assertEquals(listOf(Duration.ofDays(1), Duration.ofHours(1)), task.leadTimes)
        assertEquals(RepeatRule.ONCE, task.repeat)
        assertEquals(3, world.scheduler.pending().size)
    }

    @Test
    fun `a call that leaves out the optional properties gets a one-time notification with no early warning`() {
        val task = act("create_task", "title" to "Buy milk", "due" to "2026-11-17 18:00").task!!

        assertEquals(DeliveryStyle.NOTIFICATION, task.style)
        assertEquals(emptyList<Duration>(), task.leadTimes)
        assertEquals(RepeatRule.ONCE, task.repeat)
    }

    @Test
    fun `the repeat arguments map onto every repeat rule the app has`() {
        fun rule(vararg args: Pair<String, String>) =
            act("create_task", "title" to "x", "due" to "2026-11-18T08:00", *args).task!!.repeat

        assertEquals(RepeatRule.daily(), rule("repeat" to "daily"))
        assertEquals(RepeatRule.everyDays(3), rule("repeat" to "daily", "repeat_every" to "3"))
        assertEquals(RepeatRule.everyWeeks(1), rule("repeat" to "weekly"))
        assertEquals(RepeatRule.everyWeeks(2), rule("repeat" to "weekly", "repeat_every" to "2"))
        assertEquals(
            RepeatRule.weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
            rule("repeat" to "weekly", "repeat_days" to "mon, wed,Friday"),
        )
        assertEquals(RepeatRule.monthly(), rule("repeat" to "monthly"))
        assertEquals(RepeatRule.yearly(until = LocalDate.parse("2030-01-01")), rule("repeat" to "yearly", "repeat_until" to "2030-01-01"))
        assertEquals(RepeatRule.ONCE, rule("repeat" to "none"))
    }

    @Test
    fun `a weekly task on chosen weekdays starts on the first of those days after now`() {
        val task = act("create_task", "title" to "Gym", "due" to "2026-11-17T07:00", "repeat" to "weekly", "repeat_days" to "mon,thu").task!!

        // Tuesday 07:00 has passed and is not one of the days: the first occurrence is Thursday.
        assertEquals(at("2026-11-19T07:00"), task.due)
    }

    @Test
    fun `a value the app cannot read is refused and nothing is created`() {
        assertEquals("The app cannot read the due time \"tomorrow at 9\".", act("create_task", "title" to "x", "due" to "tomorrow at 9").refusal)
        assertEquals("The agent gave no due time.", act("create_task", "title" to "x").refusal)
        assertEquals(
            "The app cannot read the lead time \"1 hour\".",
            act("create_task", "title" to "x", "due" to "2026-11-18T09:00", "warn_before" to "1 hour").refusal,
        )
        assertEquals(
            "The app cannot read the repeat rule \"fortnightly\".",
            act("create_task", "title" to "x", "due" to "2026-11-18T09:00", "repeat" to "fortnightly").refusal,
        )
        assertEquals("The app has no tool called \"send_email\".", act("send_email", "to" to "x").refusal)
        assertEquals(emptyList<Any>(), world.tools.list())
    }

    @Test
    fun `the tools' own refusals reach the card`() {
        val card = act("create_task", "title" to "Too late", "due" to "2026-11-17T09:00")

        assertFalse(card.went)
        assertEquals("That due time has already passed.", card.refusal)
        assertNull(card.undo)
    }

    @Test
    fun `update_task changes only what the call gives`() {
        val before = (world.tools.create(
            TaskDetails("Call mom", at("2026-11-18T09:00"), DeliveryStyle.ALARM, listOf(Duration.ofMinutes(15)), RepeatRule.daily()),
        ) as ToolResult.Ok).task

        val card = act("update_task", "id" to before.id.toString(), "due" to "2026-11-18T10:30")

        val after = world.tools.list().single()
        assertTrue(card.went)
        assertEquals(at("2026-11-18T10:30"), after.due)
        assertEquals("Call mom", after.title)
        assertEquals(DeliveryStyle.ALARM, after.style)
        assertEquals(listOf(Duration.ofMinutes(15)), after.leadTimes)
        assertEquals(RepeatRule.daily(), after.repeat)
    }

    @Test
    fun `update_task can take the early warning away and end a repeat rule`() {
        val before = (world.tools.create(
            TaskDetails("Call mom", at("2026-11-18T09:00"), leadTimes = listOf(Duration.ofMinutes(15)), repeat = RepeatRule.daily()),
        ) as ToolResult.Ok).task

        act("update_task", "id" to before.id.toString(), "warn_before" to "0", "repeat_until" to "2026-12-31")

        val after = world.tools.list().single()
        assertEquals(emptyList<Duration>(), after.leadTimes)
        assertEquals(RepeatRule.daily(until = LocalDate.parse("2026-12-31")), after.repeat)
    }

    @Test
    fun `delete_task and complete_task act on the task with that id, and an unknown id is refused`() {
        val first = (world.tools.create(TaskDetails("First", at("2026-11-18T09:00"))) as ToolResult.Ok).task
        val second = (world.tools.create(TaskDetails("Second", at("2026-11-18T10:00"))) as ToolResult.Ok).task

        assertTrue(act("delete_task", "id" to first.id.toString()).went)
        assertTrue(act("complete_task", "id" to second.id.toString()).went)
        assertEquals("There is no task with id \"99\" on the list.", act("delete_task", "id" to "99").refusal)

        assertEquals(emptyList<Any>(), world.tools.list())
        assertEquals(listOf("Second"), world.tools.history().map { it.title })
    }

    @Test
    fun `undo takes back a create, an update, a delete and a completion`() {
        val created = act("create_task", "title" to "Dentist", "due" to "2026-11-20T15:30")
        agent.undo(created)
        assertEquals(emptyList<Any>(), world.tools.list())

        val task = (world.tools.create(TaskDetails("Call mom", at("2026-11-18T09:00"))) as ToolResult.Ok).task
        val id = "id" to task.id.toString()

        agent.undo(act("update_task", id, "title" to "Call dad", "due" to "2026-11-19T09:00"))
        assertEquals(listOf(task), world.tools.list())

        agent.undo(act("delete_task", id))
        assertEquals(listOf(task), world.tools.list())

        agent.undo(act("complete_task", id))
        assertEquals(listOf(task), world.tools.list())
        assertEquals(1, world.scheduler.pending().size)
    }

    @Test
    fun `the time now comes with the user's message, so the instruction stays the same while the user types`() {
        val before = agent.systemInstruction()

        assertEquals("Now: Tuesday 2026-11-17 10:00\nwake me at 8", agent.userMessage("wake me at 8"))
        world.clock.local = world.clock.local.plusMinutes(7)

        assertEquals(before, agent.systemInstruction())
        assertEquals("Now: Tuesday 2026-11-17 10:07\nwake me at 8", agent.userMessage("wake me at 8"))
    }

    @Test
    fun `the agent is told to answer with tool calls only`() {
        val told = agent.systemInstruction()

        assertTrue(told, "your whole reply is tool calls and nothing else" in told)
        assertTrue(told, "never ask a question" in told)
    }

    @Test
    fun `the agent is told the day, the coming days by name and date, and the tasks with their ids`() {
        val task = (world.tools.create(
            TaskDetails("Call mom", at("2026-11-18T09:00"), DeliveryStyle.ALARM, listOf(Duration.ofMinutes(15)), RepeatRule.daily()),
        ) as ToolResult.Ok).task

        val told = agent.systemInstruction()

        assertTrue(told, "Today: Tuesday 2026-11-17." in told)
        assertTrue(told, "Wed 2026-11-18 (tomorrow), Thu 2026-11-19" in told)
        assertTrue(told, "Tue 2026-12-01" in told)
        assertTrue(told, "- id ${task.id}: Call mom | due Wed 2026-11-18 09:00 | daily | alarm | warns 15 min before" in told)
    }

    @Test
    fun `with no tasks the agent is told there are none`() {
        assertTrue("Tasks:\n- (none)" in agent.systemInstruction())
    }
}
