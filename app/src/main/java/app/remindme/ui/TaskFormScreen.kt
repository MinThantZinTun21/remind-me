package app.remindme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import android.text.format.DateFormat
import app.remindme.RemindMeApp
import app.remindme.core.DeliveryStyle
import app.remindme.core.RepeatRule
import app.remindme.wording.Times
import app.remindme.wording.inWords
import app.remindme.wording.label
import app.remindme.core.TaskDetails
import app.remindme.core.ToolResult
import app.remindme.core.anchorFor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** The manual form: the user's way to create a task by hand, or to change the one with [taskId]. */
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
    var style by remember(taskId) { mutableStateOf(editing?.style ?: DeliveryStyle.NOTIFICATION) }
    var leadTimes by remember(taskId) { mutableStateOf(editing?.leadTimes ?: emptyList()) }
    var repeat by remember(taskId) { mutableStateOf(RepeatEntry.of(editing?.repeat ?: RepeatRule.ONCE)) }
    var pickingLeadTime by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val details = TaskDetails(
        title = title,
        due = LocalDateTime.of(date, time),
        style = style,
        leadTimes = leadTimes,
        repeat = repeat.rule(),
    )

    fun save() {
        when (val result = if (editing == null) app.tools.create(details) else app.tools.update(editing.id, details)) {
            is ToolResult.Ok -> onClose()
            is ToolResult.Refused -> refusal = result.reason
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "New task" else "Edit task") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Cancel") } },
                // Save sits in the bar so the keyboard never covers it.
                actions = { Button(onClick = ::save, modifier = Modifier.padding(end = 12.dp)) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("At the due time", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DeliveryStyle.entries.forEachIndexed { index, choice ->
                        SegmentedButton(
                            selected = style == choice,
                            onClick = { style = choice },
                            shape = SegmentedButtonDefaults.itemShape(index, DeliveryStyle.entries.size),
                        ) { Text(choice.label) }
                    }
                }
                Text(
                    if (style == DeliveryStyle.ALARM) {
                        "Rings full-screen until you answer, even on silent."
                    } else {
                        "A normal notification with sound."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LeadTimesField(
                chosen = leadTimes,
                shorterThan = details.repeat.shortestGap(),
                onChange = { leadTimes = it; refusal = null },
                onCustom = { pickingLeadTime = true },
            )
            RepeatField(
                entry = repeat,
                // A task whose days are not being moved still counts from the date it was first given.
                countedFrom = (editing?.anchorFor(details) ?: details.due).toLocalDate(),
                onChange = { repeat = it; refusal = null },
                onPickEndDate = { pickingEndDate = true },
            )
        }
    }

    if (pickingLeadTime) {
        CustomLeadTimeDialog(
            onPicked = { picked ->
                if (picked !in leadTimes) leadTimes = leadTimes + picked
                refusal = null
                pickingLeadTime = false
            },
            onDismiss = { pickingLeadTime = false },
        )
    }

    if (pickingDate) {
        PickDateDialog(
            start = date,
            onPicked = { date = it; refusal = null; pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }

    if (pickingEndDate) {
        PickDateDialog(
            start = repeat.until ?: date,
            onPicked = { repeat = repeat.copy(until = it); refusal = null; pickingEndDate = false },
            onDismiss = { pickingEndDate = false },
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(start: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

private val presetLeadTimes = listOf(
    Duration.ofMinutes(5),
    Duration.ofMinutes(15),
    Duration.ofMinutes(30),
    Duration.ofHours(1),
    Duration.ofDays(1),
)

/**
 * None, one or two early warnings: the presets, plus any custom amount already chosen. A lead time has to be
 * [shorterThan] the time between the task's occurrences, so the longer presets cannot be picked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeadTimesField(chosen: List<Duration>, shorterThan: Duration?, onChange: (List<Duration>) -> Unit, onCustom: () -> Unit) {
    val full = chosen.size >= 2
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Warn me before", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (leadTime in (presetLeadTimes + chosen).distinct().sorted()) {
                val selected = leadTime in chosen
                FilterChip(
                    selected = selected,
                    enabled = selected || (!full && (shorterThan == null || leadTime < shorterThan)),
                    onClick = { onChange(if (selected) chosen - leadTime else chosen + leadTime) },
                    label = { Text(leadTime.inWords()) },
                )
            }
            AssistChip(onClick = onCustom, enabled = !full, label = { Text("Custom…") })
        }
        Text(
            when (chosen.size) {
                0 -> "No early warning. Pick up to two."
                1 -> "One early warning. You can add one more."
                else -> "Two early warnings, the most a task can have."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class LeadTimeUnit(val label: String, val of: (Long) -> Duration) {
    MINUTES("Minutes", Duration::ofMinutes),
    HOURS("Hours", Duration::ofHours),
    DAYS("Days", Duration::ofDays),
}

@Composable
private fun CustomLeadTimeDialog(onPicked: (Duration) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(LeadTimeUnit.MINUTES) }
    val picked = amount.toLongOrNull()?.takeIf { it in 1..999 }?.let(unit.of)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warn me before") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { typed -> amount = typed.filter(Char::isDigit).take(3) },
                    label = { Text("How long before") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LeadTimeUnit.entries.forEachIndexed { index, choice ->
                        SegmentedButton(
                            selected = unit == choice,
                            onClick = { unit = choice },
                            shape = SegmentedButtonDefaults.itemShape(index, LeadTimeUnit.entries.size),
                        ) { Text(choice.label) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked != null, onClick = { picked?.let(onPicked) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
