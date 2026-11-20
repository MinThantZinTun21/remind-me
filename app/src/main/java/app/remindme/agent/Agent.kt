package app.remindme.agent

import app.remindme.core.Clock
import app.remindme.core.DeliveryStyle
import app.remindme.core.RepeatRule
import app.remindme.core.Task
import app.remindme.core.TaskDetails
import app.remindme.core.ToolResult
import app.remindme.core.Tools
import app.remindme.core.now
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.format.TextStyle
import java.util.Locale

/** The app's account of what the agent just did to a task. See GLOSSARY.md. */
data class ActionCard(
    val call: ToolCall,
    val action: Action,
    /** The task as it now stands; for a deleted task, as it was. Null when the call named no task the app could find. */
    val task: Task?,
    /** Why nothing changed; null when the call went through. */
    val refusal: String?,
    /** How to take the action back; null when nothing changed. */
    val undo: Undo?,
) {
    enum class Action { CREATE, UPDATE, DELETE, COMPLETE, UNKNOWN }

    val went: Boolean get() = refusal == null
}

sealed interface Undo {
    /** Takes away the task the agent created. */
    data class Remove(val taskId: Long) : Undo

    /** Puts a task back as it was before the agent changed, deleted or completed it. */
    data class PutBack(val task: Task) : Undo
}

/**
 * The agent's side of the tools: what the model on the phone is told it can call, what it is told about
 * the clock and the tasks, and how each call it writes becomes a call on [Tools].
 *
 * The agent decides every property of a task. Nothing here chooses a due time, a repeat rule, a lead time
 * or a delivery style for it: a call that leaves one out gets what [TaskDetails] gives by default, and a
 * value the app cannot read is refused, not guessed.
 */
class Agent(private val tools: Tools, private val clock: Clock) {

    /**
     * The part of the instruction that is the same whatever tasks and conversation come with it. It holds
     * no date: the model copies the user's words for when, and [WordReader] works out the moment.
     */
    fun rules(): String = SYSTEM

    /** The tasks the model is shown, each with its id. */
    fun taskList(tasks: List<Task>): String =
        "Tasks:\n" + tasks.joinToString("\n") { "- " + it.forTheAgent() }.ifEmpty { "- (none)" }

    /** A task in one short line for the conversation: "Call mom, Thu 19 Nov 09:00, once". */
    fun summary(task: Task): String = listOfNotNull(
        task.title,
        task.due.format(SUMMARY_DUE),
        task.repeat.forTheAgent() ?: "once",
        "alarm".takeIf { task.style == DeliveryStyle.ALARM },
        task.leadTimes.takeIf { it.isNotEmpty() }?.joinToString(" and ", prefix = "warns ", postfix = " min before") { it.toMinutes().toString() },
    ).joinToString(", ")

    /** The user's text as the model gets it: the time now, then the text. */
    fun userMessage(text: String): String = "Now: ${clock.now().format(NOW)}\n$text"

    /**
     * Runs every call, in order, and reports each on an action card. With [shown], a call may only name a
     * task the model was shown, or one it created in this reply. With [message], the user's words the
     * calls answer, words for when must be copied from it.
     */
    fun act(calls: List<ToolCall>, shown: Set<Long>? = null, message: String? = null): List<ActionCard> {
        val visible = shown?.toMutableSet()
        return calls.map { call ->
            act(call, visible, message).also { card -> if (card.action == ActionCard.Action.CREATE) card.task?.let { visible?.add(it.id) } }
        }
    }

    /** Takes back what a card reports. */
    fun undo(card: ActionCard): ToolResult? = when (val undo = card.undo) {
        is Undo.Remove -> tools.delete(undo.taskId)
        is Undo.PutBack -> tools.restore(undo.task)
        null -> null
    }

    private fun act(call: ToolCall, visible: Set<Long>?, message: String?): ActionCard = when (call.name) {
        "create_task" -> create(call, message)
        "update_task" -> onExisting(call, visible, ActionCard.Action.UPDATE) { before -> update(call, before, message) }
        "delete_task" -> onExisting(call, visible, ActionCard.Action.DELETE) { before -> tools.delete(before.id) }
        "complete_task" -> onExisting(call, visible, ActionCard.Action.COMPLETE) { before -> tools.done(before.id) }
        else -> ActionCard(call, ActionCard.Action.UNKNOWN, null, "The app has no tool called \"${call.name}\".", null)
    }

    private fun create(call: ToolCall, message: String?): ActionCard {
        val action = ActionCard.Action.CREATE
        val title = call.args["title"].orEmpty()
        val details = try {
            val style = style(call.args["style"]) ?: DeliveryStyle.NOTIFICATION
            val due = due(whenWords(call.args), message, style, keep = null)
            val repeat = repeat(call.args["repeat"], message, due.toLocalDate())
            TaskDetails(
                title = title,
                due = repeat?.on?.let { due.with(TemporalAdjusters.nextOrSame(it)) } ?: due,
                style = style,
                leadTimes = leadTimes(call.args["warn_before"]).orEmpty(),
                repeat = repeat?.rule ?: if (repeat == null) RepeatRule.ONCE else throw Unreadable(cantRepeat(call.args["repeat"].orEmpty())),
            )
        } catch (unreadable: Unreadable) {
            return ActionCard(call, action, null, unreadable.message, null)
        }
        return when (val result = tools.create(details)) {
            is ToolResult.Ok -> ActionCard(call, action, result.task, null, Undo.Remove(result.task.id))
            is ToolResult.Refused -> ActionCard(call, action, null, result.reason, null)
        }
    }

    /** Only what the call gives changes; everything else stays as the task has it. */
    private fun update(call: ToolCall, before: Task, message: String?): ToolResult {
        val args = call.args
        val due = whenWords(args)?.let { due(it, message, style(args["style"]) ?: before.style, keep = before.due) }
        val repeat = repeat(args["repeat"], message, (due ?: before.due).toLocalDate())
        val asItWas = TaskDetails(before.title, before.due, before.style, before.leadTimes, before.repeat)
        val wanted = asItWas.copy(
            title = args["title"]?.takeIf { it.isNotBlank() } ?: before.title,
            due = (due ?: before.due).let { moved -> repeat?.on?.let { moved.with(TemporalAdjusters.nextOrSame(it)) } ?: moved },
            style = style(args["style"]) ?: before.style,
            leadTimes = leadTimes(args["warn_before"]) ?: before.leadTimes,
            repeat = repeat?.rule ?: repeat?.until?.let { before.repeat.copy(until = it) } ?: before.repeat,
        )
        // The agent sometimes names the right task and then writes back what it already had.
        if (wanted == asItWas) throw Unreadable("The agent's change left \"${before.title}\" exactly as it was.")
        return tools.update(before.id, wanted)
    }

    private fun onExisting(call: ToolCall, visible: Set<Long>?, action: ActionCard.Action, change: (Task) -> ToolResult): ActionCard {
        val idText = call.args["id"].orEmpty()
        val id = idText.trim().toLongOrNull()
        if (visible != null && id !in visible) return ActionCard(call, action, null, NOT_FOUND, null)
        val before = id?.let { tools.list().firstOrNull { it.id == id } }
            ?: return ActionCard(call, action, null, "There is no task with id \"$idText\" on the list.", null)
        val result = try {
            change(before)
        } catch (unreadable: Unreadable) {
            return ActionCard(call, action, before, unreadable.message, null)
        }
        return when (result) {
            is ToolResult.Ok -> ActionCard(call, action, result.task, null, Undo.PutBack(before))
            is ToolResult.Refused -> ActionCard(call, action, before, result.reason, null)
        }
    }

    private class Unreadable(override val message: String) : Exception(message)

    /** The user's words for when; `due` is what the first tool contract called it, and some models still write it. */
    private fun whenWords(args: Map<String, String>): String? = args["when"] ?: args["due"]

    /** The moment [words] mean; refused when the app cannot read them or they are not the user's own. */
    private fun due(words: String?, message: String?, style: DeliveryStyle, keep: LocalDateTime?): LocalDateTime {
        val given = words.orEmpty()
        copied(given, message, "when")
        return WordReader.due(given, clock.now(), style == DeliveryStyle.ALARM, keep)
            ?: throw Unreadable("The app cannot read when \"$given\".")
    }

    private fun style(text: String?): DeliveryStyle? = when (text?.trim()?.lowercase()) {
        null, "" -> null
        "notification" -> DeliveryStyle.NOTIFICATION
        "alarm" -> DeliveryStyle.ALARM
        else -> throw Unreadable("The app cannot read the delivery style \"$text\".")
    }

    /** Minutes before the due time, one or two numbers; "0" or "none" for no early warning. Null when not given. */
    private fun leadTimes(text: String?): List<Duration>? {
        val given = text?.trim()?.lowercase() ?: return null
        if (given in setOf("", "none", "0", "[]")) return emptyList()
        val minutes = given.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toLongOrNull() }
        if (minutes.isEmpty() || minutes.any { it == null } || given.any { it.isLetter() }) {
            throw Unreadable("The app cannot read the lead time \"$text\".")
        }
        return minutes.filterNotNull().filter { it > 0 }.distinct().map(Duration::ofMinutes)
    }

    /**
     * What the user's words for how the task comes round say, for a task first due on [start]; null when
     * the call says nothing about repeating. Words the app cannot read are refused, never made a one-time task.
     */
    private fun repeat(words: String?, message: String?, start: LocalDate): WordReader.RepeatWords? {
        val given = words?.trim()?.takeUnless { it.lowercase() in NO_REPEAT } ?: return null
        copied(given, message, "how it comes round")
        return WordReader.repeat(given, start, clock.now()) ?: throw Unreadable(cantRepeat(given))
    }

    private fun cantRepeat(words: String) = "Remind Me can't repeat '$words' yet — set it by hand."

    /** Refuses [words] that are not all the user's own, from [message]. */
    private fun copied(words: String, message: String?, what: String) {
        if (message == null) return
        val foreign = WordReader.notIn(words, message)
        if (foreign.isNotEmpty()) throw Unreadable("The words for $what, \"$words\", are not all in the message: ${foreign.joinToString(", ")}. Copy the user's own words.")
    }

    /** One task as the agent is shown it: its id, then every property the agent could change. */
    private fun Task.forTheAgent(): String = listOfNotNull(
        "id $id: $title",
        "due ${due.format(TASK_DUE)}",
        repeat.forTheAgent(),
        "alarm".takeIf { style == DeliveryStyle.ALARM },
        leadTimes.takeIf { it.isNotEmpty() }?.joinToString(" and ", prefix = "warns ", postfix = " min before") { it.toMinutes().toString() },
    ).joinToString(" | ")

    private fun RepeatRule.forTheAgent(): String? {
        val cycle = when (cycle) {
            RepeatRule.Cycle.ONCE -> return null
            RepeatRule.Cycle.DAYS -> if (every == 1) "daily" else "every $every days"
            RepeatRule.Cycle.WEEKS -> if (every == 1) "weekly" else "every $every weeks"
            RepeatRule.Cycle.WEEKDAYS -> "weekly on " + weekdays.sorted().joinToString(",") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() }
            RepeatRule.Cycle.MONTHLY -> "monthly"
            RepeatRule.Cycle.YEARLY -> "yearly"
        }
        return cycle + (until?.let { " until $it" } ?: "")
    }

    companion object {
        /** A call named a task the model was not shown. */
        const val NOT_FOUND = "Couldn't find that task."

        private val NOW = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH)
        private val TASK_DUE = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm", Locale.ENGLISH)
        /** What a model writes for a task that does not come round; never the user's words for a repeat. */
        private val NO_REPEAT = setOf("", "none", "once", "never", "no")
        private val SUMMARY_DUE = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH)

        private const val TASK_PROPERTIES = """
          "title": {"type": "string", "description": "What to do, in a few words."},
          "when": {"type": "string", "description": "The user's own words for when it happens, copied from the message, like next tuesday at 2 pm."},
          "repeat": {"type": "string", "description": "The user's own words for how it comes round, end included, copied from the message, like every sunday and wednesday. Leave out unless it repeats."},
          "warn_before": {"type": "string", "description": "Early warnings, in minutes before due: one or two numbers, like 30 or 60,1440. 0 for none."},
          "style": {"type": "string", "enum": ["notification", "alarm"]}"""

        /** The tools the agent can call, in the JSON form the model's chat template writes into its prompt. */
        val TOOLS_JSON = """
        [
          {"type": "function", "function": {"name": "create_task",
            "description": "Adds a task the user wants to be reminded of.",
            "parameters": {"type": "object", "properties": {$TASK_PROPERTIES},
              "required": ["title"]}}},
          {"type": "function", "function": {"name": "update_task",
            "description": "Changes a task from the Tasks list. Give its id and only the properties that change, written as in create_task.",
            "parameters": {"type": "object", "properties": {
              "id": {"type": "integer"}, "title": {"type": "string"}, "when": {"type": "string"},
              "repeat": {"type": "string"}, "warn_before": {"type": "string"}, "style": {"type": "string"}},
              "required": ["id"]}}},
          {"type": "function", "function": {"name": "delete_task",
            "description": "Removes a task from the Tasks list.",
            "parameters": {"type": "object", "properties": {"id": {"type": "integer"}}, "required": ["id"]}}},
          {"type": "function", "function": {"name": "complete_task",
            "description": "Marks a task from the Tasks list as done.",
            "parameters": {"type": "object", "properties": {"id": {"type": "integer"}}, "required": ["id"]}}}
        ]
        """.trimIndent()

        private val SYSTEM = """
        You are the tool-calling part of Remind Me, a reminders and alarms app on the user's phone. Whatever the user types is something they want set, changed or removed. You never talk to the user and never ask a question: your whole reply is tool calls and nothing else. You decide every detail of a task yourself.

        Rules:
        - Read the message for what the user wants, not for its wording. A statement is a request: "I wake up every morning at 8" is an alarm titled "Wake up", daily at 08:00; "meeting with the bank Friday 3pm" is a reminder for that meeting; "dentist tomorrow" is a reminder tomorrow.
        - One create_task for each thing to be reminded of. Write every call the message needs in this one reply.
        - title is the thing itself in a few words, starting with a capital letter, without "remind me to".
        - when is the user's own words for when it happens, copied from the message as they typed them: "next tuesday at 2 pm", "5 30 tomorrow", "in 20 minutes". Never work out a date or a time yourself; the app does that. Leave when out if the message says nothing about when.
        - repeat is the user's own words for how it comes round, end included, copied from the message: "every sunday and wednesday", "weekdays", "every other day", "every monday for 3 weeks", "daily until friday". Leave repeat out unless the message says it comes round again. when is then the first time it happens.
        - warn_before is how long before due the user gets an early warning. Use what the user says ("30 minutes before" is 30). If they do not say, you choose: 0 for waking up and for small things done on the spot, 10 to 60 for things to travel to or get ready for, 1440 for birthdays and deadlines.
        - style is alarm when the user says alarm, wake up, or that they must not miss it. Otherwise leave it out.
        - To change, move, cancel or finish something that is already in Tasks, call update_task, delete_task or complete_task with its id. Do not create it again.
        - If the message names nothing that could be a reminder, reply with the one word NONE.
        - The tasks and the conversation so far come after these rules. "it" and "that" mean the task the conversation was last about.
        """.trimIndent()
    }
}
