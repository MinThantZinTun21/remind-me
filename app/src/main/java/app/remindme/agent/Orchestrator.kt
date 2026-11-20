package app.remindme.agent

import app.remindme.core.Tools

/** What the model reads before any message: the instruction (rules, tasks, conversation so far) and the tools. */
data class Preface(val instruction: String, val tools: String)

/** One reply from the model, and what the runtime counted while writing it. */
data class ModelReply(
    val text: String,
    /** Every token in the model's context once the reply was written; -1 when the runtime could not say. */
    val tokensInContext: Int = -1,
    val tokensWritten: Int = 0,
    val firstTokenMs: Long = 0,
    val totalMs: Long = 0,
)

/**
 * The narrow way the orchestrator reaches a model: the LiteRT-LM engine on the phone, or a scripted
 * model in tests. Every call blocks; the screen makes them off the main thread, one at a time.
 */
interface ModelPort {
    /** How many tokens the model can hold: what it reads and what it writes. */
    val contextTokens: Int

    /** Has a model read [preface] now, so that a later [open] with the same preface is quick. */
    fun prepare(preface: Preface) {}

    /** A talk with a model that has read [preface]. Close it when the message is done. */
    fun open(preface: Preface): Talk
}

/** One message's talk with the model. */
interface Talk : AutoCloseable {
    /** Sends [text] and waits for the whole reply; [onText] gets the reply so far while it is written. */
    fun say(text: String, onText: (String) -> Unit = {}): ModelReply
}

/** One message the owner sent and what came of it. See GLOSSARY.md. */
data class Exchange(
    val words: String,
    val cards: List<ActionCard>,
    /** What to tell the owner when nothing was done; null when the reply had calls the app could read. */
    val problem: String?,
    /** The model's reply as written; null when the model failed before replying. */
    val reply: ModelReply?,
)

/**
 * Runs one message from start to finish: builds the context, asks the model, carries out the calls and
 * adds the outcome to the conversation. The conversation lasts for one visit to the agent screen.
 */
class Orchestrator(private val agent: Agent, private val tools: Tools, private val model: ModelPort) {

    private val history = mutableListOf<Exchange>()

    /** What the model reads before the next message. */
    fun preface(): Preface {
        val parts = listOfNotNull(
            agent.rules(),
            agent.taskList(tools.list().take(MAX_TASKS)),
            conversation().takeIf { it.isNotEmpty() }?.let { "Conversation so far:\n$it" },
        )
        return Preface(parts.joinToString("\n\n"), Agent.TOOLS_JSON)
    }

    /** Has the model read the next message's preface while the owner types. */
    fun prepare() = model.prepare(preface())

    /** Sends the owner's [words] and returns what came of them. Blocks until the model has replied. */
    fun send(words: String, onText: (String) -> Unit = {}): Exchange {
        val exchange = try {
            model.open(preface()).use { talk ->
                val reply = talk.say(agent.userMessage(words), onText)
                val parsed = ReplyParser.parse(reply.text)
                val cards = agent.act(parsed.calls)
                Exchange(words, cards, parsed.problem ?: NOTHING_TO_DO.takeIf { cards.isEmpty() }, reply)
            }
        } catch (failure: Exception) {
            Exchange(words, emptyList(), "The model stopped: ${failure.message}", null)
        }
        history += exchange
        return exchange
    }

    /** The owner left the agent screen or changed the model: the next message starts afresh. */
    fun endVisit() = history.clear()

    /** The conversation so far, a line for the owner's words and one for each card. */
    fun conversation(): String = history.joinToString("\n") { exchange ->
        val lines = exchange.cards.map(::line).ifEmpty {
            listOf(if (exchange.reply == null || exchange.problem != NOTHING_TO_DO) "nothing done: ${exchange.problem}" else "nothing to remind you of")
        }
        (listOf("User: ${exchange.words}") + lines.map { "Remind Me: $it" }).joinToString("\n")
    }

    private fun line(card: ActionCard): String {
        val task = card.task
        if (!card.went || task == null) return "could not ${verb(card)}: ${card.refusal}"
        return when (card.action) {
            ActionCard.Action.CREATE -> "created id ${task.id} ${agent.summary(task)}"
            ActionCard.Action.UPDATE -> "changed id ${task.id} to ${agent.summary(task)}"
            ActionCard.Action.DELETE -> "deleted id ${task.id} ${agent.summary(task)}"
            ActionCard.Action.COMPLETE -> "marked id ${task.id} ${task.title} done"
            ActionCard.Action.UNKNOWN -> "could not ${verb(card)}: ${card.refusal}"
        }
    }

    private fun verb(card: ActionCard) = when (card.action) {
        ActionCard.Action.CREATE -> "create"
        ActionCard.Action.UPDATE -> "change"
        ActionCard.Action.DELETE -> "delete"
        ActionCard.Action.COMPLETE -> "mark done"
        ActionCard.Action.UNKNOWN -> "run ${card.call.name}"
    }

    companion object {
        const val NOTHING_TO_DO = "The agent found nothing to remind you of in that. Say what it is and when."

        /** More tasks than this do not fit beside the rules in the model's small context. */
        private const val MAX_TASKS = 15
    }
}
