package app.remindme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import app.remindme.RemindMeApp
import app.remindme.core.DeliveryStyle
import app.remindme.core.Section
import app.remindme.core.Task
import app.remindme.core.section
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The task list, grouped as Overdue, Today, Tomorrow and Later. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: RemindMeApp, onNewTask: () -> Unit, onEditTask: (Long) -> Unit, onFireLog: () -> Unit) {
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

    /** Shows the Undo bar for something that has already happened; Undo puts [before] back. */
    fun offerUndo(message: String, before: Task?) {
        if (before == null) return
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
                        DropdownMenuItem(text = { Text("Fire log") }, onClick = { menuOpen = false; onFireLog() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(undoBar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTask,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New task") },
            )
        },
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No tasks yet. Tap “New task” to add one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val sections = tasks.groupBy { it.section(now) }
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                for (section in Section.entries) {
                    val inSection = sections[section] ?: continue
                    item(key = section.name) { SectionHeading(section) }
                    items(inSection, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            overdue = section == Section.OVERDUE,
                            due = Times.dateAndTime(context, task.due) + if (task.style == DeliveryStyle.ALARM) "  ·  Alarm" else "",
                            onOpen = { onEditTask(task.id) },
                            onDone = { offerUndo("Marked done", app.tools.done(task.id)) },
                            onDelete = { offerUndo("Task deleted", app.tools.delete(task.id)) },
                        )
                        HorizontalDivider()
                    }
                }
                item { Box(Modifier.padding(bottom = 88.dp)) }
            }
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

@Composable
private fun TaskRow(task: Task, overdue: Boolean, due: String, onOpen: () -> Unit, onDone: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = {
            Checkbox(
                checked = false,
                onCheckedChange = { onDone() },
                modifier = Modifier.semantics { contentDescription = "Mark ${task.title} done" },
            )
        },
        headlineContent = { Text(task.title) },
        supportingContent = {
            Text(due, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${task.title}") }
        },
    )
}
