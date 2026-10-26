package app.aurio.domain

import app.aurio.domain.DayBar.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HistoryTest {
    private val today = LocalDate.parse("2026-10-26")
    private val yt30 = AppTerms("YouTube", AppCategory.LIMITED, 30)

    private fun record(daysAgo: Long, minutes: Int?, terms: AppTerms? = yt30) = DayRecord(today.minusDays(daysAgo), minutes, terms)

    @Test fun sevenDaysOldestFirstEndingToday() {
        val week = History.week(today, emptyList(), null, yt30)
        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first().date)
        assertEquals(today, week.last().date)
        assertEquals(State.NO_DATA, week.first().state)
    }

    @Test fun eachDayIsJudgedByTheLimitThatAppliedThatDay() {
        val week = History.week(
            today,
            listOf(record(2, 40, yt30), record(1, 40, yt30.copy(limitMinutes = 45))),
            null, yt30,
        )
        assertEquals(State.OVER, week[4].state)
        assertEquals(30, week[4].limitMinutes)
        assertEquals(State.UNDER, week[5].state)
        assertEquals(45, week[5].limitMinutes)
    }

    @Test fun atTheLimitIsUnder() {
        assertEquals(State.UNDER, History.week(today, listOf(record(1, 30)), null, yt30)[5].state)
    }

    @Test fun unknownDayIsNeitherWinNorLoss() {
        val bar = History.week(today, listOf(record(1, null)), null, yt30)[5]
        assertEquals(State.UNKNOWN, bar.state)
        assertNull(bar.minutes)
    }

    @Test fun neutralDayShowsTimeButIsNotScored() {
        val neutral = AppTerms("YouTube", AppCategory.NEUTRAL, null)
        val bar = History.week(today, listOf(record(1, 90, neutral)), null, yt30)[5]
        assertEquals(State.NOT_SCORED, bar.state)
        assertEquals(90, bar.minutes)
        assertNull(bar.limitMinutes)
    }

    @Test fun recordWithoutTermsUsesCurrentSettings() {
        assertEquals(State.OVER, History.week(today, listOf(record(1, 31, terms = null)), null, yt30)[5].state)
    }

    @Test fun todayComesFromTheLiveView() {
        val live = AppUsage("yt", "YouTube", AppCategory.LIMITED, 47, 30)
        val stale = record(0, 5)
        val bar = History.week(today, listOf(stale), live, yt30).last()
        assertEquals(47, bar.minutes)
        assertEquals(State.OVER, bar.state)
    }

    @Test fun impactCountsDaysAndTotal() {
        assertEquals(AppImpact(total = 23, daysUnder = 2, daysOver = 1), History.impact(listOf(20, -17, 20)))
        assertEquals(AppImpact(0, 0, 0), History.impact(emptyList()))
    }

    @Test fun monthGridStartsOnTheChosenDay() {
        // 1 October 2026 is a Thursday.
        val weeks = History.monthGrid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(listOf(null, null, null), weeks.first().take(3))
        assertEquals(LocalDate.parse("2026-10-01"), weeks.first()[3])
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.parse("2026-10-31"), weeks.last()[5])
        assertNull(weeks.last()[6])
        assertEquals(31, weeks.flatten().count { it != null })
    }

    @Test fun monthGridWithSundayFirst() {
        val weeks = History.monthGrid(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)
        // 1 February 2026 is a Sunday, so no leading blanks and exactly 4 weeks.
        assertEquals(LocalDate.parse("2026-02-01"), weeks.first()[0])
        assertEquals(4, weeks.size)
    }
}
