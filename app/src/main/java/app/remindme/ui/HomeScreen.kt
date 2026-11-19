package app.remindme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.remindme.RemindMeApp
import app.remindme.wording.Times
import app.remindme.wording.label
import app.remindme.wording.inWords
import app.remindme.wording.leadTimesInWords
import app.remindme.core.Section
import app.remindme.core.Task
import app.remindme.core.ToolResult
import app.remindme.core.section
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The task list, grouped as Overdue, Today, Tomorrow and Later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: RemindMeApp,
    onNewTask: () -> Unit,
    onAgent: (draft: String) -> Unit,
    onEditTask: (Long) -> Unit,
    notificationsOff: Boolean,
    onHistory: () -> Unit,
    onSetUp: () -> Unit,
    onFireLog: () -> Unit,
    onModels: () -> Unit,
) {
    val context = LocalContext.current
    val tasks = remember(app.revision.intValue) { app.tools.list() }
    // The grouping moves with the clock, so it is looked at again while the list is on screen.
    val now by produceState(LocalDateTime.now(), app.revision.intValue) {
        while (true) {
            value = LocalDateTime.now()
            delay(15_000)
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    val undoBar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    /** Shows the Undo bar once a tool has changed a task; Undo puts the task back as it was [before]. */
    fun offerUndo(message: String, before: Task, result: ToolResult) {
        if (result !is ToolResult.Ok) return
        scope.launch {
            undoBar.currentSnackbarData?.dismiss()
            val answer = undoBar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (answer == SnackbarResult.ActionPerformed) app.tools.restore(before)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remind Me") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("History") }, onClick = { menuOpen = false; onHistory() })
                        DropdownMenuItem(text = { Text("Models") }, onClick = { menuOpen = false; onModels() })
                        DropdownMenuItem(text = { Text("Set-up") }, onClick = { menuOpen = false; onSetUp() })
                        DropdownMenuItem(text = { Text("Fire log") }, onClick = { menuOpen = false; onFireLog() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(undoBar) },
        // The agent is the main way in, so it sits where the thumb is; the form is the by-hand way beside it.
        bottomBar = { TellBar(onAgent = { onAgent("") }, onNewTask = onNewTask) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (notificationsOff) NotificationsOffWarning(onSetUp)
            if (tasks.isEmpty()) {
                NothingYet(onAgent)
            } else {
                val sections = tasks.groupBy { it.section(now) }
                LazyColumn(Modifier.fillMaxSize()) {
                    for (section in Section.entries) {
                        val inSection = sections[section] ?: continue
                        item(key = section.name) { SectionHeading(section) }
                        items(inSection, key = { it.id }) { task ->
                            TaskLine(
                                task = task,
                                overdue = section == Section.OVERDUE,
                                // Under Today and Tomorrow the heading already says the day.
                                due = if (section == Section.TODAY || section == Section.TOMORROW) {
                                    Times.time(context, task.due)
                                } else {
                                    Times.dateAndTime(context, task.due)
                                },
                                details = listOfNotNull(
                                    task.style.label,
                                    task.repeat.inWords(task.firstDue),
                                    task.leadTimesInWords(),
                                ),
                                onOpen = { onEditTask(task.id) },
                                onDone = { offerUndo("Marked done", task, app.tools.done(task.id)) },
                                onDelete = { offerUndo("Task deleted", task, app.tools.delete(task.id)) },
                            )
                        }
                    }
                    item { Box(Modifier.padding(bottom = 16.dp)) }
                }
            }
        }
    }
}

/** Shown for as long as the phone will not let the app notify: nothing it fires would be seen or heard. */
@Composable
private fun NotificationsOffWarning(onSetUp: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notifications are off, so Remind Me cannot tell you when a task is due.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSetUp) { Text("Open Set-up") }
        }
    }
}

@Composable
private fun SectionHeading(section: Section) {
    val overdue = section == Section.OVERDUE
    Text(
        text = when (section) {
            Section.OVERDUE -> "Overdue"
            Section.TODAY -> "Today"
            Section.TOMORROW -> "Tomorrow"
            Section.LATER -> "Later"
        },
        style = MaterialTheme.typography.titleSmall,
        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** The due time leads the second line, because when is what the user looks a task up for. */
@Composable
private fun TaskLine(
    task: Task,
    overdue: Boolean,
    due: String,
    details: List<String>,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val dueColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = {
            Checkbox(
                checked = false,
                onCheckedChange = { onDone() },
                modifier = Modifier.semantics { contentDescription = "Mark ${task.title} done" },
            )
        },
        headlineContent = { Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = dueColor, fontWeight = FontWeight.Medium)) { append(due) }
                    for (detail in details) append("  ·  $detail")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${task.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/** The first thing a new user sees: what to do, and sentences to try with one tap. */
@Composable
private fun NothingYet(onAgent: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Text("Nothing to remember yet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Tell Remind Me in your own words. It works out the time, the repeat and whether it is an alarm.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Examples(onPick = onAgent)
    }
}

/** The way in to the agent, docked at the bottom, with the by-hand form beside it. */
@Composable
private fun TellBar(onAgent: () -> Unit, onNewTask: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onAgent,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Create, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Tell Remind Me…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            FilledTonalIconButton(onClick = onNewTask, modifier = Modifier.padding(start = 12.dp).size(56.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "New task by hand")
            }
        }
    }
}
