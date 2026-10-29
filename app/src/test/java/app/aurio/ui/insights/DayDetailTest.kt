package app.aurio.ui.insights

import app.aurio.data.db.DailyScoreEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DayDetailTest {
    private val today = LocalDate.parse("2026-10-26")
    private val locale = Locale.UK

    @Test fun settledDaySaysChangeScoreAndLevel() {
        val day = DailyScoreEntity("2026-10-20", 45, 705, partial = false, streakAfter = 3, settledAt = 0)
        assertEquals("Tue 20 Oct: +45, aura 705, Radiant", dayDetail(LocalDate.parse("2026-10-20"), day, today, locale))
    }

    @Test fun partialDayIsCalledOut() {
        val day = DailyScoreEntity("2026-10-20", 0, 100, partial = true, streakAfter = 0, settledAt = 0)
        assertEquals(
            "Tue 20 Oct: 0, aura 100, Static. Partial day: this day was not counted in full.",
            dayDetail(LocalDate.parse("2026-10-20"), day, today, locale),
        )
    }

    @Test fun lossUsesTheRealMinusSign() {
        val day = DailyScoreEntity("2026-10-20", -17, 688, partial = false, streakAfter = 0, settledAt = 0)
        assertEquals("Tue 20 Oct: −17, aura 688, Radiant", dayDetail(LocalDate.parse("2026-10-20"), day, today, locale))
    }

    @Test fun todayAndFutureAndMissingDays() {
        assertEquals("Mon 26 Oct: today settles at midnight.", dayDetail(today, null, today, locale))
        assertEquals("Fri 30 Oct: still to come.", dayDetail(LocalDate.parse("2026-10-30"), null, today, locale))
        assertEquals(
            "Thu 1 Oct: no score. Waura was not tracking yet, or the day has not settled.",
            dayDetail(LocalDate.parse("2026-10-01"), null, today, locale),
        )
    }
}
