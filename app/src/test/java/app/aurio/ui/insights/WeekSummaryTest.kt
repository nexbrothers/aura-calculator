package app.aurio.ui.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekSummaryTest {
    private val labels = listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Today")

    @Test fun bestDayIsTheHighestScore() =
        assertEquals("Mon, +72", bestDay(listOf(null, 45, -17, 0, 72, 30, 60), labels))

    @Test fun aTieGoesToTheLatestDay() =
        assertEquals("Today, +60", bestDay(listOf(null, 60, null, null, null, null, 60), labels))

    @Test fun onlyLossesStillNamesTheBestOfThem() =
        assertEquals("Fri, −5", bestDay(listOf(null, -5, -20, null, null, null, -30), labels))

    @Test fun noScoresMeansNoBestDay() = assertNull(bestDay(List(7) { null }, labels))

    @Test fun focusTimeReadsNaturally() {
        assertEquals("0 min", focusTime(0))
        assertEquals("25 min", focusTime(25))
        assertEquals("2 h", focusTime(120))
        assertEquals("4 h 12 min", focusTime(252))
    }
}
