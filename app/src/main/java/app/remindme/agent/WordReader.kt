package app.remindme.agent

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * Turns the owner's own words for when, copied by the model, into a moment, with the meanings agreed in
 * research ticket 03. The model never works out a date: this does, with plain arithmetic.
 */
object WordReader {

    /**
     * The moment [words] mean, said at [now]; null when they cannot be read. No words at all is one hour
     * from now. With [keep] (the task's due time, on update), words giving only a time keep its day and
     * words giving only a day keep its time. An [alarm] reads an hour with no am or pm as morning.
     */
    fun due(words: String, now: LocalDateTime, alarm: Boolean = false, keep: LocalDateTime? = null): LocalDateTime? {
        val tokens = tokens(words)
        if (tokens.isEmpty()) return keep ?: now.plusHours(1)
        val said = Said.read(tokens) ?: return null
        return said.resolve(now, alarm, keep)
    }

    /** The words of [words] that are not in [message]: a sign the model worked something out instead of copying. */
    fun notIn(words: String, message: String): List<String> {
        val given = tokens(message).flatMap(::canonical).toSet()
        return tokens(words).flatMap(::canonical).filter { it !in given && it !in COPY_FILLERS }.distinct()
    }

    /** Lower case, with numbers, times, dates and words apart: "5:30pm" is "5:30" and "pm". */
    internal fun tokens(text: String): List<String> =
        TOKEN.findAll(text.lowercase().replace(Regex("\\b([ap])\\.m\\.?"), "$1m")).map { it.value }.toList()

    /** A token as both texts are compared: one form for each word, numbers as digits, a time as its numbers. */
    private fun canonical(token: String): List<String> {
        if (CLOCK.matches(token) || SLASH.matches(token)) return token.split(':', '.', '/').map { it.trimStart('0').ifEmpty { "0" } }
        ORDINAL.matchEntire(token)?.let { return listOf(it.groupValues[1]) }
        NUMBER_WORDS[token]?.let { return listOf(it.toString()) }
        SYNONYMS[token]?.let { return listOf(it) }
        weekday(token)?.let { return listOf(it.name.lowercase()) }
        month(token)?.let { return listOf("month${it}") }
        val single = if (token.length > 3 && token.endsWith("s")) token.dropLast(1) else token
        return listOf(SYNONYMS[single] ?: single)
    }

    private enum class Part(val time: LocalTime, val afternoon: Boolean) {
        MORNING(LocalTime.of(9, 0), false),
        AFTERNOON(LocalTime.of(15, 0), true),
        EVENING(LocalTime.of(19, 0), true),
        NIGHT(LocalTime.of(21, 0), true),
    }

    /** What the words said, before any arithmetic. */
    private class Said {
        var days: Long? = null
        var months: Long? = null
        var exact: Duration? = null
        var weekday: DayOfWeek? = null
        var nextWeek = false
        var weekend = false
        var endOfMonth = false
        var dayOfMonth: Int? = null
        var month: Int? = null
        var hour: Int? = null
        var minute = 0
        var twentyFour = false
        var meridiem: String? = null
        var part: Part? = null
        var noon = false
        var midnight = false

        private val saysDay get() = days != null || months != null || weekday != null || nextWeek || weekend || endOfMonth || dayOfMonth != null

        fun resolve(now: LocalDateTime, alarm: Boolean, keep: LocalDateTime?): LocalDateTime? {
            exact?.let { return now.plus(it) }
            val today = now.toLocalDate()
            val time = timeOfDay(now, alarm, keep)
            if (midnight) return (day(today, now, LocalTime.MIDNIGHT) ?: today).plusDays(1).atStartOfDay()
            val day = day(today, now, time ?: keep?.toLocalTime() ?: MORNING)
            return when {
                day != null -> day.atTime(time ?: keep?.toLocalTime() ?: MORNING)
                time == null -> null
                keep != null -> keep.toLocalDate().atTime(time)
                else -> today.atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }
            }
        }

        /** The day the words name; [time] decides whether today still counts. */
        private fun day(today: LocalDate, now: LocalDateTime, time: LocalTime): LocalDate? {
            fun ahead(day: LocalDate) = day.atTime(time).isAfter(now)
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return when {
                days != null -> today.plusDays(days!!)
                months != null -> today.plusMonths(months!!)
                weekday != null && nextWeek -> monday.plusWeeks(1).plusDays((weekday!!.value - 1).toLong())
                weekday != null -> today.with(TemporalAdjusters.nextOrSame(weekday)).let { if (ahead(it)) it else it.plusWeeks(1) }
                nextWeek && weekend -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).let { if (ahead(it)) it else it.plusWeeks(1) }.plusWeeks(1)
                weekend -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).let { if (ahead(it)) it else it.plusWeeks(1) }
                nextWeek -> monday.plusWeeks(1)
                endOfMonth -> today.with(TemporalAdjusters.lastDayOfMonth())
                dayOfMonth != null && month != null -> {
                    val thisYear = clamped(today.year, month!!, dayOfMonth!!)
                    if (ahead(thisYear)) thisYear else clamped(today.year + 1, month!!, dayOfMonth!!)
                }
                dayOfMonth != null -> {
                    val thisMonth = clamped(today.year, today.monthValue, dayOfMonth!!)
                    if (ahead(thisMonth)) thisMonth else today.plusMonths(1).let { clamped(it.year, it.monthValue, dayOfMonth!!) }
                }
                else -> null
            }
        }

        /** The clock time the words give, with the am/pm rules for an hour that has neither; null when they give none. */
        private fun timeOfDay(now: LocalDateTime, alarm: Boolean, keep: LocalDateTime?): LocalTime? {
            if (noon) return LocalTime.NOON
            val h = hour ?: return part?.time
            val m = minute
            val clear = when {
                meridiem == "pm" -> if (h < 12) h + 12 else 12
                meridiem == "am" -> if (h == 12) 0 else h
                twentyFour || h == 0 || h > 12 -> h
                part != null -> if (part!!.afternoon && h < 12) h + 12 else if (!part!!.afternoon && h == 12) 0 else h
                else -> null
            }
            if (clear != null) return LocalTime.of(clear, m)
            val morning = LocalTime.of(if (h == 12) 0 else h, m)
            val afternoon = LocalTime.of(if (h == 12) 12 else h + 12, m)
            return when {
                saysDay -> when {
                    alarm -> morning
                    h == 12 -> LocalTime.NOON.withMinute(m)
                    h >= 7 -> morning
                    else -> afternoon
                }
                // A time for a task already set: the reading nearer the time it has now.
                keep != null -> listOf(morning, afternoon).minBy { abs(Duration.between(keep.toLocalTime(), it).toMinutes()) }
                else -> listOf(morning, afternoon).minBy { soonest(now, it) }
            }
        }

        private fun soonest(now: LocalDateTime, time: LocalTime): LocalDateTime =
            now.toLocalDate().atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }

        companion object {
            /** Reads [tokens]; null when one of them means nothing here. */
            fun read(tokens: List<String>): Said? {
                val said = Said()
                var i = 0
                while (i < tokens.size) {
                    val token = tokens[i]
                    val used = said.take(tokens, i) ?: return null
                    if (used == 0) {
                        if (token !in FILLERS) return null
                        i++
                    } else {
                        i += used
                    }
                }
                val valid = said.hour?.let { it in 0..23 } ?: true
                return said.takeIf { valid && said.minute in 0..59 && (said.saysDay || said.hour != null || said.part != null || said.noon || said.midnight || said.exact != null) }
            }
        }

        /** How many tokens from [i] this reads; 0 when [tokens] at [i] is not one it knows; null when it is malformed. */
        private fun take(tokens: List<String>, i: Int): Int? {
            val token = tokens[i]
            val next = tokens.getOrNull(i + 1)
            fun at(vararg words: String) = words.indices.all { tokens.getOrNull(i + it) == words[it] }
            return when {
                at("day", "after", "tomorrow") -> { days = 2; 3 }
                token in TOMORROW -> { days = 1; 1 }
                token == "today" -> { days = days ?: 0; 1 }
                token == "tonight" -> { days = 0; part = Part.NIGHT; 1 }
                token == "next" && next == "week" -> { nextWeek = true; 2 }
                token == "next" && next == "weekend" -> { nextWeek = true; weekend = true; 2 }
                token == "next" && next != null && weekday(next) != null -> { weekday = weekday(next); nextWeek = true; 2 }
                token == "weekend" -> { weekend = true; 1 }
                weekday(token) != null -> { weekday = weekday(token); 1 }
                at("end", "of", "the", "month") -> { endOfMonth = true; 4 }
                at("end", "of", "month") -> { endOfMonth = true; 3 }
                at("half", "an", "hour") -> { exact = Duration.ofMinutes(30); 3 }
                (token == "in" || token == "within") && next != null && amount(next) != null -> span(tokens, i + 1)?.plus(1)
                amount(token) != null && tokens.getOrNull(i + 2) == "from" && tokens.getOrNull(i + 3) in setOf("today", "now") -> span(tokens, i)?.plus(2)
                month(token) != null && next != null && dayNumber(next) != null -> { month = month(token); dayOfMonth = dayNumber(next); 2 }
                dayNumber(token) != null && next != null && month(next) != null -> { dayOfMonth = dayNumber(token); month = month(next); 2 }
                SLASH.matches(token) -> {
                    val (d, mo) = token.split('/').map(String::toInt)
                    if (mo !in 1..12 || d !in 1..31) return null
                    dayOfMonth = d; month = mo; 1
                }
                ORDINAL.matches(token) -> { dayOfMonth = dayNumber(token) ?: return null; 1 }
                CLOCK.matches(token) -> {
                    if (hour != null) return null
                    val (h, m) = token.split(':', '.')
                    hour = h.toInt(); minute = m.toInt(); twentyFour = h.length == 2 && (h.startsWith("0") || h.toInt() > 12); 1
                }
                token.all(Char::isDigit) || NUMBER_WORDS.containsKey(token) -> {
                    if (hour != null) return null
                    hour = number(token)
                    val minutes = next?.takeIf { it.length == 2 && it.all(Char::isDigit) }
                    if (minutes != null) { minute = minutes.toInt(); 2 } else 1
                }
                token == "am" || token == "pm" -> { meridiem = token; 1 }
                token == "noon" || token == "midday" -> { noon = true; 1 }
                token == "midnight" -> { midnight = true; 1 }
                token == "morning" -> { part = Part.MORNING; 1 }
                token == "afternoon" -> { part = Part.AFTERNOON; 1 }
                token == "evening" -> { part = Part.EVENING; 1 }
                token == "night" -> { part = Part.NIGHT; 1 }
                else -> 0
            }
        }

        /** "20 minutes", "an hour", "2 weeks" at [i]: how many tokens it took, or null when the unit is not one it knows. */
        private fun span(tokens: List<String>, i: Int): Int? {
            val n = amount(tokens[i]) ?: return null
            val unit = tokens.getOrNull(i + 1)?.let { canonical(it).singleOrNull() } ?: return null
            when (unit) {
                "minute" -> exact = Duration.ofMinutes(n)
                "hour" -> exact = Duration.ofHours(n)
                "day" -> days = n
                "week" -> days = n * 7
                "month" -> months = n
                else -> return null
            }
            return 2
        }
    }

    private fun clamped(year: Int, month: Int, day: Int): LocalDate {
        val first = LocalDate.of(year, month, 1)
        return first.withDayOfMonth(minOf(day, first.lengthOfMonth()))
    }

    private fun amount(token: String): Long? = when (token) {
        "a", "an", "one" -> 1L
        else -> number(token)?.toLong()
    }

    private fun number(token: String): Int? = token.toIntOrNull() ?: NUMBER_WORDS[token]

    private fun dayNumber(token: String): Int? =
        (ORDINAL.matchEntire(token)?.groupValues?.get(1) ?: token.takeIf { it.all(Char::isDigit) })?.toIntOrNull()?.takeIf { it in 1..31 }

    internal fun weekday(token: String): DayOfWeek? = WEEKDAYS[token]

    private fun month(token: String): Int? = MONTHS[token]

    private val MORNING = LocalTime.of(9, 0)
    private val TOKEN = Regex("\\d{1,2}[:.]\\d{2}|\\d{1,2}/\\d{1,2}|\\d+(?:st|nd|rd|th)|\\d+|[a-z]+")
    private val CLOCK = Regex("\\d{1,2}[:.]\\d{2}")
    private val SLASH = Regex("\\d{1,2}/\\d{1,2}")
    private val ORDINAL = Regex("(\\d+)(?:st|nd|rd|th)")
    private val TOMORROW = setOf("tomorrow", "tmrw", "tmr", "tomorow", "tommorow")

    /** Words that carry no meaning of their own in words for when. */
    private val FILLERS = setOf("at", "on", "the", "of", "by", "this", "coming", "in", "o", "clock", "oclock", "around", "about")

    /** The only words the model may add to the owner's when it copies them. */
    private val COPY_FILLERS = setOf("at", "on", "the", "of", "and", "a", "an")

    private val NUMBER_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50,
    )
    private val SYNONYMS = mapOf(
        "min" to "minute", "mins" to "minute", "minutes" to "minute", "hr" to "hour", "hrs" to "hour", "hours" to "hour",
        "tmrw" to "tomorrow", "tmr" to "tomorrow", "tomorow" to "tomorrow", "tommorow" to "tomorrow", "midday" to "noon",
        "wk" to "week", "wks" to "week",
    )
    private val WEEKDAYS: Map<String, DayOfWeek> = DayOfWeek.entries.flatMap { day ->
        val name = day.name.lowercase()
        val more = when (day) {
            DayOfWeek.TUESDAY -> listOf("tues")
            DayOfWeek.WEDNESDAY -> listOf("weds")
            DayOfWeek.THURSDAY -> listOf("thur", "thurs")
            else -> emptyList()
        }
        (listOf(name, name.take(3), name + "s") + more).map { it to day }
    }.toMap()
    private val MONTHS: Map<String, Int> = listOf(
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
    ).flatMapIndexed { i, name -> listOf(name to i + 1, name.take(3) to i + 1) + if (name == "september") listOf("sept" to 9) else emptyList() }.toMap()
}
