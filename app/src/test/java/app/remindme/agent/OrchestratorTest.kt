package app.remindme.agent

import app.remindme.core.DayButtons
import app.remindme.core.TaskDetails
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
        model.replies += reply(call("delete_task", "id" to "9"))
        orchestrator.send("delete the dentist")
        model.replies += "NONE"
        orchestrator.send("hmm")

        val conversation = orchestrator.conversation()
        assertTrue(conversation, "Remind Me: could not delete: There is no task with id \"9\" on the list." in conversation)
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
class FakeModel(override val contextTokens: Int = 4096) : ModelPort {
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
