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
     * What the model is told before the user's text: today, the coming days, the tasks and the rules.
     * It holds no time of day, so the model can read it while the user is still typing; the time comes
     * with the message, in [userMessage].
     */
    fun systemInstruction(): String = rules() + "\n\n" + taskList(tools.list().take(MAX_TASKS))

    /** The part of the instruction that is the same whatever tasks and conversation come with it. */
    fun rules(): String {
        val today = clock.now().toLocalDate()
        val days = (1..COMING_DAYS).joinToString(", ") { ahead ->
            today.plusDays(ahead.toLong()).format(DAY) + if (ahead == 1) " (tomorrow)" else ""
        }
        return SYSTEM
            .replace("{today}", today.format(TODAY))
            .replace("{days}", days)
    }

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
     * task the model was shown, or one it created in this reply.
     */
    fun act(calls: List<ToolCall>, shown: Set<Long>? = null): List<ActionCard> {
        val visible = shown?.toMutableSet()
        return calls.map { call ->
            act(call, visible).also { card -> if (card.action == ActionCard.Action.CREATE) card.task?.let { visible?.add(it.id) } }
        }
    }

    /** Takes back what a card reports. */
    fun undo(card: ActionCard): ToolResult? = when (val undo = card.undo) {
        is Undo.Remove -> tools.delete(undo.taskId)
        is Undo.PutBack -> tools.restore(undo.task)
        null -> null
    }

    private fun act(call: ToolCall, visible: Set<Long>?): ActionCard = when (call.name) {
        "create_task" -> create(call)
        "update_task" -> onExisting(call, visible, ActionCard.Action.UPDATE) { before -> update(call, before) }
        "delete_task" -> onExisting(call, visible, ActionCard.Action.DELETE) { before -> tools.delete(before.id) }
        "complete_task" -> onExisting(call, visible, ActionCard.Action.COMPLETE) { before -> tools.done(before.id) }
        else -> ActionCard(call, ActionCard.Action.UNKNOWN, null, "The app has no tool called \"${call.name}\".", null)
    }

    private fun create(call: ToolCall): ActionCard {
        val action = ActionCard.Action.CREATE
        val title = call.args["title"].orEmpty()
        val dueText = call.args["due"] ?: return ActionCard(call, action, null, "The agent gave no due time.", null)
        val due = dueTime(dueText) ?: return ActionCard(call, action, null, "The app cannot read the due time \"$dueText\".", null)
        val details = try {
            TaskDetails(
                title = title,
                due = due,
                style = style(call.args["style"]) ?: DeliveryStyle.NOTIFICATION,
                leadTimes = leadTimes(call.args["warn_before"]).orEmpty(),
                repeat = repeat(call.args) ?: RepeatRule.ONCE,
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
    private fun update(call: ToolCall, before: Task): ToolResult {
        val args = call.args
        val due = args["due"]?.let { dueTime(it) ?: throw Unreadable("The app cannot read the due time \"$it\".") }
        val newRule = repeat(args)
        val until = args["repeat_until"]?.let(::day)
        val asItWas = TaskDetails(before.title, before.due, before.style, before.leadTimes, before.repeat)
        val wanted = asItWas.copy(
            title = args["title"]?.takeIf { it.isNotBlank() } ?: before.title,
            due = due ?: before.due,
            style = style(args["style"]) ?: before.style,
            leadTimes = leadTimes(args["warn_before"]) ?: before.leadTimes,
            repeat = newRule ?: if (until != null) before.repeat.copy(until = until) else before.repeat,
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

    /** `YYYY-MM-DDTHH:MM`, with a space for the `T` or seconds on the end if the agent writes them. */
    private fun dueTime(text: String): LocalDateTime? {
        val match = DUE.find(text.trim()) ?: return null
        return runCatching {
            LocalDate.parse(match.groupValues[1]).atTime(match.groupValues[2].toInt(), match.groupValues[3].toInt())
        }.getOrNull()
    }

    private fun day(text: String): LocalDate =
        runCatching { LocalDate.parse(text.trim().take(10)) }.getOrNull() ?: throw Unreadable("The app cannot read the day \"$text\".")

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

    /** The repeat rule the call asks for; null when the call says nothing about repeating. */
    private fun repeat(args: Map<String, String>): RepeatRule? {
        val kind = args["repeat"]?.trim()?.lowercase() ?: return null
        val every = args["repeat_every"]?.let { it.trim().toIntOrNull() ?: throw Unreadable("The app cannot read \"every $it\".") } ?: 1
        val days = weekdays(args["repeat_days"])
        val until = args["repeat_until"]?.let(::day)
        return when (kind) {
            "none", "once", "no" -> RepeatRule.ONCE
            "daily" -> RepeatRule.everyDays(every, until)
            // The working week is always Monday to Friday, whatever days the agent writes beside it.
            "weekdays" -> RepeatRule.weekdays(WORKING_WEEK, until)
            "weekly" -> when {
                days.isEmpty() -> RepeatRule.everyWeeks(every, until)
                every == 1 -> RepeatRule.weekdays(days, until)
                else -> throw Unreadable("A task cannot repeat every $every weeks on chosen weekdays.")
            }
            "monthly" -> RepeatRule.monthly(until)
            "yearly" -> RepeatRule.yearly(until)
            else -> throw Unreadable("The app cannot read the repeat rule \"${args["repeat"]}\".")
        }
    }

    private fun weekdays(text: String?): Set<DayOfWeek> =
        text.orEmpty().split(Regex("[^A-Za-z]+")).filter { it.isNotEmpty() }.map { word ->
            DayOfWeek.entries.firstOrNull { it.name.startsWith(word.take(3).uppercase()) && word.length >= 3 }
                ?: throw Unreadable("The app cannot read the weekday \"$word\".")
        }.toSet()

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

        /** Enough days to look up "next Friday" instead of counting to it. */
        private const val COMING_DAYS = 14

        /** More tasks than this do not fit beside the rules in the model's small context. */
        private const val MAX_TASKS = 15

        private val WORKING_WEEK = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        private val DUE = Regex("^(\\d{4}-\\d{2}-\\d{2})[T ](\\d{1,2}):(\\d{2})(?::\\d{2}(?:\\.\\d+)?)?$")
        private val NOW = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH)
        private val TODAY = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH)
        private val DAY = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd", Locale.ENGLISH)
        private val TASK_DUE = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm", Locale.ENGLISH)
        private val SUMMARY_DUE = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH)

        private const val TASK_PROPERTIES = """
          "title": {"type": "string", "description": "What to do, in a few words."},
          "due": {"type": "string", "description": "When it happens, as YYYY-MM-DDTHH:MM in 24-hour time."},
          "repeat": {"type": "string", "enum": ["none", "daily", "weekdays", "weekly", "monthly", "yearly"]},
          "repeat_every": {"type": "integer", "description": "Every how many days or weeks. Leave out for 1."},
          "repeat_days": {"type": "string", "description": "With weekly, the weekdays it falls on, like mon,wed,fri."},
          "repeat_until": {"type": "string", "description": "The last day it repeats, as YYYY-MM-DD."},
          "warn_before": {"type": "string", "description": "Early warnings, in minutes before due: one or two numbers, like 30 or 60,1440. 0 for none."},
          "style": {"type": "string", "enum": ["notification", "alarm"]}"""

        /** The tools the agent can call, in the JSON form the model's chat template writes into its prompt. */
        val TOOLS_JSON = """
        [
          {"type": "function", "function": {"name": "create_task",
            "description": "Adds a task the user wants to be reminded of.",
            "parameters": {"type": "object", "properties": {$TASK_PROPERTIES},
              "required": ["title", "due"]}}},
          {"type": "function", "function": {"name": "update_task",
            "description": "Changes a task from the Tasks list. Give its id and only the properties that change, written as in create_task.",
            "parameters": {"type": "object", "properties": {
              "id": {"type": "integer"}, "title": {"type": "string"}, "due": {"type": "string"},
              "repeat": {"type": "string"}, "repeat_every": {"type": "integer"}, "repeat_days": {"type": "string"},
              "repeat_until": {"type": "string"}, "warn_before": {"type": "string"}, "style": {"type": "string"}},
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
        Today: {today}. Each message starts with the time now.
        Coming days: {days}.

        Rules:
        - Read the message for what the user wants, not for its wording. A statement is a request: "I wake up every morning at 8" is an alarm titled "Wake up", daily at 08:00; "meeting with the bank Friday 3pm" is a reminder for that meeting; "dentist tomorrow" is a reminder tomorrow.
        - One create_task for each thing to be reminded of. Write every call the message needs in this one reply.
        - title is the thing itself in a few words, starting with a capital letter, without "remind me to".
        - due is the moment the thing happens. Take the date from Today and Coming days; never count days yourself. "in 20 minutes" or "in 2 hours" is added to the time now. A time with no day is the next time the clock shows it. A day with no time: you choose (morning 09:00, noon 12:00, afternoon 15:00, evening 19:00, night 21:00). No day and no time: one hour from now.
        - repeat only when the thing comes round again: every day, morning or night is daily; every weekday, every working day or Monday to Friday is weekdays; every Monday is weekly with repeat_days mon; Tuesdays and Thursdays is weekly with repeat_days tue,thu; each month is monthly; birthdays are yearly. due is then the first time it happens.
        - warn_before is how long before due the user gets an early warning. Use what the user says ("30 minutes before" is 30). If they do not say, you choose: 0 for waking up and for small things done on the spot, 10 to 60 for things to travel to or get ready for, 1440 for birthdays and deadlines.
        - style is alarm when the user says alarm, wake up, or that they must not miss it. Otherwise leave it out.
        - To change, move, cancel or finish something that is already in Tasks, call update_task, delete_task or complete_task with its id. Do not create it again.
        - If the message names nothing that could be a reminder, reply with the one word NONE.
        - The tasks and the conversation so far come after these rules. "it" and "that" mean the task the conversation was last about.
        """.trimIndent()
    }
}
