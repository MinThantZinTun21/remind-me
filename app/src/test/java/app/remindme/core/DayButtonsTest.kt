package app.remindme.core

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 18 November 2026, where these start, is a Wednesday. */
class DayButtonsTest {
    private val world = World(now = "2026-11-18T13:00")
    private val buttons = DayButtons(world.tools, world.clock)

    private fun task(due: String, repeat: RepeatRule = RepeatRule.ONCE) =
        world.tools.create(TaskDetails(title = "Gym", due = at(due), repeat = repeat)).task()

    private fun Task.tapped(day: DayOfWeek) = buttons.tap(this, day).task()

    @Test
    fun `a task gets the row its repeat rule allows`() {
        assertEquals(DayRow.MoveTo(TUESDAY), task("2026-11-24T14:00").dayRow())
        assertEquals(DayRow.RepeatsOn(setOf(SUNDAY, WEDNESDAY)), task("2026-11-22T06:00", RepeatRule.weekdays(setOf(SUNDAY, WEDNESDAY))).dayRow())
        assertEquals(DayRow.RepeatsOn(DayOfWeek.entries.toSet()), task("2026-11-18T20:00", RepeatRule.daily()).dayRow())
        assertNull(task("2026-12-01T09:00", RepeatRule.monthly()).dayRow())
        assertNull(task("2026-11-20T09:00", RepeatRule.everyDays(2)).dayRow())
        assertNull(task("2026-11-20T09:00", RepeatRule.everyWeeks(1)).dayRow())
    }

    @Test
    fun `a day added for tomorrow comes before the old next day`() {
        val gym = task("2026-11-22T06:00", RepeatRule.weekdays(setOf(SUNDAY, WEDNESDAY)))
        assertEquals(at("2026-11-22T06:00"), gym.due)

        val after = gym.tapped(THURSDAY)

        assertEquals(RepeatRule.weekdays(setOf(SUNDAY, WEDNESDAY, THURSDAY)), after.repeat)
        assertEquals(at("2026-11-19T06:00"), after.due)
    }

    @Test
    fun `a day turned off moves the task to its next remaining day`() {
        val meeting = task("2026-11-18T15:00", RepeatRule.weekdays(setOf(WEDNESDAY, FRIDAY)))

        val after = meeting.tapped(WEDNESDAY)

        assertEquals(RepeatRule.weekdays(setOf(FRIDAY)), after.repeat)
        assertEquals(at("2026-11-20T15:00"), after.due)
    }

    @Test
    fun `today counts when its time has not passed yet`() {
        val later = task("2026-11-20T15:00", RepeatRule.weekdays(setOf(FRIDAY)))

        assertEquals(at("2026-11-18T15:00"), later.tapped(WEDNESDAY).due)
    }

    @Test
    fun `all seven days make it daily and one off from daily makes it weekdays`() {
        val six = task("2026-11-19T20:00", RepeatRule.weekdays(DayOfWeek.entries.toSet() - MONDAY))

        val daily = six.tapped(MONDAY)
        assertEquals(RepeatRule.daily(), daily.repeat)

        val noSunday = daily.tapped(SUNDAY)
        assertEquals(RepeatRule.weekdays(DayOfWeek.entries.toSet() - SUNDAY), noSunday.repeat)
    }

    @Test
    fun `the last day cannot be turned off`() {
        val gym = task("2026-11-22T06:00", RepeatRule.weekdays(setOf(SUNDAY)))

        assertEquals(ToolResult.Refused(DayButtons.LAST_DAY), buttons.tap(gym, SUNDAY))
        assertEquals(gym, world.tools.list().single())
    }

    @Test
    fun `the end date stays when the days change`() {
        val until = LocalDate.parse("2026-12-31")
        val gym = task("2026-11-22T06:00", RepeatRule.weekdays(setOf(SUNDAY), until))

        assertEquals(until, gym.tapped(MONDAY).repeat.until)
    }

    @Test
    fun `a one-time task moves to the next such day at the same time`() {
        val dentist = task("2026-11-24T14:00")

        val moved = dentist.tapped(FRIDAY)

        assertEquals(RepeatRule.ONCE, moved.repeat)
        assertEquals(at("2026-11-20T14:00"), moved.due)
    }

    @Test
    fun `a one-time task moved to today stays today while its time is still to come, else goes a week on`() {
        assertEquals(at("2026-11-18T14:00"), task("2026-11-24T14:00").tapped(WEDNESDAY).due)
        assertEquals(at("2026-11-25T09:00"), task("2026-11-24T09:00").tapped(WEDNESDAY).due)
    }

    @Test
    fun `repeat weekly keeps a one-time task where it is and makes it come round on its own day`() {
        val dentist = task("2026-11-24T14:00")

        val weekly = buttons.repeatWeekly(dentist).task()

        assertEquals(RepeatRule.weekdays(setOf(TUESDAY)), weekly.repeat)
        assertEquals(at("2026-11-24T14:00"), weekly.due)
    }

    @Test
    fun `undo puts back one day change at a time`() {
        val gym = task("2026-11-22T06:00", RepeatRule.weekdays(setOf(SUNDAY, WEDNESDAY)))
        val first = gym.tapped(THURSDAY)
        first.tapped(FRIDAY)

        buttons.undo(first).task()
        assertEquals(first, world.tools.list().single())

        buttons.undo(gym).task()
        assertEquals(gym, world.tools.list().single())
    }
}
