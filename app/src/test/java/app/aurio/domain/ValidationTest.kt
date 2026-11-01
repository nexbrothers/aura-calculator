package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ValidationTest {
    private val start = LocalDate.of(2026, 11, 1)
    private val limited = AppTerms("Instagram", AppCategory.LIMITED, 30)

    private fun day(offset: Long, minutes: Int?, terms: AppTerms? = limited) = DayRecord(start.plusDays(offset), minutes, terms)

    private fun weeks(usage: List<DayRecord>, settledDays: Long, focus: List<Long> = emptyList(), deltas: Map<Long, Int> = emptyMap()) =
        Validation.weeks(
            start, start.plusDays(settledDays - 1), usage,
            focus.map { start.plusDays(it) }, deltas.mapKeys { start.plusDays(it.key) },
        )

    @Test fun nothingBeforeTheFirstSettledDay() {
        assertEquals(emptyList<WeekReport>(), Validation.weeks(start, null, emptyList(), emptyList(), emptyMap()))
        assertEquals(emptyList<WeekReport>(), Validation.weeks(start, start.minusDays(1), emptyList(), emptyList(), emptyMap()))
    }

    @Test fun twoFullWeeks() {
        val usage = (0L..6L).map { day(it, 40) } + (7L..13L).map { day(it, 20) }
        val (w1, w2) = weeks(usage, settledDays = 14)
        assertEquals(1, w1.week)
        assertEquals(0, w1.limitDaysKept)
        assertEquals(70, w1.minutesOverLimits)
        assertEquals(40, w1.averageMinutesPerLimitedApp)
        assertEquals(7, w2.limitDaysKept)
        assertEquals(0, w2.minutesOverLimits)
        assertEquals(20, w2.averageMinutesPerLimitedApp)
        assertTrue(w1.complete && w2.complete)
    }

    @Test fun lastWeekCanBePartial() {
        val reports = weeks((0L..9L).map { day(it, 10) }, settledDays = 10)
        assertEquals(2, reports.size)
        assertEquals(false, reports[1].complete)
        assertEquals(3, reports[1].limitDaysCounted)
    }

    @Test fun unknownAndNotLimitedDaysAreLeftOut() {
        val usage = listOf(day(0, null), day(1, 90, AppTerms("Maps", AppCategory.ESSENTIAL, null)), day(2, 10))
        val w = weeks(usage, settledDays = 7).single()
        assertEquals(1, w.limitDaysCounted)
        assertEquals(0, w.minutesOverLimits)
    }

    @Test fun unsettledDaysAreLeftOut() {
        val w = weeks(listOf(day(0, 10), day(5, 99)), settledDays = 3).single()
        assertEquals(1, w.limitDaysCounted)
        assertEquals(false, w.complete)
    }

    @Test fun focusAndAuraPerWeek() {
        val w = weeks(emptyList(), settledDays = 7, focus = listOf(0, 0, 3, 8), deltas = mapOf(0L to 40, 1L to -10, 9L to 99)).single()
        assertEquals(3, w.focusSessionsCompleted)
        assertEquals(2, w.daysWithFocus)
        assertEquals(30, w.auraChange)
        assertNull(w.averageMinutesPerLimitedApp)
    }

    @Test fun rescueStatsCountAndRoundTrip() {
        var s = RescueStats()
        s = Validation.record(s, null)
        s = Validation.record(s, null)
        s = Validation.record(s, RescueOutcome.KEPT)
        s = Validation.record(s, RescueOutcome.OVER)
        assertEquals(RescueStats(sent = 2, keptAfter = 1, overAfter = 1, notCounted = 0), s)
        assertEquals(s, Validation.decode(Validation.encode(s)))
        assertEquals(RescueStats(), Validation.decode("junk"))
    }
}
