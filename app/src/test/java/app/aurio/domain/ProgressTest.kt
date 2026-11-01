package app.aurio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val limited = AppTerms("Instagram", AppCategory.LIMITED, 30)

    /** Minutes for the days before today, newest first (index 0 = yesterday). Null = Unknown. */
    private fun days(vararg minutes: Int?, terms: AppTerms? = limited) =
        minutes.mapIndexed { i, m -> DayRecord(today.minusDays(i + 1L), m, terms) }

    private fun change(records: List<DayRecord>, settledThrough: LocalDate? = today.minusDays(1)) =
        Progress.weekChange("insta", records, today, settledThrough)

    // ---- Comparing weeks ----

    @Test fun averagesEachWeek() {
        val c = change(days(20, 20, 20, 20, 20, 20, 20, 40, 40, 40, 40, 40, 40, 40))!!
        assertEquals(20, c.averageMinutes)
        assertEquals(40, c.previousAverageMinutes)
        assertEquals(20, c.minutesLessPerDay)
        assertEquals(WeekChange.Direction.LESS, c.direction)
    }

    @Test fun keptDaysCountAtOrUnderTheLimit() {
        val c = change(days(30, 31, 10, 50, 50, 50, 50, 50, 50, 50, 10))!!
        assertEquals(2, c.daysKept)
        assertEquals(7, c.daysCounted)
        assertEquals(1, c.previousDaysKept)
        assertEquals(4, c.previousDaysCounted)
    }

    @Test fun moreAndSame() {
        assertEquals(WeekChange.Direction.MORE, change(days(40, 40, 40, 40, 40, 40, 40, 30, 30, 30))!!.direction)
        assertEquals(WeekChange.Direction.SAME, change(days(31, 31, 31, 31, 31, 31, 31, 30, 30, 30))!!.direction)
    }

    @Test fun needsThreeCountedDaysInEachWeek() {
        assertNull(change(days(20, 20, 20, 20, 20, 20, 20, 40, 40)))
        assertNull(change(days(20, 20, null, null, null, null, null, 40, 40, 40)))
    }

    @Test fun unknownDaysAreLeftOut() {
        val c = change(days(10, null, 10, 10, null, null, null, 40, 40, 40))!!
        assertEquals(10, c.averageMinutes)
        assertEquals(3, c.daysCounted)
    }

    @Test fun daysNotLimitedAreLeftOut() {
        val neutral = AppTerms("Instagram", AppCategory.NEUTRAL, null)
        val records = days(10, 10, 10, 10, 10, 10, 10) + days(90, 90, 90, 90, 90, 90, 90, 40, 40, 40, terms = neutral).drop(7)
        assertNull(change(records))
    }

    @Test fun unsettledDaysAreSkipped() {
        val records = days(20, 20, 20, 40, 40, 40, 40, 40, 40, 40)
        assertEquals(5, change(records, settledThrough = today.minusDays(3))!!.daysCounted)
        assertNull(change(records, settledThrough = today.minusDays(6)))
    }

    @Test fun nothingBeforeAnyDaySettled() = assertNull(change(days(20, 20, 20, 40, 40, 40, 40, 40, 40, 40), settledThrough = null))

    @Test fun todayAndOlderThanTwoWeeksAreIgnored() {
        val records = days(20, 20, 20, 20, 20, 20, 20, 40, 40, 40, 40, 40, 40, 40, 500, 500) +
            DayRecord(today, 500, limited)
        val c = change(records)!!
        assertEquals(20, c.averageMinutes)
        assertEquals(40, c.previousAverageMinutes)
    }

    @Test fun labelComesFromTheLatestDay() {
        val renamed = days(20, 20, 20, terms = limited.copy(label = "Insta")) + days(1, 1, 1, 40, 40, 40, 40, 40, 40, 40).drop(3)
        assertEquals("Insta", change(renamed)!!.label)
    }

    // ---- Order and copy ----

    private fun wc(label: String, now: Int, before: Int) = WeekChange(label, label, now, before, 0, 7, 0, 7)

    @Test fun biggestImprovementFirst() =
        assertEquals(listOf("b", "c", "a"), Progress.sorted(listOf(wc("a", 50, 40), wc("b", 10, 40), wc("c", 30, 30))).map { it.label })

    @Test fun copyDescribesOnlyWhatWasMeasured() {
        assertEquals("18 min less a day than the week before", Progress.minutesText(wc("a", 22, 40)))
        assertEquals("12 min more a day than the week before", Progress.minutesText(wc("a", 42, 30)))
        assertEquals("About the same as the week before", Progress.minutesText(wc("a", 31, 30)))
        assertEquals("18 min less", Progress.pillText(wc("a", 22, 40)))
        assertEquals("Same", Progress.pillText(wc("a", 31, 30)))
        assertEquals(
            "Limit kept on 5 of 7 days, 2 of 6 the week before",
            Progress.keptText(WeekChange("p", "a", 0, 0, 5, 7, 2, 6)),
        )
    }

    @Test fun headlineAddsUpApps() {
        assertNull(Progress.headline(emptyList()))
        assertEquals("25 min less a day in your limited apps", Progress.headline(listOf(wc("a", 10, 40), wc("b", 35, 30))))
        assertEquals("10 min more a day in your limited apps", Progress.headline(listOf(wc("a", 40, 30))))
        assertEquals("About the same time in your limited apps", Progress.headline(listOf(wc("a", 30, 31))))
    }
}
