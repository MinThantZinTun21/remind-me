package app.remindme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import app.remindme.wording.short
import app.remindme.wording.inFull
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The ways of repeating the form offers, each with everything the form needs to know about it.
 * "Every day" and "Every few days" are one cycle with two faces.
 */
enum class RepeatChoice(
    val label: String,
    /** What the choice says under the field, given the date the days are counted from. */
    val note: (LocalDate) -> String,
    val rule: (RepeatEntry) -> RepeatRule,
    /** What the number field asks, for the choices that count days or weeks. */
    val countLabel: String? = null,
    val startingCount: String = "1",
) {
    NONE("Does not repeat", note = { "Once, at the due time." }, rule = { RepeatRule.ONCE }),
    DAILY("Every day", note = { "Every day at this time." }, rule = { RepeatRule.daily(it.until) }),
    WEEKDAYS(
        "On chosen weekdays",
        note = { "On the days you pick, at this time." },
        rule = { RepeatRule.weekdays(it.weekdays, it.until) },
    ),
    EVERY_DAYS(
        "Every few days",
        note = { "Counted from the due date." },
        rule = { RepeatRule.everyDays(it.count, it.until) },
        countLabel = "Every how many days",
        startingCount = "2",
    ),
    EVERY_WEEKS(
        "Every week or few weeks",
        note = { "On ${it.dayOfWeek.short()}, the weekday of the due date." },
        rule = { RepeatRule.everyWeeks(it.count, it.until) },
        countLabel = "Every how many weeks",
    ),
    MONTHLY(
        "Every month",
        note = { if (it.dayOfMonth > 28) "On day ${it.dayOfMonth}, or the last day of a shorter month." else "On day ${it.dayOfMonth} of each month." },
        rule = { RepeatRule.monthly(it.until) },
    ),
    YEARLY(
        "Every year",
        note = { if (it.monthValue == 2 && it.dayOfMonth == 29) "On 29 Feb, or 28 Feb in other years." else "On this date each year." },
        rule = { RepeatRule.yearly(it.until) },
    ),
}

/** What the user has picked under "Repeats", kept as typed so a half-filled number is not lost. */
data class RepeatEntry(
    val choice: RepeatChoice = RepeatChoice.NONE,
    val every: String = "",
    val weekdays: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
) {
    /** A number left empty becomes 0, which the tools refuse in words the form shows. */
    val count: Int get() = every.toIntOrNull() ?: 0

    fun rule(): RepeatRule = choice.rule(this)

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

/**
 * How a task repeats: the cycle, the extra the cycle needs, and an optional last day.
 * [countedFrom] is the date its days are worked out from.
 */
@Composable
fun RepeatField(entry: RepeatEntry, countedFrom: LocalDate, onChange: (RepeatEntry) -> Unit, onPickEndDate: () -> Unit) {
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
                                    every = choice.startingCount,
                                    until = entry.until.takeIf { choice != RepeatChoice.NONE },
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (entry.choice == RepeatChoice.WEEKDAYS) {
            // The same buttons as on the agent's action card.
            DayButtonRow(
                selected = entry.weekdays,
                onTap = { day -> onChange(entry.copy(weekdays = if (day in entry.weekdays) entry.weekdays - day else entry.weekdays + day)) },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        entry.choice.countLabel?.let { countLabel ->
            OutlinedTextField(
                value = entry.every,
                onValueChange = { typed -> onChange(entry.copy(every = typed.filter(Char::isDigit).take(3))) },
                label = { Text(countLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(entry.choice.note(countedFrom), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
