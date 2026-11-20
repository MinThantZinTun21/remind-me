package app.remindme.agent

import app.remindme.core.DayButtons
import app.remindme.core.TaskDetails
import app.remindme.core.ToolResult
import app.remindme.core.World
import app.remindme.core.at
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tuesday 17 November 2026, 10:00. The orchestrator with a scripted model and the real tools. */
class OrchestratorTest {
    private val world = World(now = "2026-11-17T10:00")
    private val model = FakeModel()
    private val orchestrator = Orchestrator(Agent(world.tools, world.clock), world.tools, model)

    @Test
    fun `a message becomes the same cards as the reply's calls`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"))

        val exchange = orchestrator.send("call mom thursday at 9")

        val card = exchange.cards.single()
        assertEquals(ActionCard.Action.CREATE, card.action)
        assertTrue(card.went)
        assertEquals("Call mom", world.tools.list().single().title)
        assertEquals(at("2026-11-19T09:00"), world.tools.list().single().due)
        assertEquals(listOf("Now: Tuesday 2026-11-17 10:00\ncall mom thursday at 9"), model.said)
    }

    @Test
    fun `the next message's context holds the earlier words and one line per card, not the raw calls`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"))
        orchestrator.send("call mom thursday at 9")
        model.replies += "NONE"

        orchestrator.send("thanks")

        val instruction = model.prefaces.last().instruction
        assertTrue(instruction, "Conversation so far:\nUser: call mom thursday at 9\nRemind Me: created id 1 Call mom, Thu 19 Nov 09:00, once" in instruction)
        assertFalse(instruction, "<tool_call>" in instruction)
        assertTrue(instruction, "- id 1: Call mom | due Thu 2026-11-19 09:00" in instruction)
    }

    @Test
    fun `the first message of a visit has no conversation`() {
        model.replies += "NONE"

        orchestrator.send("hello")

        assertFalse(model.prefaces.single().instruction.contains("Conversation so far:"))
        assertEquals(Agent.TOOLS_JSON, model.prefaces.single().tools)
    }

    @Test
    fun `a follow-up naming the task by id changes the task just made`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-18T18:00"))
        orchestrator.send("call mom tomorrow at 6")
        model.replies += reply(call("update_task", "id" to "1", "due" to "2026-11-18T19:00"))

        val exchange = orchestrator.send("change it to 7")

        assertEquals(ActionCard.Action.UPDATE, exchange.cards.single().action)
        assertEquals(at("2026-11-18T19:00"), world.tools.list().single().due)
        assertTrue("Remind Me: changed id 1 to Call mom, Wed 18 Nov 19:00, once" in orchestrator.conversation())
    }

    @Test
    fun `a refused call and a reply with nothing to do both leave a line`() {
        repeat(2) { model.replies += reply(call("delete_task", "id" to "9")) }
        orchestrator.send("delete the dentist")
        model.replies += "NONE"
        orchestrator.send("hmm")

        val conversation = orchestrator.conversation()
        assertTrue(conversation, "Remind Me: could not delete: Couldn't find that task." in conversation)
        assertTrue(conversation, "User: hmm\nRemind Me: nothing to remind you of" in conversation)
    }

    @Test
    fun `ending the visit ends the conversation`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"))
        orchestrator.send("call mom thursday at 9")

        orchestrator.endVisit()
        model.replies += "NONE"
        orchestrator.send("thanks")

        assertFalse(model.prefaces.last().instruction.contains("Conversation so far:"))
        assertEquals("Call mom", world.tools.list().single().title)
    }

    @Test
    fun `an undo on a card reaches the model before the next message`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"))
        val card = orchestrator.send("call mom thursday at 9").cards.single()

        orchestrator.undo(card)
        model.replies += "NONE"
        orchestrator.send("never mind")

        val instruction = model.prefaces.last().instruction
        assertTrue(instruction, "Remind Me: created id 1 Call mom, Thu 19 Nov 09:00, once (undone by you)" in instruction)
        assertTrue(instruction, "Tasks:\n- (none)" in instruction)
    }

    @Test
    fun `an edit made on the task reaches the model before the next message`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"))
        val task = orchestrator.send("call mom thursday at 9").cards.single().task!!

        world.tools.update(task.id, TaskDetails(task.title, at("2026-11-20T09:00"), task.style, task.leadTimes, task.repeat))

        assertTrue(orchestrator.conversation(), "created id 1 Call mom, Thu 19 Nov 09:00, once (you changed it to Call mom, Fri 20 Nov 09:00, once)" in orchestrator.conversation())
        assertTrue(orchestrator.preface().instruction.contains("- id 1: Call mom | due Fri 2026-11-20 09:00"))
    }

    @Test
    fun `a day tap on a card reaches the model before the next message`() {
        model.replies += reply(call("create_task", "title" to "Gym", "due" to "2026-11-18T07:00", "repeat" to "daily"))
        val task = orchestrator.send("gym every day at 7").cards.single().task!!

        DayButtons(world.tools, world.clock).tap(task, DayOfWeek.SUNDAY)

        assertTrue(orchestrator.conversation(), "(you changed it to Gym, Wed 18 Nov 07:00, weekly on mon,tue,wed,thu,fri,sat)" in orchestrator.conversation())
    }

    @Test
    fun `a task the model changes later is not marked as changed by the owner`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-18T18:00"))
        orchestrator.send("call mom tomorrow at 6")
        model.replies += reply(call("update_task", "id" to "1", "due" to "2026-11-18T19:00"))
        orchestrator.send("change it to 7")

        assertFalse(orchestrator.conversation(), "you changed it" in orchestrator.conversation())
    }

    private fun estimate(text: String) = (text.length + 3) / 4

    /** The context size that leaves exactly [fits] for the conversation and tasks, after the reply and a short message. */
    private fun contextFor(fits: Int): Int {
        val agent = Agent(world.tools, world.clock)
        return 320 + 64 + estimate(agent.rules()) + estimate(Agent.TOOLS_JSON) + fits
    }

    private fun task(title: String, due: String) =
        (world.tools.create(TaskDetails(title, at(due))) as ToolResult.Ok).task

    @Test
    fun `the oldest exchanges are left out first`() {
        for (words in listOf("first message", "second message", "third message")) {
            model.replies += "NONE"
            orchestrator.send(words)
        }
        val agent = Agent(world.tools, world.clock)
        model.contextTokens = contextFor(estimate(agent.taskList(emptyList())) + estimate("Conversation so far:\nUser: third message\nRemind Me: nothing to remind you of"))
        model.replies += "NONE"

        val record = orchestrator.send("fourth").context

        val conversation = record.parts.single { it.name == "Conversation" }.text
        assertEquals("Conversation so far:\nUser: third message\nRemind Me: nothing to remind you of", conversation)
        assertEquals("2 earlier exchanges", record.leftOut)
    }

    @Test
    fun `then the task list shrinks, keeping touched tasks and then the soonest due`() {
        val a = task("Alpha", "2026-11-18T09:00")
        task("Bravo", "2026-11-19T09:00")
        task("Charlie", "2026-11-20T09:00")
        val d = task("Delta", "2026-11-21T09:00")
        model.replies += reply(call("update_task", "id" to "${d.id}", "title" to "Delta two"))
        orchestrator.send("rename delta")
        val agent = Agent(world.tools, world.clock)
        val kept = world.tools.list().filter { it.id in setOf(a.id, d.id) }
        model.contextTokens = contextFor(estimate(agent.taskList(kept)))
        model.replies += "NONE"

        val record = orchestrator.send("ok")

        assertEquals(agent.taskList(kept), record.context.parts.single { it.name == "Tasks" }.text)
        assertEquals("1 earlier exchange, 2 tasks", record.context.leftOut)
    }

    @Test
    fun `the instruction, the tools and the message are never cut`() {
        task("Alpha", "2026-11-18T09:00")
        model.contextTokens = 100
        model.replies += "NONE"

        val record = orchestrator.send("hello").context

        assertEquals(Agent(world.tools, world.clock).rules(), record.parts.single { it.name == "Instruction" }.text)
        assertEquals(Agent.TOOLS_JSON, record.parts.single { it.name == "Tools" }.text)
        assertEquals("Now: Tuesday 2026-11-17 10:00\nhello", record.parts.last().text)
        assertEquals("1 task", record.leftOut)
    }

    @Test
    fun `nothing is named as left out when everything fits`() {
        task("Alpha", "2026-11-18T09:00")
        model.replies += "NONE"

        assertEquals(null, orchestrator.send("hello").context.leftOut)
    }

    @Test
    fun `a call on a task the model could not see is refused`() {
        task("Alpha", "2026-11-18T09:00")
        val hidden = task("Bravo", "2026-11-19T09:00")
        model.contextTokens = contextFor(estimate(Agent(world.tools, world.clock).taskList(world.tools.list().take(1))))
        repeat(2) { model.replies += reply(call("delete_task", "id" to "${hidden.id}")) }

        val card = orchestrator.send("delete bravo").cards.single()

        assertEquals("Couldn't find that task.", card.refusal)
        assertEquals(2, world.tools.list().size)
    }

    @Test
    fun `the context record lists every part as the model read it, in order, adding up to the exact total`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"))
        orchestrator.send("call mom thursday at 9")
        model.replies += "NONE"

        val record = orchestrator.send("thanks").context

        assertEquals(listOf("Instruction", "Tools", "Tasks", "Conversation", "Message"), record.parts.map { it.name })
        val preface = model.prefaces.last()
        assertEquals(preface.instruction, record.parts.filter { it.name in setOf("Instruction", "Tasks", "Conversation") }.joinToString("\n\n") { it.text })
        assertEquals(preface.tools, record.parts.single { it.name == "Tools" }.text)
        assertEquals(model.said.last(), record.parts.last().text)
        // The fake's exact count: what it read before the reply.
        val read = model.count(preface.instruction) + model.count(preface.tools) + model.count(model.said.last())
        assertEquals(read, record.read)
        assertEquals(read, record.parts.sumOf { it.tokens })
        assertEquals(model.count("NONE"), record.written)
        assertEquals(4096, record.size)
        assertEquals("NONE", record.output)
    }

    @Test
    fun `part sizes are estimates at four characters a token when the runtime gives no count`() {
        model.exact = false
        model.replies += "NONE"

        val record = orchestrator.send("hello").context

        assertEquals(-1, record.read)
        record.parts.forEach { assertEquals((it.text.length + 3) / 4, it.tokens) }
    }

    @Test
    fun `what the model reads in the background is what the next message opens with`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"))
        orchestrator.send("call mom thursday at 9")

        orchestrator.prepare()
        world.clock.local = at("2026-11-17T10:03")
        model.replies += "NONE"
        orchestrator.send("thanks")

        assertTrue("Conversation so far:" in model.prepared.single().instruction)
        assertEquals(model.prepared.single(), model.prefaces.last())
    }

    @Test
    fun `a garbled reply is asked for once more in the same talk, giving the reason`() {
        model.replies += "<tool_call><function=create_task><parameter=title>Call mom"
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"))

        val exchange = orchestrator.send("call mom thursday at 9")

        assertEquals(2, model.said.size)
        assertEquals(1, model.opened)
        assertTrue(model.said[1], "The agent's reply stopped in the middle of a tool call." in model.said[1])
        assertTrue(exchange.cards.single().went)
        assertEquals(null, exchange.problem)
        val retry = exchange.context.retry!!
        assertEquals(model.said[1], retry.reason)
        assertEquals(reply(call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00")), retry.output)
        assertEquals("<tool_call><function=create_task><parameter=title>Call mom", exchange.context.output)
    }

    @Test
    fun `words instead of calls, and calls that all fail, are unusable`() {
        model.replies += "Sure, I will remind you."
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "thursday"))

        val exchange = orchestrator.send("call mom thursday at 9")

        assertEquals(2, model.said.size)
        assertTrue(model.said[1], "no tool call" in model.said[1])
        assertFalse(exchange.cards.single().went)
        assertEquals("The app cannot read the due time \"thursday\".", exchange.cards.single().refusal)
        assertTrue(world.tools.list().isEmpty())
    }

    @Test
    fun `there is only one second attempt`() {
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "thursday"))
        model.replies += reply(call("create_task", "title" to "Call mom", "due" to "thursday 9"))

        val exchange = orchestrator.send("call mom thursday at 9")

        assertEquals(2, model.said.size)
        assertTrue(model.said[1], "The app cannot read the due time \"thursday\"." in model.said[1])
        assertEquals("The app cannot read the due time \"thursday 9\".", exchange.cards.single().refusal)
    }

    @Test
    fun `a reply with a usable call is not asked again, and its working calls keep their cards`() {
        model.replies += reply(
            call("create_task", "title" to "Call mom", "due" to "2026-11-19T09:00"),
            call("create_task", "title" to "Dentist", "due" to "friday"),
        )

        val exchange = orchestrator.send("call mom thursday at 9 and dentist friday")

        assertEquals(1, model.said.size)
        assertEquals(listOf(true, false), exchange.cards.map { it.went })
        assertEquals(null, exchange.context.retry)
    }

    @Test
    fun `NONE is not asked again`() {
        model.replies += "NONE"

        val exchange = orchestrator.send("hello")

        assertEquals(1, model.said.size)
        assertTrue(exchange.problem!!.startsWith("Nothing to remind you of"))
    }

    @Test
    fun `each message gets its own talk with the model, closed afterwards`() {
        model.replies += "NONE"
        model.replies += "NONE"

        orchestrator.send("one")
        orchestrator.send("two")

        assertEquals(2, model.opened)
        assertEquals(2, model.closed)
    }
}

/** A model that answers from a script and remembers what it was given. */
class FakeModel(override var contextTokens: Int = 4096) : ModelPort {
    val replies = ArrayDeque<String>()
    val prefaces = mutableListOf<Preface>()
    val said = mutableListOf<String>()
    val prepared = mutableListOf<Preface>()
    var opened = 0
    var closed = 0

    /** Tokens as the fake counts them: one for every four characters. */
    var count: (String) -> Int = { (it.length + 3) / 4 }

    /** False for a runtime that cannot count the tokens in its context. */
    var exact = true

    override fun prepare(preface: Preface) {
        prepared += preface
    }

    override fun open(preface: Preface): Talk {
        prefaces += preface
        opened++
        var read = count(preface.instruction) + count(preface.tools)
        return object : Talk {
            override fun say(text: String, onText: (String) -> Unit): ModelReply {
                said += text
                val answer = replies.removeFirstOrNull() ?: error("the fake model has no reply left for \"$text\"")
                read += count(text) + count(answer)
                return ModelReply(answer, tokensInContext = if (exact) read else -1, tokensWritten = count(answer))
            }

            override fun close() {
                closed++
            }
        }
    }
}

fun call(name: String, vararg args: Pair<String, String>) = ToolCall(name, linkedMapOf(*args))

/** The reply text a model in the `<tool_call><function=…>` form writes for [calls]. */
fun reply(vararg calls: ToolCall): String = calls.joinToString("\n") { c ->
    "<tool_call>\n<function=${c.name}>\n" +
        c.args.entries.joinToString("") { (k, v) -> "<parameter=$k>\n$v\n</parameter>\n" } +
        "</function>\n</tool_call>"
}
