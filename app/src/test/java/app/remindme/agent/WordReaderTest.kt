package app.remindme.agent

import app.remindme.core.at
import app.remindme.core.RepeatRule
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket 03's table. Wednesday 18 November 2026, 09:15, unless a test says otherwise. */
class WordReaderTest {
    private val now = at("2026-11-18T09:15")

    private fun read(words: String, now: LocalDateTime = this.now, alarm: Boolean = false, keep: LocalDateTime? = null) =
        WordReader.due(words, now, alarm, keep)

    @Test
    fun `today, tomorrow and the day after tomorrow`() {
        assertEquals(at("2026-11-18T17:00"), read("today at 5 pm"))
        assertEquals(at("2026-11-19T09:00"), read("tomorrow"))
        assertEquals(at("2026-11-20T09:00"), read("day after tomorrow"))
        assertEquals(at("2026-11-20T09:00"), read("the day after tomorrow"))
    }

    @Test
    fun `a weekday is the next one, today only while its time is still ahead`() {
        assertEquals(at("2026-11-20T09:00"), read("friday"))
        assertEquals(at("2026-11-20T09:00"), read("on friday"))
        assertEquals(at("2026-11-20T09:00"), read("this friday"))
        assertEquals(at("2026-11-20T09:00"), read("coming friday"))
        assertEquals(at("2026-11-18T15:00"), read("wednesday at 3 pm"))
        assertEquals(at("2026-11-25T09:00"), read("wednesday"))
        assertEquals(at("2026-11-23T09:00"), read("mon"))
    }

    @Test
    fun `next tuesday is the tuesday of next week`() {
        assertEquals(at("2026-11-24T09:00"), read("next tuesday"))
        assertEquals(at("2026-11-24T09:00"), read("tuesday next week"))
        assertEquals(at("2026-12-01T09:00"), read("next tuesday", now = at("2026-11-23T09:15")))
    }

    @Test
    fun `next week is its monday`() {
        assertEquals(at("2026-11-23T09:00"), read("next week"))
    }

    @Test
    fun `this weekend and next weekend are the coming saturday and the one after`() {
        assertEquals(at("2026-11-21T09:00"), read("this weekend"))
        assertEquals(at("2026-11-28T09:00"), read("next weekend"))
    }

    @Test
    fun `a day of the month is the next such day, the month's last day when it has none`() {
        assertEquals(at("2026-12-05T09:00"), read("the 5th"))
        assertEquals(at("2026-11-30T09:00"), read("on the 31st"))
        assertEquals(at("2026-11-20T09:00"), read("the 20th"))
    }

    @Test
    fun `a date is the next such date, this year or next`() {
        assertEquals(at("2026-12-25T09:00"), read("december 25"))
        assertEquals(at("2026-12-25T09:00"), read("25 dec"))
        assertEquals(at("2026-12-25T09:00"), read("25/12"))
        assertEquals(at("2027-03-01T09:00"), read("march 1st"))
    }

    @Test
    fun `end of the month is its last day`() {
        assertEquals(at("2026-11-30T09:00"), read("end of the month"))
        assertEquals(at("2026-11-30T09:00"), read("end of month"))
    }

    @Test
    fun `minutes and hours from now are exact`() {
        assertEquals(at("2026-11-18T09:35"), read("in 20 minutes"))
        assertEquals(at("2026-11-18T11:15"), read("in 2 hours"))
        assertEquals(at("2026-11-18T10:15"), read("in an hour"))
        assertEquals(at("2026-11-18T09:45"), read("in half an hour"))
    }

    @Test
    fun `days, weeks and months from today come at the stated or usual time`() {
        assertEquals(at("2026-11-21T09:00"), read("in 3 days"))
        assertEquals(at("2026-12-02T09:00"), read("in 2 weeks"))
        assertEquals(at("2026-12-18T09:00"), read("in a month"))
        assertEquals(at("2026-11-25T09:00"), read("a week from today"))
        assertEquals(at("2026-12-02T18:00"), read("in 2 weeks at 6 pm"))
        assertEquals(at("2027-02-28T09:00"), read("in 3 months", now = at("2026-11-30T09:15")))
    }

    @Test
    fun `a time with am, pm or a 24-hour clock is that time`() {
        assertEquals(at("2026-11-18T17:00"), read("at 5 pm"))
        assertEquals(at("2026-11-18T17:00"), read("5pm"))
        assertEquals(at("2026-11-18T17:00"), read("17:00"))
        assertEquals(at("2026-11-18T17:30"), read("5 30 pm"))
        assertEquals(at("2026-11-18T17:30"), read("5:30pm"))
        assertEquals(at("2026-11-19T07:00"), read("7 am"))
    }

    @Test
    fun `with no day said, a bare time is whichever reading comes round next`() {
        assertEquals(at("2026-11-18T17:30"), read("5 30"))
        assertEquals(at("2026-11-18T21:00"), read("at 9"))
        assertEquals(at("2026-11-18T09:30"), read("9 30"))
    }

    @Test
    fun `with a day said, 7 to 11 is morning, 12 noon and 1 to 6 afternoon, unless it is an alarm`() {
        assertEquals(at("2026-11-19T17:30"), read("5 30 tomorrow"))
        assertEquals(at("2026-11-19T09:00"), read("tomorrow at 9"))
        assertEquals(at("2026-11-19T12:00"), read("tomorrow at 12"))
        assertEquals(at("2026-11-19T05:30"), read("5 30 tomorrow", alarm = true))
        assertEquals(at("2026-11-19T08:00"), read("tomorrow morning at 8"))
        assertEquals(at("2026-11-19T20:00"), read("tomorrow evening at 8"))
    }

    @Test
    fun `noon and midnight`() {
        assertEquals(at("2026-11-18T12:00"), read("noon"))
        assertEquals(at("2026-11-19T00:00"), read("midnight"))
        assertEquals(at("2026-11-20T00:00"), read("tomorrow at midnight"))
    }

    @Test
    fun `parts of the day have their usual times`() {
        assertEquals(at("2026-11-19T09:00"), read("tomorrow morning"))
        assertEquals(at("2026-11-18T15:00"), read("this afternoon"))
        assertEquals(at("2026-11-18T19:00"), read("evening"))
        assertEquals(at("2026-11-18T21:00"), read("night"))
    }

    @Test
    fun `tonight is 21 00 today, or the time it is given`() {
        assertEquals(at("2026-11-18T21:00"), read("tonight"))
        assertEquals(at("2026-11-18T20:00"), read("tonight at 8"))
    }

    @Test
    fun `no when at all is one hour from now`() {
        assertEquals(at("2026-11-18T10:15"), read(""))
    }

    @Test
    fun `words it cannot read are not guessed`() {
        assertNull(read("whenever"))
        assertNull(read("at"))
        assertNull(read("2026-11-19T09:00"))
        assertNull(read("at 25"))
    }

    @Test
    fun `on update, words giving only a time keep the task's day, and only a day keep its time`() {
        val task = at("2026-11-20T18:00")
        assertEquals(at("2026-11-20T19:00"), read("7", keep = task))
        assertEquals(at("2026-11-20T07:30"), read("7 30 am", keep = task))
        assertEquals(at("2026-11-24T18:00"), read("next tuesday", keep = task))
        assertEquals(at("2026-11-19T18:00"), read("tomorrow", keep = task))
    }

    @Test
    fun `words for when that are not in the message are named`() {
        assertEquals(emptyList<String>(), WordReader.notIn("next tuesday at 2 pm", "Remind me next Tuesday at 2pm to call the bank"))
        assertEquals(emptyList<String>(), WordReader.notIn("in 20 mins", "in twenty minutes check the oven"))
        assertEquals(emptyList<String>(), WordReader.notIn("on the 5th", "pay rent 5th"))
        assertEquals(listOf("wednesday"), WordReader.notIn("wednesday at 9", "tomorrow at 9 dentist"))
        assertEquals(listOf("17", "0"), WordReader.notIn("17:00", "call mom at 5 pm"))
    }

    private fun rule(words: String) = WordReader.repeat(words, now.toLocalDate(), now)?.rule

    private val weekdays = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

    @Test
    fun `chosen weekdays repeat on those days`() {
        assertEquals(RepeatRule.weekdays(setOf(SUNDAY, WEDNESDAY)), rule("every sunday and wednesday"))
        assertEquals(RepeatRule.weekdays(setOf(SUNDAY, WEDNESDAY)), rule("sundays and wednesdays"))
        assertEquals(RepeatRule.weekdays(setOf(MONDAY, WEDNESDAY, FRIDAY)), rule("every mon, wed and fri"))
        assertEquals(RepeatRule.weekdays(setOf(TUESDAY, THURSDAY)), rule("on tuesdays and thursdays"))
        assertEquals(RepeatRule.weekdays(setOf(MONDAY)), rule("every monday"))
    }

    @Test
    fun `weekends and the working week`() {
        assertEquals(RepeatRule.weekdays(setOf(SATURDAY, SUNDAY)), rule("weekends"))
        assertEquals(RepeatRule.weekdays(setOf(SATURDAY, SUNDAY)), rule("every weekend"))
        assertEquals(RepeatRule.weekdays(weekdays), rule("every weekday"))
        assertEquals(RepeatRule.weekdays(weekdays), rule("weekdays"))
        assertEquals(RepeatRule.weekdays(weekdays), rule("monday to friday"))
        assertEquals(RepeatRule.weekdays(weekdays), rule("mon-fri"))
        assertEquals(RepeatRule.weekdays(weekdays), rule("every working day"))
    }

    @Test
    fun `days can be taken out with except`() {
        assertEquals(RepeatRule.weekdays(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, SATURDAY, SUNDAY)), rule("every day except friday"))
        assertEquals(RepeatRule.weekdays(setOf(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)), rule("weekdays except monday"))
    }

    @Test
    fun `every day, every week and every few days or weeks`() {
        assertEquals(RepeatRule.daily(), rule("every day"))
        assertEquals(RepeatRule.daily(), rule("daily"))
        assertEquals(RepeatRule.daily(), rule("every morning"))
        assertEquals(RepeatRule.everyDays(3), rule("every 3 days"))
        assertEquals(RepeatRule.everyWeeks(1), rule("every week"))
        assertEquals(RepeatRule.everyWeeks(1), rule("weekly"))
        assertEquals(RepeatRule.everyWeeks(2), rule("every 2 weeks"))
    }

    @Test
    fun `every other`() {
        assertEquals(RepeatRule.everyDays(2), rule("every other day"))
        assertEquals(RepeatRule.everyWeeks(2), rule("every other week"))
        val friday = WordReader.repeat("every other friday", now.toLocalDate(), now)!!
        assertEquals(RepeatRule.everyWeeks(2), friday.rule)
        assertEquals(FRIDAY, friday.on)
    }

    @Test
    fun `monthly and yearly`() {
        assertEquals(RepeatRule.monthly(), rule("every month"))
        assertEquals(RepeatRule.monthly(), rule("monthly"))
        assertEquals(RepeatRule.yearly(), rule("every year"))
        assertEquals(RepeatRule.yearly(), rule("yearly"))
    }

    @Test
    fun `a repeat can end after a stretch or on a day`() {
        assertEquals(RepeatRule.weekdays(setOf(MONDAY), until = LocalDate.parse("2026-12-08")), rule("every monday for 3 weeks"))
        assertEquals(RepeatRule.daily(until = LocalDate.parse("2026-11-22")), rule("daily for 5 days"))
        assertEquals(RepeatRule.daily(until = LocalDate.parse("2026-11-20")), rule("daily until friday"))
        assertEquals(RepeatRule.everyWeeks(1, until = LocalDate.parse("2026-12-20")), rule("every week until december 20"))
        val end = WordReader.repeat("until friday", now.toLocalDate(), now)!!
        assertEquals(null, end.rule)
        assertEquals(LocalDate.parse("2026-11-20"), end.until)
    }

    @Test
    fun `no repeat words is once, and words it cannot read are not guessed`() {
        assertEquals(RepeatRule.ONCE, rule(""))
        assertEquals(RepeatRule.ONCE, rule("once"))
        assertNull(WordReader.repeat("every full moon", now.toLocalDate(), now))
        assertNull(WordReader.repeat("every other monday and friday", now.toLocalDate(), now))
    }

    @Test
    fun `a stated warning is read, one or two of them, the longest first`() {
        assertEquals(listOf(Duration.ofMinutes(30)), WordReader.warn("30 minutes before"))
        assertEquals(listOf(Duration.ofDays(1)), WordReader.warn("the day before"))
        assertEquals(listOf(Duration.ofDays(1), Duration.ofHours(1)), WordReader.warn("an hour and a day before"))
        assertEquals(listOf(Duration.ofMinutes(30)), WordReader.warn("half an hour before"))
        assertEquals(listOf(Duration.ofHours(2)), WordReader.warn("2 hours early"))
        assertEquals(listOf(Duration.ofMinutes(15)), WordReader.warn("15 min"))
        assertEquals(listOf(Duration.ofDays(7)), WordReader.warn("a week before"))
        assertEquals(listOf(Duration.ofDays(1), Duration.ofMinutes(10)), WordReader.warn("10 mins, 1 day before"))
    }

    @Test
    fun `warnings it cannot read, or more than two, are not read`() {
        assertNull(WordReader.warn("the morning of"))
        assertNull(WordReader.warn("10 minutes, an hour and a day before"))
        assertNull(WordReader.warn("before"))
    }

    @Test
    fun `an alarm is asked for only in so many words`() {
        assertTrue(WordReader.asksForAlarm("set an alarm for 6"))
        assertTrue(WordReader.asksForAlarm("wake me at 7"))
        assertTrue(WordReader.asksForAlarm("I need to wake up at 6"))
        assertTrue(WordReader.asksForAlarm("flight at 9, don't let me miss it"))
        assertTrue(WordReader.asksForAlarm("dont let me miss the bus"))
        assertFalse(WordReader.asksForAlarm("call mom at 9"))
        assertFalse(WordReader.asksForAlarm("important meeting at 3"))
    }
}
