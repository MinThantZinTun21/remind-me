package app.remindme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.remindme.core.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate

/** The ways of repeating the form offers. "Every day" and "Every N days" are one cycle with two faces. */
enum class RepeatChoice(val label: String) {
    NONE("Does not repeat"),
    DAILY("Every day"),
    WEEKDAYS("On chosen weekdays"),
    EVERY_DAYS("Every few days"),
    EVERY_WEEKS("Every week or few weeks"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
}

/** What the owner has picked under "Repeats", kept as typed so a half-filled number is not lost. */
data class RepeatEntry(
    val choice: RepeatChoice = RepeatChoice.NONE,
    val every: String = "",
    val weekdays: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
) {
    /** A number left empty becomes 0, which the tools refuse in words the form shows. */
    fun rule(): RepeatRule {
        val count = every.toIntOrNull() ?: 0
        return when (choice) {
            RepeatChoice.NONE -> RepeatRule.ONCE
            RepeatChoice.DAILY -> RepeatRule.daily(until)
            RepeatChoice.WEEKDAYS -> RepeatRule.weekdays(weekdays, until)
            RepeatChoice.EVERY_DAYS -> RepeatRule.everyDays(count, until)
            RepeatChoice.EVERY_WEEKS -> RepeatRule.everyWeeks(count, until)
            RepeatChoice.MONTHLY -> RepeatRule.monthly(until)
            RepeatChoice.YEARLY -> RepeatRule.yearly(until)
        }
    }

    companion object {
        fun of(rule: RepeatRule) = RepeatEntry(
            choice = when (rule.cycle) {
                RepeatRule.Cycle.ONCE -> RepeatChoice.NONE
                RepeatRule.Cycle.DAYS -> if (rule.every == 1) RepeatChoice.DAILY else RepeatChoice.EVERY_DAYS
                RepeatRule.Cycle.WEEKDAYS -> RepeatChoice.WEEKDAYS
                RepeatRule.Cycle.WEEKS -> RepeatChoice.EVERY_WEEKS
                RepeatRule.Cycle.MONTHLY -> RepeatChoice.MONTHLY
                RepeatRule.Cycle.YEARLY -> RepeatChoice.YEARLY
            },
            every = rule.every.toString(),
            weekdays = rule.weekdays,
            until = rule.until,
        )
    }
}

/** How a task repeats: the cycle, the extra the cycle needs, and an optional last day. [date] is the due date in the form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatField(entry: RepeatEntry, date: LocalDate, onChange: (RepeatEntry) -> Unit, onPickEndDate: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Repeats", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text(entry.choice.label) }
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                for (choice in RepeatChoice.entries) {
                    DropdownMenuItem(
                        text = { Text(choice.label) },
                        onClick = {
                            choosing = false
                            onChange(
                                entry.copy(
                                    choice = choice,
                                    every = when (choice) {
                                        RepeatChoice.EVERY_DAYS -> "2"
                                        else -> "1"
                                    },
                                    until = entry.until.takeIf { choice != RepeatChoice.NONE },
                                ),
                            )
                        },
                    )
                }
            }
        }
        when (entry.choice) {
            RepeatChoice.WEEKDAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (day in DayOfWeek.entries) {
                    val selected = day in entry.weekdays
                    FilterChip(
                        selected = selected,
                        onClick = { onChange(entry.copy(weekdays = if (selected) entry.weekdays - day else entry.weekdays + day)) },
                        label = { Text(day.short()) },
                    )
                }
            }
            RepeatChoice.EVERY_DAYS, RepeatChoice.EVERY_WEEKS -> OutlinedTextField(
                value = entry.every,
                onValueChange = { typed -> onChange(entry.copy(every = typed.filter(Char::isDigit).take(3))) },
                label = { Text(if (entry.choice == RepeatChoice.EVERY_DAYS) "Every how many days" else "Every how many weeks") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Unit
        }
        val note = when (entry.choice) {
            RepeatChoice.NONE -> "Once, at the due time."
            RepeatChoice.DAILY -> "Every day at this time."
            RepeatChoice.WEEKDAYS -> "On the days you pick, at this time."
            RepeatChoice.EVERY_DAYS -> "Counted from the due date."
            RepeatChoice.EVERY_WEEKS -> "On ${date.dayOfWeek.short()}, the weekday of the due date."
            RepeatChoice.MONTHLY ->
                if (date.dayOfMonth > 28) "On day ${date.dayOfMonth}, or the last day of a shorter month." else "On day ${date.dayOfMonth} of each month."
            RepeatChoice.YEARLY ->
                if (date.monthValue == 2 && date.dayOfMonth == 29) "On 29 Feb, or 28 Feb in other years." else "On this date each year."
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entry.choice != RepeatChoice.NONE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickEndDate) {
                    Text(entry.until?.let { "Last day: ${it.inFull()}" } ?: "No end date")
                }
                if (entry.until != null) TextButton(onClick = { onChange(entry.copy(until = null)) }) { Text("Remove") }
            }
        }
    }
}
