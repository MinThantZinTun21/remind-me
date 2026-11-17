package app.remindme.ui

import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import app.remindme.agent.ReplyParser
import app.remindme.core.DeliveryStyle
import app.remindme.core.Task
import app.remindme.wording.Times
import app.remindme.wording.inWords
import app.remindme.wording.label
import app.remindme.wording.leadTimesInWords

/** Sentences to try with one tap. Each is one the agent on this phone has been checked on. */
internal val EXAMPLES = listOf(
    "I wake up every morning at 6",
    "Call mom tomorrow at 9",
    "Meeting with the bank Friday 3 pm, warn me 30 minutes before",
)

/** One message the user sent and what came of it. */
private class Exchange(val text: String) {
    var writing by mutableStateOf("")
    var cards by mutableStateOf<List<ActionCard>>(emptyList())
    var undone by mutableStateOf<Set<Int>>(emptySet())
    var problem by mutableStateOf<String?>(null)
    var timing by mutableStateOf<String?>(null)
    var finished by mutableStateOf(false)
}

private enum class ModelState { MISSING, LOADING, READY, WORKING, FAILED }

/**
 * Where the user types what they want to be reminded of. The agent acts at once; each thing it did is
 * shown on an action card with Undo and Edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(app: RemindMeApp, draft: String = "", onEditTask: (Long) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val modelFile = remember { AgentEngine.modelFile(context) }
    val engine = remember { modelFile?.let { AgentEngine(it, context.cacheDir) } }
    var state by remember { mutableStateOf(if (engine == null) ModelState.MISSING else ModelState.LOADING) }
    var loadNote by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf(draft) }
    val field = remember { FocusRequester() }
    val exchanges = remember { mutableStateListOf<Exchange>() }
    val list = rememberLazyListState()

    fun load() {
        val model = engine ?: return
        // A message sent while the model is still getting ready is already waiting behind this load.
        if (state != ModelState.WORKING) state = ModelState.LOADING
        model.load(app.agent.systemInstruction()) { result ->
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
        val model = engine ?: return
        val text = typed.trim()
        if (text.isEmpty() || state !in CAN_SEND) return
        val exchange = Exchange(text)
        exchanges += exchange
        typed = ""
        state = ModelState.WORKING
        model.ask(
            app.agent.systemInstruction(),
            app.agent.userMessage(text),
            object : AgentEngine.Listener {
                override fun onText(text: String) {
                    main.post { exchange.writing = text }
                }

                override fun onDone(reply: AgentEngine.Reply) {
                    main.post {
                        val parsed = ReplyParser.parse(reply.text)
                        exchange.cards = app.agent.act(parsed.calls)
                        // The agent answers with tool calls only; anything else it writes is not shown.
                        exchange.problem = parsed.problem
                            ?: "The agent found nothing to remind you of in that. Say what it is and when.".takeIf { parsed.calls.isEmpty() }
                        exchange.timing = "Took ${seconds(reply.totalMs)} (first word after ${seconds(reply.firstTokenMs)})."
                        exchange.finished = true
                        state = ModelState.LOADING
                        load()
                    }
                }

                override fun onError(message: String) {
                    main.post {
                        exchange.problem = message
                        exchange.finished = true
                        state = ModelState.LOADING
                        load()
                    }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        load()
        // The screen is for typing: the keyboard is up as soon as it opens.
        if (engine != null) runCatching { field.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose { engine?.close() } }
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
                                ModelState.MISSING -> "No model on this phone"
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
                            Text(
                                "There is no model on this phone yet. Copy a .litertlm model file into " +
                                    "${AgentEngine.modelFolder(context)?.path} and open this screen again.",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        } else {
                            Opening(onTry = { typed = it })
                        }
                    }
                }
                items(exchanges) { exchange ->
                    ExchangeView(
                        exchange,
                        onUndo = { index ->
                            app.agent.undo(exchange.cards[index])
                            exchange.undone = exchange.undone + index
                            // The task list changed: the waiting model has to read it again.
                            if (state == ModelState.READY || state == ModelState.LOADING) load()
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
        for (example in EXAMPLES) SuggestionChip(onClick = { onTry(example) }, label = { Text(example) })
    }
}

@Composable
private fun ExchangeView(exchange: Exchange, onUndo: (Int) -> Unit, onEditTask: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        exchange.cards.forEachIndexed { index, card ->
            ActionCardView(card, undone = index in exchange.undone, onUndo = { onUndo(index) }, onEditTask = onEditTask)
        }
        exchange.problem?.let { Problem(it) }
        exchange.timing?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One thing the agent did, with every property it decided, so the user can check each one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCardView(card: ActionCard, undone: Boolean, onUndo: () -> Unit, onEditTask: (Long) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
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
            card.task?.let { task ->
                Text(task.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                Text(Times.dateAndTime(context, task.due), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    for (fact in task.facts()) Fact(fact)
                }
            }
            card.refusal?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer, modifier = Modifier.padding(top = 8.dp))
            }
            if (card.went && !undone) {
                Row(Modifier.align(Alignment.End).padding(top = 4.dp)) {
                    TextButton(onClick = onUndo) { Text("Undo") }
                    val task = card.task
                    if (task != null && card.action in EDITABLE) TextButton(onClick = { onEditTask(task.id) }) { Text("Edit") }
                }
            }
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
