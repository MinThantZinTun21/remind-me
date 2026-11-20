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

    private fun instruction() = agent.rules() + "\n\n" + agent.taskList(world.tools.list())

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
            "when" to "friday at 3 30 pm",
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
        val task = act("create_task", "title" to "Buy milk", "when" to "6 pm").task!!

        assertEquals(DeliveryStyle.NOTIFICATION, task.style)
        assertEquals(emptyList<Duration>(), task.leadTimes)
        assertEquals(RepeatRule.ONCE, task.repeat)
    }

    @Test
    fun `the user's words for how it comes round become every repeat rule the app has`() {
        fun rule(words: String) = act("create_task", "title" to "x", "when" to "tomorrow at 8", "repeat" to words).task!!.repeat

        assertEquals(RepeatRule.daily(), rule("daily"))
        assertEquals(RepeatRule.everyDays(3), rule("every 3 days"))
        assertEquals(RepeatRule.everyWeeks(1), rule("weekly"))
        assertEquals(RepeatRule.everyWeeks(2), rule("every 2 weeks"))
        assertEquals(RepeatRule.weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), rule("every mon, wed and fri"))
        assertEquals(
            RepeatRule.weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
            rule("weekdays"),
        )
        assertEquals(RepeatRule.monthly(), rule("monthly"))
        assertEquals(RepeatRule.yearly(until = LocalDate.parse("2027-01-01")), rule("every year until january 1"))
        assertEquals(RepeatRule.ONCE, rule("none"))
    }

    @Test
    fun `a task on chosen weekdays starts on the first of those days after now`() {
        val task = act("create_task", "title" to "Gym", "when" to "7 am", "repeat" to "every monday and thursday").task!!

        // Tuesday 07:00 has passed and is not one of the days: the first occurrence is Thursday.
        assertEquals(at("2026-11-19T07:00"), task.due)
    }

    @Test
    fun `a single weekday every other week starts on that weekday`() {
        val task = act("create_task", "title" to "Bins", "when" to "8 am", "repeat" to "every other friday").task!!

        assertEquals(at("2026-11-20T08:00"), task.due)
        assertEquals(RepeatRule.everyWeeks(2), task.repeat)
    }

    @Test
    fun `repeat words the app cannot read are refused, never made a one-time task`() {
        assertEquals(
            "Remind Me can't repeat 'every full moon' yet — set it by hand.",
            act("create_task", "title" to "x", "when" to "tomorrow at 9", "repeat" to "every full moon").refusal,
        )
        assertEquals(emptyList<Any>(), world.tools.list())
    }

    @Test
    fun `repeat words that are not in the user's message are refused`() {
        val card = agent.act(
            listOf(call("create_task", "title" to "Gym", "when" to "7 am", "repeat" to "every day")),
            message = "gym at 7 am every monday",
        ).single()

        assertEquals("The words for how it comes round, \"every day\", are not all in the message: day. Copy the user's own words.", card.refusal)
        assertEquals(emptyList<Any>(), world.tools.list())
    }

    @Test
    fun `a value the app cannot read is refused and nothing is created`() {
        assertEquals("The app cannot read when \"whenever\".", act("create_task", "title" to "x", "when" to "whenever").refusal)
        assertEquals(
            "The app cannot read the lead time \"1 hour\".",
            act("create_task", "title" to "x", "when" to "tomorrow at 9", "warn_before" to "1 hour").refusal,
        )
        assertEquals("The app has no tool called \"send_email\".", act("send_email", "to" to "x").refusal)
        assertEquals(emptyList<Any>(), world.tools.list())
    }

    @Test
    fun `a call with no when is set for one hour from now`() {
        assertEquals(at("2026-11-17T11:00"), act("create_task", "title" to "x").task!!.due)
    }

    @Test
    fun `words for when that are not in the user's message are refused`() {
        val card = agent.act(listOf(call("create_task", "title" to "Dentist", "when" to "wednesday at 9")), message = "dentist tomorrow at 9").single()

        assertEquals("The words for when, \"wednesday at 9\", are not all in the message: wednesday. Copy the user's own words.", card.refusal)
        assertEquals(emptyList<Any>(), world.tools.list())
    }

    @Test
    fun `the phone run's failures come out right`() {
        // 2026-11-18 on the Camon 40: "next tuesday" became Monday, "5 30 tomorrow" became 09:30.
        val next = agent.act(listOf(call("create_task", "title" to "Bank", "when" to "next tuesday")), message = "bank next tuesday").single()
        val half = agent.act(listOf(call("create_task", "title" to "Gym", "when" to "5 30 tomorrow")), message = "gym 5 30 tomorrow").single()

        assertEquals(at("2026-11-24T09:00"), next.task!!.due)
        assertEquals(at("2026-11-18T17:30"), half.task!!.due)
    }

    @Test
    fun `the tools' own refusals reach the card`() {
        val card = act("create_task", "title" to "Too late", "when" to "today at 9 am")

        assertFalse(card.went)
        assertEquals("That due time has already passed.", card.refusal)
        assertNull(card.undo)
    }

    @Test
    fun `update_task changes only what the call gives`() {
        val before = (world.tools.create(
            TaskDetails("Call mom", at("2026-11-18T09:00"), DeliveryStyle.ALARM, listOf(Duration.ofMinutes(15)), RepeatRule.daily()),
        ) as ToolResult.Ok).task

        val card = act("update_task", "id" to before.id.toString(), "when" to "10 30")

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

        act("update_task", "id" to before.id.toString(), "warn_before" to "0", "repeat" to "until december 31")

        val after = world.tools.list().single()
        assertEquals(emptyList<Duration>(), after.leadTimes)
        assertEquals(RepeatRule.daily(until = LocalDate.parse("2026-12-31")), after.repeat)
    }

    @Test
    fun `an update that would leave the task as it was is refused, so the card does not say Changed`() {
        val before = (world.tools.create(TaskDetails("Call mom", at("2026-11-18T09:00"))) as ToolResult.Ok).task

        val card = act("update_task", "id" to before.id.toString(), "when" to "tomorrow at 9")

        assertFalse(card.went)
        assertEquals("The agent's change left \"Call mom\" exactly as it was.", card.refusal)
        assertEquals(listOf(before), world.tools.list())
    }

    @Test
    fun `a daily task can be moved a day after an earlier change to it was undone`() {
        val created = act("create_task", "title" to "Call mom", "when" to "tomorrow at 9", "repeat" to "daily").task!!
        val id = "id" to created.id.toString()

        agent.undo(act("update_task", id, "when" to "tomorrow at 9", "warn_before" to "0", "style" to "alarm"))
        assertEquals(listOf(created), world.tools.list())

        val moved = act("update_task", id, "when" to "thursday at 9")
        assertTrue(moved.refusal, moved.went)
        assertEquals(at("2026-11-19T09:00"), world.tools.list().single().due)
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
        val created = act("create_task", "title" to "Dentist", "when" to "friday at 3 30 pm")
        agent.undo(created)
        assertEquals(emptyList<Any>(), world.tools.list())

        val task = (world.tools.create(TaskDetails("Call mom", at("2026-11-18T09:00"))) as ToolResult.Ok).task
        val id = "id" to task.id.toString()

        agent.undo(act("update_task", id, "title" to "Call dad", "when" to "thursday at 9"))
        assertEquals(listOf(task), world.tools.list())

        agent.undo(act("delete_task", id))
        assertEquals(listOf(task), world.tools.list())

        agent.undo(act("complete_task", id))
        assertEquals(listOf(task), world.tools.list())
        assertEquals(1, world.scheduler.pending().size)
    }

    @Test
    fun `the time now comes with the user's message, so the instruction stays the same while the user types`() {
        val before = instruction()

        assertEquals("Now: Tuesday 2026-11-17 10:00\nwake me at 8", agent.userMessage("wake me at 8"))
        world.clock.local = world.clock.local.plusMinutes(7)

        assertEquals(before, instruction())
        assertEquals("Now: Tuesday 2026-11-17 10:07\nwake me at 8", agent.userMessage("wake me at 8"))
    }

    @Test
    fun `the agent is told to answer with tool calls only`() {
        val told = instruction()

        assertTrue(told, "your whole reply is tool calls and nothing else" in told)
        assertTrue(told, "never ask a question" in told)
    }

    @Test
    fun `the agent is told the tasks with their ids, and no date to count from`() {
        val task = (world.tools.create(
            TaskDetails("Call mom", at("2026-11-18T09:00"), DeliveryStyle.ALARM, listOf(Duration.ofMinutes(15)), RepeatRule.daily()),
        ) as ToolResult.Ok).task

        val told = instruction()

        assertFalse(told, "Today:" in told)
        assertFalse(told, "Coming days" in told)
        assertFalse(told, "never count days" in told)
        assertTrue(told, "- id ${task.id}: Call mom | due Wed 2026-11-18 09:00 | daily | alarm | warns 15 min before" in told)
    }

    @Test
    fun `the agent is told to copy the user's words for how it comes round`() {
        assertTrue("repeat is the user's own words for how it comes round, end included" in instruction())
        assertFalse("repeat_days" in Agent.TOOLS_JSON)
    }

    @Test
    fun `with no tasks the agent is told there are none`() {
        assertTrue("Tasks:\n- (none)" in instruction())
    }
}
