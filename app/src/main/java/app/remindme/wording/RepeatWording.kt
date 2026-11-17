package app.remindme.wording

import app.remindme.core.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dayAndMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun DayOfWeek.short(): String = getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

/**
 * How a repeat rule reads on the list: "Every day", "Every Mon, Wed, Fri", "Every 2 weeks on Tue",
 * "Monthly on day 31", "Yearly on 29 Feb", each with ", until 31 Dec 2026" when it has an end date.
 * Null for a task that does not repeat. [firstDue] is the due time the rule is worked out from.
 */
fun RepeatRule.inWords(firstDue: LocalDateTime): String? {
    val every = when (cycle) {
        RepeatRule.Cycle.ONCE -> return null
        RepeatRule.Cycle.DAYS -> if (every == 1) "Every day" else "Every $every days"
        RepeatRule.Cycle.WEEKDAYS -> "Every " + weekdays.sorted().joinToString(", ") { it.short() }
        RepeatRule.Cycle.WEEKS -> (if (every == 1) "Every week" else "Every $every weeks") + " on ${firstDue.dayOfWeek.short()}"
        RepeatRule.Cycle.MONTHLY -> "Monthly on day ${firstDue.dayOfMonth}"
        RepeatRule.Cycle.YEARLY -> "Yearly on ${firstDue.format(dayAndMonth)}"
    }
    return every + (until?.let { ", until ${it.inFull()}" } ?: "")
}

fun LocalDate.inFull(): String = format(dayMonthYear)
