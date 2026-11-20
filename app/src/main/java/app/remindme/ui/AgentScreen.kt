package app.remindme.ui

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.remindme.RemindMeApp
import app.remindme.agent.ActionCard
import app.remindme.agent.AgentEngine
import app.remindme.agent.ContextRecord
import app.remindme.agent.Orchestrator
import app.remindme.core.DayRow
import app.remindme.core.DeliveryStyle
import app.remindme.core.Task
import app.remindme.core.ToolResult
import app.remindme.core.dayRow
import app.remindme.wording.Times
import app.remindme.wording.short
import java.time.DayOfWeek
import app.remindme.wording.inWords
import app.remindme.wording.label
import app.remindme.wording.leadTimesInWords

/** Sentences to try with one tap. Each is one the agent on this phone has been checked on. */
internal val EXAMPLES = listOf(
    "I wake up every morning at 6",
    "Call mom tomorrow at 9",
    "Meeting with the bank Friday 3 pm, warn me 30 minutes before",
)

/** One message the user sent and what came of it, as it stands on screen. */
private class ExchangeState(val text: String) {
    var writing by mutableStateOf("")
    var cards by mutableStateOf<List<CardState>>(emptyList())
    var problem by mutableStateOf<String?>(null)
    var timing by mutableStateOf<String?>(null)
    var context by mutableStateOf<ContextRecord?>(null)
    var finished by mutableStateOf(false)
}

/**
 * An action card as it stands on screen: the task as the day buttons have left it, and each day change,
 * newest last, as the task was before it, so Undo can take them back one at a time.
 */
private class CardState(val card: ActionCard) {
    var task by mutableStateOf(card.task)
    val dayChanges = mutableStateListOf<Task>()
    var undone by mutableStateOf(false)
    var note by mutableStateOf<String?>(null)
}

private enum class ModelState { MISSING, LOADING, READY, WORKING, FAILED }

/**
 * Where the user types what they want to be reminded of. The agent acts at once; each thing it did is
 * shown on an action card with Undo and Edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(app: RemindMeApp, draft: String = "", onEditTask: (Long) -> Unit, onModels: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val modelFile = remember { AgentEngine.modelFile(context) }
    val engine = remember { modelFile?.let { AgentEngine(it, context.cacheDir) } }
    val orchestrator = remember { engine?.let { Orchestrator(app.agent, app.tools, it) } }
    // The model is asked one thing at a time, off the main thread.
    val worker = remember { Executors.newSingleThreadExecutor { Thread(it, "agent-model").apply { isDaemon = true } } }
    var state by remember { mutableStateOf(if (engine == null) ModelState.MISSING else ModelState.LOADING) }
    var loadNote by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf(draft) }
    val field = remember { FocusRequester() }
    val exchanges = remember { mutableStateListOf<ExchangeState>() }
    val list = rememberLazyListState()

    fun load() {
        val talk = orchestrator ?: return
        // A message sent while the model is still getting ready is already waiting behind this load.
        if (state != ModelState.WORKING) state = ModelState.LOADING
        worker.execute {
            val result = runCatching { talk.prepare() }
            main.post {
                result.onSuccess {
                    loadNote = null
                    if (state == ModelState.LOADING) state = ModelState.READY
                }.onFailure {
                    loadNote = "The model did not load: ${it.message}"
                    if (state == ModelState.LOADING) state = ModelState.FAILED
                }
            }
        }
    }

    fun send() {
        val talk = orchestrator ?: return
        val text = typed.trim()
        if (text.isEmpty() || state !in CAN_SEND) return
        val exchange = ExchangeState(text)
        exchanges += exchange
        typed = ""
        state = ModelState.WORKING
        worker.execute {
            val done = talk.send(text) { so -> main.post { exchange.writing = so } }
            main.post {
                exchange.cards = done.cards.map(::CardState)
                exchange.problem = done.problem
                exchange.timing = done.reply?.let { "Took ${seconds(it.totalMs)} (first word after ${seconds(it.firstTokenMs)})." }
                exchange.context = done.context
                exchange.finished = true
                state = ModelState.LOADING
                load()
            }
        }
    }

    LaunchedEffect(Unit) {
        load()
        // The screen is for typing: the keyboard is up as soon as it opens.
        if (engine != null) runCatching { field.requestFocus() }
    }
    DisposableEffect(Unit) {
        onDispose {
            // Leaving the screen ends the visit, its conversation and the model.
            worker.execute { orchestrator?.endVisit(); engine?.close() }
            worker.shutdown()
        }
    }
    LaunchedEffect(exchanges.size, exchanges.lastOrNull()?.finished) {
        if (exchanges.isNotEmpty()) list.animateScrollToItem(exchanges.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tell Remind Me")
                        Text(
                            when (state) {
                                ModelState.MISSING -> "No model chosen"
                                ModelState.LOADING -> "Getting ready. You can already send."
                                ModelState.READY -> "Ready"
                                ModelState.WORKING -> "Working…"
                                ModelState.FAILED -> "The model did not load"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state == ModelState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { BackButton(onClose) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                if (exchanges.isEmpty()) {
                    item {
                        if (state == ModelState.MISSING) {
                            Column {
                                Text("No model, choose one", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "The agent needs a model on this phone. Download one, or choose one you already have, on the Models screen.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                                )
                                OutlinedButton(onClick = onModels) { Text("Open Models") }
                            }
                        } else {
                            Opening(onTry = { typed = it })
                        }
                    }
                }
                items(exchanges) { exchange ->
                    ExchangeView(
                        exchange,
                        onChange = { card, change ->
                            val result = when (change) {
                                CardChange.Undo -> {
                                    val before = card.dayChanges.lastOrNull()
                                    if (before == null) {
                                        orchestrator?.undo(card.card)
                                        card.undone = true
                                        null
                                    } else {
                                        app.dayButtons.undo(before).also { if (it is ToolResult.Ok) card.dayChanges.removeAt(card.dayChanges.lastIndex) }
                                    }
                                }
                                is CardChange.Day -> card.task?.let { task -> app.dayButtons.tap(task, change.day).also { if (it is ToolResult.Ok) card.dayChanges += task } }
                                CardChange.RepeatWeekly -> card.task?.let { task -> app.dayButtons.repeatWeekly(task).also { if (it is ToolResult.Ok) card.dayChanges += task } }
                            }
                            when (result) {
                                is ToolResult.Ok -> { card.task = result.task; card.note = null }
                                is ToolResult.Refused -> card.note = result.reason
                                null -> card.note = null
                            }
                            // The task list changed: the waiting model has to read it again.
                            if (result !is ToolResult.Refused && (state == ModelState.READY || state == ModelState.LOADING)) load()
                        },
                        onEditTask = onEditTask,
                    )
                }
                if (state == ModelState.FAILED) {
                    item { Problem(loadNote ?: "The model did not load.") }
                }
            }
            if (state == ModelState.WORKING) LinearProgressIndicator(Modifier.fillMaxWidth())
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TextField(
                        value = typed,
                        onValueChange = { typed = it },
                        placeholder = { Text("What should I remind you of?") },
                        enabled = engine != null,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        maxLines = 4,
                        modifier = Modifier.weight(1f).focusRequester(field),
                    )
                    FilledIconButton(
                        onClick = ::send,
                        enabled = state in CAN_SEND && typed.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp).size(56.dp),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                }
            }
        }
    }
}

/** What the screen says before the first message: what to do, and sentences to try with one tap. */
@Composable
private fun Opening(onTry: (String) -> Unit) {
    Column {
        Text("What should I remind you of?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Say it the way you would say it to a person. It is set at once, and you can undo or edit it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Examples(onPick = onTry)
    }
}

/** The sentences to try, one under the other. A long one wraps onto a second line. */
@Composable
internal fun Examples(onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (example in EXAMPLES) {
            Surface(
                onClick = { onPick(example) },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    example,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ExchangeView(exchange: ExchangeState, onChange: (CardState, CardChange) -> Unit, onEditTask: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // What the user said, on the right, as in a conversation.
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp),
            modifier = Modifier.align(Alignment.End).widthIn(max = 300.dp),
        ) {
            Text(
                exchange.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (!exchange.finished) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "Working it out…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            return@Column
        }
        for (card in exchange.cards) {
            ActionCardView(card, onChange = { onChange(card, it) }, onEditTask = onEditTask)
        }
        exchange.problem?.let { Problem(it) }
        exchange.timing?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        exchange.context?.let { ContextView(it) }
    }
}

/** What the user can do to an action card besides Edit. */
private sealed interface CardChange {
    /** Takes back the newest day change, or what the agent did when there is none. */
    data object Undo : CardChange
    data class Day(val day: DayOfWeek) : CardChange
    data object RepeatWeekly : CardChange
}

/** One thing the agent did, with every property it decided, so the user can check each one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCardView(state: CardState, onChange: (CardChange) -> Unit, onEditTask: (Long) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val card = state.card
    val undone = state.undone
    val accent = if (card.went) colors.primary else colors.error
    Card(
        colors = CardDefaults.cardColors(containerColor = if (card.went) colors.surfaceContainerHigh else colors.errorContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (undone) 0.6f else 1f),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (card.went && !undone) 4.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(card.icon(), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Text(card.heading(undone), style = MaterialTheme.typography.labelLarge, color = accent, modifier = Modifier.padding(start = 8.dp))
            }
            state.task?.let { task ->
                Text(task.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                Text(Times.dateAndTime(context, task.due), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    for (fact in task.facts()) Fact(fact)
                }
                if (card.went && !undone && card.action in EDITABLE) DayControls(task, onChange)
            }
            card.refusal?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer, modifier = Modifier.padding(top = 8.dp))
            }
            state.note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (card.went && !undone) {
                Row(Modifier.align(Alignment.End).padding(top = 4.dp)) {
                    TextButton(onClick = { onChange(CardChange.Undo) }) { Text(if (state.dayChanges.isEmpty()) "Undo" else "Undo day change") }
                    val task = state.task
                    if (task != null && card.action in EDITABLE) TextButton(onClick = { onEditTask(task.id) }) { Text("Edit") }
                }
            }
        }
    }
}

/**
 * The day buttons under a task the agent set or changed: its days, for a task that repeats on weekdays or
 * every day; the day it falls on, for a one-time task, with a way to make it weekly.
 */
@Composable
private fun DayControls(task: Task, onChange: (CardChange) -> Unit) {
    val row = task.dayRow() ?: return
    val label = when (row) {
        is DayRow.RepeatsOn -> "Repeats on"
        is DayRow.MoveTo -> "Move to"
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
    DayButtonRow(
        selected = when (row) {
            is DayRow.RepeatsOn -> row.days
            is DayRow.MoveTo -> setOf(row.day)
        },
        onTap = { onChange(CardChange.Day(it)) },
        modifier = Modifier.padding(top = 6.dp),
    )
    if (row is DayRow.MoveTo) {
        OutlinedButton(onClick = { onChange(CardChange.RepeatWeekly) }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Repeat every ${row.day.short()}")
        }
    }
}

@Composable
private fun Fact(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Something that went wrong, in words that say what to do next. */
@Composable
private fun Problem(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 8.dp))
    }
}

private val CAN_SEND = setOf(ModelState.READY, ModelState.LOADING)
private val EDITABLE = setOf(ActionCard.Action.CREATE, ActionCard.Action.UPDATE)

private fun ActionCard.icon(): ImageVector = when {
    !went -> Icons.Filled.Warning
    action == ActionCard.Action.UPDATE -> Icons.Filled.Edit
    action == ActionCard.Action.DELETE -> Icons.Filled.Delete
    action == ActionCard.Action.COMPLETE -> Icons.Filled.Check
    else -> Icons.Filled.Notifications
}

private fun ActionCard.heading(undone: Boolean): String {
    val did = when (action) {
        ActionCard.Action.CREATE -> when {
            !went -> "Could not set this"
            task?.style == DeliveryStyle.ALARM -> "Alarm set"
            else -> "Reminder set"
        }
        ActionCard.Action.UPDATE -> if (went) "Changed" else "Could not change this"
        ActionCard.Action.DELETE -> if (went) "Deleted" else "Could not delete this"
        ActionCard.Action.COMPLETE -> if (went) "Marked done" else "Could not mark this done"
        ActionCard.Action.UNKNOWN -> "Did nothing"
    }
    return if (undone) "$did, then undone" else did
}

/** The properties the agent decided besides the due time: repeat rule, lead times, delivery style. */
private fun Task.facts(): List<String> = listOf(
    repeat.inWords(firstDue) ?: "Once",
    leadTimesInWords()?.let { "Warns $it" } ?: "No early warning",
    style.label,
)

private fun seconds(ms: Long): String = "%.1f s".format(ms / 1000.0)
