package app.remindme.agent

import app.remindme.core.ToolResult
import app.remindme.core.Tools
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What the model reads before any message: the system text the runtime takes (the instruction, the tasks shown and
 * the conversation so far) and the tools.
 */
data class Preface(val system: String, val tools: String)

/** What the model may write, kept free in its context; the engine's output limit. */
const val MAX_OUTPUT_TOKENS = 320

/** One reply from the model, and what the runtime counted while writing it. */
data class ModelReply(
    val text: String,
    /** Every token in the model's context once the reply was written; -1 when the runtime could not say. */
    val tokensInContext: Int = -1,
    val tokensWritten: Int = 0,
    val firstTokenMs: Long = 0,
    val totalMs: Long = 0,
    /** The calls the runtime read out of the reply itself, for a model type it knows; null when it read none. */
    val calls: List<ToolCall>? = null,
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

/** One part of the context, exactly as the model read it, and about how many tokens it took. */
data class ContextPart(val name: String, val text: String, val tokens: Int)

/** What the model read and wrote for one message, for the context view. */
data class ContextRecord(
    val parts: List<ContextPart>,
    /** Tokens the model read, as the runtime counted them; -1 when it could not say. */
    val read: Int,
    /** Tokens the model can hold. */
    val size: Int,
    val written: Int,
    /** The reply exactly as the model wrote it. */
    val output: String,
    /** What did not fit, "2 earlier exchanges, 6 tasks"; null when everything did. */
    val leftOut: String? = null,
    /** The second attempt, when the first reply was unusable; null when there was none. */
    val retry: Retry? = null,
)

/** The second attempt: what the app told the model, and what the model wrote back. */
data class Retry(val reason: String, val output: String)

/** One message the user sent and what came of it. See GLOSSARY.md. */
data class Exchange(
    val words: String,
    val cards: List<ActionCard>,
    /** What to tell the user when nothing was done; null when the reply had calls the app could read. */
    val problem: String?,
    /** The model's reply as written; null when the model failed before replying. */
    val reply: ModelReply?,
    val context: ContextRecord,
)

/**
 * Runs one message from start to finish: builds the context, asks the model, carries out the calls and
 * adds the outcome to the conversation. The conversation lasts for one visit to the agent screen.
 */
class Orchestrator(private val agent: Agent, private val tools: Tools, private val model: ModelPort) {

    // The screen undoes cards on the main thread while a message may be running on the model's thread.
    private val exchanges = CopyOnWriteArrayList<Exchange>()

    /** Cards the user took back during this visit. */
    private val undone: MutableSet<ActionCard> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    /** What the model reads before the next message, for a message of usual length. */
    fun preface(): Preface = prefaceOf(fit("").parts)

    /** The context parts before the message, cut to fit beside it, and what was left out. */
    private class Fit(val parts: List<ContextPart>, val shown: Set<Long>, val leftOut: String?)

    /**
     * Fits the context to the model: the instruction, the tools and the message are never cut; earlier
     * exchanges are left out oldest first, then tasks not touched in this conversation, latest due first.
     */
    private fun fit(message: String): Fit {
        val budget = model.contextTokens - MAX_OUTPUT_TOKENS - maxOf(estimate(message), MESSAGE_ALLOWANCE)
        val fixed = estimate(agent.rules()) + estimate(Agent.TOOLS_JSON)
        val touched = touched()
        var kept = exchanges.toList()
        var tasks = tools.list()
        val allTasks = tasks.size
        fun conversation() = conversationOf(kept).takeIf { it.isNotEmpty() }?.let { "Conversation so far:\n$it" }
        fun size() = fixed + estimate(agent.taskList(tasks)) + (conversation()?.let(::estimate) ?: 0)
        while (size() > budget && kept.isNotEmpty()) kept = kept.drop(1)
        while (size() > budget) {
            val leaving = tasks.lastOrNull { it.id !in touched } ?: break
            tasks = tasks - leaving
        }
        val parts = listOfNotNull(
            part("Instruction", agent.rules()),
            part("Tools", Agent.TOOLS_JSON),
            part("Tasks", agent.taskList(tasks)),
            conversation()?.let { part("Conversation", it) },
        )
        val leftOut = listOfNotNull(
            counted(exchanges.size - kept.size, "earlier exchange", "earlier exchanges"),
            counted(allTasks - tasks.size, "task", "tasks"),
        ).joinToString(", ").ifEmpty { null }
        return Fit(parts, tasks.map { it.id }.toSet(), leftOut)
    }

    /** The tasks this conversation created, changed or showed: they stay in the task list whatever else is cut. */
    private fun touched(): Set<Long> = exchanges.flatMap { it.cards }.flatMap { card -> listOfNotNull(card.task?.id) + card.listed.map { it.id } }.toSet()

    private fun counted(n: Int, one: String, many: String) = when (n) {
        0 -> null
        1 -> "1 $one"
        else -> "$n $many"
    }

    private fun prefaceOf(parts: List<ContextPart>) = Preface(
        system = parts.filter { it.name in SYSTEM_PARTS }.joinToString("\n\n") { it.text },
        tools = parts.single { it.name == "Tools" }.text,
    )

    /** Has the model read the next message's preface while the user types. */
    fun prepare() = model.prepare(preface())

    /** Sends the user's [words] and returns what came of them. Blocks until the model has replied. */
    fun send(words: String, onText: (String) -> Unit = {}): Exchange {
        val message = agent.userMessage(words)
        val fit = fit(message)
        val parts = fit.parts + part("Message", message)
        val exchange = try {
            model.open(prefaceOf(fit.parts)).use { talk ->
                val first = attempt(talk, message, words, fit.shown, onText)
                val reason = unusable(first)
                if (reason == null) {
                    exchangeOf(words, first, record(parts, first.reply, fit.leftOut))
                } else {
                    // Once more in the same talk: the model has the context and its own reply already.
                    val second = attempt(talk, reason, words, fit.shown, onText)
                    val kept = if (second.cards.isNotEmpty() || first.cards.isEmpty()) second else first
                    val record = record(parts, first.reply, fit.leftOut).let {
                        it.copy(written = it.written + second.reply.tokensWritten, retry = Retry(reason, second.reply.text))
                    }
                    exchangeOf(words, kept, record).copy(reply = second.reply)
                }
            }
        } catch (failure: Exception) {
            Exchange(words, emptyList(), "The model stopped: ${failure.message}", null, record(parts, null, fit.leftOut))
        }
        exchanges += exchange
        return exchange
    }

    /** One reply and what the app made of it. */
    private class Attempt(val reply: ModelReply, val parsed: ParsedReply, val cards: List<ActionCard>) {
        val none: Boolean get() = parsed.calls.isEmpty() && parsed.problem == null && parsed.answer.trim().trimEnd('.').equals("NONE", ignoreCase = true)
    }

    /** Says [text] to the model and carries out the calls in its reply, which answer the user's [words]. */
    private fun attempt(talk: Talk, text: String, words: String, shown: Set<Long>, onText: (String) -> Unit): Attempt {
        val reply = talk.say(text, onText)
        // The runtime reads the calls of the model types it knows; Remind Me's own reader reads the rest.
        val parsed = reply.calls?.takeIf { it.isNotEmpty() }?.let { ParsedReply(it, "", null) } ?: ReplyParser.parse(reply.text)
        return Attempt(reply, parsed, agent.act(parsed.calls, shown, words))
    }

    /** Why nothing in the reply could be used, told to the model for its second attempt; null when something could, or it said NONE. */
    private fun unusable(attempt: Attempt): String? {
        if (attempt.none || attempt.cards.any { it.went }) return null
        val reasons = listOfNotNull(
            attempt.parsed.problem,
            NO_CALL.takeIf { attempt.parsed.calls.isEmpty() && attempt.parsed.problem == null },
        ) + attempt.cards.mapNotNull { it.refusal }
        return "Nothing in your reply could be used. " + reasons.joinToString(" ") + " Write the tool calls again, in the same form."
    }

    private fun exchangeOf(words: String, attempt: Attempt, record: ContextRecord): Exchange {
        val problem = attempt.parsed.problem ?: when {
            attempt.cards.isNotEmpty() -> null
            attempt.none -> NOTHING_TO_DO
            else -> NO_USABLE_CALL
        }
        return Exchange(words, attempt.cards, problem, attempt.reply, record)
    }

    private fun record(parts: List<ContextPart>, reply: ModelReply?, leftOut: String?): ContextRecord {
        val read = reply?.tokensInContext?.takeIf { it >= 0 }?.let { it - reply.tokensWritten } ?: -1
        return ContextRecord(scaled(parts, read), read, model.contextTokens, reply?.tokensWritten ?: 0, reply?.text.orEmpty(), leftOut)
    }

    /** The estimated part sizes, scaled so that they add up to the [read] the runtime counted. */
    private fun scaled(parts: List<ContextPart>, read: Int): List<ContextPart> {
        val estimated = parts.sumOf { it.tokens }
        if (read < 0 || estimated == 0) return parts
        val sizes = parts.map { it.tokens.toLong() * read / estimated }.map(Long::toInt).toMutableList()
        val biggest = sizes.indices.maxBy { sizes[it] }
        sizes[biggest] += read - sizes.sum()
        return parts.mapIndexed { i, part -> part.copy(tokens = sizes[i]) }
    }

    private fun part(name: String, text: String) = ContextPart(name, text, estimate(text))

    private fun estimate(text: String) = (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    /** Takes back what [card] reports, and tells the model so with the next message; a refused undo changes nothing. */
    fun undo(card: ActionCard): ToolResult? = agent.undo(card).also { if (it is ToolResult.Ok) undone += card }

    /** The user left the agent screen or changed the model: the next message starts afresh. */
    fun endVisit() {
        exchanges.clear()
        undone.clear()
    }

    /** The conversation so far, a line for the user's words and one for each card. */
    fun conversation(): String = conversationOf(exchanges)

    private fun conversationOf(shown: List<Exchange>): String {
        // Only the newest card on a task can have been overtaken by the user's own change since.
        val newest: MutableSet<ActionCard> = Collections.newSetFromMap(IdentityHashMap())
        exchanges.flatMap { it.cards }.filter { it.went && it.task != null }.associateBy { it.task!!.id }.values.forEach { newest += it }
        return shown.joinToString("\n") { exchange -> lines(exchange, newest) }
    }

    private fun lines(exchange: Exchange, newest: Set<ActionCard>): String {
        val lines = exchange.cards.map { card -> line(card) + afterwards(card, card in newest) }.ifEmpty {
            listOf(if (exchange.reply == null || exchange.problem != NOTHING_TO_DO) "nothing done: ${exchange.problem}" else "nothing to remind you of")
        }
        return (listOf("User: ${exchange.words}") + lines.map { "Remind Me: $it" }).joinToString("\n")
    }

    /** What the user did to the card's task after the card: took it back, or changed the task by hand. */
    private fun afterwards(card: ActionCard, newest: Boolean): String {
        if (card in undone) return " (undone by you)"
        val task = card.task ?: return ""
        if (!newest || card.action !in CHANGING) return ""
        val now = tools.list().firstOrNull { it.id == task.id }
            ?: return if (tools.history().any { it.id == task.id }) " (you marked it done)" else " (you deleted it)"
        return if (agent.summary(now) == agent.summary(task)) "" else " (you changed it to ${agent.summary(now)})"
    }

    private fun line(card: ActionCard): String {
        if (card.lists) return shown(card)
        val task = card.task
        if (!card.went || task == null) return "could not ${verb(card)}: ${card.refusal}"
        return when (card.action) {
            ActionCard.Action.CREATE -> "created id ${task.id} ${agent.summary(task)}"
            ActionCard.Action.UPDATE -> "changed id ${task.id} to ${agent.summary(task)}"
            ActionCard.Action.DELETE -> "deleted id ${task.id} ${agent.summary(task)}"
            ActionCard.Action.COMPLETE -> "marked id ${task.id} ${task.title} done"
            ActionCard.Action.SNOOZE -> "snoozed id ${task.id} ${task.title}"
            ActionCard.Action.SHOW -> shown(card)
            ActionCard.Action.UNKNOWN -> "could not ${verb(card)}: ${card.refusal}"
        }
    }

    /** "showed 2 tasks for Friday: ids 4, 7", or "in all" for every task. */
    private fun shown(card: ActionCard): String {
        val range = if (card.range?.all == true) "in all" else "for ${card.range?.label}"
        val ids = card.listed.map { it.id }
        if (ids.isEmpty()) return "showed no tasks $range"
        val count = if (ids.size == 1) "1 task" else "${ids.size} tasks"
        return "showed $count $range: ${if (ids.size == 1) "id" else "ids"} ${ids.joinToString(", ")}"
    }

    private fun verb(card: ActionCard) = when (card.action) {
        ActionCard.Action.CREATE -> "create"
        ActionCard.Action.UPDATE -> "change"
        ActionCard.Action.DELETE -> "delete"
        ActionCard.Action.COMPLETE -> "mark done"
        ActionCard.Action.SNOOZE -> "snooze"
        ActionCard.Action.SHOW -> "show tasks"
        ActionCard.Action.UNKNOWN -> "run ${card.call.name}"
    }

    companion object {
        const val NOTHING_TO_DO = "Nothing to remind you of in that. Say what it is and when."
        private const val NO_USABLE_CALL = "The agent wrote nothing the app could use. Try saying it another way."
        private const val NO_CALL = "Your reply had no tool call."

        /** Room kept for the message when the context is fitted before the user has typed it. */
        private const val MESSAGE_ALLOWANCE = 64

        /** About how many characters make a token, for sizing the parts of the context. */
        private const val CHARS_PER_TOKEN = 4

        /** The cards whose task the user may have changed by hand since. */
        private val CHANGING = setOf(ActionCard.Action.CREATE, ActionCard.Action.UPDATE)

        /** The context parts that go into the system text, kept in the order the context lists them. */
        private val SYSTEM_PARTS = setOf("Instruction", "Tasks", "Conversation")
    }
}
