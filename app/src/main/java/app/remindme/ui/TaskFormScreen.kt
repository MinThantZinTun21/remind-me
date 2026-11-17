package app.remindme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import android.text.format.DateFormat
import app.remindme.RemindMeApp
import app.remindme.core.TaskDraft
import app.remindme.core.ToolResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** The manual form: the owner's way to create a task by hand, or to change the one with [taskId]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFormScreen(app: RemindMeApp, taskId: Long?, onClose: () -> Unit) {
    val context = LocalContext.current
    val editing = remember(taskId) { taskId?.let { id -> app.tools.list().firstOrNull { it.id == id } } }
    if (taskId != null && editing == null) {
        // The task was done or deleted meanwhile, for instance from its notification.
        LaunchedEffect(taskId) { onClose() }
        return
    }
    val start = remember(taskId) { editing?.due ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0) }
    var title by remember(taskId) { mutableStateOf(editing?.title ?: "") }
    var date by remember(taskId) { mutableStateOf(start.toLocalDate()) }
    var time by remember(taskId) { mutableStateOf(start.toLocalTime()) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "New task" else "Edit task") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Cancel") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; refusal = null },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) { Text(Times.date(date)) }
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.weight(1f)) { Text(Times.time(context, time)) }
            }
            refusal?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val draft = TaskDraft(title = title, due = LocalDateTime.of(date, time))
                    when (val result = if (editing == null) app.tools.create(draft) else app.tools.update(editing.id, draft)) {
                        is ToolResult.Ok -> onClose()
                        is ToolResult.Refused -> refusal = result.reason
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate() }
                    refusal = null
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    refusal = null
                    pickingTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}
