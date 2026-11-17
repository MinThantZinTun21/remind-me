package app.remindme.wording

import java.time.Duration

/** How a lead time reads: "5 minutes", "1 hour", "1 day", "1 hour 30 minutes". */
fun Duration.inWords(): String {
    val parts = listOf(toDays() to "day", toHoursPart().toLong() to "hour", toMinutesPart().toLong() to "minute")
        .filter { (amount, _) -> amount > 0 }
        .map { (amount, unit) -> "$amount $unit" + if (amount == 1L) "" else "s" }
    return if (parts.isEmpty()) "0 minutes" else parts.joinToString(" ")
}
